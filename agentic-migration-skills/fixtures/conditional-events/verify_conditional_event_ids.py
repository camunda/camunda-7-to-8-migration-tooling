#!/usr/bin/env python3
"""Check conditional-event IDs and BPMN identity preservation in the M2 fixture."""

import argparse
from collections import Counter
import sys
import xml.etree.ElementTree as ET


BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL"
BPMN_DI = "http://www.omg.org/spec/BPMN/20100524/DI"
DC = "http://www.omg.org/spec/DD/20100524/DC"
DI = "http://www.omg.org/spec/DD/20100524/DI"
DI_NAMESPACE_URIS = frozenset((BPMN_DI, DC, DI))
BPMN_EVENTS = frozenset(
    (
        "startEvent",
        "endEvent",
        "intermediateCatchEvent",
        "intermediateThrowEvent",
        "boundaryEvent",
    )
)


def descendants(root, namespace, local_name):
    tag = f"{{{namespace}}}{local_name}"
    return [element for element in root.iter() if element.tag == tag]


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


def ids_for_types(root, local_names):
    return Counter(
        (element.tag, element.get("id"))
        for element in root.iter()
        if element.tag.startswith(f"{{{BPMN}}}")
        and element.tag.rsplit("}", 1)[-1] in local_names
    )


def owning_event_ids(root, definitions):
    parents = {child: parent for parent in root.iter() for child in parent}
    owner_ids = []
    for definition in definitions:
        owner = parents.get(definition)
        while owner is not None:
            if (
                owner.tag.startswith(f"{{{BPMN}}}")
                and owner.tag.rsplit("}", 1)[-1] in BPMN_EVENTS
            ):
                owner_ids.append(owner.get("id"))
                break
            owner = parents.get(owner)
        else:
            owner_ids.append(None)
    return owner_ids


def check(source_path, converted_path):
    source_root = ET.parse(source_path).getroot()
    converted_root = ET.parse(converted_path).getroot()
    source_definitions = descendants(source_root, BPMN, "conditionalEventDefinition")
    converted_definitions = descendants(
        converted_root, BPMN, "conditionalEventDefinition"
    )
    source_owner_ids = owning_event_ids(source_root, source_definitions)
    converted_owner_ids = owning_event_ids(converted_root, converted_definitions)
    failures = []

    if not source_definitions:
        failures.append("the source has no conditional event definition")
    elif not any(
        not (definition.get("id") or "").strip()
        for definition in source_definitions
    ):
        failures.append(
            "the source fixture must contain a conditional event definition without an ID"
        )

    if len(source_definitions) != len(converted_definitions):
        failures.append(
            f"conditional event definitions: expected {len(source_definitions)}, "
            f"found {len(converted_definitions)}"
        )

    source_id_counts = Counter(
        element_id.strip()
        for element in source_root.iter()
        if (element_id := element.get("id")) is not None and element_id.strip()
    )
    all_ids = Counter(
        element_id
        for element in converted_root.iter()
        if (element_id := element.get("id")) is not None
    )
    definition_ids = []
    for index, definition in enumerate(converted_definitions, start=1):
        element_id = (definition.get("id") or "").strip()
        if not element_id:
            failures.append(
                f"converted conditional event definition {index} has no nonempty ID"
            )
            continue
        definition_ids.append(element_id)
        if all_ids[element_id] != 1:
            failures.append(
                f"conditional event definition {index} ID {element_id!r} "
                "is not unique in the XML document"
            )
    if len(set(definition_ids)) != len(definition_ids):
        failures.append("converted conditional event definition IDs are not unique")

    converted_owner_ids_by_definition_id = {}
    for definition, owner_id in zip(converted_definitions, converted_owner_ids):
        definition_id = (definition.get("id") or "").strip()
        if definition_id:
            converted_owner_ids_by_definition_id.setdefault(
                definition_id, []
            ).append(owner_id)

    for definition, source_owner_id in zip(source_definitions, source_owner_ids):
        definition_id = (definition.get("id") or "").strip()
        if not definition_id or source_id_counts[definition_id] != 1:
            continue
        converted_owners = converted_owner_ids_by_definition_id.get(definition_id, [])
        if not converted_owners:
            failures.append(
                f"unique source conditional event definition ID "
                f"{definition_id!r} was not preserved"
            )
            continue
        if len(converted_owners) != 1:
            continue
        converted_owner_id = converted_owners[0]
        if not source_owner_id or not source_owner_id.strip():
            failures.append(
                f"unique source conditional event definition ID "
                f"{definition_id!r} has no owning event ID"
            )
        elif not converted_owner_id or not converted_owner_id.strip():
            failures.append(
                f"converted conditional event definition ID "
                f"{definition_id!r} has no owning event ID"
            )
        elif source_owner_id != converted_owner_id:
            failures.append(
                f"unique source conditional event definition ID "
                f"{definition_id!r} moved from event {source_owner_id!r} "
                f"to event {converted_owner_id!r}"
            )

    for local_names, label in (
        (BPMN_EVENTS, "event"),
        (frozenset(("sequenceFlow",)), "sequence-flow"),
    ):
        if ids_for_types(source_root, local_names) != ids_for_types(
            converted_root, local_names
        ):
            failures.append(f"original {label} IDs changed")

    source_diagrams = descendants(source_root, BPMN_DI, "BPMNDiagram")
    converted_diagrams = descendants(converted_root, BPMN_DI, "BPMNDiagram")
    if not source_diagrams:
        failures.append("the source fixture has no BPMN DI")
    if [canonical_element(diagram) for diagram in source_diagrams] != [
        canonical_element(diagram) for diagram in converted_diagrams
    ]:
        failures.append("BPMN DI nodes, geometry, attributes, or references changed")
    if namespace_bindings(source_path) != namespace_bindings(converted_path):
        failures.append("BPMN DI namespace bindings changed")

    converted_semantic_ids = {
        element.get("id")
        for element in converted_root.iter()
        if element.tag.startswith(f"{{{BPMN}}}") and element.get("id") is not None
    }
    for namespace, local_name in (
        (BPMN_DI, "BPMNPlane"),
        (BPMN_DI, "BPMNShape"),
        (BPMN_DI, "BPMNEdge"),
    ):
        for element in descendants(converted_root, namespace, local_name):
            reference = element.get("bpmnElement")
            if reference not in converted_semantic_ids:
                failures.append(
                    f"{local_name} {element.get('id')}: "
                    f"bpmnElement {reference!r} is not a converted BPMN ID"
                )

    return failures


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

    print("PASS: conditional event ID and preservation checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
