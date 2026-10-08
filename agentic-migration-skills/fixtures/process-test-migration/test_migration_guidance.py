from pathlib import Path
import json
import re
import unittest
import xml.etree.ElementTree as ET


FIXTURE_ROOT = Path(__file__).resolve().parent
REPO_ROOT = FIXTURE_ROOT.parents[2]
C7_TESTS = FIXTURE_ROOT / "c7-source/src/test/java/com/camunda/fixture/tests"
C7_RESOURCES = FIXTURE_ROOT / "c7-source/src/test/resources"
C8_TESTS = FIXTURE_ROOT / "expected-c8/src/test/java/com/camunda/fixture/tests"
C8_RESOURCES = FIXTURE_ROOT / "expected-c8/src/test/resources"
SKILL_PATH = REPO_ROOT / "agentic-migration-skills/skills/migrate-c7-to-c8-code/SKILL.md"
REFERENCE_PATH = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/test-migration.md"
)
CHECKLIST_PATH = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/code-transform-checklist.md"
)
HARNESS_PATTERN = (
    REPO_ROOT
    / "code-conversion/patterns/40-test-assertions/20-test-setup/10-junit-harness.md"
)
ASSERTION_PATTERN = (
    REPO_ROOT
    / "code-conversion/patterns/40-test-assertions/10-assertions/80-assertion-mapping.md"
)
MESSAGE_PATTERN = (
    REPO_ROOT
    / "code-conversion/patterns/40-test-assertions/10-assertions/50-message.md"
)
PATTERN_SOURCES_PATH = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/pattern-catalog-sources.md"
)
TEST_CLASSES = ("ImplicitDeploymentTest", "OrderProcessTest", "MessageProcessTest")
EXPECTED_PROCESS_TEST_IDS = {
    "ImplicitDeploymentTest#testStartsImplicitlyDeployedProcess",
    "OrderProcessTest#approvesOrder",
    "OrderProcessTest#escalatesAfterOneDay",
    "OrderProcessTest#continuesAfterAsync",
    "OrderProcessTest#failsWhenDelegateThrows",
    "MessageProcessTest#correlatesMessage",
}
MAVEN_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}


def test_method_names(source):
    return set(re.findall(r"(?m)^\s*(?:public\s+)?void\s+(\w+)\s*\(", source))


def annotated_test_method_names(source):
    return set(
        re.findall(
            r"(?m)^\s*@Test\b[^\n]*\n\s*(?:public\s+)?void\s+(\w+)\s*\(",
            source,
        )
    )


def c7_test_method_names(source):
    if "extends ProcessEngineTestCase" in source:
        return set(re.findall(r"(?m)^\s*public\s+void\s+(test\w*)\s*\(", source))
    return annotated_test_method_names(source)


def diagram_shapes(bpmn):
    return sorted(
        re.findall(r'<bpmndi:BPMN(?:Shape|Edge)\b[^>]*\bbpmnElement="([^"]+)"', bpmn)
    )


def extract_test_deployment_resources(source):
    resources_by_annotation = []
    for annotation in re.finditer(
        r"@TestDeployment\b\s*(?:\((?P<arguments>[^)]*)\))?",
        source,
        flags=re.DOTALL,
    ):
        arguments = annotation.group("arguments")
        if arguments is None:
            resources_by_annotation.append([])
            continue

        resource_value = re.search(
            r'\bresources\s*=\s*(?P<value>\{[^{}]*\}|"(?:\\.|[^"\\])*")',
            arguments,
            flags=re.DOTALL,
        )
        if resource_value is None:
            resources_by_annotation.append([])
            continue

        resources_by_annotation.append(
            re.findall(r'"((?:\\.|[^"\\])*)"', resource_value.group("value"))
        )

    return resources_by_annotation


