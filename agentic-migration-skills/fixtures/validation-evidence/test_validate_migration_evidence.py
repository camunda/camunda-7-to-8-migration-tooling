"""Regression tests for the migration validation evidence gate."""

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


FIXTURE = Path(__file__).resolve().parent
LOG_DIRECTORY = ".camunda-migration/validation/logs"
SKILL_ROOT = FIXTURE.parents[1] / "skills" / "migrate-c7-to-c8-code"
SCRIPT = SKILL_ROOT / "scripts" / "validate_migration_evidence.py"
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


def materialize_inventory(project_root, manifest):
    for module in manifest["modules"]:
        (project_root / module["path"]).mkdir(parents=True, exist_ok=True)
    for model in manifest["models"]:
        for key in ("source_path", "path"):
            model_file = project_root / model[key]
            model_file.parent.mkdir(parents=True, exist_ok=True)
            model_file.write_text("fixture model\n", encoding="utf-8")


class ValidationEvidenceTest(unittest.TestCase):
    def run_gate(
        self,
        project_root,
        evidence="validation-evidence.json",
        report="MIGRATION_REPORT.md",
    ):
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--project-root",
                str(project_root),
                "--evidence",
                evidence,
                "--report",
                report,
            ],
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

    def test_external_launcher_mode_uses_its_own_check(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary)
            (project_root / "external").mkdir()
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
            evidence = project_root / "validation-evidence.json"
            evidence.write_text(json.dumps(manifest), encoding="utf-8")
            report = project_root / "MIGRATION_REPORT.md"
            report.write_text("# Migration report\n", encoding="utf-8")

            completed = self.run_gate(project_root, evidence.name)

            self.assertEqual(completed.returncode, 0, completed.stdout)
            self.assertEqual(json.loads(completed.stdout)["readiness"], "ready")

    def test_docker_suite_requires_a_daemon_probe(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary)
            (project_root / "service").mkdir()
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
                        "processes": [],
                        "recurring_timer_starts": [],
                    }
                ],
                "checks": checks,
            }
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
