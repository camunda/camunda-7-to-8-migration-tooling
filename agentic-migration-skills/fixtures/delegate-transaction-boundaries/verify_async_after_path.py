#!/usr/bin/env python3
"""Check the asyncAfter and synchronous control paths in this fixture."""

import argparse
from pathlib import Path
import xml.etree.ElementTree as ET


BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL"
CAMUNDA = "http://camunda.org/schema/1.0/bpmn"


def main():
    default_model = (
        Path(__file__).parent
        / "c7-source"
        / "src"
        / "main"
        / "resources"
        / "async-after-boundary-c7.bpmn"
    )
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", nargs="?", type=Path, default=default_model)
    model = parser.parse_args().model

    process = ET.parse(model).getroot().find(f".//{{{BPMN}}}process")
    if process is None:
        raise SystemExit(f"No BPMN process found in {model}.")

    activities = {element.get("id"): element for element in process}
    flows = {
        flow.get("id"): flow
        for flow in process.findall(f"{{{BPMN}}}sequenceFlow")
    }

    start = activities.get("StartEvent")
    gateway = activities.get("RoutePath")
    async_wait = activities.get("WaitStateAsyncAfter")
    sync_wait = activities.get("WaitStateSynchronous")
    delegate = activities.get("SynchronousDelegate")
    if any(element is None for element in (start, gateway, async_wait, sync_wait, delegate)):
        raise SystemExit("The fixture must contain the start, gateway, both wait states, and delegate.")
    if gateway.get("default") != "Flow_RouteToSynchronous":
        raise SystemExit("The gateway must default to the synchronous path.")

    start_flow = flows.get(start.findtext(f"{{{BPMN}}}outgoing"))
    async_branch = flows.get("Flow_RouteToAsyncAfter")
    sync_branch = flows.get("Flow_RouteToSynchronous")
    if start_flow is None or start_flow.get("targetRef") != "RoutePath":
        raise SystemExit("The start event must flow to RoutePath.")
    if (
        async_branch is None
        or async_branch.get("sourceRef") != "RoutePath"
        or async_branch.get("targetRef") != "WaitStateAsyncAfter"
    ):
        raise SystemExit("RoutePath must have an outgoing branch to WaitStateAsyncAfter.")
    condition = async_branch.findtext(f"{{{BPMN}}}conditionExpression")
    if condition is None or "useAsyncAfterPath" not in condition:
        raise SystemExit("The asyncAfter path must use the useAsyncAfterPath condition.")
    if (
        sync_branch is None
        or sync_branch.get("sourceRef") != "RoutePath"
        or sync_branch.get("targetRef") != "WaitStateSynchronous"
    ):
        raise SystemExit("RoutePath must have a default branch to WaitStateSynchronous.")
    if async_wait.get(f"{{{CAMUNDA}}}asyncAfter") != "true":
        raise SystemExit("WaitStateAsyncAfter must have camunda:asyncAfter=true.")
    if sync_wait.get(f"{{{CAMUNDA}}}asyncAfter") is not None:
        raise SystemExit("WaitStateSynchronous must not have camunda:asyncAfter.")
    if delegate.tag != f"{{{BPMN}}}serviceTask":
        raise SystemExit("SynchronousDelegate must be a BPMN serviceTask.")
    if delegate.get(f"{{{CAMUNDA}}}class") != "org.camunda.example.SynchronousDelegate":
        raise SystemExit("Both paths must reach the fixture JavaDelegate.")
    if delegate.get(f"{{{CAMUNDA}}}asyncBefore") is not None:
        raise SystemExit("The delegate must not introduce a separate asyncBefore boundary.")
    if delegate.get(f"{{{CAMUNDA}}}asyncAfter") is not None:
        raise SystemExit("The delegate must not introduce an asyncAfter boundary after itself.")

    for activity in (async_wait, sync_wait):
        outgoing_id = activity.findtext(f"{{{BPMN}}}outgoing")
        outgoing = flows.get(outgoing_id)
        if outgoing is None or outgoing.get("targetRef") != "SynchronousDelegate":
            raise SystemExit(f"{activity.get('id')} must flow directly to the JavaDelegate.")

    print("PASS: both gateway paths reach the JavaDelegate; only one has an asyncAfter boundary.")


if __name__ == "__main__":
    main()
