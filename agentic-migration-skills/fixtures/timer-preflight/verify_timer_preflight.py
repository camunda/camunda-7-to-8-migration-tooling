#!/usr/bin/env python3
"""Check the timer-preflight fixture inputs."""

from collections import defaultdict
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parent
MAVEN = "http://maven.apache.org/POM/4.0.0"
BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL"


def require(condition, message):
    if not condition:
        print(f"FAIL: {message}", file=sys.stderr)
        raise SystemExit(1)


def check_deployment_set():
    pom = ET.parse(ROOT / "deployment-set/pom.xml").getroot()
    modules = [
        module.text
        for module in pom.findall(f"./{{{MAVEN}}}modules/{{{MAVEN}}}module")
    ]
    require(
        modules == ["loan-module", "order-module"],
        "the Maven reactor must contain the loan and order modules",
    )

    definitions = defaultdict(list)
    recurring_starts = []
    for module in modules:
        resources = ROOT / "deployment-set" / module / "src/main/resources"
        models = sorted(resources.glob("*.bpmn"))
        require(models, f"{module} must contain a BPMN resource")
        for model in models:
            document = ET.parse(model).getroot()
            for process in document.findall(f".//{{{BPMN}}}process"):
                process_id = process.get("id")
                require(process_id, f"{model.name} has a process without an ID")
                definitions[process_id].append((module, model.name))
                for start in process.findall(f".//{{{BPMN}}}startEvent"):
                    timer = start.find(f"./{{{BPMN}}}timerEventDefinition")
                    cycle = (
                        timer.find(f"./{{{BPMN}}}timeCycle")
                        if timer is not None
                        else None
                    )
                    if cycle is not None:
                        recurring_starts.append(
                            (module, process_id, start.get("id"), cycle.text)
                        )

    require(
        definitions["Sample"]
        == [
            ("loan-module", "loan-cycle.bpmn"),
            ("order-module", "order-cycle.bpmn"),
        ],
        "both modules must define process ID Sample",
    )
    require(
        recurring_starts
        == [("loan-module", "Sample", "recurringStart", "R/PT5S")],
        "the loan module must define one recurring R/PT5S timer start",
    )

    starter = (ROOT / "deployment-set/starter-call/SampleStarter.java").read_text(
        encoding="utf-8"
    )
    require(
        '.bpmnProcessId("Sample")' in starter and ".latestVersion()" in starter,
        "the starter call must select Sample by latest version",
    )


def check_active_timer_update():
    model = ET.parse(ROOT / "active-timer-update/project-termination.bpmn").getroot()
    process = model.find(f".//{{{BPMN}}}process")
    require(
        process is not None and process.get("id") == "projectTermination",
        "the active-timer model must define projectTermination",
    )
    timer = process.find(f".//{{{BPMN}}}boundaryEvent")
    time_date = (
        timer.find(f".//{{{BPMN}}}timeDate") if timer is not None else None
    )
    require(
        timer is not None
        and timer.get("id") == "terminationDeadline"
        and time_date is not None
        and time_date.text == "${terminationDate}",
        "the model must link terminationDeadline to the termination date",
    )

    source_root = ROOT / "active-timer-update/src/main/java/org/example"
    updater = (source_root / "TimerDueDateUpdater.java").read_text(
        encoding="utf-8"
    )
    delegate = (
        source_root / "ChangeProjectTerminationDateDelegate.java"
    ).read_text(encoding="utf-8")
    administration = (
        source_root / "ProjectAdministrationService.java"
    ).read_text(encoding="utf-8")
    require(
        "setJobDuedate" in updater,
        "the helper must contain the Camunda 7 timer due-date update",
    )
    require(
        delegate.count("updateForProcessInstance(") == 2,
        "the date-change delegate must call the helper twice",
    )
    require(
        administration.count("updateForProcessInstance(") == 1,
        "the administration service must call the helper once",
    )


def main():
    try:
        check_deployment_set()
        check_active_timer_update()
    except ET.ParseError as error:
        print(f"FAIL: invalid BPMN or Maven XML: {error}", file=sys.stderr)
        return 1

    print(
        "PASS: duplicate Sample IDs, recurring timer start, latestVersion caller, "
        "active timer, and all helper call sites are present"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
