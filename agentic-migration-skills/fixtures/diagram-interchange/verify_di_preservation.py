#!/usr/bin/env python3
"""Check BPMN DI preservation for the diagram-interchange fixture."""

import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL"
BPMN_DI = "http://www.omg.org/spec/BPMN/20100524/DI"
DC = "http://www.omg.org/spec/DD/20100524/DC"
DI = "http://www.omg.org/spec/DD/20100524/DI"
DI_NAMESPACE_URIS = frozenset((BPMN_DI, DC, DI))


def descendants(root, namespace, local_name):
    return root.findall(f".//{{{namespace}}}{local_name}")


def semantic_ids(root):
    return {
        element_id
        for element in root.iter()
        if element.tag.startswith(f"{{{BPMN}}}")
        and (element_id := element.get("id")) is not None
    }


def canonical_element(element):
    return (
        element.tag,
        tuple(sorted(element.attrib.items())),
        tuple(canonical_element(child) for child in element),
    )


def namespace_bindings(path):
    bindings = []
    pending = []
    for event, item in ET.iterparse(path, events=("start-ns", "start")):
        if event == "start-ns":
            pending.append(item)
            continue
        element_key = (item.tag, item.get("id"))
        bindings.extend(
            (element_key, prefix, uri)
            for prefix, uri in pending
            if uri in DI_NAMESPACE_URIS
        )
        pending.clear()
    return tuple(sorted(bindings, key=repr))


def report_has_absent_di_entry(report, source_name):
    markers = (
        "source bpmn di: absent",
        "source bpmn di is absent",
        "source has no bpmn di",
        "without source bpmn di",
        "absent source di",
    )
    lines = report.splitlines()
    for index, line in enumerate(lines):
        if source_name not in line:
            continue
        if any(marker in line.lower() for marker in markers):
            return True

        if re.match(r"^\s*#{1,6}\s", line):
            heading_level = len(line) - len(line.lstrip("#"))
            section = []
            for section_line in lines[index + 1 :]:
                next_heading = re.match(r"^\s*(#{1,6})\s", section_line)
                if next_heading and len(next_heading.group(1)) <= heading_level:
                    break
                section.append(section_line)
            if any(
                marker in "\n".join(section).lower() for marker in markers
            ):
                return True
            continue

        indentation = len(line) - len(line.lstrip())
        for continuation in lines[index + 1 :]:
            if not continuation.strip():
                continue
            continuation_indentation = len(continuation) - len(
                continuation.lstrip()
            )
            if continuation_indentation <= indentation:
                break
            if any(marker in continuation.lower() for marker in markers):
                return True
    return False


def di_snapshot(root):
    return {
        "diagrams": descendants(root, BPMN_DI, "BPMNDiagram"),
        "planes": descendants(root, BPMN_DI, "BPMNPlane"),
        "shapes": descendants(root, BPMN_DI, "BPMNShape"),
        "edges": descendants(root, BPMN_DI, "BPMNEdge"),
        "labels": descendants(root, BPMN_DI, "BPMNLabel"),
        "bounds": descendants(root, DC, "Bounds"),
        "waypoints": descendants(root, DI, "waypoint"),
    }


def check(source_path, converted_path, expect_no_source_di, report_path):
    source_root = ET.parse(source_path).getroot()
    converted_root = ET.parse(converted_path).getroot()
    source = di_snapshot(source_root)
    converted = di_snapshot(converted_root)
    failures = []

    if expect_no_source_di:
        if any(source.values()):
            failures.append("the control source contains BPMN DI")
        if any(converted.values()):
            failures.append("the converted control contains manufactured BPMN DI")
        if report_path is None:
            failures.append("the no-DI check requires --report MIGRATION_REPORT.md")
        else:
            report = Path(report_path).read_text(encoding="utf-8")
            source_name = Path(source_path).name
            if source_name not in report:
                failures.append(
                    f"the migration report does not mention {source_name}"
                )
            elif not report_has_absent_di_entry(report, source_name):
                failures.append(
                    "the migration report does not record absent source BPMN DI "
                    f"for {source_name}"
                )
        return failures

    if namespace_bindings(source_path) != namespace_bindings(converted_path):
        failures.append("BPMN DI namespace bindings changed")

    source_semantic_ids = semantic_ids(source_root)
    converted_semantic_ids = semantic_ids(converted_root)
    for category in ("planes", "shapes", "edges"):
        for element in source[category]:
            reference = element.get("bpmnElement")
            if reference not in source_semantic_ids:
                failures.append(
                    f"{category} {element.get('id')}: "
                    f"source bpmnElement {reference} is not a semantic ID"
                )
        for element in converted[category]:
            reference = element.get("bpmnElement")
            if reference not in converted_semantic_ids:
                failures.append(
                    f"{category} {element.get('id')}: "
                    f"converted bpmnElement {reference} is not a semantic ID"
                )

    for category in (
        "diagrams",
        "planes",
        "shapes",
        "edges",
        "labels",
        "bounds",
        "waypoints",
    ):
        if len(source[category]) != len(converted[category]):
            failures.append(
                f"{category}: expected {len(source[category])}, "
                f"found {len(converted[category])}"
            )

    for category in ("diagrams", "planes", "shapes", "edges"):
        converted_by_id = {
            element.get("id"): element
            for element in converted[category]
            if element.get("id") is not None
        }
        for source_element in source[category]:
            element_id = source_element.get("id")
            converted_element = converted_by_id.get(element_id)
            if converted_element is None:
                failures.append(f"{category}: missing element {element_id}")
                continue
            if canonical_element(source_element) != canonical_element(
                converted_element
            ):
                failures.append(
                    f"{category} {element_id}: attributes or children changed"
                )

    return failures


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-source-di", action="store_true")
    parser.add_argument("--report", help="path to the migration report")
    parser.add_argument("source")
    parser.add_argument("converted")
    args = parser.parse_args()

    try:
        failures = check(
            args.source, args.converted, args.no_source_di, args.report
        )
    except (ET.ParseError, OSError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}", file=sys.stderr)
        return 1

    print("PASS: BPMN DI checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
