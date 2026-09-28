#!/usr/bin/env python3

import argparse
import re
from fnmatch import fnmatchcase
from pathlib import Path
from zipfile import BadZipFile, ZipFile


CLASS_ROOT = "BOOT-INF/classes/"
APPLICATION_SOURCE = (
    Path(__file__).resolve().parent
    / "expected-c8/src/main/java/org/camunda/bpm/example/event/message/MessageStartApplication.java"
)
DEPLOYMENT_INVENTORY = {
    "converted-c8-message-start.bpmn",
    "converted-c8-message-decision.dmn",
}
DEPLOYMENT_ANNOTATION = re.compile(
    r"@Deployment\s*\(\s*resources\s*=\s*\{(?P<patterns>.*?)\}\s*\)",
    re.DOTALL,
)
JAVA_STRING_LITERAL = re.compile(r'"([^"\\]*)"')


def load_deployment_patterns(source):
    source_text = source.read_text(encoding="utf-8")
    annotation = DEPLOYMENT_ANNOTATION.search(source_text)
    if annotation is None:
        raise ValueError(f"Cannot find a literal @Deployment resources array in {source}.")

    pattern_source = annotation.group("patterns")
    patterns = JAVA_STRING_LITERAL.findall(pattern_source)
    remainder = JAVA_STRING_LITERAL.sub("", pattern_source)
    if not patterns or re.sub(r"[\s,]", "", remainder):
        raise ValueError(f"@Deployment resources in {source} must be string literals.")
    return patterns


def resource_pattern(deployment_pattern):
    classpath_prefix = "classpath*:"
    if not deployment_pattern.startswith(classpath_prefix):
        raise ValueError(
            f"Unsupported @Deployment resource pattern {deployment_pattern!r}. "
            "Use a root-level classpath* pattern."
        )

    pattern = deployment_pattern[len(classpath_prefix) :].lstrip("/")
    if not pattern or "/" in pattern:
        raise ValueError(
            f"Unsupported @Deployment resource pattern {deployment_pattern!r}. "
            "The packaged-resource probe supports root-level patterns only."
        )
    return pattern


def resources_for_pattern(entries, pattern):
    resources = []
    for entry in entries:
        if not entry.startswith(CLASS_ROOT):
            continue
        resource = entry[len(CLASS_ROOT) :]
        if "/" not in resource and fnmatchcase(resource, pattern):
            resources.append(resource)
    return resources


def verify_packaged_resources(
    entries, patterns=None, expected_inventory=DEPLOYMENT_INVENTORY
):
    if patterns is None:
        patterns = load_deployment_patterns(APPLICATION_SOURCE)
    patterns = list(patterns)
    if not patterns:
        raise ValueError("The @Deployment annotation has no resource patterns.")

    resolved_resources = set()
    for deployment_pattern in patterns:
        pattern = resource_pattern(deployment_pattern)
        matches = resources_for_pattern(entries, pattern)
        if not matches:
            raise ValueError(
                f"Packaged @Deployment pattern {deployment_pattern!r} matches no resources."
            )
        if len(matches) != len(set(matches)):
            raise ValueError(
                f"Packaged @Deployment pattern {deployment_pattern!r} matches duplicate entries."
            )

        actual = set(matches)
        overlap = actual & resolved_resources
        if overlap:
            names = ", ".join(sorted(overlap))
            raise ValueError(
                f"Packaged resources match multiple deployment patterns: {names}."
            )
        resolved_resources.update(actual)

    expected = set(expected_inventory)
    missing = sorted(expected - resolved_resources)
    unexpected = sorted(resolved_resources - expected)
    details = []
    if missing:
        details.append(f"missing: {', '.join(missing)}")
    if unexpected:
        details.append(f"unexpected: {', '.join(unexpected)}")
    if details:
        raise ValueError(
            "Packaged resources selected by @Deployment do not match the expected "
            f"inventory ({'; '.join(details)})."
        )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", required=True, type=Path)
    arguments = parser.parse_args()

    jar = arguments.jar.resolve()
    if not jar.is_file():
        parser.error(f"Executable JAR does not exist: {jar}")

    try:
        patterns = load_deployment_patterns(APPLICATION_SOURCE)
        with ZipFile(jar) as archive:
            verify_packaged_resources(
                [entry.filename for entry in archive.infolist()],
                patterns,
            )
    except (BadZipFile, OSError, ValueError) as error:
        parser.error(f"Cannot verify packaged resources in {jar}: {error}")

    print(f"Packaged deployment resources in {jar.name} match the @Deployment inventory.")


if __name__ == "__main__":
    main()
