"""Regression tests for safe M1 lint handling and source-faithful form-free tasks."""

from __future__ import annotations

from pathlib import Path
import sys
import subprocess
import unittest
import xml.etree.ElementTree as ET

FIXTURE_DIR = Path(__file__).resolve().parent
REPO_ROOT = FIXTURE_DIR.parents[2]
SKILL_DIR = REPO_ROOT / "agentic-migration-skills/skills/migrate-c7-to-c8-code"
REFERENCE_PATH = SKILL_DIR / "references/model-lint-and-connectivity.md"
BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL"
BPMNDI_NS = "http://www.omg.org/spec/BPMN/20100524/DI"
CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn"
ZEEBE_NS = "http://camunda.org/schema/zeebe/1.0"
BPMN = f"{{{BPMN_NS}}}"
BPMNDI = f"{{{BPMNDI_NS}}}"
CAMUNDA = f"{{{CAMUNDA_NS}}}"
ZEEBE = f"{{{ZEEBE_NS}}}"


def run_verifier(path: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(FIXTURE_DIR / "verify_bpmn_connectivity.py"), str(path)],
        capture_output=True,
        text=True,
        check=False,
    )


class ModelLintReadinessTest(unittest.TestCase):
    def test_no_di_source_and_form_free_converted_copy_have_a_valid_graph(self):
        source_path = FIXTURE_DIR / "order-c7-no-di-form-free.bpmn"
        converted_path = FIXTURE_DIR / "converted-c8-order-no-di-form-free.bpmn"
        source = ET.parse(source_path).getroot()
        converted = ET.parse(converted_path).getroot()

        for root in (source, converted):
            self.assertIsNone(root.find(".//" + BPMNDI + "BPMNDiagram"))
            self.assertEqual(root.findall(".//" + BPMN + "incoming"), [])
            self.assertEqual(root.findall(".//" + BPMN + "outgoing"), [])

        source_task = source.find(".//" + BPMN + "userTask")
        converted_task = converted.find(".//" + BPMN + "userTask")
        self.assertIsNotNone(source_task)
        self.assertIsNotNone(converted_task)
        self.assertNotIn(CAMUNDA + "formKey", source_task.attrib)
        self.assertEqual(source_task.findall(".//" + CAMUNDA + "formData"), [])
        self.assertEqual(source_task.findall(".//" + CAMUNDA + "formProperty"), [])
        self.assertNotIn(CAMUNDA + "formRef", source_task.attrib)
        self.assertNotIn(CAMUNDA + "formKey", converted_task.attrib)
        self.assertNotIn(CAMUNDA + "formRef", converted_task.attrib)
        self.assertEqual(converted_task.findall(".//" + CAMUNDA + "formData"), [])
        self.assertEqual(converted_task.findall(".//" + CAMUNDA + "formProperty"), [])
        self.assertEqual(converted_task.findall(".//" + ZEEBE + "formDefinition"), [])
        for path in (source_path, converted_path):
            with self.subTest(path=path.name):
                result = run_verifier(path)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("valid sequence-flow references", result.stdout)

    def test_source_with_form_metadata_is_not_eligible_for_form_free_exception(self):
        source = ET.parse(FIXTURE_DIR / "order-c7-form-bearing.bpmn").getroot()
        user_task = source.find(".//" + BPMN + "userTask")
        self.assertIsNotNone(user_task)
        self.assertEqual(user_task.get(CAMUNDA + "formKey"), "fixture-review-form")

    def test_form_ref_is_a_namespaced_user_task_attribute(self):
        source = ET.parse(FIXTURE_DIR / "order-c7-form-ref-bearing.bpmn").getroot()
        user_task = source.find(".//" + BPMN + "userTask")
        self.assertIsNotNone(user_task)
        self.assertEqual(user_task.get(CAMUNDA + "formRef"), "fixture-review-form")
        self.assertEqual(user_task.findall(".//" + CAMUNDA + "formRef"), [])

    def test_disconnected_converted_flow_fails_connectivity_check(self):
        path = FIXTURE_DIR / "converted-c8-order-disconnected.bpmn"
        result = run_verifier(path)
        self.assertEqual(result.returncode, 1)
        self.assertIn("not on an explicit start-to-end path", result.stderr)

    def test_unknown_sequence_flow_reference_fails_connectivity_check(self):
        invalid = f"""<bpmn:definitions xmlns:bpmn="{BPMN_NS}">
          <bpmn:process id="invalid">
            <bpmn:startEvent id="start" />
            <bpmn:endEvent id="end" />
            <bpmn:sequenceFlow id="flow" sourceRef="missing" targetRef="end" />
          </bpmn:process>
        </bpmn:definitions>"""
        path = FIXTURE_DIR / "invalid-flow-reference.tmp.bpmn"
        path.write_text(invalid, encoding="utf-8")
        self.addCleanup(path.unlink)
        result = run_verifier(path)
        self.assertEqual(result.returncode, 1)
        self.assertIn("references unknown flow node", result.stderr)

    def test_skill_routes_di_less_and_form_free_decisions_to_the_reference(self):
        skill_text = (SKILL_DIR / "SKILL.md").read_text(encoding="utf-8")
        model_text = (SKILL_DIR / "references/model-migration-approaches.md").read_text(
            encoding="utf-8"
        )
        form_text = (SKILL_DIR / "references/form-reference-migration.md").read_text(
            encoding="utf-8"
        )
        self.assertIn("references/model-lint-and-connectivity.md", skill_text)
        self.assertIn("effective-configuration check", model_text)
        self.assertIn("model-lint-and-connectivity.md", model_text)
        self.assertIn("model-lint-and-connectivity.md", form_text)
        self.assertTrue(REFERENCE_PATH.is_file())

    def test_reference_requires_targeted_lint_and_runtime_evidence(self):
        reference = REFERENCE_PATH.read_text(encoding="utf-8") if REFERENCE_PATH.is_file() else ""
        for required in (
            "no-bpmndi",
            "no-disconnected",
            "sourceRef",
            "targetRef",
            "form-free",
            "camunda:formData",
            "camunda:formProperty",
            "camunda:formKey",
            "camunda:formRef",
            "namespaced attributes",
            "child elements",
            "zeebe:formDefinition",
            ".bpmnlintrc",
            "project-wide",
            "namespace-aware",
            "explicitly chooses",
            "deploy",
            "complete",
            "different cause",
            "blocked",
        ):
            with self.subTest(required=required):
                self.assertIn(required.casefold(), reference.casefold())

    def test_effective_linter_config_must_not_suppress_rules_or_mismatch_target(self):
        reference = REFERENCE_PATH.read_text(encoding="utf-8")
        lint_scope = reference.split("## Run and record target lint", 1)[1].split(
            "## Check the source and target graph", 1
        )[0]
        normalized_scope = " ".join(lint_scope.split()).casefold()
        for required in (
            "effective lint configuration",
            "project `.bpmnlintrc` and inherited presets",
            "selected target version",
            "effective config includes each converted copy",
            "does not disable or ignore applicable rules",
            "excludes a converted copy",
            "uses another target",
            "cannot be verified",
            "raw lint unverified",
            "blocks model readiness",
        ):
            with self.subTest(required=required):
                self.assertIn(required.casefold(), normalized_scope)

    def test_runtime_gate_does_not_require_user_task_for_di_less_process(self):
        reference = REFERENCE_PATH.read_text(encoding="utf-8")
        runtime_scope = reference.split("## Require target runtime evidence", 1)[1].split(
            "## ", 1
        )[0]
        normalized_scope = " ".join(runtime_scope.split()).casefold()
        for required in (
            "expected path and end event",
            "expected process path",
            "form-free user-task exception",
            "instance reaches the task",
            "completes the task through the target API",
            "does not require a user task",
            "expected end event",
            "also records the user-task ID",
        ):
            with self.subTest(required=required):
                self.assertIn(required.casefold(), normalized_scope)
        exception_start = normalized_scope.index(
            "when the process uses a form-free user-task exception"
        )
        service_only_start = normalized_scope.index(
            "for a di-less process without a form-free user-task exception"
        )
        exception_scope = normalized_scope[exception_start:service_only_start]
        self.assertIn("completes the task through", exception_scope)
        self.assertNotIn("completes the task through", normalized_scope[:exception_start])

    def test_di_less_checks_apply_to_every_model_conversion_approach(self):
        reference = REFERENCE_PATH.read_text(encoding="utf-8")
        scope = " ".join(
            reference.split("## Scope", 1)[1].split("## Run and record target lint", 1)[0].split()
        )
        self.assertIn(
            "The DI-less checks apply to every model-conversion approach, including M2.",
            scope,
        )

    def test_form_free_exception_applies_when_source_has_di(self):
        reference = REFERENCE_PATH.read_text(encoding="utf-8")
        scope = " ".join(
            reference.split("## Scope", 1)[1].split("## Run and record target lint", 1)[0].split()
        )
        self.assertIn(
            "The form-free exception applies regardless of source DI or conversion approach.",
            scope,
        )
        self.assertIn(
            "When the source has BPMN DI, the skill follows the existing DI-preservation check.",
            scope,
        )

    def test_every_bpmn_lint_run_checks_effective_configuration(self):
        skill_text = (SKILL_DIR / "SKILL.md").read_text(encoding="utf-8")
        skill_scope = " ".join(
            skill_text.split("#### Model checks, when models were migrated", 1)[1]
            .split("#### Process behavior", 1)[0]
            .split()
        ).casefold()
        for required in (
            "before every bpmn lint run",
            "effective configuration",
            "also follow the reference for scoped lint exceptions",
        ):
            with self.subTest(required=required):
                self.assertIn(required, skill_scope)

        fixture_readme = " ".join(
            (FIXTURE_DIR / "README.md").read_text(encoding="utf-8").casefold().split()
        )
        self.assertIn("before accepting any bpmn lint output", fixture_readme)
        self.assertIn("regardless of source di or form choice", fixture_readme)

        model_text = (SKILL_DIR / "references/model-migration-approaches.md").read_text(
            encoding="utf-8"
        )
        model_scope = " ".join(
            model_text.split("## Model validation (Step 4)", 1)[1].split("\n1.", 1)[0].split()
        ).casefold()
        for required in (
            "before every bpmn lint run",
            "regardless of source di, form choice, or conversion approach",
            "effective-configuration check",
            "when the original source is di-less",
            "also follow",
        ):
            with self.subTest(required=required):
                self.assertIn(required, model_scope)

    def test_di_less_source_cannot_gain_layout_by_user_approval(self):
        reference = REFERENCE_PATH.read_text(encoding="utf-8")
        scope = " ".join(
            reference.split("## Scope", 1)[1].split("## Run and record target lint", 1)[0].split()
        )
        self.assertIn(
            "When the source is DI-less, the converted copy remains DI-less.",
            scope,
        )
        self.assertNotIn("approves a layout change", scope)

    def test_project_readiness_allows_only_validated_bpmn_lint_exceptions(self):
        readiness_text = (SKILL_DIR / "references/project-readiness.md").read_text(
            encoding="utf-8"
        )
        lint_row = next(
            line for line in readiness_text.splitlines() if line.startswith("| BPMN lint |")
        )
        normalized_row = " ".join(lint_row.split()).casefold()
        for required in (
            "every in-scope converted bpmn file passes lint",
            "every remaining finding has a valid scoped exception",
            "graph and runtime evidence",
            "every in-scope converted dmn file passes lint",
        ):
            with self.subTest(required=required):
                self.assertIn(required, normalized_row)

        report_scope = readiness_text.split("### Project readiness", 1)[1].split(
            "Use this readiness verdict table", 1
        )[0]
        self.assertIn("effective lint configuration", report_scope.casefold())
        self.assertIn("scoped exception evidence", report_scope.casefold())


if __name__ == "__main__":
    unittest.main()
