"""Regression tests for call-activity output scope validation."""

from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

import verify_call_activity_output_scope


FIXTURE = Path(__file__).parent
SOURCE = FIXTURE / "c7-source" / "call-activity-output-scope.bpmn"
CONVERTED = (
    FIXTURE
    / "expected-c8"
    / "converted-c8-call-activity-output-scope.bpmn"
)
BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL"
ZEEBE = "http://camunda.org/schema/zeebe/1.0"


def call_activity(root, call_id):
    tag = f"{{{BPMN}}}callActivity"
    return next(
        element
        for element in root.iter(tag)
        if element.get("id") == call_id
    )


def called_element(root, call_id):
    call = call_activity(root, call_id)
    elements = verify_call_activity_output_scope.extension_children(
        call, ZEEBE, "calledElement"
    )
    if len(elements) != 1:
        raise AssertionError(f"expected one calledElement for {call_id}")
    return elements[0]


class CallActivityOutputScopeTests(unittest.TestCase):
    def check_fixture(self, source_mutation=None, converted_mutation=None):
        source_root = ET.parse(SOURCE).getroot()
        converted_root = ET.parse(CONVERTED).getroot()
        if source_mutation is not None:
            source_mutation(source_root)
        if converted_mutation is not None:
            converted_mutation(converted_root)

        with tempfile.TemporaryDirectory(dir=FIXTURE) as directory:
            source_path = Path(directory) / "source.bpmn"
            converted_path = Path(directory) / "converted.bpmn"
            ET.ElementTree(source_root).write(
                source_path, encoding="utf-8", xml_declaration=True
            )
            ET.ElementTree(converted_root).write(
                converted_path, encoding="utf-8", xml_declaration=True
            )
            return verify_call_activity_output_scope.check(
                source_path, converted_path
            )

    def test_reference_covers_no_selected_and_all_outputs(self):
        self.assertEqual([], self.check_fixture())

    def test_rejects_all_child_propagation_for_no_output_contract(self):
        failures = self.check_fixture(
            converted_mutation=lambda root: called_element(
                root, "Call_No_Outputs"
            ).set("propagateAllChildVariables", "true")
        )

        self.assertIn(
            "converted call activity 'Call_No_Outputs' must set "
            "propagateAllChildVariables='false', found 'true'",
            failures,
        )

    def test_rejects_all_child_propagation_for_selected_outputs(self):
        failures = self.check_fixture(
            converted_mutation=lambda root: called_element(
                root, "Call_Selected_Output"
            ).set("propagateAllChildVariables", "true")
        )

        self.assertIn(
            "converted call activity 'Call_Selected_Output' must set "
            "propagateAllChildVariables='false', found 'true'",
            failures,
        )

    def test_rejects_disabled_all_child_propagation_for_all_outputs(self):
        failures = self.check_fixture(
            converted_mutation=lambda root: called_element(
                root, "Call_All_Outputs"
            ).set("propagateAllChildVariables", "false")
        )

        self.assertIn(
            "converted call activity 'Call_All_Outputs' must set "
            "propagateAllChildVariables='true', found 'false'",
            failures,
        )

    def test_rejects_a_missing_selected_output_mapping(self):
        def remove_output_mapping(root):
            call = call_activity(root, "Call_Selected_Output")
            mappings = verify_call_activity_output_scope.extension_children(
                call, ZEEBE, "ioMapping"
            )
            outputs = [
                output
                for mapping in mappings
                for output in mapping.findall(f"{{{ZEEBE}}}output")
            ]
            self.assertEqual(1, len(outputs))
            mappings[0].remove(outputs[0])

        failures = self.check_fixture(
            converted_mutation=remove_output_mapping
        )

        self.assertTrue(
            any(
                "Call_Selected_Output' output mappings" in failure
                for failure in failures
            ),
            failures,
        )

    def test_routes_uninspected_delegated_mappings_to_review(self):
        def add_delegated_mapping(root):
            call_activity(root, "Call_No_Outputs").set(
                f"{{{verify_call_activity_output_scope.CAMUNDA}}}"
                "variableMappingClass",
                "example.OutputMapping",
            )

        failures = self.check_fixture(source_mutation=add_delegated_mapping)

        self.assertTrue(
            any(
                "Call_No_Outputs" in failure
                and "delegated variable mappings and needs review" in failure
                for failure in failures
            ),
            failures,
        )


if __name__ == "__main__":
    unittest.main()
