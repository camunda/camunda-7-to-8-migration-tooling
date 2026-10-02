#!/usr/bin/env python3
"""Check C7 and C8 call-activity output contracts in the M2 fixture."""

import argparse
from collections import Counter
import sys
import xml.etree.ElementTree as ET


BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL"
CAMUNDA = "http://camunda.org/schema/1.0/bpmn"
ZEEBE = "http://camunda.org/schema/zeebe/1.0"
DELEGATED_MAPPING_ATTRIBUTES = (
    "variableMappingClass",
    "variableMappingDelegateExpression",
)
REQUIRED_CASES = {
    "Call_No_Outputs": "none",
    "Call_Selected_Output": "selected",
    "Call_All_Outputs": "all",
}


def direct_children(element, namespace, local_name):
    tag = f"{{{namespace}}}{local_name}"
    return [child for child in element if child.tag == tag]


def extension_children(element, namespace, local_name):
    return [
        child
        for extension in direct_children(element, BPMN, "extensionElements")
        for child in direct_children(extension, namespace, local_name)
    ]


def call_activities(root, label, failures):
    calls = {}
    tag = f"{{{BPMN}}}callActivity"
    for call in root.iter(tag):
        call_id = call.get("id")
        if not call_id:
            failures.append(f"{label} call activity has no ID")
        elif call_id in calls:
            failures.append(f"{label} call activity ID {call_id!r} is duplicated")
        else:
            calls[call_id] = call
    return calls


def feel_source(source):
    return source if source.startswith("=") else f"={source}"


def source_output_contract(call, failures):
    call_id = call.get("id")
    if any(
        f"{{{CAMUNDA}}}{attribute}" in call.attrib
        for attribute in DELEGATED_MAPPING_ATTRIBUTES
    ):
        failures.append(
            f"source call activity {call_id!r} uses delegated variable mappings "
            "and needs review"
        )
        return None

    mappings = extension_children(call, CAMUNDA, "out")
    all_output_mappings = [
        mapping for mapping in mappings if mapping.get("variables") == "all"
    ]
    if all_output_mappings:
        if len(mappings) != 1:
            failures.append(
                f"source call activity {call_id!r} combines all-output and "
                "other mappings and needs review"
            )
            return None
        return "all", []

    if not mappings:
        return "none", []

    pairs = []
    for mapping in mappings:
        source = mapping.get("source")
        target = mapping.get("target")
        if mapping.get("variables") is not None or not source or not target:
            failures.append(
                f"source call activity {call_id!r} has an unsupported output "
                "mapping and needs review"
            )
            return None
        pairs.append((feel_source(source), target))
    return "selected", pairs


def check_roots(source_root, converted_root):
    failures = []
    source_calls = call_activities(source_root, "source", failures)
    converted_calls = call_activities(converted_root, "converted", failures)

    for call_id in REQUIRED_CASES:
        if call_id not in source_calls:
            failures.append(f"source fixture is missing call activity {call_id!r}")
        if call_id not in converted_calls:
            failures.append(f"converted fixture is missing call activity {call_id!r}")

    if set(source_calls) != set(converted_calls):
        failures.append(
            "source and converted call activity IDs differ: "
            f"{sorted(source_calls)!r} versus {sorted(converted_calls)!r}"
        )

    for call_id, source_call in source_calls.items():
        contract = source_output_contract(source_call, failures)
        if contract is None:
            continue
        contract_kind, expected_outputs = contract
        required_kind = REQUIRED_CASES.get(call_id)
        if required_kind is not None and contract_kind != required_kind:
            failures.append(
                f"source fixture call activity {call_id!r} must cover "
                f"{required_kind!r}, found {contract_kind!r}"
            )

        converted_call = converted_calls.get(call_id)
        if converted_call is None:
            continue

        called_elements = extension_children(converted_call, ZEEBE, "calledElement")
        if len(called_elements) != 1:
            failures.append(
                f"converted call activity {call_id!r} must have exactly one "
                "zeebe:calledElement"
            )
            continue

        expected_flag = "true" if contract_kind == "all" else "false"
        actual_flag = called_elements[0].get("propagateAllChildVariables")
        if actual_flag != expected_flag:
            failures.append(
                f"converted call activity {call_id!r} must set "
                f"propagateAllChildVariables={expected_flag!r}, "
                f"found {actual_flag!r}"
            )

        actual_outputs = [
            (output.get("source"), output.get("target"))
            for io_mapping in extension_children(
                converted_call, ZEEBE, "ioMapping"
            )
            for output in direct_children(io_mapping, ZEEBE, "output")
        ]
        if Counter(expected_outputs) != Counter(actual_outputs):
            failures.append(
                f"converted call activity {call_id!r} output mappings: "
                f"expected {expected_outputs!r}, found {actual_outputs!r}"
            )

    return failures


def check(source_path, converted_path):
    source_root = ET.parse(source_path).getroot()
    converted_root = ET.parse(converted_path).getroot()
    return check_roots(source_root, converted_root)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("converted")
    args = parser.parse_args()

    try:
        failures = check(args.source, args.converted)
    except (ET.ParseError, OSError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}", file=sys.stderr)
        return 1

    print("PASS: call-activity output scope checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
