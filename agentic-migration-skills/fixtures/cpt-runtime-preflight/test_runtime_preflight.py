# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.

import copy
import contextlib
import importlib.util
import io
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


SKILLS = Path(__file__).resolve().parents[2] / "skills/migrate-c7-to-c8-code"
SPEC = importlib.util.spec_from_file_location(
    "migration_evidence_runtime_fixture",
    SKILLS / "scripts/validate_migration_evidence.py",
)
evidence = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = evidence
SPEC.loader.exec_module(evidence)

MODULE = "examples/web"
SOURCE_ID = f"{MODULE}:com.example.SourceTest#run"
TARGET_ID = f"{MODULE}:com.example.TargetTest#run"
OTHER_SOURCE_ID = f"{MODULE}:com.example.OtherSourceTest#run"
OTHER_ID = f"{MODULE}:com.example.OtherTest#run"
ADDED_ID = f"{MODULE}:com.example.AddedTest#run"
SUITE = {
    "module": MODULE,
    "name": "unit",
    "test_ids": [SOURCE_ID],
}
MAPPING = {
    "tests": [
        {"c7_id": SOURCE_ID, "status": "migrated", "c8_ids": [TARGET_ID]},
        {
            "c7_id": OTHER_SOURCE_ID,
            "status": "migrated",
            "c8_ids": [OTHER_ID],
        },
    ],
    "baseline": {"suites": []},
}


