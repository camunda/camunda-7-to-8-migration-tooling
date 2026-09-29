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
                    "module": "app",
                    "deployment_set": "shared",
                    "processes": [{"id": "p", "standalone": True, "scenarios": ["normal"]}],
                }
            ],
            "deployment_sets": [
                {
                    "name": "shared",
                    "modules": ["app"],
                    "models": ["models/converted-c8-process.bpmn"],
                }
            ],
            "checks": [],
        }
        self.write_scope()

    def write_scope(self, timer=False, extra="", timer_model=None):
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
            has_timer = timer if timer_model is None else model["path"] == timer_model
            xml = bpmn(model["processes"][0]["id"], timer=has_timer, extra=extra)
            for name in (model["source_path"], model["path"]):
                path = self.root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(xml, encoding="utf-8")
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root))

    def add_active_timer_model(self):
        timer_events = (
            '<bpmn:intermediateCatchEvent id="sample-timer">'
            '<bpmn:timerEventDefinition><bpmn:timeDuration>PT1S</bpmn:timeDuration>'
            "</bpmn:timerEventDefinition></bpmn:intermediateCatchEvent>"
            '<bpmn:intermediateCatchEvent id="second-sample-timer">'
            '<bpmn:timerEventDefinition><bpmn:timeDuration>PT2S</bpmn:timeDuration>'
            "</bpmn:timerEventDefinition></bpmn:intermediateCatchEvent>"
        )
        for model in self.plan["models"]:
            for name in (model["source_path"], model["path"]):
                path = self.root / name
                xml = path.read_text(encoding="utf-8")
                self.assertIn("</bpmn:process>", xml)
                path.write_text(
                    xml.replace("</bpmn:process>", f"{timer_events}</bpmn:process>", 1),
                    encoding="utf-8",
                )

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
            disposition=options.get("disposition"),
            caller_inventory_json=options.get("caller_inventory_json"),
            rename_mappings_json=options.get("rename_mappings_json"),
            approval_reference=options.get("approval_reference"),
            alternative_evidence_reference=options.get("alternative_evidence_reference"),
            target_version=options.get("target_version"),
            target_disposable=options.get("target_disposable", False),
            cleanup_plan=options.get("cleanup_plan", options.get("isolation_plan")),
            timer_observation_json=options.get("timer_observation_json"),
            affected_timers_json=options.get("affected_timers_json"),
            non_timer_update_evidence_json=options.get(
                "non_timer_update_evidence_json"
            ),
            active_timer_update_observation_json=options.get(
                "active_timer_update_observation_json"
            ),
        )
        with redirect_stdout(StringIO()):
            return gate.record(self.root, arguments)

    def write_duplicate_sample_scope(self):
        modules = ["modules/c7-client", "modules/c8-client"]
        paths = [
            ("models/one.bpmn", "models/converted-one.bpmn", modules[0]),
            ("models/two.bpmn", "models/converted-two.bpmn", modules[1]),
        ]
        self.plan["modules"] = [
            {
                "path": module,
                "runtime_mode": "none",
                "test_suites": [{"name": "unit", "requires_docker": False}],
            }
            for module in modules
        ]
        self.plan["models"] = [
            {
                "source_path": source,
                "path": converted,
                "module": module,
                "deployment_set": "shared",
                "processes": [{"id": "Sample", "standalone": True, "scenarios": ["normal"]}],
            }
            for source, converted, module in paths
        ]
        self.plan["deployment_sets"] = [
            {
                "name": "shared",
                "modules": modules,
                "models": [converted for _, converted, _ in paths],
            }
        ]
        self.write_scope()

    def timer_observation(self, key):
        inventory = gate.requirements(self.root, self.plan).timer_inventory[key]
        return {
            "deployment": {
                "performed": True,
                "reference": "fixture-deployment-001",
                "environment": "local",
                "target_disposable": True,
                "target_version": "8.9.21",
            },
            "observation": {
                "process_id": inventory["process_id"],
                "start_id": inventory["start_id"],
                "cycle": inventory["converted_cycle"],
                "instances_started": 1,
            },
            "cleanup": {
                "completed": True,
                "evidence_reference": "fixture-cleanup-record-001",
            },
        }

    def affected_timer_inventory(self, target="app"):
        hits = gate.requirements(self.root, self.plan).active_timer_updates[target]
        locations = sorted({hit["location"] for hit in hits})
        return [
            {
                "model_path": "models/converted-c8-process.bpmn",
                "process_id": "p",
                "timer_id": timer_id,
                "source_locations": locations,
            }
            for timer_id in ("sample-timer", "second-sample-timer")
        ]

    def active_timer_observation(self, target="app"):
        affected_timers = self.affected_timer_inventory(target)
        return {
            "deployment": {
                "performed": True,
                "reference": "fixture-active-timer-deployment",
                "environment": "local",
                "target_disposable": True,
                "target_version": "8.9.21",
            },
            "observation": {
                "evidence_reference": "fixture-active-timer-observation",
                "timers": [
                    {
                        **timer,
                        "process_instance_id": f"fixture-instance-{index}",
                        "active_before_updates": True,
                        "updates_applied": 2,
                        "obsolete_deadlines_fired": 0,
                        "final_deadline_fired": 1,
                        "requested_final_deadline": "2026-09-29T12:00:00Z",
                        "final_deadline_fired_at": "2026-09-29T12:00:01Z",
                    }
                    for index, timer in enumerate(affected_timers)
                ],
            },
            "cleanup": {
                "completed": True,
                "evidence_reference": "fixture-active-timer-cleanup",
            },
        }

    def test_duplicate_process_id_does_not_pass_with_missing_or_empty_caller_inventory(self):
        self.write_duplicate_sample_scope()
        with self.assertRaisesRegex(gate.EvidenceError, "caller-inventory-json"):
            self.submit(
                ("deployment_set", "shared", "preflight", None),
                action="review",
                caller_inventory_json=None,
            )

        self.complete_required_checks(skip_keys=self.deployment_execution_keys())
        self.assertEqual(1, self.audit())
        self.assertIn(
            "non-empty caller inventory",
            "\n".join(self.summary()["issues"]),
        )

    def test_deployment_inventory_checks_all_bpmn_process_ids(self):
        self.write_duplicate_sample_scope()
        self.plan["models"][1]["processes"] = []
        for path in (
            "models/two.bpmn",
            "models/converted-two.bpmn",
        ):
            model = self.root / path
            model.write_text(
                model.read_text(encoding="utf-8").replace(
                    'id="Sample" isExecutable="true"',
                    'id="Sample" isExecutable="false"',
                ),
                encoding="utf-8",
            )

        plan = gate.requirements(self.root, self.plan)
        self.assertEqual(
            ["models/converted-one.bpmn", "models/converted-two.bpmn"],
            plan.duplicate_process_ids[("shared", "Sample")],
        )
        self.assertIn(
            ("deployment_set", "shared", "duplicate_process_id", "Sample"),
            plan.required,
        )

    def test_renamed_models_still_require_old_id_callers_to_be_updated(self):
        self.write_duplicate_sample_scope()
        mappings = []
        for model, new_id in zip(self.plan["models"], ("One", "Two")):
            converted = self.root / model["path"]
            converted.write_text(
                converted.read_text(encoding="utf-8").replace('"Sample"', f'"{new_id}"'),
                encoding="utf-8",
            )
            model["processes"][0]["id"] = new_id
            mappings.append({
                "model": model["path"],
                "from_process_id": "Sample",
                "to_process_id": new_id,
            })
        write_json(self.root / gate.EVIDENCE, self.plan)
        caller = self.root / "modules/c7-client/src/main/java/ProcessCaller.java"
        caller.parent.mkdir(parents=True, exist_ok=True)
        caller.write_text(
            'runtimeService.startProcessInstanceByKey("Sample");\n',
            encoding="utf-8",
        )
        plan = gate.requirements(self.root, self.plan)
        self.assertIn(("shared", "Sample"), plan.duplicate_process_ids)
        self.assertIn(
            plan.process_callers["modules/c7-client"][0],
            gate.deployment_set_callers(plan, "shared", "modules/c7-client"),
        )
        empty_inventory_issues = []
        gate.validate_caller_inventory(
            self.root,
            plan,
            "shared",
            {"caller_inventory": []},
            {},
            empty_inventory_issues,
        )
        self.assertTrue(
            any("detected process caller is missing from the inventory" in issue
                for issue in empty_inventory_issues),
            empty_inventory_issues,
        )
        blocked = self.deployment_execution_keys() | {
            ("deployment_set", "shared", "duplicate_process_id", "Sample")
        }
        self.complete_required_checks(
            caller_inventory=plan.process_callers["modules/c7-client"],
            skip_keys=blocked,
        )
        self.submit(
            ("deployment_set", "shared", "duplicate_process_id", "Sample"),
            action="review",
            disposition="mapped_rename",
            rename_mappings_json=json.dumps(mappings),
        )
        marker = self.root / "deployment-ran"
        with self.assertRaisesRegex(gate.EvidenceError, "callers still reference renamed"):
            self.submit(
                ("model", "models/converted-one.bpmn", "deployment", None),
                environment="local",
                command=[sys.executable, "-c", "from pathlib import Path; Path('deployment-ran').touch()"],
            )
        self.assertFalse(marker.exists())

        caller.write_text(
            'runtimeService.startProcessInstanceByKey("One");\n',
            encoding="utf-8",
        )
        self.plan["checks"] = []
        write_json(self.root / gate.EVIDENCE, self.plan)
        with redirect_stdout(StringIO()):
            self.assertEqual(0, gate.initialize(self.root))
        plan = gate.requirements(self.root, self.plan)
        self.complete_required_checks(
            caller_inventory=plan.process_callers["modules/c7-client"],
            skip_keys=blocked,
        )
        self.submit(
            ("deployment_set", "shared", "duplicate_process_id", "Sample"),
            action="review",
            disposition="mapped_rename",
            rename_mappings_json=json.dumps(mappings),
        )
        self.assertEqual(
            0,
            self.submit(
                ("model", "models/converted-one.bpmn", "deployment", None),
                environment="local",
                command=[sys.executable, "-c", "from pathlib import Path; Path('deployment-ran').touch()"],
            ),
        )
        self.assertTrue(marker.exists())

    def test_mapped_rename_can_coexist_with_explicit_version_collision(self):
        self.write_duplicate_sample_scope()
        mappings = []
        for model, new_id in zip(self.plan["models"], ("One", "Two")):
            converted = self.root / model["path"]
            converted.write_text(
                converted.read_text(encoding="utf-8").replace('"Sample"', f'"{new_id}"'),
                encoding="utf-8",
            )
            model["processes"][0]["id"] = new_id
            mappings.append({
                "model": model["path"],
                "from_process_id": "Sample",
                "to_process_id": new_id,
            })
        for index, module in enumerate(self.plan["modules"]):
            model = {
                "source_path": f"models/other-{index}.bpmn",
                "path": f"models/converted-other-{index}.bpmn",
                "module": module["path"],
                "deployment_set": "shared",
                "processes": [{"id": "Other", "standalone": True, "scenarios": ["normal"]}],
            }
            self.plan["models"].append(model)
            self.plan["deployment_sets"][0]["models"].append(model["path"])
            for path in (model["source_path"], model["path"]):
                (self.root / path).write_text(bpmn("Other"), encoding="utf-8")
        inventory = json.loads((self.root / gate.INVENTORY).read_text(encoding="utf-8"))
        inventory["models"] = [model["source_path"] for model in self.plan["models"]]
        write_json(self.root / gate.INVENTORY, inventory)
        write_json(self.root / gate.EVIDENCE, self.plan)
        caller = self.root / "modules/c7-client/src/main/java/ProcessCaller.java"
        caller.parent.mkdir(parents=True, exist_ok=True)
        caller.write_text(
            'runtimeService.startProcessInstanceByKey("One");\n'
            'client.bpmnProcessId("Other").version(1).execute();\n',
            encoding="utf-8",
        )
        plan = gate.requirements(self.root, self.plan)
        blocked = self.deployment_execution_keys() | {
            ("deployment_set", "shared", "duplicate_process_id", process_id)
            for process_id in ("Sample", "Other")
        }
        self.complete_required_checks(
            caller_inventory=plan.process_callers["modules/c7-client"],
            skip_keys=blocked,
        )
        self.submit(
            ("deployment_set", "shared", "duplicate_process_id", "Sample"),
            action="review",
            disposition="mapped_rename",
            rename_mappings_json=json.dumps(mappings),
        )
        self.submit(
            ("deployment_set", "shared", "duplicate_process_id", "Other"),
            action="review",
            disposition="explicit_version",
        )
        self.assertEqual(
            0,
            self.submit(
                ("model", "models/converted-one.bpmn", "deployment", None),
                environment="local",
            ),
        )

    def test_c7_by_key_caller_without_latest_version_must_be_in_inventory(self):
        self.write_duplicate_sample_scope()
        caller = self.root / "modules/c7-client/src/main/java/ProcessCaller.java"
        caller.parent.mkdir(parents=True, exist_ok=True)
        caller.write_text(
            'runtimeService.startProcessInstanceByKey("Sample");\n',
            encoding="utf-8",
        )

        self.complete_required_checks(skip_keys=self.deployment_execution_keys())
        self.assertEqual(1, self.audit())
        issues = "\n".join(self.summary()["issues"])
        self.assertIn("detected process caller is missing", issues)
        self.assertIn("startProcessInstanceByKey", issues)
        self.assertIn("ProcessCaller.java", issues)

    def test_multiline_c7_by_key_caller_cannot_be_missed_by_explicit_version_inventory(self):
        self.write_duplicate_sample_scope()
        caller = self.root / "modules/c7-client/src/main/java/ProcessCaller.java"
        caller.parent.mkdir(parents=True, exist_ok=True)
        caller.write_text(
            'client.bpmnProcessId("Sample").version(1).execute();\n'
            "runtimeService.startProcessInstanceByKey\n"
            '    ("Sample");\n',
            encoding="utf-8",
        )

        self.complete_required_checks(
            caller_inventory=[
                {
                    "module": "modules/c7-client",
                    "location": "modules/c7-client/src/main/java/ProcessCaller.java:1",
                    "process_id": "Sample",
                    "operation": "bpmnProcessId",
                    "version_selection": "explicit_version",
                }
            ],
            skip_keys=self.deployment_execution_keys(),
        )
        self.assertEqual(1, self.audit())
        issues = "\n".join(self.summary()["issues"])
        self.assertIn("detected process caller is missing", issues)
        self.assertIn("startProcessInstanceByKey", issues)

    def test_caller_scan_ignores_comments_and_strings_and_bounds_version_selection(self):
        self.write_duplicate_sample_scope()
        caller = self.root / "modules/c7-client/src/main/java/ProcessCaller.java"
        caller.parent.mkdir(parents=True, exist_ok=True)
        caller.write_text(
            '// client.bpmnProcessId("Comment").latestVersion();\n'
            'String sample = "client.bpmnProcessId(\'String\').latestVersion()";\n'
            'client.bpmnProcessId("Sample")\n'
            'client.bpmnProcessId("Other").version(1);\n'
            'client.bpmnProcessId("Latest").latestVersion();\n'
            'runtimeService.createProcessInstanceByKey(\n'
            '    "Nested", nested(foo(1, 2))\n'
            ');\n'
            'RuntimeService::startProcessInstanceByKey;\n',
            encoding="utf-8",
        )
        nested_comment = self.root / "modules/c7-client/src/main/kotlin/CommentedCaller.kt"
        nested_comment.parent.mkdir(parents=True, exist_ok=True)
        nested_comment.write_text(
            '/* outer comment\n'
            '   /* nested comment */\n'
            '   client.bpmnProcessId("Sample").version(2);\n'
            '*/\n',
            encoding="utf-8",
        )

        _, latest_versions, callers, _, issues = gate.scan_module_sources(
            self.root,
            "modules/c7-client",
        )
        self.assertEqual([], issues)
        self.assertEqual(1, len(latest_versions))
        self.assertEqual(
            ["Latest", "Nested", "Other", "Sample", "unknown"],
            sorted(hit["process_id"] for hit in callers),
        )
        sample_call = next(hit for hit in callers if hit["process_id"] == "Sample")
        self.assertEqual("unknown", sample_call["version_selection"])
        nested_call = next(hit for hit in callers if hit["process_id"] == "Nested")
        self.assertEqual("latest_version", nested_call["version_selection"])
        latest_call = next(hit for hit in callers if hit["process_id"] == "Latest")
        self.assertEqual("latest_version", latest_call["version_selection"])

    def test_interpolated_process_calls_fail_closed(self):
        self.write_duplicate_sample_scope()
        caller = self.root / "modules/c7-client/src/main/python/Caller.py"
        caller.parent.mkdir(parents=True, exist_ok=True)
        caller.write_text(
            'message = f"{runtimeService.startProcessInstanceByKey(\'Sample\')}"\n',
            encoding="utf-8",
        )

        _, _, _, _, issues = gate.scan_module_sources(self.root, "modules/c7-client")
        self.assertTrue(any("interpolated string" in issue for issue in issues))

    def test_scala_interpolated_timer_calls_fail_closed_for_builtin_and_custom_prefixes(self):
        self.write_duplicate_sample_scope()
        caller = self.root / "modules/c7-client/src/main/scala/Caller.scala"
        caller.parent.mkdir(parents=True, exist_ok=True)
        for prefix in ("raw", "sql"):
            with self.subTest(prefix=prefix):
                caller.write_text(
                    f'val dueDate = {prefix}"${{managementService.setJobDuedate(timerId, date)}}"\n',
                    encoding="utf-8",
                )
                _, _, _, _, issues = gate.scan_module_sources(
                    self.root,
                    "modules/c7-client",
                )
                self.assertTrue(any("interpolated string" in issue for issue in issues))

    def test_caller_scan_parses_static_template_literals_and_keeps_interpolation_unknown(self):
        self.write_duplicate_sample_scope()
        caller = self.root / "modules/c7-client/src/main/typescript/Caller.ts"
        caller.parent.mkdir(parents=True, exist_ok=True)
        caller.write_text(
            "client.bpmnProcessId(`Sample`).latestVersion();\n"
            "client.bpmnProcessId(`Sample-${tenant}`).latestVersion();\n",
            encoding="utf-8",
        )

        _, latest_versions, callers, _, issues = gate.scan_module_sources(
            self.root,
            "modules/c7-client",
        )
        self.assertEqual([], issues)
        self.assertEqual(2, len(latest_versions))
        self.assertEqual(["Sample", "unknown"], sorted(hit["process_id"] for hit in callers))
        static_call = next(hit for hit in callers if hit["process_id"] == "Sample")
        self.assertEqual("latest_version", static_call["version_selection"])
        interpolated_call = next(hit for hit in callers if hit["process_id"] == "unknown")
        self.assertEqual("latest_version", interpolated_call["version_selection"])

    def test_caller_inventory_distinguishes_same_line_version_selections(self):
        self.write_duplicate_sample_scope()
        caller = self.root / "modules/c7-client/src/main/typescript/Caller.ts"
        caller.parent.mkdir(parents=True, exist_ok=True)
        caller.write_text(
            'client.bpmnProcessId("Sample").latestVersion(); '
            'client.bpmnProcessId("Sample").version(1);\n',
            encoding="utf-8",
        )

        plan = gate.requirements(self.root, self.plan)
        hits = plan.process_callers["modules/c7-client"]
        self.assertEqual(2, len(hits))
        self.assertEqual(
            ["explicit_version", "latest_version"],
            sorted(hit["version_selection"] for hit in hits),
        )
        issues = []
        gate.validate_caller_inventory(
            self.root,
            plan,
            "shared",
            {"caller_inventory": hits},
            {},
            issues,
        )
        self.assertEqual([], issues)

    def test_constant_backed_process_callers_can_be_resolved_but_dynamic_callers_cannot(self):
        self.write_duplicate_sample_scope()
        caller = self.root / "modules/c7-client/src/main/java/ProcessCaller.java"
        caller.parent.mkdir(parents=True, exist_ok=True)
        caller.write_text(
            "client.bpmnProcessId(PROCESS_ID).version(PROCESS_VERSION).execute();\n",
            encoding="utf-8",
        )

        plan = gate.requirements(self.root, self.plan)
        detected = plan.process_callers["modules/c7-client"][0]
        self.assertEqual("unknown", detected["process_id"])
        self.assertEqual("reviewable", detected["process_id_resolution"])
        self.assertEqual("unknown", detected["version_selection"])
        self.assertEqual("reviewable", detected["version_selection_resolution"])
        inventory = [{
            "module": "modules/c7-client",
            "location": "modules/c7-client/src/main/java/ProcessCaller.java:1",
            "process_id": "Sample",
            "operation": "bpmnProcessId",
            "version_selection": "explicit_version",
        }]
        issues = []
        gate.validate_caller_inventory(
            self.root,
            plan,
            "shared",
            {"caller_inventory": inventory},
            {},
            issues,
        )
        self.assertEqual([], issues)

        caller.write_text(
            "client.bpmnProcessId(resolveProcessId()).version(currentVersion()).execute();\n",
            encoding="utf-8",
        )
        plan = gate.requirements(self.root, self.plan)
        detected = plan.process_callers["modules/c7-client"][0]
        self.assertEqual("dynamic", detected["process_id_resolution"])
        self.assertEqual("dynamic", detected["version_selection_resolution"])
        issues = []
        gate.validate_caller_inventory(
            self.root,
            plan,
            "shared",
            {"caller_inventory": inventory},
            {},
            issues,
        )
        self.assertTrue(any("unresolved process caller" in issue for issue in issues))

    def test_deployment_set_caller_inventory_excludes_calls_to_other_sets(self):
        self.write_duplicate_sample_scope()
        self.plan["models"][1]["processes"] = [
            {"id": "Other", "standalone": True, "scenarios": ["normal"]}
        ]
        self.plan["models"][0]["deployment_set"] = "first"
        self.plan["models"][1]["deployment_set"] = "second"
        self.plan["deployment_sets"] = [
            {
                "name": "first",
                "modules": ["modules/c7-client"],
                "models": ["models/converted-one.bpmn"],
            },
            {
                "name": "second",
                "modules": ["modules/c7-client", "modules/c8-client"],
                "models": ["models/converted-two.bpmn"],
            },
        ]
        self.write_scope()
        caller = self.root / "modules/c7-client/src/main/java/Callers.java"
        caller.parent.mkdir(parents=True, exist_ok=True)
        caller.write_text(
            'client.bpmnProcessId("Sample").latestVersion();\n'
            'client.bpmnProcessId("Other").latestVersion();\n',
            encoding="utf-8",
        )

        plan = gate.requirements(self.root, self.plan)
        for set_name, process_id in (("first", "Sample"), ("second", "Other")):
            with self.subTest(deployment_set=set_name):
                inventory = [
                    hit
                    for hit in plan.process_callers["modules/c7-client"]
                    if hit["process_id"] == process_id
                ]
                issues = []
                gate.validate_caller_inventory(
                    self.root,
                    plan,
                    set_name,
                    {"caller_inventory": inventory},
                    {},
                    issues,
                )
                self.assertEqual([], issues)
                snapshot = gate.deployment_set_snapshot(plan, set_name)
                self.assertEqual(
                    [process_id],
                    [hit["process_id"] for hit in snapshot["process_callers"]],
                )

    def test_active_timer_scan_covers_method_references_and_rest_paths_only(self):
        source = self.root / "app/src/main/java/TimerUpdates.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "// managementService.setJobDuedate(commentId, date);\n"
            'String example = "managementService.setJobDuedate(stringId, date)";\n'
            "ManagementService::setJobDuedate;\n",
            encoding="utf-8",
        )
        config = self.root / "app/src/main/resources/application.yml"
        config.parent.mkdir(parents=True, exist_ok=True)
        config.write_text(
            'timer-url: "/job/{jobKey}/duedate"\n'
            "# /job/commentId/duedate\n",
            encoding="utf-8",
        )

        updates, _, _, _, issues = gate.scan_module_sources(self.root, "app")
        self.assertEqual([], issues)
        self.assertEqual(
            [
                {
                    "location": "app/src/main/java/TimerUpdates.java:3",
                    "kind": "setJobDuedate",
                },
                {
                    "location": "app/src/main/resources/application.yml:1",
                    "kind": "REST due-date endpoint",
                },
            ],
            sorted(updates, key=lambda hit: hit["location"]),
        )

    def test_active_timer_scan_detects_concatenated_template_and_uri_builder_paths(self):
        source = self.root / "app/src/main/typescript/TimerUpdates.ts"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            'client.put("/job/" + jobId + "/duedate", body);\n'
            "client.put(`/job/${jobId}/duedate/recalculate`, body);\n"
            'uriBuilder.pathSegment("job").pathSegment(jobId).pathSegment("duedate");\n',
            encoding="utf-8",
        )

        updates, _, _, _, issues = gate.scan_module_sources(self.root, "app")
        self.assertEqual([], issues)
        self.assertEqual(
            [
                "app/src/main/typescript/TimerUpdates.ts:1",
                "app/src/main/typescript/TimerUpdates.ts:2",
                "app/src/main/typescript/TimerUpdates.ts:3",
            ],
            sorted(hit["location"] for hit in updates),
        )

    def test_active_timer_scan_detects_jax_rs_web_target_paths(self):
        source = self.root / "app/src/main/java/TimerUpdates.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            'target.path("job").path(jobId).path("duedate").request().put(body);\n'
            'target.path("job").path(jobId).path("duedate/recalculate").request().put(body);\n',
            encoding="utf-8",
        )
        updates, _, _, _, issues = gate.scan_module_sources(self.root, "app")
        self.assertEqual([], issues)
        self.assertEqual(
            [
                "app/src/main/java/TimerUpdates.java:1",
                "app/src/main/java/TimerUpdates.java:2",
            ],
            sorted(hit["location"] for hit in updates),
        )

    def test_configuration_apostrophes_do_not_break_module_scanning(self):
        config = self.root / "app/src/main/resources/application.yml"
        config.parent.mkdir(parents=True, exist_ok=True)
        config.write_text("message=Don't retry\n", encoding="utf-8")

        plan = gate.requirements(self.root, self.plan)
        self.assertEqual([], plan.issues)

    def test_deployment_set_preflight_covers_cross_module_duplicate_process_ids(self):
        modules = ["examples/loan", "clients/java/order-handling"]
        paths = [
            ("models/loan.bpmn", "models/converted-c8-loan.bpmn", modules[0]),
            ("models/order.bpmn", "models/converted-c8-order.bpmn", modules[1]),
        ]
        self.plan["modules"] = [
            {
                "path": module,
                "runtime_mode": "none",
                "test_suites": [{"name": "unit", "requires_docker": False}],
            }
            for module in modules
        ]
        self.plan["models"] = [
            {
                "source_path": source,
                "path": converted,
                "module": module,
                "deployment_set": "shared-showcase",
                "processes": [{"id": "Sample", "standalone": True, "scenarios": ["normal"]}],
            }
            for source, converted, module in paths
        ]
        self.plan["deployment_sets"] = [
            {
                "name": "shared-showcase",
                "modules": modules,
                "models": [converted for _, converted, _ in paths],
            }
        ]
        self.write_scope(timer_model="models/converted-c8-loan.bpmn")

        caller = self.root / modules[0] / "src/main/java/Showcase.java"
        caller.parent.mkdir(parents=True, exist_ok=True)
        caller.write_text(
            'runtimeService.createProcessInstanceByKey("Sample").latestVersion().execute();\n',
            encoding="utf-8",
        )

        required = gate.requirements(self.root, self.plan).required
        self.assertIn(
            ("deployment_set", "shared-showcase", "preflight", None),
            required,
        )
        self.assertIn(
            ("deployment_set", "shared-showcase", "duplicate_process_id", "Sample"),
            required,
        )
        self.assertIn(
            ("timer", "models/converted-c8-loan.bpmn#Sample#Start", "disposition", None),
            required,
        )
        self.assertIn(
            ("timer", "models/converted-c8-loan.bpmn#Sample#Start", "preflight", None),
            required,
        )

        self.assertEqual(1, self.audit())
        issues = "\n".join(self.summary()["issues"])
        self.assertIn("duplicate process ID", issues)
        self.assertIn("deployment_set preflight", issues)
        self.assertIn("timer disposition", issues)
        self.complete_required_checks(skip_keys=self.deployment_execution_keys())
        self.assertEqual(1, self.audit())
        self.assertIn("latestVersion caller is missing", "\n".join(self.summary()["issues"]))

    def test_invalid_deployment_set_evidence_blocks_commands_before_execution(self):
        self.write_duplicate_sample_scope()
        caller = self.root / "modules/c7-client/src/main/java/ProcessCaller.java"
        caller.parent.mkdir(parents=True, exist_ok=True)
        caller.write_text(
            'runtimeService.startProcessInstanceByKey("Sample");\n',
            encoding="utf-8",
        )
        required = gate.requirements(self.root, self.plan).required
        dependent = {
            key for key in required if key[2] in ("deployment", "process_path")
        }
        self.complete_required_checks(skip_keys=dependent)

        marker = self.root / "deployment-ran"
        command = [
            sys.executable,
            "-c",
            "from pathlib import Path; Path('deployment-ran').touch()",
        ]
        deployment = ("model", "models/converted-one.bpmn", "deployment", None)
        with self.assertRaisesRegex(gate.EvidenceError, "caller"):
            self.submit(deployment, environment="local", command=command)
        self.assertFalse(marker.exists())

        callers = gate.requirements(self.root, self.plan).process_callers["modules/c7-client"]
        self.submit(
            ("deployment_set", "shared", "preflight", None),
            action="review",
            caller_inventory_json=json.dumps(callers),
            note="Recorded the detected caller, which still selects the latest version.",
        )
        with self.assertRaisesRegex(gate.EvidenceError, "latestVersion"):
            self.submit(deployment, environment="local", command=command)
        self.assertFalse(marker.exists())

    def test_active_timer_updates_and_repeated_calls_cannot_report_ready(self):
        source = self.root / "app/src/main/java/TimerUpdates.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "managementService.setJobDuedate(timerId, firstDate);\n"
            "managementService.setJobDuedate(timerId, secondDate);\n",
            encoding="utf-8",
        )

        self.complete_required_checks()
        self.assertEqual(1, self.audit())
        issues = "\n".join(self.summary()["issues"])
        self.assertIn("setJobDuedate", issues)
        self.assertIn("repeated", issues.lower())
        blocked = next(
            check for check in self.summary()["checks"] if check["kind"] == "active_timer_updates"
        )
        self.assertEqual("blocked", blocked["result"])
        self.assertIn("Manual blocker", blocked["reason"])

    def test_verified_active_timer_alternative_still_needs_runtime_evidence(self):
        self.add_active_timer_model()
        source = self.root / "app/src/main/java/TimerUpdates.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "managementService.setJobDuedate(timerId, firstDate);\n"
            "managementService.setJobDuedate(timerId, secondDate);\n",
            encoding="utf-8",
        )
        self.complete_required_checks()
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        evidence["active_timer_update_decision"] = {
            "status": "approved",
            "approval_reference": "synthetic-approval-reference",
            "alternative_evidence_reference": "synthetic-support-evidence-reference",
            "target_version": "8.9.21",
        }
        write_json(self.root / gate.EVIDENCE, evidence)
        key = ("module", "app", "active_timer_updates", None)
        self.assertEqual(
            0,
            self.submit(
                key,
                action="review",
                disposition="verified",
                affected_timers_json=json.dumps(self.affected_timer_inventory()),
                note="A synthetic approved decision is recorded for this gate test.",
            ),
        )
        self.assertEqual(1, self.audit())
        self.assertIn(
            "Missing module active_timer_update_runtime",
            "\n".join(self.summary()["issues"]),
        )

    def test_pending_or_missing_active_timer_decision_cannot_be_overridden_by_a_passing_command(self):
        source = self.root / "app/src/main/java/TimerUpdates.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "managementService.setJobDuedate(timerId, newDate);\n",
            encoding="utf-8",
        )
        self.complete_required_checks()
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        evidence["active_timer_update_decision"] = {
            "status": "pending",
            "approval_reference": "pending-review",
            "alternative_evidence_reference": "pending-review",
        }
        write_json(self.root / gate.EVIDENCE, evidence)

        key = ("module", "app", "active_timer_updates", None)
        with self.assertRaisesRegex(gate.EvidenceError, "approved project decision"):
            self.submit(
                key,
                action="review",
                disposition="verified",
                approval_reference="pending-review",
                alternative_evidence_reference="pending-review",
                note="The project decision remains pending.",
            )
        with self.assertRaisesRegex(gate.EvidenceError, "Review and approve"):
            self.submit(
                ("module", "app", "active_timer_update_runtime", None),
                environment="local",
                target_version="8.9.21",
                target_disposable=True,
                cleanup_plan="Remove the test deployment and instances.",
                command=[sys.executable, "-c", "print('success')"],
            )
        self.assertEqual(1, self.audit())
        issues = "\n".join(self.summary()["issues"])
        self.assertIn("Manual blocker", issues)
        self.assertIn("remains blocked", issues)

    def test_active_timer_updates_without_project_decision_reject_a_passing_runtime_command(self):
        source = self.root / "app/src/main/java/TimerUpdates.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "managementService.setJobDuedate(timerId, newDate);\n",
            encoding="utf-8",
        )
        self.complete_required_checks()
        with self.assertRaisesRegex(gate.EvidenceError, "approved project decision"):
            self.submit(
                ("module", "app", "active_timer_updates", None),
                action="review",
                disposition="verified",
                approval_reference="pending-review",
                note="No project approval exists.",
            )
        with self.assertRaisesRegex(gate.EvidenceError, "Review and approve"):
            self.submit(
                ("module", "app", "active_timer_update_runtime", None),
                environment="local",
                target_version="8.9.21",
                target_disposable=True,
                cleanup_plan="Remove the test deployment and instances.",
                command=[sys.executable, "-c", "print('success')"],
            )
        self.assertEqual(1, self.audit())

    def test_active_timer_review_cannot_pass_without_decision_support_evidence(self):
        source = self.root / "app/src/main/java/TimerUpdates.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "managementService.setJobDuedate(timerId, newDate);\n",
            encoding="utf-8",
        )
        self.complete_required_checks()
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        evidence["active_timer_update_decision"] = {
            "status": "approved",
            "approval_reference": "approved-decision-record",
            "alternative_evidence_reference": "pending-review",
            "target_version": "8.9.21",
        }
        write_json(self.root / gate.EVIDENCE, evidence)

        with self.assertRaisesRegex(gate.EvidenceError, "approved project decision"):
            self.submit(
                ("module", "app", "active_timer_updates", None),
                action="review",
                disposition="verified",
                note="The alternative evidence is not approved or verified.",
            )
        self.assertEqual(1, self.audit())
        self.assertIn("remains blocked", "\n".join(self.summary()["issues"]))

    def test_active_timer_decision_rejects_punctuation_only_references(self):
        decision = {
            "status": "approved",
            "approval_reference": "fixture-approval",
            "alternative_evidence_reference": "fixture-support",
            "target_version": "8.9.21",
        }
        for field in ("approval_reference", "alternative_evidence_reference"):
            with self.subTest(field=field):
                invalid = dict(decision)
                invalid[field] = "---"
                self.assertFalse(gate.active_timer_decision_is_approved(invalid))

    def test_active_timer_runtime_requires_observation_and_completed_cleanup(self):
        self.add_active_timer_model()
        source = self.root / "app/src/main/java/TimerUpdates.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "managementService.setJobDuedate(timerId, firstDate);\n"
            "managementService.setJobDuedate(timerId, secondDate);\n",
            encoding="utf-8",
        )
        self.complete_required_checks()
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        evidence["active_timer_update_decision"] = {
            "status": "approved",
            "approval_reference": "fixture-active-timer-approval",
            "alternative_evidence_reference": "fixture-active-timer-support",
            "target_version": "8.9.21",
        }
        write_json(self.root / gate.EVIDENCE, evidence)
        review = ("module", "app", "active_timer_updates", None)
        runtime = ("module", "app", "active_timer_update_runtime", None)
        self.submit(
            review,
            action="review",
            disposition="verified",
            affected_timers_json=json.dumps(self.affected_timer_inventory()),
            note="Synthetic evidence exercises the approved-decision path.",
        )
        common = {
            "environment": "local",
            "target_version": "8.9.21",
            "target_disposable": True,
            "cleanup_plan": "Delete the test deployment and generated instances.",
            "command": [sys.executable, "-c", "print('success')"],
        }
        with self.assertRaisesRegex(gate.EvidenceError, "machine-readable observation evidence"):
            self.submit(runtime, **common)

        invalid = self.active_timer_observation()
        invalid["cleanup"]["completed"] = False
        with self.assertRaisesRegex(gate.EvidenceError, "cleanup completed"):
            self.submit(
                runtime,
                **common,
                active_timer_update_observation_json=json.dumps(invalid),
            )

        mismatched = self.active_timer_observation()
        mismatched["observation"]["timers"][0]["timer_id"] = "unreviewed-timer"
        with self.assertRaisesRegex(gate.EvidenceError, "reviewed affected timer inventory"):
            self.submit(
                runtime,
                **common,
                active_timer_update_observation_json=json.dumps(mismatched),
            )

        incomplete = self.active_timer_observation()
        for timer in incomplete["observation"]["timers"]:
            timer["source_locations"] = timer["source_locations"][:-1]
        with self.assertRaisesRegex(gate.EvidenceError, "cover every detected update source"):
            self.submit(
                runtime,
                **common,
                active_timer_update_observation_json=json.dumps(incomplete),
            )

        early = self.active_timer_observation()
        early["observation"]["timers"][0]["final_deadline_fired_at"] = (
            "2026-09-29T11:59:59Z"
        )
        with self.assertRaisesRegex(gate.EvidenceError, "before the requested final deadline"):
            self.submit(
                runtime,
                **common,
                active_timer_update_observation_json=json.dumps(early),
            )

        self.submit(
            runtime,
            **common,
            active_timer_update_observation_json=json.dumps(self.active_timer_observation()),
        )
        self.assertEqual(0, self.audit())
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        runtime_record = next(
            path for path in evidence["checks"]
            if json.loads((self.root / path).read_text(encoding="utf-8"))["kind"]
            == "active_timer_update_runtime"
        )
        recorded = json.loads((self.root / runtime_record).read_text(encoding="utf-8"))
        del recorded["active_timer_update_observation"]
        write_json(self.root / runtime_record, recorded)
        self.assertEqual(1, self.audit())
        self.assertIn(
            "machine-readable observation evidence",
            "\n".join(self.summary()["issues"]),
        )

    def test_active_timer_review_requires_existing_bpmn_timer_inventory(self):
        self.add_active_timer_model()
        source = self.root / "app/src/main/java/TimerUpdates.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "managementService.setJobDuedate(timerId, newDate);\n",
            encoding="utf-8",
        )
        self.complete_required_checks()
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        evidence["active_timer_update_decision"] = {
            "status": "approved",
            "approval_reference": "fixture-active-timer-approval",
            "alternative_evidence_reference": "fixture-active-timer-support",
            "target_version": "8.9.21",
        }
        write_json(self.root / gate.EVIDENCE, evidence)

        review = ("module", "app", "active_timer_updates", None)
        invalid_inventory = self.affected_timer_inventory()
        invalid_inventory[0]["model_path"] = "models/not-in-scope.bpmn"
        with self.assertRaisesRegex(gate.EvidenceError, "in-scope BPMN timer"):
            self.submit(
                review,
                action="review",
                disposition="verified",
                affected_timers_json=json.dumps(invalid_inventory),
            )

        with self.assertRaisesRegex(gate.EvidenceError, "Affected timer inventory"):
            self.submit(
                review,
                action="review",
                disposition="verified",
                affected_timers_json=json.dumps([]),
            )

    def test_active_timer_review_rejects_a_timer_from_another_deployment_set(self):
        self.write_duplicate_sample_scope()
        self.plan["models"][0]["deployment_set"] = "source-set"
        self.plan["models"][1]["deployment_set"] = "other-set"
        self.plan["deployment_sets"] = [
            {
                "name": "source-set",
                "modules": ["modules/c7-client"],
                "models": ["models/converted-one.bpmn"],
            },
            {
                "name": "other-set",
                "modules": ["modules/c8-client"],
                "models": ["models/converted-two.bpmn"],
            },
        ]
        self.write_scope()
        self.add_active_timer_model()
        source = self.root / "modules/c7-client/src/main/java/TimerUpdates.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "managementService.setJobDuedate(timerId, newDate);\n",
            encoding="utf-8",
        )
        self.complete_required_checks()
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        evidence["active_timer_update_decision"] = {
            "status": "approved",
            "approval_reference": "fixture-active-timer-approval",
            "alternative_evidence_reference": "fixture-active-timer-support",
            "target_version": "8.9.21",
        }
        write_json(self.root / gate.EVIDENCE, evidence)

        review = ("module", "modules/c7-client", "active_timer_updates", None)
        inventory = [{
            "model_path": "models/converted-two.bpmn",
            "process_id": "Sample",
            "timer_id": "sample-timer",
            "source_locations": [
                "modules/c7-client/src/main/java/TimerUpdates.java:1"
            ],
        }]
        with self.assertRaisesRegex(gate.EvidenceError, "in-scope BPMN timer"):
            self.submit(
                review,
                action="review",
                disposition="verified",
                affected_timers_json=json.dumps(inventory),
                note="The affected timer is outside the source module's deployment set.",
            )

    def test_non_timer_due_date_occurrences_can_be_reviewed_with_evidence(self):
        source = self.root / "app/src/main/java/TimerUpdates.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "managementService.setJobDuedate(jobId, newDate);\n",
            encoding="utf-8",
        )
        self.complete_required_checks()
        evidence = [{
            "location": "app/src/main/java/TimerUpdates.java:1",
            "kind": "setJobDuedate",
            "evidence": "The selected job is a service-task job, not a timer job.",
        }]

        review = ("module", "app", "active_timer_updates", None)
        with self.assertRaisesRegex(gate.EvidenceError, "non-timer update evidence"):
            self.submit(
                review,
                action="review",
                disposition="non_timer",
                non_timer_update_evidence_json=json.dumps([]),
            )
        self.assertEqual(
            0,
            self.submit(
                review,
                action="review",
                disposition="non_timer",
                non_timer_update_evidence_json=json.dumps(evidence),
                note="Reviewed the selected job type and recorded why this is not a timer update.",
            ),
        )
        self.assertEqual(0, self.audit())

    def test_non_timer_classification_keeps_other_active_timer_updates_blocked(self):
        self.add_active_timer_model()
        source = self.root / "app/src/main/java/TimerUpdates.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "managementService.setJobDuedate(timerId, finalDate);\n"
            "managementService.setJobDuedate(jobId, serviceDate);\n",
            encoding="utf-8",
        )
        self.complete_required_checks()
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        evidence["active_timer_update_decision"] = {
            "status": "approved",
            "approval_reference": "fixture-active-timer-approval",
            "alternative_evidence_reference": "fixture-active-timer-support",
            "target_version": "8.9.21",
        }
        write_json(self.root / gate.EVIDENCE, evidence)

        review = ("module", "app", "active_timer_updates", None)
        non_timer = [{
            "location": "app/src/main/java/TimerUpdates.java:2",
            "kind": "setJobDuedate",
            "evidence": "The selected job is a service-task job, not a timer job.",
        }]
        active_timer = [{
            "model_path": "models/converted-c8-process.bpmn",
            "process_id": "p",
            "timer_id": "sample-timer",
            "source_locations": ["app/src/main/java/TimerUpdates.java:1"],
        }]
        self.submit(
            review,
            action="review",
            disposition="verified",
            affected_timers_json=json.dumps(active_timer),
            non_timer_update_evidence_json=json.dumps(non_timer),
            note="Reviewed one non-timer call and mapped the active timer call.",
        )

        self.assertEqual(1, self.audit())
        self.assertIn(
            "Missing module active_timer_update_runtime",
            "\n".join(self.summary()["issues"]),
        )
        observation = self.active_timer_observation()
        observation["observation"]["timers"] = [
            timer
            for timer in observation["observation"]["timers"]
            if timer["timer_id"] == "sample-timer"
        ]
        observation["observation"]["timers"][0]["source_locations"] = [
            "app/src/main/java/TimerUpdates.java:1"
        ]
        self.assertEqual(
            0,
            self.submit(
                ("module", "app", "active_timer_update_runtime", None),
                environment="local",
                target_version="8.9.21",
                target_disposable=True,
                cleanup_plan="Delete the test deployment and generated instances.",
                active_timer_update_observation_json=json.dumps(observation),
            ),
        )
        self.assertEqual(0, self.audit())

    def test_timer_preflight_requires_explicit_disposable_target_and_cleanup(self):
        self.write_scope(timer=True)
        key = ("timer", "models/converted-c8-process.bpmn#p#Start", "disposition", None)
        self.assertEqual(
            0,
            self.submit(
                key,
                action="review",
                disposition="preserve",
                note="Preserve the R/PT1H cycle and its automatic-start effect.",
            ),
        )
        with self.assertRaisesRegex(gate.EvidenceError, "disposable"):
            self.submit(
                ("timer", "models/converted-c8-process.bpmn#p#Start", "preflight", None),
                environment="local",
                cleanup_plan="Delete the test deployment and all generated instances.",
            )

    def test_timer_preflight_requires_observed_deployment_and_completed_cleanup(self):
        self.write_scope(timer=True)
        key = ("timer", "models/converted-c8-process.bpmn#p#Start", "disposition", None)
        self.submit(
            key,
            action="review",
            disposition="preserve",
            note="Preserve the R/PT1H cycle and its automatic-start effect.",
        )
        preflight = ("timer", "models/converted-c8-process.bpmn#p#Start", "preflight", None)
        common = {
            "environment": "local",
            "target_version": "8.9.21",
            "target_disposable": True,
            "cleanup_plan": "Delete the test deployment and all generated instances.",
            "command": [sys.executable, "-c", "print('success')"],
        }
        with self.assertRaisesRegex(gate.EvidenceError, "timer observation"):
            self.submit(preflight, **common)
        invalid = self.timer_observation(preflight)
        invalid["cleanup"]["completed"] = False
        with self.assertRaisesRegex(gate.EvidenceError, "cleanup completed"):
            self.submit(
                preflight,
                **common,
                timer_observation_json=json.dumps(invalid),
            )
        self.assertEqual(1, self.audit())

        self.complete_required_checks()
        self.assertEqual(0, self.audit())

    def test_timer_observation_is_revalidated_before_deployment_command(self):
        self.write_scope(timer=True)
        self.complete_required_checks()
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        timer_reference = None
        for reference in evidence["checks"]:
            check = json.loads((self.root / reference).read_text(encoding="utf-8"))
            if check["type"] == "timer" and check["kind"] == "preflight":
                timer_reference = reference
                break
        self.assertIsNotNone(timer_reference)
        timer_check_path = self.root / timer_reference
        timer_check = json.loads(timer_check_path.read_text(encoding="utf-8"))
        timer_check["timer_observation"] = None
        write_json(timer_check_path, timer_check)

        marker = self.root / "deployment-ran-without-timer-observation"
        command = [
            sys.executable,
            "-c",
            "from pathlib import Path; Path('deployment-ran-without-timer-observation').touch()",
        ]
        deployment = ("model", "models/converted-c8-process.bpmn", "deployment", None)
        with self.assertRaisesRegex(
            gate.EvidenceError,
            "machine-readable timer observation evidence",
        ):
            self.submit(deployment, environment="local", command=command)
        self.assertFalse(marker.exists())

    def test_model_and_config_edits_make_prior_evidence_stale(self):
        self.write_scope(timer=True)
        config = self.root / "app/application.yml"
        config.write_text("worker.enabled: true\n", encoding="utf-8")
        self.complete_required_checks()
        self.assertEqual(0, self.audit())

        source = self.root / "models/process.bpmn"
        original = source.read_text(encoding="utf-8")
        changed = original.replace(
            '<bpmn:startEvent id="Start">',
            '<bpmn:startEvent id="Start" name="source metadata update">',
        )
        self.assertNotEqual(original, changed)
        self.assertIn("id=\"p\"", changed)
        self.assertIn("R/PT1H", changed)
        source.write_text(changed, encoding="utf-8")
        config.write_text("worker.enabled: false\n", encoding="utf-8")

        self.assertEqual(1, self.audit())
        issues = "\n".join(self.summary()["issues"])
        self.assertIn("stale", issues.lower())

    def test_stale_evidence_blocks_dependent_commands_before_execution_but_allows_refresh(self):
        self.complete_required_checks()
        source = self.root / "models/process.bpmn"
        changed = source.read_text(encoding="utf-8").replace(
            '<bpmn:startEvent id="Start">',
            '<bpmn:startEvent id="Start" name="source changed">',
        )
        self.assertNotEqual(source.read_text(encoding="utf-8"), changed)
        source.write_text(changed, encoding="utf-8")

        marker = self.root / "dependent-command-ran"
        command = [
            sys.executable,
            "-c",
            "from pathlib import Path; Path('dependent-command-ran').touch()",
        ]
        deployment = ("model", "models/converted-c8-process.bpmn", "deployment", None)
        with self.assertRaisesRegex(gate.EvidenceError, "stale"):
            self.submit(deployment, environment="local", command=command)
        self.assertFalse(marker.exists())

        process_path = next(
            key
            for key in gate.requirements(
                self.root,
                json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8")),
            ).required
            if key[0] == "process" and key[2] == "process_path"
        )
        with self.assertRaisesRegex(gate.EvidenceError, "stale"):
            self.submit(process_path, environment="local", command=command)
        self.assertFalse(marker.exists())

        lint = ("model", "models/converted-c8-process.bpmn", "lint", None)
        self.assertEqual(
            0,
            self.submit(
                lint,
                command=[sys.executable, "-c", "print('fresh lint evidence')"],
            ),
        )
        preflight = ("deployment_set", "shared", "preflight", None)
        self.assertEqual(
            0,
            self.submit(
                preflight,
                action="review",
                caller_inventory_json="[]",
            ),
        )

        with self.assertRaisesRegex(gate.EvidenceError, "stale"):
            self.submit(process_path, environment="local", command=command)
        self.assertFalse(marker.exists())

    def test_stale_timer_disposition_blocks_runtime_preflight_before_command(self):
        self.write_scope(timer=True)
        self.complete_required_checks()
        converted = self.root / "models/converted-c8-process.bpmn"
        xml = converted.read_text(encoding="utf-8")
        converted.write_text(xml.replace("R/PT1H", "R/PT2H"), encoding="utf-8")

        marker = self.root / "timer-preflight-ran"
        command = [
            sys.executable,
            "-c",
            "from pathlib import Path; Path('timer-preflight-ran').touch()",
        ]
        key = ("timer", "models/converted-c8-process.bpmn#p#Start", "preflight", None)
        with self.assertRaisesRegex(gate.EvidenceError, "stale"):
            self.submit(
                key,
                environment="local",
                target_version="8.9.21",
                target_disposable=True,
                cleanup_plan="Delete the test deployment and generated instances.",
                command=command,
            )
        self.assertFalse(marker.exists())

    def test_stale_active_timer_review_blocks_runtime_command(self):
        self.add_active_timer_model()
        source = self.root / "app/src/main/java/TimerUpdates.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "managementService.setJobDuedate(timerId, newDate);\n",
            encoding="utf-8",
        )
        self.complete_required_checks()
        evidence = json.loads((self.root / gate.EVIDENCE).read_text(encoding="utf-8"))
        evidence["active_timer_update_decision"] = {
            "status": "approved",
            "approval_reference": "fixture-active-timer-approval",
            "alternative_evidence_reference": "fixture-active-timer-support",
            "target_version": "8.9.21",
        }
        write_json(self.root / gate.EVIDENCE, evidence)
        review = ("module", "app", "active_timer_updates", None)
        self.submit(
            review,
            action="review",
            disposition="verified",
            affected_timers_json=json.dumps(self.affected_timer_inventory()),
            note="Synthetic review records the approved alternative and affected timers.",
        )
        (self.root / "app/application.yml").write_text("worker.enabled: true\n", encoding="utf-8")

        marker = self.root / "active-timer-validation-ran"
        command = [
            sys.executable,
            "-c",
            "from pathlib import Path; Path('active-timer-validation-ran').touch()",
        ]
        with self.assertRaisesRegex(gate.EvidenceError, "stale"):
            self.submit(
                ("module", "app", "active_timer_update_runtime", None),
                environment="local",
                target_version="8.9.21",
                target_disposable=True,
                cleanup_plan="Delete the test deployment and generated instances.",
                command=command,
            )
        self.assertFalse(marker.exists())

    def test_symlinked_in_scope_source_cannot_be_silently_skipped(self):
        shared = self.root / "shared/Caller.java"
        shared.parent.mkdir(parents=True, exist_ok=True)
        shared.write_text(
            'runtimeService.startProcessInstanceByKey("p");\n',
            encoding="utf-8",
        )
        link = self.root / "app/src/main/java/Caller.java"
        link.parent.mkdir(parents=True, exist_ok=True)
        link.symlink_to(shared)

        plan = gate.requirements(self.root, self.plan)
        self.assertIn(
            "Cannot scan symlinked in-scope source file: app/src/main/java/Caller.java",
            plan.issues,
        )
        self.assertEqual(1, self.audit())
        self.assertIn(
            "symlinked in-scope source file",
            "\n".join(self.summary()["issues"]),
        )

    def test_event_subprocess_timer_is_not_a_deployment_start_timer(self):
        event_subprocess = (
            '<bpmn:subProcess id="EventSubprocess" triggeredByEvent="true">'
            '<bpmn:startEvent id="EventTimerStart">'
            '<bpmn:timerEventDefinition><bpmn:timeCycle>R/PT1H</bpmn:timeCycle>'
            "</bpmn:timerEventDefinition></bpmn:startEvent>"
            "</bpmn:subProcess>"
        )
        self.write_scope(extra=event_subprocess)

        requirements = gate.requirements(self.root, self.plan)
        self.assertEqual({}, requirements.timer_inventory)
        self.assertEqual(
            {("p", "EventTimerStart")},
            requirements.timer_elements_by_model["models/converted-c8-process.bpmn"],
        )

    def test_timer_inventory_requires_explicit_removal_disposition(self):
        self.write_scope(timer=True)
        converted = self.root / "models/converted-c8-process.bpmn"
        xml = converted.read_text(encoding="utf-8")
        converted.write_text(
            xml.replace(
                "<bpmn:timerEventDefinition><bpmn:timeCycle>R/PT1H</bpmn:timeCycle>"
                "</bpmn:timerEventDefinition>",
                "",
            ),
            encoding="utf-8",
        )
        requirements = gate.requirements(self.root, self.plan)
        disposition = ("timer", "models/converted-c8-process.bpmn#p#Start", "disposition", None)
        self.assertEqual("remove", requirements.timer_inventory[disposition]["expected_disposition"])
        self.assertNotIn(
            ("timer", "models/converted-c8-process.bpmn#p#Start", "preflight", None),
            requirements.required,
        )
        self.complete_required_checks()
        self.assertEqual(0, self.audit())
        check = next(
            check for check in self.summary()["checks"]
            if check["type"] == "timer" and check["kind"] == "disposition"
        )
        self.assertEqual("remove", check["disposition"])
        self.assertEqual("R/PT1H", check["timer_inventory"]["source_cycle"])
        self.assertIsNone(check["timer_inventory"]["converted_cycle"])
        self.assertEqual("PT1H", check["timer_inventory"]["interval"])
        self.assertIsNone(check["timer_inventory"]["repetitions"])
        self.assertEqual("app", check["timer_inventory"]["module"])
        self.assertEqual(
            "Each firing starts a process instance.",
            check["timer_inventory"]["automatic_start_effect"],
        )

    def test_timer_cycle_change_records_the_new_interval_and_repetition_count(self):
        self.write_scope(timer=True)
        converted = self.root / "models/converted-c8-process.bpmn"
        converted.write_text(
            converted.read_text(encoding="utf-8").replace("R/PT1H", "R5/PT5S"),
            encoding="utf-8",
        )
        requirements = gate.requirements(self.root, self.plan)
        disposition = ("timer", "models/converted-c8-process.bpmn#p#Start", "disposition", None)
        self.assertEqual("change", requirements.timer_inventory[disposition]["expected_disposition"])
        self.assertEqual("PT5S", requirements.timer_inventory[disposition]["interval"])
        self.assertEqual(5, requirements.timer_inventory[disposition]["repetitions"])
        self.complete_required_checks()
        self.assertEqual(0, self.audit())

    def test_cron_time_cycle_is_supported_without_iso_interval_fields(self):
        self.write_scope(timer=True)
        converted = self.root / "models/converted-c8-process.bpmn"
        converted.write_text(
            converted.read_text(encoding="utf-8").replace(
                "R/PT1H",
                "0 0 9-17 * * MON-FRI",
            ),
            encoding="utf-8",
        )
        requirements = gate.requirements(self.root, self.plan)
        disposition = ("timer", "models/converted-c8-process.bpmn#p#Start", "disposition", None)
        inventory = requirements.timer_inventory[disposition]
        self.assertEqual("change", inventory["expected_disposition"])
        self.assertEqual("cron", inventory["cycle_type"])
        self.assertIsNone(inventory["interval"])
        self.assertIsNone(inventory["repetitions"])
        self.complete_required_checks()
        self.assertEqual(0, self.audit())

    def test_iso_8601_cycles_reject_empty_or_missing_time_components(self):
        for cycle in ("R/PT", "R/P1DT"):
            with self.subTest(cycle=cycle):
                self.assertIsNone(gate.cycle_details(cycle))
        self.assertEqual("iso_8601", gate.cycle_details("R/PT1S")["cycle_type"])
        self.assertEqual("iso_8601", gate.cycle_details("R/P1DT1H")["cycle_type"])

    def test_fixture_import_is_independent_of_the_current_working_directory(self):
        result = subprocess.run(
            [
                sys.executable,
                "-c",
                "import runpy, sys; runpy.run_path(sys.argv[1])",
                str(Path(__file__).resolve()),
            ],
            cwd=self.root,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def complete_required_checks(self, caller_inventory=None, skip_keys=None):
        requirements = gate.requirements(self.root, self.plan)
        required = requirements.required
        skip_keys = set(skip_keys or ())
        self.assertEqual([], requirements.issues)
        priorities = {
            "project": 0, "module": 1, "deployment_set": 2, "timer": 3, "model": 4, "process": 5,
        }
        for key in sorted(
            required,
            key=lambda item: (
                priorities[item[0]],
                item[1],
                {
                    "preflight": 0, "lint": 0, "worker_input_inventory": 0,
                    "review": 1, "disposition": 0, "duplicate_process_id": 1,
                    "deployment": 2,
                }.get(item[2], 3),
                item[2],
                item[3] or "",
            ),
        ):
            if key in skip_keys:
                continue
            if key == ("project", ".", "docker_info", None):
                continue
            environment = (
                "local"
                if key[0] in ("timer", "process")
                or key[2] in (*gate.RUNTIME_CHECKS, "deployment", "active_timer_update_runtime")
                else None
            )
            options = {
                "environment": environment,
                "target_version": (
                    "8.9.21"
                    if key[0] == "timer" and key[2] == "preflight"
                    or key[2] == "active_timer_update_runtime"
                    else None
                ),
                "target_disposable": (
                    key[0] == "timer" and key[2] == "preflight"
                    or key[2] == "active_timer_update_runtime"
                ),
                "cleanup_plan": (
                    "Remove the test deployment and all generated instances."
                    if key[0] == "timer" and key[2] == "preflight"
                    or key[2] == "active_timer_update_runtime"
                    else None
                ),
                "timer_observation_json": (
                    json.dumps(self.timer_observation(key))
                    if key[0] == "timer" and key[2] == "preflight"
                    else None
                ),
                "caller_inventory_json": (
                    json.dumps(caller_inventory or [])
                    if key[0] == "deployment_set" and key[2] == "preflight"
                    else None
                ),
                "disposition": (
                    "explicit_version"
                    if key[0] == "deployment_set" and key[2] == "duplicate_process_id"
                    else requirements.timer_inventory[key]["expected_disposition"]
                    if key[0] == "timer" and key[2] == "disposition"
                    else "no_updates"
                    if key[2] == "active_timer_updates"
                    and not requirements.active_timer_updates.get(key[1])
                    else None
                ),
            }
            action = "review" if required[key] == "review" else "run"
            expected_result = 0
            if key[2] == "active_timer_updates" and requirements.active_timer_updates.get(key[1]):
                action = "block"
                expected_result = 1
                options["reason"] = (
                    "Manual blocker: trace every C7 due-date caller and affected active timer, "
                    "including repeated updates, before approving a supported replacement."
                )
            self.assertEqual(
                expected_result,
                self.submit(key, action=action, **options),
            )

    def deployment_execution_keys(self):
        required = gate.requirements(self.root, self.plan).required
        return {key for key in required if key[2] in ("deployment", "process_path")}

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
                "module": f"examples/{name}" if name in module_names else "examples/web",
                "deployment_set": "shared-target",
                "processes": [{"id": f"{name}-process", "standalone": True, "scenarios": ["normal"]}],
            }
            for name in model_names
        ]
        self.plan["deployment_sets"] = [
            {
                "name": "shared-target",
                "modules": [f"examples/{name}" for name in module_names],
                "models": [model["path"] for model in self.plan["models"]],
            }
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
        requirements = gate.requirements(self.root, self.plan)
        required = requirements.required
        self.assertEqual([], requirements.issues)
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
            {
                "source_path": "models/rules.dmn11.xml",
                "path": "models/converted-c8-rules.dmn",
                "module": ".",
                "deployment_set": "rules",
                "processes": [],
            }
        ]
        self.plan["deployment_sets"] = [
            {
                "name": "rules",
                "modules": [],
                "models": ["models/converted-c8-rules.dmn"],
            }
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
            {"lint", "review", "deployment", "preflight", "active_timer_updates"},
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
        self.assertEqual(
            0,
            self.submit(
                ("deployment_set", "shared", "preflight", None),
                action="review",
                caller_inventory_json="[]",
            ),
        )
        with self.assertRaisesRegex(gate.EvidenceError, "Timer preflight"):
            self.submit(("model", model, "deployment", None), environment="local")
        with self.assertRaisesRegex(gate.EvidenceError, "Record the timer disposition"):
            self.submit(
                ("timer", f"{model}#p#Start", "preflight", None),
                environment="local",
                target_version="8.9.21",
                target_disposable=True,
                cleanup_plan="Delete the test deployment and instances.",
            )
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
