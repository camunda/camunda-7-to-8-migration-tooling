from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


FIXTURE = Path(__file__).resolve().parent
PACKAGE_ROOT = FIXTURE.parents[1]
REPO_ROOT = PACKAGE_ROOT.parent
REFERENCE = (
    PACKAGE_ROOT
    / "skills/migrate-c7-to-c8-code/references/test-migration.md"
)
DECISION_PATTERN = (
    REPO_ROOT
    / "code-conversion/patterns/40-test-assertions/40-decisions/10-decision-tests.md"
)
SPRING_PATTERN = (
    REPO_ROOT
    / "code-conversion/patterns/40-test-assertions/20-test-setup/30-spring-boot-test.md"
)
PATTERN_SOURCES = (
    PACKAGE_ROOT
    / "skills/migrate-c7-to-c8-code/references/pattern-catalog-sources.md"
)
SKILL = PACKAGE_ROOT / "skills/migrate-c7-to-c8-code/SKILL.md"
QUESTIONS = (
    PACKAGE_ROOT
    / "skills/migrate-c7-to-c8-code/references/interview-questions.md"
)
C7 = FIXTURE / "c7-source"
EXPECTED_C8 = FIXTURE / "expected-c8"
DMN_NS = {"dmn": "https://www.omg.org/spec/DMN/20191111/MODEL/"}
POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}


