"""Regression tests for the DI-less and form-free M1 lint path."""

from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

from verify_bpmn_connectivity import BPMN, check

FIXTURE_DIR = Path(__file__).resolve().parent
SOURCE = FIXTURE_DIR / "order-c7-no-di-form-free.bpmn"
CONVERTED = FIXTURE_DIR / "converted-c8-order-no-di-form-free.bpmn"
FORM_NAMES = {"formKey", "formRef", "formRefBinding", "formRefVersion", "formHandlerClass",
              "formData", "formProperty", "formDefinition"}


class ModelLintReadinessTest(unittest.TestCase):
    def test_di_less_form_free_source_and_converted_copy_are_connected(self):
        for path in (SOURCE, CONVERTED):
            with self.subTest(path=path.name):
                root = ET.parse(path).getroot()
                names = {e.tag.rsplit("}", 1)[-1] for e in root.iter()}
                names |= {a.rsplit("}", 1)[-1] for e in root.iter(BPMN + "userTask") for a in e.attrib}
                self.assertTrue(names.isdisjoint({"BPMNDiagram", "incoming", "outgoing"} | FORM_NAMES))
                self.assertEqual([], check(path))

    def test_disconnected_converted_copy_fails(self):
        self.assertEqual(
            ["DetachedReview is not on a start-to-end path"],
            check(FIXTURE_DIR / "converted-c8-order-disconnected.bpmn"),
        )

    def test_unknown_sequence_flow_reference_fails(self):
        with tempfile.NamedTemporaryFile("w", suffix=".bpmn", delete=False) as file:
            file.write(
                f'<definitions xmlns="{BPMN[1:-1]}"><process id="p">'
                '<startEvent id="start"/><endEvent id="end"/>'
                '<sequenceFlow id="flow" sourceRef="missing" targetRef="end"/>'
                "</process></definitions>"
            )
        self.addCleanup(Path(file.name).unlink)
        self.assertIn("flow references unknown node", check(file.name))


if __name__ == "__main__":
    unittest.main()