class RuntimePreflightTest(unittest.TestCase):
    def setUp(self):
        self.plan = SimpleNamespace(
            test_contract={
                "mode": "run",
                "suites": {(MODULE, "unit"): SUITE},
                "cpt_suites": {(MODULE, "unit"): {"name": "unit"}},
            },
            required={
                ("project", ".", "docker_info", None): "command",
                ("project", ".", "test_freeze", None): "snapshot",
                ("module", MODULE, "compile", None): "command",
                ("module", MODULE, "c7_baseline", "unit"): "command",
                ("module", MODULE, "cpt_runtime_preflight", "unit"): "command",
                ("module", MODULE, "test_repeat", "unit"): "command",
            },
            docker_suites={
                ("module", MODULE, "cpt_runtime_preflight", "unit"): True,
                ("module", MODULE, "test_repeat", "unit"): True,
            },
            issues=[],
            allowed={("module", MODULE, "cpt_runtime_preflight", "unit")},
            source_digest="source-digest",
        )
        self.args = SimpleNamespace(
            target=MODULE,
            scenario="unit",
            command=["mvn", "-B", "-Dtest=TargetTest", "test"],
            timeout=90,
        )

    def recorder_args(self):
        return SimpleNamespace(
            **vars(self.args),
            type="module",
            kind="cpt_runtime_preflight",
            action="run",
            environment="local",
            non_timer_evidence_json=None,
            isolation_plan=None,
            target_disposable=False,
            target_version=None,
            reference=None,
            disposition=None,
        )

    def ledger(self, tests=None):
        return {
            "schema_version": 1,
            "baseline": {"suites": []},
            "tests": copy.deepcopy(MAPPING["tests"] if tests is None else tests),
            "freeze": {"files": {}},
            "test_changes": [],
            "mock_changes": [],
        }

    def write_failed_probe(self, root, mapping):
        evidence.write_json(
            root, evidence.INVENTORY, {"run_id": "test-run", "test_run_mode": "run"}
        )
        evidence.write_json(root, evidence.TEST_MAPPING, mapping)
        key = ("module", MODULE, "cpt_runtime_preflight", "unit")
        reference = evidence.write_check_log(root, key, {
            "run_id": "test-run",
            "type": "module",
            "target": MODULE,
            "kind": "cpt_runtime_preflight",
            "scenario": "unit",
            "method": "command",
            "command": ["mvn", "-B", "test"],
            "exit_code": 0,
            "result": "failed",
            "reason": "No mapped CPT method ran",
            "output": "Only an unrelated test ran",
            "environment": "local",
            "source_digest": "source-digest",
            "test_mapping_digest": evidence.test_mapping_digest(mapping, "all"),
        })
        evidence.write_json(root, evidence.EVIDENCE, {"checks": [reference]})
        return key, reference

    def run_with_results(self, results, exit_code=0):
        with patch.object(evidence, "run_cpt_test_suite", return_value={
            "exit_code": exit_code,
            "result": "passed" if exit_code == 0 else "failed",
            "reason": None,
            "test_results": results,
            "output": "",
        }):
            return evidence.record_cpt_runtime_preflight(
                Path("."), self.plan, self.args, MAPPING
            )

    def test_requires_a_passing_mapped_cpt_method(self):
        result = self.run_with_results(
            {TARGET_ID: {"result": "passed", "invocations": ["passed"]}}
        )
        self.assertEqual("passed", result["result"])

    def test_rejects_class_initialization_without_executed_method(self):
        result = self.run_with_results({
            f"{MODULE}:com.example.TargetTest#initializationError": {
                "result": "error",
                "invocations": ["error"],
            }
        })
        self.assertEqual("failed", result["result"])
        self.assertIn("mapped CPT method", result["reason"])

    def test_rejects_skipped_or_unrelated_methods(self):
        for results in (
            {TARGET_ID: {"result": "skipped", "invocations": ["skipped"]}},
            {OTHER_ID: {"result": "passed", "invocations": ["passed"]}},
        ):
            with self.subTest(results=results):
                self.assertEqual("failed", self.run_with_results(results)["result"])

    def test_baseline_results_cannot_assign_a_migrated_test_to_another_suite(self):
        mapping = self.ledger()
        mapping["baseline"]["suites"] = [{
            "module": MODULE,
            "suite": "unit",
            "test_results": {OTHER_SOURCE_ID: {"result": "passed"}},
        }]
        self.assertEqual(
            {TARGET_ID}, evidence.suite_cpt_test_ids(SUITE, mapping, MODULE, "unit")
        )
        mapping["tests"][1]["handling"] = "Report only"
        self.assertEqual(
            {TARGET_ID, OTHER_ID},
            evidence.suite_cpt_test_ids(SUITE, mapping, MODULE, "unit"),
        )

    def test_added_only_docker_suite_can_preflight(self):
        mapping = self.ledger([{
            "status": "added", "suite": "extra", "c8_ids": [ADDED_ID]
        }])
        self.plan.test_contract["cpt_suites"][(MODULE, "extra")] = {"name": "extra"}
        args = SimpleNamespace(**vars(self.args))
        args.scenario = "extra"
        with patch.object(evidence, "run_cpt_test_suite", return_value={
            "exit_code": 0,
            "result": "passed",
            "reason": None,
            "test_results": {ADDED_ID: {"result": "passed"}},
            "output": "",
        }):
            probe = evidence.record_cpt_runtime_preflight(
                Path("."), self.plan, args, mapping
            )
        self.assertEqual("passed", probe["result"])

    def test_failed_command_keeps_its_exit_code_and_reason(self):
        result = self.run_with_results({}, exit_code=1)
        self.assertEqual("failed", result["result"])
        self.assertEqual(1, result["exit_code"])
        self.assertIn("code 1", result["reason"])

    def test_docker_api_mismatch_is_compatibility_not_missing_docker(self):
        message = (
            "client version 1.32 is too old. "
            "Minimum supported API version is 1.40"
        )
        failure_class, reason = evidence.cpt_docker_failure(message)
        self.assertEqual("compatibility", failure_class)
        self.assertIn("1.32", reason)
        self.assertIn("1.40", reason)
        self.assertIsNone(evidence.cpt_docker_failure("An assertion failed"))

    def test_recorder_classifies_docker_api_error_without_mislabeling_daemon(self):
        message = (
            "client version 1.32 is too old. "
            "Minimum supported API version is 1.40"
        )
        args = self.recorder_args()
        contents = {"checks": [], "run_id": "test-run", "test_run_mode": "run"}
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.object(evidence, "read_json", return_value=contents))
            stack.enter_context(patch.object(evidence, "requirements", return_value=self.plan))
            stack.enter_context(patch.object(evidence, "load_checks", return_value={}))
            stack.enter_context(patch.object(evidence, "require_prerequisites"))
            stack.enter_context(patch.object(evidence, "read_test_mapping", return_value=MAPPING))
            stack.enter_context(patch.object(evidence, "record_cpt_runtime_preflight", return_value={
                "command": ["mvn", "-B", "test"],
                "exit_code": 1,
                "result": "failed",
                "reason": "CPT runtime probe exited with code 1",
                "output": message,
                "test_ids": [],
                "test_mapping_digest": "digest",
            }))
            log = stack.enter_context(
                patch.object(evidence, "write_check_log", return_value="log.json")
            )
            stack.enter_context(patch.object(evidence, "write_json"))
            stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
            self.assertEqual(1, evidence.record(Path("."), args))
        check = log.call_args.args[2]
        self.assertEqual("compatibility", check["failure_class"])
        self.assertIn("1.32", check["reason"])
        self.assertIn("1.40", check["reason"])

    def test_zero_exit_failed_probe_can_be_reloaded_and_retried(self):
        mapping = self.ledger()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            key, reference = self.write_failed_probe(root, mapping)
            issues = []
            self.assertIn(
                key,
                evidence.load_checks(
                    root, {"checks": [reference]}, self.plan, issues
                ),
            )
            self.assertEqual([], issues)
            with contextlib.ExitStack() as stack:
                stack.enter_context(
                    patch.object(evidence, "requirements", return_value=self.plan)
                )
                stack.enter_context(patch.object(evidence, "require_prerequisites"))
                stack.enter_context(patch.object(evidence, "record_cpt_runtime_preflight", return_value={
                    "command": ["mvn", "-B", "test"],
                    "exit_code": 0,
                    "result": "passed",
                    "reason": None,
                    "output": "",
                    "test_ids": [TARGET_ID],
                    "test_mapping_digest": evidence.test_mapping_digest(
                        mapping, "all"
                    ),
                }))
                stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
                self.assertEqual(0, evidence.record(root, self.recorder_args()))
            self.assertEqual(
                "passed", evidence.read_json(root / reference)["result"]
            )

    def test_retired_suite_ignores_saved_preflight_evidence(self):
        mapping = self.ledger()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            key, reference = self.write_failed_probe(root, mapping)
            retired = self.ledger([{
                "c7_id": SOURCE_ID, "status": "retired", "c8_ids": []
            }])
            evidence.write_json(root, evidence.TEST_MAPPING, retired)
            self.plan.allowed.remove(key)
            self.plan.required.pop(key)
            issues = []
            self.assertNotIn(
                key,
                evidence.load_checks(
                    root, {"checks": [reference]}, self.plan, issues
                ),
            )
            self.assertEqual([], issues)

    def test_runtime_preflight_precedes_cpt_repeat_after_baseline(self):
        preflight = ("module", MODULE, "cpt_runtime_preflight", "unit")
        self.assertTrue(evidence.needs_safe_environment(preflight))
        self.assertIn(
            ("project", ".", "docker_info", None),
            evidence.prerequisites(self.plan, preflight),
        )
        self.assertIn(
            ("module", MODULE, "c7_baseline", "unit"),
            evidence.prerequisites(self.plan, preflight),
        )
        self.assertIn(
            preflight,
            evidence.prerequisites(
                self.plan, ("module", MODULE, "test_repeat", "unit")
            ),
        )

    def test_docker_cli_success_does_not_allow_repeat_after_api_mismatch(self):
        docker_check = ("project", ".", "docker_info", None)
        runtime_check = ("module", MODULE, "cpt_runtime_preflight", "unit")
        checks = {
            docker_check: (
                0,
                {"result": "passed", "method": "command", "source_digest": "source-digest"},
                "docker-log.json",
            ),
            runtime_check: (
                1,
                {
                    "result": "failed",
                    "method": "command",
                    "failure_class": "compatibility",
                    "source_digest": "source-digest",
                },
                "cpt-log.json",
            ),
        }
        with self.assertRaisesRegex(evidence.EvidenceError, "cpt_runtime_preflight"):
            evidence.require_prerequisites(
                Path("."), self.plan, checks, ("module", MODULE, "test_repeat", "unit")
            )


