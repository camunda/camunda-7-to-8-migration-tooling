#!/usr/bin/env python3

import argparse
import json
import re
import shutil
import subprocess
import tempfile
from pathlib import Path
from xml.etree import ElementTree


CATEGORY = "execution-listener-on-start-event"
EXPECTED_MESSAGES = (
    "'delegateExpression' '${startListener}'",
    "'class' 'com.example.StartListener'",
    "'expression' '${startExpression}'",
    "'script' 'groovy'",
    "'null' 'null'",
)
NAMESPACES = {
    "bpmn": "http://www.omg.org/spec/BPMN/20100524/MODEL",
    "zeebe": "http://camunda.org/schema/zeebe/1.0",
}


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def parse_java_major(version_output):
    match = re.search(r'\bversion\s+"([^"]+)"', version_output, re.IGNORECASE)
    require(match is not None, "Could not parse the Java major version from `java -version`.")
    version = match.group(1)
    major_text = version.split(".", 1)[1] if version.startswith("1.") else version
    major = re.match(r"\d+", major_text)
    require(major is not None, f"Could not parse the Java major version from `{version}`.")
    return int(major.group(0))


def read_java_major(java):
    try:
        result = subprocess.run(
            [str(java), "-version"],
            capture_output=True,
            text=True,
            check=False,
        )
    except OSError as error:
        raise SystemExit(f"Cannot run Java at {java}: {error}") from error
    require(
        result.returncode == 0,
        f"`java -version` failed for {java} with exit code {result.returncode}.\n"
        f"{result.stdout}\n{result.stderr}",
    )
    return parse_java_major(result.stderr + result.stdout)


def require_supported_java(major, java):
    require(
        major >= 21,
        f"The Diagram Converter CLI requires Java 21 or later. Detected Java {major} at {java}. "
        "Use a Java 21+ runtime, M2 (agentic AI), or M3 (online converter).",
    )


def require_runtime_matrix(majors):
    require(21 in majors, "The compatibility matrix must include Java 21.")
    require(
        any(major >= 26 for major in majors),
        "The compatibility matrix must include Java 26 or later.",
    )


def verify_findings(report):
    require(
        isinstance(report, list) and all(isinstance(finding, dict) for finding in report),
        "The converter JSON report must be an array of findings.",
    )
    findings = [finding for finding in report if finding.get("messageId") == CATEGORY]
    matched = []
    for finding in findings:
        filename = finding.get("filename")
        require(isinstance(filename, str), "The finding must identify its source path.")
        require(
            (
                filename.replace("\\", "/"),
                finding.get("elementId"),
                finding.get("severity"),
            )
            == ("models/start-listener.bpmn", "Start_Listener", "TASK"),
            "Every start-listener finding must be a blocking TASK for the source event.",
        )
        message = finding.get("message")
        require(isinstance(message, str), "The finding must identify the implementation.")
        implementations = [value for value in EXPECTED_MESSAGES if value in message]
        require(len(implementations) == 1, "The finding must identify one source listener.")
        matched.extend(implementations)
    require(
        sorted(matched) == sorted(EXPECTED_MESSAGES),
        "The report must contain one actionable finding per source listener and none for the control.",
    )


def verify_converted_copy(path):
    root = ElementTree.parse(path).getroot()
    invalid = root.findall(
        ".//bpmn:startEvent//zeebe:executionListener[@eventType='start']",
        NAMESPACES,
    )
    require(not invalid, f"The converted copy retains a start listener on a start event: {path}")


def verify_json_option(java, jar, major):
    command = [str(java), "-jar", str(jar), "local", "--help"]
    result = subprocess.run(
        command,
        capture_output=True,
        text=True,
        check=False,
    )
    require(
        result.returncode == 0,
        f"Could not read CLI help with Java {major} at {java}.\n"
        f"Command arguments: {command!r}\n"
        f"Exit code: {result.returncode}\n{result.stdout}\n{result.stderr}",
    )
    require(
        "--json" in result.stdout + result.stderr,
        f"CLI release {jar.name} does not support `--json`. Use release 0.3.7 or later. "
        "This is a CLI capability failure, not a Java compatibility failure.",
    )


def verify_artifact(java, major, jar, target_version):
    verify_json_option(java, jar, major)
    fixtures = Path(__file__).resolve().parent / "c7-source"
    with tempfile.TemporaryDirectory(prefix="camunda-cli-artifact-check-") as temporary:
        input_directory = Path(temporary) / "input"
        models = (("models", "start-listener.bpmn"), ("controls", "no-listener.bpmn"))
        for folder, filename in models:
            destination = input_directory / folder / filename
            destination.parent.mkdir(parents=True)
            shutil.copyfile(fixtures / filename, destination)

        command = [
            str(java),
            "-Dfile.encoding=UTF-8",
            "-jar",
            str(jar),
            "local",
            str(input_directory),
            "--platform-version",
            target_version,
            "--json",
        ]
        result = subprocess.run(
            command,
            cwd=temporary,
            capture_output=True,
            text=True,
            check=False,
        )
        require(
            result.returncode == 0,
            f"Converter failed under Java {major} at {java}.\n"
            f"Command arguments: {command!r}\n"
            f"Exit code: {result.returncode}\n{result.stdout}\n{result.stderr}",
        )
        report_path = input_directory / "analysis-results.json"
        require(report_path.is_file(), f"Converter did not create {report_path}")
        verify_findings(json.loads(report_path.read_text(encoding="utf-8")))

        for folder, filename in models:
            converted = input_directory / folder / f"converted-c8-{filename}"
            require(converted.is_file(), f"Converter did not create {converted}")
            verify_converted_copy(converted)

    print(f"CLI artifact {jar.name} passed under Java {major} at {java}.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--java",
        action="append",
        required=True,
        type=Path,
        help="Absolute Java executable. Repeat to test multiple runtimes.",
    )
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--target-version", required=True)
    parser.add_argument(
        "--require-runtime-matrix",
        action="store_true",
        help="Require Java 21 and Java 26 or later.",
    )
    args = parser.parse_args()

    jar = args.jar.resolve()
    require(jar.is_file(), f"Converter JAR does not exist: {jar}")

    runtimes = []
    for candidate in args.java:
        java = candidate.expanduser()
        require(java.is_absolute() and java.is_file(), "Pass each Java executable as an absolute path.")
        java = java.resolve()
        major = read_java_major(java)
        require_supported_java(major, java)
        runtimes.append((java, major))

    if args.require_runtime_matrix:
        require_runtime_matrix([major for _, major in runtimes])

    for java, major in runtimes:
        verify_artifact(java, major, jar, args.target_version)


if __name__ == "__main__":
    main()
