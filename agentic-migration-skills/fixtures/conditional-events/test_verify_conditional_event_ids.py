"""Regression tests for conditional event definition ID preservation."""

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

    def test_preserves_each_unique_nonempty_source_definition_id(self):
        failures = self.check_documents(
            (None, "Definition_Keep_One", "Definition_Keep_Two"),
            ("Definition_Generated", "Definition_Keep_One", "Definition_Keep_Two"),
        )

        self.assertEqual([], failures)

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


if __name__ == "__main__":
    unittest.main()
