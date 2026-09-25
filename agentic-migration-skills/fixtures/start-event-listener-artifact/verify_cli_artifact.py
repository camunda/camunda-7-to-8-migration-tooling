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
    "camunda": "http://camunda.org/schema/1.0/bpmn",
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


def get_source_listener_implementation(source):
    root = ElementTree.parse(source).getroot()
    listeners = root.findall(
        ".//bpmn:startEvent[@id='Start_Listener']/bpmn:extensionElements/"
        "camunda:executionListener[@event='start']",
        NAMESPACES,
    )
    require(
        len(listeners) == 1,
        "The source fixture must have exactly one start listener at Start_Listener.",
    )
    implementations = [
        listeners[0].get(attribute)
        for attribute in ("class", "expression", "delegateExpression")
        if listeners[0].get(attribute)
    ]
    require(
        len(implementations) == 1,
        "The source listener must have exactly one implementation.",
    )
    return implementations[0]


def find_start_listener(report):
    require(isinstance(report, list), "The converter JSON report must be an array.")
    require(
        all(isinstance(finding, dict) for finding in report),
        "Every converter JSON report entry must be an object.",
    )
    return [
        finding
        for finding in report
        if finding.get("messageId") == CATEGORY
    ]


def require_listener_finding(report, source_name, implementation):
    findings = find_start_listener(report)
    require(
        len(findings) == 1
        and findings[0].get("filename") == source_name
        and findings[0].get("elementId") == "Start_Listener",
        f"The listener model must report exactly one matching {CATEGORY} "
        f"finding for {source_name} at Start_Listener.",
    )
    finding = findings[0]
    require(
        finding.get("severity") == "TASK",
        f"The matching {CATEGORY} finding must have blocking TASK severity.",
    )
    require(
        isinstance(finding.get("message"), str)
        and implementation in finding["message"],
        f"The matching {CATEGORY} finding message must include the source "
        f"listener implementation {implementation!r}.",
    )


def require_no_listener_findings(report):
    require(
        not find_start_listener(report),
        f"The no-listener control must not report {CATEGORY}.",
    )


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
    implementation = get_source_listener_implementation(listener_source)

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

        require_listener_finding(
            listener_report, listener_source.name, implementation
        )
        require_no_listener_findings(control_report)
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
