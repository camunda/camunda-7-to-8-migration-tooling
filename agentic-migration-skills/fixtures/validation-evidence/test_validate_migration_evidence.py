"""Category-scoped regressions for recorded migration evidence."""

import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import uuid
import zipfile
from argparse import Namespace
from contextlib import nullcontext, redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import call, patch


FIXTURE = Path(__file__).resolve().parent
SCRIPT_DIR = FIXTURE.parents[1] / "skills" / "migrate-c7-to-c8-code" / "scripts"
sys.path.insert(0, str(SCRIPT_DIR))
import validate_migration_evidence as gate  # noqa: E402
import run_live_timer_fixture as runner  # noqa: E402


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def migrated_test_rows(*test_ids):
    return [
        {"c7_id": test_id, "status": "migrated", "c8_ids": [test_id]}
        for test_id in test_ids
    ]


def c7_baseline_with_coverage(coverage):
    return {
        "suites": [
            {
                "module": "app",
                "suite": "unit",
                "result": "passed",
                "test_results": {},
                "coverage_available": True,
                "coverage_by_process": coverage,
            }
        ],
        "coverage_available": True,
        "coverage": coverage,
    }


def bpmn(process_id, timer=False, extra=""):
    start = (
        '<bpmn:timerEventDefinition><bpmn:timeCycle>R/PT1H</bpmn:timeCycle>'
        "</bpmn:timerEventDefinition>"
        if timer
        else ""
    )
    return (
        '<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" '
        'xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" '
        'xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" '
        'xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" '
        'xmlns:di="http://www.omg.org/spec/DD/20100524/DI" '
        'id="Definitions" targetNamespace="http://camunda.io/schema/1.0/bpmn">'
        f'<bpmn:process id="{process_id}" isExecutable="true">'
        f'<bpmn:startEvent id="Start">{start}</bpmn:startEvent>'
        '<bpmn:endEvent id="End" />'
        '<bpmn:sequenceFlow id="Flow" sourceRef="Start" targetRef="End" />'
        f"{extra}</bpmn:process>"
        '<bpmndi:BPMNDiagram id="Diagram"><bpmndi:BPMNPlane id="Plane" '
        f'bpmnElement="{process_id}">'
        '<bpmndi:BPMNShape id="Start_di" bpmnElement="Start">'
        '<dc:Bounds x="100" y="100" width="36" height="36" />'
        "</bpmndi:BPMNShape>"
        '<bpmndi:BPMNShape id="End_di" bpmnElement="End">'
        '<dc:Bounds x="250" y="100" width="36" height="36" />'
        "</bpmndi:BPMNShape>"
        '<bpmndi:BPMNEdge id="Flow_di" bpmnElement="Flow">'
        '<di:waypoint x="136" y="118" /><di:waypoint x="250" y="118" />'
        "</bpmndi:BPMNEdge></bpmndi:BPMNPlane></bpmndi:BPMNDiagram>"
        "</bpmn:definitions>"
    )


def bpmn_with_job_types(process_id, *job_types):
    tasks = "".join(
        (
            f'<bpmn:serviceTask id="Worker{index}"><bpmn:extensionElements>'
            f'<zeebe:taskDefinition type="{job_type}" />'
            "</bpmn:extensionElements></bpmn:serviceTask>"
        )
        for index, job_type in enumerate(job_types, start=1)
    )
    return bpmn(process_id, extra=tasks)


def message_rearm_bpmn():
    return """<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
      xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions">
      <bpmn:message id="DateChangedMessage" name="DueDateChanged">
        <bpmn:extensionElements><zeebe:subscription correlationKey="=projectId" /></bpmn:extensionElements>
      </bpmn:message>
      <bpmn:process id="p-parent" isExecutable="true">
        <bpmn:startEvent id="Start"><bpmn:outgoing>Start_Prepare</bpmn:outgoing></bpmn:startEvent>
        <bpmn:exclusiveGateway id="Prepare">
          <bpmn:incoming>Start_Prepare</bpmn:incoming><bpmn:incoming>Update_Prepare</bpmn:incoming>
          <bpmn:outgoing>Prepare_Call</bpmn:outgoing>
        </bpmn:exclusiveGateway>
        <bpmn:callActivity id="DeadlineWaitCall">
          <bpmn:extensionElements>
            <zeebe:calledElement processId="p" propagateAllChildVariables="false" />
            <zeebe:ioMapping><zeebe:input source="=dueDate" target="dueDate" /></zeebe:ioMapping>
          </bpmn:extensionElements>
          <bpmn:incoming>Prepare_Call</bpmn:incoming><bpmn:outgoing>Call_End</bpmn:outgoing>
        </bpmn:callActivity>
        <bpmn:boundaryEvent id="DateChanged" attachedToRef="DeadlineWaitCall" cancelActivity="true">
          <bpmn:extensionElements><zeebe:ioMapping>
            <zeebe:output source="=updatedDueDate" target="dueDate" />
          </zeebe:ioMapping></bpmn:extensionElements>
          <bpmn:outgoing>Update_Prepare</bpmn:outgoing>
          <bpmn:messageEventDefinition messageRef="DateChangedMessage" />
        </bpmn:boundaryEvent>
        <bpmn:endEvent id="End"><bpmn:incoming>Call_End</bpmn:incoming></bpmn:endEvent>
        <bpmn:sequenceFlow id="Start_Prepare" sourceRef="Start" targetRef="Prepare" />
        <bpmn:sequenceFlow id="Prepare_Call" sourceRef="Prepare" targetRef="DeadlineWaitCall" />
        <bpmn:sequenceFlow id="Call_End" sourceRef="DeadlineWaitCall" targetRef="End" />
        <bpmn:sequenceFlow id="Update_Prepare" sourceRef="DateChanged" targetRef="Prepare" />
      </bpmn:process>
      <bpmn:process id="p" isExecutable="true">
        <bpmn:startEvent id="TimerStart"><bpmn:outgoing>TimerStart_Timer</bpmn:outgoing></bpmn:startEvent>
        <bpmn:intermediateCatchEvent id="Timer">
          <bpmn:incoming>TimerStart_Timer</bpmn:incoming><bpmn:outgoing>Timer_End</bpmn:outgoing>
          <bpmn:timerEventDefinition><bpmn:timeDate>=dueDate</bpmn:timeDate></bpmn:timerEventDefinition>
        </bpmn:intermediateCatchEvent>
        <bpmn:endEvent id="TimerEnd"><bpmn:incoming>Timer_End</bpmn:incoming></bpmn:endEvent>
        <bpmn:sequenceFlow id="TimerStart_Timer" sourceRef="TimerStart" targetRef="Timer" />
        <bpmn:sequenceFlow id="Timer_End" sourceRef="Timer" targetRef="TimerEnd" />
      </bpmn:process>
    </bpmn:definitions>"""


class ValidationEvidenceTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.plan = {
            "schema_version": 1,
            "modules": [
                {
                    "path": "app",
                    "runtime_mode": "none",
                    "test_suites": [{"name": "unit", "requires_docker": False}],
                }
            ],
            "models": [
                {
                    "source_path": "models/process.bpmn",
                    "path": "models/converted-c8-process.bpmn",
                    "processes": [{"id": "p", "standalone": True, "scenarios": ["normal"]}],
                }
            ],
            "deployment_sets": [
                {"name": "shared", "modules": ["app"],
                 "models": ["models/converted-c8-process.bpmn"]}
            ],
            "checks": [],
        }
        self.write_scope()

    def write_scope(self, timer=False, extra="", test_run_mode=None):
        inventory = {
            "schema_version": 1,
            "modules": [module["path"] for module in self.plan["modules"]],
            "models": [model["source_path"] for model in self.plan["models"]],
        }
        if test_run_mode is not None:
            inventory["test_run_mode"] = test_run_mode
        if test_run_mode == "migrate_only":
            test_module = inventory["modules"][0]
            test_id = f"{test_module}:com.example.Question8Test#testMigration"
            test_file = (
                f"{test_module}/src/test/java/com/example/Question8Test.java"
            )
            inventory["test_suites"] = [
                {
                    "module": test_module,
                    "name": "unit",
                    "command": ["mvn", "-B", "-pl", test_module, "test"],
                    "test_ids": [test_id],
                }
            ]
        write_json(self.root / gate.INVENTORY, inventory)
        write_json(self.root / gate.EVIDENCE, self.plan)
        for module in self.plan["modules"]:
            (self.root / module["path"]).mkdir(parents=True, exist_ok=True)
        if test_run_mode == "migrate_only":
            test_path = self.root / test_file
            test_path.parent.mkdir(parents=True, exist_ok=True)
            test_path.write_text("class Question8Test {}\n", encoding="utf-8")
            (self.root / gate.REPORT).write_text(
                "# Migration report\n\n"
                "## Test Inventory\n\n"
                "| Test ID | File | Test kind | Signals | Models | Handling | Notes |\n"
                "|---|---|---|---|---|---|---|\n"
                f"| `{test_id}` | `{test_file}` | process test | ProcessEngineRule "
                "| order.bpmn | Migrate | — |\n",
                encoding="utf-8",
            )
        for model in self.plan["models"]:
            xml = bpmn(model["processes"][0]["id"], timer=timer, extra=extra)
            for name in (model["source_path"], model["path"]):
                path = self.root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(xml, encoding="utf-8")
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root))

    def write_reports_command(self, files, counter=None):
        script = (
            "import json, sys\n"
            "from pathlib import Path\n"
            "payload = json.loads(sys.argv[1])\n"
            "count = 1\n"
            "if payload.get('counter'):\n"
            "    counter = Path(payload['counter'])\n"
            "    count = int(counter.read_text()) + 1 if counter.exists() else 1\n"
            "    counter.write_text(str(count))\n"
            "for name, content in payload['files'].items():\n"
            "    path = Path(name)\n"
            "    path.parent.mkdir(parents=True, exist_ok=True)\n"
            "    if isinstance(content, list):\n"
            "        content = content[min(count - 1, len(content) - 1)]\n"
            "    path.write_text(content, encoding='utf-8')\n"
        )
        payload = {"files": files, "counter": counter}
        return [sys.executable, "-c", script, json.dumps(payload)]

    def configure_test_run(
        self,
        c7_junit,
        c7_coverage=None,
        *,
        test_file_path="app/src/test/java/com/example/OrderTest.java",
        test_handling="Migrate",
        test_run_mode=None,
        test_source_roots=None,
        test_resource_roots=None,
    ):
        if test_run_mode is None and test_handling.casefold().startswith("migrate"):
            test_run_mode = "run"
        self.c7_test_id = "app:com.example.OrderTest#testOrder"
        self.c8_test_id = "app:com.example.OrderCptTest#testOrder"
        self.c7_test_file_path = test_file_path
        c7_file = self.root / test_file_path
        c7_file.parent.mkdir(parents=True, exist_ok=True)
        c7_file.write_text("class OrderTest {}\n", encoding="utf-8")
        resource = self.root / "app/src/test/resources/order.bpmn"
        resource.parent.mkdir(parents=True, exist_ok=True)
        resource.write_text("test resource\n", encoding="utf-8")
        baseline_reports = {
            "app/target/surefire-reports/TEST-com.example.OrderTest.xml": c7_junit,
        }
        if c7_coverage is not None:
            baseline_reports[
                "app/target/process-test-coverage/OrderTest/report.json"
            ] = c7_coverage
        self.c7_command = self.write_reports_command(baseline_reports)

        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        if test_run_mode is None:
            inventory.pop("test_run_mode", None)
        else:
            inventory["test_run_mode"] = test_run_mode
        suite = {
            "module": "app",
            "name": "unit",
            "command": self.c7_command,
            "test_ids": [self.c7_test_id],
            "reports": ["target/surefire-reports/TEST-*.xml"],
        }
        if test_source_roots is not None:
            suite["test_source_roots"] = test_source_roots
        if test_resource_roots is not None:
            suite["test_resource_roots"] = test_resource_roots
        inventory["test_suites"] = [suite]
        write_json(self.root / gate.INVENTORY, inventory)
        (self.root / gate.REPORT).write_text(
            "# Migration report\n\n"
            "## Test Inventory\n\n"
            "| Test ID | File | Test kind | Signals | Models | Handling | Notes |\n"
            "|---|---|---|---|---|---|---|\n"
            f"| `{self.c7_test_id}` | `{test_file_path}` "
            f"| process test | ProcessEngineRule | order.bpmn | {test_handling} | — |\n",
            encoding="utf-8",
        )
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root, reset_source_snapshot=True))

    def record_c7_baseline(self):
        return self.submit(
            ("module", "app", "c7_baseline", "unit"),
            command=self.c7_command,
        )

    def map_test_to_cpt(self, mocks_c7=None, mocks_c8=None, *, cpt_file_path=None):
        mapping = gate.read_test_mapping(self.root, required=True)
        test = next(
            item for item in mapping["tests"] if item.get("c7_id") == self.c7_test_id
        )
        test.update(
            status="migrated",
            c8_ids=[self.c8_test_id],
            mocks={
                "c7": mocks_c7 or [],
                "c8": mocks_c8 or [],
            },
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)
        old_file = self.root / self.c7_test_file_path
        old_file.unlink()
        c8_file = self.root / (
            cpt_file_path or "app/src/test/java/com/example/OrderCptTest.java"
        )
        c8_file.parent.mkdir(parents=True, exist_ok=True)
        c8_file.write_text("class OrderCptTest {}\n", encoding="utf-8")

    def add_report_only_inventory_test(self, test_id, file_path, test_kind):
        source_file = self.root / file_path
        source_file.parent.mkdir(parents=True, exist_ok=True)
        source_file.write_text("", encoding="utf-8")
        (self.root / gate.REPORT).write_text(
            "# Migration report\n\n"
            "## Test Inventory\n\n"
            "| Test ID | File | Test kind | Handling |\n"
            "|---|---|---|---|\n"
            f"| `{self.c7_test_id}` | `{self.c7_test_file_path}` | process test | Migrate |\n"
            f"| `{test_id}` | `{file_path}` | {test_kind} | Report only |\n",
            encoding="utf-8",
        )
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root, reset_source_snapshot=True))

    def cpt_command(self, first_junit=None, second_junit=None, first_coverage=None, second_coverage=None):
        files = {
            "app/target/surefire-reports/TEST-com.example.OrderCptTest.xml": [
                first_junit or '<testsuite><testcase classname="com.example.OrderCptTest" name="testOrder" /></testsuite>',
                second_junit or first_junit or '<testsuite><testcase classname="com.example.OrderCptTest" name="testOrder" /></testsuite>',
            ],
        }
        if first_coverage is not None or second_coverage is not None:
            files["app/target/coverage-report/report.json"] = [
                first_coverage or '{"processCoverages":[]}',
                second_coverage or first_coverage or '{"processCoverages":[]}',
            ]
        return self.write_reports_command(files, counter=".test-run-count")

    def timer_observation(self, key):
        expected = gate.requirements(self.root, self.plan).timer_starts[key]
        return {
            "deployment": {
                "performed": True, "reference": "fixture-deployment",
                "environment": "local", "target_disposable": True,
                "target_version": "8.9.21",
            },
            "observation": {
                **{field: expected[field] for field in ("model_path", "process_id", "start_id", "cycle")},
                "instances_started": 1,
            },
            "cleanup": {"completed": True, "evidence_reference": "fixture-cleanup"},
        }

    def install_active_timer_decision(self, multiple_callers=False):
        source = self.root / "app" / "Timer.java"
        count = 2 if multiple_callers else 1
        source.write_text(
            "managementService.setJobDuedate(jobId, dueDate);\n" * count,
            encoding="utf-8",
        )
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root, reset_source_snapshot=True))
        snapshot = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        locations = list(snapshot["source_updates"]["app"])
        self.assertEqual(count, len(locations))
        source.write_text(
            "terminationDateUpdater.update(projectId, dueDate);\n" * count,
            encoding="utf-8",
        )
        model = "models/converted-c8-process.bpmn"
        self.plan["models"][0]["processes"] = [
            {"id": "p-parent", "standalone": True, "scenarios": ["normal"]},
            {"id": "p", "standalone": False, "scenarios": [], "covering_test": "rearm"},
        ]
        for path in ("models/process.bpmn", model):
            (self.root / path).write_text(message_rearm_bpmn(), encoding="utf-8")
        self.plan["active_timer_update_decision"] = {
            "status": "approved",
            "strategy": "message_rearm",
            "reference": "MIGRATION_REPORT.md#approved-rearm",
            "target_version": "8.9.21",
            "updates": [{
                "model_path": model,
                "process_id": "p",
                "rearm_process_id": "p-parent",
                "rearm_call_activity_id": "DeadlineWaitCall",
                "timer_id": "Timer",
                "message_name": "DueDateChanged",
                "correlation_key_variable": "projectId",
                "date_variable": "dueDate",
                "message_date_variable": "updatedDueDate",
                "caller_mappings": [
                    {
                        "module": "app",
                        "source_locations": [location],
                        "migrated_caller_location": f"app/Timer.java:{index}:1",
                    }
                    for index, location in enumerate(locations, start=1)
                ],
            }],
        }
        write_json(self.root / gate.EVIDENCE, self.plan)
        return ("timer", f"{model}#p#Timer", "active_instance_reschedule", None)

    def active_timer_observation(self, key):
        decision = gate.requirements(self.root, self.plan).active_timer_decisions[key[1]]
        mapping = {
            field: decision[field] for field in (
                "model_path", "process_id", "rearm_process_id", "rearm_call_activity_id",
                "timer_id", "strategy", "message_name", "correlation_key_variable",
                "date_variable", "message_date_variable",
            )
        }
        return {
            "deployment": {
                "performed": True, "reference": "fixture-deployment",
                "environment": "local", "target_disposable": True,
                "target_version": decision["target_version"],
            },
            "observation": {"active_timer": {
                **mapping,
                "updates": [
                    {"old_deadline": "2050-11-23T00:00:25Z",
                     "new_deadline": "2050-11-23T00:00:15Z",
                     "timer_active_before_update": True, "correlated": True,
                     "checked_after_old_deadline": "2050-11-23T00:00:25.500Z",
                     "old_deadline_fire_count": 0},
                    {"old_deadline": "2050-11-23T00:00:15Z",
                     "new_deadline": "2050-11-23T00:00:35Z",
                     "timer_active_before_update": True, "correlated": True,
                     "checked_after_old_deadline": "2050-11-23T00:00:15.500Z",
                     "old_deadline_fire_count": 0},
                ],
                "final_deadline_fire_count": 1,
                "final_deadline_last_active_at": "2050-11-23T00:00:30Z",
                "final_deadline_fired_at": "2050-11-23T00:00:35Z",
            }},
            "cleanup": {"completed": True, "evidence_reference": "fixture-cleanup"},
        }

    def sample_models(self, timer=False, isolated=False):
        self.plan["modules"] = [
            {"path": f"modules/{name}", "runtime_mode": "none",
             "test_suites": [{"name": "unit", "requires_docker": False}]}
            for name in ("a", "b")
        ]
        self.plan["models"] = [
            {"source_path": f"models/{name}.bpmn",
             "path": f"models/converted-c8-{name}.bpmn",
             "processes": [{"id": "Sample", "standalone": True, "scenarios": ["normal"]}]}
            for name in ("a", "b")
        ]
        self.plan["deployment_sets"] = (
            [
                {"name": name, "modules": [f"modules/{name}"],
                 "models": [f"models/converted-c8-{name}.bpmn"]}
                for name in ("a", "b")
            ]
            if isolated else
            [{"name": "shared", "modules": ["modules/a", "modules/b"],
              "models": [model["path"] for model in self.plan["models"]]}]
        )
        self.write_scope(timer=timer)

    def submit(self, key, action="run", command=None, **options):
        category, target, kind, scenario = key
        if category == "timer" and kind == "preflight" and action == "run" and command is None:
            observation = options.get("observation", self.timer_observation(key))
            command = [sys.executable, "-c", f"print({json.dumps(json.dumps(observation))})"]
        if category == "timer" and kind == "active_instance_reschedule" and action == "run" and command is None:
            observation = options.get("observation", self.active_timer_observation(key))
            command = [sys.executable, "-c", f"print({json.dumps(json.dumps(observation))})"]
        disposition = options.get("disposition")
        if action == "review" and category == "timer" and kind == "disposition" and "disposition" not in options:
            disposition = gate.requirements(self.root, self.plan).timer_starts[key]["disposition"]
        if action == "review" and kind == "active_timer_updates":
            decisions = [
                decision
                for decision in gate.requirements(self.root, self.plan).active_timer_decisions.values()
                if target in decision["modules"]
            ]
            disposition = options.get("disposition", "message_rearm" if decisions else "no_updates")
            if decisions:
                options.setdefault("reference", decisions[0]["reference"])
                options.setdefault(
                    "note",
                    "Reviewed "
                    + " ".join(
                        caller["migrated_caller_location"]
                        for decision in decisions
                        for caller in decision["caller_mappings"]
                        if caller["module"] == target
                    )
                    + " publishing DueDateChanged with projectId and updatedDueDate into dueDate.",
                )
        arguments = Namespace(
            type=category,
            target=target,
            kind=kind,
            scenario=scenario,
            environment=options.get("environment"),
            isolation_plan=options.get("isolation_plan"),
            timeout=5,
            action=action,
            command=[sys.executable, "-c", "print('check completed')"] if command is None else command,
            baseline_root=options.get("baseline_root"),
            note=options.get("note", "Reviewed the migration checklist and recorded decisions."),
            reason=options.get("reason", "Check could not run."),
            target_disposable=options.get(
                "target_disposable",
                category == "timer" and kind in ("preflight", "active_instance_reschedule"),
            ),
            target_version=options.get("target_version", "8.9.21"),
            reference=options.get("reference", "MIGRATION_REPORT.md#preflight"),
            disposition=disposition,
            non_timer_evidence_json=options.get("non_timer_evidence_json"),
        )
        with redirect_stdout(StringIO()):
            return gate.record(self.root, arguments)

    def maven_effective_pom_runner(self, effective_pom):
        def run(command, **kwargs):
            if "help:effective-pom" in command:
                output = next(
                    argument.partition("=")[2]
                    for argument in command
                    if argument.startswith("-Doutput=")
                )
                Path(output).write_text(effective_pom, encoding="utf-8")
                return subprocess.CompletedProcess(command, 0, "effective POM")
            return subprocess.CompletedProcess(command, 0, "compiled")

        return run

    def gradle_test_compile_runner(self, graph=None):
        app_root = self.root / "app"
        app_root.mkdir(parents=True, exist_ok=True)
        (self.root / "settings.gradle").write_text("include 'app'\n", encoding="utf-8")
        (app_root / "build.gradle").write_text("", encoding="utf-8")
        default_tasks = None
        if graph is None:
            default_tasks = (
                "compileJava",
                "processResources",
                "classes",
                "compileTestJava",
                "processTestResources",
                "testClasses",
            )

        def run(command, **kwargs):
            current_graph = graph
            if default_tasks is not None:
                project_dir_selected = any(
                    argument in ("-p", "--project-dir")
                    or argument.startswith(("-p=", "--project-dir="))
                    for argument in command
                )
                task_prefix = ":" if project_dir_selected else ":app:"
                current_graph = "\n".join(
                    f"> Task {task_prefix}{task} SKIPPED"
                    for task in default_tasks
                )
            task_records = []
            for line in (current_graph or "").splitlines():
                stripped = line.strip()
                if stripped.startswith("> Task "):
                    stripped = stripped[len("> Task "):]
                fields = stripped.split()
                if len(fields) >= 2 and fields[0].startswith(":") and fields[1] == "SKIPPED":
                    task_records.append(f"NWF-TASK\t{fields[0]}\tother")
            inspected_graph = "\n".join((current_graph or "", *task_records))
            output = inspected_graph if "--dry-run" in command else "compiled"
            return subprocess.CompletedProcess(command, 0, output)

        return run

    def gradle_task_graph_runner(self, tasks, archives=()):
        output = []
        for path, task_type in tasks:
            output.append(f"> Task {path} SKIPPED")
            output.append(f"NWF-TASK\t{path}\t{task_type}")
        for task, path in archives:
            output.append(f"NWF-ARCHIVE\t{task}\t{path}")
        graph = "\n".join(output)

        def run(command, **kwargs):
            result = graph if "--dry-run" in command else "verified"
            return subprocess.CompletedProcess(command, 0, result)

        return run

    def complete_required_checks(self):
        plan = gate.requirements(self.root, self.plan)
        self.assertEqual([], plan.issues)
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        test_run_mode = gate.read_test_run_mode(inventory)
        priorities = {
            "project": 0,
            "module": 1,
            "deployment_set": 2,
            "timer": 3,
            "model": 4,
            "test": 5,
            "process": 6,
        }
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        check_issues = []
        recorded = gate.load_checks(self.root, evidence, plan, check_issues)
        self.assertEqual([], check_issues)
        for key in sorted(
            plan.required,
            key=lambda item: (
                5 if item[0] == "timer" and item[2] == "active_instance_reschedule"
                else priorities[item[0]],
                item[1],
                {"lint": 0, "review": 1, "deployment": 2, "worker_input_inventory": 0}.get(item[2], 3),
                item[2],
                item[3] or "",
            ),
        ):
            if key in recorded:
                check = recorded[key][1]
                expected_digest = gate.expected_check_digest(self.root, plan, key)
                current = (
                    expected_digest is None
                    or check.get("source_digest") == expected_digest
                )
                if key[2] in gate.TEST_LEDGER_CHECK_KINDS:
                    mapping = gate.read_test_mapping(self.root)
                    digest_kind = (
                        "freeze"
                        if key[2] == "test_freeze"
                        else "review"
                        if key[2] in ("assertion_strength", "mock_boundary")
                        else "all"
                    )
                    current = current and check.get("test_mapping_digest") == gate.test_mapping_digest(
                        mapping, digest_kind
                    )
                if current:
                    continue
            if key == ("project", ".", "docker_info", None):
                continue
            if test_run_mode == "migrate_only" and key[2] in gate.TEST_EXECUTION_KINDS:
                self.assertEqual(
                    1,
                    self.submit(
                        key,
                        action="block",
                        reason="declined by user (Question 8)",
                    ),
                )
                continue
            environment = (
                "local" if key[0] in ("timer", "process") or key[2] in (*gate.RUNTIME_CHECKS, "deployment")
                else None
            )
            options = {
                "environment": environment,
                "isolation_plan": "Use an isolated local cluster; remove the timer deployment and instances.",
            }
            command_patch = nullcontext()
            if test_run_mode == "migrate_only" and key[0] == "module" and key[2] == "compile":
                options["command"] = ["mvn", "-pl", "app", "test-compile"]
                command_patch = patch.object(
                    gate.subprocess,
                    "run",
                    side_effect=self.maven_effective_pom_runner(
                        "<project><build><plugins /></build></project>"
                    ),
                )
            elif key[2] == "c7_baseline":
                options["command"] = plan.test_contract["suites"][
                    (key[1], key[3])
                ]["command"]
            elif key[2] == "test_repeat":
                options["command"] = self.cpt_command(
                    first_coverage='{"processCoverages":[]}'
                )
            elif plan.required[key] in ("snapshot", "computed"):
                options["command"] = []
            elif key[2] == "assertion_strength":
                options["note"] = f"Reviewed assertions for {key[1]}."
            elif key[2] == "mock_boundary":
                options["note"] = f"Reviewed C7 test {key[1]} and its CPT mocks."
            elif test_run_mode == "migrate_only" and key[0] == "model" and key[2] == "lint":
                options["command"] = ["npx", "bpmnlint", key[1]]
                command_patch = patch.object(
                    gate.subprocess,
                    "run",
                    return_value=gate.subprocess.CompletedProcess(
                        options["command"], 0, "linted"
                    ),
                )
            elif test_run_mode == "migrate_only" and key[0] == "model" and key[2] == "deployment":
                options["command"] = ["c8ctl", "deploy", key[1]]
                command_patch = patch.object(
                    gate.subprocess,
                    "run",
                    return_value=gate.subprocess.CompletedProcess(
                        options["command"], 0, "deployed"
                    ),
                )
            with command_patch:
                self.assertEqual(
                    0,
                    self.submit(
                        key,
                        action="review" if plan.required[key] == "review" else "run",
                        **options,
                    ),
                )
            evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
            check_issues = []
            recorded = gate.load_checks(self.root, evidence, plan, check_issues)
            self.assertEqual([], check_issues)

    def summary(self):
        return json.loads((self.root / gate.SUMMARY).read_text(encoding="utf-8"))

    def audit(self):
        with redirect_stdout(StringIO()):
            return gate.report(self.root)

    def test_migrate_only_refuses_test_commands_and_requires_exact_block_reason(self):
        self.write_scope(test_run_mode="migrate_only")
        for key in (
            ("module", "app", "tests", "unit"),
            ("process", "models/converted-c8-process.bpmn#p", "process_path", "normal"),
        ):
            with self.subTest(key=key):
                with patch.object(gate.subprocess, "run") as command:
                    with self.assertRaisesRegex(gate.EvidenceError, "Migrate tests only"):
                        self.submit(key, command=[sys.executable, "-c", "print('must not run')"])
                    command.assert_not_called()
                for reason in ("tests deferred", " declined by user (Question 8)"):
                    with self.assertRaisesRegex(gate.EvidenceError, "exact Question 8 reason"):
                        self.submit(key, action="block", reason=reason)
                self.assertEqual(1, self.submit(key, action="block", reason=gate.QUESTION_8_DECLINE_REASON))

    def test_migrate_only_requires_test_source_compilation(self):
        self.write_scope(test_run_mode="migrate_only")
        key = ("module", "app", "compile", None)
        for command_args in (
            ["python3", "-c", "print('test-compile')", "test-compile"],
            ["mvn", "compile"],
            ["mvn", "test-compile"],
            ["mvn", "-pl", "other", "test-compile"],
            ["mvn", "-pl", "app,other", "test-compile"],
            ["mvn", "-pl", "app", "-am", "test-compile"],
            ["mvn", "-pl", "app", "-rf", "other", "test-compile"],
            ["gradle", "classes"],
            ["gradle", "testClasses"],
            ["gradle", ":other:testClasses"],
            ["mvn", "test-compile", "-Dmaven.test.skip=true"],
            ["mvn", "-D", "maven.test.skip", "test-compile"],
            ["mvn", "-pl", "app", "test-compile", "-Dmaven.test.skip=true"],
            ["mvn", "-pl", "app", "-D", "maven.test.skip", "test-compile"],
            ["mvn", "-pl", "app", "--define=maven.test.skip=true", "test-compile"],
            ["mvn", "-pl", "app", "test-compile", "test"],
            ["gradle", "--dry-run", ":app:testClasses"],
            ["gradle", "-m", ":app:testClasses"],
            ["gradle", "-x", ":app:compileTestJava", ":app:testClasses"],
            ["gradle", "--exclude-task", ":app:compileTestJava", ":app:testClasses"],
            ["gradle", "-x:app:compileTestJava", ":app:testClasses"],
            ["gradle", "-x", ":app:testClasses"],
            ["gradle", ":app:testClasses", ":app:test"],
        ):
            with self.subTest(command=command_args):
                completed = gate.subprocess.CompletedProcess(
                    command_args, 0, "unexpected pass"
                )
                with patch.object(
                    gate.subprocess, "run", return_value=completed
                ) as command:
                    with self.assertRaisesRegex(gate.EvidenceError, "test-source compilation"):
                        self.submit(key, command=command_args)
                    command.assert_not_called()
        safe_maven_pom = """
        <project><build><plugins>
          <plugin><groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-resources-plugin</artifactId>
            <executions>
              <execution><phase>process-resources</phase>
                <goals><goal>resources</goal></goals></execution>
              <execution><phase>process-test-resources</phase>
                <goals><goal>testResources</goal></goals></execution>
            </executions>
          </plugin>
          <plugin><groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <executions>
              <execution><phase>compile</phase>
                <goals><goal>compile</goal></goals></execution>
              <execution><phase>test-compile</phase>
                <goals><goal>testCompile</goal></goals></execution>
            </executions>
          </plugin>
          <plugin><groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <executions><execution><phase>test</phase>
              <goals><goal>test</goal></goals></execution></executions>
          </plugin>
        </plugins></build></project>
        """
        for command_args in (
            ["mvn", "-pl", "app", "test-compile"],
            ["mvn", "-f", "app/pom.xml", "test-compile"],
            ["mvn", "-pl", "app", "-Dmaven.test.skip=false", "test-compile"],
            ["mvn", "-pl", "app", "--define=maven.test.skip=false", "test-compile"],
            ["gradle", ":app:testClasses"],
            ["gradle", "-p", "app", "testClasses"],
        ):
            with self.subTest(command=command_args):
                runner = (
                    self.gradle_test_compile_runner()
                    if gate.build_tool(command_args) == "gradle"
                    else self.maven_effective_pom_runner(
                        safe_maven_pom
                    )
                )
                with patch.object(gate.subprocess, "run", side_effect=runner) as run:
                    self.assertEqual(0, self.submit(key, command=command_args))
                    self.assertEqual(2, run.call_count)
                    first_command = run.call_args_list[0].args[0]
                    if gate.build_tool(command_args) == "gradle":
                        self.assertIn("--dry-run", first_command)
                    else:
                        self.assertIn("help:effective-pom", first_command)

    def test_migrate_only_rejects_unclassified_maven_test_compile_executions(self):
        self.write_scope(test_run_mode="migrate_only")
        key = ("module", "app", "compile", None)
        effective_poms = (
            """
            <project><build><plugins><plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-surefire-plugin</artifactId>
              <executions><execution><phase>test-compile</phase>
                <goals><goal>test</goal></goals>
              </execution></executions>
            </plugin></plugins></build></project>
            """,
            """
            <project><build><plugins><plugin>
              <groupId>org.example</groupId>
              <artifactId>custom-test-runner</artifactId>
              <executions><execution><phase>validate</phase>
                <goals><goal>run</goal></goals>
              </execution></executions>
            </plugin></plugins></build></project>
            """,
            """
            <project><build><plugins><plugin>
              <groupId>org.example</groupId>
              <artifactId>custom-test-runner</artifactId>
              <executions><execution>
                <goals><goal>run</goal></goals>
              </execution></executions>
            </plugin></plugins></build></project>
            """,
            "<project><packaging>pom</packaging><build><plugins /></build></project>",
        )
        for effective_pom in effective_poms:
            with self.subTest(effective_pom=effective_pom):
                with patch.object(
                    gate.subprocess,
                    "run",
                    side_effect=self.maven_effective_pom_runner(effective_pom),
                ) as run:
                    with self.assertRaisesRegex(
                        gate.EvidenceError,
                        "Question 8|test-source compilation",
                    ):
                        self.submit(
                            key,
                            command=["mvn", "-pl", "app", "test-compile"],
                        )
                    self.assertEqual(1, run.call_count)
                    self.assertIn("help:effective-pom", run.call_args.args[0])

    def test_migrate_only_rejects_unknown_maven_default_phase_executions(self):
        self.write_scope(test_run_mode="migrate_only")
        key = ("module", "app", "compile", None)
        effective_pom = """
        <project><build><plugins><plugin>
          <groupId>org.example</groupId>
          <artifactId>custom-test-runner</artifactId>
          <executions><execution>
            <goals><goal>run</goal></goals>
          </execution></executions>
        </plugin></plugins></build></project>
        """
        command_args = ["mvn", "-pl", "app", "test-compile"]
        with patch.object(
            gate.subprocess,
            "run",
            side_effect=self.maven_effective_pom_runner(effective_pom),
        ) as run:
            with self.assertRaisesRegex(
                gate.EvidenceError,
                "without a known default phase|unclassified",
            ):
                self.submit(key, command=command_args)
            self.assertEqual(1, run.call_count)
            self.assertIn("help:effective-pom", run.call_args.args[0])

    def test_migrate_only_fails_closed_when_maven_effective_pom_is_unavailable(self):
        self.write_scope(test_run_mode="migrate_only")
        key = ("module", "app", "compile", None)
        command_args = ["mvn", "-pl", "app", "test-compile"]
        with patch.object(
            gate.subprocess,
            "run",
            return_value=subprocess.CompletedProcess(
                command_args,
                1,
                "help plugin unavailable",
            ),
        ) as run:
            with self.assertRaisesRegex(
                gate.EvidenceError,
                "could not inspect the effective Maven lifecycle",
            ):
                self.submit(key, command=command_args)
            self.assertEqual(1, run.call_count)

    def test_migrate_only_rejects_unclassified_gradle_test_compile_tasks(self):
        self.write_scope(test_run_mode="migrate_only")
        key = ("module", "app", "compile", None)
        graphs = (
            "> Task :app:compileTestJava SKIPPED\n"
            "> Task :app:testClasses SKIPPED\n"
            "> Task :app:customTestRunner SKIPPED\n",
            "> Task :app:compileTestJava SKIPPED\n"
            "> Task :app:testClasses SKIPPED\n"
            "> Task :app:generateTestSources SKIPPED\n",
            "> Task :app:classes SKIPPED\n"
            "> Task :app:testClasses SKIPPED\n",
            "",
        )
        for graph in graphs:
            with self.subTest(graph=graph):
                with patch.object(
                    gate.subprocess,
                    "run",
                    side_effect=self.gradle_test_compile_runner(graph),
                ) as run:
                    with self.assertRaisesRegex(
                        gate.EvidenceError,
                        "Migrate tests only|unclassified|test-source compiler|"
                        "could not classify|complete Gradle task graph|"
                        "unexcluded test tasks",
                    ):
                        self.submit(
                            key,
                            command=["gradle", ":app:testClasses"],
                        )
                    self.assertEqual(1, run.call_count)
                    self.assertIn("--dry-run", run.call_args.args[0])

    def test_migrate_only_rejects_gradle_test_compile_tasks_registered_as_test_tasks(self):
        self.write_scope(test_run_mode="migrate_only")
        (self.root / "settings.gradle").write_text("include 'app'\n", encoding="utf-8")
        (self.root / "app/build.gradle").write_text("", encoding="utf-8")
        key = ("module", "app", "compile", None)
        command_args = ["gradle", ":app:testClasses"]

        for test_task in (":app:compileTestJava", ":app:testClasses"):
            with self.subTest(test_task=test_task):
                task_graph = self.gradle_task_graph_runner(
                    (
                        (
                            ":app:compileTestJava",
                            "test" if test_task == ":app:compileTestJava" else "other",
                        ),
                        (
                            ":app:testClasses",
                            "test" if test_task == ":app:testClasses" else "other",
                        ),
                    )
                )
                with patch.object(gate.subprocess, "run", side_effect=task_graph) as run:
                    with self.assertRaisesRegex(
                        gate.EvidenceError,
                        "test execution|test task",
                    ):
                        self.submit(key, command=command_args)
                    self.assertEqual(1, run.call_count)
                    self.assertIn("--init-script", run.call_args.args[0])

    def test_migrate_only_rejects_maven_test_skip_from_jvm_and_project_options(self):
        self.write_scope(test_run_mode="migrate_only")
        key = ("module", "app", "compile", None)
        jvm_config = self.root / ".mvn/jvm.config"
        jvm_config.parent.mkdir(parents=True)
        jvm_config.write_text("-Dmaven.test.skip=true\n", encoding="utf-8")
        with self.assertRaisesRegex(gate.EvidenceError, "test-source compilation"):
            self.submit(key, command=["mvn", "-pl", "app", "test-compile"])
        jvm_config.unlink()

        for name in ("MAVEN_OPTS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS"):
            with self.subTest(environment=name):
                with patch.dict(gate.os.environ, {name: "-Dmaven.test.skip=true"}):
                    with self.assertRaisesRegex(gate.EvidenceError, "test-source compilation"):
                        self.submit(key, command=["mvn", "-pl", "app", "test-compile"])

        with patch.dict(gate.os.environ, {"MAVEN_ARGS": "test"}):
            with self.assertRaisesRegex(gate.EvidenceError, "test-source compilation"):
                self.submit(key, command=["mvn", "-pl", "app", "test-compile"])

        pom = self.root / "app/pom.xml"
        for value in ("true", "${skipTests}"):
            with self.subTest(pom_value=value):
                pom.write_text(
                    "<project><properties><maven.test.skip>"
                    f"{value}</maven.test.skip></properties></project>\n",
                    encoding="utf-8",
                )
                with self.assertRaisesRegex(gate.EvidenceError, "test-source compilation"):
                    self.submit(key, command=["mvn", "-pl", "app", "test-compile"])
        pom.write_text(
            "<project><properties><maven.test.skip>false</maven.test.skip>"
            "</properties></project>\n",
            encoding="utf-8",
        )
        completed = gate.subprocess.CompletedProcess(
            ["mvn", "-pl", "app", "test-compile"], 0, "compiled"
        )
        with patch.object(
            gate.subprocess,
            "run",
            side_effect=self.maven_effective_pom_runner(
                "<project><build><plugins /></build></project>"
            ),
        ):
            self.assertEqual(
                0,
                self.submit(key, command=["mvn", "-pl", "app", "test-compile"]),
            )

        maven_config = self.root / ".mvn/maven.config"
        maven_config.write_text("test\n", encoding="utf-8")
        with self.assertRaisesRegex(gate.EvidenceError, "test-source compilation"):
            self.submit(key, command=["mvn", "-pl", "app", "test-compile"])

    def test_migrate_only_rejects_effective_maven_test_skip_property(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_run_mode="migrate_only",
        )
        key = ("module", "app", "compile", None)
        effective_poms = (
            (
                "inherited parent property",
                """
                <project><properties><maven.test.skip>true</maven.test.skip></properties>
                  <build><plugins /></build></project>
                """,
            ),
            (
                "active settings profile property",
                """
                <project><properties><maven.test.skip>true</maven.test.skip></properties>
                  <build><plugins /></build></project>
                """,
            ),
        )
        for source, effective_pom in effective_poms:
            with self.subTest(source=source):
                with patch.object(
                    gate.subprocess,
                    "run",
                    side_effect=self.maven_effective_pom_runner(effective_pom),
                ):
                    with self.assertRaisesRegex(
                        gate.EvidenceError,
                        "test-source compilation|maven.test.skip",
                    ):
                        self.submit(
                            key,
                            command=["mvn", "-pl", "app", "test-compile"],
                        )
        with patch.object(
            gate.subprocess,
            "run",
            side_effect=self.maven_effective_pom_runner(
                """
                <project><properties><maven.test.skip>false</maven.test.skip></properties>
                  <build><plugins /></build></project>
                """
            ),
        ):
            self.assertEqual(
                0,
                self.submit(
                    key,
                    command=["mvn", "-pl", "app", "test-compile"],
                ),
            )

    def test_migrate_only_rejects_test_commands_under_non_test_keys(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        self.write_scope(test_run_mode="migrate_only")
        cases = (
            (("module", "app", "spring_boot_run", None), ["mvn", "-pl", "app", "test"]),
            (
                ("module", "app", "spring_boot_run", None),
                ["mvn", "-pl", "app", "com.example:spring-boot-maven-plugin:1.0:run"],
            ),
            (
                ("module", "app", "spring_boot_run", None),
                [
                    "mvn",
                    "-pl",
                    "app",
                    "org.springframework.boot:custom-spring-boot-maven-plugin:1.0:run",
                ],
            ),
            (
                ("module", "app", "spring_boot_run", None),
                [
                    "mvn",
                    "-pl",
                    "app",
                    "org.springframework.boot:spring-boot-maven-plugin:1.0:RUN",
                ],
            ),
            (("module", "app", "spring_boot_run", None), ["gradle", ":app:integrationTest"]),
            (("module", "app", "spring_boot_run", None), ["gradle", "--dry-run", ":app:bootRun"]),
            (("module", "app", "spring_boot_run", None), ["gradle", "-m", ":app:bootRun"]),
            (("module", "app", "executable_jar", None), ["./mvnw", "-pl", "app", "verify"]),
            (("module", "app", "spring_boot_run", None), ["mvnDebug", "-pl", "app", "test"]),
            (("module", "app", "spring_boot_run", None), ["env", "mvn", "-pl", "app", "test"]),
            (("module", "app", "spring_boot_run", None), ["cargo", "test"]),
            (("module", "app", "spring_boot_run", None), ["pnpm", "test"]),
            (("module", "app", "spring_boot_run", None), ["mvn", "-pl", "app", "custom:run-tests"]),
            (("module", "app", "spring_boot_run", None), ["mvn", "-pl", "app", "prepare-package"]),
            (("module", "app", "spring_boot_run", None), ["mvn", "-pl", "app", "pre-integration-test"]),
            (("module", "app", "spring_boot_run", None), ["mvn", "-pl", "app", "post-integration-test"]),
            (("module", "app", "spring_boot_run", None), ["sh", "-c", "mvn -pl app test"]),
            (("module", "app", "spring_boot_run", None), ["python3", "-m", "unittest"]),
            (("module", "app", "spring_boot_run", None), ["python3", "scripts/test_helper.py"]),
            (("module", "app", "spring_boot_run", None), ["npm", "test"]),
            (("module", "app", "spring_boot_run", None), ["java", "org.junit.platform.console.ConsoleLauncher"]),
            (("module", "app", "executable_jar", None), ["mvn", "-pl", "app", "package"]),
            (
                ("module", "app", "executable_jar", None),
                ["mvn", "-pl", "app", "package", "-Dmaven.test.skip=true"],
            ),
            (("module", "app", "executable_jar", None), ["gradle", ":app:bootJar"]),
            (("module", "app", "executable_jar", None), ["gradle", ":app:build"]),
            (("module", "app", "executable_jar", None), ["gradle", ":app:bootRun"]),
            (
                ("module", "app", "executable_jar", None),
                ["gradle", ":app:bootJar", ":app:customTask", "-x", "test"],
            ),
            (
                ("module", "app", "executable_jar", None),
                ["gradle", ":app:bootJar", "-x", "test", "--dry-run"],
            ),
            (
                ("module", "app", "executable_jar", None),
                ["gradle", "-m", ":app:bootJar", "-x", "test"],
            ),
            (("module", "app", "spring_boot_run", None), ["gradle", ":other:bootRun"]),
            (("module", "app", "spring_boot_run", None), ["mvn", "-pl", "other", "spring-boot:run"]),
            (
                ("module", "app", "spring_boot_run", None),
                ["mvn", "-pl", "app", "spring-boot:run", "-DskipTests"],
            ),
            (("module", "app", "spring_boot_run", None), ["gradle", ":app:bootRun", "-x", "test"]),
            (
                ("module", "app", "executable_jar", None),
                ["gradle", ":other:bootJar", "-x", "test"],
            ),
            (
                ("module", "app", "executable_jar", None),
                ["mvn", "-pl", "other", "package", "-DskipTests"],
            ),
        )
        for key, command_args in cases:
            with self.subTest(key=key, command=command_args):
                completed = gate.subprocess.CompletedProcess(
                    command_args, 0, "unexpected pass"
                )
                with patch.object(
                    gate.subprocess, "run", return_value=completed
                ) as command:
                    with self.assertRaisesRegex(gate.EvidenceError, "Migrate tests only"):
                        self.submit(
                            key,
                            command=command_args,
                            environment="local",
                        )
                    command.assert_not_called()
        with patch.dict(gate.os.environ, {"MAVEN_ARGS": "test"}):
            with patch.object(gate.subprocess, "run") as command:
                with self.assertRaisesRegex(gate.EvidenceError, "MAVEN_ARGS"):
                    self.submit(
                        ("module", "app", "spring_boot_run", None),
                        command=["mvn", "-pl", "app", "spring-boot:run"],
                        environment="local",
                    )
                command.assert_not_called()

    def test_migrate_only_rejects_model_commands_for_a_different_file(self):
        self.write_scope(test_run_mode="migrate_only")
        cases = (
            (
                    ("model", "models/converted-c8-process.bpmn", "lint", None),
                    ["npx", "bpmnlint", "models/other.bpmn"],
            ),
            (
                    ("model", "models/converted-c8-process.bpmn", "lint", None),
                    ["c8ctl", "bpmn", "lint", "models/other.bpmn"],
            ),
            (
                    ("model", "models/converted-c8-process.bpmn", "deployment", None),
                    ["c8ctl", "deploy", "models/other.bpmn"],
            ),
        )
        for key, command_args in cases:
            with self.subTest(command=command_args):
                    with self.assertRaisesRegex(
                        gate.EvidenceError,
                        "Migrate tests only",
                    ):
                        gate.validate_migrate_only_command(
                            self.root,
                            key,
                            command_args,
                        )

    def test_migrate_only_allows_verified_runtime_and_packaging_commands(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        self.write_scope(test_run_mode="migrate_only")
        (self.root / "app/pom.xml").write_text("<project />\n", encoding="utf-8")
        (self.root / "settings.gradle").write_text("include 'app'\n", encoding="utf-8")
        (self.root / "app/build.gradle").write_text("", encoding="utf-8")
        safe_maven_pom = (
            "<project><packaging>jar</packaging>"
            "<build><plugins /></build></project>"
        )
        safe_gradle_tasks = (
            (":app:compileJava", "other"),
            (":app:classes", "other"),
            (":app:bootJar", "other"),
            (":app:test", "test"),
        )
        safe_gradle_project_dir_tasks = (
            (":compileJava", "other"),
            (":classes", "other"),
            (":bootJar", "other"),
            (":test", "test"),
        )
        cases = (
            (("module", "app", "spring_boot_run", None), ["mvn", "-pl", "app", "spring-boot:run"]),
            (
                ("module", "app", "spring_boot_run", None),
                ["mvn", "-pl", "app", "org.springframework.boot:spring-boot-maven-plugin:run"],
            ),
            (
                ("module", "app", "spring_boot_run", None),
                [
                    "mvn",
                    "-pl",
                    "app",
                    "org.springframework.boot:spring-boot-maven-plugin:3.5.0:run",
                ],
            ),
            (("module", "app", "spring_boot_run", None), ["gradle", ":app:bootRun"]),
            (("module", "app", "executable_jar", None), ["mvn", "-pl", "app", "package", "-DskipTests"]),
            (("module", "app", "executable_jar", None), ["gradle", ":app:bootJar", "-x", "test"]),
            (
                ("module", "app", "executable_jar", None),
                ["gradle", ":app:bootJar", "-x", ":app:test"],
            ),
            (("module", "app", "executable_jar", None), ["gradle", "-p", "app", "bootJar", "-x", "test"]),
        )
        for key, command_args in cases:
            with self.subTest(key=key, command=command_args):
                if "package" in command_args:
                    runner = self.maven_effective_pom_runner(safe_maven_pom)
                elif "bootRun" in " ".join(command_args):
                    runner = self.gradle_task_graph_runner(
                        (
                            (":app:compileJava", "other"),
                            (":app:classes", "other"),
                            (":app:bootRun", "other"),
                        )
                    )
                elif "bootJar" in " ".join(command_args):
                    tasks = (
                        safe_gradle_project_dir_tasks
                        if "-p" in command_args
                        else safe_gradle_tasks
                    )
                    runner = self.gradle_task_graph_runner(tasks)
                else:
                    runner = lambda command, **kwargs: subprocess.CompletedProcess(
                        command, 0, "verified"
                    )
                with patch.object(gate.subprocess, "run", side_effect=runner):
                    self.assertEqual(
                        0,
                        self.submit(key, command=command_args, environment="local"),
                    )

    def test_migrate_only_rejects_maven_packaging_with_unclassified_test_goal(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        self.write_scope(test_run_mode="migrate_only")
        (self.root / "app/pom.xml").write_text("<project />\n", encoding="utf-8")
        key = ("module", "app", "executable_jar", None)
        command_args = ["mvn", "-pl", "app", "package", "-DskipTests"]
        effective_poms = (
            """
            <project><build><plugins><plugin>
              <groupId>org.example</groupId>
              <artifactId>custom-test-runner</artifactId>
              <executions><execution><phase>test</phase>
                <goals><goal>run</goal></goals>
              </execution></executions>
            </plugin></plugins></build></project>
            """,
            """
            <project><build><plugins><plugin>
              <groupId>org.example</groupId>
              <artifactId>custom-test-runner</artifactId>
              <executions><execution>
                <goals><goal>run</goal></goals>
              </execution></executions>
            </plugin></plugins></build></project>
            """,
            """
            <project><build><plugins><plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-surefire-plugin</artifactId>
              <configuration><skipTests>false</skipTests></configuration>
              <executions><execution><phase>test</phase>
                <goals><goal>test</goal></goals>
              </execution></executions>
            </plugin></plugins></build></project>
            """,
            """
            <project><build><plugins><plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-surefire-plugin</artifactId>
              <configuration><skipTests>false</skipTests></configuration>
            </plugin></plugins></build></project>
            """,
            """
            <project><build><pluginManagement><plugins><plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-surefire-plugin</artifactId>
              <configuration><skipTests>false</skipTests></configuration>
            </plugin></plugins></pluginManagement></build></project>
            """,
            """
            <project><build><pluginManagement><plugins><plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-surefire-plugin</artifactId>
              <executions><execution><phase>test</phase>
                <configuration><skipTests>false</skipTests></configuration>
                <goals><goal>test</goal></goals>
              </execution></executions>
            </plugin></plugins></pluginManagement></build></project>
            """,
            """
            <project><properties><maven.test.skip>true</maven.test.skip></properties>
              <build><plugins /></build></project>
            """,
        )
        for effective_pom in effective_poms:
            with self.subTest(effective_pom=effective_pom):
                with patch.object(
                    gate.subprocess,
                    "run",
                    side_effect=self.maven_effective_pom_runner(effective_pom),
                ) as run:
                    with self.assertRaisesRegex(
                        gate.EvidenceError,
                        "unclassified|known default phase",
                    ):
                        self.submit(
                            key,
                            command=command_args,
                            environment="local",
                        )
                    self.assertEqual(1, run.call_count)
                    self.assertIn("help:effective-pom", run.call_args.args[0])

    def test_migrate_only_rejects_gradle_packaging_with_custom_test_task(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        self.write_scope(test_run_mode="migrate_only")
        (self.root / "settings.gradle").write_text("include 'app'\n", encoding="utf-8")
        (self.root / "app/build.gradle").write_text("", encoding="utf-8")
        key = ("module", "app", "executable_jar", None)
        command_args = ["gradle", ":app:bootJar", "-x", "test"]
        task_graph = self.gradle_task_graph_runner(
            (
                (":app:compileJava", "other"),
                (":app:classes", "other"),
                (":app:integrationTest", "test"),
                (":app:verifyBuild", "test"),
                (":app:bootJar", "other"),
            )
        )

        with patch.object(gate.subprocess, "run", side_effect=task_graph) as invoked:
            with self.assertRaisesRegex(
                gate.EvidenceError,
                "integrationTest|test execution|unclassified",
            ):
                self.submit(key, command=command_args, environment="local")
            self.assertEqual(1, invoked.call_count)
            self.assertIn("--dry-run", invoked.call_args.args[0])

    def test_migrate_only_rejects_qualified_gradle_test_exclusion_for_another_module(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        self.write_scope(test_run_mode="migrate_only")
        (self.root / "settings.gradle").write_text(
            "include 'app', 'other'\n",
            encoding="utf-8",
        )
        (self.root / "app/build.gradle").write_text("", encoding="utf-8")
        task_graph = self.gradle_task_graph_runner(
            (
                (":app:compileJava", "other"),
                (":app:classes", "other"),
                (":app:test", "test"),
                (":app:bootJar", "other"),
            )
        )

        for excluded_task in (":other:test", ":test"):
            with self.subTest(excluded_task=excluded_task):
                command_args = ["gradle", ":app:bootJar", "-x", excluded_task]
                with patch.object(gate.subprocess, "run", side_effect=task_graph) as invoked:
                    with self.assertRaisesRegex(gate.EvidenceError, "Migrate tests only"):
                        self.submit(
                            ("module", "app", "executable_jar", None),
                            command=command_args,
                            environment="local",
                        )
                    invoked.assert_not_called()

    def test_migrate_only_matches_qualified_gradle_test_exclusions_by_exact_path(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        self.write_scope(test_run_mode="migrate_only")
        (self.root / "settings.gradle").write_text(
            "include 'app', 'other'\n",
            encoding="utf-8",
        )
        (self.root / "app/build.gradle").write_text("", encoding="utf-8")
        command_args = ["gradle", ":app:bootJar", "-x", ":app:test"]
        task_graph = self.gradle_task_graph_runner(
            (
                (":app:compileJava", "other"),
                (":app:classes", "other"),
                (":app:test", "test"),
                (":other:test", "test"),
                (":app:bootJar", "other"),
            )
        )

        with patch.object(gate.subprocess, "run", side_effect=task_graph) as invoked:
            with self.assertRaisesRegex(
                gate.EvidenceError,
                "unexcluded test tasks.*:other:test",
            ):
                self.submit(
                    ("module", "app", "executable_jar", None),
                    command=command_args,
                    environment="local",
                )
            self.assertEqual(1, invoked.call_count)
            self.assertIn("--init-script", invoked.call_args.args[0])

    def test_migrate_only_rejects_case_mismatched_gradle_test_exclusions(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        self.write_scope(test_run_mode="migrate_only")
        (self.root / "settings.gradle").write_text("include 'app'\n", encoding="utf-8")
        (self.root / "app/build.gradle").write_text("", encoding="utf-8")
        task_graph = self.gradle_task_graph_runner(
            (
                (":app:compileJava", "other"),
                (":app:classes", "other"),
                (":app:test", "test"),
                (":app:bootJar", "other"),
            )
        )

        exclusions = (
            ("-x", "TEST"),
            ("--exclude-task", "TEST"),
            ("--exclude-task=TEST",),
            ("-x=TEST",),
        )
        for exclusion in exclusions:
            with self.subTest(exclusion=exclusion):
                command_args = ["gradle", ":app:bootJar", *exclusion]
                with patch.object(
                    gate.subprocess,
                    "run",
                    side_effect=task_graph,
                ) as invoked:
                    with self.assertRaisesRegex(
                        gate.EvidenceError,
                        "Migrate tests only",
                    ):
                        self.submit(
                            ("module", "app", "executable_jar", None),
                            command=command_args,
                            environment="local",
                        )
                    invoked.assert_not_called()

    def test_migrate_only_rejects_gradle_boot_run_with_test_task_dependency(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        write_json(self.root / gate.EVIDENCE, self.plan)
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_run_mode="migrate_only",
        )
        (self.root / "settings.gradle").write_text("include 'app'\n", encoding="utf-8")
        (self.root / "app/build.gradle").write_text("", encoding="utf-8")
        for test_task in (
            (":app:verifyBuild", "test"),
            (":app:integrationTest", "other"),
        ):
            with self.subTest(test_task=test_task):
                task_graph = self.gradle_task_graph_runner(
                    (
                        (":app:compileJava", "other"),
                        (":app:classes", "other"),
                        test_task,
                        (":app:bootRun", "other"),
                    )
                )
                with patch.object(gate.subprocess, "run", side_effect=task_graph) as invoked:
                    with self.assertRaisesRegex(
                        gate.EvidenceError,
                        "test task|test execution",
                    ):
                        self.submit(
                            ("module", "app", "spring_boot_run", None),
                            command=["gradle", ":app:bootRun"],
                            environment="local",
                        )
                    self.assertEqual(1, invoked.call_count)
                    self.assertIn("--dry-run", invoked.call_args.args[0])

    def test_migrate_only_allows_module_executable_jar_with_main_class(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        self.write_scope(test_run_mode="migrate_only")
        (self.root / "app/pom.xml").write_text("<project />\n", encoding="utf-8")
        artifact = self.root / "app/target/app-1.0.jar"
        artifact.parent.mkdir(parents=True)
        with zipfile.ZipFile(artifact, "w") as archive:
            archive.writestr(
                "META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\nMain-Class: com.example.Application\n",
            )
        effective_pom = f"""
        <project>
          <artifactId>app</artifactId>
          <version>1.0</version>
          <build>
            <directory>{self.root / "app" / "target"}</directory>
            <finalName>app-1.0</finalName>
          </build>
        </project>
        """
        command_args = ["java", "-jar", "app/target/app-1.0.jar"]
        with patch.object(
            gate.subprocess,
            "run",
            side_effect=self.maven_effective_pom_runner(effective_pom),
        ):
            self.assertEqual(
                0,
                self.submit(
                    ("module", "app", "executable_jar", None),
                    command=command_args,
                    environment="local",
                ),
            )

    def test_migrate_only_allows_configured_jar_with_test_in_filename(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        self.write_scope(test_run_mode="migrate_only")
        (self.root / "app/pom.xml").write_text("<project />\n", encoding="utf-8")
        artifact = self.root / "app/target/contest-service.jar"
        artifact.parent.mkdir(parents=True)
        with zipfile.ZipFile(artifact, "w") as archive:
            archive.writestr(
                "META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\nMain-Class: com.example.Application\n",
            )
        effective_pom = f"""
        <project>
          <artifactId>app</artifactId>
          <version>1.0</version>
          <build>
            <directory>{self.root / "app" / "target"}</directory>
            <finalName>contest-service</finalName>
          </build>
        </project>
        """

        with patch.object(
            gate.subprocess,
            "run",
            side_effect=self.maven_effective_pom_runner(effective_pom),
        ):
            self.assertEqual(
                0,
                self.submit(
                    ("module", "app", "executable_jar", None),
                    command=["java", "-jar", "app/target/contest-service.jar"],
                    environment="local",
                ),
            )

    def test_migrate_only_rejects_maven_test_runner_jar_renamed_as_application(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        self.write_scope(test_run_mode="migrate_only")
        (self.root / "app/pom.xml").write_text("<project />\n", encoding="utf-8")
        artifact = self.root / "app/target/application.jar"
        artifact.parent.mkdir(parents=True)
        with zipfile.ZipFile(artifact, "w") as archive:
            archive.writestr(
                "META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\nMain-Class: org.junit.platform.console.ConsoleLauncher\n",
            )
        effective_pom = f"""
        <project>
          <artifactId>app</artifactId>
          <version>1.0</version>
          <build>
            <directory>{self.root / "app" / "target"}</directory>
            <finalName>app-1.0</finalName>
          </build>
        </project>
        """
        command_args = ["java", "-jar", "app/target/application.jar"]
        with patch.object(
            gate.subprocess,
            "run",
            side_effect=self.maven_effective_pom_runner(effective_pom),
        ) as invoked:
            with self.assertRaisesRegex(
                gate.EvidenceError,
                "configured|application artifact",
            ):
                self.submit(
                    ("module", "app", "executable_jar", None),
                    command=command_args,
                    environment="local",
                )
            self.assertEqual(1, invoked.call_count)
            self.assertIn("help:effective-pom", invoked.call_args.args[0])

    def test_migrate_only_rejects_gradle_test_runner_jar_renamed_as_application(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        self.write_scope(test_run_mode="migrate_only")
        (self.root / "app/build.gradle").write_text("", encoding="utf-8")
        (self.root / "app/gradlew").write_text("", encoding="utf-8")
        artifact = self.root / "app/build/libs/application.jar"
        artifact.parent.mkdir(parents=True)
        with zipfile.ZipFile(artifact, "w") as archive:
            archive.writestr(
                "META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\nMain-Class: org.junit.platform.console.ConsoleLauncher\n",
            )
        command_args = ["java", "-jar", "app/build/libs/application.jar"]
        runner = self.gradle_task_graph_runner(
            ((":tasks", "other"),),
            (
                (
                    ":testJar",
                    self.root / "app" / "build" / "libs" / "application.jar",
                ),
            ),
        )
        with patch.object(gate.subprocess, "run", side_effect=runner) as invoked:
            with self.assertRaisesRegex(
                gate.EvidenceError,
                "configured|application artifact",
            ):
                self.submit(
                    ("module", "app", "executable_jar", None),
                    command=command_args,
                    environment="local",
                )
            self.assertEqual(1, invoked.call_count)
            self.assertIn("--init-script", invoked.call_args.args[0])

    def test_migrate_only_rejects_test_runner_main_classes_in_configured_maven_jar(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        write_json(self.root / gate.EVIDENCE, self.plan)
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_run_mode="migrate_only",
        )
        (self.root / "app/pom.xml").write_text("<project />\n", encoding="utf-8")
        artifact = self.root / "app/target/app-1.0.jar"
        artifact.parent.mkdir(parents=True)
        effective_pom = f"""
        <project>
          <artifactId>app</artifactId>
          <version>1.0</version>
          <build>
            <directory>{self.root / "app" / "target"}</directory>
            <finalName>app-1.0</finalName>
          </build>
        </project>
        """
        command_args = [
            "java",
            "-jar",
            "app/target/app-1.0.jar",
            "execute",
            "--scan-class-path",
        ]
        for main_class in (
            "org.junit.platform.console.ConsoleLauncher",
            "org.junit.runner.JUnitCore",
            "org.testng.TestNG",
            "org.apache.maven.surefire.booter.ForkedBooter",
            "io.cucumber.core.cli.Main",
        ):
            with self.subTest(main_class=main_class):
                with zipfile.ZipFile(artifact, "w") as archive:
                    archive.writestr(
                        "META-INF/MANIFEST.MF",
                        f"Manifest-Version: 1.0\nMain-Class: {main_class}\n",
                    )
                with patch.object(
                    gate.subprocess,
                    "run",
                    side_effect=self.maven_effective_pom_runner(effective_pom),
                ):
                    with self.assertRaisesRegex(
                        gate.EvidenceError,
                        "test.runner|test runner",
                    ):
                        self.submit(
                            ("module", "app", "executable_jar", None),
                            command=command_args,
                            environment="local",
                        )

        with zipfile.ZipFile(artifact, "w") as archive:
            archive.writestr(
                "META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\n"
                "Main-Class: org.springframework.boot.loader.launch.JarLauncher\n"
                "Start-Class: org.junit.platform.console.ConsoleLauncher\n",
            )
        with patch.object(
            gate.subprocess,
            "run",
            side_effect=self.maven_effective_pom_runner(effective_pom),
        ):
            with self.assertRaisesRegex(
                gate.EvidenceError,
                "test.runner|test runner",
            ):
                self.submit(
                    ("module", "app", "executable_jar", None),
                    command=command_args,
                    environment="local",
                )

    def test_migrate_only_rejects_test_runner_main_class_in_configured_gradle_jar(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        write_json(self.root / gate.EVIDENCE, self.plan)
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_run_mode="migrate_only",
        )
        (self.root / "app/build.gradle").write_text("", encoding="utf-8")
        (self.root / "app/gradlew").write_text("", encoding="utf-8")
        artifact = self.root / "app/build/libs/app-1.0.jar"
        artifact.parent.mkdir(parents=True)
        with zipfile.ZipFile(artifact, "w") as archive:
            archive.writestr(
                "META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\nMain-Class: org.junit.platform.console.ConsoleLauncher\n",
            )
        task_graph = self.gradle_task_graph_runner(
            ((":tasks", "other"),),
            ((":bootJar", artifact),),
        )

        with patch.object(gate.subprocess, "run", side_effect=task_graph) as invoked:
            with self.assertRaisesRegex(
                gate.EvidenceError,
                "test.runner|test runner",
            ):
                self.submit(
                    ("module", "app", "executable_jar", None),
                    command=["java", "-jar", "app/build/libs/app-1.0.jar"],
                    environment="local",
                )
            self.assertEqual(1, invoked.call_count)
            self.assertIn("--init-script", invoked.call_args.args[0])

        with zipfile.ZipFile(artifact, "w") as archive:
            archive.writestr(
                "META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\n"
                "Main-Class: org.springframework.boot.loader.launch.JarLauncher\n"
                "Start-Class: org.junit.platform.console.ConsoleLauncher\n",
            )
        with patch.object(gate.subprocess, "run", side_effect=task_graph) as invoked:
            with self.assertRaisesRegex(
                gate.EvidenceError,
                "test.runner|test runner",
            ):
                self.submit(
                    ("module", "app", "executable_jar", None),
                    command=["java", "-jar", "app/build/libs/app-1.0.jar"],
                    environment="local",
                )
            self.assertEqual(1, invoked.call_count)
            self.assertIn("--init-script", invoked.call_args.args[0])

    def test_migrate_only_rejects_unverified_external_launcher_commands(self):
        self.plan["modules"][0]["runtime_mode"] = "external-launcher"
        self.write_scope(test_run_mode="migrate_only")
        key = ("module", "app", "external_launcher", None)
        for command_args in (
            ["java", "-cp", "app/target/test-classes", "com.example.TestRunner"],
            ["python3", "scripts/start-worker.py"],
        ):
            with self.subTest(command=command_args):
                with patch.object(gate.subprocess, "run") as command:
                    with self.assertRaisesRegex(gate.EvidenceError, "unverified executable"):
                        self.submit(key, command=command_args, environment="local")
                    command.assert_not_called()

    def test_migrate_only_blocks_gate_without_docker_probe(self):
        self.plan["modules"][0]["test_suites"][0]["requires_docker"] = True
        self.write_scope(test_run_mode="migrate_only")
        self.assertNotIn(
            ("project", ".", "docker_info", None), gate.requirements(self.root, self.plan).required
        )
        self.complete_required_checks()
        self.assertEqual(1, self.audit())
        summary = self.summary()
        self.assertEqual("NOT READY", summary["gate"])
        self.assertTrue(summary["issues"])
        self.assertTrue(
            all(gate.QUESTION_8_DECLINE_REASON in issue for issue in summary["issues"]),
            summary["issues"],
        )

    def test_migrate_only_allows_gradle_configured_module_jar(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        self.write_scope(test_run_mode="migrate_only")
        (self.root / "app/build.gradle").write_text("", encoding="utf-8")
        (self.root / "app/gradlew").write_text("", encoding="utf-8")
        artifact = self.root / "app/build/libs/app-1.0.jar"
        artifact.parent.mkdir(parents=True)
        with zipfile.ZipFile(artifact, "w") as archive:
            archive.writestr(
                "META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\nMain-Class: com.example.Application\n",
            )
        command_args = ["java", "-jar", "app/build/libs/app-1.0.jar"]
        runner = self.gradle_task_graph_runner(
            ((":tasks", "other"),),
            ((":bootJar", artifact),),
        )
        with patch.object(gate.subprocess, "run", side_effect=runner):
            self.assertEqual(
                0,
                self.submit(
                    ("module", "app", "executable_jar", None),
                    command=command_args,
                    environment="local",
                ),
            )

    def test_migrate_only_gate_rejects_previously_passed_test_checks(self):
        self.complete_required_checks()
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        inventory["test_run_mode"] = "migrate_only"
        write_json(self.root / gate.INVENTORY, inventory)
        self.assertEqual(1, self.audit())
        issues = self.summary()["issues"]
        self.assertTrue(any("must be blocked with reason" in issue for issue in issues), issues)

    def test_run_mode_accepts_migratable_inventory_with_suite(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_run_mode="run",
        )
        contract = gate.test_contract(
            self.root,
            json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8")),
        )

        self.assertEqual("run", contract["mode"])
        self.assertEqual([("app", "unit")], contract["test_suites"][self.c7_test_id])

    def test_migrate_only_requires_test_suites_for_all_migratable_handling_values(self):
        junit = '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        for handling in ("Migrate", "Migrate to CPT", "Migrate (lower priority)"):
            for suite_entries in (None, []):
                with self.subTest(handling=handling, suites=suite_entries):
                    self.configure_test_run(
                        junit,
                        test_handling=handling,
                        test_run_mode="migrate_only",
                    )
                    inventory = json.loads(
                        (self.root / gate.INVENTORY).read_text(encoding="utf-8")
                    )
                    if suite_entries is None:
                        inventory.pop("test_suites")
                    else:
                        inventory["test_suites"] = suite_entries

                    with self.assertRaisesRegex(
                        gate.EvidenceError,
                        "no Step 2 test suite records this migrated test",
                    ):
                        gate.test_contract(self.root, inventory)

    def test_test_run_mode_is_rejected_without_migratable_tests(self):
        junit = '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        for handling in ("Report only", "Not part of test migration", None):
            for mode in ("run", "migrate_only"):
                with self.subTest(handling=handling, mode=mode):
                    self.configure_test_run(
                        junit,
                        test_run_mode="migrate_only",
                    )
                    report_path = self.root / gate.REPORT
                    report = report_path.read_text(encoding="utf-8")
                    if handling is None:
                        report = "\n".join(
                            line
                            for line in report.splitlines()
                            if self.c7_test_id not in line
                        ) + "\n"
                    else:
                        report = report.replace("| Migrate |", f"| {handling} |")
                    report_path.write_text(report, encoding="utf-8")
                    inventory = json.loads(
                        (self.root / gate.INVENTORY).read_text(encoding="utf-8")
                    )
                    inventory["test_run_mode"] = mode

                    with self.assertRaisesRegex(
                        gate.EvidenceError,
                        "test_run_mode is not allowed unless the Test Inventory contains migratable tests",
                    ):
                        gate.test_contract(self.root, inventory)

    def test_deferred_run_transition_preserves_plan_source_digest(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_run_mode="migrate_only",
        )
        before = gate.requirements(self.root, self.plan).source_digest
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        inventory["test_run_mode"] = "run"
        write_json(self.root / gate.INVENTORY, inventory)

        after = gate.requirements(self.root, self.plan).source_digest

        self.assertEqual(before, after)

    def test_unknown_test_run_mode_is_rejected(self):
        for mode in ("skip", None, [], {}):
            with self.subTest(mode=mode):
                inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
                inventory["test_run_mode"] = mode
                write_json(self.root / gate.INVENTORY, inventory)
                self.assertEqual(1, self.audit())
                self.assertTrue(
                    any("test_run_mode must be 'run' or 'migrate_only'" in issue for issue in self.summary()["issues"])
                )
                self.write_scope()

    def test_complete_records_are_ready(self):
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        summary = self.summary()
        self.assertEqual("READY", summary["gate"])
        self.assertEqual([], summary["issues"])
        self.assertTrue(
            any(check["type"] == "process" and check["kind"] == "process_path" for check in summary["checks"])
        )
        self.assertTrue(all(check["result"] == "passed" for check in summary["checks"]))
        self.assertEqual(
            1, (self.root / gate.REPORT).read_text(encoding="utf-8").count("**Validation gate:**")
        )

    def test_c7_baseline_aggregates_parameterized_invocations(self):
        junit = (
            '<testsuite name="OrderTest">'
            '<testcase classname="com.example.OrderTest" name="testOrder(String)[1]" />'
            '<testcase classname="com.example.OrderTest" name="testOrder(String)[2]">'
            "<skipped /></testcase>"
            '<testcase classname="com.example.OrderTest" name="testOrder[3](String)" />'
            "</testsuite>"
        )
        self.configure_test_run(junit)
        self.assertEqual(0, self.record_c7_baseline())
        mapping = gate.read_test_mapping(self.root, required=True)
        test = next(item for item in mapping["tests"] if item["c7_id"] == self.c7_test_id)
        self.assertEqual("skipped", test["c7_result"])
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        issues = []
        checks = gate.load_checks(self.root, evidence, gate.requirements(self.root, self.plan), issues)
        self.assertEqual([], issues)
        check = checks[("module", "app", "c7_baseline", "unit")][1]
        self.assertEqual(3, check["test_results"][self.c7_test_id]["invocation_count"])
        self.assertTrue(check["reports"])
        self.assertTrue((self.root / check["reports"][0]).is_file())

    def test_test_inventory_parses_escaped_pipes_in_framework_display_names(self):
        tests = (
            (
                "app:com.example.CucumberTest#Given an order | when paid",
                "Cucumber scenario",
                "app/src/test/java/com/example/CucumberTest.java",
            ),
            (
                "app:com.example.OrderSpec#an order | pays \\ the invoice",
                "Spock feature",
                "app/src/test/java/com/example/OrderSpec.java",
            ),
        )
        rows = []
        for test_id, test_kind, file_path in tests:
            source_file = self.root / file_path
            source_file.parent.mkdir(parents=True, exist_ok=True)
            source_file.write_text("", encoding="utf-8")
            escaped_test_id = gate.markdown_cell(test_id)
            rows.append(
                f"| `{escaped_test_id}` | `{file_path}` | {test_kind} | Migrate |"
            )
        (self.root / gate.REPORT).write_text(
            "# Migration report\n\n"
            "## Test Inventory\n\n"
            "| Test ID | File | Test kind | Handling |\n"
            "|---|---|---|---|\n"
            + "\n".join(rows)
            + "\n",
            encoding="utf-8",
        )

        inventory = gate.test_report_inventory(self.root)

        self.assertEqual(
            [test_id for test_id, _, _ in tests],
            [test["id"] for test in inventory],
        )
        self.assertEqual(
            [kind for _, kind, _ in tests],
            [test["test_kind"] for test in inventory],
        )

    def test_report_rejects_null_baseline_test_results_without_crashing(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())

        mapping = gate.read_test_mapping(self.root, required=True)
        mapping["baseline"]["suites"][0]["test_results"] = None
        write_json(self.root / gate.TEST_MAPPING, mapping)

        self.assertEqual(1, self.audit())
        summary = self.summary()
        self.assertEqual("NOT READY", summary["gate"])
        self.assertIn(
            "Test parity ledger baseline has an invalid shape",
            summary["issues"],
        )

    def test_read_test_mapping_rejects_malformed_baseline_suite_payloads(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        valid_mapping = gate.read_test_mapping(self.root, required=True)
        test_id = self.c7_test_id
        malformed_suites = (
            ("test results must be an object", lambda suite: suite.update(test_results=None)),
            (
                "each test result must be an object",
                lambda suite: suite["test_results"].update({test_id: None}),
            ),
            (
                "test results must use a known verdict",
                lambda suite: suite["test_results"][test_id].update(result="unknown"),
            ),
            (
                "invocations must be status arrays",
                lambda suite: suite["test_results"][test_id].update(invocations=None),
            ),
            (
                "invocation statuses must be known",
                lambda suite: suite["test_results"][test_id].update(invocations=[None]),
            ),
            ("module must be a string", lambda suite: suite.update(module=[])),
            ("suite must be a string", lambda suite: suite.update(suite=[])),
            (
                "suite verdict must be known",
                lambda suite: suite.update(result="unknown"),
            ),
            (
                "coverage must be an object",
                lambda suite: suite.update(coverage_by_process=None),
            ),
            (
                "coverage values must be string arrays",
                lambda suite: suite.update(coverage_by_process={"p": None}),
            ),
        )

        for name, corrupt in malformed_suites:
            with self.subTest(name=name):
                mapping = json.loads(json.dumps(valid_mapping))
                corrupt(mapping["baseline"]["suites"][0])
                write_json(self.root / gate.TEST_MAPPING, mapping)

                with self.assertRaisesRegex(
                    gate.EvidenceError,
                    "Test parity ledger baseline has an invalid shape",
                ):
                    gate.read_test_mapping(self.root, required=True)

    def test_read_test_mapping_rejects_coverage_aggregates_that_differ_from_suite_records(self):
        valid_mapping = {
            "schema_version": 1,
            "baseline": {
                "suites": [
                    {
                        "module": "app",
                        "suite": "unit",
                        "result": "passed",
                        "test_results": {},
                        "coverage_available": True,
                        "coverage_by_process": {"p": ["TaskA"]},
                    }
                ],
                "coverage_available": True,
                "coverage": {"p": ["TaskA"]},
            },
            "tests": [],
            "freeze": {"files": {}},
            "test_changes": [],
            "mock_changes": [],
        }
        corruptions = (
            ("availability", lambda baseline: baseline.update(coverage_available=False)),
            ("coverage", lambda baseline: baseline.update(coverage={})),
        )

        for name, corrupt in corruptions:
            with self.subTest(aggregate=name):
                mapping = json.loads(json.dumps(valid_mapping))
                corrupt(mapping["baseline"])
                write_json(self.root / gate.TEST_MAPPING, mapping)

                with self.assertRaisesRegex(
                    gate.EvidenceError,
                    "C7 coverage aggregate differs from its suite records",
                ):
                    gate.read_test_mapping(self.root, required=True)

    def test_test_parity_rejects_tampered_baseline_coverage_availability(self):
        test_id = "app:com.example.OrderTest#testOrder"
        coverage = {"p": ["TaskA"]}
        plan = Namespace(
            test_contract={
                "tests": [],
                "suites": {
                    ("app", "unit"): {
                        "migrate_test_ids": [test_id],
                        "test_ids": [test_id],
                    }
                },
            }
        )
        mapping = {
            "baseline": {
                "suites": [
                    {
                        "module": "app",
                        "suite": "unit",
                        "result": "passed",
                        "test_results": {},
                        "coverage_available": False,
                        "coverage_by_process": coverage,
                    }
                ],
                "coverage_available": False,
                "coverage": coverage,
            },
            "tests": [],
        }
        checks = {
            ("module", "app", "c7_baseline", "unit"): (
                None,
                {
                    "result": "passed",
                    "test_results": {},
                    "coverage_available": True,
                    "coverage_by_process": coverage,
                },
            )
        }

        issues = gate.test_parity_issues(plan, checks, mapping)

        self.assertIn(
            "('app', 'unit'): test parity ledger differs from its baseline log",
            issues,
        )

    def test_test_parity_rejects_tampered_baseline_suite_verdict(self):
        test_id = "app:com.example.OrderTest#testOrder"
        cpt_test_id = "app:com.example.OrderCptTest#testOrder"
        suite_key = ("app", "unit")
        test_result = {"result": "passed", "invocations": ["passed"]}
        test_results = {test_id: test_result}
        coverage = {}
        plan = Namespace(
            test_contract={
                "tests": [
                    {
                        "id": test_id,
                        "module": "app",
                        "test_kind": "process test",
                        "handling": "Migrate",
                    }
                ],
                "suites": {
                    suite_key: {
                        "module": "app",
                        "test_ids": [test_id],
                        "migrate_test_ids": [test_id],
                    }
                },
                "test_suites": {test_id: [suite_key]},
            }
        )
        mapping = {
            "baseline": {
                "suites": [
                    {
                        "module": "app",
                        "suite": "unit",
                        "result": "failed",
                        "test_results": test_results,
                        "coverage_available": False,
                        "coverage_by_process": coverage,
                    }
                ],
                "coverage_available": False,
                "coverage": coverage,
            },
            "tests": [
                {
                    "c7_id": test_id,
                    "test_kind": "process test",
                    "handling": "Migrate",
                    "c7_result": None,
                    "status": "migrated",
                    "c8_ids": [cpt_test_id],
                }
            ],
        }
        checks = {
            ("module", "app", "c7_baseline", "unit"): (
                None,
                {
                    "result": "passed",
                    "test_results": test_results,
                    "coverage_available": False,
                    "coverage_by_process": coverage,
                },
            ),
            ("module", "app", "test_repeat", "unit"): (
                None,
                {
                    "test_runs": [
                        {"test_results": {cpt_test_id: {"result": "skipped"}}},
                        {"test_results": {cpt_test_id: {"result": "skipped"}}},
                    ]
                },
            ),
        }

        issues = gate.test_parity_issues(plan, checks, mapping)

        self.assertIn(
            "('app', 'unit'): test parity ledger differs from its baseline log",
            issues,
        )

    def test_c7_baseline_tracks_out_of_module_converted_copies(self):
        junit = (
            '<testsuite><testcase classname="com.example.OrderTest" '
            'name="testOrder" /></testsuite>'
        )
        self.configure_test_run(junit)
        converted_copy = self.root / "models/converted-c8-process.bpmn"
        original = converted_copy.read_text(encoding="utf-8")
        converted_copy.unlink()

        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root, reset_source_snapshot=True))
        converted_copy.write_text(original, encoding="utf-8")
        with self.assertRaisesRegex(
            gate.EvidenceError,
            "C7 baseline must run before source changes: models/converted-c8-process.bpmn",
        ):
            self.record_c7_baseline()
        self.assertFalse(
            (self.root / "app/target/surefire-reports/TEST-com.example.OrderTest.xml").exists()
        )

        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root, reset_source_snapshot=True))
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        converted_copy.write_text(original + "\n", encoding="utf-8")
        with self.assertRaisesRegex(
            gate.EvidenceError,
            "C7 baseline must run before source changes: models/converted-c8-process.bpmn",
        ):
            gate.verify_unchanged_source(self.root, inventory)

        converted_copy.write_text(original, encoding="utf-8")
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root, reset_source_snapshot=True))
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        converted_copy.unlink()
        with self.assertRaisesRegex(
            gate.EvidenceError,
            "C7 baseline must run before source changes: models/converted-c8-process.bpmn",
        ):
            gate.verify_unchanged_source(self.root, inventory)

    def test_c7_baseline_rechecks_source_snapshot_after_the_command(self):
        junit = (
            '<testsuite><testcase classname="com.example.OrderTest" '
            'name="testOrder" /></testsuite>'
        )
        self.configure_test_run(junit)
        self.c7_command = self.write_reports_command(
            {
                "app/target/surefire-reports/TEST-com.example.OrderTest.xml": junit,
                "app/src/test/java/com/example/OrderTest.java": (
                    "class OrderTest { int changed; }\n"
                ),
            }
        )
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        inventory["test_suites"][0]["command"] = self.c7_command
        write_json(self.root / gate.INVENTORY, inventory)
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root, reset_source_snapshot=True))

        self.assertEqual(1, self.record_c7_baseline())
        mapping = gate.read_test_mapping(self.root, required=True)
        baseline = mapping["baseline"]["suites"][0]
        self.assertEqual("failed", baseline["result"])
        self.assertIn("C7 baseline must run before source changes", baseline["reason"])

    def test_deferred_c7_baseline_runs_from_the_preserved_source_root(self):
        junit = (
            '<testsuite><testcase classname="com.example.OrderTest" '
            'name="testOrder" /></testsuite>'
        )
        self.configure_test_run(junit, test_run_mode="migrate_only")
        baseline_root = self.root.parent / f"{self.root.name}-c7-baseline"
        shutil.copytree(self.root, baseline_root)
        self.addCleanup(shutil.rmtree, baseline_root)
        (self.root / self.c7_test_file_path).write_text(
            "class OrderCptTest {}\n", encoding="utf-8"
        )
        inventory_path = self.root / gate.INVENTORY
        inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
        inventory["test_run_mode"] = "run"
        write_json(inventory_path, inventory)

        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "c7_baseline", "unit"),
                command=self.c7_command,
                baseline_root=baseline_root,
            ),
        )
        mapping = gate.read_test_mapping(self.root, required=True)
        self.assertEqual("passed", mapping["baseline"]["suites"][0]["result"])

    def test_deferred_c7_baseline_uses_the_preserved_source_root(self):
        junit = (
            '<testsuite><testcase classname="com.example.OrderTest" '
            'name="testOrder" /></testsuite>'
        )
        self.configure_test_run(junit, test_run_mode="migrate_only")
        baseline_root = self.root.parent / f"{self.root.name}-c7-baseline"
        shutil.copytree(self.root, baseline_root)
        self.addCleanup(shutil.rmtree, baseline_root)
        (self.root / self.c7_test_file_path).write_text(
            "class OrderCptTest {}\n", encoding="utf-8"
        )
        inventory_path = self.root / gate.INVENTORY
        inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
        inventory["test_run_mode"] = "run"
        write_json(inventory_path, inventory)

        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "c7_baseline", "unit"),
                command=self.c7_command,
                baseline_root=baseline_root,
            ),
        )
        mapping = gate.read_test_mapping(self.root, required=True)
        self.assertEqual("passed", mapping["baseline"]["suites"][0]["result"])

    def test_c7_baseline_snapshot_tracks_inventory_tests_under_build_directories(self):
        junit = (
            '<testsuite><testcase classname="com.example.OrderTest" '
            'name="testOrder" /></testsuite>'
        )
        test_file = "app/target/generated-test-sources/java/com/example/OrderTest.java"
        self.configure_test_run(junit, test_file_path=test_file)
        (self.root / test_file).write_text("class OrderTest { int changed; }\n", encoding="utf-8")

        with self.assertRaisesRegex(
            gate.EvidenceError,
            f"C7 baseline must run before source changes: {test_file}",
        ):
            self.record_c7_baseline()
        self.assertFalse(
            (self.root / "app/target/surefire-reports/TEST-com.example.OrderTest.xml").exists()
        )

    def test_c7_baseline_snapshot_rejects_missing_test_inventory_files(self):
        junit = (
            '<testsuite><testcase classname="com.example.OrderTest" '
            'name="testOrder" /></testsuite>'
        )
        self.configure_test_run(junit)
        (self.root / self.c7_test_file_path).unlink()

        with self.assertRaisesRegex(
            gate.EvidenceError, "Test Inventory file is missing"
        ):
            gate.initialize(self.root, reset_source_snapshot=True)

    def test_reused_legacy_snapshot_cannot_reuse_stale_passing_baseline(self):
        junit = (
            '<testsuite><testcase classname="com.example.OrderTest" '
            'name="testOrder" /></testsuite>'
        )
        self.configure_test_run(junit)
        self.assertEqual(0, self.record_c7_baseline())

        mapping = gate.read_test_mapping(self.root, required=True)
        baseline = mapping["baseline"]
        baseline_suite = baseline["suites"][0]
        self.assertEqual("passed", baseline_suite["result"])
        check_path = self.root / baseline_suite["evidence_path"]
        check = json.loads(check_path.read_text(encoding="utf-8"))

        inventory_path = self.root / gate.INVENTORY
        inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
        inventory["source_files"].pop(self.c7_test_file_path)
        legacy_digest = gate.source_snapshot_digest(
            inventory["modules"],
            inventory["models"],
            inventory["source_files"],
            inventory["source_snapshot_test_contract"],
        )
        inventory["source_snapshot_sha256"] = legacy_digest
        baseline["source_digest"] = legacy_digest
        check["source_digest"] = legacy_digest
        write_json(self.root / gate.TEST_MAPPING, mapping)
        write_json(check_path, check)
        write_json(inventory_path, inventory)
        (self.root / self.c7_test_file_path).unlink()
        original_run_id = inventory["run_id"]

        with self.assertRaisesRegex(
            gate.EvidenceError, "C7 source snapshot omits Test Inventory file"
        ):
            gate.initialize(self.root)
        updated_inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
        self.assertEqual(original_run_id, updated_inventory["run_id"])

    def test_repeated_init_accepts_migrated_test_source_with_complete_snapshot(self):
        junit = (
            '<testsuite><testcase classname="com.example.OrderTest" '
            'name="testOrder" /></testsuite>'
        )
        self.configure_test_run(junit)
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        self.assertFalse((self.root / self.c7_test_file_path).exists())

        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root))

    def test_c7_baseline_snapshot_tracks_configured_root_contents_under_build_directories(self):
        junit = (
            '<testsuite><testcase classname="com.example.OrderTest" '
            'name="testOrder" /></testsuite>'
        )
        source_root = "app/target/generated-test-sources"
        resource_root = "app/target/generated-test-resources"
        files = {
            f"{source_root}/com/example/GeneratedOrderTest.java": "class GeneratedOrderTest {}\n",
            f"{resource_root}/order.json": "{}\n",
        }
        for path, content in files.items():
            target = self.root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8")
        self.configure_test_run(
            junit,
            test_source_roots=[source_root],
            test_resource_roots=[resource_root],
        )
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))

        for path, content in files.items():
            with self.subTest(path=path):
                target = self.root / path
                target.write_text(content + "changed\n", encoding="utf-8")
                with self.assertRaisesRegex(
                    gate.EvidenceError,
                    f"C7 baseline must run before source changes: {path}",
                ):
                    gate.verify_unchanged_source(self.root, inventory)
                target.write_text(content, encoding="utf-8")

    def test_snapshot_and_computed_evidence_errors_name_their_method(self):
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        key = ("project", ".", "test_freeze", None)
        plan = Namespace(
            allowed={key},
            test_contract={"mode": None},
            source_digest="plan-source",
        )
        cases = (
            (
                {"command": ["test"]},
                "{method} evidence cannot have a command result",
            ),
            ({"result": "invalid"}, "invalid {method} result"),
            (
                {"result": "passed", "output": ""},
                "passing {method} evidence lacks output",
            ),
        )

        for method in ("snapshot", "computed"):
            for changes, message in cases:
                with self.subTest(method=method, message=message):
                    check = {
                        "run_id": inventory["run_id"],
                        "type": key[0],
                        "target": key[1],
                        "kind": key[2],
                        "scenario": key[3],
                        "method": method,
                        "result": "failed",
                        "command": None,
                        "exit_code": None,
                        "output": "Failure details",
                        "reason": "The check did not pass.",
                    }
                    check.update(changes)
                    reference = gate.write_check_log(self.root, key, check)
                    issues = []

                    gate.load_checks(
                        self.root,
                        {"checks": [reference]},
                        plan,
                        issues,
                    )

                    self.assertEqual(
                        [f"{key}: {message.format(method=method)}"],
                        issues,
                    )

    def test_passing_unlisted_report_only_test_requires_disposition(self):
        test_id = "app:com.example.LegacyTest#testLegacy"
        test = {
            "id": test_id,
            "module": "app",
            "test_kind": "legacy test",
            "handling": "Report only",
        }
        plan = Namespace(
            test_contract={
                "tests": [test],
                "suites": {},
                "test_suites": {},
            }
        )
        mapping = {
            "baseline": {
                "suites": [
                    {
                        "module": "app",
                        "suite": "unit",
                        "result": "passed",
                        "test_results": {
                            test_id: {"result": "passed", "invocations": ["passed"]}
                        },
                    }
                ]
            },
            "tests": [
                {
                    "c7_id": test_id,
                    "test_kind": "legacy test",
                    "handling": "Report only",
                    "c7_result": None,
                    "status": "manual",
                }
            ],
        }
        checks = {
            ("module", "app", "c7_baseline", "unit"): (
                1,
                {
                    "result": "passed",
                    "test_results": {
                        test_id: {"result": "passed", "invocations": ["passed"]}
                    },
                },
            )
        }

        baseline_results = gate.aggregate_baseline_results(
            plan.test_contract, mapping["baseline"]["suites"]
        )
        self.assertEqual("passed", baseline_results[test_id]["c7_result"])

        issues = gate.test_parity_issues(plan, checks, mapping)
        self.assertIn(f"{test_id}: manual test is not verified", issues)

        mapping["tests"] = []
        issues = gate.test_parity_issues(plan, checks, mapping)
        self.assertIn(f"{test_id}: test parity ledger entry is missing", issues)

        mapping["tests"] = [
            {
                "c7_id": test_id,
                "test_kind": "legacy test",
                "handling": "Report only",
                "c7_result": "passed",
                "status": "retired",
                "retirement": {
                    "reason": "The behavior is no longer required",
                    "approved_by": "migration owner",
                },
            }
        ]
        self.assertEqual([], gate.test_parity_issues(plan, checks, mapping))

    def test_unbound_report_only_baseline_suite_cannot_supply_c7_results(self):
        test_id = "app:com.example.LegacyTest#testLegacy"
        test = {
            "id": test_id,
            "module": "app",
            "test_kind": "legacy test",
            "handling": "Report only",
        }
        plan = Namespace(
            test_contract={
                "tests": [test],
                "suites": {},
                "test_suites": {},
            }
        )
        suite = {
            "module": "app",
            "suite": "unit",
            "result": "passed",
            "test_results": {
                test_id: {"result": "passed", "invocations": ["passed"]}
            },
        }
        mapping = {
            "baseline": {"suites": [suite]},
            "tests": [
                {
                    "c7_id": test_id,
                    "test_kind": "legacy test",
                    "handling": "Report only",
                    "c7_result": "passed",
                    "c8_ids": [],
                    "status": "retired",
                    "retirement": {
                        "reason": "The behavior is no longer required.",
                        "approved_by": "migration owner",
                    },
                }
            ],
        }
        suite_key = ("module", "app", "c7_baseline", "unit")
        cases = (
            ("missing check log", {}),
            (
                "mismatched check log",
                {
                    suite_key: (
                        1,
                        {
                            "result": "passed",
                            "test_results": {},
                        },
                    )
                },
            ),
        )

        for name, checks in cases:
            with self.subTest(name=name):
                issues = gate.test_parity_issues(plan, checks, mapping)
                self.assertTrue(
                    any(
                        "no matching validator-owned C7 baseline check" in issue
                        or "differs from its baseline log" in issue
                        for issue in issues
                    ),
                    issues,
                )
                self.assertIn(
                    f"{test_id}: C7 result differs from the captured baseline reports",
                    issues,
                )

    def test_report_only_note_is_limited_to_manual_ledger_rows(self):
        mapping = {
            "tests": [
                {
                    "c7_id": test_id,
                    "handling": "Report only",
                    "status": status,
                }
                for test_id, status in (
                    ("manual", "manual"),
                    ("migrated", "migrated"),
                    ("retired", "retired"),
                    ("unmapped", None),
                )
            ]
        }
        report = gate.render_test_parity(
            Namespace(test_contract={"suites": {}}),
            {},
            mapping,
        )
        rows = {
            test_id: next(
                line for line in report.splitlines() if line.startswith(f"| {test_id} |")
            )
            for test_id in ("manual", "migrated", "retired", "unmapped")
        }

        self.assertIn("Report only; not verified.", rows["manual"])
        for test_id in ("migrated", "retired", "unmapped"):
            with self.subTest(test_id=test_id):
                self.assertNotIn("Report only; not verified.", rows[test_id])

    def test_parity_checks_require_run_mode_and_a_migrated_test(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        plan = gate.requirements(self.root, self.plan)
        self.assertIn(("module", "app", "c7_baseline", "unit"), plan.required)

        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        inventory["test_run_mode"] = "migrate_only"
        write_json(self.root / gate.INVENTORY, inventory)
        migrate_only = gate.requirements(self.root, self.plan)
        self.assertFalse(any(key[2] in {
            "c7_baseline", "test_freeze", "test_repeat", "test_parity", "coverage_parity"
        } for key in migrate_only.required))

        inventory["test_run_mode"] = "run"
        write_json(self.root / gate.INVENTORY, inventory)
        report = (self.root / gate.REPORT).read_text(encoding="utf-8")
        report = report.replace("| Migrate | — |", "| Report only | — |")
        (self.root / gate.REPORT).write_text(report, encoding="utf-8")
        report_only = gate.requirements(self.root, self.plan)
        self.assertFalse(any(key[2] in {
            "c7_baseline", "test_freeze", "test_repeat", "test_parity", "coverage_parity"
        } for key in report_only.required))

    def test_invalid_test_run_mode_is_reported_without_crashing(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        inventory["test_run_mode"] = "unsupported"
        write_json(self.root / gate.INVENTORY, inventory)

        self.assertEqual(1, self.audit())
        self.assertIn(
            "Step 2 test_run_mode must be 'run' or 'migrate_only'",
            self.summary()["issues"],
        )

    def test_c7_baseline_accepts_cucumber_and_spock_display_names(self):
        cucumber_id = "app:com.example.RunCucumberTest#Scenario: customer pays"
        spock_id = "app:com.example.OrderSpec#an order can be paid (3DS)"
        parameterized_id = "app:com.example.ParameterizedTest#testWithCase"
        junit = (
            "<testsuite>"
            '<testcase classname="com.example.OrderTest" name="testOrder" />'
            '<testcase classname="com.example.RunCucumberTest" '
            'name="Scenario: customer pays" />'
            '<testcase classname="com.example.OrderSpec" '
            'name="an order can be paid (3DS)" />'
            '<testcase classname="com.example.ParameterizedTest" '
            'name="testWithCase[1]" />'
            '<testcase classname="com.example.ParameterizedTest" '
            'name="testWithCase[2]" />'
            "</testsuite>"
        )
        self.configure_test_run(junit)
        framework_tests = (
            (
                cucumber_id,
                "app/src/test/resources/features/order.feature",
                "Cucumber scenario",
            ),
            (
                spock_id,
                "app/src/test/groovy/com/example/OrderSpec.groovy",
                "Spock feature",
            ),
            (
                parameterized_id,
                "app/src/test/java/com/example/ParameterizedTest.java",
                "JUnit parameterized test",
            ),
        )
        report = self.root / gate.REPORT
        for test_id, file_path, test_kind in framework_tests:
            source_file = self.root / file_path
            source_file.parent.mkdir(parents=True, exist_ok=True)
            source_file.write_text("", encoding="utf-8")
        report.write_text(
            "# Migration report\n\n"
            "## Test Inventory\n\n"
            "| Test ID | File | Test kind | Handling |\n"
            "|---|---|---|---|\n"
            f"| `{self.c7_test_id}` | `{self.c7_test_file_path}` "
            "| process test | Migrate |\n"
            + "".join(
                f"| `{test_id}` | `{file_path}` | {test_kind} | Report only |\n"
                for test_id, file_path, test_kind in framework_tests
            ),
            encoding="utf-8",
        )

        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root, reset_source_snapshot=True))
        self.assertEqual(0, self.record_c7_baseline())

        mapping = gate.read_test_mapping(self.root, required=True)
        results = {test["c7_id"]: test["c7_result"] for test in mapping["tests"]}
        self.assertEqual("passed", results[cucumber_id])
        self.assertEqual("passed", results[spock_id])
        self.assertEqual("passed", results[parameterized_id])

    def test_gradle_cpt_repeat_reruns_tasks_for_fresh_reports(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        passed_result = {
            "exit_code": 0,
            "result": "passed",
            "reason": None,
            "reports": [],
            "test_results": {
                self.c8_test_id: {
                    "result": "passed",
                    "invocations": ["passed"],
                }
            },
            "coverage_reports": [],
            "coverage_by_process": {},
            "decision_coverage_by_id": {},
            "coverage_available": False,
            "output": "",
        }
        with patch.object(
            gate,
            "run_cpt_test_suite",
            side_effect=[passed_result, passed_result],
        ) as run:
            result = gate.record_test_repeat(
                self.root,
                gate.requirements(self.root, self.plan),
                Namespace(
                    target="app",
                    scenario="unit",
                    command=["gradle", ":app:test"],
                    timeout=None,
                ),
                gate.read_test_mapping(self.root, required=True),
            )

        self.assertEqual("passed", result["result"])
        self.assertEqual(
            ["gradle", ":app:test"],
            run.call_args_list[0].args[3],
        )
        self.assertEqual(
            ["gradle", ":app:test", "--rerun-tasks"],
            run.call_args_list[1].args[3],
        )

    def test_cpt_repeat_preserves_mapped_display_name_suffixes(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.c8_test_id = (
            "app:com.example.OrderCptTest#Scenario: pay by card (3DS)"
        )
        self.map_test_to_cpt()
        junit = (
            "<testsuite>"
            '<testcase classname="com.example.OrderCptTest" '
            'name="Scenario: pay by card (3DS)" />'
            "</testsuite>"
        )
        result = gate.record_test_repeat(
            self.root,
            gate.requirements(self.root, self.plan),
            Namespace(
                target="app",
                scenario="unit",
                command=self.cpt_command(first_junit=junit),
                timeout=None,
            ),
            gate.read_test_mapping(self.root, required=True),
        )

        self.assertEqual("passed", result["result"])
        for run in result["test_runs"]:
            self.assertEqual({self.c8_test_id}, set(run["test_results"]))

    def test_cpt_repeat_preserves_added_display_name_suffixes(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        added_id = (
            "app:com.example.AddedCptTest#Scenario: place order (3DS)"
        )
        mapping = gate.read_test_mapping(self.root, required=True)
        mapping["tests"].append({"status": "added", "suite": "unit", "c8_ids": [added_id]})
        write_json(self.root / gate.TEST_MAPPING, mapping)
        junit = (
            "<testsuite>"
            '<testcase classname="com.example.OrderCptTest" name="testOrder" />'
            '<testcase classname="com.example.AddedCptTest" '
            'name="Scenario: place order (3DS)" />'
            "</testsuite>"
        )

        result = gate.record_test_repeat(
            self.root,
            gate.requirements(self.root, self.plan),
            Namespace(
                target="app",
                scenario="unit",
                command=self.cpt_command(first_junit=junit),
                timeout=None,
            ),
            mapping,
        )

        self.assertEqual("passed", result["result"])
        for run in result["test_runs"]:
            self.assertEqual({self.c8_test_id, added_id}, set(run["test_results"]))

    def test_cpt_repeat_preserves_migrated_report_only_display_name_suffixes(self):
        report_only_id = "app:com.example.OrderSpec#an order can be paid (3DS)"
        c7_junit = (
            "<testsuite>"
            '<testcase classname="com.example.OrderTest" name="testOrder" />'
            '<testcase classname="com.example.OrderSpec" '
            'name="an order can be paid (3DS)" />'
            "</testsuite>"
        )
        self.configure_test_run(c7_junit)
        self.add_report_only_inventory_test(
            report_only_id,
            "app/src/test/groovy/com/example/OrderSpec.groovy",
            "Spock feature",
        )
        self.assertEqual(0, self.record_c7_baseline())
        report_only_cpt_id = (
            "app:com.example.OrderCptSpec#an order can be paid (3DS)"
        )
        mapping = gate.read_test_mapping(self.root, required=True)
        report_only_test = next(
            test for test in mapping["tests"] if test.get("c7_id") == report_only_id
        )
        report_only_test.update(
            status="migrated",
            c8_ids=[report_only_cpt_id],
            mocks={"c7": [], "c8": []},
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)
        self.map_test_to_cpt()
        junit = (
            "<testsuite>"
            '<testcase classname="com.example.OrderCptTest" name="testOrder" />'
            '<testcase classname="com.example.OrderCptSpec" '
            'name="an order can be paid (3DS)" />'
            "</testsuite>"
        )

        result = gate.record_test_repeat(
            self.root,
            gate.requirements(self.root, self.plan),
            Namespace(
                target="app",
                scenario="unit",
                command=self.cpt_command(first_junit=junit),
                timeout=None,
            ),
            gate.read_test_mapping(self.root, required=True),
        )

        self.assertEqual("passed", result["result"])
        for run in result["test_runs"]:
            self.assertEqual(
                {self.c8_test_id, report_only_cpt_id},
                set(run["test_results"]),
            )

    def test_migrated_report_only_tests_require_review_checks(self):
        report_only_id = "app:com.example.OrderSpec#an order can be paid (3DS)"
        c7_junit = (
            "<testsuite>"
            '<testcase classname="com.example.OrderTest" name="testOrder" />'
            '<testcase classname="com.example.OrderSpec" '
            'name="an order can be paid (3DS)" />'
            "</testsuite>"
        )
        self.configure_test_run(c7_junit)
        self.add_report_only_inventory_test(
            report_only_id,
            "app/src/test/groovy/com/example/OrderSpec.groovy",
            "Spock feature",
        )
        self.assertEqual(0, self.record_c7_baseline())
        report_only_cpt_id = (
            "app:com.example.OrderCptSpec#an order can be paid (3DS)"
        )
        mapping = gate.read_test_mapping(self.root, required=True)
        report_only_test = next(
            test for test in mapping["tests"] if test.get("c7_id") == report_only_id
        )
        report_only_test.update(
            status="migrated",
            c8_ids=[report_only_cpt_id],
            mocks={"c7": [], "c8": []},
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)
        self.map_test_to_cpt()

        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        plan = gate.requirements(self.root, evidence)
        mapped_c8_ids = gate.mapped_cpt_test_ids(
            plan.test_contract,
            gate.test_rows_by_id(gate.read_test_mapping(self.root, required=True)),
        )

        self.assertIn(
            ("test", "app:com.example.OrderSpec", "assertion_strength", None),
            plan.required,
        )
        self.assertIn(
            ("test", report_only_id, "mock_boundary", None),
            plan.required,
        )
        self.assertIn(report_only_cpt_id, mapped_c8_ids)

    def test_captured_migrated_report_only_test_requires_repeat_in_its_suite(self):
        report_only_id = "app:com.example.OrderSpec#legacy"
        c7_junit = (
            "<testsuite>"
            '<testcase classname="com.example.OrderTest" name="testOrder" />'
            '<testcase classname="com.example.OrderSpec" name="legacy" />'
            "</testsuite>"
        )
        self.configure_test_run(c7_junit)
        self.add_report_only_inventory_test(
            report_only_id,
            "app/src/test/groovy/com/example/OrderSpec.groovy",
            "Spock feature",
        )
        self.assertEqual(0, self.record_c7_baseline())

        mapping = gate.read_test_mapping(self.root, required=True)
        migrated_test = next(
            test for test in mapping["tests"] if test.get("c7_id") == self.c7_test_id
        )
        migrated_test.update(
            status="retired",
            c8_ids=[],
            retirement={
                "reason": "The behavior is no longer required.",
                "approved_by": "operator",
            },
        )
        report_only_test = next(
            test for test in mapping["tests"] if test.get("c7_id") == report_only_id
        )
        report_only_test.update(
            status="migrated",
            c8_ids=["app:com.example.OrderCptSpec#legacy"],
            mocks={"c7": [], "c8": []},
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)

        plan = gate.requirements(
            self.root,
            json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8")),
        )

        self.assertNotIn(
            report_only_id,
            plan.test_contract["suites"][("app", "unit")]["test_ids"],
        )
        self.assertIn(
            report_only_id,
            mapping["baseline"]["suites"][0]["test_results"],
        )
        self.assertIn(("module", "app", "test_repeat", "unit"), plan.required)

    def test_migrated_report_only_test_requires_a_c7_baseline(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_handling="Report only",
        )
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        mapping = gate.empty_test_mapping(inventory)
        mapping["tests"] = [
            {
                "c7_id": self.c7_test_id,
                "test_kind": "process test",
                "handling": "Report only",
                "c7_result": None,
                "c8_ids": [self.c8_test_id],
                "mocks": {"c7": [], "c8": []},
                "status": "migrated",
            }
        ]
        write_json(self.root / gate.TEST_MAPPING, mapping)

        plan = gate.requirements(self.root, self.plan)
        baseline_key = ("module", "app", "c7_baseline", "unit")
        self.assertIn(baseline_key, plan.required)
        issues = gate.test_parity_issues(plan, {}, mapping)
        missing_baseline_issue = (
            f"{self.c7_test_id}: migrated Report only test requires a captured C7 baseline"
        )
        self.assertIn(missing_baseline_issue, issues)

        self.assertEqual(0, self.record_c7_baseline())
        recorded = gate.read_test_mapping(self.root, required=True)
        recorded_test = next(
            test for test in recorded["tests"] if test.get("c7_id") == self.c7_test_id
        )
        self.assertEqual("passed", recorded_test["c7_result"])

    def test_retired_report_only_test_requires_baseline_when_validation_is_enabled(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_handling="Report only",
        )
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        mapping = gate.empty_test_mapping(inventory)
        mapping["tests"] = [
            {
                "c7_id": self.c7_test_id,
                "test_kind": "process test",
                "handling": "Report only",
                "c7_result": None,
                "c8_ids": [],
                "mocks": {"c7": [], "c8": []},
                "status": "retired",
                "retirement": {
                    "reason": "The behavior is no longer required.",
                    "approved_by": "migration owner",
                },
            },
            {"status": "added", "suite": "unit", "c8_ids": [self.c8_test_id]},
        ]
        write_json(self.root / gate.TEST_MAPPING, mapping)

        plan = gate.requirements(self.root, self.plan)

        self.assertIn(("project", ".", "test_parity", None), plan.required)
        self.assertIn(("module", "app", "c7_baseline", "unit"), plan.required)
        issues = gate.test_parity_issues(plan, {}, mapping)
        self.assertIn("('app', 'unit'): C7 baseline has not run", issues)
        self.assertIn(
            f"{self.c7_test_id}: retired Report only test requires a captured C7 baseline",
            issues,
        )

    def test_retired_report_only_only_test_does_not_enable_validation_without_mode(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_handling="Report only",
        )
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        mapping = gate.empty_test_mapping(inventory)
        mapping["tests"] = [
            {
                "c7_id": self.c7_test_id,
                "test_kind": "process test",
                "handling": "Report only",
                "c7_result": None,
                "c8_ids": [],
                "mocks": {"c7": [], "c8": []},
                "status": "retired",
                "retirement": {
                    "reason": "The behavior is no longer required.",
                    "approved_by": "migration owner",
                },
            }
        ]
        write_json(self.root / gate.TEST_MAPPING, mapping)

        plan = gate.requirements(self.root, self.plan)

        self.assertIsNone(plan.test_contract["mode"])
        self.assertEqual(set(), gate.mapped_migrated_test_ids(mapping))
        self.assertEqual(set(), gate.expected_cpt_test_ids(mapping))
        self.assertNotIn(("module", "app", "c7_baseline", "unit"), plan.required)
        self.assertNotIn(("project", ".", "test_parity", None), plan.required)
        self.assertNotIn(("project", ".", "coverage_parity", None), plan.required)
        self.assertNotIn(("module", "app", "test_repeat", "unit"), plan.required)
        self.assertNotIn(("project", ".", "test_freeze", None), plan.required)

        self.complete_required_checks()
        self.assertEqual(0, self.audit())

    def test_retired_report_only_without_test_run_mode_can_reach_ready(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_handling="Report only",
        )
        inventory_path = self.root / gate.INVENTORY
        inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
        mapping = gate.empty_test_mapping(inventory)
        mapping["tests"] = [
            {
                "c7_id": self.c7_test_id,
                "test_kind": "process test",
                "handling": "Report only",
                "c7_result": None,
                "c8_ids": [],
                "status": "retired",
                "retirement": {
                    "reason": "The behavior is no longer required.",
                    "approved_by": "migration owner",
                },
            }
        ]
        write_json(self.root / gate.TEST_MAPPING, mapping)
        self.assertNotIn("test_run_mode", inventory)
        write_json(inventory_path, inventory)

        plan = gate.requirements(self.root, self.plan)
        self.assertFalse(
            any("test_run_mode is required" in issue for issue in plan.issues),
            plan.issues,
        )
        if not plan.issues:
            self.complete_required_checks()
        self.assertEqual(0, self.audit())
        summary = self.summary()
        self.assertEqual("READY", summary["gate"])

    def test_migrate_test_inventory_requires_an_explicit_test_run_mode(self):
        for handling in ("Migrate", "Migrate to CPT", "Migrate (lower priority)"):
            with self.subTest(handling=handling):
                self.configure_test_run(
                    '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
                    test_handling=handling,
                )
                inventory_path = self.root / gate.INVENTORY
                inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
                inventory.pop("test_run_mode")
                write_json(inventory_path, inventory)

                plan = gate.requirements(self.root, self.plan)

                self.assertIn(
                    "Step 2 test_run_mode is required when the Test Inventory contains migratable tests",
                    plan.issues,
                )

    def test_out_of_scope_inventory_without_test_run_mode_is_allowed(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_handling="Not part of test migration",
        )
        inventory_path = self.root / gate.INVENTORY
        inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
        self.assertNotIn("test_run_mode", inventory)
        write_json(inventory_path, inventory)

        contract = gate.test_contract(
            self.root, json.loads(inventory_path.read_text(encoding="utf-8"))
        )

        self.assertIsNone(contract["mode"])
        self.assertEqual("Not part of test migration", contract["tests"][0]["handling"])

    def test_missing_test_run_mode_rejects_a_malformed_test_inventory(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_handling="Report only",
        )
        report_path = self.root / gate.REPORT
        report = report_path.read_text(encoding="utf-8")
        malformed_report = report.replace(
            "| Test ID | File | Test kind | Signals | Models | Handling | Notes |",
            "| Test ID | File | Test kind | Signals | Models | Disposition | Notes |",
        )
        self.assertNotEqual(report, malformed_report)
        report_path.write_text(malformed_report, encoding="utf-8")
        inventory_path = self.root / gate.INVENTORY
        inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
        self.assertNotIn("test_run_mode", inventory)
        write_json(inventory_path, inventory)

        plan = gate.requirements(self.root, self.plan)

        self.assertTrue(
            any("MIGRATION_REPORT.md Test Inventory" in issue for issue in plan.issues),
            plan.issues,
        )

    def assert_missing_test_run_mode_rejects_malformed_inventory(
        self, heading, test_id=None, include_empty_table=False
    ):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_handling="Report only",
        )
        report_path = self.root / gate.REPORT
        report = report_path.read_text(encoding="utf-8")
        malformed_report = report.replace(
            "| Test ID | File | Test kind | Signals | Models | Handling | Notes |",
            "| Test ID | File | Test kind | Signals | Models | Disposition | Notes |",
        )
        if test_id is not None:
            malformed_report = malformed_report.replace(
                f"`{self.c7_test_id}`",
                f"`{test_id}`",
            )
        if heading is None:
            malformed_report = malformed_report.replace("## Test Inventory\n\n", "")
        else:
            malformed_report = malformed_report.replace("## Test Inventory", heading)
        if include_empty_table:
            malformed_report = (
                "# Migration report\n\n"
                "## Test Inventory\n\n"
                "| Test ID | File | Test kind | Handling |\n"
                "|---|---|---|---|\n\n"
                + malformed_report.replace("# Migration report\n\n", "", 1)
            )
        self.assertNotEqual(report, malformed_report)
        report_path.write_text(malformed_report, encoding="utf-8")
        inventory_path = self.root / gate.INVENTORY
        inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
        self.assertNotIn("test_run_mode", inventory)
        write_json(inventory_path, inventory)
        with redirect_stdout(StringIO()):
            with self.assertRaisesRegex(gate.EvidenceError, "Test Inventory"):
                gate.initialize(self.root, reset_source_snapshot=True)

        plan = gate.requirements(self.root, self.plan)
        if not plan.issues:
            self.complete_required_checks()
        self.assertEqual(1, self.audit())
        summary = self.summary()
        self.assertEqual("NOT READY", summary["gate"])
        self.assertTrue(
            any("Test Inventory" in issue for issue in summary["issues"]),
            summary["issues"],
        )

    def test_missing_test_run_mode_rejects_malformed_inventory_after_empty_table(self):
        self.assert_missing_test_run_mode_rejects_malformed_inventory(
            None,
            include_empty_table=True,
        )

    def test_test_inventory_parses_cucumber_feature_path_ids(self):
        test_id = "app:src/test/resources/features/order.feature#Order is paid@L12"
        file_path = "app/src/test/resources/features/order.feature"
        source_file = self.root / file_path
        source_file.parent.mkdir(parents=True, exist_ok=True)
        source_file.write_text("Feature: Order\n", encoding="utf-8")
        (self.root / gate.REPORT).write_text(
            "# Migration report\n\n"
            "## Test Inventory\n\n"
            "| Test ID | File | Test kind | Handling |\n"
            "|---|---|---|---|\n"
            f"| `{test_id}` | `{file_path}` | Cucumber scenario | Migrate |\n",
            encoding="utf-8",
        )

        test = gate.test_report_inventory(self.root)[0]

        self.assertEqual(test_id, test["id"])
        self.assertEqual("src/test/resources/features/order.feature", test["class_name"])
        self.assertEqual("Order is paid@L12", test["method"])

    def test_c7_baseline_can_be_captured_before_mapping_report_only_tests(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_handling="Report only",
        )

        self.assertEqual(0, self.record_c7_baseline())

        mapping = gate.read_test_mapping(self.root, required=True)
        recorded_test = next(
            test for test in mapping["tests"] if test.get("c7_id") == self.c7_test_id
        )
        self.assertEqual("manual", recorded_test["status"])
        self.assertEqual("passed", recorded_test["c7_result"])

    def test_all_report_only_migrated_tests_require_validation(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_handling="Report only",
        )
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        mapping = gate.empty_test_mapping(inventory)
        mapping["tests"] = [
            {
                "c7_id": self.c7_test_id,
                "test_kind": "process test",
                "handling": "Report only",
                "c7_result": None,
                "c8_ids": [self.c8_test_id],
                "mocks": {"c7": [], "c8": []},
                "status": "migrated",
            }
        ]
        write_json(self.root / gate.TEST_MAPPING, mapping)

        plan = gate.requirements(self.root, self.plan)
        for key in (
            ("module", "app", "test_repeat", "unit"),
            ("project", ".", "test_freeze", None),
            ("project", ".", "test_parity", None),
            ("project", ".", "coverage_parity", None),
            ("test", "app:com.example.OrderTest", "assertion_strength", None),
            ("test", self.c7_test_id, "mock_boundary", None),
        ):
            with self.subTest(key=key):
                self.assertIn(key, plan.required)

        repeat_result = gate.record_test_repeat(
            self.root,
            plan,
            Namespace(
                target="app",
                scenario="unit",
                command=self.cpt_command(),
                timeout=None,
            ),
            mapping,
        )
        self.assertEqual("passed", repeat_result["result"])
        self.assertEqual(
            {self.c8_test_id},
            set(repeat_result["test_runs"][0]["test_results"]),
        )
        gate.record_test_freeze(self.root, plan, mapping)
        self.assertIn(self.c7_test_file_path, mapping["freeze"]["files"])

        with redirect_stdout(StringIO()):
            self.assertEqual(1, gate.report(self.root))
        issues = json.loads((self.root / gate.SUMMARY).read_text(encoding="utf-8"))[
            "issues"
        ]
        self.assertIn(
            f"{self.c7_test_id}: mapped CPT test {self.c8_test_id} is missing from both runs",
            issues,
        )

    def test_test_id_parts_rejects_blank_method_names(self):
        for test_id in ("app:com.example.OrderSpec#", "app:com.example.OrderSpec#  "):
            with self.subTest(test_id=test_id):
                with self.assertRaisesRegex(
                    gate.EvidenceError, "Invalid Test Inventory ID"
                ):
                    gate.test_id_parts(test_id)

    def test_test_id_parts_accepts_cucumber_feature_paths(self):
        self.assertEqual(
            ("app", "src/test/resources/features/order.feature", "Order is paid@L12"),
            gate.test_id_parts(
                "app:src/test/resources/features/order.feature#Order is paid@L12"
            ),
        )

    def test_test_id_parts_rejects_unsafe_cucumber_feature_paths(self):
        for path in (
            "../outside.feature",
            "/outside.feature",
            "C:/outside.feature",
            r"src\test\resources\order.feature",
        ):
            with self.subTest(path=path):
                with self.assertRaisesRegex(
                    gate.EvidenceError, "Invalid Test Inventory ID"
                ):
                    gate.test_id_parts(f"app:{path}#Order is paid@L12")

    def test_test_id_parts_preserves_hashes_in_display_names(self):
        self.assertEqual(
            (
                "app",
                "com.example.RunCucumberTest",
                "Scenario: reconcile #2",
            ),
            gate.test_id_parts(
                "app:com.example.RunCucumberTest#Scenario: reconcile #2"
            ),
        )

    def test_test_run_mode_validation_uses_one_error_message(self):
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        inventory["test_run_mode"] = "unsupported"
        expected = "Step 2 test_run_mode must be 'run' or 'migrate_only'"

        with self.assertRaises(gate.EvidenceError) as error:
            gate.test_contract(self.root, inventory)
        self.assertEqual(expected, str(error.exception))

        write_json(self.root / gate.INVENTORY, inventory)
        with self.assertRaises(gate.EvidenceError) as error:
            gate.initialize(self.root)
        self.assertEqual(expected, str(error.exception))

    def test_invalid_test_suite_shape_is_reported_without_crashing(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        inventory["test_suites"] = {}
        write_json(self.root / gate.INVENTORY, inventory)

        self.assertEqual(1, self.audit())
        self.assertIn("Step 2 test_suites must be an array", self.summary()["issues"])

    def test_test_parity_pass_writes_report_tables(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt(
            mocks_c7=['Mocks.register("orderService", mock)'],
            mocks_c8=["@MockitoBean OrderService"],
        )
        self.assertEqual(
            0,
            self.submit(
                ("project", ".", "test_freeze", None),
                command=[],
            ),
        )
        cpt_coverage = (
            '{"processCoverages":[],"decisionCoverages":[{"decisionDefinitionId":"approval",'
            '"matchedRuleIds":["rule-1"],"matchedRuleIndices":[0],"coverage":1.0}]}'
        )
        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "test_repeat", "unit"),
                command=self.cpt_command(
                    first_coverage=cpt_coverage,
                    second_coverage=cpt_coverage,
                ),
            ),
        )
        class_target = "app:com.example.OrderTest"
        self.assertEqual(
            0,
            self.submit(
                ("test", class_target, "assertion_strength", None),
                action="review",
                note=f"Reviewed assertions for {class_target}.",
            ),
        )
        self.assertEqual(
            0,
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            ),
        )
        self.assertEqual(
            0,
            self.submit(("project", ".", "test_parity", None), command=[]),
        )
        self.assertEqual(
            0,
            self.submit(("project", ".", "coverage_parity", None), command=[]),
        )
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        report = (self.root / gate.REPORT).read_text(encoding="utf-8")
        self.assertIn("## Test Parity", report)
        self.assertIn("run 1: passed, run 2: passed", report)
        self.assertIn("## Test Coverage", report)
        self.assertIn("No Camunda 7 coverage baseline", report)
        self.assertIn("DMN approval", report)
        self.assertIn("rule-1", report)

    def test_test_parity_fails_when_a_passing_c7_test_has_no_passing_cpt_test(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        self.assertEqual(
            0,
            self.submit(("project", ".", "test_freeze", None), command=[]),
        )
        skipped = (
            '<testsuite><testcase classname="com.example.OrderCptTest" name="testOrder">'
            "<skipped /></testcase></testsuite>"
        )
        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "test_repeat", "unit"),
                command=self.cpt_command(first_junit=skipped),
            ),
        )
        class_target = "app:com.example.OrderTest"
        self.assertEqual(
            0,
            self.submit(
                ("test", class_target, "assertion_strength", None),
                action="review",
                note=f"Reviewed assertions for {class_target}.",
            ),
        )
        self.assertEqual(
            0,
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            ),
        )
        self.assertEqual(
            1,
            self.submit(("project", ".", "test_parity", None), command=[]),
        )
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        checks = gate.load_checks(
            self.root, evidence, gate.requirements(self.root, self.plan), []
        )
        mapping = gate.read_test_mapping(self.root, required=True)
        issues = gate.test_parity_issues(
            gate.requirements(self.root, self.plan), checks, mapping
        )
        self.assertTrue(any("must pass in both runs" in issue for issue in issues), issues)
        self.assertTrue(any(self.c8_test_id in issue for issue in issues), issues)
        self.assertEqual(1, self.audit())
        self.assertTrue(
            any(self.c8_test_id in issue for issue in self.summary()["issues"])
        )

    def _exercise_approved_test_continuation(self, baseline_status):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        if baseline_status == "failed":
            self.c7_command = [
                sys.executable,
                "-c",
                "print('No fresh JUnit report was produced')",
            ]
            inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
            inventory["test_suites"][0]["command"] = self.c7_command
            write_json(self.root / gate.INVENTORY, inventory)
            with redirect_stdout(StringIO()):
                self.assertEqual(0, gate.initialize(self.root, reset_source_snapshot=True))

        baseline_key = ("module", "app", "c7_baseline", "unit")
        if baseline_status == "blocked":
            self.assertEqual(
                1,
                self.submit(
                    baseline_key,
                    action="block",
                    reason="The database required by the baseline suite is unavailable.",
                ),
            )
        else:
            self.assertEqual(1, self.record_c7_baseline())

        mapping = gate.read_test_mapping(self.root, required=True)
        mapping["baseline"]["continue_without_baseline"] = {
            "decision": "continue",
            "reason": "The database required by the baseline suite is unavailable.",
            "approved_by": "operator",
        }
        write_json(self.root / gate.TEST_MAPPING, mapping)

        self.map_test_to_cpt(
            mocks_c7=['Mocks.register("orderService", mock)'],
            mocks_c8=["@MockitoBean OrderService"],
        )
        self.assertEqual(
            0,
            self.submit(("project", ".", "test_freeze", None), command=[]),
        )
        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "test_repeat", "unit"),
                command=self.cpt_command(),
            ),
        )
        class_target = "app:com.example.OrderTest"
        self.assertEqual(
            0,
            self.submit(
                ("test", class_target, "assertion_strength", None),
                action="review",
                note=f"Reviewed assertions for {class_target}.",
            ),
        )
        self.assertEqual(
            0,
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            ),
        )
        self.assertEqual(
            1,
            self.submit(("project", ".", "test_parity", None), command=[]),
        )
        self.submit(("project", ".", "coverage_parity", None), command=[])

        self.assertEqual(1, self.audit())
        summary = self.summary()
        self.assertEqual("NOT READY", summary["gate"])
        checks = summary["checks"]
        baseline = next(check for check in checks if check["kind"] == "c7_baseline")
        self.assertEqual(baseline_status, baseline["result"])
        self.assertTrue(
            any(
                check["kind"] == "test_freeze" and check["result"] == "passed"
                for check in checks
            )
        )
        self.assertTrue(
            any(
                check["kind"] == "test_repeat" and check["result"] == "passed"
                for check in checks
            )
        )
        self.assertTrue(any(check["kind"] == "coverage_parity" for check in checks))
        parity = next(check for check in checks if check["kind"] == "test_parity")
        self.assertEqual("failed", parity["result"])
        self.assertIn(f"C7 baseline is {baseline_status}", parity["reason"])

    def test_approved_continuation_allows_follow_up_checks_after_blocked_baseline(self):
        self._exercise_approved_test_continuation("blocked")

    def test_approved_continuation_allows_follow_up_checks_after_failed_baseline(self):
        self._exercise_approved_test_continuation("failed")

    def test_test_parity_rejects_a_cpt_test_shared_by_migrated_c7_tests(self):
        first_c7_id = "app:com.example.OrderTest#testFirst"
        second_c7_id = "app:com.example.OrderTest#testSecond"
        cpt_id = "app:com.example.OrderCptTest#testShared"
        suite_key = ("app", "unit")
        test_results = {
            test_id: {
                "result": "passed",
                "invocations": ["passed"],
                "invocation_count": 1,
            }
            for test_id in (first_c7_id, second_c7_id)
        }
        baseline = {
            "module": "app",
            "suite": "unit",
            "result": "passed",
            "test_results": test_results,
            "coverage_by_process": {},
        }
        plan = Namespace(
            test_contract={
                "tests": [
                    {
                        "id": test_id,
                        "module": "app",
                        "test_kind": "process test",
                        "handling": "Migrate",
                    }
                    for test_id in (first_c7_id, second_c7_id)
                ],
                "suites": {
                    suite_key: {
                        "module": "app",
                        "name": "unit",
                        "migrate_test_ids": [first_c7_id, second_c7_id],
                    }
                },
                "test_suites": {
                    first_c7_id: [suite_key],
                    second_c7_id: [suite_key],
                },
            }
        )
        checks = {
            ("module", "app", "c7_baseline", "unit"): (None, baseline),
            ("module", "app", "test_repeat", "unit"): (
                None,
                {
                    "test_runs": [
                        {"test_results": {cpt_id: {"result": "passed"}}},
                        {"test_results": {cpt_id: {"result": "passed"}}},
                    ]
                },
            ),
        }
        mapping = {
            "baseline": {"suites": [baseline]},
            "tests": [
                {
                    "c7_id": test_id,
                    "test_kind": "process test",
                    "handling": "Migrate",
                    "c7_result": "passed",
                    "status": "migrated",
                    "c8_ids": [cpt_id],
                }
                for test_id in (first_c7_id, second_c7_id)
            ],
        }

        issues = gate.test_parity_issues(plan, checks, mapping)

        self.assertTrue(
            any("mapped from multiple test parity entries" in issue for issue in issues),
            issues,
        )

    def test_test_parity_rejects_cpt_ids_shared_with_added_tests(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        added_cpt_id = "app:com.example.OrderCptTest#testAdded"
        mapping = gate.read_test_mapping(self.root, required=True)
        mapping["tests"].extend(
            [
                {"status": "added", "suite": "unit", "c8_ids": [self.c8_test_id]},
                {"status": "added", "suite": "unit", "c8_ids": [added_cpt_id]},
                {"status": "added", "suite": "unit", "c8_ids": [added_cpt_id]},
                {"status": "added", "suite": "unit", "c8_ids": [added_cpt_id, added_cpt_id]},
            ]
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)
        self.assertEqual(
            0,
            self.submit(("project", ".", "test_freeze", None), command=[]),
        )
        junit = (
            '<testsuite><testcase classname="com.example.OrderCptTest" name="testOrder" />'
            '<testcase classname="com.example.OrderCptTest" name="testAdded" /></testsuite>'
        )
        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "test_repeat", "unit"),
                command=self.cpt_command(first_junit=junit),
            ),
        )
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        plan = gate.requirements(self.root, evidence)
        check_issues = []
        checks = gate.load_checks(self.root, evidence, plan, check_issues)
        self.assertEqual([], check_issues)

        issues = gate.test_parity_issues(plan, checks, mapping)

        ownership_issues = [
            issue for issue in issues
            if "mapped from multiple test parity entries" in issue
        ]
        self.assertEqual(3, len(ownership_issues), issues)
        self.assertTrue(
            any("Added CPT tests need distinct c8_ids" in issue for issue in issues),
            issues,
        )

    def test_retired_test_needs_approval(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        mapping = gate.read_test_mapping(self.root, required=True)
        test = next(item for item in mapping["tests"] if item["c7_id"] == self.c7_test_id)
        test.update(status="retired", c8_ids=[], retirement={"reason": "", "approved_by": ""})
        write_json(self.root / gate.TEST_MAPPING, mapping)
        plan = gate.requirements(self.root, self.plan)
        checks = gate.load_checks(
            self.root,
            json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8")),
            plan,
            [],
        )
        issues = gate.test_parity_issues(plan, checks, mapping)
        self.assertTrue(any("retired test needs an approved reason" in issue for issue in issues))
        test["retirement"] = {
            "reason": "The CMMN case has no Camunda 8 equivalent.",
            "approved_by": "operator",
        }
        write_json(self.root / gate.TEST_MAPPING, mapping)
        self.assertEqual([], gate.test_parity_issues(plan, checks, mapping))

    def test_approved_retired_test_completes_without_migration_reviews(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        mapping = gate.read_test_mapping(self.root, required=True)
        test = next(item for item in mapping["tests"] if item["c7_id"] == self.c7_test_id)
        test.update(
            status="retired",
            c8_ids=[],
            retirement={
                "reason": "The behavior is no longer required.",
                "approved_by": "operator",
            },
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)

        plan = gate.requirements(self.root, self.plan)
        class_target = "app:com.example.OrderTest"
        self.assertNotIn(("test", class_target, "assertion_strength", None), plan.required)
        self.assertNotIn(("test", self.c7_test_id, "mock_boundary", None), plan.required)
        self.assertIn(("project", ".", "test_parity", None), plan.required)

        self.complete_required_checks()
        self.assertEqual(0, self.audit())

    def test_approved_retirement_without_cpt_artifacts_keeps_c7_parity(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            c7_coverage=(
                '{"suites":[{"coverage":[{"source":"FLOW_NODE",'
                '"modelKey":"p","definitionKey":"Start"}]}]}'
            ),
        )
        self.assertEqual(0, self.record_c7_baseline())
        mapping = gate.read_test_mapping(self.root, required=True)
        test = next(item for item in mapping["tests"] if item["c7_id"] == self.c7_test_id)
        test.update(
            status="retired",
            c8_ids=[],
            retirement={
                "reason": "The behavior is no longer required.",
                "approved_by": "operator",
            },
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)
        (self.root / self.c7_test_file_path).unlink()
        (self.root / "app/src/test/resources/order.bpmn").unlink()

        plan = gate.requirements(
            self.root,
            json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8")),
        )

        self.assertIn(("module", "app", "c7_baseline", "unit"), plan.required)
        self.assertIn(("project", ".", "test_parity", None), plan.required)
        self.assertIn(("project", ".", "coverage_parity", None), plan.required)
        self.assertNotIn(("project", ".", "test_freeze", None), plan.required)
        self.assertNotIn(("module", "app", "test_repeat", "unit"), plan.required)
        self.assertEqual({}, gate.current_test_files(self.root, plan, mapping))

        self.complete_required_checks()
        self.assertEqual(0, self.audit())

    def test_added_cpt_tests_select_test_repeat_only_for_their_suite(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_handling="Report only",
        )
        self.plan["modules"][0]["test_suites"].append(
            {"name": "integration", "requires_docker": False}
        )
        added_test = {
            "status": "added",
            "suite": "integration",
            "c8_ids": ["app:com.example.OrderIT#testOrder"],
        }
        write_json(
            self.root / gate.TEST_MAPPING,
            {
                "schema_version": 1,
                "baseline": {"suites": []},
                "tests": [added_test],
                "freeze": {"files": {}},
                "test_changes": [],
                "mock_changes": [],
            },
        )

        plan = gate.requirements(self.root, self.plan)

        self.assertIn(("module", "app", "test_repeat", "integration"), plan.required)
        self.assertIn(("module", "app", "tests", "unit"), plan.required)
        self.assertNotIn(("module", "app", "test_repeat", "unit"), plan.required)

        mapping = gate.read_test_mapping(self.root, required=True)
        passing_run = {
            "test_results": {"app:com.example.OrderIT#testOrder": {"result": "passed"}}
        }
        for suite_name in ("integration", "unit"):
            with self.subTest(suite=suite_name):
                checks = {
                    ("module", "app", "test_repeat", suite_name): (
                        None,
                        {"test_runs": [passing_run, passing_run]},
                    )
                }
                issues = gate.test_parity_issues(plan, checks, mapping)
                self.assertEqual(
                    suite_name != "integration",
                    "Added CPT test app:com.example.OrderIT#testOrder must pass in both runs"
                    in issues,
                    issues,
                )

        mapping["tests"][0]["suite"] = "missing"
        self.assertIn(
            "Added CPT test app:com.example.OrderIT#testOrder names unknown suite "
            "missing in module app",
            gate.test_parity_issues(plan, {}, mapping),
        )
        del mapping["tests"][0]["suite"]
        self.assertIn(
            "Added CPT tests need the name of the suite that runs them",
            gate.test_parity_issues(plan, {}, mapping),
        )

    def test_test_inventory_rejects_a_symlinked_report(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        outside = self.root / "outside.md"
        (self.root / gate.REPORT).replace(outside)
        (self.root / gate.REPORT).symlink_to(outside)

        with self.assertRaisesRegex(gate.EvidenceError, "Refusing symlinked MIGRATION_REPORT.md"):
            gate.test_report_inventory(self.root)

    def test_every_ledger_read_checks_its_c7_snapshot_binding(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        mapping = gate.read_test_mapping(self.root, required=True)
        for field, message in (
            ("source_digest", "belongs to a different C7 source snapshot"),
            ("commit", "C7 commit differs from the Step 2 snapshot"),
        ):
            with self.subTest(field=field):
                edited = json.loads(json.dumps(mapping))
                edited["baseline"][field] = "edited"
                write_json(self.root / gate.TEST_MAPPING, edited)
                with self.assertRaisesRegex(gate.EvidenceError, message):
                    gate.read_test_mapping(self.root)
                plan = gate.requirements(self.root, self.plan)
                self.assertTrue(
                    any(message in issue for issue in plan.issues), plan.issues
                )

    def test_added_only_cpt_ledger_enables_validation_gates(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_handling="Report only",
        )
        added_test_id = "app:com.example.OrderCptTest#testOrder"
        mapping = {
            "schema_version": 1,
            "baseline": {"suites": []},
            "tests": [{"status": "added", "suite": "unit", "c8_ids": [added_test_id]}],
            "freeze": {"files": {}},
            "test_changes": [],
            "mock_changes": [],
        }
        write_json(self.root / gate.TEST_MAPPING, mapping)
        added_test_file = self.root / "app/src/test/java/com/example/OrderCptTest.java"
        added_test_file.parent.mkdir(parents=True, exist_ok=True)
        added_test_file.write_text("class OrderCptTest {}\n", encoding="utf-8")

        plan = gate.requirements(
            self.root,
            json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8")),
        )

        self.assertEqual(set(), gate.mapped_migrated_test_ids(mapping))
        self.assertEqual({added_test_id}, gate.expected_cpt_test_ids(mapping))
        self.assertNotIn(("module", "app", "c7_baseline", "unit"), plan.required)
        self.assertIn(("module", "app", "test_repeat", "unit"), plan.required)
        self.assertNotIn(("module", "app", "tests", "unit"), plan.required)
        self.assertIn(("project", ".", "test_freeze", None), plan.required)
        self.assertIn(("project", ".", "test_parity", None), plan.required)
        self.assertIn(("project", ".", "coverage_parity", None), plan.required)
        self.assertIn(
            "app/src/test/java/com/example/OrderCptTest.java",
            gate.current_test_files(self.root, plan, mapping),
        )

        self.assertEqual(
            0, self.submit(("project", ".", "test_freeze", None), command=[])
        )
        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "test_repeat", "unit"),
                command=self.cpt_command(first_coverage='{"processCoverages":[]}'),
            ),
        )
        self.assertEqual(
            0, self.submit(("project", ".", "test_parity", None), command=[])
        )
        self.assertEqual(
            0, self.submit(("project", ".", "coverage_parity", None), command=[])
        )
        self.complete_required_checks()
        self.assertEqual(0, self.audit())

    def test_added_entry_without_c8_ids_is_still_validated(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_handling="Report only",
        )
        mapping = {
            "schema_version": 1,
            "baseline": {"suites": []},
            "tests": [{"status": "added", "suite": "unit", "c8_ids": []}],
            "freeze": {"files": {}},
            "test_changes": [],
            "mock_changes": [],
        }
        write_json(self.root / gate.TEST_MAPPING, mapping)

        plan = gate.requirements(
            self.root,
            json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8")),
        )
        parity_issues = gate.test_parity_issues(plan, {}, mapping)

        self.assertIn(("project", ".", "test_parity", None), plan.required)
        self.assertTrue(
            any("Added CPT tests need one or more c8_ids" in issue for issue in parity_issues),
            parity_issues,
        )

    def test_added_cpt_test_keeps_post_migration_checks_after_retirement(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        mapping = gate.read_test_mapping(self.root, required=True)
        test = next(item for item in mapping["tests"] if item["c7_id"] == self.c7_test_id)
        test.update(
            status="retired",
            c8_ids=[],
            retirement={
                "reason": "The behavior is no longer required.",
                "approved_by": "operator",
            },
        )
        added_test_id = "app:com.example.AddedTest#testAdded"
        mapping["tests"].append({"status": "added", "suite": "unit", "c8_ids": [added_test_id]})
        write_json(self.root / gate.TEST_MAPPING, mapping)
        added_test_file = self.root / "app/src/test/java/com/example/AddedTest.java"
        added_test_file.parent.mkdir(parents=True, exist_ok=True)
        added_test_file.write_text("class AddedTest {}\n", encoding="utf-8")

        plan = gate.requirements(
            self.root,
            json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8")),
        )

        self.assertIn(("module", "app", "c7_baseline", "unit"), plan.required)
        self.assertIn(("project", ".", "test_parity", None), plan.required)
        self.assertIn(("project", ".", "coverage_parity", None), plan.required)
        self.assertIn(("project", ".", "test_freeze", None), plan.required)
        self.assertIn(("module", "app", "test_repeat", "unit"), plan.required)
        self.assertIn(
            "app/src/test/java/com/example/AddedTest.java",
            gate.current_test_files(self.root, plan, mapping),
        )

    def test_retired_suite_does_not_inherit_sibling_cpt_tests(self):
        live_test_id = "app:com.example.LiveTest#testLive"
        retired_test_id = "app:com.example.RetiredTest#testRetired"
        mapping = {
            "tests": [
                {
                    "c7_id": live_test_id,
                    "status": "migrated",
                    "c8_ids": ["app:com.example.LiveCptTest#testLive"],
                },
                {
                    "c7_id": retired_test_id,
                    "status": "retired",
                    "c8_ids": [],
                },
            ]
        }

        self.assertTrue(
            gate.suite_has_cpt_tests(
                {"module": "app", "test_ids": [live_test_id]}, mapping
            )
        )
        self.assertFalse(
            gate.suite_has_cpt_tests(
                {"module": "app", "test_ids": [retired_test_id]}, mapping
            )
        )

    def test_captured_report_only_tests_are_scoped_to_their_baseline_suite(self):
        test_id = "app:com.example.OrderSpec#legacy"
        mapping = {
            "baseline": {
                "suites": [
                    {
                        "module": "app",
                        "suite": "unit",
                        "result": "passed",
                        "test_results": {
                            test_id: {
                                "result": "passed",
                                "invocations": ["passed"],
                            }
                        },
                    }
                ]
            },
            "tests": [
                {
                    "c7_id": test_id,
                    "handling": "Report only",
                    "status": "migrated",
                    "c8_ids": ["app:com.example.OrderCptSpec#legacy"],
                }
            ],
        }

        cases = (
            ({"module": "app", "name": "unit", "test_ids": []}, True),
            ({"module": "app", "name": "integration", "test_ids": []}, False),
            ({"module": "other", "name": "unit", "test_ids": []}, False),
        )
        for suite, expected in cases:
            with self.subTest(suite=suite):
                self.assertEqual(expected, gate.suite_has_cpt_tests(suite, mapping))

    def test_baseline_capture_does_not_infer_membership_for_migrated_tests(self):
        captured_test_id = "app:com.example.SharedTest#testShared"
        declared_test_id = "app:com.example.UnitTest#testUnit"
        mapping = {
            "baseline": {
                "suites": [
                    {
                        "module": "app",
                        "suite": "unit",
                        "result": "passed",
                        "test_results": {
                            captured_test_id: {
                                "result": "passed",
                                "invocations": ["passed"],
                            }
                        },
                    }
                ]
            },
            "tests": [
                {
                    "c7_id": captured_test_id,
                    "handling": "Migrate",
                    "status": "migrated",
                    "c8_ids": ["app:com.example.SharedCptTest#testShared"],
                },
                {
                    "c7_id": declared_test_id,
                    "handling": "Migrate",
                    "status": "retired",
                    "c8_ids": [],
                },
            ],
        }

        self.assertFalse(
            gate.suite_has_cpt_tests(
                {"module": "app", "name": "unit", "test_ids": [declared_test_id]},
                mapping,
            )
        )

    def test_retiring_migrated_test_replaces_stale_test_evidence(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        class_target = "app:com.example.OrderTest"
        self.assertEqual(
            0,
            self.submit(("project", ".", "test_freeze", None), command=[]),
        )
        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "test_repeat", "unit"),
                command=self.cpt_command(
                    first_coverage='{"processCoverages":[]}',
                    second_coverage='{"processCoverages":[]}',
                ),
            ),
        )
        self.assertEqual(
            0,
            self.submit(
                ("test", class_target, "assertion_strength", None),
                action="review",
                note=f"Reviewed assertions for {class_target}.",
            ),
        )
        self.assertEqual(
            0,
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            ),
        )
        self.assertEqual(
            0,
            self.submit(("project", ".", "test_parity", None), command=[]),
        )
        self.assertEqual(
            0,
            self.submit(("project", ".", "coverage_parity", None), command=[]),
        )

        mapping = gate.read_test_mapping(self.root, required=True)
        test = next(item for item in mapping["tests"] if item["c7_id"] == self.c7_test_id)
        test.pop("mocks", None)
        test.update(
            status="retired",
            c8_ids=[],
            retirement={
                "reason": "The behavior is no longer required.",
                "approved_by": "operator",
            },
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)
        (self.root / "app/src/test/java/com/example/OrderCptTest.java").unlink()
        (self.root / "app/src/test/resources/order.bpmn").unlink()

        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        stale_issues = []
        plan = gate.requirements(self.root, evidence)
        stale_checks = gate.load_checks(
            self.root, evidence, plan, stale_issues
        )
        self.assertEqual([], stale_issues)
        self.assertNotIn(("project", ".", "test_freeze", None), plan.required)
        self.assertNotIn(("module", "app", "test_repeat", "unit"), plan.required)
        self.assertNotIn(("project", ".", "test_freeze", None), stale_checks)
        self.assertNotIn(("module", "app", "test_repeat", "unit"), stale_checks)
        self.assertNotIn(
            ("test", class_target, "assertion_strength", None), stale_checks
        )
        self.assertNotIn(
            ("test", self.c7_test_id, "mock_boundary", None), stale_checks
        )

        self.assertEqual(
            0,
            self.submit(("project", ".", "test_parity", None), command=[]),
        )
        self.assertEqual(
            0,
            self.submit(("project", ".", "coverage_parity", None), command=[]),
        )

        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        plan = gate.requirements(self.root, evidence)
        issues = []
        checks = gate.load_checks(self.root, evidence, plan, issues)
        self.assertEqual([], issues)
        self.assertNotIn(("project", ".", "test_freeze", None), checks)
        self.assertNotIn(("module", "app", "test_repeat", "unit"), checks)
        self.assertNotIn(("test", class_target, "assertion_strength", None), checks)
        self.assertNotIn(("test", self.c7_test_id, "mock_boundary", None), checks)

    def test_approved_test_changes_invalidate_assertion_and_mock_reviews(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        self.assertEqual(
            0,
            self.submit(("project", ".", "test_freeze", None), command=[]),
        )
        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "test_repeat", "unit"),
                command=self.cpt_command(),
            ),
        )
        class_target = "app:com.example.OrderTest"
        self.assertEqual(
            0,
            self.submit(
                ("test", class_target, "assertion_strength", None),
                action="review",
                note=f"Reviewed assertions for {class_target}.",
            ),
        )
        self.assertEqual(
            0,
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            ),
        )

        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        plan = gate.requirements(self.root, evidence)
        check_issues = []
        checks = gate.load_checks(self.root, evidence, plan, check_issues)
        self.assertEqual([], check_issues)
        review_keys = (
            ("test", class_target, "assertion_strength", None),
            ("test", self.c7_test_id, "mock_boundary", None),
        )
        for key in review_keys:
            self.assertIn(key, checks)

        mapping = gate.read_test_mapping(self.root, required=True)
        mapping_without_test_changes = {
            key: value for key, value in mapping.items() if key != "test_changes"
        }
        self.assertEqual(
            gate.test_mapping_digest(mapping_without_test_changes, "review"),
            gate.test_mapping_digest(mapping, "review"),
        )
        test_path = "app/src/test/java/com/example/OrderCptTest.java"
        test_file = self.root / test_path
        old_hash = mapping["freeze"]["files"][test_path]
        test_file.write_text("class OrderCptTest { void weakened() {} }\n", encoding="utf-8")
        mapping["test_changes"].append(
            {
                "file": test_path,
                "reason": "The operator approved a test assertion update.",
                "old_hash": old_hash,
                "new_hash": f"sha256:{gate.file_digest(test_file)}",
                "approved_by": "operator",
            }
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)
        self.assertEqual([], gate.validate_test_freeze(self.root, plan, mapping))

        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        plan = gate.requirements(self.root, evidence)
        check_issues = []
        checks = gate.load_checks(self.root, evidence, plan, check_issues)
        self.assertEqual([], check_issues)
        for key in review_keys:
            self.assertIn(key, plan.required)
            self.assertFalse(key in checks, f"{key} remained current after test changes")

    def test_replacing_frozen_hashes_invalidates_assertion_and_mock_reviews(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        self.assertEqual(
            0,
            self.submit(("project", ".", "test_freeze", None), command=[]),
        )
        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "test_repeat", "unit"),
                command=self.cpt_command(),
            ),
        )
        class_target = "app:com.example.OrderTest"
        review_keys = (
            ("test", class_target, "assertion_strength", None),
            ("test", self.c7_test_id, "mock_boundary", None),
        )
        for key, note in (
            (review_keys[0], f"Reviewed assertions for {class_target}."),
            (review_keys[1], f"Reviewed C7 test {self.c7_test_id} and its CPT mocks."),
        ):
            self.assertEqual(0, self.submit(key, action="review", note=note))

        test_path = "app/src/test/java/com/example/OrderCptTest.java"
        test_file = self.root / test_path
        test_file.write_text("class OrderCptTest { void weakened() {} }\n", encoding="utf-8")
        mapping = gate.read_test_mapping(self.root, required=True)
        mapping["freeze"]["files"][test_path] = f"sha256:{gate.file_digest(test_file)}"
        write_json(self.root / gate.TEST_MAPPING, mapping)
        plan = gate.requirements(
            self.root, json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        )
        issues = gate.validate_test_freeze(self.root, plan, mapping)
        self.assertTrue(
            any("validator-owned" in issue for issue in issues),
            issues,
        )
        with self.assertRaisesRegex(gate.EvidenceError, "validator-owned"):
            self.submit(("project", ".", "test_freeze", None), command=[])
        for key, note in (
            (review_keys[0], f"Reviewed assertions for {class_target}."),
            (review_keys[1], f"Reviewed C7 test {self.c7_test_id} and its CPT mocks."),
        ):
            self.assertEqual(0, self.submit(key, action="review", note=note))
        self.assertEqual(1, self.audit())
        self.assertTrue(
            any("validator-owned" in issue for issue in self.summary()["issues"])
        )

        mapping = gate.read_test_mapping(self.root, required=True)
        mapping["freeze"]["files"] = {}
        write_json(self.root / gate.TEST_MAPPING, mapping)
        issues = gate.validate_test_freeze(self.root, plan, mapping)
        self.assertTrue(
            any("validator-owned" in issue for issue in issues),
            issues,
        )
        with self.assertRaisesRegex(gate.EvidenceError, "validator-owned"):
            self.submit(("project", ".", "test_freeze", None), command=[])
        self.assertEqual(1, self.audit())
        self.assertTrue(
            any("validator-owned" in issue for issue in self.summary()["issues"])
        )

    def test_test_freeze_requires_approval_for_changed_files(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        self.assertEqual(
            0,
            self.submit(("project", ".", "test_freeze", None), command=[]),
        )
        test_file = self.root / "app/src/test/java/com/example/OrderCptTest.java"
        test_file.write_text("class OrderCptTest { void changed() {} }\n", encoding="utf-8")
        mapping = gate.read_test_mapping(self.root, required=True)
        issues = gate.validate_test_freeze(
            self.root, gate.requirements(self.root, self.plan), mapping
        )
        self.assertTrue(any("changed without an approved test_changes" in issue for issue in issues))
        self.assertEqual(1, self.audit())
        self.assertTrue(
            any(
                "OrderCptTest.java" in issue
                for issue in self.summary()["issues"]
            )
        )
        with self.assertRaisesRegex(gate.EvidenceError, "without an approved test_changes"):
            self.submit(
                ("project", ".", "test_freeze", None),
                command=[],
            )
        old_hash = mapping["freeze"]["files"][
            "app/src/test/java/com/example/OrderCptTest.java"
        ]
        mapping["test_changes"].append(
            {
                "file": "app/src/test/java/com/example/OrderCptTest.java",
                "reason": "The operator approved a required assertion update.",
                "old_hash": old_hash,
                "new_hash": f"sha256:{gate.file_digest(test_file)}",
                "approved_by": "operator",
            }
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)
        self.assertEqual(
            [],
            gate.validate_test_freeze(
                self.root, gate.requirements(self.root, self.plan), mapping
            ),
        )

    def test_test_freeze_tracks_inventory_files_and_custom_suite_roots(self):
        inventory_file = "app/legacy-tests/com/example/OrderTest.java"
        source_root = "app/target/generated-test-sources"
        cpt_file = f"{source_root}/java/com/example/OrderCptTest.java"
        resource_root = "app/target/generated-test-resources"
        for root_path in (source_root, resource_root):
            (self.root / root_path).mkdir(parents=True, exist_ok=True)
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_file_path=inventory_file,
            test_source_roots=[source_root],
            test_resource_roots=[resource_root],
        )

        plan = gate.requirements(self.root, self.plan)
        current = gate.current_test_files(self.root, plan)
        self.assertIn(inventory_file, current)

        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt(cpt_file_path=cpt_file)
        cpt_resource = self.root / resource_root / "order.json"
        cpt_resource.parent.mkdir(parents=True, exist_ok=True)
        cpt_resource.write_text("{}\n", encoding="utf-8")
        unrelated_source = self.root / "app/src/main/java/com/example/Production.java"
        unrelated_source.parent.mkdir(parents=True, exist_ok=True)
        unrelated_source.write_text("class Production {}\n", encoding="utf-8")

        self.assertEqual(
            0,
            self.submit(("project", ".", "test_freeze", None), command=[]),
        )
        mapping = gate.read_test_mapping(self.root, required=True)
        frozen = mapping["freeze"]["files"]
        self.assertIn(cpt_file, frozen)
        resource_file = cpt_resource.relative_to(self.root).as_posix()
        self.assertIn(resource_file, frozen)
        self.assertIn("app/src/test/resources/order.bpmn", frozen)
        self.assertNotIn(unrelated_source.relative_to(self.root).as_posix(), frozen)

        for path in (cpt_file, resource_file):
            frozen_path = self.root / path
            original = frozen_path.read_text(encoding="utf-8")
            frozen_path.write_text(original + "changed\n", encoding="utf-8")
            issues = gate.validate_test_freeze(self.root, plan, mapping)
            self.assertTrue(
                any(
                    f"{path}: frozen test file changed without an approved" in issue
                    for issue in issues
                ),
                issues,
            )
            frozen_path.write_text(original, encoding="utf-8")

    def test_test_freeze_tracks_inventory_tests_under_build_directories(self):
        inventory_file = (
            "app/target/generated-test-sources/java/com/example/OrderTest.java"
        )
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_file_path=inventory_file,
        )

        plan = gate.requirements(self.root, self.plan)
        self.assertIn(inventory_file, gate.current_test_files(self.root, plan))

    def test_test_suite_roots_are_locked_by_source_snapshot(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_source_roots=["app/custom-tests"],
            test_resource_roots=["app/custom-resources"],
        )
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        for root_type in ("test_source_roots", "test_resource_roots"):
            changed = json.loads(json.dumps(inventory))
            changed["test_suites"][0][root_type] = [f"app/changed-{root_type}"]
            with self.subTest(root_type=root_type):
                with self.assertRaisesRegex(
                    gate.EvidenceError, "Test Inventory or C7 suite commands changed"
                ):
                    gate.verify_unchanged_source(self.root, changed)

    def test_test_suite_roots_reject_paths_outside_the_module(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        cases = (
            ("test_source_roots", "../outside"),
            ("test_resource_roots", "models"),
            ("test_source_roots", "app/src/test/resources/order.bpmn"),
        )
        for root_type, path in cases:
            changed = json.loads(json.dumps(inventory))
            changed["test_suites"][0][root_type] = [path]
            with self.subTest(root_type=root_type, path=path):
                with self.assertRaises(gate.EvidenceError):
                    gate.test_contract(self.root, changed)

    def test_test_suite_roots_cannot_include_a_nested_module(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        inventory["modules"].append("app/nested")
        inventory["test_suites"][0]["test_source_roots"] = ["app/nested/src/test"]

        with self.assertRaisesRegex(gate.EvidenceError, "another Step 2 module"):
            gate.test_contract(self.root, inventory)

    def test_test_freeze_rejects_a_missing_configured_root(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_source_roots=["app/custom-tests"],
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()

        with self.assertRaisesRegex(gate.EvidenceError, "custom-tests"):
            self.submit(("project", ".", "test_freeze", None), command=[])

    def test_mock_boundary_requires_approval_for_new_worker_mock(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        mock = 'mockJobWorker("charge")'
        self.map_test_to_cpt(mocks_c8=[mock])
        mapping = gate.read_test_mapping(self.root, required=True)
        test = next(item for item in mapping["tests"] if item["c7_id"] == self.c7_test_id)
        test_contract = gate.test_contract(
            self.root, json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        )
        mapped_c8_ids = gate.mapped_cpt_test_ids(
            test_contract, gate.test_rows_by_id(mapping)
        )
        self.assertTrue(gate.test_mock_issues(test, mapping, mapped_c8_ids))
        with self.assertRaisesRegex(gate.EvidenceError, "unapproved mock"):
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            )
        mapping["mock_changes"].append(
            {
                "cpt_test_id": self.c8_test_id,
                "mock": mock,
                "reason": "The operator approved an isolated worker boundary.",
                "approved_by": "operator",
            }
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)
        self.assertEqual(
            0,
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            ),
        )

    def test_mock_boundary_uses_per_cpt_mocks_for_split_tests(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        mock = 'mockJobWorker("charge")'
        self.map_test_to_cpt(mocks_c8=[mock])
        mapping = gate.read_test_mapping(self.root, required=True)
        test = next(item for item in mapping["tests"] if item["c7_id"] == self.c7_test_id)
        other_cpt_test_id = "app:com.example.OrderCptTest#testOrderDetails"
        test["c8_ids"].append(other_cpt_test_id)
        test["mocks"]["c8_by_test_id"] = {
            self.c8_test_id: [],
            other_cpt_test_id: [mock],
        }
        mapping["mock_changes"].append(
            {
                "cpt_test_id": other_cpt_test_id,
                "mock": mock,
                "reason": "The operator approved an isolated worker boundary.",
                "approved_by": "operator",
            }
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)

        self.assertEqual(
            0,
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            ),
        )

    def test_mock_boundary_requires_per_cpt_mocks_for_split_tests(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        mock = 'mockJobWorker("charge")'
        self.map_test_to_cpt(mocks_c8=[mock])
        mapping = gate.read_test_mapping(self.root, required=True)
        test = next(item for item in mapping["tests"] if item["c7_id"] == self.c7_test_id)
        other_cpt_test_id = "app:com.example.OrderCptTest#testOrderDetails"
        test["c8_ids"].append(other_cpt_test_id)
        mapping["mock_changes"].append(
            {
                "cpt_test_id": other_cpt_test_id,
                "mock": mock,
                "reason": "The operator approved an isolated worker boundary.",
                "approved_by": "operator",
            }
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)

        with self.assertRaisesRegex(
            gate.EvidenceError, "mocks.c8_by_test_id is required"
        ):
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            )

    def test_mock_boundary_rejects_incomplete_per_cpt_mock_map(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        mapping = gate.read_test_mapping(self.root, required=True)
        test = next(item for item in mapping["tests"] if item["c7_id"] == self.c7_test_id)
        other_cpt_test_id = "app:com.example.OrderCptTest#testOrderDetails"
        test["c8_ids"].append(other_cpt_test_id)
        test["mocks"]["c8_by_test_id"] = {self.c8_test_id: []}
        write_json(self.root / gate.TEST_MAPPING, mapping)

        with self.assertRaisesRegex(
            gate.EvidenceError, "mocks.c8_by_test_id must have exactly the c8_ids as keys"
        ):
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            )

    def test_mock_boundary_rejects_mocks_missing_from_per_cpt_map(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        mock = 'mockJobWorker("charge")'
        self.map_test_to_cpt(mocks_c8=[mock])
        mapping = gate.read_test_mapping(self.root, required=True)
        test = next(item for item in mapping["tests"] if item["c7_id"] == self.c7_test_id)
        other_cpt_test_id = "app:com.example.OrderCptTest#testOrderDetails"
        test["c8_ids"].append(other_cpt_test_id)
        test["mocks"]["c8_by_test_id"] = {
            self.c8_test_id: [],
            other_cpt_test_id: [],
        }
        write_json(self.root / gate.TEST_MAPPING, mapping)

        with self.assertRaisesRegex(
            gate.EvidenceError, "mocks.c8 must match the union of mocks.c8_by_test_id"
        ):
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            )

    def test_mock_boundary_rejects_approval_for_unmapped_cpt_test(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        mapping = gate.read_test_mapping(self.root, required=True)
        mapping["mock_changes"].append(
            {
                "cpt_test_id": "app:com.example.OrderCptTest#misspelled",
                "mock": 'mockJobWorker("charge")',
                "reason": "The user approved the additional worker mock.",
                "approved_by": "operator",
            }
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)
        with self.assertRaisesRegex(gate.EvidenceError, "unmapped CPT test"):
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            )

    def test_mock_boundary_rejects_approval_for_untracked_test_mapping(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        mapping = gate.read_test_mapping(self.root, required=True)
        untracked_test = {
            "c7_id": "app:com.example.UntrackedTest#testUntracked",
            "status": "migrated",
            "c8_ids": ["app:com.example.UntrackedCptTest#testUntracked"],
            "mocks": {"c7": [], "c8": []},
        }
        mapping["tests"].append(untracked_test)
        mapping["mock_changes"].append(
            {
                "cpt_test_id": untracked_test["c8_ids"][0],
                "mock": 'mockJobWorker("charge")',
                "reason": "The user approved the additional worker mock.",
                "approved_by": "operator",
            }
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)
        with self.assertRaisesRegex(gate.EvidenceError, "unmapped CPT test"):
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            )

    def test_mock_boundary_accepts_approval_for_another_mapped_cpt_test(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        mapping = gate.read_test_mapping(self.root, required=True)
        other_test = {
            "c7_id": "app:com.example.OtherTest#testOther",
            "status": "migrated",
            "c8_ids": ["app:com.example.OtherCptTest#testOther"],
            "mocks": {
                "c7": [],
                "c8": ['mockJobWorker("charge")'],
            },
        }
        mapping["tests"].append(other_test)
        mapping["mock_changes"].append(
            {
                "cpt_test_id": other_test["c8_ids"][0],
                "mock": 'mockJobWorker("charge")',
                "reason": "The user approved the additional worker mock.",
                "approved_by": "operator",
            }
        )
        write_json(self.root / gate.TEST_MAPPING, mapping)

        current_test = next(
            item for item in mapping["tests"] if item.get("c7_id") == self.c7_test_id
        )
        contract = {
            "tests": [
                {"id": self.c7_test_id, "handling": "Migrate"},
                {"id": other_test["c7_id"], "handling": "Migrate"},
            ]
        }
        mapped_c8_ids = gate.mapped_cpt_test_ids(
            contract, gate.test_rows_by_id(mapping)
        )
        self.assertEqual(
            [], gate.test_mock_issues(current_test, mapping, mapped_c8_ids)
        )
        self.assertEqual([], gate.test_mock_issues(other_test, mapping, mapped_c8_ids))

    def test_mock_boundary_allows_only_workers_from_auto_mocked_models(self):
        self.plan["models"] = [
            {
                "source_path": "models/process.bpmn",
                "path": "models/converted-c8-process.bpmn",
                "processes": [{"id": "p", "standalone": True, "scenarios": ["normal"]}],
            },
            {
                "source_path": "models/other.bpmn",
                "path": "models/converted-c8-other.bpmn",
                "processes": [{"id": "q", "standalone": True, "scenarios": ["normal"]}],
            },
        ]
        self.plan["deployment_sets"][0]["models"] = [
            model["path"] for model in self.plan["models"]
        ]
        self.write_scope()
        for path, process_id, job_type in (
            ("models/converted-c8-process.bpmn", "p", "charge"),
            ("models/converted-c8-other.bpmn", "q", "invoice"),
        ):
            (self.root / path).write_text(
                bpmn_with_job_types(process_id, job_type),
                encoding="utf-8",
            )
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt(
            mocks_c7=['autoMock("process.bpmn")'],
            mocks_c8=['mockJobWorker("charge")', 'mockJobWorker("invoice")'],
        )

        with self.assertRaisesRegex(gate.EvidenceError, "unapproved mock"):
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            )

        mapping = gate.read_test_mapping(self.root, required=True)
        test = next(item for item in mapping["tests"] if item["c7_id"] == self.c7_test_id)
        test["mocks"]["c7"] = ['autoMock("models/missing.bpmn")']
        write_json(self.root / gate.TEST_MAPPING, mapping)
        with self.assertRaisesRegex(gate.EvidenceError, "unapproved mock"):
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            )

        test["mocks"]["c7"] = [
            'autoMock("process.bpmn")',
            'autoMock("models/other.bpmn")',
        ]
        write_json(self.root / gate.TEST_MAPPING, mapping)
        self.assertEqual(
            0,
            self.submit(
                ("test", self.c7_test_id, "mock_boundary", None),
                action="review",
                note=f"Reviewed C7 test {self.c7_test_id} and its CPT mocks.",
            ),
        )

    def test_cpt_repeat_detects_flaky_test_results(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        self.assertEqual(
            0,
            self.submit(("project", ".", "test_freeze", None), command=[]),
        )
        failed = (
            '<testsuite><testcase classname="com.example.OrderCptTest" name="testOrder">'
            "<failure /></testcase></testsuite>"
        )
        self.assertEqual(
            1,
            self.submit(
                ("module", "app", "test_repeat", "unit"),
                command=self.cpt_command(second_junit=failed),
            ),
        )
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        issues = []
        checks = gate.load_checks(
            self.root, evidence, gate.requirements(self.root, self.plan), issues
        )
        self.assertEqual([], issues)
        check = checks[("module", "app", "test_repeat", "unit")][1]
        self.assertIn("repeat results differ", check["reason"])

    def test_cpt_repeat_accepts_8_9_21_coverage_report(self):
        coverage_report = (
            FIXTURE / "cpt-8.9.21-coverage-report.json"
        ).read_text(encoding="utf-8")
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        self.assertEqual(
            0,
            self.submit(("project", ".", "test_freeze", None), command=[]),
        )
        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "test_repeat", "unit"),
                command=self.cpt_command(
                    first_coverage=coverage_report,
                    second_coverage=coverage_report,
                ),
            ),
        )

    def test_repeat_evidence_rejects_tampered_test_results(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        self.assertEqual(
            0, self.submit(("project", ".", "test_freeze", None), command=[])
        )
        key = ("module", "app", "test_repeat", "unit")
        self.assertEqual(0, self.submit(key, command=self.cpt_command()))
        log_path = self.root / gate.check_reference(key)
        original = json.loads(log_path.read_text(encoding="utf-8"))
        for tampered in ({}, {"result": "unknown"}, {"result": "passed", "invocations": ["ok"]}):
            with self.subTest(tampered=tampered):
                check = json.loads(json.dumps(original))
                for run in check["test_runs"]:
                    for test_id in run["test_results"]:
                        run["test_results"][test_id] = tampered
                write_json(log_path, check)
                evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
                issues = []
                gate.load_checks(
                    self.root, evidence, gate.requirements(self.root, self.plan), issues
                )
                self.assertTrue(
                    any("repeat evidence has an invalid run" in issue for issue in issues),
                    issues,
                )

    def test_coverage_parity_fails_when_cpt_drops_a_covered_element(self):
        for name in ("models/process.bpmn", "models/converted-c8-process.bpmn"):
            path = self.root / name
            path.write_text(bpmn("p", extra='<bpmn:serviceTask id="TaskA" />'), encoding="utf-8")
        c7_coverage = json.dumps(
            {
                "suites": [
                    {
                        "runs": [
                            {
                                "events": [
                                    {
                                        "source": "FLOW_NODE",
                                        "modelKey": "p",
                                        "definitionKey": "TaskA",
                                    },
                                    {
                                        "source": "SEQUENCE_FLOW",
                                        "modelKey": "p",
                                        "definitionKey": "Flow",
                                    },
                                ]
                            }
                        ]
                    }
                ]
            }
        )
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            c7_coverage,
        )
        self.assertEqual(0, self.record_c7_baseline())
        self.map_test_to_cpt()
        self.assertEqual(
            0,
            self.submit(("project", ".", "test_freeze", None), command=[]),
        )
        dropped = json.dumps(
            {
                "processCoverages": [
                    {
                        "processDefinitionId": "p",
                        "completedElements": ["Start", "End"],
                        "takenSequenceFlows": ["Flow"],
                        "coverage": 0.75,
                    }
                ]
            }
        )
        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "test_repeat", "unit"),
                command=self.cpt_command(
                    first_coverage=dropped,
                    second_coverage=dropped,
                ),
            ),
        )
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        checks = gate.load_checks(
            self.root, evidence, gate.requirements(self.root, self.plan), []
        )
        mapping = gate.read_test_mapping(self.root, required=True)
        issues, _ = gate.coverage_parity_issues(
            gate.requirements(self.root, self.plan), checks, mapping
        )
        self.assertTrue(any("lost C7-covered elements: TaskA" in issue for issue in issues), issues)

    def test_test_coverage_report_labels_cpt_only_processes(self):
        plan = Namespace(
            converted_elements={
                ("models/converted-c8-process.bpmn", "renamed-p"): {"TaskA"},
                ("models/converted-c8-process.bpmn", "new-p"): {"TaskB"},
            }
        )
        coverage_output = {
            "baseline_note": "Camunda 7 coverage baseline captured.",
            "c7_coverage": {"source-p": ["TaskA"]},
            "cpt_run_1": {
                "new-p": ["TaskB"],
                "renamed-p": ["TaskA"],
            },
            "cpt_run_2": {
                "new-p": ["TaskB"],
                "renamed-p": ["TaskA"],
            },
            "process_mappings": {"source-p": ["renamed-p"]},
            "retained_c7_elements": {"source-p": ["TaskA"]},
        }

        report = gate.render_test_coverage(plan, coverage_output)
        statuses = {
            cells[1].strip(): cells[5].strip()
            for line in report.splitlines()
            if line.startswith("| ")
            for cells in [line.split("|")]
            if len(cells) > 5
        }

        self.assertEqual("passed", statuses["source-p"])
        self.assertEqual("CPT coverage", statuses["new-p"])
        self.assertEqual("CPT coverage", statuses["renamed-p"])

    def test_coverage_parity_fails_when_exact_process_id_is_ambiguous(self):
        run = {
            "coverage_available": True,
            "coverage_by_process": {"p": ["TaskA"]},
        }
        plan = Namespace(
            test_contract={
                "suites": {
                    ("app", "unit"): {
                        "migrate_test_ids": ["app:com.example.OrderTest#testOrder"]
                    }
                }
            },
            source_ids={
                "models/converted-c8-process.bpmn": {"p"},
                "models/converted-c8-other.bpmn": {"p"},
            },
            converted_elements={
                ("models/converted-c8-process.bpmn", "p"): {"TaskA"},
                ("models/converted-c8-other.bpmn", "p"): {"Start", "End", "Flow"},
            },
        )
        checks = {
            ("module", "app", "test_repeat", "unit"): (None, {"test_runs": [run, run]})
        }
        mapping = {
            "baseline": c7_baseline_with_coverage({"p": ["TaskA"]}),
            "tests": migrated_test_rows("app:com.example.OrderTest#testOrder"),
        }

        issues, _ = gate.coverage_parity_issues(plan, checks, mapping)

        self.assertTrue(
            any(
                "C7 coverage maps to multiple converted processes" in issue
                or "C7 coverage is ambiguous across source models" in issue
                for issue in issues
            ),
            issues,
        )

    def test_coverage_parity_does_not_match_processes_from_other_models(self):
        source_model = "models/converted-c8-process.bpmn"
        other_model = "models/converted-c8-other.bpmn"
        for label, other_process in (
            ("same process ID", "p"),
            ("same element ID in renamed process", "renamed-p"),
        ):
            with self.subTest(label=label):
                run = {
                    "coverage_available": True,
                    "coverage_by_process": {other_process: ["TaskA"]},
                }
                plan = Namespace(
                    test_contract={
                        "suites": {
                            ("app", "unit"): {
                                "migrate_test_ids": [
                                    "app:com.example.OrderTest#testOrder"
                                ]
                            }
                        }
                    },
                    source_ids={source_model: {"p"}},
                    converted_elements={
                        (source_model, "converted-p"): {"Start", "End", "Flow"},
                        (other_model, other_process): {"TaskA"},
                    },
                )
                checks = {
                    ("module", "app", "test_repeat", "unit"): (
                        None,
                        {"test_runs": [run, run]},
                    )
                }
                mapping = {
                    "baseline": c7_baseline_with_coverage({"p": ["TaskA"]}),
                    "tests": migrated_test_rows(
                        "app:com.example.OrderTest#testOrder"
                    ),
                }

                issues, details = gate.coverage_parity_issues(plan, checks, mapping)

                self.assertEqual([], issues)
                self.assertEqual([], details["process_mappings"]["p"])
                self.assertEqual([], details["retained_c7_elements"]["p"])

    def test_coverage_parity_fails_when_other_suite_reuses_converted_process_id(self):
        source_model = "models/converted-c8-process-a.bpmn"
        other_model = "models/converted-c8-process-b.bpmn"
        source_run = {"coverage_available": True, "coverage_by_process": {}}
        other_run = {
            "coverage_available": True,
            "coverage_by_process": {"p": ["TaskA"]},
        }
        plan = Namespace(
            test_contract={
                "suites": {
                    ("app", "unit"): {
                        "migrate_test_ids": ["app:com.example.OrderTest#testOrder"]
                    },
                    ("worker", "unit"): {
                        "migrate_test_ids": ["worker:com.example.OtherTest#testOther"]
                    },
                }
            },
            source_ids={
                source_model: {"source-a"},
                other_model: {"source-b"},
            },
            converted_elements={
                (source_model, "p"): {"TaskA"},
                (other_model, "p"): {"TaskA"},
            },
        )
        checks = {
            ("module", "app", "test_repeat", "unit"): (
                None,
                {"test_runs": [source_run, source_run]},
            ),
            ("module", "worker", "test_repeat", "unit"): (
                None,
                {"test_runs": [other_run, other_run]},
            ),
        }
        mapping = {
            "baseline": c7_baseline_with_coverage({"source-a": ["TaskA"]}),
            "tests": migrated_test_rows(
                "app:com.example.OrderTest#testOrder",
                "worker:com.example.OtherTest#testOther",
            ),
        }

        issues, _ = gate.coverage_parity_issues(plan, checks, mapping)

        self.assertTrue(
            any(
                "CPT coverage process ID appears in multiple converted models" in issue
                for issue in issues
            ),
            issues,
        )

    def test_coverage_parity_fails_when_multiple_c7_processes_map_to_one_cpt_process(self):
        run = {
            "coverage_available": True,
            "coverage_by_process": {"converted-p": ["TaskA", "TaskB"]},
        }
        plan = Namespace(
            test_contract={
                "suites": {
                    ("app", "unit"): {
                        "migrate_test_ids": ["app:com.example.OrderTest#testOrder"]
                    }
                }
            },
            source_ids={
                "models/converted-c8-process.bpmn": {"source-p1", "source-p2"}
            },
            converted_elements={
                ("models/converted-c8-process.bpmn", "converted-p"): {
                    "TaskA",
                    "TaskB",
                }
            },
        )
        checks = {
            ("module", "app", "test_repeat", "unit"): (None, {"test_runs": [run, run]})
        }
        mapping = {
            "baseline": c7_baseline_with_coverage(
                {
                    "source-p1": ["TaskA"],
                    "source-p2": ["TaskB"],
                }
            ),
            "tests": migrated_test_rows("app:com.example.OrderTest#testOrder"),
        }

        issues, _ = gate.coverage_parity_issues(plan, checks, mapping)

        self.assertTrue(
            any("multiple C7 process IDs map to the same CPT process" in issue for issue in issues),
            issues,
        )

    def test_coverage_parity_fails_when_source_process_id_is_aggregated_across_models(self):
        run = {
            "coverage_available": True,
            "coverage_by_process": {
                "p": ["TaskA"],
                "renamed-p": ["TaskB"],
            },
        }
        plan = Namespace(
            test_contract={
                "suites": {
                    ("app", "unit"): {
                        "migrate_test_ids": ["app:com.example.OrderTest#testOrder"]
                    }
                }
            },
            source_ids={
                "models/converted-c8-process.bpmn": {"p"},
                "models/converted-c8-other.bpmn": {"p"},
            },
            converted_elements={
                ("models/converted-c8-process.bpmn", "p"): {"TaskA"},
                ("models/converted-c8-other.bpmn", "renamed-p"): {"TaskB"},
            },
        )
        checks = {
            ("module", "app", "test_repeat", "unit"): (None, {"test_runs": [run, run]})
        }
        mapping = {
            "baseline": c7_baseline_with_coverage({"p": ["TaskA", "TaskB"]}),
            "tests": migrated_test_rows("app:com.example.OrderTest#testOrder"),
        }

        issues, _ = gate.coverage_parity_issues(plan, checks, mapping)

        self.assertTrue(
            any("C7 coverage is ambiguous across source models" in issue for issue in issues),
            issues,
        )

        mapping["baseline"]["coverage"] = {"p": ["RemovedTask"]}
        mapping["baseline"]["suites"][0]["coverage_by_process"] = {
            "p": ["RemovedTask"]
        }
        issues, _ = gate.coverage_parity_issues(plan, checks, mapping)
        self.assertEqual([], issues)

    def test_coverage_parity_ignores_removed_c7_elements_when_mapping_another_process(self):
        run = {
            "coverage_available": True,
            "coverage_by_process": {"p": ["TaskA"]},
        }
        plan = Namespace(
            test_contract={
                "suites": {
                    ("app", "unit"): {
                        "migrate_test_ids": ["app:com.example.OrderTest#testOrder"]
                    }
                }
            },
            source_ids={"models/converted-c8-process.bpmn": {"p", "legacy-p"}},
            converted_elements={
                ("models/converted-c8-process.bpmn", "p"): {"TaskA"},
            },
        )
        checks = {
            ("module", "app", "test_repeat", "unit"): (None, {"test_runs": [run, run]})
        }
        mapping = {
            "baseline": c7_baseline_with_coverage(
                {
                    "p": ["RemovedTask"],
                    "legacy-p": ["TaskA"],
                }
            ),
            "tests": migrated_test_rows("app:com.example.OrderTest#testOrder"),
        }

        issues, _ = gate.coverage_parity_issues(plan, checks, mapping)

        self.assertEqual([], issues)

    def test_migrate_only_preserves_inventory_for_deferred_test_verification(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_file_path="app/target/generated-test-sources/com/example/OrderTest.java",
        )
        inventory_path = self.root / gate.INVENTORY
        inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
        inventory["test_run_mode"] = "migrate_only"
        write_json(inventory_path, inventory)
        with redirect_stdout(StringIO()):
            self.assertEqual(
                0, gate.initialize(self.root, reset_source_snapshot=True)
            )

        inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
        snapshot = inventory["source_snapshot_test_contract"]
        self.assertEqual(
            [self.c7_test_id], [test["id"] for test in snapshot["tests"]]
        )
        self.assertIn(self.c7_test_file_path, inventory["source_files"])

        inventory["test_run_mode"] = "run"
        write_json(inventory_path, inventory)
        contract = gate.validate_source_snapshot_test_contract(self.root, inventory)
        self.assertEqual([self.c7_test_id], [test["id"] for test in contract["tests"]])
        gate.verify_unchanged_source(self.root, inventory)

        added_suite = dict(
            inventory["test_suites"][0],
            name="integration",
            test_source_roots=["app/src/it/java"],
        )
        (self.root / "app/src/it/java").mkdir(parents=True)
        inventory["test_suites"].append(added_suite)
        write_json(inventory_path, inventory)
        with self.assertRaisesRegex(
            gate.EvidenceError, "changed after the Step 2 snapshot"
        ):
            gate.validate_source_snapshot_test_contract(self.root, inventory)

    def test_test_run_mode_may_only_change_from_migrate_only_to_run(self):
        snapshot = {"mode": "migrate_only", "tests": [], "modules": ["app"], "suites": []}
        self.assertTrue(
            gate.source_test_contract_matches_snapshot(dict(snapshot, mode="run"), snapshot)
        )
        self.assertFalse(
            gate.source_test_contract_matches_snapshot(
                snapshot, dict(snapshot, mode="run")
            )
        )
        self.assertFalse(
            gate.source_test_contract_matches_snapshot(
                dict(snapshot, mode="run", suites=[{"name": "unit"}]), snapshot
            )
        )

    def test_test_inventory_table_outside_its_section_is_rejected(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        report_path = self.root / gate.REPORT
        report = report_path.read_text(encoding="utf-8")
        for heading in (None, "## Test Cases"):
            with self.subTest(heading=heading):
                moved = (
                    report.replace("## Test Inventory\n\n", "")
                    if heading is None
                    else report.replace("## Test Inventory", heading)
                )
                report_path.write_text(moved, encoding="utf-8")
                with self.assertRaisesRegex(
                    gate.EvidenceError, "must be under the Test Inventory heading"
                ):
                    gate.test_report_inventory(self.root, required=False)

    def test_test_inventory_rejects_rows_without_outer_pipes(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        report_path = self.root / gate.REPORT
        lines = report_path.read_text(encoding="utf-8").splitlines()
        pipeless = [
            line.strip().strip("|") if line.lstrip().startswith("|") else line
            for line in lines
        ]
        report_path.write_text("\n".join(pipeless) + "\n", encoding="utf-8")

        with self.assertRaisesRegex(gate.EvidenceError, "rows must start with \\|"):
            gate.test_report_inventory(self.root, required=False)

    def test_validator_owned_reads_reject_symlinked_parent_directories(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        self.assertEqual(0, self.record_c7_baseline())
        validation = (self.root / gate.TEST_MAPPING).parent
        moved = self.root / "elsewhere"
        validation.replace(moved)
        validation.symlink_to(moved, target_is_directory=True)

        with self.assertRaisesRegex(gate.EvidenceError, "Refusing symlinked test parity ledger"):
            gate.read_test_mapping(self.root, required=True)
        validation.unlink()
        moved.replace(validation)
        ledger = self.root / gate.TEST_MAPPING
        ledger.unlink()
        ledger.symlink_to(self.root / "missing.json")
        with self.assertRaisesRegex(gate.EvidenceError, "Refusing symlinked test parity ledger"):
            gate.read_test_mapping(self.root)
        ledger.unlink()
        moved = self.root / "elsewhere"
        validation.replace(moved)
        validation.symlink_to(moved, target_is_directory=True)
        with self.assertRaisesRegex(gate.EvidenceError, "Refusing symlinked"):
            gate.recorded_test_freeze_digest(self.root, {})

    def test_migrate_only_rejects_malformed_test_inventory(self):
        self.write_scope(test_run_mode="migrate_only")
        (self.root / gate.REPORT).write_text(
            "# Migration report\n\n"
            "## Test Inventory\n\n"
            "| Test ID | File | Test kind | Disposition |\n"
            "|---|---|---|---|\n"
            "| app:com.example.OrderTest#testOrder | "
            "app/src/test/java/com/example/OrderTest.java | process test | Migrate |\n",
            encoding="utf-8",
        )

        plan = gate.requirements(self.root, self.plan)

        self.assertTrue(
            any(
                "MIGRATION_REPORT.md Test Inventory table has no valid header" in issue
                for issue in plan.issues
            ),
            plan.issues,
        )

    def test_run_mode_rejects_nonempty_rows_after_test_inventory_table_break(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>',
            test_run_mode="run",
        )

        rows = (
            "|  | app/src/test/java/com/example/OrderTest.java | process test | Migrate |",
            "| invalid-test-id | app/src/test/java/com/example/OrderTest.java "
            "| process test | Migrate |",
            "| app/src/test/java/com/example/OrderTest.java | process test | Migrate |",
            "|---|---|---|---|\n"
            "|  | app/src/test/java/com/example/OrderTest.java | process test | Migrate |",
        )
        for row in rows:
            with self.subTest(row=row):
                (self.root / gate.REPORT).write_text(
                    "# Migration report\n\n"
                    "## Test Inventory\n\n"
                    "| Test ID | File | Test kind | Handling |\n"
                    "|---|---|---|---|\n\n"
                    f"{row}\n",
                    encoding="utf-8",
                )

                self.assertEqual(1, self.audit())
                summary = self.summary()
                self.assertEqual("NOT READY", summary["gate"])
                self.assertTrue(
                    any("Test Inventory" in issue for issue in summary["issues"]),
                    summary["issues"],
                )

    def test_test_inventory_ignores_table_in_following_section(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        report_path = self.root / gate.REPORT
        report_path.write_text(
            report_path.read_text(encoding="utf-8")
            + "\n## Other evidence\n\n"
            "| ID | Path | Kind | Status |\n"
            "|---|---|---|---|\n"
            "| other | app/src/test/java/OtherTest.java | process test | passed |\n",
            encoding="utf-8",
        )

        tests = gate.test_report_inventory(self.root)

        self.assertEqual([self.c7_test_id], [test["id"] for test in tests])

    def test_test_inventory_ignores_table_after_adjacent_section_heading(self):
        self.configure_test_run(
            '<testsuite><testcase classname="com.example.OrderTest" name="testOrder" /></testsuite>'
        )
        report_path = self.root / gate.REPORT
        report_path.write_text(
            report_path.read_text(encoding="utf-8").rstrip()
            + "\n## Other evidence\n"
            + "| ID | Path | Kind | Status |\n"
            + "|---|---|---|---|\n"
            + "| other | app/src/test/java/OtherTest.java | process test | passed |\n",
            encoding="utf-8",
        )

        tests = gate.test_report_inventory(self.root)

        self.assertEqual([self.c7_test_id], [test["id"] for test in tests])

    def test_previous_run_checks_cannot_validate_new_run(self):
        self.write_scope(timer=True)
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        original = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))["run_id"]
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root))
        current = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))["run_id"]
        self.assertNotEqual(original, current)
        self.assertEqual(1, self.audit())
        checks = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))["checks"]
        stale = [issue for issue in self.summary()["issues"] if "another migration run" in issue]
        self.assertEqual(len(checks), len(stale))

    def test_source_snapshot_digest_preserves_schema_v1_json_encoding(self):
        modules = ["app"]
        models = ["models/process.bpmn"]
        files = {"app/pom.xml": "8bff"}
        test_contract = {"mode": "run", "tests": ["app:OrderTest#testOrder"]}
        snapshot = {
            "modules": modules,
            "models": models,
            "files": files,
            "test_contract": test_contract,
        }
        expected = hashlib.sha256(
            json.dumps(snapshot, sort_keys=True).encode("utf-8")
        ).hexdigest()

        self.assertEqual(
            expected,
            gate.source_snapshot_digest(modules, models, files, test_contract),
        )

    def test_schema_v1_persisted_snapshot_and_ledger_digests_remain_valid(self):
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        snapshot = {
            "modules": inventory["modules"],
            "models": inventory["models"],
            "files": inventory["source_files"],
            "test_contract": inventory.get("source_snapshot_test_contract"),
        }
        legacy_digest = hashlib.sha256(
            json.dumps(snapshot, sort_keys=True).encode("utf-8")
        ).hexdigest()
        inventory["source_snapshot_sha256"] = legacy_digest
        write_json(self.root / gate.INVENTORY, inventory)
        write_json(
            self.root / gate.TEST_MAPPING,
            gate.empty_test_mapping(inventory),
        )

        gate.verify_unchanged_source(self.root, inventory)
        mapping = gate.ensure_test_mapping(self.root, inventory)
        self.assertEqual(legacy_digest, mapping["baseline"]["source_digest"])

    def test_repeated_init_preserves_source_updates_after_conversion(self):
        self.install_active_timer_decision()
        original = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        del self.plan["active_timer_update_decision"]
        write_json(self.root / gate.EVIDENCE, self.plan)
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root))
        with self.assertRaisesRegex(gate.EvidenceError, "block active"):
            self.submit(
                ("module", "app", "active_timer_updates", None),
                action="review", disposition="no_updates",
            )
        current = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        self.assertNotEqual(original["run_id"], current["run_id"])
        self.assertEqual(original["source_updates"], current["source_updates"])

    def test_init_requires_explicit_reset_for_a_restored_c7_baseline(self):
        self.install_active_timer_decision()
        original = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        (self.root / "app" / "Timer.java").write_text(
            "managementService.setJobDuedate(jobId, dueDate);\n"
            "managementService.setJobDuedate(otherJobId, otherDate);\n",
            encoding="utf-8",
        )
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root))
        current = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        self.assertEqual(original["source_updates"], current["source_updates"])
        reset = subprocess.run(
            [sys.executable, str(SCRIPT_DIR / "validate_migration_evidence.py"),
             "--project-root", str(self.root), "init", "--reset-source-snapshot"],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(0, reset.returncode, reset.stderr)
        updated = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        self.assertEqual(2, len(updated["source_updates"]["app"]))

    def test_init_rejects_missing_source_snapshot_in_existing_run(self):
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        del inventory["source_updates"]
        write_json(self.root / gate.INVENTORY, inventory)
        with self.assertRaisesRegex(gate.EvidenceError, "source snapshot is missing"):
            gate.initialize(self.root)

    def test_report_needs_an_initialized_scope(self):
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        del inventory["run_id"]
        write_json(self.root / gate.INVENTORY, inventory)
        self.assertEqual(1, self.audit())
        self.assertIn("Initialize a migration validation run", "\n".join(self.summary()["issues"]))

    def test_command_accepts_an_empty_argument(self):
        self.complete_required_checks()
        command = [
            sys.executable, "-c", "import sys; assert sys.argv[1] == ''", "",
        ]
        self.assertEqual(0, self.submit(("module", "app", "compile", None), command=command))
        self.assertEqual(0, self.audit())

    def test_report_rejects_the_nine_module_ten_model_contradiction(self):
        module_names = (
            "web", "account", "loan", "order", "invoice", "messaging", "customer",
            "runtime-api", "runtime-worker",
        )
        model_names = (
            "order", "account", "loan", "invoice", "customer", "messaging",
            "customer-archive", "eligibility", "notification", "report",
        )
        self.plan["modules"] = [
            {
                "path": f"examples/{name}",
                "runtime_mode": "spring-boot" if name.startswith("runtime-") else "none",
                "test_suites": [{"name": "web-smoke" if name == "web" else "unit", "requires_docker": False}],
            }
            for name in module_names
        ]
        self.plan["modules"][0]["test_suites"].append(
            {"name": "container-integration", "requires_docker": True}
        )
        self.plan["models"] = [
            {
                "source_path": f"models/{name}.bpmn",
                "path": f"models/converted-c8-{name}.bpmn",
                "processes": [{"id": f"{name}-process", "standalone": True, "scenarios": ["normal"]}],
            }
            for name in model_names
        ]
        self.plan["deployment_sets"] = [{
            "name": "shared",
            "modules": [module["path"] for module in self.plan["modules"]],
            "models": [model["path"] for model in self.plan["models"]],
        }]
        self.write_scope()
        for model in self.plan["models"]:
            if model["source_path"] not in ("models/order.bpmn", "models/messaging.bpmn"):
                continue
            text = bpmn(
                model["processes"][0]["id"],
                timer=model["source_path"] == "models/order.bpmn",
                extra=(
                    '<bpmn:startEvent id="MessageStart"><bpmn:messageEventDefinition '
                    'messageRef="Message" /></bpmn:startEvent>'
                    if model["source_path"] == "models/messaging.bpmn"
                    else ""
                ),
            )
            for name in (model["source_path"], model["path"]):
                path = self.root / name
                path.write_text(text, encoding="utf-8")
        cases = (
            (("project", ".", "docker_info", None), 0, "Docker daemon responds", None, None),
            (("module", "examples/web", "tests", "web-smoke"), 1,
             "Non-Docker web tests fail on the invalid client mode", "application", None),
            (("module", "examples/web", "tests", "container-integration"), 1,
             "Testcontainers cannot select an environment", "testcontainers", None),
            (("module", "examples/runtime-api", "configuration", None), 1,
             "Invalid client mode prevents startup", "application", None),
            (("model", "models/converted-c8-order.bpmn", "lint", None), 1,
             "Compatibility lint errors", "compatibility", None),
            (("model", "models/converted-c8-order.bpmn", "deployment", None), 1,
             "Conditional definition has no ID", "compatibility", "local"),
            (("module", "examples/runtime-api", "executable_jar", None), 1,
             "Packaged JAR has no executable entry point", "application", "local"),
            (("module", "examples/runtime-worker", "executable_jar", None), 1,
             "Packaged JAR has no executable entry point", "application", "local"),
        )
        run_id = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))["run_id"]
        for index, (key, exit_code, reason, failure_class, environment) in enumerate(cases):
            category, target, kind, scenario = key
            path = gate.LOGS / f"historical-{index}.json"
            write_json(self.root / path, {
                "run_id": run_id,
                "type": category, "target": target, "kind": kind, "scenario": scenario,
                "method": "command",
                "command": ["docker", "info"] if kind == "docker_info" else ["synthetic-check", kind],
                "exit_code": exit_code, "result": "passed" if exit_code == 0 else "failed",
                "reason": None if exit_code == 0 else reason,
                "failure_class": failure_class, "environment": environment, "output": reason,
            })
            self.plan["checks"].append(path.as_posix())
        write_json(self.root / gate.EVIDENCE, self.plan)
        (self.root / gate.REPORT).write_text(
            "# Deliberately contradictory migration report\n\n"
            "## Reported checks\n\nConfiguration, tests, conversion, and build wiring: PASS\n\n"
            "## Aggregate validation gate\n\n**Validation gate:** **READY**\n",
            encoding="utf-8",
        )
        self.assertIn("**Validation gate:** **READY**", (self.root / gate.REPORT).read_text(encoding="utf-8"))
        self.assertEqual(1, self.audit())
        self.assertEqual(9, len(self.plan["modules"]))
        self.assertEqual(10, len(self.plan["models"]))
        missing_lints = [
            issue for issue in self.summary()["issues"] if issue.startswith("Missing model lint:")
        ]
        self.assertEqual(9, len(missing_lints))
        issues = "\n".join(self.summary()["issues"])
        self.assertIn("Non-Docker web tests fail", issues)
        self.assertIn("Testcontainers cannot select an environment", issues)
        self.assertIn("Missing model lint: models/converted-c8-account.bpmn", issues)
        self.assertIn("Missing timer preflight", issues)
        self.assertIn("Missing process downstream_message_instance", issues)
        self.assertIn("Packaged JAR has no executable entry point", issues)
        report = (self.root / gate.REPORT).read_text(encoding="utf-8")
        self.assertIn("**Validation gate:** **NOT READY**", report)
        self.assertNotIn("**Validation gate:** **READY**", report)
        self.assertIn("## Reported checks", report)

    def test_failed_command_does_not_stop_independent_checks(self):
        compile_key = ("module", "app", "compile", None)
        self.assertEqual(
            1, self.submit(compile_key, command=[sys.executable, "-c", "import sys; sys.exit(5)"])
        )
        self.assertEqual(0, self.submit(("module", "app", "tests", "unit")))
        self.assertEqual(1, self.audit())
        compile_check = next(
            check for check in self.summary()["checks"] if check["kind"] == "compile"
        )
        self.assertEqual(("failed", 5), (compile_check["result"], compile_check["exit_code"]))
        self.assertTrue(
            any(check["kind"] == "tests" and check["result"] == "passed" for check in self.summary()["checks"])
        )

    def test_missing_evidence_never_passes(self):
        self.complete_required_checks()
        for kind in ("tests", "lint", "deployment", "process_path"):
            with self.subTest(kind=kind):
                plan = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
                plan["checks"] = [
                    path for path in plan["checks"]
                    if json.loads((self.root / path).read_text(encoding="utf-8"))["kind"] != kind
                ]
                write_json(self.root / gate.EVIDENCE, plan)
                self.assertEqual(1, self.audit())
                self.assertIn(f"Missing", "\n".join(self.summary()["issues"]))
                self.complete_required_checks()

    def test_scope_cannot_omit_module_or_model(self):
        for collection in ("modules", "models"):
            with self.subTest(collection=collection):
                plan = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
                plan[collection] = []
                write_json(self.root / gate.EVIDENCE, plan)
                self.assertEqual(1, self.audit())
                self.assertIn("confirmed Step 2 scope", "\n".join(self.summary()["issues"]))
                self.write_scope()

    def test_process_assertions_come_from_converted_bpmn(self):
        extra = (
            '<bpmn:userTask id="User"><bpmn:extensionElements>'
            '<zeebe:userTask /><zeebe:formDefinition formId="request" />'
            "</bpmn:extensionElements></bpmn:userTask>"
            '<bpmn:exclusiveGateway id="Choice" />'
            '<bpmn:intermediateCatchEvent id="Catch"><bpmn:messageEventDefinition '
            'messageRef="message" /></bpmn:intermediateCatchEvent>'
            '<bpmn:serviceTask id="Worker"><bpmn:extensionElements>'
            '<zeebe:taskDefinition type="process" /></bpmn:extensionElements></bpmn:serviceTask>'
        )
        self.write_scope(extra=extra)
        required = gate.requirements(self.root, self.plan).required
        self.assertEqual(set(gate.ASSERTIONS), {
            key[2] for key in required if key[0] == "process" and key[2] in gate.ASSERTIONS
        })
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        plan = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        plan["checks"] = [
            name for name in plan["checks"]
            if json.loads((self.root / name).read_text(encoding="utf-8"))["kind"] != "downstream_message_instance"
        ]
        write_json(self.root / gate.EVIDENCE, plan)
        self.assertEqual(1, self.audit())
        self.assertIn("Missing process downstream_message_instance", "\n".join(self.summary()["issues"]))

    def test_process_inventory_must_match_executable_bpmn(self):
        self.plan["models"][0]["processes"] = []
        write_json(self.root / gate.EVIDENCE, self.plan)
        self.assertEqual(1, self.audit())
        self.assertIn("process inventory differs", "\n".join(self.summary()["issues"]))

    def test_executable_process_accepts_xml_boolean_one(self):
        converted = self.root / self.plan["models"][0]["path"]
        converted.write_text(
            converted.read_text(encoding="utf-8").replace('isExecutable="true"', 'isExecutable="1"'),
            encoding="utf-8",
        )
        plan = gate.requirements(self.root, self.plan)
        self.assertEqual([], plan.issues)
        self.assertIn(
            ("process", "models/converted-c8-process.bpmn#p", "process_path", "normal"),
            plan.required,
        )

    def test_mismatched_source_model_type_blocks_readiness(self):
        source = self.root / self.plan["models"][0]["source_path"]
        source.write_text(
            '<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="rules" />',
            encoding="utf-8",
        )
        self.assertEqual(1, self.audit())
        self.assertIn("Not BPMN definitions documents", "\n".join(self.summary()["issues"]))

    def test_legacy_bpmn_file_extension_converts_to_bpmn(self):
        model = self.plan["models"][0]
        old_source = self.root / model["source_path"]
        new_source = self.root / "models/process.bpmn20.xml"
        old_source.rename(new_source)
        model["source_path"] = "models/process.bpmn20.xml"
        self.write_scope()
        self.complete_required_checks()
        self.assertEqual(0, self.audit())

    def test_non_standalone_process_needs_covering_test_not_direct_start(self):
        process = self.plan["models"][0]["processes"][0]
        process.update({"standalone": False, "scenarios": [], "covering_test": "parent-call"})
        self.write_scope()
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        self.assertTrue(any(
            check["kind"] == "process_path" and check["scenario"] == "parent-call"
            for check in self.summary()["checks"]
        ))
        process["scenarios"] = ["normal"]
        write_json(self.root / gate.EVIDENCE, self.plan)
        self.assertEqual(1, self.audit())
        self.assertIn("no direct-start scenarios", "\n".join(self.summary()["issues"]))

    def test_dmn_models_still_need_lint_and_safe_deployment(self):
        self.plan["modules"] = []
        self.plan["models"] = [
            {"source_path": "models/rules.dmn11.xml", "path": "models/converted-c8-rules.dmn", "processes": []}
        ]
        self.plan["deployment_sets"] = [{
            "name": "shared", "modules": [], "models": ["models/converted-c8-rules.dmn"]
        }]
        write_json(self.root / gate.INVENTORY, {
            "schema_version": 1, "modules": [], "models": ["models/rules.dmn11.xml"],
        })
        write_json(self.root / gate.EVIDENCE, self.plan)
        xml = '<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="rules" />'
        for name in ("models/rules.dmn11.xml", "models/converted-c8-rules.dmn"):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(xml, encoding="utf-8")
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root))
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        self.assertEqual(
            {"lint", "review", "deployment", "preflight"},
            {check["kind"] for check in self.summary()["checks"]},
        )

    def test_unavailable_executable_is_blocked_with_reason(self):
        self.assertEqual(
            1, self.submit(("module", "app", "compile", None), command=["nonexistent-validation-tool-2926"])
        )
        self.assertEqual(1, self.audit())
        recorded = next(
            check for check in self.summary()["checks"] if check["kind"] == "compile"
        )
        self.assertEqual(("blocked", None), (recorded["result"], recorded["exit_code"]))
        self.assertIn("nonexistent-validation-tool-2926", recorded["reason"])

    def test_timer_preflight_is_required_before_deployment(self):
        self.write_scope(timer=True)
        model = "models/converted-c8-process.bpmn"
        self.assertEqual(0, self.submit(("model", model, "lint", None)))
        with self.assertRaisesRegex(gate.EvidenceError, "preflight"):
            self.submit(("model", model, "deployment", None), environment="local")
        with self.assertRaisesRegex(gate.EvidenceError, "disposable"):
            self.submit(("timer", f"{model}#p#Start", "preflight", None), environment="local")
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        plan = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        plan["checks"] = list(reversed(plan["checks"]))
        write_json(self.root / gate.EVIDENCE, plan)
        self.assertEqual(1, self.audit())
        self.assertIn("disposition must pass before execution", "\n".join(self.summary()["issues"]))

    def test_duplicate_process_ids_block_deployment_until_caller_decision(self):
        self.sample_models()
        model = self.plan["models"][0]["path"]
        collision = ("deployment_set", "shared", "duplicate_process_id", "Sample")
        self.assertIn(("shared", "Sample"), gate.requirements(self.root, self.plan).duplicates)
        self.assertEqual(0, self.submit(("model", model, "lint", None)))
        self.assertEqual(0, self.submit(
            ("deployment_set", "shared", "preflight", None), action="review"
        ))
        command = [sys.executable, "-c", "from pathlib import Path; Path('executed').touch()"]
        with self.assertRaisesRegex(gate.EvidenceError, "duplicate_process_id"):
            self.submit(("model", model, "deployment", None), environment="local", command=command)
        self.assertFalse((self.root / "executed").exists())
        with self.assertRaisesRegex(gate.EvidenceError, "concrete approval"):
            self.submit(collision, action="review", disposition="explicit_version",
                        reference="not applicable")
        self.assertEqual(0, self.submit(
            collision, action="review", disposition="explicit_version",
            note="Reviewed every caller and pinned the selected version in both modules.",
        ))
        self.assertEqual(0, self.submit(
            ("model", model, "deployment", None), environment="local", command=command
        ))
        self.assertTrue((self.root / "executed").exists())

    def test_retained_timer_collision_requires_rename_or_isolation(self):
        self.sample_models(timer=True)
        collision = ("deployment_set", "shared", "duplicate_process_id", "Sample")
        with self.assertRaisesRegex(gate.EvidenceError, "colliding timer start"):
            self.submit(collision, action="review", disposition="explicit_version")
        with self.assertRaisesRegex(gate.EvidenceError, "rename colliding IDs"):
            self.submit(collision, action="review", disposition="mapped_rename")
        self.sample_models(timer=True, isolated=True)
        self.assertEqual({}, gate.requirements(self.root, self.plan).duplicates)

    def test_source_collision_stays_open_until_mapped_rename(self):
        self.sample_models()
        second = self.plan["models"][1]
        converted = self.root / second["path"]
        converted.write_text(
            converted.read_text(encoding="utf-8").replace("Sample", "Renamed"),
            encoding="utf-8",
        )
        second["processes"][0]["id"] = "Renamed"
        write_json(self.root / gate.EVIDENCE, self.plan)
        collision = ("deployment_set", "shared", "duplicate_process_id", "Sample")
        self.assertNotIn("converted", gate.requirements(self.root, self.plan).duplicates[("shared", "Sample")])
        with self.assertRaisesRegex(gate.EvidenceError, "source collision"):
            self.submit(collision, action="review", disposition="explicit_version")
        self.assertEqual(0, self.submit(
            collision, action="review", disposition="mapped_rename",
            note="Mapped Sample to Renamed and updated callers in both modules.",
        ))

    def test_timer_observation_cannot_be_reused_for_another_model(self):
        self.sample_models(timer=True, isolated=True)
        a, b = (f"models/converted-c8-{name}.bpmn#Sample#Start" for name in ("a", "b"))
        self.assertEqual(0, self.submit(("timer", b, "disposition", None), action="review"))
        key = ("timer", b, "preflight", None)
        options = {"environment": "local", "isolation_plan": "Destroy the disposable target."}
        self.assertEqual(1, self.submit(
            key, observation=self.timer_observation(("timer", a, "preflight", None)),
            **options,
        ))
        recorded = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        check = json.loads((self.root / recorded["checks"][-1]).read_text(encoding="utf-8"))
        self.assertEqual(("failed", 0), (check["result"], check["exit_code"]))
        with redirect_stdout(StringIO()):
            self.assertEqual(1, gate.report(self.root))
        self.assertIn("match this model", "\n".join(self.summary()["issues"]))
        observation = self.timer_observation(key)
        del observation["observation"]["model_path"]
        self.assertEqual(1, self.submit(key, observation=observation, **options))
        observation = self.timer_observation(key)
        observation["cleanup"]["completed"] = False
        self.assertEqual(1, self.submit(key, observation=observation, **options))
        with self.assertRaisesRegex(gate.EvidenceError, "disposable"):
            self.submit(key, target_disposable=False, **options)
        self.assertEqual(0, self.submit(key, **options))

    def test_active_timer_calls_cannot_be_approved_without_a_replacement(self):
        source = self.root / "app" / "Timer.java"
        source.write_text(
            'setJobDuedate(jobA, date); request.path("job").path("duedate");',
            encoding="utf-8",
        )
        key = ("module", "app", "active_timer_updates", None)
        hits = gate.requirements(self.root, self.plan).update_hits["app"]
        self.assertEqual(2, len(hits))
        self.assertEqual(2, len(set(hits)))
        with self.assertRaisesRegex(gate.EvidenceError, "block active"):
            self.submit(key, action="review", disposition="no_updates")
        with self.assertRaisesRegex(gate.EvidenceError, "block active"):
            self.submit(key, action="review", disposition="verified", reference="not applicable")
        evidence = [{"location": hit, "evidence": f"decision-{index}"} for index, hit in enumerate(hits)]
        with self.assertRaisesRegex(gate.EvidenceError, "classify each"):
            self.submit(key, action="review", disposition="non_timer",
                        non_timer_evidence_json=json.dumps(evidence[:1]))
        evidence[1]["evidence"] = "not applicable"
        with self.assertRaisesRegex(gate.EvidenceError, "classify each"):
            self.submit(key, action="review", disposition="non_timer",
                        non_timer_evidence_json=json.dumps(evidence))
        evidence[1]["evidence"] = "MIGRATION_REPORT.md#non-timer-job"
        self.assertEqual(0, self.submit(
            key, action="review", disposition="non_timer",
            non_timer_evidence_json=json.dumps(evidence),
        ))
        self.plan["active_timer_update_decision"] = {"status": "approved"}
        write_json(self.root / gate.EVIDENCE, self.plan)
        self.assertEqual(1, self.audit())
        self.assertIn("approved message_rearm decision", "\n".join(self.summary()["issues"]))

    def test_due_date_scan_skips_comments_but_preserves_http_urls(self):
        (self.root / "app" / "Timer.java").write_text(
            "// setJobDuedate(commented, date);\n"
            "/* setJobDuedate(blocked, date); */\n"
            'String url = "http://localhost/job/42/duedate";\n'
            "managementService.setJobDuedate(jobId, date);\n",
            encoding="utf-8",
        )
        (self.root / "app" / "requests.http").write_text(
            "# GET http://localhost/job/old/duedate\n"
            "GET http://localhost/job/42/duedate/recalculate\n",
            encoding="utf-8",
        )
        hits = gate.requirements(self.root, self.plan).update_hits["app"]
        self.assertEqual(3, len(hits))
        self.assertEqual({"Timer.java", "requests.http"}, {
            location.split("/")[-1].split(":")[0] for location in hits
        })

    def test_due_date_scan_finds_live_setters_after_non_nested_block_comments(self):
        suffixes = (".java", ".js", ".jsx", ".ts", ".tsx", ".cjs", ".mjs",
                    ".cts", ".mts", ".groovy")
        for suffix in suffixes:
            (self.root / "app" / f"Timer{suffix}").write_text(
                "/* comment with another opener /* */\n"
                "managementService.setJobDuedate(jobId, dueDate);\n",
                encoding="utf-8",
            )
        hits = gate.requirements(self.root, self.plan).update_hits["app"]
        self.assertEqual(
            {f"Timer{suffix}" for suffix in suffixes},
            {location.rsplit("/", 1)[1].split(":")[0] for location in hits},
        )

    def test_due_date_scan_keeps_kotlin_and_scala_nested_comments(self):
        for suffix in (".kt", ".kts", ".scala"):
            (self.root / "app" / f"Timer{suffix}").write_text(
                "/* outer /* nested */ setJobDuedate(commented, date); */\n"
                "managementService.setJobDuedate(jobId, dueDate);\n",
                encoding="utf-8",
            )
        hits = gate.requirements(self.root, self.plan).update_hits["app"]
        self.assertEqual(3, len(hits))
        self.assertTrue(all(location.rsplit(":", 2)[1] == "2" for location in hits))

    def test_due_date_scan_skips_http_line_comments_without_masking_urls(self):
        (self.root / "app" / "requests.http").write_text(
            "# GET http://localhost/job/old/duedate\n"
            "  // GET http://localhost/job/commented/duedate\n"
            "GET http://localhost/job/active/duedate\n"
            "GET http://localhost/job/*/duedate\n",
            encoding="utf-8",
        )
        hits = gate.requirements(self.root, self.plan).update_hits["app"]
        self.assertEqual([3, 4], [int(location.rsplit(":", 2)[1]) for location in hits])

    def test_due_date_scan_accepts_quoted_property_keys(self):
        (self.root / "app" / "Timer.js").write_text(
            "obj['duedate']; obj[\"duedate\"]; obj[`duedate`];\n",
            encoding="utf-8",
        )
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root, reset_source_snapshot=True))
        snapshot = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        self.assertEqual(
            ["duedate"] * 3, list(snapshot["source_updates"]["app"].values())
        )
        self.assertEqual([], gate.requirements(self.root, self.plan).issues)

    def test_active_timer_gate_requires_mapping_and_runtime_evidence(self):
        key = self.install_active_timer_decision()
        plan = gate.requirements(self.root, self.plan)
        self.assertEqual([], plan.issues)
        self.assertIn(key, plan.required)
        with self.assertRaises(gate.EvidenceError):
            self.submit(
                ("module", "app", "active_timer_updates", None),
                action="review", disposition="no_updates",
            )
        self.assertEqual(1, self.audit())
        self.assertIn("Missing timer active_instance_reschedule", "\n".join(self.summary()["issues"]))
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        self.assertEqual("READY", self.summary()["gate"])

    def test_active_timer_gate_rejects_reused_caller_and_unmapped_source(self):
        self.install_active_timer_decision(multiple_callers=True)
        update = self.plan["active_timer_update_decision"]["updates"][0]
        callers = update["caller_mappings"]
        self.assertEqual([], gate.requirements(self.root, self.plan).issues)

        callers[1]["migrated_caller_location"] = callers[0]["migrated_caller_location"]
        write_json(self.root / gate.EVIDENCE, self.plan)
        self.assertIn(
            "migrated caller location can map to only one due-date caller",
            "\n".join(gate.requirements(self.root, self.plan).issues),
        )
        callers[1]["migrated_caller_location"] = "app/Timer.java:2:1"
        callers.pop()
        write_json(self.root / gate.EVIDENCE, self.plan)
        with self.assertRaisesRegex(gate.EvidenceError, "classify each non-timer"):
            self.submit(
                ("module", "app", "active_timer_updates", None),
                action="review", disposition="mixed",
            )

    def test_active_timer_gate_maps_one_source_helper_to_distinct_callers(self):
        key = self.install_active_timer_decision()
        (self.root / "app" / "Timer.java").write_text(
            "terminationDateUpdater.update(projectId, dueDate);\n" * 2,
            encoding="utf-8",
        )
        callers = self.plan["active_timer_update_decision"]["updates"][0]["caller_mappings"]
        callers.append({
            **callers[0], "migrated_caller_location": "app/Timer.java:2:1",
        })
        write_json(self.root / gate.EVIDENCE, self.plan)
        plan = gate.requirements(self.root, self.plan)
        self.assertEqual([], plan.issues)
        self.assertEqual(2, len(plan.active_timer_decisions[key[1]]["caller_mappings"]))
        self.assertEqual(0, self.submit(
            ("module", "app", "active_timer_updates", None),
            action="review",
        ))

    def test_active_timer_gate_cannot_reuse_a_source_helper_for_different_timers(self):
        self.install_active_timer_decision()
        second = {
            **self.plan["models"][0],
            "source_path": "models/other.bpmn",
            "path": "models/converted-c8-other.bpmn",
        }
        self.plan["models"].append(second)
        self.plan["deployment_sets"].append({
            "name": "other", "modules": ["app"], "models": [second["path"]],
        })
        for path in (second["source_path"], second["path"]):
            (self.root / path).write_text(message_rearm_bpmn(), encoding="utf-8")
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        inventory["models"].append(second["source_path"])
        write_json(self.root / gate.INVENTORY, inventory)
        (self.root / "app" / "Timer.java").write_text(
            "terminationDateUpdater.update(projectId, dueDate);\n" * 2,
            encoding="utf-8",
        )
        updates = self.plan["active_timer_update_decision"]["updates"]
        updates.append({
            **updates[0],
            "model_path": second["path"],
            "caller_mappings": [{
                **updates[0]["caller_mappings"][0],
                "migrated_caller_location": "app/Timer.java:2:1",
            }],
        })
        write_json(self.root / gate.EVIDENCE, self.plan)
        self.assertEqual(
            ["A due-date location cannot map to different timers"],
            gate.requirements(self.root, self.plan).issues,
        )

    def test_active_timer_gate_rejects_retained_setters_after_moving(self):
        self.install_active_timer_decision()
        source = self.root / "app" / "Timer.java"
        for current in (
            "terminationDateUpdater.update(projectId, dueDate);\n"
            "managementService.setJobDuedate(jobId, dueDate);\n",
            "// Recipe TODO: setJobDuedate() needs migration.\n"
            "managementService\n  .setJobDuedate(\n    jobId, dueDate);\n",
        ):
            with self.subTest(current=current):
                source.write_text(current, encoding="utf-8")
                plan = gate.requirements(self.root, self.plan)
                self.assertIn(
                    "mapped C7 due-date location remains in the migrated source",
                    "\n".join(plan.issues),
                )

    def test_active_timer_gate_rejects_moved_setter_with_changed_arguments(self):
        self.install_active_timer_decision()
        (self.root / "app" / "Timer.java").write_text(
            "terminationDateUpdater.update(projectId, dueDate);\n"
            "managementService.setJobDuedate(renamedJobId, renamedDate);\n",
            encoding="utf-8",
        )
        plan = gate.requirements(self.root, self.plan)
        self.assertIn(
            "mapped C7 due-date location remains in the migrated source",
            "\n".join(plan.issues),
        )
        with self.assertRaisesRegex(gate.EvidenceError, "mapped C7 due-date location remains"):
            self.submit(
                ("module", "app", "active_timer_updates", None),
                action="review",
                disposition="mixed",
                non_timer_evidence_json=json.dumps([{
                    "location": plan.update_hits["app"][0],
                    "evidence": "MIGRATION_REPORT.md#claimed-non-timer",
                }]),
            )

    def test_active_timer_gate_allows_evidenced_different_operation(self):
        self.install_active_timer_decision()
        (self.root / "app" / "Timer.java").write_text(
            "terminationDateUpdater.update(projectId, dueDate);\n"
            'String jobUrl = "http://localhost/job/42/duedate";\n',
            encoding="utf-8",
        )
        plan = gate.requirements(self.root, self.plan)
        self.assertEqual([], plan.issues)
        self.assertEqual(0, self.submit(
            ("module", "app", "active_timer_updates", None),
            action="review",
            disposition="mixed",
            non_timer_evidence_json=json.dumps([{
                "location": plan.update_hits["app"][0],
                "evidence": "MIGRATION_REPORT.md#non-timer-job",
            }]),
        ))

    def test_active_timer_gate_rejects_invalid_operation_snapshot(self):
        self.install_active_timer_decision()
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        location = next(iter(inventory["source_updates"]["app"]))
        inventory["source_updates"]["app"][location] = []
        write_json(self.root / gate.INVENTORY, inventory)
        self.assertIn(
            "invalid pre-migration due-date source snapshot",
            "\n".join(gate.requirements(self.root, self.plan).issues),
        )

    def test_active_timer_mapping_requires_direct_date_expression(self):
        self.install_active_timer_decision()
        model = self.root / "models/converted-c8-process.bpmn"
        original = model.read_text(encoding="utf-8")
        for expression in ('="dueDate"', "=payload.dueDate"):
            with self.subTest(expression=expression):
                model.write_text(
                    original.replace(
                        "<bpmn:timeDate>=dueDate</bpmn:timeDate>",
                        f"<bpmn:timeDate>{expression}</bpmn:timeDate>",
                    ),
                    encoding="utf-8",
                )
                self.assertIn(
                    "converted model lacks the mapped message-driven timer rearm path",
                    "\n".join(gate.requirements(self.root, self.plan).issues),
                )

    def test_active_timer_mapping_rejects_an_undeclared_gateway_outgoing_flow(self):
        self.install_active_timer_decision()
        model = self.root / "models/converted-c8-process.bpmn"
        original = model.read_text(encoding="utf-8")
        end = '<bpmn:endEvent id="End"><bpmn:incoming>Call_End</bpmn:incoming></bpmn:endEvent>'
        self.assertIn(end, original)
        for source, anchor in (
            ("Prepare", '<bpmn:sequenceFlow id="Prepare_Call" sourceRef="Prepare" targetRef="DeadlineWaitCall" />'),
            ("DateChanged", '<bpmn:sequenceFlow id="Update_Prepare" sourceRef="DateChanged" targetRef="Prepare" />'),
        ):
            with self.subTest(source=source):
                self.assertIn(anchor, original)
                model.write_text(
                    original.replace(
                        end, end + '<bpmn:endEvent id="Bypass"><bpmn:incoming>BypassFlow</bpmn:incoming></bpmn:endEvent>',
                        1,
                    ).replace(
                        anchor,
                        anchor + f'<bpmn:sequenceFlow id="BypassFlow" sourceRef="{source}" targetRef="Bypass" />',
                        1,
                    ),
                    encoding="utf-8",
                )
                plan = gate.requirements(self.root, self.plan)
                self.assertIn(
                    "converted model lacks the mapped message-driven timer rearm path",
                    "\n".join(plan.issues),
                )

    def test_active_timer_gate_accepts_setup_before_the_merge(self):
        self.install_active_timer_decision()
        model = self.root / "models/converted-c8-process.bpmn"
        xml = model.read_text(encoding="utf-8")
        model.write_text(
            xml.replace(
                '<bpmn:startEvent id="Start"><bpmn:outgoing>Start_Prepare</bpmn:outgoing></bpmn:startEvent>',
                '<bpmn:startEvent id="Start"><bpmn:outgoing>Start_Setup</bpmn:outgoing></bpmn:startEvent>'
                '<bpmn:task id="Setup"><bpmn:incoming>Start_Setup</bpmn:incoming>'
                '<bpmn:outgoing>Setup_Prepare</bpmn:outgoing></bpmn:task>',
            ).replace(
                "<bpmn:incoming>Start_Prepare</bpmn:incoming>",
                "<bpmn:incoming>Setup_Prepare</bpmn:incoming>",
            ).replace(
                '<bpmn:sequenceFlow id="Start_Prepare" sourceRef="Start" targetRef="Prepare" />',
                '<bpmn:sequenceFlow id="Start_Setup" sourceRef="Start" targetRef="Setup" />'
                '<bpmn:sequenceFlow id="Setup_Prepare" sourceRef="Setup" targetRef="Prepare" />',
            ),
            encoding="utf-8",
        )
        self.assertEqual([], gate.requirements(self.root, self.plan).issues)

    def test_active_timer_gate_validates_deadlines_and_cleanup(self):
        key = self.install_active_timer_decision()
        plan = gate.requirements(self.root, self.plan)
        observation = self.active_timer_observation(key)
        check = {
            "environment": "local",
            "target_disposable": True,
            "target_version": "8.9.21",
            "isolation_plan": "Remove the disposable target.",
            "output": json.dumps(observation),
        }
        gate.validate_active_timer_observation(plan, key, check)
        for path, field, value in (
            (("observation", "active_timer", "updates", 0), "timer_active_before_update", False),
            (("observation", "active_timer", "updates", 0), "old_deadline_fire_count", 1),
            (("observation", "active_timer", "updates", 0), "checked_after_old_deadline", "2050-11-23T00:00:24Z"),
            (("observation", "active_timer"), "final_deadline_last_active_at", "2050-11-23T00:00:29Z"),
            (("observation", "active_timer"), "final_deadline_fired_at", "2050-11-23T00:00:34Z"),
            (("observation", "active_timer"), "final_deadline_fire_count", 2),
            (("cleanup",), "completed", False),
        ):
            with self.subTest(field=field, value=value):
                invalid = json.loads(json.dumps(observation))
                target = invalid
                for part in path:
                    target = target[part]
                target[field] = value
                with self.assertRaises(gate.EvidenceError):
                    gate.validate_active_timer_observation(
                        plan, key, {**check, "output": json.dumps(invalid)}
                    )
        invalid = json.loads(json.dumps(observation))
        invalid["observation"]["active_timer"]["updates"][0]["old_deadline"] = "2050-11-23T00:00:05Z"
        with self.assertRaisesRegex(gate.EvidenceError, "earlier and a later"):
            gate.validate_active_timer_observation(
                plan, key, {**check, "output": json.dumps(invalid)}
            )

    def test_documented_active_timer_observation_passes_validation(self):
        key = self.install_active_timer_decision()
        plan = gate.requirements(self.root, self.plan)
        reference = (
            FIXTURE.parents[1] / "skills" / "migrate-c7-to-c8-code"
            / "references" / "validation-evidence.md"
        ).read_text(encoding="utf-8")
        example = json.loads(
            reference.split("The outer object must identify the disposable", 1)[1]
            .split("```json\n", 1)[1].split("\n```", 1)[0]
        )
        decision = plan.active_timer_decisions[key[1]]
        for field in (
            "model_path", "process_id", "rearm_process_id", "rearm_call_activity_id",
            "timer_id", "strategy", "message_name", "correlation_key_variable",
            "date_variable", "message_date_variable",
        ):
            example["observation"]["active_timer"][field] = decision[field]
        gate.validate_active_timer_observation(
            plan, key, {
                "environment": "local",
                "target_disposable": True,
                "target_version": "8.9.21",
                "isolation_plan": "Remove the disposable target.",
                "output": json.dumps(example),
            },
        )

    def test_active_timer_gate_rejects_unsafe_runtime_before_execution(self):
        key = self.install_active_timer_decision()
        marker = self.root / "ran"
        command = [sys.executable, "-c", f"from pathlib import Path; Path({str(marker)!r}).touch()"]
        for environment, version in (("non-production", "8.9.21"), ("local", "8.9.22")):
            with self.subTest(environment=environment, version=version):
                with self.assertRaisesRegex(gate.EvidenceError, "approved version"):
                    self.submit(
                        key, command=command, environment=environment,
                        target_version=version, isolation_plan="Remove the target.",
                    )
                self.assertFalse(marker.exists())

    def test_stale_source_blocks_commands_before_execution_and_can_be_refreshed(self):
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        (self.root / "app" / "Starter.mts").write_text(
            "class Starter {}", encoding="utf-8"
        )
        model = self.plan["models"][0]["path"]
        command = [sys.executable, "-c", "from pathlib import Path; Path('executed').touch()"]
        with self.assertRaisesRegex(gate.EvidenceError, "must pass before execution"):
            self.submit(("model", model, "deployment", None), environment="local", command=command)
        self.assertFalse((self.root / "executed").exists())
        self.assertEqual(1, self.audit())
        self.assertIn("stale source", "\n".join(self.summary()["issues"]))
        self.assertEqual(0, self.submit(("model", model, "lint", None)))
        self.assertEqual(0, self.submit(
            ("deployment_set", "shared", "preflight", None), action="review"
        ))
        self.assertEqual(0, self.submit(
            ("model", model, "deployment", None), environment="local", command=command
        ))
        self.assertTrue((self.root / "executed").exists())
        self.complete_required_checks()
        self.assertEqual(0, self.audit())

    def test_stale_timer_review_can_be_refreshed(self):
        self.write_scope(timer=True)
        target = "models/converted-c8-process.bpmn#p#Start"
        review = ("timer", target, "disposition", None)
        self.assertEqual(0, self.submit(review, action="review"))
        path = self.root / "models/converted-c8-process.bpmn"
        path.write_text(
            path.read_text(encoding="utf-8").replace("R/PT1H", "R/PT2H"),
            encoding="utf-8",
        )
        command = [sys.executable, "-c", "from pathlib import Path; Path('executed').touch()"]
        with self.assertRaisesRegex(gate.EvidenceError, "disposition must pass"):
            self.submit(
                ("timer", target, "preflight", None), environment="local",
                isolation_plan="Destroy the target.", command=command,
            )
        self.assertFalse((self.root / "executed").exists())
        self.assertEqual(0, self.submit(review, action="review", disposition="change"))

    def test_forged_approval_reference_cannot_pass_report(self):
        key = ("deployment_set", "shared", "preflight", None)
        self.assertEqual(0, self.submit(key, action="review"))
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        log = self.root / evidence["checks"][0]
        check = json.loads(log.read_text(encoding="utf-8"))
        check["reference"] = "not applicable"
        write_json(log, check)
        self.assertEqual(1, self.audit())
        self.assertIn("concrete approval", "\n".join(self.summary()["issues"]))

    def test_cli_records_decisions_and_structured_timer_observation(self):
        self.write_scope(timer=True)
        script = [sys.executable, str(SCRIPT_DIR / "validate_migration_evidence.py"),
                  "--project-root", str(self.root)]
        target = "models/converted-c8-process.bpmn#p#Start"
        review = subprocess.run([
            *script, "review", "--type", "timer", "--target", target,
            "--kind", "disposition", "--disposition", "preserve",
            "--reference", "MIGRATION_REPORT.md#timer", "--note", "Approved R/PT1H.",
        ], capture_output=True, text=True, check=False)
        self.assertEqual(0, review.returncode, review.stderr)
        observation = json.dumps(self.timer_observation(("timer", target, "preflight", None)))
        run = subprocess.run([
            *script, "run", "--type", "timer", "--target", target,
            "--kind", "preflight", "--environment", "local",
            "--target-disposable", "--target-version", "8.9.21",
            "--isolation-plan", "Destroy the disposable target.", "--",
            sys.executable, "-c", f"print({observation!r})",
        ], capture_output=True, text=True, check=False)
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertIn("PASSED timer", run.stdout)

    def test_nested_event_subprocess_timer_does_not_schedule_on_deployment(self):
        extra = (
            '<bpmn:subProcess id="Sub" triggeredByEvent="true">'
            '<bpmn:startEvent id="Nested"><bpmn:timerEventDefinition>'
            '<bpmn:timeCycle>R/PT1H</bpmn:timeCycle>'
            '</bpmn:timerEventDefinition></bpmn:startEvent></bpmn:subProcess>'
        )
        self.write_scope(extra=extra)
        self.assertEqual([], gate.requirements(self.root, self.plan).timers[
            "models/converted-c8-process.bpmn"
        ])

    def test_removed_source_timer_needs_a_decision_but_no_runtime_test(self):
        self.write_scope(timer=True)
        source = self.root / "models/process.bpmn"
        source.write_text(
            source.read_text(encoding="utf-8").replace("R/PT1H", "${originalCycle}"),
            encoding="utf-8",
        )
        converted = self.root / "models/converted-c8-process.bpmn"
        converted.write_text(
            converted.read_text(encoding="utf-8").replace(
                "<bpmn:timerEventDefinition><bpmn:timeCycle>R/PT1H</bpmn:timeCycle>"
                "</bpmn:timerEventDefinition>", ""
            ),
            encoding="utf-8",
        )
        plan = gate.requirements(self.root, self.plan)
        self.assertEqual([], plan.issues)
        self.assertEqual([], plan.timers["models/converted-c8-process.bpmn"])
        self.assertEqual(0, self.submit(
            ("timer", "models/converted-c8-process.bpmn#p#Start", "disposition", None),
            action="review", disposition="remove",
        ))

    def test_retained_unresolved_timer_cycle_blocks_gate(self):
        self.write_scope(timer=True)
        converted = self.root / "models/converted-c8-process.bpmn"
        original = converted.read_text(encoding="utf-8")
        for cycle in ("= duration", "${timerCycle}", "#{timerCycle}"):
            with self.subTest(cycle=cycle):
                converted.write_text(original.replace("R/PT1H", cycle), encoding="utf-8")
                self.assertEqual(1, self.audit())
                self.assertIn("unresolved timer cycle", "\n".join(self.summary()["issues"]))

    def test_nested_modules_and_same_line_calls_are_scoped_separately(self):
        self.plan["modules"].append({
            "path": ".", "runtime_mode": "none",
            "test_suites": [{"name": "unit", "requires_docker": False}],
        })
        self.plan["deployment_sets"][0]["modules"].append(".")
        self.write_scope()
        (self.root / "app" / "Timer.cjs").write_text(
            "setJobDuedate(a, b); setJobDuedate(c, d);", encoding="utf-8"
        )
        plan = gate.requirements(self.root, self.plan)
        self.assertEqual([], plan.update_hits["."])
        self.assertEqual(2, len(set(plan.update_hits["app"])))
        self.assertEqual([], plan.issues)

    def test_root_module_report_does_not_stale_its_checks(self):
        self.plan["modules"][0]["path"] = "."
        self.plan["deployment_sets"][0]["modules"] = ["."]
        self.write_scope()
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        self.assertEqual(0, self.audit())

    def test_unsafe_model_path_is_not_read(self):
        outside = self.root.parent / f"{self.root.name}-outside.bpmn"
        outside.write_text("outside", encoding="utf-8")
        self.addCleanup(outside.unlink)
        (self.root / self.plan["models"][0]["path"]).unlink()
        (self.root / self.plan["models"][0]["path"]).symlink_to(outside)
        self.assertEqual(1, self.audit())
        self.assertIn("outside the project", "\n".join(self.summary()["issues"]))

    def test_docker_failure_blocks_dependent_suites_not_independent_checks(self):
        self.plan["modules"][0]["test_suites"].append(
            {"name": "integration", "requires_docker": True}
        )
        self.write_scope()
        with patch.object(
            gate.subprocess, "run",
            return_value=subprocess.CompletedProcess(["docker", "info"], 1, "Daemon is unavailable"),
        ):
            self.assertEqual(
                1, self.submit(("project", ".", "docker_info", None), command=["docker", "info"])
            )
        suite = ("module", "app", "tests", "integration")
        self.assertEqual(1, self.submit(suite, action="block", reason="Docker probe failed"))
        self.assertEqual(0, self.submit(("module", "app", "tests", "unit")))
        self.assertEqual(1, self.audit())
        checks = self.summary()["checks"]
        self.assertEqual(
            "docker_unavailable",
            next(check["failure_class"] for check in checks if check["scenario"] == "integration"),
        )
        self.assertTrue(
            any(check["scenario"] == "unit" and check["result"] == "passed" for check in checks)
        )

    def test_classification_uses_the_failed_probe_and_preserves_exit_code(self):
        failed = ("module", "app", "tests", "unit")
        self.assertEqual(
            1, self.submit(failed, command=[sys.executable, "-c", "import sys; sys.exit(2)"])
        )
        args = Namespace(
            type="module", target="app", kind="tests", scenario="unit",
            failure_class="application", reason="The migrated assertion failed",
        )
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.classify(self.root, args))
        self.assertEqual(1, self.audit())
        check = next(check for check in self.summary()["checks"] if check["kind"] == "tests")
        self.assertEqual(("application", 2), (check["failure_class"], check["exit_code"]))
        args.failure_class = "docker_unavailable"
        with self.assertRaisesRegex(gate.EvidenceError, "failed Docker probe"):
            gate.classify(self.root, args)

    def test_runtime_modules_require_configuration_and_both_launch_checks(self):
        self.plan["modules"][0]["runtime_mode"] = "spring-boot"
        self.write_scope()
        with self.assertRaisesRegex(gate.EvidenceError, "local or non-production"):
            self.submit(("module", "app", "executable_jar", None))
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        plan = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        plan["checks"] = [
            path for path in plan["checks"]
            if json.loads((self.root / path).read_text(encoding="utf-8"))["kind"] != "executable_jar"
        ]
        write_json(self.root / gate.EVIDENCE, plan)
        self.assertEqual(1, self.audit())
        self.assertIn("Missing module executable_jar", "\n".join(self.summary()["issues"]))

    def test_cli_records_and_reports_a_check(self):
        previous = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))["run_id"]
        init = subprocess.run(
            [sys.executable, str(SCRIPT_DIR / "validate_migration_evidence.py"),
             "--project-root", str(self.root), "init"],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(0, init.returncode, init.stderr)
        current = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))["run_id"]
        self.assertNotEqual(previous, current)
        command = [
            sys.executable, str(SCRIPT_DIR / "validate_migration_evidence.py"),
            "--project-root", str(self.root), "run",
            "--type", "module", "--target", "app", "--kind", "compile",
            "--", sys.executable, "-c", "print('compiled')",
        ]
        run = subprocess.run(command, capture_output=True, text=True, check=False)
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertIn("compiled", run.stdout)
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        recorded = json.loads((self.root / evidence["checks"][0]).read_text(encoding="utf-8"))
        self.assertEqual(current, recorded["run_id"])
        report = subprocess.run(
            [sys.executable, str(SCRIPT_DIR / "validate_migration_evidence.py"),
             "--project-root", str(self.root), "report"],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(1, report.returncode)
        self.assertIn("NOT READY", report.stdout)

    def test_report_removes_stale_gate_but_rejects_unmarked_status(self):
        self.complete_required_checks()
        (self.root / gate.REPORT).write_text(
            "## Other\n\nKeep this section.\n\n## Aggregate validation gate\n"
            "**Validation gate:** **READY**\n\n## More\n\nAlso keep this.\n",
            encoding="utf-8",
        )
        self.assertEqual(0, self.audit())
        report = (self.root / gate.REPORT).read_text(encoding="utf-8")
        self.assertEqual(1, report.count("**Validation gate:**"))
        self.assertIn("Also keep this.", report)
        (self.root / gate.REPORT).write_text(
            "## Other\n\n**Validation gate:** **READY**\n", encoding="utf-8"
        )
        self.assertEqual(1, self.audit())
        report = (self.root / gate.REPORT).read_text(encoding="utf-8")
        self.assertNotIn("**Validation gate:** **READY**", report)
        self.assertEqual("NOT READY", self.summary()["gate"])

    def test_forged_command_result_and_missing_log_are_rejected(self):
        self.complete_required_checks()
        plan = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        log = next(
            self.root / name for name in plan["checks"]
            if json.loads((self.root / name).read_text(encoding="utf-8"))["kind"] == "compile"
        )
        value = json.loads(log.read_text(encoding="utf-8"))
        value["exit_code"] = 3
        write_json(log, value)
        self.assertEqual(1, self.audit())
        self.assertIn("passed without exit code 0", "\n".join(self.summary()["issues"]))
        log.unlink()
        self.assertEqual(1, self.audit())
        self.assertIn("missing", "\n".join(self.summary()["issues"]).lower())

    def test_summary_symlink_cannot_overwrite_evidence(self):
        self.complete_required_checks()
        original = (self.root / gate.EVIDENCE).read_bytes()
        (self.root / gate.SUMMARY).unlink()
        (self.root / gate.SUMMARY).symlink_to(self.root / gate.EVIDENCE)
        with self.assertRaises(gate.EvidenceError):
            self.audit()
        self.assertEqual(original, (self.root / gate.EVIDENCE).read_bytes())

    def test_write_file_rejects_symlinked_output_directory(self):
        redirect_root = self.root / "redirect"
        redirect_root.mkdir()
        sentinel = redirect_root / "result.json"
        sentinel.write_text("original", encoding="utf-8")
        output_directory = self.root / gate.VALIDATION / "generated"
        output_directory.symlink_to(redirect_root, target_is_directory=True)

        with self.assertRaises(gate.EvidenceError):
            gate.write_file(
                self.root,
                gate.VALIDATION / "generated" / "result.json",
                "replacement",
            )

        self.assertEqual("original", sentinel.read_text(encoding="utf-8"))

    def test_discover_reports_rejects_symlinked_parent_components(self):
        scenarios = (
            (
                "target",
                Path("target"),
                Path("redirect"),
                Path("surefire-reports/TEST-result.xml"),
            ),
            (
                "report-directory",
                Path("target/surefire-reports"),
                Path("redirect-reports"),
                Path("TEST-result.xml"),
            ),
        )

        for name, link_relative, target_relative, report_relative in scenarios:
            with self.subTest(component=name):
                module_root = self.root / f"module-{name}"
                target = module_root / target_relative
                report = target / report_relative
                report.parent.mkdir(parents=True)
                report.write_text("<testsuite />", encoding="utf-8")
                link = module_root / link_relative
                link.parent.mkdir(parents=True, exist_ok=True)
                link.symlink_to(target, target_is_directory=True)

                with self.assertRaises(gate.EvidenceError):
                    gate.discover_reports(
                        self.root,
                        module_root.name,
                        ["target/surefire-reports/TEST-*.xml"],
                        "JUnit report",
                    )

        module_target = self.root / "real-module"
        report = module_target / "target" / "surefire-reports" / "TEST-result.xml"
        report.parent.mkdir(parents=True)
        report.write_text("<testsuite />", encoding="utf-8")
        module_link = self.root / "module-link"
        module_link.symlink_to(module_target, target_is_directory=True)

        with self.assertRaises(gate.EvidenceError):
            gate.discover_reports(
                self.root,
                module_link.name,
                ["target/surefire-reports/TEST-*.xml"],
                "JUnit report",
            )
        with self.assertRaises(gate.EvidenceError):
            gate.copy_reports(
                self.root,
                module_link.name,
                [report],
                Path("module-link-copies"),
            )

    def test_copy_reports_rejects_symlinked_destination_components(self):
        module_root = self.root / "module"
        report = module_root / "target" / "surefire-reports" / "TEST-result.xml"
        report.parent.mkdir(parents=True)
        report.write_text("<testsuite />", encoding="utf-8")
        scenarios = (
            ("destination-root", Path("target/surefire-reports/TEST-result.xml")),
            ("nested-component", Path("surefire-reports/TEST-result.xml")),
        )

        for name, redirected_report in scenarios:
            with self.subTest(component=name):
                destination = gate.VALIDATION / "cpt" / name / "suite"
                destination_root = self.root / destination
                redirect_root = self.root / f"redirect-{name}"
                sentinel = redirect_root / redirected_report
                sentinel.parent.mkdir(parents=True)
                sentinel.write_text("original", encoding="utf-8")
                if name == "destination-root":
                    destination_root.parent.mkdir(parents=True)
                    symlink = destination_root
                else:
                    destination_root.mkdir(parents=True)
                    symlink = destination_root / "target"
                symlink.symlink_to(redirect_root, target_is_directory=True)

                with self.assertRaises(gate.EvidenceError):
                    gate.copy_reports(
                        self.root, "module", [report], destination
                    )

                self.assertEqual("original", sentinel.read_text(encoding="utf-8"))

    def test_copy_reports_rejects_symlinked_source_components(self):
        module_root = self.root / "module-source-link"
        redirect_root = module_root / "redirect-reports"
        report = redirect_root / "TEST-result.xml"
        report.parent.mkdir(parents=True)
        report.write_text("<testsuite />", encoding="utf-8")
        symlink = module_root / "target" / "surefire-reports"
        symlink.parent.mkdir(parents=True)
        symlink.symlink_to(redirect_root, target_is_directory=True)
        source = symlink / report.name

        with self.assertRaises(gate.EvidenceError):
            gate.copy_reports(
                self.root,
                module_root.name,
                [source],
                Path("symlinked-source-copies"),
            )

    def test_copy_reports_replaces_hard_link_without_overwriting_linked_file(self):
        module_root = self.root / "module"
        report = module_root / "target" / "surefire-reports" / "TEST-result.xml"
        report.parent.mkdir(parents=True)
        report.write_text("<testsuite />\n", encoding="utf-8")
        destination = gate.VALIDATION / "cpt" / "hard-link" / "suite"
        target = self.root / destination / "target" / "surefire-reports" / "TEST-result.xml"
        target.parent.mkdir(parents=True)
        linked_file = self.root / "pom.xml"
        linked_file.write_text("original project file", encoding="utf-8")
        os.link(linked_file, target)

        copied = gate.copy_reports(self.root, "module", [report], destination)

        self.assertEqual([target.relative_to(self.root).as_posix()], copied)
        self.assertEqual("original project file", linked_file.read_text(encoding="utf-8"))
        self.assertEqual(report.read_bytes(), target.read_bytes())
        self.assertFalse(os.path.samefile(linked_file, target))


class LiveTimerFixtureRunnerTest(unittest.TestCase):
    def test_cleanup_is_scoped_to_the_fixture_session_and_version(self):
        session = str(uuid.uuid4())
        listing = (
            "owned\tcamunda/camunda:8.9.21\n"
            "different-version\tcamunda/camunda:8.10\n"
            "ryuk\ttestcontainers/ryuk:0.8.1\n"
        )
        with patch.object(runner, "docker", side_effect=[listing, "", ""]) as docker:
            runner.cleanup_session(session)
        self.assertIn(
            f"label=org.testcontainers.sessionId={session}", docker.call_args_list[0].args
        )
        self.assertEqual(
            [call("container", "rm", "--force", "owned")],
            [invocation for invocation in docker.call_args_list if invocation.args[1] == "rm"],
        )

    def test_cleanup_rejects_a_remaining_owned_target(self):
        session = str(uuid.uuid4())
        listing = "owned\tcamunda/camunda:8.9.21\n"
        with patch.object(runner, "docker", side_effect=[listing, "", listing]):
            with self.assertRaisesRegex(RuntimeError, "cleanup failed"):
                runner.cleanup_session(session)

    def test_failed_fixture_still_cleans_up_without_reporting_success(self):
        with tempfile.TemporaryDirectory() as directory:
            session_file = Path(directory) / "session"
            observation_file = Path(directory) / "observation"
            session = str(uuid.uuid4())

            def fail_maven(*args, **kwargs):
                session_file.write_text(session, encoding="utf-8")
                return subprocess.CompletedProcess(args, 1, "Maven failed\n", "")

            with (
                patch.object(runner, "SESSION_FILE", session_file),
                patch.object(runner, "OBSERVATION", observation_file),
                patch.object(runner, "require_java_21"),
                patch.object(
                    runner,
                    "docker",
                    side_effect=["Docker ready", "owned\tcamunda/camunda:8.9.21\n", "", ""],
                ) as docker,
                patch.object(runner.subprocess, "run", side_effect=fail_maven),
                redirect_stdout(StringIO()) as output,
                redirect_stderr(StringIO()),
            ):
                result = runner.main()
            self.assertEqual(1, result)
            self.assertFalse(session_file.exists())
            self.assertFalse(observation_file.exists())
            self.assertIn("Maven failed", output.getvalue())
            docker.assert_any_call("container", "rm", "--force", "owned")

    def test_interrupted_fixture_cleans_up_before_propagating(self):
        with tempfile.TemporaryDirectory() as directory:
            session_file = Path(directory) / "session"
            session = str(uuid.uuid4())

            def interrupt_maven(*args, **kwargs):
                session_file.write_text(session, encoding="utf-8")
                raise KeyboardInterrupt

            with (
                patch.object(runner, "SESSION_FILE", session_file),
                patch.object(runner, "OBSERVATION", Path(directory) / "observation"),
                patch.object(runner, "require_java_21"),
                patch.object(
                    runner,
                    "docker",
                    side_effect=["Docker ready", "owned\tcamunda/camunda:8.9.21\n", "", ""],
                ) as docker,
                patch.object(runner.subprocess, "run", side_effect=interrupt_maven),
            ):
                with self.assertRaises(KeyboardInterrupt):
                    runner.main()
            self.assertFalse(session_file.exists())
            docker.assert_any_call("container", "rm", "--force", "owned")

    def test_interrupted_or_failed_cleanup_keeps_session_for_retry(self):
        for operation, error in (
            ("session_id", KeyboardInterrupt()),
            ("cleanup_session", KeyboardInterrupt()),
            ("cleanup_session", RuntimeError("Docker unavailable")),
        ):
            with (
                self.subTest(operation=operation, error=type(error).__name__),
                tempfile.TemporaryDirectory() as directory,
            ):
                session_file = Path(directory) / "session"
                session = str(uuid.uuid4())

                def complete_maven(*args, **kwargs):
                    session_file.write_text(session, encoding="utf-8")
                    return subprocess.CompletedProcess(args, 1, "Maven failed\n", "")

                with (
                    patch.object(runner, "SESSION_FILE", session_file),
                    patch.object(runner, "OBSERVATION", Path(directory) / "observation"),
                    patch.object(runner, "require_java_21"),
                    patch.object(runner, "docker", return_value="Docker ready"),
                    patch.object(runner.subprocess, "run", side_effect=complete_maven),
                    patch.object(runner, operation, side_effect=error),
                    redirect_stdout(StringIO()),
                    redirect_stderr(StringIO()),
                ):
                    with self.assertRaises(type(error)):
                        runner.main()
                self.assertEqual(session, session_file.read_text(encoding="utf-8"))

    def test_fixture_reconciles_previous_session_before_new_run(self):
        with tempfile.TemporaryDirectory() as directory:
            session_file = Path(directory) / "session"
            previous = str(uuid.uuid4())
            current = str(uuid.uuid4())
            session_file.write_text(previous, encoding="utf-8")

            def complete_maven(*args, **kwargs):
                self.assertFalse(session_file.exists())
                session_file.write_text(current, encoding="utf-8")
                return subprocess.CompletedProcess(args, 1, "Maven failed\n", "")

            with (
                patch.object(runner, "SESSION_FILE", session_file),
                patch.object(runner, "OBSERVATION", Path(directory) / "observation"),
                patch.object(runner, "require_java_21"),
                patch.object(runner, "docker", return_value="Docker ready"),
                patch.object(runner.subprocess, "run", side_effect=complete_maven),
                patch.object(runner, "cleanup_session") as cleanup,
                redirect_stdout(StringIO()),
                redirect_stderr(StringIO()),
            ):
                self.assertEqual(1, runner.main())
            self.assertEqual([call(previous), call(current)], cleanup.call_args_list)
            self.assertFalse(session_file.exists())

    def test_failed_previous_session_cleanup_blocks_new_run(self):
        with tempfile.TemporaryDirectory() as directory:
            session_file = Path(directory) / "session"
            session = str(uuid.uuid4())
            session_file.write_text(session, encoding="utf-8")
            with (
                patch.object(runner, "SESSION_FILE", session_file),
                patch.object(runner, "OBSERVATION", Path(directory) / "observation"),
                patch.object(runner, "require_java_21"),
                patch.object(runner, "docker", return_value="Docker ready"),
                patch.object(runner.subprocess, "run") as maven,
                patch.object(runner, "cleanup_session", side_effect=RuntimeError("Docker unavailable")),
            ):
                with self.assertRaisesRegex(RuntimeError, "Docker unavailable"):
                    runner.main()
            self.assertEqual(session, session_file.read_text(encoding="utf-8"))
            maven.assert_not_called()

    def test_successful_fixture_emits_gate_observation_contract(self):
        with tempfile.TemporaryDirectory() as directory:
            session_file = Path(directory) / "session"
            observation_file = Path(directory) / "observation"
            session = str(uuid.uuid4())

            def complete_maven(*args, **kwargs):
                session_file.write_text(session, encoding="utf-8")
                write_json(observation_file, {"case1": {}, "active_timer": {}})
                return subprocess.CompletedProcess(args, 0, "", "")

            with (
                patch.object(runner, "SESSION_FILE", session_file),
                patch.object(runner, "OBSERVATION", observation_file),
                patch.object(runner, "require_java_21"),
                patch.object(runner, "docker", side_effect=["Docker ready", "", ""]),
                patch.object(runner.subprocess, "run", side_effect=complete_maven),
                redirect_stdout(StringIO()) as output,
                redirect_stderr(StringIO()),
            ):
                self.assertEqual(0, runner.main())
            result = json.loads(output.getvalue().splitlines()[-1])
            self.assertEqual("8.9.21", result["deployment"]["target_version"])
            self.assertEqual("local", result["deployment"]["environment"])
            self.assertTrue(result["cleanup"]["completed"])
            self.assertIn(session, result["cleanup"]["evidence_reference"])
            self.assertEqual({"case1": {}, "active_timer": {}}, result["observation"])


if __name__ == "__main__":
    unittest.main()
