import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from verify_cli_artifact import (
    CATEGORY,
    EXPECTED_MESSAGES,
    NAMESPACES,
    verify_converted_copy,
    verify_findings,
)


def findings():
    return [
        {
            "filename": "models/start-listener.bpmn",
            "elementId": "Start_Listener",
            "messageId": CATEGORY,
            "severity": "TASK",
            "message": f"Execution Listener at 'start' with implementation {value} cannot be transformed.",
        }
        for value in EXPECTED_MESSAGES
    ]


class SelectedArtifactTest(unittest.TestCase):
    def test_accepts_windows_relative_path(self):
        report = findings()
        for finding in report:
            finding["filename"] = r"models\start-listener.bpmn"
        verify_findings(report)

    def test_requires_one_blocking_finding_per_source_listener(self):
        verify_findings(findings())

        for change in (
            lambda rows: rows.pop(),
            lambda rows: rows[0].update(severity="WARNING"),
            lambda rows: rows[0].pop("severity"),
            lambda rows: rows[0].update(filename="other/start-listener.bpmn"),
            lambda rows: rows[0].update(filename=r"other\start-listener.bpmn"),
            lambda rows: rows[0].update(message="Execution Listener cannot be transformed."),
            lambda rows: rows.append(rows[0].copy()),
            lambda rows: rows[0].update(filename="controls/no-listener.bpmn"),
        ):
            with self.subTest(change=change):
                report = findings()
                change(report)
                with self.assertRaises(SystemExit):
                    verify_findings(report)

    def test_rejects_invalid_start_placement_in_nested_process(self):
        with TemporaryDirectory() as directory:
            converted = Path(directory) / "converted.bpmn"
            model = (
                f'<bpmn:definitions xmlns:bpmn="{NAMESPACES["bpmn"]}" '
                f'xmlns:zeebe="{NAMESPACES["zeebe"]}">'
                '<bpmn:process id="Process_1"><bpmn:subProcess id="SubProcess_1">'
                '<bpmn:startEvent id="Nested_Start"><bpmn:extensionElements>'
                '<zeebe:executionListeners><zeebe:executionListener eventType="start" />'
                "</zeebe:executionListeners></bpmn:extensionElements></bpmn:startEvent>"
                "</bpmn:subProcess></bpmn:process></bpmn:definitions>"
            )
            converted.write_text(model, encoding="utf-8")
            with self.assertRaises(SystemExit):
                verify_converted_copy(converted)

            converted.write_text(model.replace('eventType="start"', 'eventType="end"'), encoding="utf-8")
            verify_converted_copy(converted)


if __name__ == "__main__":
    unittest.main()