class ProcessTestMigrationFixtureTest(unittest.TestCase):
    def test_c7_harnesses_and_required_cases_are_present(self):
        implicit = (C7_TESTS / "ImplicitDeploymentTest.java").read_text()
        order = (C7_TESTS / "OrderProcessTest.java").read_text()
        message = (C7_TESTS / "MessageProcessTest.java").read_text()

        self.assertIn("extends ProcessEngineTestCase", implicit)
        self.assertRegex(implicit, r"(?m)^@Deployment$")
        self.assertIn("@Rule", order)
        self.assertIn("ProcessEngineRule", order)
        self.assertIn("@ExtendWith(ProcessEngineExtension.class)", message)
        self.assertIn("@Deployment(resources =", order)
        self.assertIn("execute(job())", order)
        self.assertIn("isNotWaitingAt(", order)
        self.assertIn("@Test(expected = IllegalStateException.class)", order)
        self.assertIn("correlateMessage(", message)

    def test_process_test_class_and_method_names_are_preserved(self):
        for class_name in TEST_CLASSES:
            with self.subTest(class_name=class_name):
                c7_source = (C7_TESTS / f"{class_name}.java").read_text()
                c8_source = (C8_TESTS / f"{class_name}.java").read_text()
                c7_methods = c7_test_method_names(c7_source)
                self.assertTrue(c7_methods)
                self.assertEqual(c7_methods, annotated_test_method_names(c8_source))
                self.assertIn("@CamundaProcessTest", c8_source)

    def test_junit3_lifecycle_overrides_map_to_junit5_callbacks(self):
        c7_source = (C7_TESTS / "ImplicitDeploymentTest.java").read_text()
        c8_source = (C8_TESTS / "ImplicitDeploymentTest.java").read_text()

        self.assertIn("protected void setUp() throws Exception", c7_source)
        self.assertIn("protected void tearDown() throws Exception", c7_source)
        self.assertRegex(c8_source, r"@BeforeEach\s+void setUp\(\)")
        self.assertRegex(c8_source, r"@AfterEach\s+void tearDown\(\)")
        self.assertNotIn("super.setUp()", c8_source)
        self.assertNotIn("super.tearDown()", c8_source)
        self.assertNotIn("@Override", c8_source)

    def test_cpt_tests_cover_semantic_changes(self):
        order = (C8_TESTS / "OrderProcessTest.java").read_text()
        message = (C8_TESTS / "MessageProcessTest.java").read_text()

        self.assertIn("hasNoActiveElements(\"Task_Escalate\")", order)
        self.assertIn("hasActiveElements(\"Task_Approve\")", order)
        self.assertIn("increaseTime(Duration.ofDays(1))", order)
        self.assertNotIn("execute(job())", order)
        async_test = order.split("void continuesAfterAsync()", maxsplit=1)[1].split("@Test", maxsplit=1)[0]
        self.assertNotIn("completeJob(", async_test)
        self.assertIn("mockJobWorker(\"failingDelegate\")", order)
        self.assertIn("mockJobWorker(\"noopDelegate\")", order)
        self.assertIn("hasActiveIncidents()", order)
        self.assertIn("newCorrelateMessageCommand()", message)
        self.assertIn('.correlationKey("subscription-key")', message)
        self.assertIn('.businessId("legacy-business-key")', message)
        self.assertIn("processInstance.getBusinessId()", message)

    def test_test_deployment_extraction_supports_single_and_array_values(self):
        source = """
        @TestDeployment(resources = "single.bpmn")
        @TestDeployment(resources = {"first.bpmn", "second.form"})
        """

        self.assertEqual(
            [["single.bpmn"], ["first.bpmn", "second.form"]],
            extract_test_deployment_resources(source),
        )

    def test_test_deployment_extraction_tracks_annotations_without_resources(self):
        self.assertEqual(
            [[]],
            extract_test_deployment_resources("@TestDeployment"),
        )

    def test_every_cpt_deployment_resolves_to_a_converted_copy(self):
        deployment_paths = []
        for class_name in TEST_CLASSES:
            source = (C8_TESTS / f"{class_name}.java").read_text()
            resources_by_annotation = extract_test_deployment_resources(source)
            self.assertTrue(resources_by_annotation, f"{class_name} has no @TestDeployment")
            for resources in resources_by_annotation:
                self.assertTrue(resources, f"{class_name} has an entry without resources")
                deployment_paths.extend(resources)

        self.assertEqual(
            {
                "com/camunda/fixture/tests/converted-c8-ImplicitDeploymentTest.bpmn",
                "com/camunda/fixture/tests/converted-c8-process-test-cases.bpmn",
            },
            set(deployment_paths),
        )
        for resource in deployment_paths:
            with self.subTest(resource=resource):
                self.assertTrue(Path(resource).name.startswith("converted-c8-"))
                self.assertTrue((C8_RESOURCES / resource).is_file())
                source_name = Path(resource).name.removeprefix("converted-c8-")
                self.assertTrue((C7_RESOURCES / Path(resource).parent / source_name).is_file())

        expected_sources = "\n".join(path.read_text() for path in C8_TESTS.glob("*Test.java"))
        self.assertNotRegex(expected_sources, r"(?<!converted-c8-)process-test-cases\.bpmn")
        self.assertNotRegex(expected_sources, r"(?<!converted-c8-)ImplicitDeploymentTest\.bpmn")
        self.assertTrue(
            (C7_RESOURCES / "com/camunda/fixture/tests/ImplicitDeploymentTest.bpmn").is_file()
        )

    def test_expected_build_uses_cpt_and_vintage_without_camunda_7_engine(self):
        pom_path = FIXTURE_ROOT / "expected-c8/pom.xml"
        pom = ET.parse(pom_path).getroot()
        dependencies = {
            dependency.findtext("m:artifactId", namespaces=MAVEN_NAMESPACE): dependency
            for dependency in pom.findall(".//m:dependency", MAVEN_NAMESPACE)
        }

        self.assertIn("camunda-process-test-java", dependencies)
        self.assertIn("junit-vintage-engine", dependencies)
        self.assertIn("junit-jupiter", dependencies)
        self.assertIn("junit", dependencies)
        self.assertIn("assertj-core", dependencies)
        for removed in (
            "camunda-engine",
            "camunda-bpm-assert",
            "camunda-bpm-junit5",
            "camunda-process-test-coverage",
            "h2",
        ):
            with self.subTest(removed=removed):
                self.assertNotIn(removed, dependencies)

        parent_pom = ET.parse(FIXTURE_ROOT / "pom.xml").getroot()
        properties = {
            prop.tag.rsplit("}", maxsplit=1)[-1]: prop.text
            for prop in parent_pom.findall("m:properties/*", MAVEN_NAMESPACE)
        }
        self.assertEqual("3.27.7", properties["assertj.version"])

    def test_skill_and_checklist_load_the_migration_reference(self):
        skill = SKILL_PATH.read_text()
        checklist = CHECKLIST_PATH.read_text()
        reference = REFERENCE_PATH.read_text()
        harness = HARNESS_PATTERN.read_text()
        assertions = ASSERTION_PATTERN.read_text()
        messages = MESSAGE_PATTERN.read_text()
        sources = PATTERN_SOURCES_PATH.read_text()

        self.assertIn("references/test-migration.md", skill)
        self.assertIn("references/test-migration.md", checklist)
        self.assertIn("ProcessEngineTestCase", harness)
        self.assertIn("hasNoActiveElements", assertions)
        self.assertIn("newCorrelateMessageCommand", messages)
        self.assertIn("`@BeforeEach`", harness)
        self.assertIn("`@AfterEach`", harness)
        self.assertIn("40-test-assertions/20-test-setup/10-junit-harness.md", reference)
        self.assertIn("40-test-assertions/10-assertions/80-assertion-mapping.md", reference)
        self.assertIn("40-test-assertions/10-assertions/50-message.md", reference)
        self.assertIn("Test kind `process test`", sources)
        self.assertIn("Message correlation or signal delivery", sources)
        self.assertIn("accepted forms from the Test Inventory", reference)
        self.assertIn("ProcessEngineTestCase", checklist)

    def test_has_not_passed_has_no_exact_cpt_assertion_mapping(self):
        catalog = ASSERTION_PATTERN.read_text()
        row = next(
            line
            for line in catalog.splitlines()
            if line.startswith('| `hasNotPassed("A")` |')
        )
        cells = [cell.strip() for cell in row.split("|")[1:-1]]

        self.assertEqual("No exact counterpart", cells[1])
        self.assertIn("hasNotActivatedElements", cells[2])
        self.assertIn(
            "40-test-assertions/10-assertions/80-assertion-mapping.md",
            REFERENCE_PATH.read_text(),
        )

    def test_recipe_workarounds_use_fixed_release_thresholds(self):
        reference = REFERENCE_PATH.read_text()
        recipe_guidance = reference.split(
            "### Recipe-assisted migration", maxsplit=1
        )[1].split("\n### Limitations", maxsplit=1)[0]

        self.assertIn(
            "When the selected recipe is earlier than `0.2.10` for Camunda 8.8 or `0.3.11` for Camunda 8.9 or 8.10",
            recipe_guidance,
        )
        self.assertNotIn("While #3213 is open", recipe_guidance)
        self.assertNotIn("While #3214 is open", recipe_guidance)

    def test_validation_guidance_is_read_only_and_checks_empty_deployments(self):
        skill = " ".join(SKILL_PATH.read_text().split())

        self.assertIn(
            "when the target is Camunda 8.9 or later, verify that every process test with "
            "handling `Migrate to CPT` and every remote-engine test with handling "
            "`Migrate (lower priority)` were migrated",
            skill,
        )
        self.assertIn(
            "When the target is Camunda 8.8, verify that each such process test and "
            "remote-engine test keeps `Report only` handling with the reason "
            "`test migration needs Camunda 8.9 or later`.",
            skill,
        )
        self.assertIn("match a converted copy or an accepted form in the inventory", skill)
        self.assertNotIn(
            "migrate every process test that uses Camunda 7 engine support without Spring",
            skill,
        )
        self.assertIn("Require each entry to resolve at least one resource.", skill)

    def test_migration_report_records_test_mappings_and_mock_boundaries(self):
        report_text = (FIXTURE_ROOT / "expected-c8/MIGRATION_REPORT.md").read_text()
        decisions = report_text.split(
            "## Process-test migration decisions\n", maxsplit=1
        )[1].split("\n## ", maxsplit=1)[0]
        source_test_ids = {
            f"{class_name}#{method_name}"
            for class_name in TEST_CLASSES
            for method_name in c7_test_method_names(
                (C7_TESTS / f"{class_name}.java").read_text()
            )
        }
        self.assertEqual(EXPECTED_PROCESS_TEST_IDS, source_test_ids)

        reported_mappings = set(
            re.findall(r"(?m)^\| `([^`]+)` \| `([^`]+)` \|", decisions)
        )
        expected_mappings = {(test_id, test_id) for test_id in source_test_ids}
        self.assertEqual(expected_mappings, reported_mappings)

        report = " ".join(report_text.split())

        for evidence in (
            "`noopDelegate`",
            "`failingDelegate`",
            "BPMN diagram interchange from the Camunda 7 source models",
            "`IllegalStateException`",
            "incident",
            "`legacy-business-key`",
            "`subscription-key`",
            "`Task_Approve`",
        ):
            with self.subTest(evidence=evidence):
                self.assertIn(evidence, report)
        self.assertIn("approval is pending", report.lower())

    def test_cpt_waiting_guidance_distinguishes_immediate_negative_assertions(self):
        reference = " ".join(REFERENCE_PATH.read_text().split())

        self.assertNotIn("CPT assertions wait for asynchronous process behavior.", reference)
        self.assertNotIn("CPT assertions wait for the expected state.", reference)
        self.assertIn(
            "Most CPT assertions wait for an expected state for up to 10 seconds by default.",
            reference,
        )
        self.assertIn(
            "`hasNotActivatedElements(...)` evaluates the current process state immediately "
            "and does not wait.",
            reference,
        )
        self.assertIn(
            "The skill uses `hasNoActiveElements(...)`, `isNotWaitingForMessage(...)`, and "
            "`hasNotActivatedElements(...)` to inspect the current state.",
            reference,
        )
        self.assertIn(
            "The skill establishes the observation point with a positive waiting or terminal "
            "assertion before it uses a current-state absence check.",
            reference,
        )
        self.assertIn(
            "If the skill does not establish that point first, then the negative assertion can pass "
            "before the process reaches it.",
            reference,
        )

    def test_converted_copies_match_converter_output(self):
        for name in (
            "converted-c8-ImplicitDeploymentTest.bpmn",
            "converted-c8-process-test-cases.bpmn",
        ):
            with self.subTest(name=name):
                converted = (C8_RESOURCES / "com/camunda/fixture/tests" / name).read_text()
                source_name = name.removeprefix("converted-c8-")
                source = (C7_RESOURCES / "com/camunda/fixture/tests" / source_name).read_text()
                self.assertIn("conversion:converterVersion=", converted)
                self.assertEqual(diagram_shapes(source), diagram_shapes(converted))
                self.assertTrue(diagram_shapes(converted))
                self.assertNotIn("correlation key", converted)

        converted = (
            C8_RESOURCES / "com/camunda/fixture/tests/converted-c8-process-test-cases.bpmn"
        ).read_text()
        self.assertIn('<zeebe:taskDefinition type="noopDelegate"/>', converted)
        self.assertIn('<zeebe:taskDefinition type="failingDelegate"/>', converted)
        self.assertIn('<zeebe:header key="class" value="com.camunda.fixture.tests.NoopDelegate"/>', converted)
        self.assertIn('<zeebe:subscription correlationKey="= orderId"/>', converted)

    def test_bpmn_lint_suppresses_only_form_free_user_tasks(self):
        lint_config = json.loads((FIXTURE_ROOT / "expected-c8/.bpmnlintrc").read_text())

        self.assertEqual(
            {"camunda-compat/user-task-definition": "off"},
            lint_config["rules"],
        )

    def test_legacy_junit4_test_remains_on_both_projects(self):
        c7_test = (C7_TESTS / "LegacyFormatterTest.java").read_text()
        c8_test = (C8_TESTS / "LegacyFormatterTest.java").read_text()
        c8_pom = (FIXTURE_ROOT / "expected-c8/pom.xml").read_text()

        self.assertIn("org.junit.Test", c7_test)
        self.assertIn("org.junit.Test", c8_test)
        self.assertIn("junit-vintage-engine", c8_pom)
        self.assertEqual(test_method_names(c7_test), test_method_names(c8_test))

    def test_engine_configuration_is_removed_and_reported(self):
        c7_config = (C7_RESOURCES / "camunda.cfg.xml").read_text()
        report = (FIXTURE_ROOT / "expected-c8/MIGRATION_REPORT.md").read_text()

        self.assertIn('name="history" value="full"', c7_config)
        self.assertIn('name="historyTimeToLive" value="180"', c7_config)
        self.assertFalse((FIXTURE_ROOT / "expected-c8/src/test/resources/camunda.cfg.xml").exists())
        self.assertIn("history", report)
        self.assertIn("historyTimeToLive", report)
        self.assertIn("do not query history", report)


if __name__ == "__main__":
    unittest.main()
