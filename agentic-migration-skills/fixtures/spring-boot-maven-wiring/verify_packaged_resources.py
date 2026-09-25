#!/usr/bin/env python3

import argparse
from fnmatch import fnmatchcase
from pathlib import Path
from zipfile import BadZipFile, ZipFile


CLASS_ROOT = "BOOT-INF/classes/"
DEPLOYMENT_PATTERNS = {
    "converted-c8-*.bpmn": {"converted-c8-message-start.bpmn"},
    "converted-c8-*.dmn": {"converted-c8-message-decision.dmn"},
}


def resources_for_pattern(entries, pattern):
    resources = []
    for entry in entries:
        if not entry.startswith(CLASS_ROOT):
            continue
        resource = entry[len(CLASS_ROOT) :]
        if "/" not in resource and fnmatchcase(resource, pattern):
            resources.append(resource)
    return resources


def verify_packaged_resources(entries):
    for pattern, expected in DEPLOYMENT_PATTERNS.items():
        matches = resources_for_pattern(entries, pattern)
        if not matches:
            raise ValueError(f"Packaged resource pattern {pattern!r} matches no resources.")
        if len(matches) != len(set(matches)):
            raise ValueError(f"Packaged resource pattern {pattern!r} matches duplicate entries.")

        actual = set(matches)
        if actual != expected:
            missing = sorted(expected - actual)
            unexpected = sorted(actual - expected)
            details = []
            if missing:
                details.append(f"missing: {', '.join(missing)}")
            if unexpected:
                details.append(f"unexpected: {', '.join(unexpected)}")
            raise ValueError(f"Packaged resource pattern {pattern!r} has {'; '.join(details)}.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", required=True, type=Path)
    arguments = parser.parse_args()

    jar = arguments.jar.resolve()
    if not jar.is_file():
        parser.error(f"Executable JAR does not exist: {jar}")

    try:
        with ZipFile(jar) as archive:
            verify_packaged_resources([entry.filename for entry in archive.infolist()])
    except (BadZipFile, ValueError) as error:
        parser.error(f"Cannot verify packaged resources in {jar}: {error}")

    print(f"Packaged deployment resources in {jar.name} match the expected inventory.")


if __name__ == "__main__":
    main()
