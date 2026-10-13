#!/usr/bin/env python3
"""Check sequence-flow references and start-to-end paths in a single-process BPMN fixture."""

import sys
import xml.etree.ElementTree as ET

BPMN = "{http://www.omg.org/spec/BPMN/20100524/MODEL}"
UNSUPPORTED = {"subProcess", "adHocSubProcess", "transaction", "boundaryEvent"}


def _reachable(starts, edges):
    seen, stack = set(starts), list(starts)
    while stack:
        for nxt in edges.get(stack.pop(), ()):
            if nxt not in seen:
                seen.add(nxt)
                stack.append(nxt)
    return seen


def check(path):
    """Return a list of connectivity errors. This is a fixture checker, not a BPMN validator."""
    (process,) = ET.parse(path).getroot().findall(BPMN + "process")
    nodes, flows = {}, []
    for element in process:
        name = element.tag.removeprefix(BPMN)
        if name in UNSUPPORTED:
            return [f"unsupported construct: {name}"]
        if name == "sequenceFlow":
            flows.append((element.get("id"), element.get("sourceRef"), element.get("targetRef")))
        elif name.endswith(("Event", "Task", "Gateway")) or name in {"task", "callActivity"}:
            nodes[element.get("id")] = name

    errors = [f"{f} references unknown node" for f, s, t in flows if s not in nodes or t not in nodes]
    forward, backward = {}, {}
    for _, source, target in flows:
        forward.setdefault(source, []).append(target)
        backward.setdefault(target, []).append(source)
    from_start = _reachable([n for n, k in nodes.items() if k == "startEvent"], forward)
    to_end = _reachable([n for n, k in nodes.items() if k == "endEvent"], backward)
    errors += [f"{n} is not on a start-to-end path" for n in nodes if n not in from_start & to_end]
    return errors


if __name__ == "__main__":
    problems = check(sys.argv[1])
    for problem in problems:
        print(f"ERROR: {problem}", file=sys.stderr)
    sys.exit(1 if problems else 0)
