#!/usr/bin/env python3

import argparse
import json
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--target-version", required=True)
    args = parser.parse_args()

    java = args.java.expanduser()
    jar = args.jar.resolve()
    require(java.is_absolute() and java.is_file(), "Pass the validated absolute Java executable.")
    require(jar.is_file(), f"Converter JAR does not exist: {jar}")

    fixtures = Path(__file__).resolve().parent / "c7-source"
    with tempfile.TemporaryDirectory(prefix="camunda-cli-artifact-check-") as temporary:
        input_directory = Path(temporary) / "input"
        models = (("models", "start-listener.bpmn"), ("controls", "no-listener.bpmn"))
        for folder, filename in models:
            destination = input_directory / folder / filename
            destination.parent.mkdir(parents=True)
            shutil.copyfile(fixtures / filename, destination)

        result = subprocess.run(
            [
                str(java),
                "-Dfile.encoding=UTF-8",
                "-jar",
                str(jar),
                "local",
                str(input_directory),
                "--platform-version",
                args.target_version,
                "--json",
            ],
            cwd=temporary,
            capture_output=True,
            text=True,
            check=False,
        )
        require(result.returncode == 0, f"Converter failed:\n{result.stdout}\n{result.stderr}")
        report_path = input_directory / "analysis-results.json"
        require(report_path.is_file(), f"Converter did not create {report_path}")
        verify_findings(json.loads(report_path.read_text(encoding="utf-8")))

        for folder, filename in models:
            converted = input_directory / folder / f"converted-c8-{filename}"
            require(converted.is_file(), f"Converter did not create {converted}")
            verify_converted_copy(converted)

    print(f"CLI artifact {jar.name} passed for target {args.target_version}.")


if __name__ == "__main__":
    main()