class DecisionTestMigrationGuidanceTest(unittest.TestCase):
    def test_skill_points_to_decision_test_mapping(self):
        skill = " ".join(SKILL.read_text().lower().split())
        self.assertIn("references/test-migration.md", skill)

    def test_reference_maps_decision_test_semantics(self):
        reference = " ".join(REFERENCE.read_text().lower().split())
        catalog = " ".join(DECISION_PATTERN.read_text().lower().split())
        questions = " ".join(QUESTIONS.read_text().lower().split())

        for phrase in (
            "decision test",
            "hit policy",
            "collect",
            "docker",
            "remote runtime",
            "question 8",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, reference)

        for phrase in (
            "dmnenginerule",
            "decisionservice",
            "newevaluatedecisioncommand",
            "testdeployment",
            "hasnomatchedrules",
            "getfailuremessage()",
            "hashmap",
            "map.of",
        ):
            with self.subTest(catalog_phrase=phrase):
                self.assertIn(phrase, catalog)

        self.assertIn(
            "the skill never uses `hasoutput(list)` for a `collect` decision",
            reference,
        )
        self.assertIn(
            "40-test-assertions/40-decisions/10-decision-tests.md",
            REFERENCE.read_text(),
        )
        self.assertIn(
            "Test kind `decision test` | `40-test-assertions/40-decisions/10-decision-tests.md`",
            PATTERN_SOURCES.read_text(),
        )
        self.assertIn("question 8 runtime notice for decision tests", questions)
        self.assertIn("the cpt test needs a camunda 8 runtime", questions)

    def test_nullable_input_uses_a_map_that_accepts_null(self):
        source = (
            C7
            / "src/test/java/org/camunda/fixture/dmn/DiscountDecisionTest.java"
        ).read_text()
        migrated = (
            EXPECTED_C8
            / "src/test/java/org/camunda/fixture/dmn/DiscountDecisionTest.java"
        ).read_text()
        null_case = migrated.split("void preservesNullInput()", maxsplit=1)[1].split(
            "private EvaluateDecisionResponse", maxsplit=1
        )[0]

        self.assertIn('putValue("customerType", null)', source)
        self.assertIn("new HashMap<>()", null_case)
        self.assertIn('variables.put("customerType", null)', null_case)
        self.assertNotIn('Map.of("customerType", null)', null_case)

    def test_standalone_and_engine_backed_tests_use_cpt_harnesses(self):
        standalone = (
            C7
            / "src/test/java/org/camunda/fixture/dmn/DiscountDecisionTest.java"
        ).read_text()
        service = (
            C7
            / "src/test/java/org/camunda/fixture/dmn/PromotionsDecisionTest.java"
        ).read_text()
        migrated_standalone = (
            EXPECTED_C8
            / "src/test/java/org/camunda/fixture/dmn/DiscountDecisionTest.java"
        ).read_text()
        migrated_service = (
            EXPECTED_C8
            / "src/test/java/org/camunda/fixture/dmn/PromotionsDecisionTest.java"
        ).read_text()
        reference = REFERENCE.read_text()
        decision_catalog = DECISION_PATTERN.read_text()
        spring_catalog = SPRING_PATTERN.read_text()

        self.assertIn("new DmnEngineRule()", standalone)
        self.assertIn("parseDecision(", standalone)
        self.assertIn('@Deployment(resources = "promotions.dmn")', service)
        self.assertIn("getDecisionService()", service)
        self.assertIn("evaluateDecisionByKey", service)
        self.assertIn("DmnEngineException.class", standalone)
        self.assertIn("ProcessEngineException.class", service)
        self.assertIn(
            '@TestDeployment(resources = "converted-c8-discount.dmn")',
            migrated_standalone,
        )
        self.assertIn("newEvaluateDecisionCommand()", migrated_standalone)
        self.assertIn("getFailureMessage()", migrated_standalone)
        self.assertIn("DecisionSelectors.byResponse(response)", migrated_standalone)
        self.assertIn(
            '@TestDeployment(resources = "converted-c8-promotions.dmn")',
            migrated_service,
        )
        self.assertIn("@CamundaSpringProcessTest", spring_catalog)
        self.assertIn("DecisionSelectors.byResponse(response)", decision_catalog)

    def test_collect_results_are_parsed_and_order_insensitive(self):
        source = (
            C7
            / "src/test/java/org/camunda/fixture/dmn/PromotionsDecisionTest.java"
        ).read_text()
        migrated = (
            EXPECTED_C8
            / "src/test/java/org/camunda/fixture/dmn/PromotionsDecisionTest.java"
        ).read_text()

        self.assertIn('result.collectEntries("promotion")', source)
        self.assertIn('result.collectEntries("bonus")', source)
        self.assertIn("response.getDecisionOutput()", migrated)
        self.assertIn(
            "new TypeReference<List<Map<String, Object>>>()", migrated
        )
        self.assertIn('containsKeys("promotion", "bonus")', migrated)
        self.assertIn('output.get("promotion")', migrated)
        self.assertIn('output.get("bonus")', migrated)
        self.assertIn('containsExactlyInAnyOrder(null, "premium")', migrated)
        self.assertIn("containsExactlyInAnyOrder", migrated)
        self.assertNotIn("hasOutput(List", migrated)

    def test_collect_models_cover_multiple_outputs_and_null_values(self):
        for base, filename in (
            (C7 / "src/main/resources", "promotions.dmn"),
            (EXPECTED_C8 / "src/main/resources", "converted-c8-promotions.dmn"),
        ):
            root = ET.parse(base / filename).getroot()
            table = root.find(
                "dmn:decision[@id='promotions']/dmn:decisionTable", DMN_NS
            )
            outputs = table.findall("dmn:output", DMN_NS)

            self.assertEqual(
                [output.get("name") for output in outputs],
                ["promotion", "bonus"],
            )
            self.assertTrue(
                any(
                    (entry.find("dmn:text", DMN_NS).text or "").strip() == "null"
                    for entry in table.findall(
                        "dmn:rule/dmn:outputEntry", DMN_NS
                    )
                )
            )

    def test_non_scalar_decisions_do_not_declare_scalar_variables(self):
        for base, filename in (
            (C7 / "src/main/resources", "discount.dmn"),
            (EXPECTED_C8 / "src/main/resources", "converted-c8-discount.dmn"),
        ):
            root = ET.parse(base / filename).getroot()
            for decision_id in ("discount", "ruleOrder"):
                with self.subTest(decision_id=decision_id, filename=filename):
                    decision = root.find(
                        f"dmn:decision[@id='{decision_id}']", DMN_NS
                    )
                    self.assertIsNone(decision.find("dmn:variable", DMN_NS))

        for base, filename in (
            (C7 / "src/main/resources", "promotions.dmn"),
            (EXPECTED_C8 / "src/main/resources", "converted-c8-promotions.dmn"),
        ):
            root = ET.parse(base / filename).getroot()
            promotions = root.find("dmn:decision[@id='promotions']", DMN_NS)
            self.assertIsNone(promotions.find("dmn:variable", DMN_NS))

    def test_ordered_results_preserve_null_output_values(self):
        source = (
            C7
            / "src/test/java/org/camunda/fixture/dmn/DiscountDecisionTest.java"
        ).read_text()
        migrated = (
            EXPECTED_C8
            / "src/test/java/org/camunda/fixture/dmn/DiscountDecisionTest.java"
        ).read_text()

        self.assertIn('result.collectEntries("value")', source)
        self.assertIn("new TypeReference<List<String>>()", migrated)
        self.assertIn("response.getDecisionOutput()", migrated)
        self.assertIn('containsExactly("first", null, "last")', migrated)

        for base, filename in (
            (C7 / "src/main/resources", "discount.dmn"),
            (EXPECTED_C8 / "src/main/resources", "converted-c8-discount.dmn"),
        ):
            root = ET.parse(base / filename).getroot()
            decision = root.find("dmn:decision[@id='ruleOrder']", DMN_NS)
            self.assertIsNotNone(decision)
            table = decision.find("dmn:decisionTable", DMN_NS)
            self.assertEqual(table.get("hitPolicy"), "RULE ORDER")
            self.assertEqual(len(table.findall("dmn:rule", DMN_NS)), 3)
            self.assertTrue(
                any(
                    (entry.find("dmn:text", DMN_NS).text or "").strip() == "null"
                    for entry in table.findall(
                        "dmn:rule/dmn:outputEntry", DMN_NS
                    )
                )
            )

    def test_single_result_null_output_is_parsed_into_a_map(self):
        source = (
            C7
            / "src/test/java/org/camunda/fixture/dmn/DiscountDecisionTest.java"
        ).read_text()
        migrated = (
            EXPECTED_C8
            / "src/test/java/org/camunda/fixture/dmn/DiscountDecisionTest.java"
        ).read_text()

        self.assertIn('getEntry("discountRate")', source)
        self.assertIn("new TypeReference<Map<String, Object>>()", migrated)
        self.assertIn('containsKeys("discountRate", "segment")', migrated)
        self.assertIn('outputs.get("discountRate")', migrated)

        for base, filename in (
            (C7 / "src/main/resources", "discount.dmn"),
            (EXPECTED_C8 / "src/main/resources", "converted-c8-discount.dmn"),
        ):
            root = ET.parse(base / filename).getroot()
            table = root.find(
                "dmn:decision[@id='discount']/dmn:decisionTable", DMN_NS
            )
            trial = table.find("dmn:rule[@id='Rule_Trial']", DMN_NS)
            self.assertEqual(
                [
                    (entry.find("dmn:text", DMN_NS).text or "").strip()
                    for entry in trial.findall("dmn:outputEntry", DMN_NS)
                ],
                ["null", '"basic"'],
            )

    def test_nullable_output_expectations_use_null_tolerant_containers(self):
        reference = " ".join(REFERENCE.read_text().lower().split())

        self.assertIn(
            "the skill never uses `map.of` or `list.of` to build an expected output that contains `null`",
            reference,
        )
        self.assertIn("null-tolerant map or list", reference)

    def test_unsupported_hit_policies_require_manual_redesign(self):
        reference = " ".join(REFERENCE.read_text().lower().split())
        catalog = DECISION_PATTERN.read_text()

        self.assertIn("camunda 8.9 does not support `priority`", reference)
        self.assertIn("camunda 8.9 does not support `output order`", reference)
        self.assertNotIn("with hit policy `rule order` or `output order`", reference)
        self.assertIn(
            "| `collectEntries(\"x\")` with hit policy `OUTPUT ORDER` | Manual redesign |",
            catalog,
        )

    def test_json_input_type_is_checked_against_the_converted_dmn(self):
        catalog = " ".join(DECISION_PATTERN.read_text().lower().split())

        self.assertIn("when a camunda 7 value is a `date` or typed value", catalog)
        self.assertIn("json representation", catalog)
        self.assertIn(
            "do not assume the java type survives serialization",
            catalog,
        )

    def test_decision_service_failure_maps_to_cpt_response_fields(self):
        source = (
            C7
            / "src/test/java/org/camunda/fixture/dmn/PromotionsDecisionTest.java"
        ).read_text()
        migrated = (
            EXPECTED_C8
            / "src/test/java/org/camunda/fixture/dmn/PromotionsDecisionTest.java"
        ).read_text()
        catalog = " ".join(DECISION_PATTERN.read_text().lower().split())

        self.assertIn("ProcessEngineException.class", source)
        self.assertIn("getFailureMessage()", migrated)
        self.assertIn("getFailedDecisionId()", migrated)
        self.assertNotIn("assertThrows(", migrated)
        self.assertIn(
            "including one wrapped by `decisionservice` in `processengineexception`",
            catalog,
        )

    def test_c7_and_cpt_tests_keep_the_same_cases(self):
        for class_name in ("DiscountDecisionTest", "PromotionsDecisionTest"):
            with self.subTest(class_name=class_name):
                before = (
                    C7
                    / f"src/test/java/org/camunda/fixture/dmn/{class_name}.java"
                ).read_text()
                after = (
                    EXPECTED_C8
                    / f"src/test/java/org/camunda/fixture/dmn/{class_name}.java"
                ).read_text()
                before_methods = re.findall(
                    r"@Test\s+public void (\w+)\s*\(", before
                )
                after_methods = re.findall(r"@Test\s+void (\w+)\s*\(", after)
                self.assertEqual(before_methods, after_methods)

    def test_models_cover_required_decision_and_output_shapes(self):
        for base, filename in (
            (C7 / "src/main/resources", "discount.dmn"),
            (EXPECTED_C8 / "src/main/resources", "converted-c8-discount.dmn"),
        ):
            root = ET.parse(base / filename).getroot()
            decision = root.find("dmn:decision[@id='discount']", DMN_NS)
            self.assertIsNotNone(decision)
            table = decision.find("dmn:decisionTable", DMN_NS)
            self.assertEqual(table.get("hitPolicy"), "UNIQUE")
            self.assertEqual(len(table.findall("dmn:output", DMN_NS)), 2)
            unique = root.find("dmn:decision[@id='uniqueViolation']", DMN_NS)
            unique_table = unique.find("dmn:decisionTable", DMN_NS)
            self.assertEqual(unique_table.get("hitPolicy"), "UNIQUE")
            self.assertEqual(len(unique_table.findall("dmn:rule", DMN_NS)), 2)

        for base, filename in (
            (C7 / "src/main/resources", "promotions.dmn"),
            (EXPECTED_C8 / "src/main/resources", "converted-c8-promotions.dmn"),
        ):
            root = ET.parse(base / filename).getroot()
            promotions = root.find("dmn:decision[@id='promotions']", DMN_NS)
            self.assertIsNotNone(promotions)
            requirement = promotions.find(
                "dmn:informationRequirement/dmn:requiredDecision", DMN_NS
            )
            self.assertEqual(requirement.get("href"), "#customerTier")
            table = promotions.find("dmn:decisionTable", DMN_NS)
            self.assertEqual(table.get("hitPolicy"), "COLLECT")
            self.assertEqual(len(table.findall("dmn:rule", DMN_NS)), 3)

            unique = root.find("dmn:decision[@id='uniqueViolation']", DMN_NS)
            unique_table = unique.find("dmn:decisionTable", DMN_NS)
            self.assertEqual(unique_table.get("hitPolicy"), "UNIQUE")
            self.assertEqual(len(unique_table.findall("dmn:rule", DMN_NS)), 2)

    def test_cpt_dependency_replaces_c7_test_engine_dependencies(self):
        before = ET.parse(C7 / "pom.xml").getroot()
        after = ET.parse(EXPECTED_C8 / "pom.xml").getroot()
        before_coordinates = {
            (
                dependency.findtext("m:groupId", namespaces=POM_NS),
                dependency.findtext("m:artifactId", namespaces=POM_NS),
                dependency.findtext("m:scope", namespaces=POM_NS),
            )
            for dependency in before.findall(".//m:dependency", POM_NS)
        }
        before_artifacts = {
            item.text
            for item in before.findall(".//m:dependency/m:artifactId", POM_NS)
        }
        after_artifacts = {
            item.text
            for item in after.findall(".//m:dependency/m:artifactId", POM_NS)
        }

        self.assertIn("camunda-engine-dmn", before_artifacts)
        self.assertIn("camunda-engine-feel-juel", before_artifacts)
        self.assertIn("camunda-engine-feel-scala", before_artifacts)
        self.assertIn(
            ("org.camunda.bpm.dmn", "camunda-engine-dmn", "test"),
            before_coordinates,
        )
        self.assertIn("camunda-process-test-java", after_artifacts)
        self.assertFalse(
            any(
                artifact.startswith("camunda-engine-dmn")
                or artifact.startswith("camunda-engine-feel-")
                for artifact in after_artifacts
            )
        )


if __name__ == "__main__":
    unittest.main()
