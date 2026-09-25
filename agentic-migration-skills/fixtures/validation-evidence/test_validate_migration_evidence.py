"""Regression tests for the migration validation evidence gate."""

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
import xml.etree.ElementTree as ET
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import patch


FIXTURE = Path(__file__).resolve().parent
LOG_DIRECTORY = ".camunda-migration/validation/logs"
BPMN_MODEL_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL"
BPMN_DI_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/DI"
CAMUNDA_BPMN_NAMESPACE = "http://camunda.org/schema/1.0/bpmn"
DMN_MODEL_NAMESPACE = "https://www.omg.org/spec/DMN/20191111/MODEL/"
STEP2_INVENTORY_PATH = (
    ".camunda-migration/validation/step2-inventory.json"
)
SKILL_ROOT = FIXTURE.parents[1] / "skills" / "migrate-c7-to-c8-code"
SCRIPT = SKILL_ROOT / "scripts" / "validate_migration_evidence.py"
FORM_CHECKS = (
    "accepted_forms",
    "form_parsing",
    "form_schema",
    "form_js",
    "form_definition",
    "form_deployment",
    "form_references",
    "form_binding",
)
sys.path.insert(0, str(SCRIPT.parent))
import validate_migration_evidence as gate  # noqa: E402


def passing_check(
    target_type, target, kind, evidence_path, scenario=None, environment=None
):
    return {
        "target_type": target_type,
        "target": target,
        "kind": kind,
        "scenario": scenario,
        "method": "command",
        "command": "python3 -c pass",
        "exit_code": 0,
        "result": "passed",
        "evidence_path": evidence_path,
        "reason": None,
        "blocker_reason": None,
        "failure_class": None,
        "environment": environment,
    }


def not_applicable_check(target_type, target, kind, reason):
    return {
        "target_type": target_type,
        "target": target,
        "kind": kind,
        "scenario": None,
        "method": "not_applicable",
        "command": "N/A: " + reason,
        "exit_code": None,
        "result": "not_applicable",
        "evidence_path": None,
        "reason": reason,
        "blocker_reason": None,
        "failure_class": None,
        "environment": None,
    }


def write_step2_inventory(project_root, modules, models):
    inventory_path = project_root / STEP2_INVENTORY_PATH
    inventory_path.parent.mkdir(parents=True, exist_ok=True)
    inventory_path.write_text(
        json.dumps(
            {
                "schema_version": 1,
                "modules": modules,
                "models": models,
            }
        ),
        encoding="utf-8",
    )


def write_unit_suite_configuration(project_root, module_path):
    module_root = project_root / module_path
    module_root.mkdir(parents=True, exist_ok=True)
    (module_root / "build.gradle.kts").write_text(
        'tasks.register<Test>("unit") {}\n',
        encoding="utf-8",
    )


def passing_module_manifest(module_path):
    checks = []
    for kind in gate.MODULE_CHECKS:
        if kind in {
            "spring_boot_run",
            "executable_jar",
            "external_launcher",
        }:
            checks.append(
                not_applicable_check(
                    "module",
                    module_path,
                    kind,
                    "The module has no runtime entry point.",
                )
            )
        else:
            checks.append(
                passing_check(
                    "module",
                    module_path,
                    kind,
                    LOG_DIRECTORY + "/pass.log",
                )
            )
    checks.append(
        passing_check(
            "module",
            module_path,
            "tests",
            LOG_DIRECTORY + "/pass.log",
            "unit",
        )
    )
    return {
        "schema_version": 1,
        "mode": "migration",
        "modules": [
            {
                "path": module_path,
                "runtime_mode": "none",
                "test_suites": [{"name": "unit", "requires_docker": False}],
            }
        ],
        "models": [],
        "checks": checks,
    }


def write_bpmn_file(path, processes, timer_starts, include_di=False):
    root = ET.Element(
        "{{{}}}definitions".format(BPMN_MODEL_NAMESPACE)
    )
    process_elements = {}
    for process in processes:
        if not isinstance(process, dict) or not isinstance(process.get("id"), str):
            continue
        executable = str(process.get("executable", False)).lower()
        process_element = ET.SubElement(
            root,
            "{{{}}}process".format(BPMN_MODEL_NAMESPACE),
            {"id": process["id"], "isExecutable": executable},
        )
        process_elements[process["id"]] = process_element
    for timer in timer_starts:
        if not isinstance(timer, dict):
            continue
        process_element = process_elements.get(timer.get("process_id"))
        timer_id = timer.get("id")
        if process_element is None or not isinstance(timer_id, str):
            continue
        start_event = ET.SubElement(
            process_element,
            "{{{}}}startEvent".format(BPMN_MODEL_NAMESPACE),
            {"id": timer_id},
        )
        timer_definition = ET.SubElement(
            start_event,
            "{{{}}}timerEventDefinition".format(BPMN_MODEL_NAMESPACE),
        )
        ET.SubElement(
            timer_definition,
            "{{{}}}timeCycle".format(BPMN_MODEL_NAMESPACE),
        ).text = "R/PT1M"
    if include_di:
        ET.SubElement(
            root,
            "{{{}}}BPMNDiagram".format(BPMN_DI_NAMESPACE),
            {"id": "BPMNDiagram_1"},
        )
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


