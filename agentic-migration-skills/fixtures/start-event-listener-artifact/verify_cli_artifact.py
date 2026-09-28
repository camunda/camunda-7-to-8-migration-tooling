#!/usr/bin/env python3

import argparse
import json
import shutil
import subprocess
import tempfile
from collections import Counter
from pathlib import Path
from xml.etree import ElementTree


CATEGORY = "execution-listener-on-start-event"
START_EVENT_ID = "Start_Listener"
IMPLEMENTATION_ATTRIBUTES = ("delegateExpression", "class", "expression")
NAMESPACES = {
    "bpmn": "http://www.omg.org/spec/BPMN/20100524/MODEL",
    "camunda": "http://camunda.org/schema/1.0/bpmn",
    "zeebe": "http://camunda.org/schema/zeebe/1.0",
}


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def convert_case(java, jar, source, target_version, work_directory, relative_path):
    work_directory.mkdir()
    input_directory = work_directory / "input"
    input_directory.mkdir()
    input_file = input_directory / relative_path
    input_file.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, input_file)
    command = [
        java,
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

    report_file = input_directory / "analysis-results.json"
    converted_file = input_file.parent / f"converted-c8-{source.name}"
    require(report_file.is_file(), f"Converter did not create {report_file}")
    require(converted_file.is_file(), f"Converter did not create {converted_file}")
    return json.loads(report_file.read_text(encoding="utf-8")), converted_file


def get_source_listener_implementations(source):
    root = ElementTree.parse(source).getroot()
    listeners = root.findall(
        f".//bpmn:startEvent[@id='{START_EVENT_ID}']/bpmn:extensionElements/"
        "camunda:executionListener[@event='start']",
        NAMESPACES,
    )
    require(
        len(listeners) > 0,
        f"The source fixture must have at least one start listener at {START_EVENT_ID}.",
    )
    source_implementations = []
    for listener in listeners:
        implementations = [
            (attribute, listener.get(attribute))
            for attribute in IMPLEMENTATION_ATTRIBUTES
            if listener.get(attribute) is not None
        ]
        scripts = listener.findall("camunda:script", NAMESPACES)
        if scripts:
            require(
                len(scripts) == 1,
                "Each source listener must have exactly one implementation.",
            )
            script_format = scripts[0].get("scriptFormat")
            implementations.append(
                ("script", "null" if script_format is None else script_format)
            )
        if not implementations:
            implementations.append(("null", "null"))
        require(
            len(implementations) == 1,
            "Each source listener must have exactly one implementation.",
        )
        source_implementations.append(implementations[0])
    return source_implementations


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


def require_listener_findings(
    report, source_name, event_id, source_implementations
):
    findings = find_start_listener(report)
    expected = Counter(source_implementations)
    require(
        sum(expected.values()) > 0,
        "The source listener inventory must not be empty.",
    )
    require(
        len(findings) == sum(expected.values()),
        f"The listener model must report exactly one {CATEGORY} finding "
        "for each source listener.",
    )
    matched = Counter()
    for finding in findings:
        require(
            finding.get("filename") == source_name
            and finding.get("elementId") == event_id,
            f"Each {CATEGORY} finding must match {source_name} at {event_id}.",
        )
        message = finding.get("message")
        require(
            isinstance(message, str),
            f"Each {CATEGORY} finding must identify its source listener implementation.",
        )
        implementations = [
            implementation
            for implementation in expected
            if f"'{implementation[0]}' '{implementation[1]}'" in message
        ]
        require(
            len(implementations) == 1,
            f"Each {CATEGORY} finding must match exactly one source listener implementation.",
        )
        implementation = implementations[0]
        matched[implementation] += 1
        require(
            matched[implementation] <= expected[implementation],
            f"The converter report contains a duplicate {CATEGORY} finding.",
        )
        require(
            finding.get("severity") == "TASK",
            f"Each matching {CATEGORY} finding must have blocking TASK severity.",
        )
    require(
        matched == expected,
        f"The converter report must include each source {CATEGORY} finding.",
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


def parse_arguments(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--target-version", required=True)
    parser.add_argument(
        "--java",
        required=True,
        type=Path,
        help="Absolute path to the Java executable checked by the migration run",
    )
    return parser.parse_args(argv)


def resolve_java_executable(java):
    java = java.expanduser()
    require(
        java.is_absolute(),
        f"The Java executable path must be absolute: {java}",
    )
    java = java.resolve()
    require(java.is_file(), f"The Java executable does not exist: {java}")
    return str(java)


def main():
    arguments = parse_arguments()

    java = resolve_java_executable(arguments.java)
    jar = arguments.jar.resolve()
    require(jar.is_file(), f"CLI JAR does not exist: {jar}")
    fixture_directory = Path(__file__).resolve().parent / "c7-source"
    listener_source = fixture_directory / "start-listener.bpmn"
    control_source = fixture_directory / "no-listener.bpmn"
    listener_relative_path = Path("models") / listener_source.name
    control_relative_path = Path("controls") / control_source.name
    implementations = get_source_listener_implementations(listener_source)

    with tempfile.TemporaryDirectory(prefix="camunda-cli-artifact-check-") as temporary:
        temporary_directory = Path(temporary)
        listener_report, listener_copy = convert_case(
            java,
            jar,
            listener_source,
            arguments.target_version,
            temporary_directory / "listener",
            listener_relative_path,
        )
        control_report, control_copy = convert_case(
            java,
            jar,
            control_source,
            arguments.target_version,
            temporary_directory / "control",
            control_relative_path,
        )

        require_listener_findings(
            listener_report,
            str(listener_relative_path),
            START_EVENT_ID,
            implementations,
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
