import json
import re
import sys
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
FIXTURE = Path(__file__).resolve().parent
VALIDATOR_PATH = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/scripts/validate_migration_evidence.py"
)
sys.path.insert(0, str(VALIDATOR_PATH.parent))
import validate_migration_evidence as gate


class ProcessTestMocksFixtureTest(unittest.TestCase):
    def test_c7_fixture_covers_required_mock_apis(self):
        source = (
            FIXTURE
            / "c7-source/src/test/java/org/camunda/example/processmock/InvoiceProcessTest.java"
        ).read_text(encoding="utf-8")
        for api in (
            "Mocks.register",
            "registerJavaDelegateMock",
            "onExecutionSetVariables",
            "onExecutionThrowBpmnError",
            "onExecutionThrowException",
            "autoMock",
            "registerExecutionListenerMock",
            "registerCallActivityMock",
            "verifyJavaDelegateMock",
            "verifyExecutionListenerMock",
        ):
            with self.subTest(api=api):
                self.assertIn(api, source)

    def test_spring_harness_disables_each_mocked_job_type(self):
        test_source = (
            FIXTURE
            / "expected-c8/src/test/java/org/camunda/example/processmock/InvoiceProcessTest.java"
        ).read_text(encoding="utf-8")
        properties = (
            FIXTURE / "expected-c8/src/test/resources/application.properties"
        ).read_text(encoding="utf-8")
        models = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (FIXTURE / "expected-c8/src/main/resources/processes").glob("*.bpmn")
        )

        job_types = set(re.findall(r'mockJobWorker\("([^"]+)"\)', test_source))
        declared_types = set(
            re.findall(
                r'<zeebe:(?:taskDefinition|executionListener)\b[^>]*type="([^"]+)"',
                models,
            )
        )
        self.assertTrue(job_types)
        self.assertTrue(job_types.issubset(declared_types))
        for job_type in job_types:
            with self.subTest(job_type=job_type):
                self.assertIn(
                    f"camunda.client.worker.override.{job_type}.enabled=false",
                    properties,
                )

    def test_listener_mappings_use_the_listener_job_type(self):
        guidance = (
            REPO_ROOT
            / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/test-migration.md"
        ).read_text(encoding="utf-8")
        test_source = (
            FIXTURE
            / "expected-c8/src/test/java/org/camunda/example/processmock/InvoiceProcessTest.java"
        ).read_text(encoding="utf-8")
        models = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (FIXTURE / "expected-c8/src/main/resources/processes").glob("*.bpmn")
        )

        task_types = set(
            re.findall(r'<zeebe:taskDefinition\b[^>]*\btype="([^"]+)"', models)
        )
        listener_types = set(
            re.findall(r'<zeebe:executionListener\b[^>]*\btype="([^"]+)"', models)
        )
        mocked_types = set(re.findall(r'mockJobWorker\("([^"]+)"\)', test_source))

        self.assertTrue(listener_types)
        self.assertTrue(listener_types.isdisjoint(task_types))
        self.assertTrue(listener_types.issubset(mocked_types))
        self.assertIn('processTestContext.mockJobWorker("notify-start")', test_source)
        self.assertIn("zeebe:executionListener/@type", guidance)
        self.assertIn("zeebe:taskDefinition/@type", guidance)
        self.assertIn("the skill records that mock in `mocks.c7`", guidance.lower())
        self.assertIn(
            "the skill leaves `mocks.c8` without a corresponding mock",
            guidance.lower(),
        )

    def test_verification_mappings_keep_invocation_counts_and_wait(self):
        guidance = (
            REPO_ROOT
            / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/test-migration.md"
        ).read_text(encoding="utf-8")
        test_source = (
            FIXTURE
            / "expected-c8/src/test/java/org/camunda/example/processmock/InvoiceProcessTest.java"
        ).read_text(encoding="utf-8")

        for mapping in (
            'verifyJavaDelegateMock("name").executed()',
            'verifyJavaDelegateMock("name").executed(times(n))',
            'verifyJavaDelegateMock("name").executedNever()',
            'verifyExecutionListenerMock("name").executed()',
            'verifyExecutionListenerMock("name").executed(times(n))',
            'verifyExecutionListenerMock("name").executedNever()',
            "assertThat(mock.getInvocations()).isEqualTo(1)",
            "assertThat(mock.getInvocations()).isEqualTo(n)",
            "assertThat(mock.getInvocations()).isZero()",
        ):
            with self.subTest(mapping=mapping):
                self.assertIn(mapping, guidance)

        waiting_assertion = test_source.index("CamundaAssert.assertThat(instance)")
        listener_count = test_source.index("assertThat(notifyStart.getInvocations())")
        self.assertLess(waiting_assertion, listener_count)

    def test_expected_build_removes_c7_mock_libraries_but_keeps_mockito(self):
        pom = (FIXTURE / "expected-c8/pom.xml").read_text(encoding="utf-8")
        for artifact in ("camunda-platform-7-mockito", "c7-mockito", "camunda-bpm-mockito"):
            with self.subTest(artifact=artifact):
                self.assertNotIn(artifact, pom)
        self.assertIn("<artifactId>mockito-core</artifactId>", pom)

        c7_config = (FIXTURE / "c7-source/src/test/resources/camunda.cfg.xml").read_text(
            encoding="utf-8"
        )
        self.assertIn("MockExpressionManager", c7_config)
        self.assertFalse((FIXTURE / "expected-c8/src/test/resources/camunda.cfg.xml").exists())

    def test_unapproved_worker_mock_fails_mock_boundary_check(self):
        mapping = json.loads(
            (FIXTURE / "negative/unapproved-worker-mock.json").read_text(encoding="utf-8")
        )
        test = mapping["tests"][0]
        issues = gate.test_mock_issues(test, mapping)
        self.assertTrue(
            any("unapproved mock" in issue for issue in issues),
            f"Expected the mock-boundary check to reject the new worker mock, got: {issues}",
        )


if __name__ == "__main__":
    unittest.main()
