"""Category-scoped regressions for recorded migration evidence."""

import json
import subprocess
import sys
import tempfile
import unittest
from argparse import Namespace
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import patch


FIXTURE = Path(__file__).resolve().parent
SCRIPT_DIR = FIXTURE.parents[1] / "skills" / "migrate-c7-to-c8-code" / "scripts"
sys.path.insert(0, str(SCRIPT_DIR))
import validate_migration_evidence as gate  # noqa: E402


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


def message_rearm_bpmn(process_id):
    return (
        '<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" '
        'xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" '
        'id="Definitions" targetNamespace="http://camunda.io/schema/1.0/bpmn">'
        '<bpmn:message id="DateChangedMessage" name="DueDateChanged">'
        '<bpmn:extensionElements><zeebe:subscription correlationKey="=projectId" />'
        "</bpmn:extensionElements></bpmn:message>"
        f'<bpmn:process id="{process_id}" isExecutable="true">'
        '<bpmn:startEvent id="Start"><bpmn:outgoing>Start_Prepare</bpmn:outgoing></bpmn:startEvent>'
        '<bpmn:exclusiveGateway id="Prepare"><bpmn:incoming>Start_Prepare</bpmn:incoming>'
        '<bpmn:incoming>Update_Prepare</bpmn:incoming><bpmn:outgoing>Prepare_Wait</bpmn:outgoing>'
        '</bpmn:exclusiveGateway>'
        '<bpmn:eventBasedGateway id="Wait"><bpmn:incoming>Prepare_Wait</bpmn:incoming>'
        '<bpmn:outgoing>Wait_Timer</bpmn:outgoing>'
        '<bpmn:outgoing>Wait_Update</bpmn:outgoing></bpmn:eventBasedGateway>'
        '<bpmn:intermediateCatchEvent id="Timer">'
        '<bpmn:incoming>Wait_Timer</bpmn:incoming><bpmn:timerEventDefinition>'
        '<bpmn:timeDate>=date and time(dueDate)</bpmn:timeDate>'
        '</bpmn:timerEventDefinition></bpmn:intermediateCatchEvent>'
        '<bpmn:intermediateCatchEvent id="DateChanged">'
        '<bpmn:incoming>Wait_Update</bpmn:incoming><bpmn:outgoing>Update_Prepare</bpmn:outgoing>'
        '<bpmn:messageEventDefinition messageRef="DateChangedMessage" />'
        '</bpmn:intermediateCatchEvent>'
        '<bpmn:sequenceFlow id="Start_Prepare" sourceRef="Start" targetRef="Prepare" />'
        '<bpmn:sequenceFlow id="Prepare_Wait" sourceRef="Prepare" targetRef="Wait" />'
        '<bpmn:sequenceFlow id="Wait_Timer" sourceRef="Wait" targetRef="Timer" />'
        '<bpmn:sequenceFlow id="Wait_Update" sourceRef="Wait" targetRef="DateChanged" />'
        '<bpmn:sequenceFlow id="Update_Prepare" sourceRef="DateChanged" targetRef="Prepare" />'
        "</bpmn:process></bpmn:definitions>"
    )


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

    def write_scope(self, timer=False, extra=""):
        inventory = {
            "schema_version": 1,
            "modules": [module["path"] for module in self.plan["modules"]],
            "models": [model["source_path"] for model in self.plan["models"]],
        }
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

    def active_timer_observation(self, key):
        decision = gate.requirements(self.root, self.plan).active_timer_decisions[key[1]]
        original_deadline = "2050-11-23T00:00:05Z"
        first_updated_deadline = "2050-11-23T00:00:15Z"
        final_deadline = "2050-11-23T00:00:25Z"
        return {
            "deployment": {
                "performed": True,
                "reference": "camunda/camunda:8.9.21 container fixture-1",
                "environment": "local",
                "target_disposable": True,
                "target_version": decision["target_version"],
            },
            "observation": {
                "case1": {
                    "module_a_model": "module-a/src/main/resources/module-a/sample.bpmn",
                    "module_b_model": "module-b/src/main/resources/module-b/sample.bpmn",
                    "process_id": "Sample",
                    "module_a_timer_start_id": "RecurringStart",
                    "module_a_cycle": "R/PT5S",
                    "module_b_has_timer_start": False,
                    "timer_started_instances_before_replacement": 1,
                    "timer_started_instances_after_replacement": 1,
                    "new_instances_after_replacement": 0,
                    "latest_by_id_start_element": "Hold_B",
                },
                "active_timer": {
                    "model_path": decision["model_path"],
                    "process_id": decision["process_id"],
                    "timer_id": decision["timer_id"],
                    "strategy": decision["strategy"],
                    "message_name": decision["message_name"],
                    "correlation_key_variable": decision["correlation_key_variable"],
                    "date_variable": decision["date_variable"],
                    "timer_was_active_before_first_update": True,
                    "updates": [
                        {
                            "old_deadline": original_deadline,
                            "new_deadline": first_updated_deadline,
                            "timer_active_before_update": True,
                            "correlated": True,
                        },
                        {
                            "old_deadline": first_updated_deadline,
                            "new_deadline": final_deadline,
                            "timer_active_before_update": True,
                            "correlated": True,
                        },
                    ],
                    "obsolete_deadlines": [
                        {"deadline": original_deadline, "fire_count": 0},
                        {"deadline": first_updated_deadline, "fire_count": 0},
                    ],
                    "advanced_past_obsolete_deadlines": True,
                    "final_deadline": final_deadline,
                    "final_deadline_fire_count": 1,
                    "final_deadline_fired_at": "2050-11-23T00:00:26Z",
                },
            },
            "cleanup": {
                "completed": True,
                "evidence_reference": "Docker destroy event fixture-1",
            },
        }

    def install_active_timer_decision(self):
        self.write_scope()
        source = self.root / "app" / "Timer.java"
        source.write_text("managementService.setJobDuedate(jobId, terminationDate);\n", encoding="utf-8")
        model_path = "models/converted-c8-process.bpmn"
        for name in ("models/process.bpmn", model_path):
            path = self.root / name
            path.write_text(message_rearm_bpmn("p"), encoding="utf-8")
        locations = gate.requirements(self.root, self.plan).update_hits["app"]
        self.assertEqual(1, len(locations))
        self.plan["active_timer_update_decision"] = {
            "status": "approved",
            "strategy": "message_rearm",
            "reference": "MIGRATION_DECISIONS.md#active-timer-rearm",
            "target_version": "8.9.21",
            "updates": [
                {
                    "module": "app",
                    "locations": locations,
                    "model_path": model_path,
                    "process_id": "p",
                    "timer_id": "Timer",
                    "message_name": "DueDateChanged",
                    "correlation_key_variable": "projectId",
                    "date_variable": "dueDate",
                }
            ],
        }
        write_json(self.root / gate.EVIDENCE, self.plan)
        return ("timer", f"{model_path}#p#Timer", "active_instance_reschedule", None)

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
        if action == "review" and kind == "active_timer_updates" and "disposition" not in options:
            plan = gate.requirements(self.root, self.plan)
            disposition = "message_rearm" if plan.active_timer_locations.get(target) else "no_updates"
            if disposition == "message_rearm":
                options.setdefault(
                    "reference",
                    next(
                        decision["reference"]
                        for decision in plan.active_timer_decisions.values()
                        if target in decision["modules"]
                    ),
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
            command=command or [sys.executable, "-c", "print('check completed')"],
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
        priorities = {"project": 0, "module": 1, "deployment_set": 2,
                      "timer": 3, "model": 4, "process": 5}
        for key in sorted(
            plan.required,
            key=lambda item: (
                4 if item[0] == "timer" and item[2] == "active_instance_reschedule"
                else priorities[item[0]],
                0,
                item[1],
                {"lint": 0, "review": 1, "deployment": 2, "worker_input_inventory": 0}.get(item[2], 3),
                item[2],
                item[3] or "",
            ),
        ):
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
            self.assertEqual(
                0,
                self.submit(key, action="review" if plan.required[key] == "review" else "run", **options),
            )

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

    def test_active_timer_updates_need_approved_rearm_and_live_runtime_evidence(self):
        runtime_key = self.install_active_timer_decision()
        decision_reference = self.plan["active_timer_update_decision"]["reference"]
        module_key = ("module", "app", "active_timer_updates", None)
        self.assertEqual([], gate.requirements(self.root, self.plan).issues)

        decision = self.plan.pop("active_timer_update_decision")
        write_json(self.root / gate.EVIDENCE, self.plan)
        with self.assertRaisesRegex(gate.EvidenceError, "block active"):
            self.submit(module_key, action="review", disposition="message_rearm")
        self.plan["active_timer_update_decision"] = decision
        write_json(self.root / gate.EVIDENCE, self.plan)

        with self.assertRaisesRegex(gate.EvidenceError, "approved active-timer decision"):
            self.submit(
                module_key,
                action="review",
                disposition="message_rearm",
                reference="not applicable",
            )
        self.assertEqual(
            0,
            self.submit(
                module_key,
                action="review",
                disposition="message_rearm",
                reference=decision_reference,
                note="Approved BPMN message rearming and mapped the due-date call to this timer.",
            ),
        )
        model_path = self.plan["active_timer_update_decision"]["updates"][0]["model_path"]
        self.assertEqual(0, self.submit(("model", model_path, "lint", None)))
        self.assertEqual(0, self.submit(("model", model_path, "review", None), action="review"))
        self.assertEqual(
            0,
            self.submit(("deployment_set", "shared", "preflight", None), action="review"),
        )

        self.assertEqual(1, self.audit())
        self.assertIn(
            "Missing timer active_instance_reschedule",
            "\n".join(self.summary()["issues"]),
        )
        options = {
            "environment": "local",
            "isolation_plan": "Run the pinned 8.9.21 fixture and remove its disposable container.",
        }
        invalid = self.active_timer_observation(runtime_key)
        invalid["observation"]["active_timer"]["obsolete_deadlines"][0]["fire_count"] = 1
        self.assertEqual(1, self.submit(runtime_key, observation=invalid, **options))
        self.assertEqual(1, self.audit())
        self.assertTrue(
            any(
                check["kind"] == "active_instance_reschedule"
                and check["result"] == "failed"
                for check in self.summary()["checks"]
            )
        )

        invalid = self.active_timer_observation(runtime_key)
        invalid["observation"]["active_timer"]["updates"][1]["timer_active_before_update"] = False
        self.assertEqual(1, self.submit(runtime_key, observation=invalid, **options))

        self.assertEqual(0, self.submit(runtime_key, **options))
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        self.assertEqual("READY", self.summary()["gate"])

        invalid = self.active_timer_observation(runtime_key)
        invalid["observation"]["active_timer"]["final_deadline_fire_count"] = 2
        self.assertEqual(1, self.submit(runtime_key, observation=invalid, **options))

    def test_active_timer_command_rejects_unsafe_environment_and_unapproved_version_before_execution(self):
        runtime_key = self.install_active_timer_decision()
        decision_reference = self.plan["active_timer_update_decision"]["reference"]
        module_key = ("module", "app", "active_timer_updates", None)
        model_path = self.plan["active_timer_update_decision"]["updates"][0]["model_path"]
        self.assertEqual(
            0,
            self.submit(
                module_key,
                action="review",
                disposition="message_rearm",
                reference=decision_reference,
            ),
        )
        self.assertEqual(0, self.submit(("model", model_path, "lint", None)))
        self.assertEqual(0, self.submit(("model", model_path, "review", None), action="review"))
        self.assertEqual(
            0,
            self.submit(("deployment_set", "shared", "preflight", None), action="review"),
        )

        marker = self.root / "active-timer-command-ran"
        command = [
            sys.executable,
            "-c",
            f"from pathlib import Path; Path({str(marker)!r}).touch()",
        ]
        cases = (
            {"environment": "non-production", "target_version": "8.9.21"},
            {"environment": "local", "target_version": "8.9.22"},
        )
        for options in cases:
            with self.subTest(**options), self.assertRaisesRegex(
                gate.EvidenceError, "approved version on an isolated disposable local target"
            ):
                self.submit(
                    runtime_key,
                    command=command,
                    isolation_plan="Remove the disposable target after the check.",
                    **options,
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


if __name__ == "__main__":
    unittest.main()
