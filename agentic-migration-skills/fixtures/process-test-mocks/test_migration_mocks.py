import json
import re
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
from xml.etree import ElementTree as ET


REPO_ROOT = Path(__file__).resolve().parents[3]
FIXTURE = Path(__file__).resolve().parent
VALIDATOR_PATH = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/scripts/validate_migration_evidence.py"
)
sys.path.insert(0, str(VALIDATOR_PATH.parent))
import validate_migration_evidence as gate


def call_mock_boundary_validator(test, mapping):
    validator = getattr(gate, "test_mock_issues", None)
    if validator is None:
        raise unittest.SkipTest("The mock-boundary validator is supplied by PR #3228.")
    return validator(test, mapping, test["c8_ids"])


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
            "registerTaskListenerMock",
            "registerCallActivityMock",
            "verifyJavaDelegateMock",
            "verifyExecutionListenerMock",
            "verifyTaskListenerMock",
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
        auto_mock_model = (
            FIXTURE
            / "expected-c8/src/main/resources/processes/converted-c8-auto-mock-invoice.bpmn"
        ).read_text(encoding="utf-8")
        auto_mock_types = set(
            re.findall(
                r'<zeebe:(?:taskDefinition|executionListener)\b[^>]*type="([^"]+)"',
                auto_mock_model,
            )
        )
        self.assertTrue(job_types)
        self.assertTrue(job_types.issubset(declared_types))
        self.assertTrue(auto_mock_types)
        self.assertTrue(auto_mock_types.issubset(job_types))
        for job_type in job_types:
            with self.subTest(job_type=job_type):
                self.assertIn(
                    f"camunda.client.worker.override.{job_type}.enabled=false",
                    properties,
                )

    def test_task_listener_mapping_uses_its_declared_job_type(self):
        c7_test = (
            FIXTURE
            / "c7-source/src/test/java/org/camunda/example/processmock/InvoiceProcessTest.java"
        ).read_text(encoding="utf-8")
        c7_model = (
            FIXTURE / "c7-source/src/test/resources/task-listener.bpmn"
        ).read_text(encoding="utf-8")
        c8_test = (
            FIXTURE
            / "expected-c8/src/test/java/org/camunda/example/processmock/InvoiceProcessTest.java"
        ).read_text(encoding="utf-8")
        c8_model = (
            FIXTURE
            / "expected-c8/src/main/resources/processes/converted-c8-task-listener.bpmn"
        ).read_text(encoding="utf-8")

        listener_types = set(
            re.findall(r'<zeebe:taskListener\b[^>]*\btype="([^"]+)"', c8_model)
        )
        self.assertEqual({"review-created-listener"}, listener_types)
        self.assertIn('registerTaskListenerMock("reviewTaskListener")', c7_test)
        self.assertIn('delegateExpression="${reviewTaskListener}"', c7_model)
        self.assertIn(
            'JobSelectors.byJobType("review-created-listener")',
            c8_test,
        )
        self.assertIn("completeJobOfUserTaskListener", c8_test)
        self.assertNotIn('mockJobWorker("review-created-listener")', c8_test)

    def test_task_listener_conversion_does_not_invent_form_metadata(self):
        c7_model = ET.parse(
            FIXTURE / "c7-source/src/test/resources/task-listener.bpmn"
        ).getroot()
        c8_model = ET.parse(
            FIXTURE
            / "expected-c8/src/main/resources/processes/converted-c8-task-listener.bpmn"
        ).getroot()
        namespaces = {"bpmn": "http://www.omg.org/spec/BPMN/20100524/MODEL"}
        c7_task = c7_model.find(".//bpmn:userTask[@id='Task_Review']", namespaces)
        c8_task = c8_model.find(".//bpmn:userTask[@id='Task_Review']", namespaces)
        self.assertIsNotNone(c7_task)
        self.assertIsNotNone(c8_task)

        def has_form_metadata(task):
            form_names = {"formData", "formDefinition", "formKey", "formId", "externalReference"}
            for element in task.iter():
                if element.tag.rsplit("}", 1)[-1] in form_names:
                    return True
                if any(
                    name.rsplit("}", 1)[-1] in form_names for name in element.attrib
                ):
                    return True
            return False

        self.assertFalse(has_form_metadata(c7_task))
        self.assertFalse(has_form_metadata(c8_task))

    def test_form_free_listener_lint_suppresses_only_the_missing_form_rule(self):
        lint_config = json.loads(
            (FIXTURE / "expected-c8/.bpmnlintrc").read_text(encoding="utf-8")
        )
        self.assertEqual(
            [
                "bpmnlint:recommended",
                "plugin:camunda-compat/camunda-cloud-8-9",
            ],
            lint_config["extends"],
        )
        self.assertEqual(
            {"camunda-compat/user-task-definition": "off"},
            lint_config["rules"],
        )

    def test_delegate_answer_preserves_inputs_outputs_and_verification(self):
        c7_test = (
            FIXTURE
            / "c7-source/src/test/java/org/camunda/example/processmock/InvoiceProcessTest.java"
        ).read_text(encoding="utf-8")
        c8_test = (
            FIXTURE
            / "expected-c8/src/test/java/org/camunda/example/processmock/InvoiceProcessTest.java"
        ).read_text(encoding="utf-8")

        self.assertIn("doAnswer(", c7_test)
        self.assertIn('execution.getVariable("invoiceId")', c7_test)
        self.assertIn('execution.setVariable("notified", true)', c7_test)
        self.assertIn('.thenComplete(Map.of("notified", true))', c8_test)
        self.assertIn('.containsEntry("invoiceId", "I-1")', c8_test)
        self.assertIn('verify(notifyDelegate).execute(any(DelegateExecution.class))', c7_test)
        self.assertIn("assertThat(notify.getInvocations()).isEqualTo(1)", c8_test)

    def test_dmn_runs_the_deployed_decision_without_adding_a_mock(self):
        c7_test = (
            FIXTURE
            / "c7-source/src/test/java/org/camunda/example/processmock/InvoiceProcessTest.java"
        ).read_text(encoding="utf-8")
        c7_model = (
            FIXTURE / "c7-source/src/test/resources/decision-output.bpmn"
        ).read_text(encoding="utf-8")
        c7_dmn = (
            FIXTURE / "c7-source/src/test/resources/invoice-risk.dmn"
        ).read_text(encoding="utf-8")
        c8_test = (
            FIXTURE
            / "expected-c8/src/test/java/org/camunda/example/processmock/InvoiceProcessTest.java"
        ).read_text(encoding="utf-8")
        c8_model = (
            FIXTURE
            / "expected-c8/src/main/resources/processes/converted-c8-decision-output.bpmn"
        ).read_text(encoding="utf-8")
        result_map = 'Map.of("approved", true, "discount", "10%")'

        self.assertIn('camunda:decisionRef="invoice_risk"', c7_model)
        self.assertIn('camunda:mapDecisionResult="singleResult"', c7_model)
        self.assertIn('name="approved"', c7_dmn)
        self.assertIn('name="discount"', c7_dmn)
        self.assertIn(result_map, c7_test)
        self.assertIn('decisionId="invoice_risk" resultVariable="riskOutcome"', c8_model)
        self.assertNotIn("mockDmnDecision(", c8_test)
        self.assertIn(result_map, c8_test)
        self.assertIn(
            "MockExpressionManager",
            (FIXTURE / "c7-source/src/test/resources/camunda.cfg.xml").read_text(
                encoding="utf-8"
            ),
        )
        dmn_test = c7_test.split("public void preservesBusinessRuleResultShape()", 1)[1].split(
            "\n  private InvoiceService", 1
        )[0]
        self.assertNotIn("Mocks.register(", dmn_test)
        self.assertNotIn("registerTaskListenerMock(", dmn_test)

    def test_mock_modifier_is_derived_and_recorded_from_source(self):
        guidance = (
            REPO_ROOT
            / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/test-migration.md"
        ).read_text(encoding="utf-8")
        skill = (
            REPO_ROOT
            / "agentic-migration-skills/skills/migrate-c7-to-c8-code/SKILL.md"
        ).read_text(encoding="utf-8")

        for signal in (
            "`Mocks.register(...)`",
            "`CamundaMockito.registerMockInstance(...)`",
            "`@MockBean`",
            "`@MockitoBean`",
            "C7 `register...Mock` helper",
            "`autoMock(...)`",
        ):
            with self.subTest(signal=signal):
                self.assertIn(signal, guidance)
        self.assertIn("| `mocks` |", guidance)
        self.assertIn(
            "The Test Inventory records the `mocks` modifier in its `Signals` column",
            guidance,
        )
        self.assertIn(
            "the skill derives this modifier from source code, not from an input marker or a dependency alone.",
            " ".join(guidance.lower().split()),
        )
        self.assertIn(
            "the skill checks inherited and local setup and teardown methods.",
            " ".join(guidance.lower().split()),
        )
        self.assertIn(
            "| C7 mock library | No remaining test uses the library. | Remove the dependency. |",
            guidance,
        )
        step_two, step_three = skill.split("### Step 3: Execute Migration", 1)
        self.assertIn("detect mock signals from the original C7 test source", step_two)
        self.assertIn(
            "Record the source-derived `mocks` modifier in the Test Inventory's `Signals` column.",
            step_two,
        )
        self.assertIn("uses the source-derived modifier recorded in Step 2.", step_three)
        self.assertIn(
            "does not derive the modifier again after source transformations.",
            step_three,
        )
        self.assertIn("Users do not add it.", skill)

    def test_mock_modifier_ignores_shared_engine_configuration_and_dependencies(self):
        guidance = (
            REPO_ROOT
            / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/test-migration.md"
        ).read_text(encoding="utf-8")
        c7_test = (
            FIXTURE
            / "c7-source/src/test/java/org/camunda/example/processmock/InvoiceProcessTest.java"
        ).read_text(encoding="utf-8")
        c7_config = (
            FIXTURE / "c7-source/src/test/resources/camunda.cfg.xml"
        ).read_text(encoding="utf-8")
        c7_pom = (FIXTURE / "c7-source/pom.xml").read_text(encoding="utf-8")

        detector = re.search(r"(?m)^\| `mocks` \| (.*?) \|", guidance)
        self.assertIsNotNone(detector)
        self.assertIn("MockExpressionManager", c7_config)
        self.assertIn("c7-mockito", c7_pom)
        self.assertIn("Mocks.reset()", c7_test)
        self.assertIn(
            "Do not treat a `MockExpressionManager` setting, a mock-library dependency or import, or `Mocks.reset()` alone",
            guidance,
        )
        self.assertNotIn("MockExpressionManager", detector.group(1))
        self.assertNotIn("c7-mockito", detector.group(1))
        self.assertNotIn("Mocks.reset()", detector.group(1))
        self.assertNotIn("import", detector.group(1))

        dmn_test = c7_test.split("public void preservesBusinessRuleResultShape()", 1)[1].split(
            "\n  private InvoiceService", 1
        )[0]
        for mock_operation in (
            "Mocks.register(",
            "registerJavaDelegateMock(",
            "registerTaskListenerMock(",
            "autoMock(",
        ):
            with self.subTest(mock_operation=mock_operation):
                self.assertNotIn(mock_operation, dmn_test)

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

    def test_task_listener_verification_preserves_each_invocation_count(self):
        guidance = (
            REPO_ROOT
            / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/test-migration.md"
        ).read_text(encoding="utf-8")
        c7_test = (
            FIXTURE
            / "c7-source/src/test/java/org/camunda/example/processmock/InvoiceProcessTest.java"
        ).read_text(encoding="utf-8")
        c8_test = (
            FIXTURE
            / "expected-c8/src/test/java/org/camunda/example/processmock/InvoiceProcessTest.java"
        ).read_text(encoding="utf-8")

        for mapping in (
            'verifyTaskListenerMock("name").executed()',
            'verifyTaskListenerMock("name").executed(times(n))',
            'verifyTaskListenerMock("name").executedNever()',
        ):
            with self.subTest(mapping=mapping):
                self.assertIn(mapping, guidance)
        self.assertIn('verifyTaskListenerMock("reviewTaskListener").executed();', c7_test)
        self.assertIn(
            'verifyTaskListenerMock("reviewTaskListener").executed(times(2));',
            c7_test,
        )
        self.assertIn(
            'verifyTaskListenerMock("reviewTaskListener").executedNever();',
            c7_test,
        )
        self.assertIn("AtomicInteger listenerInvocations", c8_test)
        self.assertIn("assertThat(listenerInvocations.get()).isEqualTo(1)", c8_test)
        self.assertIn("assertThat(listenerInvocations.get()).isEqualTo(2)", c8_test)
        c7_twice = (
            FIXTURE / "c7-source/src/test/resources/task-listener-twice.bpmn"
        ).read_text(encoding="utf-8")
        c8_twice = (
            FIXTURE
            / "expected-c8/src/main/resources/processes/converted-c8-task-listener-twice.bpmn"
        ).read_text(encoding="utf-8")
        c7_never = (
            FIXTURE / "c7-source/src/test/resources/task-listener-never.bpmn"
        ).read_text(encoding="utf-8")
        c8_never = (
            FIXTURE
            / "expected-c8/src/main/resources/processes/converted-c8-task-listener-never.bpmn"
        ).read_text(encoding="utf-8")
        self.assertEqual(
            2,
            len(
                re.findall(
                    r'<camunda:taskListener\b[^>]*event="create"[^>]*delegateExpression="\$\{reviewTaskListener\}"',
                    c7_twice,
                )
            ),
        )
        self.assertEqual(
            2,
            len(
                re.findall(
                    r'<zeebe:taskListener\b[^>]*eventType="creating"[^>]*type="review-created-listener"',
                    c8_twice,
                )
            ),
        )
        self.assertIn('event="assignment"', c7_never)
        self.assertIn('delegateExpression="${reviewTaskListener}"', c7_never)
        self.assertIn('eventType="assigning"', c8_never)
        self.assertIn('type="review-assigned-listener"', c8_never)

        never_test = c8_test.split(
            "void preservesNeverExecutedTaskListenerAssertion()", 1
        )[1].split("\n  private ProcessInstanceEvent start", 1)[0]
        self.assertIn("CamundaAssert.assertThat(instance).isCompleted()", never_test)
        self.assertNotIn("completeJobOfUserTaskListener", never_test)

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

    def test_mock_boundary_validator_receives_mapped_c8_ids(self):
        mapping = json.loads(
            (FIXTURE / "negative/unapproved-worker-mock.json").read_text(encoding="utf-8")
        )
        test = mapping["tests"][0]
        received = []

        def validator(test_argument, mapping_argument, mapped_c8_ids):
            received.append((test_argument, mapping_argument, mapped_c8_ids))
            return []

        with patch.object(gate, "test_mock_issues", validator, create=True):
            self.assertEqual([], call_mock_boundary_validator(test, mapping))

        self.assertEqual([(test, mapping, test["c8_ids"])], received)

    def test_unapproved_worker_mock_fails_mock_boundary_check(self):
        mapping = json.loads(
            (FIXTURE / "negative/unapproved-worker-mock.json").read_text(encoding="utf-8")
        )
        test = mapping["tests"][0]
        issues = call_mock_boundary_validator(test, mapping)
        self.assertTrue(
            any("unapproved mock" in issue for issue in issues),
            f"Expected the mock-boundary check to reject the new worker mock, got: {issues}",
        )


if __name__ == "__main__":
    unittest.main()
