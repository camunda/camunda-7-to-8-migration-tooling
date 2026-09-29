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

    def submit(self, key, action="run", command=None, **options):
        category, target, kind, scenario = key
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
        )
        with redirect_stdout(StringIO()):
            return gate.record(self.root, arguments)

    def complete_required_checks(self):
        required, _, _, _, issues = gate.requirements(self.root, self.plan)
        self.assertEqual([], issues)
        priorities = {"project": 0, "module": 1, "timer": 2, "model": 3, "process": 4}
        for key in sorted(
            required,
            key=lambda item: (
                priorities[item[0]],
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
                self.submit(key, action="review" if required[key] == "review" else "run", **options),
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
        required, _, _, _, _ = gate.requirements(self.root, self.plan)
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
        required, _, _, _, issues = gate.requirements(self.root, self.plan)
        self.assertEqual([], issues)
        self.assertIn(
            ("process", "models/converted-c8-process.bpmn#p", "process_path", "normal"),
            required,
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
            {"lint", "review", "deployment"},
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
        with self.assertRaisesRegex(gate.EvidenceError, "Timer preflight"):
            self.submit(("model", model, "deployment", None), environment="local")
        with self.assertRaisesRegex(gate.EvidenceError, "isolation or cleanup"):
            self.submit(("timer", f"{model}#p#Start", "preflight", None), environment="local")
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        plan = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        plan["checks"] = list(reversed(plan["checks"]))
        write_json(self.root / gate.EVIDENCE, plan)
        self.assertEqual(1, self.audit())
        self.assertIn("timer preflight must pass before execution", "\n".join(self.summary()["issues"]))

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
