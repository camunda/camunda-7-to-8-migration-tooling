"""Category-scoped regressions for recorded migration evidence."""

import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
import uuid
from argparse import Namespace
from contextlib import redirect_stderr, redirect_stdout
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
        write_json(self.root / gate.INVENTORY, inventory)
        write_json(self.root / gate.EVIDENCE, self.plan)
        for module in self.plan["modules"]:
            (self.root / module["path"]).mkdir(parents=True, exist_ok=True)
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
        test_source_roots=None,
        test_resource_roots=None,
    ):
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
        inventory["test_run_mode"] = "run"
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
            "| process test | ProcessEngineRule | order.bpmn | Migrate | — |\n",
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

    def cpt_command(self, first_junit=None, second_junit=None, first_coverage=None, second_coverage=None):
        files = {
            "app/target/surefire-reports/TEST-com.example.OrderCptTest.xml": [
                first_junit or '<testsuite><testcase classname="com.example.OrderCptTest" name="testOrder" /></testsuite>',
                second_junit or first_junit or '<testsuite><testcase classname="com.example.OrderCptTest" name="testOrder" /></testsuite>',
            ],
        }
        if first_coverage is not None or second_coverage is not None:
            files["app/target/process-test-coverage/report.json"] = [
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
            command=(
                command
                if command is not None
                else [sys.executable, "-c", "print('check completed')"]
            ),
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

    def complete_required_checks(self):
        plan = gate.requirements(self.root, self.plan)
        self.assertEqual([], plan.issues)
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
            environment = (
                "local" if key[0] in ("timer", "process") or key[2] in (*gate.RUNTIME_CHECKS, "deployment")
                else None
            )
            options = {
                "environment": environment,
                "isolation_plan": "Use an isolated local cluster; remove the timer deployment and instances.",
            }
            if key[2] == "c7_baseline":
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
            self.assertEqual(
                0,
                self.submit(key, action="review" if plan.required[key] == "review" else "run", **options),
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

        baseline_results = gate.aggregate_baseline_results(
            plan.test_contract, mapping["baseline"]["suites"]
        )
        self.assertEqual("passed", baseline_results[test_id]["c7_result"])

        issues = gate.test_parity_issues(plan, {}, mapping)
        self.assertIn(f"{test_id}: manual test is not verified", issues)

        mapping["tests"] = []
        issues = gate.test_parity_issues(plan, {}, mapping)
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
        self.assertEqual([], gate.test_parity_issues(plan, {}, mapping))

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
        spock_id = "app:com.example.OrderSpec#an order can be paid"
        junit = (
            "<testsuite>"
            '<testcase classname="com.example.OrderTest" name="testOrder" />'
            '<testcase classname="com.example.RunCucumberTest" '
            'name="Scenario: customer pays" />'
            '<testcase classname="com.example.OrderSpec" '
            'name="an order can be paid" />'
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

    def test_test_id_parts_rejects_blank_method_names(self):
        for test_id in ("app:com.example.OrderSpec#", "app:com.example.OrderSpec#  "):
            with self.subTest(test_id=test_id):
                with self.assertRaisesRegex(
                    gate.EvidenceError, "Invalid Test Inventory ID"
                ):
                    gate.test_id_parts(test_id)

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
                {"status": "added", "c8_ids": [self.c8_test_id]},
                {"status": "added", "c8_ids": [added_cpt_id]},
                {"status": "added", "c8_ids": [added_cpt_id]},
                {"status": "added", "c8_ids": [added_cpt_id, added_cpt_id]},
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

        self.assertEqual(
            0,
            self.submit(("project", ".", "test_freeze", None), command=[]),
        )
        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "test_repeat", "unit"),
                command=self.cpt_command(first_coverage='{"processCoverages":[]}'),
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

        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        stale_issues = []
        stale_checks = gate.load_checks(
            self.root, evidence, gate.requirements(self.root, evidence), stale_issues
        )
        self.assertEqual([], stale_issues)
        self.assertNotIn(
            ("test", class_target, "assertion_strength", None), stale_checks
        )
        self.assertNotIn(
            ("test", self.c7_test_id, "mock_boundary", None), stale_checks
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
        self.assertNotIn(("test", class_target, "assertion_strength", None), checks)
        self.assertNotIn(("test", self.c7_test_id, "mock_boundary", None), checks)
        self.assertEqual(
            "passed",
            checks[("module", "app", "test_repeat", "unit")][1]["result"],
        )

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
            "baseline": {
                "coverage_available": True,
                "coverage": {"p": ["TaskA"]},
            }
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
                    "baseline": {
                        "coverage_available": True,
                        "coverage": {"p": ["TaskA"]},
                    }
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
            "baseline": {
                "coverage_available": True,
                "coverage": {"source-a": ["TaskA"]},
            }
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
            "baseline": {
                "coverage_available": True,
                "coverage": {
                    "source-p1": ["TaskA"],
                    "source-p2": ["TaskB"],
                },
            }
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
            "baseline": {
                "coverage_available": True,
                "coverage": {"p": ["TaskA", "TaskB"]},
            }
        }

        issues, _ = gate.coverage_parity_issues(plan, checks, mapping)

        self.assertTrue(
            any("C7 coverage is ambiguous across source models" in issue for issue in issues),
            issues,
        )

        mapping["baseline"]["coverage"] = {"p": ["RemovedTask"]}
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
            "baseline": {
                "coverage_available": True,
                "coverage": {
                    "p": ["RemovedTask"],
                    "legacy-p": ["TaskA"],
                },
            }
        }

        issues, _ = gate.coverage_parity_issues(plan, checks, mapping)

        self.assertEqual([], issues)

    def test_migrate_only_refuses_test_commands_and_requires_exact_block_reason(self):
        self.write_scope(test_run_mode="migrate_only")
        test_keys = (
            ("module", "app", "tests", "unit"),
            ("process", "models/converted-c8-process.bpmn#p", "process_path", "normal"),
        )
        for key in test_keys:
            with self.subTest(key=key):
                with patch.object(gate.subprocess, "run") as command:
                    with self.assertRaisesRegex(gate.EvidenceError, "Migrate tests only"):
                        self.submit(key, command=[sys.executable, "-c", "print('must not run')"])
                    command.assert_not_called()
                with self.assertRaisesRegex(gate.EvidenceError, "exact Question 8 reason"):
                    self.submit(key, action="block", reason="tests deferred")
                self.assertEqual(
                    1,
                    self.submit(
                        key,
                        action="block",
                        reason="declined by user (Question 8)",
                    ),
                )

        self.assertEqual(1, self.audit())
        summary = self.summary()
        self.assertEqual("NOT READY", summary["gate"])
        for key in test_keys:
            check = next(
                check for check in summary["checks"]
                if (check["type"], check["target"], check["kind"], check["scenario"]) == key
            )
            self.assertEqual("blocked", check["result"])
            self.assertEqual("declined by user (Question 8)", check["reason"])

    def test_migrate_only_gate_rejects_previously_passed_test_checks(self):
        self.complete_required_checks()
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        inventory["test_run_mode"] = "migrate_only"
        write_json(self.root / gate.INVENTORY, inventory)

        self.assertEqual(1, self.audit())
        issues = self.summary()["issues"]
        self.assertTrue(
            any("must be blocked with reason" in issue for issue in issues),
            issues,
        )

    def test_unknown_test_run_mode_is_rejected(self):
        for mode in ("skip", None, [], {}):
            with self.subTest(mode=mode):
                inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
                inventory["test_run_mode"] = mode
                write_json(self.root / gate.INVENTORY, inventory)

                self.assertEqual(1, self.audit())
                self.assertTrue(
                    any(
                        "test_run_mode must be 'run' or 'migrate_only'" in issue
                        for issue in self.summary()["issues"]
                    )
                )
                self.write_scope()

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
