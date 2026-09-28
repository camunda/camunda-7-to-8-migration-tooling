import unittest
from pathlib import Path

from verify_cli_artifact import (
    CATEGORY,
    START_EVENT_ID,
    find_start_listener,
    get_source_listener_implementations,
    require_listener_findings,
    require_no_listener_findings,
)

SOURCE_NAME = "start-listener.bpmn"
SOURCE_LISTENERS = (
    ("delegateExpression", "${startListener}"),
    ("class", "com.example.StartListener"),
)
SKILL_PATH = (
    Path(__file__).resolve().parents[2]
    / "skills/migrate-c7-to-c8-code/SKILL.md"
)
MODEL_MIGRATION_APPROACHES_PATH = (
    Path(__file__).resolve().parents[2]
    / "skills/migrate-c7-to-c8-code/references/model-migration-approaches.md"
)


def listener_finding(implementation_attribute, implementation_value, **overrides):
    return {
        "filename": SOURCE_NAME,
        "elementId": START_EVENT_ID,
        "messageId": CATEGORY,
        "severity": "TASK",
        "message": (
            "Execution Listener at 'start' with implementation "
            f"'{implementation_attribute}' '{implementation_value}' "
            "cannot be transformed."
        ),
        **overrides,
    }


class CliFindingValidationTest(unittest.TestCase):
    def test_accepts_one_finding_for_each_source_listener(self):
        require_listener_findings(
            [listener_finding(*listener) for listener in SOURCE_LISTENERS],
            SOURCE_NAME,
            START_EVENT_ID,
            SOURCE_LISTENERS,
        )

    def test_rejects_same_basename_from_a_different_path(self):
        with self.assertRaises(SystemExit):
            require_listener_findings(
                [
                    listener_finding(
                        *SOURCE_LISTENERS[0],
                        filename="other/start-listener.bpmn",
                    ),
                    listener_finding(*SOURCE_LISTENERS[1]),
                ],
                SOURCE_NAME,
                START_EVENT_ID,
                SOURCE_LISTENERS,
            )

    def test_rejects_wrong_start_event_id(self):
        with self.assertRaises(SystemExit):
            require_listener_findings(
                [
                    listener_finding(*SOURCE_LISTENERS[0], elementId="Other_Start"),
                    listener_finding(*SOURCE_LISTENERS[1]),
                ],
                SOURCE_NAME,
                START_EVENT_ID,
                SOURCE_LISTENERS,
            )

    def test_rejects_a_report_missing_one_source_listener(self):
        with self.assertRaises(SystemExit):
            require_listener_findings(
                [listener_finding(*SOURCE_LISTENERS[0])],
                SOURCE_NAME,
                START_EVENT_ID,
                SOURCE_LISTENERS,
            )

    def test_rejects_duplicate_finding_instead_of_missing_listener(self):
        with self.assertRaises(SystemExit):
            require_listener_findings(
                [
                    listener_finding(*SOURCE_LISTENERS[0]),
                    listener_finding(*SOURCE_LISTENERS[0]),
                ],
                SOURCE_NAME,
                START_EVENT_ID,
                SOURCE_LISTENERS,
            )

    def test_rejects_finding_for_an_unknown_implementation(self):
        with self.assertRaises(SystemExit):
            require_listener_findings(
                [
                    listener_finding("delegateExpression", "${otherListener}"),
                    listener_finding(*SOURCE_LISTENERS[1]),
                ],
                SOURCE_NAME,
                START_EVENT_ID,
                SOURCE_LISTENERS,
            )

    def test_matches_repeated_implementations_by_finding_count(self):
        listener = ("class", "com.example.Duplicate")
        findings = [listener_finding(*listener), listener_finding(*listener)]
        require_listener_findings(
            findings,
            SOURCE_NAME,
            START_EVENT_ID,
            [listener, listener],
        )
        with self.assertRaises(SystemExit):
            require_listener_findings(
                findings[:1],
                SOURCE_NAME,
                START_EVENT_ID,
                [listener, listener],
            )

    def test_rejects_downgraded_or_missing_severity(self):
        for severity in ("WARNING", "REVIEW", "INFO", None):
            with self.subTest(severity=severity):
                findings = [
                    listener_finding(*SOURCE_LISTENERS[0]),
                    listener_finding(*SOURCE_LISTENERS[1]),
                ]
                if severity is None:
                    findings[0].pop("severity")
                else:
                    findings[0]["severity"] = severity
                with self.assertRaises(SystemExit):
                    require_listener_findings(
                        findings,
                        SOURCE_NAME,
                        START_EVENT_ID,
                        SOURCE_LISTENERS,
                    )

    def test_m1_guidance_requires_task_severity_for_listener_matches(self):
        required_sentences = (
            "the skill accepts a start-listener finding as a converter match only when "
            "its severity is `task`.",
            "if its severity is missing or different, then the skill marks compatibility **blocked**.",
            "the mismatched finding does not count as a converter match.",
            "if a source listener entry lacks a matching `task` finding, then the skill adds a "
            "source-derived `task` row.",
            "that row does not count as a converter match.",
        )
        for path in (SKILL_PATH, MODEL_MIGRATION_APPROACHES_PATH):
            guidance = " ".join(path.read_text(encoding="utf-8").lower().split())
            with self.subTest(path=path):
                for sentence in required_sentences:
                    self.assertIn(
                        sentence,
                        guidance,
                        f"{path} is missing {sentence!r}",
                    )

    def test_rejects_message_without_source_listener_implementation(self):
        for message in ("Execution Listener cannot be transformed.", None):
            with self.subTest(message=message):
                with self.assertRaises(SystemExit):
                    require_listener_findings(
                        [
                            listener_finding(
                                *SOURCE_LISTENERS[0], message=message
                            ),
                            listener_finding(*SOURCE_LISTENERS[1]),
                        ],
                        SOURCE_NAME,
                        START_EVENT_ID,
                        SOURCE_LISTENERS,
                    )

    def test_reads_all_source_listener_implementations(self):
        source = (
            Path(__file__).resolve().parent
            / "c7-source"
            / "start-listener.bpmn"
        )
        self.assertEqual(
            list(SOURCE_LISTENERS),
            get_source_listener_implementations(source),
        )

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