def write_source_bpmn_with_owner(path, process_id, form_kind, form_reference=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    root = ET.Element("{{{}}}definitions".format(BPMN_MODEL_NAMESPACE))
    process = ET.SubElement(
        root,
        "{{{}}}process".format(BPMN_MODEL_NAMESPACE),
        {"id": process_id, "isExecutable": "true"},
    )
    owner = ET.SubElement(
        process,
        "{{{}}}userTask".format(BPMN_MODEL_NAMESPACE),
        {"id": "Task_FormOwner"},
    )
    if form_kind == "generated":
        extension_elements = ET.SubElement(
            owner,
            "{{{}}}extensionElements".format(BPMN_MODEL_NAMESPACE),
        )
        form_data = ET.SubElement(
            extension_elements,
            "{{{}}}formData".format(CAMUNDA_BPMN_NAMESPACE),
        )
        ET.SubElement(
            form_data,
            "{{{}}}formField".format(CAMUNDA_BPMN_NAMESPACE),
            {"id": "field"},
        )
    elif form_kind == "referenced":
        owner.set(
            "{{{}}}formKey".format(CAMUNDA_BPMN_NAMESPACE),
            form_reference or "embedded:app:forms/task.html",
        )
    elif form_kind != "form-free-owner":
        raise ValueError("Unsupported source form kind: {}".format(form_kind))
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


def write_dmn_file(path):
    root = ET.Element(
        "{{{}}}definitions".format(DMN_MODEL_NAMESPACE)
    )
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


def materialize_inventory(project_root, manifest):
    for module in manifest["modules"]:
        (project_root / module["path"]).mkdir(parents=True, exist_ok=True)
    for model in manifest["models"]:
        for key in ("source_path", "path"):
            model_file = project_root / model[key]
            model_file.parent.mkdir(parents=True, exist_ok=True)
            if model.get("type") == "bpmn":
                write_bpmn_file(
                    model_file,
                    model.get("processes", []),
                    model.get("recurring_timer_starts", []),
                )
            else:
                write_dmn_file(model_file)


class ValidationEvidenceTest(unittest.TestCase):
    def run_gate(
        self,
        project_root,
        evidence="validation-evidence.json",
        report="MIGRATION_REPORT.md",
        summary=None,
    ):
        command = [
            sys.executable,
            str(SCRIPT),
            "--project-root",
            str(project_root),
            "--evidence",
            evidence,
        ]
        if report is not None:
            command.extend(["--report", report])
        if summary is not None:
            command.extend(["--summary", summary])
        return subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
        )

    def test_contradictory_report_is_not_ready(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            materialize_inventory(project_root, manifest)
            completed = self.run_gate(project_root)

            self.assertEqual(completed.returncode, 1)
            summary = json.loads(completed.stdout)
            self.assertEqual(summary["readiness"], "not_ready")
            self.assertGreater(summary["counts"]["failed"], 0)
            self.assertGreater(summary["counts"]["blocked"], 0)
            self.assertGreater(summary["counts"]["not_run"], 0)
            self.assertGreater(summary["counts"]["missing"], 0)
            saved_summary = json.loads(
                (
                    project_root
                    / ".camunda-migration/validation/validation-summary.json"
                ).read_text(encoding="utf-8")
            )
            self.assertEqual(saved_summary, summary)

            report = (project_root / "MIGRATION_REPORT.md").read_text(
                encoding="utf-8"
            )
            gate_block = report.split(gate.REPORT_START, 1)[1].split(
                gate.REPORT_END, 1
            )[0]
            self.assertIn("**Validation gate:** **NOT READY**", gate_block)
            self.assertNotIn("**Validation gate:** **READY**", gate_block)

    def test_malformed_existing_report_gate_is_replaced_with_not_ready(self):
        malformed_gates = {
            "incomplete": (
                "{}\n**Validation gate:** **READY**\n".format(
                    gate.REPORT_START
                )
            ),
            "duplicate": (
                "{}\n**Validation gate:** **READY**\n{}\n\n"
                "{}\n**Validation gate:** **READY**\n{}".format(
                    gate.REPORT_START,
                    gate.REPORT_END,
                    gate.REPORT_START,
                    gate.REPORT_END,
                )
            ),
            "missing-start": (
                "## Aggregate validation gate\n\n"
                "**Validation gate:** **READY**\n\n{}".format(
                    gate.REPORT_END
                )
            ),
            "reversed-markers": (
                "{}\n**Validation gate:** **READY**\n{}".format(
                    gate.REPORT_END,
                    gate.REPORT_START,
                )
            ),
        }
        for malformed_kind, malformed_gate in malformed_gates.items():
            with self.subTest(malformed_kind=malformed_kind):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary) / "project"
                    shutil.copytree(FIXTURE, project_root)
                    manifest = json.loads(
                        (project_root / "validation-evidence.json").read_text(
                            encoding="utf-8"
                        )
                    )
                    materialize_inventory(project_root, manifest)
                    report = project_root / "MIGRATION_REPORT.md"
                    report.write_text(
                        "Migration findings.\n\n"
                        + malformed_gate
                        + "\n\n## Open items\n\nKeep this decision.\n",
                        encoding="utf-8",
                    )

                    completed = self.run_gate(project_root)

                    self.assertEqual(completed.returncode, 1)
                    summary = json.loads(completed.stdout)
                    self.assertTrue(
                        any(
                            "malformed validation gate block"
                            in blocker
                            for blocker in summary["blockers"]
                        )
                    )
                    report_text = report.read_text(encoding="utf-8")
                    self.assertIn("Migration findings.", report_text)
                    self.assertIn("## Open items", report_text)
                    self.assertIn("Keep this decision.", report_text)
                    self.assertIn(
                        "**Validation gate:** **NOT READY**", report_text
                    )
                    self.assertNotIn(
                        "**Validation gate:** **READY**", report_text
                    )
                    self.assertEqual(
                        report_text.count(gate.REPORT_START), 1
                    )
                    self.assertEqual(report_text.count(gate.REPORT_END), 1)
                    saved_summary = json.loads(
                        (
                            project_root
                            / ".camunda-migration/validation/validation-summary.json"
                        ).read_text(encoding="utf-8")
                    )
                    self.assertEqual(saved_summary, summary)

    def test_markerless_legacy_ready_gate_is_replaced_with_not_ready(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            materialize_inventory(project_root, manifest)
            report = project_root / "MIGRATION_REPORT.md"
            report.write_text(
                "Migration findings.\n\n"
                "## Aggregate validation gate\n\n"
                "**Validation gate:** **READY**\n\n"
                "Legacy gate details.\n\n"
                "## Open items\n\nKeep this decision.\n",
                encoding="utf-8",
            )

            completed = self.run_gate(project_root)

            self.assertEqual(completed.returncode, 1)
            summary = json.loads(completed.stdout)
            self.assertEqual(summary["readiness"], "not_ready")
            self.assertTrue(
                any(
                    "malformed validation gate block" in blocker
                    for blocker in summary["blockers"]
                )
            )
            report_text = report.read_text(encoding="utf-8")
            self.assertIn("Migration findings.", report_text)
            self.assertIn("## Open items", report_text)
            self.assertIn("Keep this decision.", report_text)
            self.assertIn("**Validation gate:** **NOT READY**", report_text)
            self.assertNotIn("**Validation gate:** **READY**", report_text)
            self.assertEqual(report_text.count(gate.REPORT_START), 1)
            self.assertEqual(report_text.count(gate.REPORT_END), 1)

    def test_unbounded_malformed_report_gate_is_left_untouched(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            materialize_inventory(project_root, manifest)
            report = project_root / "MIGRATION_REPORT.md"
            original_contents = (
                "Migration findings.\n\n"
                + gate.REPORT_START
                + "\n## Aggregate validation gate\n\n"
                "**Validation gate:** **READY**\n\n"
                "Retain these unsectioned notes.\n"
            )
            report.write_text(original_contents, encoding="utf-8")

            completed = self.run_gate(project_root)

            self.assertEqual(completed.returncode, 1)
            summary = json.loads(completed.stdout)
            self.assertEqual(summary["readiness"], "not_ready")
            self.assertEqual(report.read_text(encoding="utf-8"), original_contents)

    def test_converted_model_cannot_overwrite_source_path(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = manifest["models"][0]
            model["source_path"] = model["path"]
            materialize_inventory(project_root, manifest)

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "source and converted paths identify the same file"
                    in blocker
                    for blocker in summary["blockers"]
                )
            )

    def test_converted_model_cannot_resolve_to_source_symlink(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = manifest["models"][0]
            materialize_inventory(project_root, manifest)
            source_file = project_root / model["source_path"]
            converted_file = project_root / model["path"]
            source_file.unlink()
            source_file.symlink_to(converted_file)

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "source and converted paths identify the same file"
                    in blocker
                    for blocker in summary["blockers"]
                )
            )

    def test_converted_model_outside_project_is_not_parsed(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            workspace = Path(temporary)
            project_root = workspace / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = manifest["models"][0]
            materialize_inventory(project_root, manifest)

            converted_file = project_root / model["path"]
            converted_file.unlink()
            outside_file = workspace / "outside.xml"
            outside_file.write_text("<malformed", encoding="utf-8")
            converted_file.symlink_to(outside_file)

            parsed_paths = []
            original_reader = gate.read_converted_model
            identity_path_pairs = []
            original_identity_check = gate.paths_identify_same_file

            def record_read(path, location, error):
                parsed_paths.append(Path(path).resolve())
                return original_reader(path, location, error)

            def record_identity_check(left, right):
                identity_path_pairs.append((Path(left), Path(right)))
                return original_identity_check(left, right)

            with patch.object(
                gate, "read_converted_model", side_effect=record_read
            ), patch.object(
                gate,
                "paths_identify_same_file",
                side_effect=record_identity_check,
            ):
                summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "resolves outside the project root" in blocker
                    for blocker in summary["blockers"]
                )
            )
            self.assertNotIn(outside_file.resolve(), parsed_paths)
            self.assertFalse(
                any(
                    outside_file.resolve() in path_pair
                    for path_pair in identity_path_pairs
                )
            )

    def test_converted_models_cannot_alias_other_source_files(self):
        for alias_type in ("cycle", "hard_link"):
            with self.subTest(alias_type=alias_type):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary) / "project"
                    shutil.copytree(FIXTURE, project_root)
                    manifest = json.loads(
                        (project_root / "validation-evidence.json").read_text(
                            encoding="utf-8"
                        )
                    )
                    models = [
                        model
                        for model in manifest["models"]
                        if model["type"] == "bpmn"
                    ][:3]
                    self.assertEqual(len(models), 3)
                    materialize_inventory(project_root, manifest)

                    if alias_type == "cycle":
                        source_paths = [
                            model["source_path"] for model in models
                        ]
                        for index, model in enumerate(models):
                            model["path"] = source_paths[
                                (index + 1) % len(source_paths)
                            ]
                    else:
                        converted_file = project_root / models[0]["path"]
                        other_source_file = (
                            project_root / models[1]["source_path"]
                        )
                        converted_file.unlink()
                        os.link(other_source_file, converted_file)

                    summary = gate.validate_manifest(manifest, project_root)

                    self.assertTrue(
                        any(
                            "converted model path" in blocker
                            and "source model" in blocker
                            for blocker in summary["blockers"]
                        ),
                        "The cross-model source collision was not rejected.",
                    )

    def test_model_inventory_rejects_source_file_aliases(self):
        for alias_type in ("symlink", "hard_link"):
            with self.subTest(alias_type=alias_type):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary) / "project"
                    shutil.copytree(FIXTURE, project_root)
                    manifest = json.loads(
                        (project_root / "validation-evidence.json").read_text(
                            encoding="utf-8"
                        )
                    )
                    models = [
                        model
                        for model in manifest["models"]
                        if model["type"] == "bpmn"
                    ][:2]
                    self.assertEqual(len(models), 2)
                    materialize_inventory(project_root, manifest)

                    first_source = project_root / models[0]["source_path"]
                    aliased_source = project_root / models[1]["source_path"]
                    aliased_source.unlink()
                    if alias_type == "symlink":
                        aliased_source.symlink_to(first_source)
                    else:
                        os.link(first_source, aliased_source)
                    write_step2_inventory(
                        project_root,
                        [module["path"] for module in manifest["modules"]],
                        [model["source_path"] for model in manifest["models"]],
                    )

                    summary = gate.validate_manifest(manifest, project_root)

                    self.assertTrue(
                        any(
                            "source_path identifies the same file" in blocker
                            for blocker in summary["blockers"]
                        ),
                        "The source-file alias was not rejected.",
                    )

    def test_module_inventory_rejects_canonical_directory_aliases(self):
        aliases = ("examples/./web", "examples/web-alias")
        for alias in aliases:
            with self.subTest(alias=alias):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary)
                    module_directory = project_root / "examples/web"
                    module_directory.mkdir(parents=True)
                    if alias == "examples/web-alias":
                        (project_root / alias).symlink_to("web")
                    log_directory = project_root / LOG_DIRECTORY
                    log_directory.mkdir(parents=True)
                    (log_directory / "pass.log").write_text(
                        "check completed\n", encoding="utf-8"
                    )
                    manifest = passing_module_manifest("examples/web")
                    duplicate_module = dict(manifest["modules"][0])
                    duplicate_module["path"] = alias
                    manifest["modules"].append(duplicate_module)
                    manifest["checks"].extend(
                        dict(check, target=alias)
                        for check in list(manifest["checks"])
                        if check.get("target_type") == "module"
                    )
                    write_step2_inventory(
                        project_root,
                        ["examples/web", alias],
                        [],
                    )

                    summary = gate.validate_manifest(manifest, project_root)

                    self.assertTrue(
                        any(
                            "module path" in blocker
                            and "appears more than once after resolving the path"
                            in blocker
                            for blocker in summary["blockers"]
                        ),
                        "The module directory alias was not rejected: {}".format(
                            alias
                        ),
                    )

    def test_out_of_root_module_skips_test_suite_discovery(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            temporary_root = Path(temporary)
            project_root = temporary_root / "project"
            project_root.mkdir()
            external_module = temporary_root / "external-module"
            external_module.mkdir()
            (external_module / "build.gradle").write_text(
                'tasks.register<Test>("externalSuite") {}\n',
                encoding="utf-8",
            )
            (project_root / "service").symlink_to(
                external_module, target_is_directory=True
            )
            log_directory = project_root / LOG_DIRECTORY
            log_directory.mkdir(parents=True)
            (log_directory / "pass.log").write_text(
                "check completed\n", encoding="utf-8"
            )
            write_step2_inventory(project_root, ["service"], [])

            with patch.object(
                gate,
                "detect_module_test_suites",
                wraps=gate.detect_module_test_suites,
            ) as detect_test_suites:
                summary = gate.validate_manifest(
                    passing_module_manifest("service"),
                    project_root,
                )

            self.assertEqual(summary["readiness"], "not_ready")
            self.assertTrue(
                any(
                    "resolves outside the project root" in blocker
                    for blocker in summary["blockers"]
                ),
                summary["blockers"],
            )
            detect_test_suites.assert_not_called()

    def test_duplicate_converted_model_paths_are_normalized(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            models = [
                model
                for model in manifest["models"]
                if model["type"] == "bpmn"
            ][:2]
            models[1]["path"] = models[0]["path"].replace(
                "/", "/./", 1
            )
            materialize_inventory(project_root, manifest)

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "converted model path" in blocker
                    and "appears more than once" in blocker
                    for blocker in summary["blockers"]
                ),
                "The normalized converted-path alias was not rejected.",
            )

    def test_timer_inventory_cannot_omit_repeating_bpmn_start_events(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = next(
                model
                for model in manifest["models"]
                if model["type"] == "bpmn" and model["recurring_timer_starts"]
            )
            timer = model["recurring_timer_starts"][0]
            materialize_inventory(project_root, manifest)
            timer_target = "{}#{}#{}".format(
                model["path"], timer["process_id"], timer["id"]
            )
            manifest["checks"] = [
                check
                for check in manifest["checks"]
                if not (
                    check.get("target_type") == "timer"
                    and check.get("target") == timer_target
                    and check.get("kind") == "timer_preflight"
                )
            ]
            model["recurring_timer_starts"] = []

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "recurring_timer_starts omits converted BPMN repeating timer"
                    in blocker
                    and timer["id"] in blocker
                    for blocker in summary["blockers"]
                ),
                "The missing repeating timer inventory entry was not rejected.",
            )

    def test_timer_inventory_rejects_unknown_repeating_bpmn_start_events(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = next(
                model
                for model in manifest["models"]
                if model["type"] == "bpmn" and model["recurring_timer_starts"]
            )
            timer = model["recurring_timer_starts"][0]
            materialize_inventory(project_root, manifest)
            unknown_timer_id = "unlisted-start"
            model["recurring_timer_starts"] = [
                {
                    "process_id": timer["process_id"],
                    "id": unknown_timer_id,
                }
            ]
            timer_check = next(
                check
                for check in manifest["checks"]
                if check.get("target_type") == "timer"
                and check.get("kind") == "timer_preflight"
            )
            timer_check["target"] = "{}#{}#{}".format(
                model["path"], timer["process_id"], unknown_timer_id
            )
            timer_check.update(
                {
                    "exit_code": 0,
                    "result": "passed",
                    "evidence_path": (
                        LOG_DIRECTORY + "/evidence/message-assertion.txt"
                    ),
                    "reason": None,
                    "blocker_reason": None,
                    "failure_class": None,
                    "environment": "local",
                }
            )
            manifest["checks"].remove(timer_check)
            manifest["checks"].insert(0, timer_check)

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "recurring_timer_starts includes unknown converted BPMN "
                    "repeating timer" in blocker
                    and "unlisted-start" in blocker
                    for blocker in summary["blockers"]
                ),
                "The unknown repeating timer inventory entry was not rejected.",
            )

    def test_process_inventory_cannot_omit_bpmn_processes(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = next(
                model
                for model in manifest["models"]
                if model["type"] == "bpmn" and model["processes"]
            )
            process_id = model["processes"][0]["id"]
            materialize_inventory(project_root, manifest)
            model["processes"] = []

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "process inventory omits BPMN process {}".format(process_id)
                    in blocker
                    for blocker in summary["blockers"]
                ),
                "The omitted BPMN process was not reported.",
            )

    def test_process_executability_must_match_bpmn(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = next(
                model
                for model in manifest["models"]
                if model["type"] == "bpmn" and model["processes"]
            )
            process = model["processes"][0]
            process_id = process["id"]
            materialize_inventory(project_root, manifest)
            process.update(
                {
                    "executable": False,
                    "standalone_entry_point": False,
                    "direct_start_scenarios": [],
                    "covering_test": None,
                    "reason": "The manifest incorrectly marks this process as non-executable.",
                }
            )

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "process {} executable value does not match BPMN".format(
                        process_id
                    )
                    in blocker
                    for blocker in summary["blockers"]
                ),
                "The process executability mismatch was not reported.",
            )

    def test_declared_model_type_must_match_converted_xml(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = next(
                model
                for model in manifest["models"]
                if model["type"] == "bpmn"
                and model["processes"]
                and model["recurring_timer_starts"]
            )
            process_id = model["processes"][0]["id"]
            timer = model["recurring_timer_starts"][0]
            materialize_inventory(project_root, manifest)
            model["type"] = "dmn"
            model["processes"] = []
            model["recurring_timer_starts"] = []

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "declared type dmn does not match converted XML type bpmn"
                    in blocker
                    for blocker in summary["blockers"]
                ),
                "The converted model type mismatch was not reported.",
            )
            self.assertTrue(
                any(
                    "process inventory omits BPMN process {}".format(process_id)
                    in blocker
                    for blocker in summary["blockers"]
                ),
                "BPMN process requirements were skipped after a type mismatch.",
            )
            self.assertTrue(
                any(
                    "recurring_timer_starts omits converted BPMN repeating "
                    "timer start {} in process {}.".format(
                        timer["id"], timer["process_id"]
                    )
                    in blocker
                    for blocker in summary["blockers"]
                ),
                "BPMN timer requirements were skipped after a type mismatch.",
            )

    def test_dmn_models_reject_process_and_timer_inventories(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = manifest["models"][0]
            model["type"] = "dmn"
            model["processes"] = [
                {
                    "id": "invented-process",
                    "executable": True,
                    "standalone_entry_point": True,
                    "direct_start_scenarios": ["normal"],
                    "missing_worker_input_scenarios": [],
                    "covering_test": None,
                    "reason": None,
                    "assertion_applicability": {
                        kind: False for kind in gate.PROCESS_ASSERTIONS
                    },
                }
            ]
            model["recurring_timer_starts"] = [
                {"process_id": "invented-process", "id": "invented-timer"}
            ]
            materialize_inventory(project_root, manifest)

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "processes must be empty for DMN models" in blocker
                    for blocker in summary["blockers"]
                ),
                "A DMN process inventory was not rejected.",
            )
            self.assertTrue(
                any(
                    "recurring_timer_starts must be empty for DMN models"
                    in blocker
                    for blocker in summary["blockers"]
                ),
                "A DMN timer inventory was not rejected.",
            )

    def test_cli_rejects_output_paths_that_overwrite_inputs_or_each_other(self):
        cases = (
            (
                "summary",
                "validation-evidence.json",
                "MIGRATION_REPORT.md",
                "validation-evidence.json",
            ),
            (
                "report",
                None,
                "validation-evidence.json",
                "validation-evidence.json",
            ),
            (
                "outputs",
                "MIGRATION_REPORT.md",
                "MIGRATION_REPORT.md",
                None,
            ),
        )
        for name, summary_path, report_path, overwritten_input in cases:
            with self.subTest(name=name):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary) / "project"
                    shutil.copytree(FIXTURE, project_root)
                    manifest = json.loads(
                        (project_root / "validation-evidence.json").read_text(
                            encoding="utf-8"
                        )
                    )
                    materialize_inventory(project_root, manifest)
                    original_files = {
                        path: (project_root / path).read_bytes()
                        for path in (
                            "validation-evidence.json",
                            "MIGRATION_REPORT.md",
                        )
                    }

                    completed = self.run_gate(
                        project_root,
                        report=report_path,
                        summary=summary_path,
                    )

                    self.assertEqual(completed.returncode, 1)
                    summary = json.loads(completed.stdout)
                    self.assertTrue(
                        any(
                            "must not identify the same file" in blocker
                            for blocker in summary["blockers"]
                        ),
                        "Colliding output paths were not rejected.",
                    )
                    paths_to_check = (
                        (overwritten_input,)
                        if overwritten_input
                        else ("MIGRATION_REPORT.md",)
                    )
                    for path in paths_to_check:
                        self.assertEqual(
                            (project_root / path).read_bytes(),
                            original_files[path],
                        )

    def test_cli_rejects_hard_link_output_aliases(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            materialize_inventory(project_root, manifest)
            evidence_file = project_root / "validation-evidence.json"
            summary_alias = (
                project_root
                / ".camunda-migration/validation/evidence-alias.json"
            )
            os.link(evidence_file, summary_alias)
            original_evidence = evidence_file.read_bytes()

            completed = self.run_gate(
                project_root,
                summary=".camunda-migration/validation/evidence-alias.json",
            )

            self.assertEqual(completed.returncode, 1)
            summary = json.loads(completed.stdout)
            self.assertTrue(
                any(
                    "must not identify the same file" in blocker
                    for blocker in summary["blockers"]
                ),
                "The hard-linked output alias was not rejected.",
            )
            self.assertEqual(evidence_file.read_bytes(), original_evidence)

    def test_cli_rejects_outputs_that_alias_reserved_files(self):
        cases = (
            ("summary", STEP2_INVENTORY_PATH, False),
            ("summary", STEP2_INVENTORY_PATH, True),
            ("summary", gate.DEFAULT_EVIDENCE_PATH, False),
            ("summary", gate.DEFAULT_EVIDENCE_PATH, True),
            ("summary", gate.DEFAULT_SUMMARY_PATH, True),
            ("summary", "MIGRATION_REPORT.md", False),
            ("summary", "MIGRATION_REPORT.md", True),
            ("report", STEP2_INVENTORY_PATH, False),
            ("report", STEP2_INVENTORY_PATH, True),
            ("report", gate.DEFAULT_SUMMARY_PATH, False),
            ("report", gate.DEFAULT_SUMMARY_PATH, True),
        )
        for output_kind, reserved_path, hard_link in cases:
            with self.subTest(
                output_kind=output_kind,
                reserved_path=reserved_path,
                hard_link=hard_link,
            ):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary) / "project"
                    shutil.copytree(FIXTURE, project_root)
                    manifest = json.loads(
                        (project_root / "validation-evidence.json").read_text(
                            encoding="utf-8"
                        )
                    )
                    materialize_inventory(project_root, manifest)
                    protected_file = project_root / reserved_path
                    protected_file.parent.mkdir(parents=True, exist_ok=True)
                    if not protected_file.exists():
                        protected_file.write_bytes(b"reserved validation file\n")
                    output_file = protected_file
                    if hard_link:
                        output_file = (
                            project_root
                            / ".camunda-migration/validation/reserved-alias"
                        )
                        output_file.parent.mkdir(parents=True, exist_ok=True)
                        os.link(protected_file, output_file)
                    original_contents = protected_file.read_bytes()
                    output_path = output_file.relative_to(
                        project_root
                    ).as_posix()

                    if output_kind == "summary":
                        completed = self.run_gate(
                            project_root,
                            report=None,
                            summary=output_path,
                        )
                    else:
                        summary_path = (
                            "custom-validation-summary.json"
                            if reserved_path == gate.DEFAULT_SUMMARY_PATH
                            else None
                        )
                        completed = self.run_gate(
                            project_root,
                            report=output_path,
                            summary=summary_path,
                        )

                    self.assertEqual(completed.returncode, 1)
                    self.assertEqual(
                        protected_file.read_bytes(), original_contents
                    )
                    summary = json.loads(completed.stdout)
                    self.assertTrue(
                        any(
                            "must not identify the same file" in blocker
                            for blocker in summary["blockers"]
                        ),
                        "An output alias of a reserved file was not rejected.",
                    )

    def test_cli_rejects_outputs_that_alias_evidence_files(self):
        cases = (("summary", False), ("report", True))
        for output_kind, hard_link in cases:
            with self.subTest(output_kind=output_kind, hard_link=hard_link):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary) / "project"
                    shutil.copytree(FIXTURE, project_root)
                    manifest = json.loads(
                        (project_root / "validation-evidence.json").read_text(
                            encoding="utf-8"
                        )
                    )
                    materialize_inventory(project_root, manifest)
                    test_check = next(
                        check
                        for check in manifest["checks"]
                        if check.get("kind") == "tests"
                        and check.get("evidence_path")
                    )
                    evidence_file = project_root / test_check["evidence_path"]
                    original_evidence = evidence_file.read_bytes()
                    output_file = evidence_file
                    if hard_link:
                        output_file = (
                            evidence_file.parent / "report-alias.log"
                        )
                        os.link(evidence_file, output_file)
                    output_path = output_file.relative_to(
                        project_root
                    ).as_posix()

                    if output_kind == "summary":
                        completed = self.run_gate(
                            project_root,
                            report=None,
                            summary=output_path,
                        )
                    else:
                        completed = self.run_gate(
                            project_root,
                            report=output_path,
                            summary=(
                                ".camunda-migration/validation/"
                                "custom-summary.json"
                            ),
                        )

                    self.assertEqual(completed.returncode, 1)
                    summary = json.loads(completed.stdout)
                    self.assertEqual(
                        evidence_file.read_bytes(), original_evidence
                    )
                    self.assertTrue(
                        any(
                            "path must not identify an evidence file"
                            in blocker
                            for blocker in summary["blockers"]
                        ),
                        "An output alias of a manifest evidence file was accepted.",
                    )

    def test_cli_reports_looped_input_symlinks_as_not_ready(self):
        for input_kind in ("module", "model", "evidence"):
            with self.subTest(input_kind=input_kind):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary) / "project"
                    shutil.copytree(FIXTURE, project_root)
                    manifest_path = project_root / "validation-evidence.json"
                    manifest = json.loads(
                        manifest_path.read_text(encoding="utf-8")
                    )
                    materialize_inventory(project_root, manifest)

                    if input_kind == "module":
                        manifest["modules"][0]["path"] = "looped-module"
                        looped_path = project_root / "looped-module"
                    elif input_kind == "model":
                        manifest["models"][0]["path"] = "models/looped.bpmn"
                        looped_path = project_root / "models/looped.bpmn"
                    else:
                        check = next(
                            check
                            for check in manifest["checks"]
                            if check.get("evidence_path")
                        )
                        looped_path = (
                            project_root / check["evidence_path"]
                        )
                        looped_path.unlink()

                    looped_path = project_root.resolve() / (
                        looped_path.relative_to(project_root)
                    )
                    looped_path.parent.mkdir(parents=True, exist_ok=True)
                    looped_path.symlink_to(looped_path.name)
                    manifest_path.write_text(
                        json.dumps(manifest), encoding="utf-8"
                    )

                    original_resolve = Path.resolve

                    def resolve_with_loop_error(path, *args, **kwargs):
                        if path == looped_path:
                            raise RuntimeError("Symlink loop")
                        return original_resolve(path, *args, **kwargs)

                    output = StringIO()
                    argv = [
                        str(SCRIPT),
                        "--project-root",
                        str(project_root),
                        "--evidence",
                        "validation-evidence.json",
                        "--report",
                        "MIGRATION_REPORT.md",
                    ]
                    with patch.object(
                        Path, "resolve", new=resolve_with_loop_error
                    ):
                        with patch.object(sys, "argv", argv):
                            with redirect_stdout(output):
                                exit_code = gate.main()

                    self.assertEqual(exit_code, 1)
                    summary = json.loads(output.getvalue())
                    self.assertEqual(summary["readiness"], "not_ready")
                    summary_file = (
                        project_root / gate.DEFAULT_SUMMARY_PATH
                    )
                    self.assertEqual(
                        json.loads(summary_file.read_text(encoding="utf-8"))[
                            "readiness"
                        ],
                        "not_ready",
                    )
                    self.assertIn(
                        "<!-- migration-validation-gate:start -->",
                        (project_root / "MIGRATION_REPORT.md").read_text(
                            encoding="utf-8"
                        ),
                    )

    def test_process_assertions_cannot_be_waived_when_applicable(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            process = manifest["models"][0]["processes"][0]
            process["assertion_applicability"] = {
                kind: True for kind in gate.PROCESS_ASSERTIONS
            }
            process_target = "{}#{}".format(
                manifest["models"][0]["path"], process["id"]
            )
            for kind in gate.PROCESS_ASSERTIONS:
                check = next(
                    (
                        check
                        for check in manifest["checks"]
                        if check.get("target_type") == "process"
                        and check.get("target") == process_target
                        and check.get("kind") == kind
                    ),
                    None,
                )
                if check is None:
                    check = not_applicable_check(
                        "process",
                        process_target,
                        kind,
                        "The test manifest incorrectly waives this assertion.",
                    )
                    manifest["checks"].append(check)
                check.update(
                    {
                        "method": "not_applicable",
                        "command": "N/A: test marks this assertion as applicable",
                        "exit_code": None,
                        "result": "not_applicable",
                        "evidence_path": None,
                        "reason": "The manifest incorrectly waives this assertion.",
                        "blocker_reason": None,
                        "failure_class": None,
                        "environment": None,
                    }
                )
            materialize_inventory(project_root, manifest)

            summary = gate.validate_manifest(manifest, project_root)

            for kind in gate.PROCESS_ASSERTIONS:
                assertion_label = "process {} / {}".format(
                    process_target, kind
                )
                self.assertTrue(
                    any(
                        assertion_label in blocker
                        and "must pass for this target" in blocker
                        for blocker in summary["blockers"]
                    ),
                    "{} was waived despite being applicable.".format(kind),
                )

    def test_form_checks_cannot_be_waived_when_form_inventory_applies(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = manifest["models"][0]
            model["form_inventory"] = [
                {
                    "id": "order-form",
                    "kind": "referenced",
                    "accepted": True,
                    "schema_applicable": True,
                    "form_js_applicable": True,
                    "binding_required": True,
                }
            ]
            manifest["checks"] = [
                check
                for check in manifest["checks"]
                if not (
                    check.get("target_type") == "model"
                    and check.get("target") == model["path"]
                    and check.get("kind") in FORM_CHECKS
                )
            ]
            for kind in FORM_CHECKS:
                manifest["checks"].append(
                    not_applicable_check(
                        "model",
                        model["path"],
                        kind,
                        "The manifest incorrectly waives this form check.",
                    )
                )
            materialize_inventory(project_root, manifest)
            write_source_bpmn_with_owner(
                project_root / model["source_path"],
                model["processes"][0]["id"],
                "referenced",
                "order-form",
            )

            summary = gate.validate_manifest(manifest, project_root)

            for kind in FORM_CHECKS:
                check_label = "model {} / {}".format(model["path"], kind)
                self.assertTrue(
                    any(
                        check_label in blocker
                        and "must pass for this target" in blocker
                        for blocker in summary["blockers"]
                    ),
                    "{} was waived despite its form inventory.".format(kind),
                )

    def test_source_form_inventory_cannot_omit_detected_records(self):
        for form_kind in ("generated", "referenced", "form-free-owner"):
            with self.subTest(form_kind=form_kind):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary) / "project"
                    shutil.copytree(FIXTURE, project_root)
                    manifest = json.loads(
                        (project_root / "validation-evidence.json").read_text(
                            encoding="utf-8"
                        )
                    )
                    model = manifest["models"][0]
                    materialize_inventory(project_root, manifest)
                    write_source_bpmn_with_owner(
                        project_root / model["source_path"],
                        model["processes"][0]["id"],
                        form_kind,
                    )

                    summary = gate.validate_manifest(manifest, project_root)

                    self.assertTrue(
                        any(
                            "source form inventory" in blocker.lower()
                            and form_kind in blocker.lower()
                            for blocker in summary["blockers"]
                        ),
                        "The source inventory omitted {}.".format(form_kind),
                    )

    def test_source_form_inventory_requires_matching_source_identities(self):
        form_inventory_cases = (
            ("generated", "Task_FormOwner", "wrong-owner", None),
            ("referenced", "order-form", "wrong-form", "order-form"),
            ("form-free-owner", "Task_FormOwner", "wrong-owner", None),
        )
        for (
            form_kind,
            source_id,
            declared_id,
            source_reference,
        ) in form_inventory_cases:
            with self.subTest(form_kind=form_kind):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary) / "project"
                    shutil.copytree(FIXTURE, project_root)
                    manifest = json.loads(
                        (project_root / "validation-evidence.json").read_text(
                            encoding="utf-8"
                        )
                    )
                    model = manifest["models"][0]
                    model["form_inventory"] = [
                        {
                            "id": source_id,
                            "kind": form_kind,
                            "accepted": False,
                            "schema_applicable": False,
                            "form_js_applicable": False,
                            "binding_required": False,
                        }
                    ]
                    materialize_inventory(project_root, manifest)
                    write_source_bpmn_with_owner(
                        project_root / model["source_path"],
                        model["processes"][0]["id"],
                        form_kind,
                        source_reference,
                    )

                    summary = gate.validate_manifest(manifest, project_root)
                    self.assertFalse(
                        any(
                            "source form inventory identities do not match"
                            in blocker.lower()
                            for blocker in summary["blockers"]
                        ),
                        summary["blockers"],
                    )

                    model["form_inventory"][0]["id"] = declared_id
                    summary = gate.validate_manifest(manifest, project_root)

                    self.assertTrue(
                        any(
                            "source form inventory identities do not match"
                            in blocker.lower()
                            for blocker in summary["blockers"]
                        ),
                        summary["blockers"],
                    )

    def test_form_free_owner_requires_form_reference_evidence(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = manifest["models"][0]
            model["form_inventory"] = [
                {
                    "id": "Task_FormOwner",
                    "kind": "form-free-owner",
                    "accepted": False,
                    "schema_applicable": False,
                    "form_js_applicable": False,
                    "binding_required": False,
                }
            ]
            manifest["checks"] = [
                check
                for check in manifest["checks"]
                if not (
                    check.get("target_type") == "model"
                    and check.get("target") == model["path"]
                    and check.get("kind") == "form_references"
                )
            ]
            manifest["checks"].append(
                not_applicable_check(
                    "model",
                    model["path"],
                    "form_references",
                    "The manifest incorrectly waives this form-free owner.",
                )
            )
            materialize_inventory(project_root, manifest)
            write_source_bpmn_with_owner(
                project_root / model["source_path"],
                model["processes"][0]["id"],
                "form-free-owner",
            )

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "model {} / form_references".format(model["path"]) in blocker
                    and "must pass for this target" in blocker
                    for blocker in summary["blockers"]
                )
            )

    def test_non_standalone_process_requires_coverage_not_direct_start(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = next(
                model
                for model in manifest["models"]
                if model["source_path"] == "models/account.bpmn"
            )
            process = model["processes"][0]
            process["standalone_entry_point"] = False
            process["direct_start_scenarios"] = []
            process["covering_test"] = "account-flow-integration"
            process["reason"] = "Started by the parent process."
            model["form_inventory"] = []
            process_target = "{}#{}".format(model["path"], process["id"])
            manifest["checks"] = [
                check
                for check in manifest["checks"]
                if not (
                    check.get("target_type") == "process"
                    and check.get("target") == process_target
                    and check.get("kind")
                    in {"direct_start", "worker_input_inventory"}
                )
            ]
            materialize_inventory(project_root, manifest)

            summary = gate.validate_manifest(manifest, project_root)

            self.assertFalse(
                any(
                    "direct_start (not-standalone)" in blocker
                    and process_target in blocker
                    for blocker in summary["blockers"]
                )
            )
            self.assertTrue(
                any(
                    "process_coverage" in blocker
                    and process_target in blocker
                    for blocker in summary["blockers"]
                )
            )

    def test_standalone_process_requires_worker_input_inventory_evidence(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = manifest["models"][0]
            process = model["processes"][0]
            process["missing_worker_input_scenarios"] = []
            materialize_inventory(project_root, manifest)

            summary = gate.validate_manifest(manifest, project_root)

            inventory_label = "process {}#{} / worker_input_inventory".format(
                model["path"], process["id"]
            )
            self.assertTrue(
                any(
                    inventory_label in blocker
                    and ("not_run" in blocker or "must pass" in blocker)
                    for blocker in summary["blockers"]
                ),
                "A standalone process passed without completed worker-input inventory evidence.",
            )

    def test_standalone_process_requires_each_inventoried_missing_input_scenario(
        self,
    ):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            model = manifest["models"][0]
            process = model["processes"][0]
            process["missing_worker_input_scenarios"] = [
                "missing-customer-id"
            ]
            process_target = "{}#{}".format(model["path"], process["id"])
            inventory_evidence = (
                LOG_DIRECTORY + "/order-worker-input-inventory.log"
            )
            inventory_file = project_root / inventory_evidence
            inventory_file.parent.mkdir(parents=True, exist_ok=True)
            inventory_file.write_text(
                "Reviewed worker input mappings and implementations. "
                "missing-customer-id omits customerId.\n",
                encoding="utf-8",
            )
            inventory_check = passing_check(
                "process",
                process_target,
                "worker_input_inventory",
                inventory_evidence,
            )
            inventory_check.update(
                {
                    "method": "manual",
                    "command": "Manual review of BPMN worker inputs.",
                    "exit_code": None,
                }
            )
            manifest["checks"] = [
                check
                for check in manifest["checks"]
                if not (
                    check.get("target_type") == "process"
                    and check.get("target") == process_target
                    and check.get("kind") == "worker_input_inventory"
                )
            ]
            manifest["checks"].append(inventory_check)
            materialize_inventory(project_root, manifest)

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "direct_start_scenarios must include normal and "
                    "every worker-input inventory scenario." in blocker
                    and "processes[0]" in blocker
                    for blocker in summary["blockers"]
                ),
                "The process omitted an inventoried missing-input scenario.",
            )
            self.assertTrue(
                any(
                    "Missing required evidence for process {} / direct_start "
                    "(missing-customer-id)".format(process_target) in blocker
                    for blocker in summary["blockers"]
                ),
                "No direct-start check was derived from the independent worker-input inventory.",
            )

    def test_step2_inventory_prevents_omitted_modules_and_models(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary)
            for module_path in ("service", "omitted-module"):
                (project_root / module_path).mkdir()
            (project_root / "models").mkdir()
            (project_root / "models/omitted.bpmn").write_text(
                "source model\n", encoding="utf-8"
            )
            (project_root / LOG_DIRECTORY).mkdir(parents=True)
            (project_root / LOG_DIRECTORY / "pass.log").write_text(
                "check completed\n", encoding="utf-8"
            )
            manifest = passing_module_manifest("service")
            write_step2_inventory(
                project_root,
                ["service", "omitted-module"],
                ["models/omitted.bpmn"],
            )

            summary = gate.validate_manifest(manifest, project_root)

            self.assertEqual(summary["readiness"], "not_ready")
            self.assertTrue(
                any(
                    "Step 2 module inventory" in blocker
                    and "omitted-module" in blocker
                    for blocker in summary["blockers"]
                )
            )
            self.assertTrue(
                any(
                    "Step 2 model inventory" in blocker
                    and "models/omitted.bpmn" in blocker
                    for blocker in summary["blockers"]
                )
            )

    def test_step2_inventory_is_required(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            inventory_path = project_root / STEP2_INVENTORY_PATH
            inventory_path.unlink()
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            materialize_inventory(project_root, manifest)

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "Step 2 inventory" in blocker
                    and "missing" in blocker
                    for blocker in summary["blockers"]
                )
            )

    def test_executable_check_cannot_pass_with_manual_evidence(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            lint_check = next(
                check
                for check in manifest["checks"]
                if check.get("target_type") == "model"
                and check.get("kind") == "lint"
            )
            lint_check.update(
                {
                    "method": "manual",
                    "command": "Manual lint note",
                    "exit_code": None,
                }
            )

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "cannot use manual method for model lint" in blocker
                    for blocker in summary["blockers"]
                )
            )

    def test_evidence_path_must_be_a_validation_log(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            check = next(
                check
                for check in manifest["checks"]
                if check.get("result") == "passed"
            )
            check["evidence_path"] = "MIGRATION_REPORT.md"

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "evidence_path must be inside {}".format(LOG_DIRECTORY)
                    in blocker
                    for blocker in summary["blockers"]
                )
            )

    def test_manifest_and_report_cannot_be_used_as_evidence(self):
        for generated_file in ("manifest", "report"):
            with self.subTest(generated_file=generated_file):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary) / "project"
                    shutil.copytree(FIXTURE, project_root)
                    manifest = json.loads(
                        (project_root / "validation-evidence.json").read_text(
                            encoding="utf-8"
                        )
                    )
                    check = next(
                        check
                        for check in manifest["checks"]
                        if check.get("result") == "passed"
                    )
                    if generated_file == "manifest":
                        evidence_path = (
                            LOG_DIRECTORY + "/validation-evidence.json"
                        )
                        check["evidence_path"] = evidence_path
                        manifest_file = project_root / evidence_path
                        manifest_file.parent.mkdir(parents=True, exist_ok=True)
                        manifest_file.write_text(
                            json.dumps(manifest), encoding="utf-8"
                        )
                        completed = self.run_gate(
                            project_root, evidence=evidence_path
                        )
                    else:
                        evidence_path = LOG_DIRECTORY + "/generated-report.md"
                        check["evidence_path"] = evidence_path
                        evidence_file = project_root / "validation-evidence.json"
                        evidence_file.write_text(
                            json.dumps(manifest), encoding="utf-8"
                        )
                        completed = self.run_gate(
                            project_root,
                            report=evidence_path,
                        )

                    summary = json.loads(completed.stdout)
                    self.assertEqual(completed.returncode, 1)
                    self.assertTrue(
                        any(
                            "cannot reference a generated validation file"
                            in blocker
                            for blocker in summary["blockers"]
                        )
                    )

    def test_hard_links_to_generated_validation_files_cannot_be_used_as_evidence(self):
        generated_files = {
            "manifest": gate.DEFAULT_EVIDENCE_PATH,
            "inventory": gate.DEFAULT_INVENTORY_PATH,
            "summary": gate.DEFAULT_SUMMARY_PATH,
            "report": gate.DEFAULT_REPORT_PATH,
        }
        for name, generated_path in generated_files.items():
            with self.subTest(generated_file=name):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary) / "project"
                    shutil.copytree(FIXTURE, project_root)
                    manifest = json.loads(
                        (project_root / "validation-evidence.json").read_text(
                            encoding="utf-8"
                        )
                    )
                    materialize_inventory(project_root, manifest)
                    write_step2_inventory(
                        project_root,
                        [module["path"] for module in manifest["modules"]],
                        [
                            model["source_path"]
                            for model in manifest["models"]
                        ],
                    )
                    check = next(
                        check
                        for check in manifest["checks"]
                        if check.get("result") == "passed"
                    )
                    evidence_path = (
                        LOG_DIRECTORY + "/hard-link-{}.log".format(name)
                    )
                    check["evidence_path"] = evidence_path

                    generated_file = project_root / generated_path
                    generated_file.parent.mkdir(parents=True, exist_ok=True)
                    if name == "manifest":
                        generated_file.write_text(
                            json.dumps(manifest), encoding="utf-8"
                        )
                    elif name == "summary":
                        generated_file.write_text(
                            "generated validation summary\n", encoding="utf-8"
                        )
                    evidence_file = project_root / evidence_path
                    evidence_file.parent.mkdir(parents=True, exist_ok=True)
                    os.link(generated_file, evidence_file)

                    summary = gate.validate_manifest(manifest, project_root)

                    self.assertTrue(
                        any(
                            "cannot reference a generated validation file"
                            in blocker
                            for blocker in summary["blockers"]
                        ),
                        summary["blockers"],
                    )

    def test_timer_preflight_requires_an_isolation_or_cleanup_plan(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            manifest = json.loads(
                (project_root / "validation-evidence.json").read_text(
                    encoding="utf-8"
                )
            )
            timer_check = next(
                check
                for check in manifest["checks"]
                if check.get("kind") == "timer_preflight"
            )
            timer_check.pop("isolation_or_cleanup_plan", None)
            timer_check.update(
                {
                    "method": "command",
                    "command": "Run the repeating timer preflight",
                    "exit_code": 0,
                    "result": "passed",
                    "evidence_path": (
                        LOG_DIRECTORY + "/evidence/message-assertion.txt"
                    ),
                    "reason": None,
                    "blocker_reason": None,
                    "failure_class": None,
                    "environment": "local",
                }
            )

            summary = gate.validate_manifest(manifest, project_root)

            self.assertTrue(
                any(
                    "timer_preflight needs a non-empty "
                    "isolation_or_cleanup_plan" in blocker
                    for blocker in summary["blockers"]
                )
            )

    def test_module_gate_passes_only_with_executed_tests(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary)
            (project_root / "service").mkdir()
            write_unit_suite_configuration(project_root, "service")
            (project_root / LOG_DIRECTORY).mkdir(parents=True)
            (project_root / LOG_DIRECTORY / "pass.log").write_text(
                "check completed\n", encoding="utf-8"
            )
            module_path = "service"
            checks = []
            for kind in gate.MODULE_CHECKS:
                if kind in {
                    "spring_boot_run",
                    "executable_jar",
                    "external_launcher",
                }:
                    checks.append(
                        not_applicable_check(
                            "module",
                            module_path,
                            kind,
                            "The module has no runtime entry point.",
                        )
                    )
                else:
                    checks.append(
                        passing_check(
                            "module",
                            module_path,
                            kind,
                            LOG_DIRECTORY + "/pass.log",
                        )
                    )
            checks.append(
                passing_check(
                    "module",
                    module_path,
                    "tests",
                    LOG_DIRECTORY + "/pass.log",
                    "unit",
                )
            )
            manifest = {
                "schema_version": 1,
                "mode": "migration",
                "modules": [
                    {
                        "path": module_path,
                        "runtime_mode": "none",
                        "test_suites": [
                            {"name": "unit", "requires_docker": False}
                        ],
                    }
                ],
                "models": [],
                "checks": checks,
            }
            write_step2_inventory(project_root, [module_path], [])
            evidence = project_root / "validation-evidence.json"
            evidence.write_text(json.dumps(manifest), encoding="utf-8")
            report = project_root / "MIGRATION_REPORT.md"
            report.write_text("# Migration report\n", encoding="utf-8")

            completed = self.run_gate(project_root, evidence.name)

            self.assertEqual(completed.returncode, 0, completed.stdout)
            self.assertEqual(json.loads(completed.stdout)["readiness"], "ready")
            self.assertIn(
                "**Validation gate:** **READY**",
                report.read_text(encoding="utf-8"),
            )

            manual_check = next(
                check
                for check in checks
                if check.get("kind") == "migration_todos"
            )
            manual_check.update(
                {
                    "method": "manual",
                    "command": "Review migration TODOs",
                    "exit_code": None,
                }
            )
            evidence.write_text(json.dumps(manifest), encoding="utf-8")
            completed = self.run_gate(project_root, evidence.name)

            self.assertEqual(completed.returncode, 0, completed.stdout)

            manifest["modules"][0]["test_suites"].append(
                {"name": "integration", "requires_docker": False}
            )
            checks.append(
                passing_check(
                    "module",
                    module_path,
                    "tests",
                    LOG_DIRECTORY + "/pass.log",
                    "integration",
                )
            )
            evidence.write_text(json.dumps(manifest), encoding="utf-8")
            completed = self.run_gate(project_root, evidence.name)

            self.assertEqual(completed.returncode, 1)
            summary = json.loads(completed.stdout)
            self.assertTrue(
                any(
                    "test suites must use distinct evidence files" in blocker
                    for blocker in summary["blockers"]
                )
            )

            integration_check = checks[-1]
            integration_check["evidence_path"] = (
                LOG_DIRECTORY + "/integration.log"
            )
            (project_root / LOG_DIRECTORY / "integration.log").write_text(
                "integration suite output\n", encoding="utf-8"
            )
            evidence.write_text(json.dumps(manifest), encoding="utf-8")
            completed = self.run_gate(project_root, evidence.name)

            self.assertEqual(completed.returncode, 1)
            summary = json.loads(completed.stdout)
            self.assertTrue(
                any(
                    "test suites must use distinct commands" in blocker
                    for blocker in summary["blockers"]
                )
            )

            manifest["modules"][0]["test_suites"] = []
            manifest["checks"] = [
                check
                for check in manifest["checks"]
                if not (check["kind"] == "tests")
            ]
            manifest["checks"].append(
                passing_check(
                    "module",
                    module_path,
                    "tests",
                    LOG_DIRECTORY + "/pass.log",
                    "no-tests",
                )
            )
            evidence.write_text(json.dumps(manifest), encoding="utf-8")
            completed = self.run_gate(project_root, evidence.name)

            self.assertEqual(completed.returncode, 1)
            summary = json.loads(completed.stdout)
            self.assertEqual(summary["readiness"], "not_ready")
            self.assertTrue(
                any(
                    "cannot pass when no test suite exists" in blocker
                    for blocker in summary["blockers"]
                )
            )

    def test_none_runtime_mode_rejects_detectable_entry_points(self):
        for entry_point in (
            "spring_boot_source",
            "spring_boot_qualified_annotation",
            "spring_boot_qualified_run",
            "java_main",
            "kotlin_main",
            "scala_main",
            "executable_jar",
            "pom_main_class",
        ):
            with self.subTest(entry_point=entry_point):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary)
                    module_path = "service"
                    module_root = project_root / module_path
                    module_root.mkdir()
                    log_directory = project_root / LOG_DIRECTORY
                    log_directory.mkdir(parents=True)
                    (log_directory / "pass.log").write_text(
                        "check completed\n", encoding="utf-8"
                    )

                    if entry_point.startswith("spring_boot"):
                        source = (
                            module_root
                            / "src/main/java/com/example/Application.java"
                        )
                        source.parent.mkdir(parents=True)
                        spring_boot_sources = {
                            "spring_boot_source": (
                                "@SpringBootApplication\n"
                                "class Application {}\n"
                            ),
                            "spring_boot_qualified_annotation": (
                                "@org.springframework.boot.autoconfigure."
                                "SpringBootApplication\n"
                                "class Application {}\n"
                            ),
                            "spring_boot_qualified_run": (
                                "org.springframework.boot.SpringApplication."
                                "run(Application.class, args);\n"
                            ),
                        }
                        source.write_text(
                            spring_boot_sources[entry_point],
                            encoding="utf-8",
                        )
                    elif entry_point == "java_main":
                        source = (
                            module_root
                            / "src/main/java/com/example/Application.java"
                        )
                        source.parent.mkdir(parents=True)
                        source.write_text(
                            "class Application {\n"
                            "  public static void main(String[] args) {}\n"
                            "}\n",
                            encoding="utf-8",
                        )
                    elif entry_point == "kotlin_main":
                        source = (
                            module_root
                            / "src/main/kotlin/com/example/Application.kt"
                        )
                        source.parent.mkdir(parents=True)
                        source.write_text(
                            "fun main(args: Array<String>) {}\n",
                            encoding="utf-8",
                        )
                    elif entry_point == "scala_main":
                        source = (
                            module_root
                            / "src/main/scala/com/example/Application.scala"
                        )
                        source.parent.mkdir(parents=True)
                        source.write_text(
                            "object Application extends App {}\n",
                            encoding="utf-8",
                        )
                    elif entry_point == "executable_jar":
                        jar_path = module_root / "target/service.jar"
                        jar_path.parent.mkdir(parents=True)
                        with zipfile.ZipFile(jar_path, "w") as archive:
                            archive.writestr(
                                "META-INF/MANIFEST.MF",
                                "Manifest-Version: 1.0\n"
                                "Main-Class: "
                                "org.springframework.boot.loader.launch.JarLauncher\n"
                                "Start-Class: com.example.Application\n\n",
                            )
                    else:
                        (module_root / "pom.xml").write_text(
                            "<project><build><plugins><plugin>"
                            "<configuration><mainClass>"
                            "com.example.Application"
                            "</mainClass></configuration>"
                            "</plugin></plugins></build></project>",
                            encoding="utf-8",
                        )

                    write_step2_inventory(project_root, [module_path], [])
                    summary = gate.validate_manifest(
                        passing_module_manifest(module_path),
                        project_root,
                    )

                    self.assertEqual(
                        summary["readiness"],
                        "not_ready",
                        summary["blockers"],
                    )
                    self.assertTrue(
                        any(
                            "runtime_mode none conflicts with detected "
                            "runtime entry point" in blocker
                            for blocker in summary["blockers"]
                        ),
                        summary["blockers"],
                    )

    def test_none_runtime_mode_does_not_read_out_of_root_symlink_entry_points(
        self,
    ):
        for entry_point in ("pom.xml", "source", "jar"):
            with self.subTest(entry_point=entry_point):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    temporary_root = Path(temporary)
                    project_root = temporary_root / "project"
                    module_path = "service"
                    module_root = project_root / module_path
                    module_root.mkdir(parents=True)
                    log_directory = project_root / LOG_DIRECTORY
                    log_directory.mkdir(parents=True)
                    (log_directory / "pass.log").write_text(
                        "check completed\n", encoding="utf-8"
                    )

                    if entry_point == "pom.xml":
                        external_path = temporary_root / "external-pom.xml"
                        external_path.write_text(
                            "<project><build><mainClass>"
                            "com.example.Application"
                            "</mainClass></build></project>",
                            encoding="utf-8",
                        )
                        (module_root / "pom.xml").symlink_to(external_path)
                    elif entry_point == "source":
                        external_path = temporary_root / "External.java"
                        external_path.write_text(
                            "class Application {\n"
                            "  public static void main(String[] args) {}\n"
                            "}\n",
                            encoding="utf-8",
                        )
                        source_path = (
                            module_root
                            / "src/main/java/com/example/Application.java"
                        )
                        source_path.parent.mkdir(parents=True)
                        source_path.symlink_to(external_path)
                    else:
                        external_path = temporary_root / "external.jar"
                        with zipfile.ZipFile(external_path, "w") as archive:
                            archive.writestr(
                                "META-INF/MANIFEST.MF",
                                "Manifest-Version: 1.0\n"
                                "Main-Class: "
                                "org.springframework.boot.loader.launch.JarLauncher\n"
                                "Start-Class: com.example.Application\n\n",
                            )
                        jar_path = module_root / "target/service.jar"
                        jar_path.parent.mkdir(parents=True)
                        jar_path.symlink_to(external_path)

                    write_step2_inventory(project_root, [module_path], [])
                    summary = gate.validate_manifest(
                        passing_module_manifest(module_path),
                        project_root,
                    )

                    self.assertEqual(summary["readiness"], "not_ready")
                    self.assertTrue(
                        any(
                            "resolves outside the project root" in blocker
                            for blocker in summary["blockers"]
                        ),
                        summary["blockers"],
                    )
                    self.assertFalse(
                        any(
                            "runtime_mode none conflicts with detected "
                            "runtime entry point" in blocker
                            for blocker in summary["blockers"]
                        ),
                        summary["blockers"],
                    )

    def test_test_suites_reject_shared_evidence_and_commands_across_modules(
        self,
    ):
        for duplicate_field, blocker_fragment in (
            ("evidence_path", "test suites must use distinct evidence files"),
            ("command", "test suites must use distinct commands"),
        ):
            with self.subTest(duplicate_field=duplicate_field):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary)
                    log_directory = project_root / LOG_DIRECTORY
                    log_directory.mkdir(parents=True)
                    (log_directory / "pass.log").write_text(
                        "check completed\n", encoding="utf-8"
                    )

                    module_paths = ("service-a", "service-b")
                    modules = []
                    checks = []
                    test_checks = []
                    for module_path in module_paths:
                        (project_root / module_path).mkdir()
                        write_unit_suite_configuration(project_root, module_path)
                        unit_log_path = log_directory / "{}-unit.log".format(
                            module_path
                        )
                        unit_log_path.write_text(
                            "test suite completed\n", encoding="utf-8"
                        )
                        module_manifest = passing_module_manifest(module_path)
                        test_check = next(
                            check
                            for check in module_manifest["checks"]
                            if check["kind"] == "tests"
                        )
                        test_check["command"] = "Run unit suite for {}".format(
                            module_path
                        )
                        test_check["evidence_path"] = (
                            LOG_DIRECTORY + "/{}-unit.log".format(module_path)
                        )
                        test_checks.append(test_check)
                        modules.extend(module_manifest["modules"])
                        checks.extend(module_manifest["checks"])

                    write_step2_inventory(project_root, module_paths, [])
                    manifest = {
                        "schema_version": 1,
                        "mode": "migration",
                        "modules": modules,
                        "models": [],
                        "checks": checks,
                    }
                    baseline = gate.validate_manifest(manifest, project_root)
                    self.assertEqual(
                        baseline["readiness"],
                        "ready",
                        baseline["blockers"],
                    )

                    test_checks[1][duplicate_field] = test_checks[0][
                        duplicate_field
                    ]
                    summary = gate.validate_manifest(manifest, project_root)

                    self.assertEqual(
                        summary["readiness"],
                        "not_ready",
                        summary["blockers"],
                    )
                    self.assertTrue(
                        any(
                            blocker_fragment in blocker.lower()
                            for blocker in summary["blockers"]
                        ),
                        summary["blockers"],
                    )

    def test_spring_boot_runtime_accepts_either_launch_strategy(self):
        for launch_kind in ("spring_boot_run", "executable_jar"):
            with self.subTest(launch_kind=launch_kind):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary)
                    module_path = "service"
                    (project_root / module_path).mkdir()
                    write_unit_suite_configuration(project_root, module_path)
                    log_directory = project_root / LOG_DIRECTORY
                    log_directory.mkdir(parents=True)
                    (log_directory / "pass.log").write_text(
                        "check completed\n", encoding="utf-8"
                    )
                    manifest = passing_module_manifest(module_path)
                    manifest["modules"][0]["runtime_mode"] = "spring-boot"
                    for index, check in enumerate(manifest["checks"]):
                        if check.get("kind") == launch_kind:
                            manifest["checks"][index] = passing_check(
                                "module",
                                module_path,
                                launch_kind,
                                LOG_DIRECTORY + "/pass.log",
                                environment="local",
                            )
                        elif check.get("kind") in {
                            "spring_boot_run",
                            "executable_jar",
                            "external_launcher",
                        }:
                            manifest["checks"][index] = not_applicable_check(
                                "module",
                                module_path,
                                check["kind"],
                                "The module uses another launch strategy.",
                            )
                    write_step2_inventory(project_root, [module_path], [])

                    summary = gate.validate_manifest(manifest, project_root)

                    self.assertEqual(
                        summary["readiness"], "ready", summary["blockers"]
                    )

                    for index, check in enumerate(manifest["checks"]):
                        if check.get("kind") in {
                            "spring_boot_run",
                            "executable_jar",
                        }:
                            manifest["checks"][index] = not_applicable_check(
                                "module",
                                module_path,
                                check["kind"],
                                "The module has no selected launch strategy.",
                            )
                    summary = gate.validate_manifest(manifest, project_root)

                    self.assertEqual(summary["readiness"], "not_ready")
                    self.assertTrue(
                        any(
                            "must mark exactly one Spring Boot launch check"
                            in blocker
                            for blocker in summary["blockers"]
                        )
                    )

                    for index, check in enumerate(manifest["checks"]):
                        if check.get("kind") in {
                            "spring_boot_run",
                            "executable_jar",
                        }:
                            manifest["checks"][index] = passing_check(
                                "module",
                                module_path,
                                check["kind"],
                                LOG_DIRECTORY + "/pass.log",
                                environment="local",
                            )
                    summary = gate.validate_manifest(manifest, project_root)

                    self.assertEqual(summary["readiness"], "not_ready")
                    self.assertTrue(
                        any(
                            "must mark exactly one Spring Boot launch check"
                            in blocker
                            for blocker in summary["blockers"]
                        )
                    )

    def test_external_launcher_mode_uses_its_own_check(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary)
            (project_root / "external").mkdir()
            write_unit_suite_configuration(project_root, "external")
            (project_root / LOG_DIRECTORY).mkdir(parents=True)
            (project_root / LOG_DIRECTORY / "pass.log").write_text(
                "check completed\n", encoding="utf-8"
            )
            module_path = "external"
            checks = []
            for kind in gate.MODULE_CHECKS:
                if kind in {"spring_boot_run", "executable_jar"}:
                    checks.append(
                        not_applicable_check(
                            "module",
                            module_path,
                            kind,
                            "The module uses an external launcher.",
                        )
                    )
                elif kind == "external_launcher":
                    checks.append(
                        passing_check(
                            "module",
                            module_path,
                            kind,
                            LOG_DIRECTORY + "/pass.log",
                            environment="local",
                        )
                    )
                else:
                    checks.append(
                        passing_check(
                            "module",
                            module_path,
                            kind,
                            LOG_DIRECTORY + "/pass.log",
                        )
                    )
            checks.append(
                passing_check(
                    "module",
                    module_path,
                    "tests",
                    LOG_DIRECTORY + "/pass.log",
                    "unit",
                )
            )
            manifest = {
                "schema_version": 1,
                "mode": "migration",
                "modules": [
                    {
                        "path": module_path,
                        "runtime_mode": "external-launcher",
                        "test_suites": [
                            {"name": "unit", "requires_docker": False}
                        ],
                    }
                ],
                "models": [],
                "checks": checks,
            }
            write_step2_inventory(project_root, [module_path], [])
            evidence = project_root / "validation-evidence.json"
            evidence.write_text(json.dumps(manifest), encoding="utf-8")
            report = project_root / "MIGRATION_REPORT.md"
            report.write_text("# Migration report\n", encoding="utf-8")

            completed = self.run_gate(project_root, evidence.name)

            self.assertEqual(completed.returncode, 0, completed.stdout)
            self.assertEqual(json.loads(completed.stdout)["readiness"], "ready")

    def test_build_configuration_suites_cannot_be_omitted(self):
        configurations = (
            (
                "pom.xml",
                "<project><build><plugins><plugin>"
                "<artifactId>maven-failsafe-plugin</artifactId>"
                "<executions><execution><id>integration-tests</id>"
                "<goals><goal>integration-test</goal><goal>verify</goal>"
                "</goals></execution></executions></plugin></plugins></build>"
                "</project>",
                "integration-tests",
            ),
            (
                "pom.xml",
                "<project><build><plugins><plugin>"
                "<artifactId>maven-failsafe-plugin</artifactId>"
                "<executions><execution><goals><goal>integration-test</goal>"
                "<goal>verify</goal></goals></execution></executions>"
                "</plugin></plugins></build></project>",
                "integration",
            ),
            (
                "pom.xml",
                "<project><build><pluginManagement><plugins><plugin>"
                "<artifactId>maven-failsafe-plugin</artifactId>"
                "<executions><execution><id>managed-integration</id>"
                "<goals><goal>integration-test</goal><goal>verify</goal>"
                "</goals></execution></executions></plugin></plugins>"
                "</pluginManagement><plugins><plugin>"
                "<artifactId>maven-failsafe-plugin</artifactId>"
                "</plugin></plugins></build></project>",
                "managed-integration",
            ),
            (
                "build.gradle.kts",
                'tasks.register<Test>("integrationTest") {}',
                "integrationTest",
            ),
            (
                "build.gradle.kts",
                'tasks.named<Test>("integrationTest") {}',
                "integrationTest",
            ),
            (
                "build.gradle.kts",
                'tasks.named("integrationTest", Test::class) {}',
                "integrationTest",
            ),
            (
                "build.gradle",
                "tasks.register('integrationTest', Test) {}",
                "integrationTest",
            ),
            (
                "build.gradle.kts",
                'testing { suites { register<JvmTestSuite>("integrationTest") {} } }',
                "integrationTest",
            ),
        )
        for build_file, contents, suite_name in configurations:
            with self.subTest(build_file=build_file):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary)
                    module_path = "service"
                    module_root = project_root / module_path
                    module_root.mkdir()
                    (module_root / build_file).write_text(
                        contents, encoding="utf-8"
                    )
                    log_directory = project_root / LOG_DIRECTORY
                    log_directory.mkdir(parents=True)
                    (log_directory / "pass.log").write_text(
                        "test suite completed\n", encoding="utf-8"
                    )
                    manifest = passing_module_manifest(module_path)
                    write_step2_inventory(project_root, [module_path], [])

                    summary = gate.validate_manifest(manifest, project_root)

                    self.assertEqual(
                        summary["readiness"],
                        "not_ready",
                        summary["blockers"],
                    )
                    self.assertTrue(
                        any(
                            "build-configured suite {}".format(suite_name)
                            in blocker
                            for blocker in summary["blockers"]
                        ),
                        "The build-configured suite was not required.",
                    )

    def test_maven_default_surefire_suite_cannot_be_omitted(self):
        with tempfile.TemporaryDirectory(
            prefix="migration-evidence-"
        ) as temporary:
            project_root = Path(temporary)
            module_path = "service"
            module_root = project_root / module_path
            test_source_root = module_root / "src/test/java"
            test_source_root.mkdir(parents=True)
            (test_source_root / "ServiceTest.java").write_text(
                "class ServiceTest {}",
                encoding="utf-8",
            )
            (module_root / "pom.xml").write_text(
                "<project><build><plugins><plugin>"
                "<artifactId>maven-failsafe-plugin</artifactId>"
                "<executions><execution><id>integration-tests</id>"
                "<goals><goal>integration-test</goal><goal>verify</goal>"
                "</goals></execution></executions></plugin></plugins></build>"
                "</project>",
                encoding="utf-8",
            )
            log_directory = project_root / LOG_DIRECTORY
            log_directory.mkdir(parents=True)
            (log_directory / "pass.log").write_text(
                "test suite completed\n", encoding="utf-8"
            )
            manifest = passing_module_manifest(module_path)
            manifest["modules"][0]["test_suites"] = [
                {"name": "integration-tests", "requires_docker": False}
            ]
            test_check = next(
                check for check in manifest["checks"] if check.get("kind") == "tests"
            )
            test_check["scenario"] = "integration-tests"
            write_step2_inventory(project_root, [module_path], [])

            summary = gate.validate_manifest(manifest, project_root)

            self.assertEqual(summary["readiness"], "not_ready")
            self.assertTrue(
                any(
                    "build-configured suite unit" in blocker
                    for blocker in summary["blockers"]
                ),
                "The default Maven Surefire suite was not required.",
            )

    def test_gradle_default_test_suite_cannot_be_omitted(self):
        configurations = (
            ("plugins { id(\"java\") }", False),
            ("plugins { java }", False),
            ('plugins { kotlin("jvm") version "1.9.0" }', False),
            ('apply(plugin = "java")', False),
            ("apply plugin: 'java'", False),
            ("", True),
        )
        for build_configuration, has_test_sources in configurations:
            with self.subTest(
                build_configuration=build_configuration,
                has_test_sources=has_test_sources,
            ):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary)
                    module_path = "service"
                    module_root = project_root / module_path
                    module_root.mkdir()
                    (module_root / "build.gradle.kts").write_text(
                        build_configuration
                        + '\ntasks.register<Test>("integrationTest") {}\n',
                        encoding="utf-8",
                    )
                    if has_test_sources:
                        test_source_root = module_root / "src/test/java"
                        test_source_root.mkdir(parents=True)
                        (test_source_root / "ServiceTest.java").write_text(
                            "class ServiceTest {}",
                            encoding="utf-8",
                        )
                    log_directory = project_root / LOG_DIRECTORY
                    log_directory.mkdir(parents=True)
                    (log_directory / "pass.log").write_text(
                        "test suite completed\n", encoding="utf-8"
                    )
                    manifest = passing_module_manifest(module_path)
                    manifest["modules"][0]["test_suites"] = [
                        {
                            "name": "integrationTest",
                            "requires_docker": False,
                        }
                    ]
                    test_check = next(
                        check
                        for check in manifest["checks"]
                        if check.get("kind") == "tests"
                    )
                    test_check["scenario"] = "integrationTest"
                    write_step2_inventory(project_root, [module_path], [])

                    summary = gate.validate_manifest(manifest, project_root)

                    self.assertEqual(summary["readiness"], "not_ready")
                    self.assertTrue(
                        any(
                            "build-configured suite test" in blocker
                            for blocker in summary["blockers"]
                        ),
                        "The default Gradle test suite was not required.",
                    )

    def test_declared_test_suites_must_be_build_configured(self):
        with tempfile.TemporaryDirectory(
            prefix="migration-evidence-"
        ) as temporary:
            project_root = Path(temporary)
            module_path = "service"
            (project_root / module_path).mkdir()
            log_directory = project_root / LOG_DIRECTORY
            log_directory.mkdir(parents=True)
            (log_directory / "pass.log").write_text(
                "test suite completed\n", encoding="utf-8"
            )
            manifest = passing_module_manifest(module_path)
            manifest["modules"][0]["test_suites"][0]["name"] = "invented"
            test_check = next(
                check
                for check in manifest["checks"]
                if check.get("kind") == "tests"
            )
            test_check["scenario"] = "invented"
            write_step2_inventory(project_root, [module_path], [])

            summary = gate.validate_manifest(manifest, project_root)

            self.assertEqual(summary["readiness"], "not_ready")
            self.assertTrue(
                any(
                    "test_suites declares suite invented" in blocker
                    for blocker in summary["blockers"]
                ),
                "An unconfigured test suite was accepted as passing.",
            )

    def test_docker_suite_requires_a_daemon_probe(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary)
            (project_root / "service").mkdir()
            write_unit_suite_configuration(project_root, "service")
            (project_root / LOG_DIRECTORY).mkdir(parents=True)
            (project_root / LOG_DIRECTORY / "pass.log").write_text(
                "check completed\n", encoding="utf-8"
            )
            checks = []
            module_path = "service"
            for kind in gate.MODULE_CHECKS:
                if kind in {
                    "spring_boot_run",
                    "executable_jar",
                    "external_launcher",
                }:
                    checks.append(
                        not_applicable_check(
                            "module",
                            module_path,
                            kind,
                            "The module has no runtime entry point.",
                        )
                    )
                else:
                    checks.append(
                        passing_check(
                            "module",
                            module_path,
                            kind,
                            LOG_DIRECTORY + "/pass.log",
                        )
                    )
            checks.append(
                passing_check(
                    "module",
                    module_path,
                    "tests",
                    LOG_DIRECTORY + "/pass.log",
                    "container",
                )
            )
            manifest = {
                "schema_version": 1,
                "mode": "migration",
                "modules": [
                    {
                        "path": module_path,
                        "runtime_mode": "none",
                        "test_suites": [
                            {"name": "container", "requires_docker": True}
                        ],
                    }
                ],
                "models": [],
                "checks": checks,
            }
            write_step2_inventory(project_root, [module_path], [])
            evidence = project_root / "validation-evidence.json"
            evidence.write_text(json.dumps(manifest), encoding="utf-8")
            report = project_root / "MIGRATION_REPORT.md"
            report.write_text("# Migration report\n", encoding="utf-8")

            completed = self.run_gate(project_root, evidence.name)

            self.assertEqual(completed.returncode, 1)
            summary = json.loads(completed.stdout)
            self.assertEqual(summary["readiness"], "not_ready")
            self.assertTrue(
                any("docker_info" in blocker for blocker in summary["blockers"])
            )

    def test_docker_probe_must_invoke_docker_info(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary)
            module_path = "service"
            (project_root / module_path).mkdir()
            log_directory = project_root / LOG_DIRECTORY
            log_directory.mkdir(parents=True)
            (log_directory / "pass.log").write_text(
                "check completed\n", encoding="utf-8"
            )
            manifest = passing_module_manifest(module_path)
            manifest["modules"][0]["test_suites"][0]["requires_docker"] = True
            docker_probe = passing_check(
                "project",
                "docker",
                "docker_info",
                LOG_DIRECTORY + "/pass.log",
            )
            docker_probe["command"] = "echo docker info"
            manifest["checks"].insert(-1, docker_probe)
            write_step2_inventory(project_root, [module_path], [])

            summary = gate.validate_manifest(manifest, project_root)

            self.assertEqual(
                summary["readiness"],
                "not_ready",
            )
            self.assertTrue(
                any(
                    "must invoke docker info" in blocker.lower()
                    for blocker in summary["blockers"]
                ),
                "The Docker probe did not invoke docker info.",
            )

    def test_docker_probe_must_precede_dependent_suite(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            evidence_path = project_root / "validation-evidence.json"
            manifest = json.loads(evidence_path.read_text(encoding="utf-8"))
            materialize_inventory(project_root, manifest)
            docker_check = next(
                check
                for check in manifest["checks"]
                if check["kind"] == "docker_info"
            )
            manifest["checks"].remove(docker_check)
            suite_index = next(
                index
                for index, check in enumerate(manifest["checks"])
                if check.get("scenario") == "container-integration"
            )
            manifest["checks"].insert(suite_index + 1, docker_check)
            evidence_path.write_text(json.dumps(manifest), encoding="utf-8")

            completed = self.run_gate(project_root)

            self.assertEqual(completed.returncode, 1)
            summary = json.loads(completed.stdout)
            self.assertTrue(
                any(
                    "docker info probe must precede" in blocker
                    for blocker in summary["blockers"]
                )
            )

    def test_timer_preflight_must_precede_deployment(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary) / "project"
            shutil.copytree(FIXTURE, project_root)
            evidence_path = project_root / "validation-evidence.json"
            manifest = json.loads(evidence_path.read_text(encoding="utf-8"))
            materialize_inventory(project_root, manifest)
            timer_check = next(
                check
                for check in manifest["checks"]
                if check["kind"] == "timer_preflight"
            )
            manifest["checks"].remove(timer_check)
            timer_check.update(
                {
                    "method": "command",
                    "command": "Run the repeating timer preflight",
                    "exit_code": 0,
                    "result": "passed",
                    "evidence_path": (
                        LOG_DIRECTORY + "/evidence/message-assertion.txt"
                    ),
                    "isolation_or_cleanup_plan": (
                        "Use an isolated local environment and clean up the timer instance."
                    ),
                    "reason": None,
                    "blocker_reason": None,
                    "failure_class": None,
                    "environment": "local",
                }
            )
            manifest["checks"].append(timer_check)
            evidence_path.write_text(json.dumps(manifest), encoding="utf-8")

            completed = self.run_gate(project_root)

            self.assertEqual(completed.returncode, 1)
            summary = json.loads(completed.stdout)
            self.assertTrue(
                any(
                    "repeating timer preflight must precede deployment"
                    in blocker
                    for blocker in summary["blockers"]
                )
            )

    def test_production_deployment_cannot_pass(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary)
            (project_root / "models").mkdir()
            (project_root / LOG_DIRECTORY).mkdir(parents=True)
            (project_root / "models/decision.dmn").write_text(
                "source\n", encoding="utf-8"
            )
            (project_root / "models/converted-c8-decision.dmn").write_text(
                "converted\n", encoding="utf-8"
            )
            (project_root / LOG_DIRECTORY / "check.log").write_text(
                "check completed\n", encoding="utf-8"
            )
            model_path = "models/converted-c8-decision.dmn"
            source_path = "models/decision.dmn"
            checks = []
            for kind in gate.MODEL_CHECKS:
                if kind == "deployment":
                    deployment = passing_check(
                        "model",
                        model_path,
                        kind,
                        LOG_DIRECTORY + "/check.log",
                    )
                    deployment["environment"] = "production"
                    checks.append(deployment)
                elif kind in {
                    "bpmn_di",
                    "source_di_provenance",
                    "task_definition_types",
                }:
                    checks.append(
                        not_applicable_check(
                            "model",
                            model_path,
                            kind,
                            "The target is a DMN decision model.",
                        )
                    )
                elif kind in gate.MODEL_CHECKS_REQUIRING_PASS:
                    checks.append(
                        passing_check(
                            "model",
                            model_path,
                            kind,
                            LOG_DIRECTORY + "/check.log",
                        )
                    )
                else:
                    checks.append(
                        not_applicable_check(
                            "model",
                            model_path,
                            kind,
                            "The decision model has no related form behavior.",
                        )
                    )
            manifest = {
                "schema_version": 1,
                "mode": "migration",
                "modules": [],
                "models": [
                    {
                        "path": model_path,
                        "source_path": source_path,
                        "type": "dmn",
                        "approach": "M1",
                        "deployable": True,
                        "source_has_di": False,
                        "form_inventory": [],
                        "processes": [],
                        "recurring_timer_starts": [],
                    }
                ],
                "checks": checks,
            }
            write_step2_inventory(project_root, [], [source_path])
            evidence = project_root / "validation-evidence.json"
            evidence.write_text(json.dumps(manifest), encoding="utf-8")
            report = project_root / "MIGRATION_REPORT.md"
            report.write_text("# Migration report\n", encoding="utf-8")

            completed = self.run_gate(project_root, evidence.name)

            self.assertEqual(completed.returncode, 1)
            summary = json.loads(completed.stdout)
            self.assertEqual(summary["readiness"], "not_ready")
            self.assertTrue(
                any(
                    "unsafe or unknown environment" in blocker
                    for blocker in summary["blockers"]
                )
            )

    def test_dmn_source_xml_must_parse_before_readiness(self):
        with tempfile.TemporaryDirectory(
            prefix="migration-evidence-"
        ) as temporary:
            project_root = Path(temporary) / "project"
            source_path = "models/decision.dmn"
            model_path = "models/converted-c8-decision.dmn"
            log_directory = project_root / LOG_DIRECTORY
            log_directory.mkdir(parents=True)
            model_directory = project_root / "models"
            model_directory.mkdir()
            source_file = project_root / source_path
            converted_file = project_root / model_path
            write_dmn_file(source_file)
            write_dmn_file(converted_file)

            checks = []
            for kind in gate.MODEL_CHECKS:
                if (
                    kind in gate.MODEL_CHECKS_REQUIRING_PASS
                    or kind == "deployment"
                ):
                    check_evidence = (
                        LOG_DIRECTORY + "/{}.log".format(kind)
                    )
                    (project_root / check_evidence).write_text(
                        "validation evidence\n", encoding="utf-8"
                    )
                    check = passing_check(
                        "model",
                        model_path,
                        kind,
                        check_evidence,
                    )
                    if ("model", kind) in gate.MANUAL_CHECKS:
                        check.update(
                            {
                                "method": "manual",
                                "command": "Reviewed the DMN model",
                                "exit_code": None,
                            }
                        )
                    if kind == "deployment":
                        check["environment"] = "local"
                    checks.append(check)
                else:
                    checks.append(
                        not_applicable_check(
                            "model",
                            model_path,
                            kind,
                            "The DMN model has no related behavior.",
                        )
                    )

            manifest = {
                "schema_version": 1,
                "mode": "migration",
                "modules": [],
                "models": [
                    {
                        "path": model_path,
                        "source_path": source_path,
                        "type": "dmn",
                        "approach": "M1",
                        "deployable": True,
                        "source_has_di": False,
                        "form_inventory": [],
                        "processes": [],
                        "recurring_timer_starts": [],
                    }
                ],
                "checks": checks,
            }
            write_step2_inventory(project_root, [], [source_path])

            valid_summary = gate.validate_manifest(manifest, project_root)

            self.assertEqual(
                valid_summary["readiness"],
                "ready",
                valid_summary["blockers"],
            )

            source_file.write_text("not XML", encoding="utf-8")
            invalid_summary = gate.validate_manifest(manifest, project_root)

            self.assertEqual(invalid_summary["readiness"], "not_ready")
            self.assertTrue(
                any(
                    "source model cannot be parsed" in blocker
                    for blocker in invalid_summary["blockers"]
                ),
                invalid_summary["blockers"],
            )

    def test_source_has_di_must_match_parsed_bpmn_di(self):
        for declared_has_di, parsed_has_di in ((True, False), (False, True)):
            with self.subTest(
                declared_has_di=declared_has_di,
                parsed_has_di=parsed_has_di,
            ):
                with tempfile.TemporaryDirectory(
                    prefix="migration-evidence-"
                ) as temporary:
                    project_root = Path(temporary) / "project"
                    shutil.copytree(FIXTURE, project_root)
                    manifest = json.loads(
                        (project_root / "validation-evidence.json").read_text(
                            encoding="utf-8"
                        )
                    )
                    model = manifest["models"][0]
                    model["source_has_di"] = declared_has_di
                    materialize_inventory(project_root, manifest)
                    write_bpmn_file(
                        project_root / model["source_path"],
                        model["processes"],
                        model["recurring_timer_starts"],
                        include_di=parsed_has_di,
                    )

                    summary = gate.validate_manifest(manifest, project_root)

                    self.assertTrue(
                        any(
                            "source_has_di declaration does not match parsed "
                            "source BPMN DI" in blocker
                            for blocker in summary["blockers"]
                        ),
                        summary["blockers"],
                    )

    def test_passing_command_with_nonzero_exit_is_rejected(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary)
            (project_root / "service").mkdir()
            (project_root / LOG_DIRECTORY).mkdir(parents=True)
            (project_root / LOG_DIRECTORY / "pass.log").write_text(
                "check output\n", encoding="utf-8"
            )
            module_path = "service"
            checks = []
            for kind in gate.MODULE_CHECKS:
                if kind in {
                    "spring_boot_run",
                    "executable_jar",
                    "external_launcher",
                }:
                    checks.append(
                        not_applicable_check(
                            "module",
                            module_path,
                            kind,
                            "The module has no runtime entry point.",
                        )
                    )
                else:
                    checks.append(
                        passing_check(
                            "module",
                            module_path,
                            kind,
                            LOG_DIRECTORY + "/pass.log",
                        )
                    )
            checks.append(
                passing_check(
                    "module",
                    module_path,
                    "tests",
                    LOG_DIRECTORY + "/pass.log",
                    "unit",
                )
            )
            checks[0]["exit_code"] = 1
            manifest = {
                "schema_version": 1,
                "mode": "migration",
                "modules": [
                    {
                        "path": module_path,
                        "runtime_mode": "none",
                        "test_suites": [
                            {"name": "unit", "requires_docker": False}
                        ],
                    }
                ],
                "models": [],
                "checks": checks,
            }
            write_step2_inventory(project_root, [module_path], [])
            evidence = project_root / "validation-evidence.json"
            evidence.write_text(json.dumps(manifest), encoding="utf-8")
            report = project_root / "MIGRATION_REPORT.md"
            report.write_text("# Migration report\n", encoding="utf-8")

            completed = self.run_gate(project_root, evidence.name)

            self.assertEqual(completed.returncode, 1)
            summary = json.loads(completed.stdout)
            self.assertEqual(summary["readiness"], "not_ready")
            self.assertGreater(summary["counts"]["invalid"], 0)


if __name__ == "__main__":
    unittest.main()
