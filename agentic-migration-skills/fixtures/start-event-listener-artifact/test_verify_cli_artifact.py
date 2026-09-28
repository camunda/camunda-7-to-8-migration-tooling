import unittest
from contextlib import redirect_stderr
from io import StringIO
from pathlib import Path
from tempfile import TemporaryDirectory

from verify_cli_artifact import (
    CATEGORY,
    NAMESPACES,
    START_EVENT_ID,
    find_start_listener,
    get_source_listener_implementations,
    has_unsupported_start_listener,
    parse_arguments,
    resolve_java_executable,
    require_listener_findings,
    require_no_listener_findings,
)

SOURCE_NAME = "start-listener.bpmn"
DIRECTORY_SOURCE_NAME = str(Path("models") / SOURCE_NAME)
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


def write_converted_model(directory, bpmn_element, event_type):
    listener = ""
    if event_type is not None:
        listener = (
            "<bpmn:extensionElements><zeebe:executionListeners>"
            f'<zeebe:executionListener eventType="{event_type}" />'
            "</zeebe:executionListeners></bpmn:extensionElements>"
        )
    return write_bpmn_model(
        directory,
        f'<bpmn:{bpmn_element} id="Element_1">{listener}</bpmn:{bpmn_element}>',
    )


def write_bpmn_model(directory, process_contents):
    bpmn_namespace = NAMESPACES["bpmn"]
    zeebe_namespace = NAMESPACES["zeebe"]
    model = Path(directory) / "converted.bpmn"
    model.write_text(
        f'<bpmn:definitions xmlns:bpmn="{bpmn_namespace}" '
        f'xmlns:zeebe="{zeebe_namespace}"><bpmn:process id="Process_1">'
        f"{process_contents}"
        "</bpmn:process></bpmn:definitions>",
        encoding="utf-8",
    )
    return model


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
    def test_accepts_directory_relative_report_path(self):
        findings = [
            listener_finding(*listener, filename=DIRECTORY_SOURCE_NAME)
            for listener in SOURCE_LISTENERS
        ]
        require_listener_findings(
            findings,
            DIRECTORY_SOURCE_NAME,
            START_EVENT_ID,
            SOURCE_LISTENERS,
        )

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
                        filename=str(Path("other") / SOURCE_NAME),
                    ),
                    listener_finding(
                        *SOURCE_LISTENERS[1],
                        filename=DIRECTORY_SOURCE_NAME,
                    ),
                ],
                DIRECTORY_SOURCE_NAME,
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

    def test_m1_uses_the_same_validated_java_executable(self):
        guidance = " ".join(
            MODEL_MIGRATION_APPROACHES_PATH.read_text(encoding="utf-8")
            .lower()
            .split()
        )
        for sentence in (
            "resolve the `java` executable selected from `path` to an absolute path.",
            "run `-version` on that path.",
            "record its absolute path and actual major version.",
            "use the validated absolute executable for every converter invocation.",
            "never replace it with bare `java` or a different executable.",
            '"<java-executable>" -dfile.encoding=utf-8 -jar "<jar>" local',
        ):
            with self.subTest(sentence=sentence):
                self.assertIn(sentence, guidance)
        skill = " ".join(SKILL_PATH.read_text(encoding="utf-8").lower().split())
        self.assertIn(
            "the skill records the cli release tag, jar path, exact java executable path, "
            "and target version.",
            skill,
        )

    def test_m1_directory_input_uses_relative_report_path(self):
        guidance = " ".join(
            MODEL_MIGRATION_APPROACHES_PATH.read_text(encoding="utf-8")
            .lower()
            .split()
        )
        for sentence in (
            "for directory input, the cli reports each path relative to the input directory.",
            "the skill relativizes each source path against the input directory.",
            "the skill uses each report path exactly and never falls back to a basename match.",
        ):
            with self.subTest(sentence=sentence):
                self.assertIn(sentence, guidance)

    def test_target_deployment_confirms_profile_and_version(self):
        skill = " ".join(SKILL_PATH.read_text(encoding="utf-8").lower().split())
        approaches = " ".join(
            MODEL_MIGRATION_APPROACHES_PATH.read_text(encoding="utf-8")
            .lower()
            .split()
        )
        self.assertIn(
            "where the user authorizes a test-target deployment, the skill verifies the target "
            "version first.",
            skill,
        )
        self.assertIn(
            "`target deployment: verify the target version` in "
            "`references/model-migration-approaches.md`.",
            skill,
        )
        required_reference_rules = (
            "run `c8ctl which profile` and confirm the returned profile with the user.",
            "run `c8ctl get topology --profile=<name> --json` with that profile.",
            "read `gatewayversion` and every `brokers[].version` from the "
            "[orchestration cluster rest api topology response]",
            "the skill uses the confirmed profile or deployment client for both topology "
            "verification and deployment.",
            "when c8ctl is unavailable, use an authorized deployment client to read the same "
            "topology metadata.",
            "if that client cannot return the topology versions, then the skill blocks deployment "
            "and model readiness.",
            "compare the major and minor numbers of every reported version with the declared target.",
            "a version is missing or malformed, the topology has no brokers, or any major or minor "
            "number differs.",
        )
        for sentence in required_reference_rules:
            with self.subTest(sentence=sentence):
                self.assertIn(sentence, approaches)
        self.assertIn(
            "the target's reported major and minor version match the declared target.",
            approaches,
        )

    def test_artifact_probe_requires_an_explicit_java_path(self):
        with redirect_stderr(StringIO()):
            with self.assertRaises(SystemExit) as failure:
                parse_arguments(["--jar", "converter.jar", "--target-version", "8.9"])
        self.assertEqual(failure.exception.code, 2)

    def test_java_path_must_be_absolute_and_exist(self):
        with TemporaryDirectory() as directory:
            executable = Path(directory) / "java"
            executable.touch()
            self.assertEqual(
                resolve_java_executable(executable),
                str(executable.resolve()),
            )
            with self.assertRaises(SystemExit):
                resolve_java_executable(Path("java"))
            with self.assertRaises(SystemExit):
                resolve_java_executable(Path(directory) / "missing-java")

    def test_detects_direct_start_listener_on_start_event(self):
        with TemporaryDirectory() as directory:
            model = write_converted_model(directory, "startEvent", "start")
            self.assertTrue(has_unsupported_start_listener(model))

    def test_detects_listener_on_nested_start_event_after_another_start_event(self):
        with TemporaryDirectory() as directory:
            model = write_bpmn_model(
                directory,
                '<bpmn:startEvent id="First_Start" />'
                '<bpmn:subProcess id="SubProcess_1">'
                '<bpmn:startEvent id="Nested_Start">'
                "<bpmn:extensionElements><zeebe:executionListeners>"
                '<zeebe:executionListener eventType="end" />'
                '<zeebe:executionListener eventType="start" />'
                "</zeebe:executionListeners></bpmn:extensionElements>"
                "</bpmn:startEvent></bpmn:subProcess>",
            )
            self.assertTrue(has_unsupported_start_listener(model))

    def test_ignores_other_listener_placements(self):
        cases = (
            ("startEvent", "end"),
            ("serviceTask", "start"),
            ("startEvent", None),
        )
        for bpmn_element, event_type in cases:
            with self.subTest(bpmn_element=bpmn_element, event_type=event_type):
                with TemporaryDirectory() as directory:
                    model = write_converted_model(directory, bpmn_element, event_type)
                    self.assertFalse(has_unsupported_start_listener(model))

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
