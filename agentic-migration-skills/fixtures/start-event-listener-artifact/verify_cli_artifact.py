#!/usr/bin/env python3

import argparse
import json
import shutil
import subprocess
import tempfile
from pathlib import Path
from xml.etree import ElementTree


CATEGORY = "execution-listener-on-start-event"
NAMESPACES = {
    "bpmn": "http://www.omg.org/spec/BPMN/20100524/MODEL",
    "zeebe": "http://camunda.org/schema/zeebe/1.0",
}


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def convert_case(java, jar, source, target_version, work_directory):
    work_directory.mkdir()
    input_file = work_directory / source.name
    shutil.copyfile(source, input_file)
    command = [
        java,
        "-Dfile.encoding=UTF-8",
        "-jar",
        str(jar),
        "local",
        str(input_file),
        "--platform-version",
        target_version,
        "--json",
    ]
    result = subprocess.run(
        command,
        cwd=work_directory,
        capture_output=True,
        check=False,
        text=True,
    )
    output = result.stdout + result.stderr
    require(
        result.returncode == 0,
        f"Converter exited with {result.returncode}:\n{output}",
    )

    report_file = work_directory / "analysis-results.json"
    converted_file = work_directory / f"converted-c8-{source.name}"
    require(report_file.is_file(), f"Converter did not create {report_file}")
    require(converted_file.is_file(), f"Converter did not create {converted_file}")
    return json.loads(report_file.read_text(encoding="utf-8")), converted_file


def find_start_listener(report, source_name):
    return [
        finding
        for finding in report
        if finding.get("messageId") == CATEGORY
        and Path(finding.get("filename") or "").name == source_name
    ]


def has_unsupported_start_listener(converted_file):
    root = ElementTree.parse(converted_file).getroot()
    for start_event in root.findall(".//bpmn:startEvent", NAMESPACES):
        listeners = start_event.findall(
            "./bpmn:extensionElements/zeebe:executionListeners/"
            "zeebe:executionListener",
            NAMESPACES,
        )
        if any(listener.get("eventType") == "start" for listener in listeners):
            return True
    return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--target-version", required=True)
    parser.add_argument("--java", default="java")
    arguments = parser.parse_args()

    jar = arguments.jar.resolve()
    require(jar.is_file(), f"CLI JAR does not exist: {jar}")
    fixture_directory = Path(__file__).resolve().parent / "c7-source"
    listener_source = fixture_directory / "start-listener.bpmn"
    control_source = fixture_directory / "no-listener.bpmn"

    with tempfile.TemporaryDirectory(prefix="camunda-cli-artifact-check-") as temporary:
        temporary_directory = Path(temporary)
        listener_report, listener_copy = convert_case(
            arguments.java,
            jar,
            listener_source,
            arguments.target_version,
            temporary_directory / "listener",
        )
        control_report, control_copy = convert_case(
            arguments.java,
            jar,
            control_source,
            arguments.target_version,
            temporary_directory / "control",
        )

        listener_findings = find_start_listener(listener_report, listener_source.name)
        require(
            len(listener_findings) == 1
            and listener_findings[0].get("elementId") == "Start_Listener",
            "The listener model must report exactly one matching "
            "execution-listener-on-start-event finding.",
        )
        require(
            not find_start_listener(control_report, control_source.name),
            "The no-listener control must not report "
            "execution-listener-on-start-event.",
        )
        require(
            not has_unsupported_start_listener(listener_copy),
            "The converted listener model retains an unsupported start listener.",
        )
        require(
            not has_unsupported_start_listener(control_copy),
            "The converted control model contains an unexpected start listener.",
        )
    print(
        f"CLI artifact {jar.name} passed the start-listener probe "
        f"for target {arguments.target_version}."
    )


if __name__ == "__main__":
    main()
