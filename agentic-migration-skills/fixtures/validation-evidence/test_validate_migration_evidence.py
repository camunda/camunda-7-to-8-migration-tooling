"""Regression tests for the migration validation evidence gate."""

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


FIXTURE = Path(__file__).resolve().parent
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
    def run_gate(self, project_root, evidence="validation-evidence.json"):
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--project-root",
                str(project_root),
                "--evidence",
                evidence,
                "--report",
                "MIGRATION_REPORT.md",
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

    def test_module_gate_passes_only_with_executed_tests(self):
        with tempfile.TemporaryDirectory(prefix="migration-evidence-") as temporary:
            project_root = Path(temporary)
            (project_root / "service").mkdir()
            (project_root / "logs").mkdir()
            (project_root / "logs/pass.log").write_text(
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
                            "module", module_path, kind, "logs/pass.log"
                        )
                    )
            checks.append(
                passing_check("module", module_path, "tests", "logs/pass.log", "unit")
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
                    "logs/pass.log",
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
            (project_root / "logs").mkdir()
            (project_root / "logs/pass.log").write_text(
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
                            "logs/pass.log",
                            environment="local",
                        )
                    )
                else:
                    checks.append(
                        passing_check(
                            "module", module_path, kind, "logs/pass.log"
                        )
                    )
            checks.append(
                passing_check("module", module_path, "tests", "logs/pass.log", "unit")
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
            (project_root / "logs").mkdir()
            (project_root / "logs/pass.log").write_text(
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
                            "module", module_path, kind, "logs/pass.log"
                        )
                    )
            checks.append(
                passing_check(
                    "module", module_path, "tests", "logs/pass.log", "container"
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
                    "method": "manual",
                    "command": "Manual timer-start safety preflight",
                    "exit_code": None,
                    "result": "passed",
                    "evidence_path": "logs/message-assertion.log",
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
            (project_root / "logs").mkdir()
            (project_root / "models/decision.dmn").write_text(
                "source\n", encoding="utf-8"
            )
            (project_root / "models/converted-c8-decision.dmn").write_text(
                "converted\n", encoding="utf-8"
            )
            (project_root / "logs/check.log").write_text(
                "check completed\n", encoding="utf-8"
            )
            model_path = "models/converted-c8-decision.dmn"
            source_path = "models/decision.dmn"
            checks = []
            for kind in gate.MODEL_CHECKS:
                if kind == "deployment":
                    deployment = passing_check(
                        "model", model_path, kind, "logs/check.log"
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
                            "model", model_path, kind, "logs/check.log"
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
            (project_root / "logs").mkdir()
            (project_root / "logs/pass.log").write_text(
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
                            "module", module_path, kind, "logs/pass.log"
                        )
                    )
            checks.append(
                passing_check("module", module_path, "tests", "logs/pass.log", "unit")
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
