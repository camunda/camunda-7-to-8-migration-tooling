#!/usr/bin/env python3
"""Validate sequence-flow integrity for the simple BPMN regression fixtures."""

from __future__ import annotations

import argparse
from collections import defaultdict, deque
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL"
BPMN = f"{{{BPMN_NS}}}"

FLOW_NODE_NAMES = {
    "startEvent",
    "intermediateCatchEvent",
    "intermediateThrowEvent",
    "endEvent",
    "task",
    "serviceTask",
    "userTask",
    "manualTask",
    "sendTask",
    "receiveTask",
    "businessRuleTask",
    "scriptTask",
    "callActivity",
    "exclusiveGateway",
    "parallelGateway",
    "inclusiveGateway",
    "eventBasedGateway",
    "complexGateway",
}
UNSUPPORTED_SCOPE_NAMES = {"adHocSubProcess", "boundaryEvent", "eventSubProcess", "subProcess", "transaction"}


class ValidationError(ValueError):
    """Raised when a fixture has an invalid or unsupported sequence-flow graph."""


def _reachable(start_ids: set[str], adjacency: dict[str, set[str]]) -> set[str]:
    reached = set(start_ids)
    queue = deque(start_ids)
    while queue:
        current = queue.popleft()
        for target in adjacency[current]:
            if target not in reached:
                reached.add(target)
                queue.append(target)
    return reached


def _local_name(tag: str) -> str:
    return tag[len(BPMN):] if tag.startswith(BPMN) else tag


def validate_connectivity(path: Path) -> None:
    """Validate references and start-to-end connectivity in a simple process fixture."""
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as error:
        raise ValidationError(f"{path}: invalid XML: {error}") from error

    processes = root.findall(BPMN + "process")
    if len(processes) != 1:
        raise ValidationError(f"{path}: expected one top-level BPMN process, found {len(processes)}")

    process = processes[0]
    unsupported = [
        _local_name(element.tag)
        for element in process.iter()
        if element.tag.startswith(BPMN) and _local_name(element.tag) in UNSUPPORTED_SCOPE_NAMES
    ]
    if unsupported:
        raise ValidationError(
            f"{path}: fixture verifier does not support BPMN scope constructs: {', '.join(sorted(set(unsupported)))}"
        )

    nodes = [
        element
        for element in process
        if element.tag.startswith(BPMN) and _local_name(element.tag) in FLOW_NODE_NAMES
    ]
    flows = process.findall(BPMN + "sequenceFlow")
    node_by_id: dict[str, ET.Element] = {}
    for node in nodes:
        node_id = node.get("id")
        if not node_id:
            raise ValidationError(f"{path}: flow node is missing its id")
        if node_id in node_by_id:
            raise ValidationError(f"{path}: duplicate flow-node id {node_id!r}")
        node_by_id[node_id] = node

    adjacency: dict[str, set[str]] = defaultdict(set)
    reverse_adjacency: dict[str, set[str]] = defaultdict(set)
    flow_by_id: dict[str, ET.Element] = {}
    for flow in flows:
        flow_id = flow.get("id")
        source = flow.get("sourceRef")
        target = flow.get("targetRef")
        if not flow_id or not source or not target:
            raise ValidationError(f"{path}: every sequence flow needs id, sourceRef, and targetRef")
        if flow_id in flow_by_id or flow_id in node_by_id:
            raise ValidationError(f"{path}: duplicate BPMN id {flow_id!r}")
        if source not in node_by_id or target not in node_by_id:
            raise ValidationError(
                f"{path}: sequence flow {flow_id!r} references unknown flow node(s): "
                f"sourceRef={source!r}, targetRef={target!r}"
            )
        flow_by_id[flow_id] = flow
        adjacency[source].add(target)
        reverse_adjacency[target].add(source)

    for node_id, node in node_by_id.items():
        expected_incoming = {flow_id for flow_id, flow in flow_by_id.items() if flow.get("targetRef") == node_id}
        expected_outgoing = {flow_id for flow_id, flow in flow_by_id.items() if flow.get("sourceRef") == node_id}
        for child_name, expected in (("incoming", expected_incoming), ("outgoing", expected_outgoing)):
            children = node.findall(BPMN + child_name)
            if children:
                declared = [(child.text or "").strip() for child in children]
                if len(declared) != len(set(declared)) or set(declared) != expected:
                    raise ValidationError(
                        f"{path}: {node_id!r} has inconsistent bpmn:{child_name} references "
                        f"{declared!r}; expected {sorted(expected)!r}"
                    )

    start_ids = {node_id for node_id, node in node_by_id.items() if node.tag == BPMN + "startEvent"}
    end_ids = {node_id for node_id, node in node_by_id.items() if node.tag == BPMN + "endEvent"}
    if not start_ids or not end_ids:
        raise ValidationError(f"{path}: the process needs an explicit start event and end event")

    from_start = _reachable(start_ids, adjacency)
    to_end = _reachable(end_ids, reverse_adjacency)
    off_path = sorted(node_id for node_id in node_by_id if node_id not in from_start or node_id not in to_end)
    if off_path:
        raise ValidationError(f"{path}: flow nodes are not on an explicit start-to-end path: {', '.join(off_path)}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bpmn", type=Path, help="a simple BPMN fixture file")
    args = parser.parse_args()
    try:
        validate_connectivity(args.bpmn)
    except ValidationError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"OK: {args.bpmn} has valid sequence-flow references and a connected start-to-end path")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