class EarlyTestChoiceContractTest(unittest.TestCase):
    def test_question_eight_matrix_keeps_explicit_choice_only_when_applicable(self):
        interview = (SKILLS / "references/interview-questions.md").read_text()
        table = interview.split(
            "| Step 2 trigger | Earlier explicit test choice | Action |", 1
        )[1].split("\n\n", 1)[0]
        rows = [
            [cell.strip() for cell in line.strip().strip("|").split("|")]
            for line in table.splitlines()
            if line.startswith("|") and not line.startswith("|---")
        ]
        self.assertEqual(["No", "Yes", "Yes"], [row[0] for row in rows])
        self.assertIn("Omit `test_run_mode`", rows[0][2])
        self.assertIn("Run tests or Migrate tests only", rows[1][1])
        self.assertIn("unchanged target, scope, and code approach", rows[1][1])
        self.assertIn("Do not ask again", rows[1][2])
        self.assertIn("Missing, deferred, declined", rows[2][1])
        self.assertIn("changed target, scope, or code approach", rows[2][1])
        self.assertIn("Ask Question 8", rows[2][2])
        self.assertIn("Decide after assessment", interview)
        self.assertIn("Never treat silence", interview)
        self.assertIn("Do not select an option for the user", interview)


if __name__ == "__main__":
    unittest.main()
