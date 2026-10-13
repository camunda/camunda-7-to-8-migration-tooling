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
        self.assertEqual(source_task.findall(".//" + CAMUNDA + "formRef"), [])
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


if __name__ == "__main__":
    unittest.main()
