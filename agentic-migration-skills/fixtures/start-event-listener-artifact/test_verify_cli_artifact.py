import unittest
from pathlib import Path

from verify_cli_artifact import (
    CATEGORY,
    find_start_listener,
    get_source_listener_implementation,
    require_listener_finding,
    require_no_listener_findings,
)

IMPLEMENTATION = "${startListener}"


def listener_finding(**overrides):
    return {
        "filename": "start-listener.bpmn",
        "elementId": "Start_Listener",
        "messageId": CATEGORY,
        "severity": "TASK",
        "message": (
            "Execution Listener at 'start' with implementation "
            f"'delegateExpression' '{IMPLEMENTATION}' cannot be transformed."
        ),
        **overrides,
    }


class CliFindingValidationTest(unittest.TestCase):
    def test_accepts_finding_for_exact_input_and_start_event(self):
        require_listener_finding(
            [listener_finding()], "start-listener.bpmn", IMPLEMENTATION
        )

    def test_rejects_same_basename_from_a_different_path(self):
        with self.assertRaises(SystemExit):
            require_listener_finding(
                [listener_finding(filename="other/start-listener.bpmn")],
                "start-listener.bpmn",
                IMPLEMENTATION,
            )

    def test_rejects_wrong_start_event_id(self):
        with self.assertRaises(SystemExit):
            require_listener_finding(
                [listener_finding(elementId="Other_Start")],
                "start-listener.bpmn",
                IMPLEMENTATION,
            )

    def test_rejects_downgraded_or_missing_severity(self):
        for severity in ("WARNING", "REVIEW", "INFO", None):
            with self.subTest(severity=severity):
                finding = listener_finding()
                if severity is None:
                    finding.pop("severity")
                else:
                    finding["severity"] = severity
                with self.assertRaises(SystemExit):
                    require_listener_finding(
                        [finding], "start-listener.bpmn", IMPLEMENTATION
                    )

    def test_rejects_message_without_source_listener_implementation(self):
        for message in ("Execution Listener cannot be transformed.", None):
            with self.subTest(message=message):
                with self.assertRaises(SystemExit):
                    require_listener_finding(
                        [listener_finding(message=message)],
                        "start-listener.bpmn",
                        IMPLEMENTATION,
                    )

    def test_reads_source_listener_implementation(self):
        source = (
            Path(__file__).resolve().parent
            / "c7-source"
            / "start-listener.bpmn"
        )
        self.assertEqual(IMPLEMENTATION, get_source_listener_implementation(source))

    def test_control_rejects_a_finding_even_when_filename_differs(self):
        report = [
            {
                "filename": "other/no-listener.bpmn",
                "elementId": "Start_Listener",
                "messageId": CATEGORY,
            }
        ]

        with self.assertRaises(SystemExit):
            require_no_listener_findings(report)

    def test_rejects_invalid_report_shapes(self):
        with self.assertRaises(SystemExit):
            find_start_listener({"messageId": CATEGORY})
        with self.assertRaises(SystemExit):
            find_start_listener([None])


if __name__ == "__main__":
    unittest.main()
