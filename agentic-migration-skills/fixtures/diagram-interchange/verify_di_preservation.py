#!/usr/bin/env python3
"""Check BPMN DI preservation for the diagram-interchange fixture."""

import argparse
import sys
import xml.etree.ElementTree as ET


BPMN_DI = "http://www.omg.org/spec/BPMN/20100524/DI"
DC = "http://www.omg.org/spec/DD/20100524/DC"
DI = "http://www.omg.org/spec/DD/20100524/DI"


def descendants(root, namespace, local_name):
    return root.findall(f".//{{{namespace}}}{local_name}")


def values(element, child_namespace, local_name, attributes):
    children = element.findall(f"./{{{child_namespace}}}{local_name}")
    return [tuple(child.get(attribute) for attribute in attributes) for child in children]


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


def check(source_path, converted_path, expect_no_source_di):
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
        return failures

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

    converted_by_id = {
        element.get("id"): element
        for category in ("diagrams", "planes", "shapes", "edges")
        for element in converted[category]
        if element.get("id") is not None
    }
    for category in ("diagrams", "planes", "shapes", "edges"):
        for source_element in source[category]:
            element_id = source_element.get("id")
            converted_element = converted_by_id.get(element_id)
            if converted_element is None:
                failures.append(f"{category}: missing element {element_id}")
            elif source_element.get("bpmnElement") != converted_element.get("bpmnElement"):
                failures.append(
                    f"{category} {element_id}: bpmnElement reference changed"
                )

    for category in ("shapes", "edges"):
        for source_element in source[category]:
            element_id = source_element.get("id")
            converted_element = converted_by_id.get(element_id)
            if converted_element is None:
                failures.append(f"{category}: missing element {element_id}")
                continue
            child_namespace = DC if category == "shapes" else DI
            child_name = "Bounds" if category == "shapes" else "waypoint"
            attributes = (
                ("x", "y", "width", "height")
                if category == "shapes"
                else ("x", "y")
            )
            if values(source_element, child_namespace, child_name, attributes) != values(
                converted_element, child_namespace, child_name, attributes
            ):
                failures.append(f"{category} {element_id}: geometry changed")

    return failures


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-source-di", action="store_true")
    parser.add_argument("source")
    parser.add_argument("converted")
    args = parser.parse_args()

    try:
        failures = check(args.source, args.converted, args.no_source_di)
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
