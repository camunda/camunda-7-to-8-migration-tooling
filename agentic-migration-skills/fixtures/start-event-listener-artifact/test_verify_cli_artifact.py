import unittest
from pathlib import Path
from subprocess import CompletedProcess
from tempfile import TemporaryDirectory
from unittest.mock import patch

from verify_cli_artifact import (
    CATEGORY,
    EXPECTED_MESSAGES,
    NAMESPACES,
    parse_java_major,
    read_java_major,
    require_runtime_matrix,
    require_supported_java,
    verify_converted_copy,
    verify_findings,
    verify_json_option,
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

            unwrapped = model.replace("<zeebe:executionListeners>", "").replace(
                "</zeebe:executionListeners>", ""
            )
            converted.write_text(unwrapped, encoding="utf-8")
            with self.assertRaises(SystemExit):
                verify_converted_copy(converted)

            converted.write_text(model.replace('eventType="start"', 'eventType="end"'), encoding="utf-8")
            verify_converted_copy(converted)


class JavaRuntimeTest(unittest.TestCase):
    def test_parses_current_and_legacy_java_versions(self):
        for output, expected in (
            ('openjdk version "21.0.8" 2026-07-21', 21),
            ('openjdk version "26.0.2.1" 2026-07-21', 26),
            ('openjdk version "27" 2026-09-15', 27),
            ('java version "1.8.0_412"', 8),
        ):
            with self.subTest(output=output):
                self.assertEqual(parse_java_major(output), expected)

    def test_rejects_unrecognized_java_version_output(self):
        with self.assertRaisesRegex(SystemExit, "Could not parse"):
            parse_java_major("Java runtime information is unavailable.")

    def test_reads_version_from_the_requested_executable(self):
        java = Path("/opt/jdk-21/bin/java")
        with patch("verify_cli_artifact.subprocess.run") as run:
            run.return_value = CompletedProcess(
                [str(java), "-version"],
                0,
                stdout="",
                stderr='openjdk version "21.0.8" 2026-07-21',
            )
            self.assertEqual(read_java_major(java), 21)
        run.assert_called_once_with(
            [str(java), "-version"],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_reports_java_version_command_failure(self):
        java = Path("/opt/jdk-21/bin/java")
        with patch("verify_cli_artifact.subprocess.run") as run:
            run.return_value = CompletedProcess(
                [str(java), "-version"],
                1,
                stdout="",
                stderr="runtime failed",
            )
            with self.assertRaisesRegex(
                SystemExit,
                "failed for /opt/jdk-21/bin/java with exit code 1",
            ):
                read_java_major(java)

    def test_accepts_a_release_with_json_report_support(self):
        java = Path("/opt/jdk-21/bin/java")
        jar = Path("/tmp/camunda-7-to-8-diagram-converter-cli-0.3.7.jar")
        with patch("verify_cli_artifact.subprocess.run") as run:
            run.return_value = CompletedProcess(
                [str(java), "-jar", str(jar), "local", "--help"],
                0,
                stdout="Options: --json --xlsx",
                stderr="",
            )
            verify_json_option(java, jar, 21)

    def test_reports_missing_json_option_as_a_cli_capability_failure(self):
        java = Path("/opt/jdk-21/bin/java")
        jar = Path("/tmp/camunda-7-to-8-diagram-converter-cli-0.3.6.jar")
        with patch("verify_cli_artifact.subprocess.run") as run:
            run.return_value = CompletedProcess(
                [str(java), "-jar", str(jar), "local", "--help"],
                0,
                stdout="Options: --csv --xlsx",
                stderr="",
            )
            with self.assertRaisesRegex(
                SystemExit,
                r"Use release 0\.3\.9 or later.*CLI capability failure, not a Java",
            ):
                verify_json_option(java, jar, 21)

    def test_rejects_versions_below_the_cli_minimum_with_alternatives(self):
        with self.assertRaisesRegex(SystemExit, "Java 21 or later"):
            require_supported_java(20, Path("/opt/jdk-20/bin/java"))
        with self.assertRaisesRegex(SystemExit, "M2 .* M3"):
            require_supported_java(20, Path("/opt/jdk-20/bin/java"))

    def test_runtime_matrix_requires_java_21_and_java_26_or_later(self):
        require_runtime_matrix([21, 26])
        with self.assertRaisesRegex(SystemExit, "must include Java 21"):
            require_runtime_matrix([26])
        with self.assertRaisesRegex(SystemExit, "must include Java 26 or later"):
            require_runtime_matrix([21, 25])


if __name__ == "__main__":
    unittest.main()
