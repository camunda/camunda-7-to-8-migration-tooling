import unittest

from verify_cli_artifact import (
    CATEGORY,
    find_start_listener,
    require_listener_finding,
    require_no_listener_findings,
)


class CliFindingValidationTest(unittest.TestCase):
    def test_accepts_finding_for_exact_input_and_start_event(self):
        report = [
            {
                "filename": "start-listener.bpmn",
                "elementId": "Start_Listener",
                "messageId": CATEGORY,
            }
        ]

        require_listener_finding(report, "start-listener.bpmn")

    def test_rejects_same_basename_from_a_different_path(self):
        report = [
            {
                "filename": "other/start-listener.bpmn",
                "elementId": "Start_Listener",
                "messageId": CATEGORY,
            }
        ]

        with self.assertRaises(SystemExit):
            require_listener_finding(report, "start-listener.bpmn")

    def test_rejects_wrong_start_event_id(self):
        report = [
            {
                "filename": "start-listener.bpmn",
                "elementId": "Other_Start",
                "messageId": CATEGORY,
            }
        ]

        with self.assertRaises(SystemExit):
            require_listener_finding(report, "start-listener.bpmn")

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
