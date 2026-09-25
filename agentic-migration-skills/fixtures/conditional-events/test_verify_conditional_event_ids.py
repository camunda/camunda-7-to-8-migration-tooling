"""Regression tests for conditional event definition ID validation."""

from pathlib import Path
import tempfile
import unittest

import verify_conditional_event_ids


BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL"
BPMN_DI = "http://www.omg.org/spec/BPMN/20100524/DI"
DC = "http://www.omg.org/spec/DD/20100524/DC"
DI = "http://www.omg.org/spec/DD/20100524/DI"


def document(definition_ids):
    events = []
    for index, definition_id in enumerate(definition_ids, start=1):
        id_attribute = f' id="{definition_id}"' if definition_id is not None else ""
        events.append(
            f"""<bpmn:boundaryEvent id="Boundary_{index}" attachedToRef="Task">
          <bpmn:conditionalEventDefinition{id_attribute}>
            <bpmn:condition />
          </bpmn:conditionalEventDefinition>
        </bpmn:boundaryEvent>"""
        )
    return f"""<bpmn:definitions xmlns:bpmn="{BPMN}" xmlns:bpmndi="{BPMN_DI}"
    xmlns:dc="{DC}" xmlns:di="{DI}" id="Definitions">
  <bpmn:process id="Process">
    <bpmn:serviceTask id="Task" />
    {"".join(events)}
  </bpmn:process>
  <bpmndi:BPMNDiagram id="Diagram">
    <bpmndi:BPMNPlane id="Plane" bpmnElement="Process" />
  </bpmndi:BPMNDiagram>
</bpmn:definitions>"""


class ConditionalEventDefinitionIdTests(unittest.TestCase):
    def check_documents(self, source_ids, converted_ids):
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as directory:
            source_path = Path(directory) / "source.bpmn"
            converted_path = Path(directory) / "converted.bpmn"
            source_path.write_text(document(source_ids), encoding="utf-8")
            converted_path.write_text(document(converted_ids), encoding="utf-8")
            return verify_conditional_event_ids.check(source_path, converted_path)

    def test_generates_id_for_idless_source_and_preserves_unique_source_ids(self):
        failures = self.check_documents(
            (None, "Definition_Keep_One", "Definition_Keep_Two"),
            ("Definition_Generated", "Definition_Keep_One", "Definition_Keep_Two"),
        )

        self.assertEqual([], failures)

    def test_regenerates_nonunique_source_definition_ids(self):
        failures = self.check_documents(
            (None, "Definition_Duplicate", "Definition_Duplicate"),
            (
                "Definition_Generated",
                "Definition_Generated_One",
                "Definition_Generated_Two",
            ),
        )

        self.assertEqual([], failures)

    def test_rejects_preserving_a_nonunique_source_definition_id(self):
        failures = self.check_documents(
            (None, "Definition_Duplicate", "Definition_Duplicate"),
            (
                "Definition_Generated",
                "Definition_Duplicate",
                "Definition_Generated_Two",
            ),
        )

        self.assertIn(
            "nonunique source conditional event definition ID "
            "'Definition_Duplicate' was preserved",
            failures,
        )

    def test_rejects_a_missing_converted_definition_id(self):
        failures = self.check_documents(
            (None, "Definition_Keep"),
            (None, "Definition_Keep"),
        )

        self.assertIn(
            "converted conditional event definition 1 has no nonempty ID",
            failures,
        )

    def test_rejects_duplicate_converted_definition_ids(self):
        failures = self.check_documents(
            (None, "Definition_Keep"),
            ("Definition_Generated", "Definition_Generated"),
        )

        self.assertIn(
            "converted conditional event definition IDs are not unique",
            failures,
        )

    def test_rejects_a_converted_definition_id_used_by_another_bpmn_element(self):
        failures = self.check_documents(
            (None, "Definition_Keep"),
            ("Boundary_1", "Definition_Keep"),
        )

        self.assertIn(
            "conditional event definition 1 ID 'Boundary_1' "
            "is not unique in the XML document",
            failures,
        )

    def test_rejects_renaming_a_unique_source_definition_id(self):
        failures = self.check_documents(
            (None, "Definition_Keep_One", "Definition_Keep_Two"),
            ("Definition_Generated", "Definition_Renamed", "Definition_Keep_Two"),
        )

        self.assertIn(
            "unique source conditional event definition ID "
            "'Definition_Keep_One' was not preserved",
            failures,
        )

    def test_rejects_moving_unique_source_definition_ids_between_events(self):
        failures = self.check_documents(
            (None, "Definition_Keep_One", "Definition_Keep_Two"),
            ("Definition_Generated", "Definition_Keep_Two", "Definition_Keep_One"),
        )

        self.assertIn(
            "unique source conditional event definition ID "
            "'Definition_Keep_One' moved from event 'Boundary_2' to event "
            "'Boundary_3'",
            failures,
        )


if __name__ == "__main__":
    unittest.main()
