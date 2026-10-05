from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


FIXTURE = Path(__file__).resolve().parent
REPO_ROOT = FIXTURE.parents[2]
C7_SOURCE = FIXTURE / "c7-source"
EXPECTED_C8 = FIXTURE / "expected-c8"
EXPECTED_ASSESSMENT = FIXTURE / "expected-assessment" / "test-inventory.md"
EXPECTED_ASSESSMENT_88 = FIXTURE / "expected-assessment-8.8" / "test-inventory.md"
EXPECTED_PARITY = FIXTURE / "expected-run" / "test-parity.md"
EXPECTED_TESTS_ONLY = FIXTURE / "expected-tests-only" / "MIGRATION_REPORT.md"
TEST_MIGRATION_REFERENCE = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/test-migration.md"
)
SKILL_PATH = REPO_ROOT / "agentic-migration-skills/skills/migrate-c7-to-c8-code/SKILL.md"

PACKAGE_RE = re.compile(r"(?m)^\s*package\s+([\w.]+)\s*;")
CLASS_RE = re.compile(r"\bclass\s+([A-Za-z_$][\w$]*)\b")
TEST_ANNOTATION_RE = re.compile(
    r"@\s*(?:org\.junit(?:\.jupiter\.api)?\.)?(?:Test|ParameterizedTest|RepeatedTest)\b"
)
METHOD_RE = re.compile(
    r"(?m)^\s*(?:(?:public|protected|private)\s+)?(?:static\s+)?"
    r"(?:[\w$<>?,.\[\]]+\s+)+([A-Za-z_$][\w$]*)\s*\("
)
JUNIT3_METHOD_RE = re.compile(r"(?m)^\s*public\s+void\s+(test[A-Za-z_$][\w$]*)\s*\(")
TEST_ID_RE = re.compile(r"(?:engine-tests|spring-boot-app|remote-engine):[\w.]+#[A-Za-z_$][\w$]*")
TEST_KINDS = (
    "process test",
    "decision test",
    "scenario test",
    "remote-engine test",
    "manual migration",
    "manual redesign",
    "out of scope",
    "out of scope (Camunda 8)",
)


def test_method_ids(project_root):
    ids = set()
    for source_file in sorted(project_root.glob("*/src/test/java/**/*.java")):
        source = source_file.read_text(encoding="utf-8")
        package_match = PACKAGE_RE.search(source)
        class_match = CLASS_RE.search(source)
        if package_match is None or class_match is None:
            continue

        module = source_file.relative_to(project_root).parts[0]
        class_name = "{}.{}".format(package_match.group(1), class_match.group(1))
        for annotation in TEST_ANNOTATION_RE.finditer(source):
            method = METHOD_RE.search(source[annotation.end() :])
            if method is not None:
                ids.add("{}:{}#{}".format(module, class_name, method.group(1)))

        if "extends ProcessEngineTestCase" in source:
            for method in JUNIT3_METHOD_RE.finditer(source):
                ids.add("{}:{}#{}".format(module, class_name, method.group(1)))
    return ids


def markdown_table(path, required_headers):
    lines = path.read_text(encoding="utf-8").splitlines()
    for index, line in enumerate(lines):
        if not line.startswith("|"):
            continue
        headers = [cell.strip().strip("`") for cell in line.strip("|").split("|")]
        if headers != required_headers:
            continue

        rows = []
        for row in lines[index + 2 :]:
            if not row.startswith("|"):
                break
            values = [cell.strip().strip("`") for cell in row.strip("|").split("|")]
            if len(values) != len(required_headers):
                raise AssertionError(
                    "Expected {} columns but found {} in {}: {}".format(
                        len(required_headers), len(values), path, row
                    )
                )
            rows.append(dict(zip(required_headers, values)))
        return rows
    raise AssertionError(
        "Could not find table with headers {} in {}".format(required_headers, path)
    )


def normalized(value):
    return " ".join(value.lower().split())


class MigrationGuidanceTest(unittest.TestCase):
    def assert_unique_rows(self, rows, identifier_column, report_path):
        identifiers = [row[identifier_column] for row in rows]
        self.assertEqual(
            len(identifiers),
            len(set(identifiers)),
            "{} contains duplicate {} values.".format(report_path, identifier_column),
        )

    def test_inventory_matches_every_camunda_7_test_method(self):
        source_ids = test_method_ids(C7_SOURCE)
        inventory_rows = markdown_table(
            EXPECTED_ASSESSMENT,
            ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"],
        )
        self.assert_unique_rows(inventory_rows, "Test ID", EXPECTED_ASSESSMENT)
        inventory_ids = {row["Test ID"] for row in inventory_rows}

        self.assertEqual(
            source_ids,
            inventory_ids,
            "The 8.9 Test Inventory must list every C7 test method exactly once.",
        )
        expected_test_classes = {
            "OrderProcessTest",
            "OrderTimerTest",
            "LegacyOrderTest",
            "OrderMockitoTest",
            "OrderAutoMockTest",
            "DiscountDecisionTest",
            "PromotionsDecisionTest",
            "FulfillmentScenarioTest",
            "SupportCaseTest",
            "FluentModelTest",
            "CheckStockDelegateTest",
            "ChargePaymentDelegateFakeTest",
            "PriceCalculatorTest",
            "SubscriptionProcessTest",
            "SubscriptionEndpointTest",
            "ActivateDelegateMockTest",
            "HousekeepingStartupTest",
            "SubscriptionStandaloneTest",
            "PaymentWorkerIT",
            "SharedEngineSmokeIT",
            "ChargePaymentHandlerTest",
        }
        actual_test_classes = {test_id.split("#", 1)[0].rsplit(".", 1)[-1] for test_id in source_ids}
        self.assertEqual(expected_test_classes, actual_test_classes)

    def test_inventory_rejects_duplicate_test_ids(self):
        duplicate_rows = [
            {"Test ID": "engine-tests:com.camunda.fixture.order.OrderProcessTest#approves"},
            {"Test ID": "engine-tests:com.camunda.fixture.order.OrderProcessTest#approves"},
        ]

        with self.assertRaisesRegex(AssertionError, "duplicate Test ID"):
            self.assert_unique_rows(duplicate_rows, "Test ID", "test inventory")

    def test_reference_tables_have_matching_header_and_separator_columns(self):
        lines = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8").splitlines()
        for index, header_line in enumerate(lines[:-1]):
            separator = lines[index + 1]
            if not header_line.startswith("|") or not separator.startswith("|"):
                continue

            separator_cells = [cell.strip() for cell in separator.strip("|").split("|")]
            if not all(re.fullmatch(r":?-{3,}:?", cell) for cell in separator_cells):
                continue

            header_cells = [cell.strip() for cell in header_line.strip("|").split("|")]
            with self.subTest(line=index + 1):
                self.assertEqual(
                    len(header_cells),
                    len(separator_cells),
                    "Table header and separator must have matching column counts.",
                )

    def test_shared_engine_smoke_has_explicit_scope_exception(self):
        scope_rows = markdown_table(
            TEST_MIGRATION_REFERENCE,
            ["Signal", "Confirmation required"],
        )
        exception_rows = [
            row for row in scope_rows if "shared engine url" in normalized(row["Signal"])
        ]
        self.assertEqual(len(exception_rows), 1)
        signal = normalized(exception_rows[0]["Signal"])
        requirement = normalized(exception_rows[0]["Confirmation required"])
        self.assertIn("does not run a process or decision", signal)
        self.assertIn("remote-engine test", requirement)
        self.assertIn("report only", requirement)
        self.assertIn("shared environment", requirement)

        headers = ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"]
        for inventory_path in (EXPECTED_ASSESSMENT, EXPECTED_ASSESSMENT_88):
            shared_rows = [
                row
                for row in markdown_table(inventory_path, headers)
                if row["Test ID"].endswith("SharedEngineSmokeIT#readsConfiguredSharedEngine")
            ]
            self.assertEqual(len(shared_rows), 1)
            self.assertEqual(shared_rows[0]["Test kind"], "remote-engine test")
            self.assertEqual(shared_rows[0]["Handling"], "Report only")
            self.assertIn("shared environment", normalized(shared_rows[0]["Notes"]))

    def test_camunda_8_8_inventory_marks_every_in_scope_test_report_only(self):
        headers = ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"]
        inventory = markdown_table(EXPECTED_ASSESSMENT, headers)
        inventory_88 = markdown_table(EXPECTED_ASSESSMENT_88, headers)
        self.assert_unique_rows(inventory, "Test ID", EXPECTED_ASSESSMENT)
        self.assert_unique_rows(inventory_88, "Test ID", EXPECTED_ASSESSMENT_88)
        rows_89 = {row["Test ID"]: row for row in inventory}
        rows_88 = {row["Test ID"]: row for row in inventory_88}

        self.assertEqual(set(rows_89), set(rows_88))
        version_reason = normalized("test migration needs Camunda 8.9 or later")
        for test_id, row in rows_89.items():
            with self.subTest(test_id=test_id):
                other = rows_88[test_id]
                if row["Test kind"] == "manual redesign":
                    self.assertEqual(other["Handling"], "Report only")
                    self.assertEqual(other["Notes"], row["Notes"])
                    continue

                if row["Handling"] == "Not part of test migration":
                    self.assertEqual(other["Handling"], row["Handling"])
                    self.assertEqual(other["Notes"], row["Notes"])
                    continue

                self.assertEqual(other["Handling"], "Report only")
                self.assertIn(version_reason, normalized(other["Notes"]))
                if row["Handling"] == "Report only":
                    self.assertIn(
                        normalized(row["Notes"].rstrip(".")),
                        normalized(other["Notes"]),
                    )

    def test_parity_maps_every_migrated_test_to_an_existing_cpt_test(self):
        inventory = markdown_table(
            EXPECTED_ASSESSMENT,
            ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"],
        )
        parity = markdown_table(
            EXPECTED_PARITY,
            ["Camunda 7 Test ID", "CPT Test ID(s)", "Verdict", "Notes"],
        )
        self.assert_unique_rows(parity, "Camunda 7 Test ID", EXPECTED_PARITY)
        parity_by_id = {row["Camunda 7 Test ID"]: row for row in parity}
        cpt_test_ids = test_method_ids(EXPECTED_C8)

        for row in inventory:
            test_id = row["Test ID"]
            parity_row = parity_by_id.get(test_id)
            if row["Handling"].startswith("Migrate"):
                self.assertIsNotNone(parity_row, "Missing parity row for {}".format(test_id))
                self.assertEqual(parity_row["Verdict"], "migrated")
                mapped_ids = TEST_ID_RE.findall(parity_row["CPT Test ID(s)"])
                self.assertTrue(mapped_ids, "Missing CPT test mapping for {}".format(test_id))
                for mapped_id in mapped_ids:
                    with self.subTest(test_id=test_id, cpt_test=mapped_id):
                        self.assertIn(mapped_id, cpt_test_ids)

        required_verdicts = {
            "engine-tests:com.camunda.fixture.order.SupportCaseTest#startsSupportCase":
                ("retired", "CMMN has no Camunda 8 equivalent"),
            "engine-tests:com.camunda.fixture.order.FluentModelTest#buildsAndStartsModel":
                ("retired", "model built in Java, migrated by hand later"),
            "remote-engine:com.camunda.fixture.payment.SharedEngineSmokeIT#readsConfiguredSharedEngine":
                ("manual", "shared environment"),
        }
        for test_id, (verdict, note) in required_verdicts.items():
            with self.subTest(test_id=test_id):
                self.assertIn(test_id, parity_by_id)
                self.assertEqual(parity_by_id[test_id]["Verdict"], verdict)
                self.assertIn(note.lower(), normalized(parity_by_id[test_id]["Notes"]))

    def test_every_converted_job_type_has_java_worker_or_mock(self):
        java_source = "\n".join(
            path.read_text(encoding="utf-8") for path in EXPECTED_C8.rglob("*.java")
        )
        job_types = set()
        for model in EXPECTED_C8.rglob("converted-c8-*.bpmn"):
            root = ET.parse(model).getroot()
            for element in root.iter():
                if element.tag.rsplit("}", 1)[-1] in {"taskDefinition", "executionListener"}:
                    job_type = element.get("type")
                    if job_type:
                        job_types.add(job_type)

        self.assertTrue(job_types, "Expected at least one converted service-task job type.")
        self.assertIn(
            "check-stock",
            job_types,
            "Service-task job types must be included in coverage checks.",
        )
        self.assertIn(
            "order-audit",
            job_types,
            "Execution-listener job types must be included in coverage checks.",
        )
        missing = sorted(job_type for job_type in job_types if '"{}"'.format(job_type) not in java_source)
        self.assertEqual([], missing, "No Java worker or mock covers these job types.")

    def test_reference_contains_scope_rule_and_test_kind_table(self):
        self.assertTrue(
            TEST_MIGRATION_REFERENCE.is_file(),
            "The test migration reference must exist.",
        )
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
        self.assertIn(
            "a test is eligible for migration only when it runs a bpmn process or dmn "
            "decision "
            "on a camunda 7 engine.",
            reference,
        )
        self.assertIn(
            "the test must also use a framework or approach that existed "
            "for camunda 7.",
            reference,
        )
        self.assertIn("| test kind | detect by | handling |", reference)
        for test_kind in TEST_KINDS:
            with self.subTest(test_kind=test_kind):
                self.assertIn("| {} |".format(normalized(test_kind)), reference)

        for source_directory in (
            "src/test/java",
            "src/test/kotlin",
            "src/test/groovy",
        ):
            with self.subTest(source_directory=source_directory):
                self.assertIn("`{}`".format(source_directory), reference)

    def test_cucumber_scenarios_have_discovery_and_stable_ids(self):
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
        skill = normalized(SKILL_PATH.read_text(encoding="utf-8"))
        self.assertIn("test cases, including configured cucumber scenarios", skill)
        for requirement in (
            "cucumber runner or build configuration",
            "`.feature` files",
            "each cucumber `scenario` as one test",
            "each data row in a cucumber `scenario outline` `examples` table "
            "as a separate test",
            "the skill does not inventory step-definition methods, lambda registrations, "
            "a cucumber runner class, or hook methods as separate tests",
            "@given",
            "@when",
            "@then",
            "@and",
            "@but",
            "the skill reads constructor-registered lambda steps",
            "`io.cucumber.java8.en`",
            "the skill follows both forms when it checks for camunda 7 process or decision calls.",
            "cucumber scenarios use camunda 7 apis to run an engine-backed bpmn process "
            "or dmn decision",
            "the cucumber classification includes applicable hooks, not only steps",
            "list every test with handling `report only` by test id",
            "when one test matches multiple test kinds",
            "<module path>:<feature path>#<scenario name>@l<line>",
            "the skill uses the `scenario` line number in its test id",
            "uses the outline name and `examples` row's line number in that format",
        ):
            with self.subTest(requirement=requirement):
                self.assertIn(requirement, reference)

    def test_manual_redesign_is_an_explicit_scope_exception(self):
        reference_text = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8")
        reference = normalized(reference_text)
        self.assertIn(
            "cmmn tests and tests that use camunda engine internals do not meet this scope rule.",
            reference,
        )
        self.assertIn(
            "the skill still inventories these tests as `manual redesign` with "
            "`report only` handling.",
            reference,
        )
        scope_confirmation_row = next(
            normalized(line)
            for line in reference_text.splitlines()
            if line.startswith("| A test uses CMMN")
        )
        self.assertIn("manual redesign", scope_confirmation_row)
        self.assertIn("report only", scope_confirmation_row)
        self.assertIn(
            "even when it does not run a bpmn process or dmn decision",
            scope_confirmation_row,
        )

        headers = ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"]
        for inventory_path in (EXPECTED_ASSESSMENT, EXPECTED_ASSESSMENT_88):
            support_case = next(
                row
                for row in markdown_table(inventory_path, headers)
                if row["Test ID"].endswith("SupportCaseTest#startsSupportCase")
            )
            self.assertEqual("manual redesign", support_case["Test kind"])
            self.assertEqual("Report only", support_case["Handling"])

    def test_parsed_dmn_models_are_inventoried(self):
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
        for requirement in (
            "every bpmn, dmn, or cmmn model that a test deploys or parses appears in the model inventory.",
            "link each test id to every model it deploys or parses in the `models` cell.",
            "trace model resources through test setup and shared helpers.",
            "`dmnengine.parsedecision(...)`",
        ):
            with self.subTest(requirement=requirement):
                self.assertIn(requirement, reference)

        headers = ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"]
        for inventory_path in (EXPECTED_ASSESSMENT, EXPECTED_ASSESSMENT_88):
            discount_rows = [
                row
                for row in markdown_table(inventory_path, headers)
                if "DiscountDecisionTest#" in row["Test ID"]
            ]
            self.assertTrue(discount_rows)
            self.assertTrue(all("discount.dmn" in row["Models"] for row in discount_rows))

    def test_model_source_table_includes_standalone_dmn_parsing(self):
        rows = markdown_table(
            TEST_MIGRATION_REFERENCE,
            ["Model source", "Model paths and notes to record", "Handling"],
        )
        self.assertEqual(
            {
                "Explicit @Deployment(resources = ...)",
                "Implicit method-level @Deployment",
                "Implicit class-level @Deployment",
                "Programmatic deployment",
                "Spring Boot auto-deployment",
                "Standalone DMN parsing",
                "CMMN model deployed by a test",
                "BPMN model built with the Camunda fluent model API",
            },
            {row["Model source"].replace("`", "") for row in rows},
        )

    def test_expected_inventories_include_complete_test_kind_counts(self):
        headers = ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"]
        for inventory_path in (EXPECTED_ASSESSMENT, EXPECTED_ASSESSMENT_88):
            with self.subTest(inventory=inventory_path):
                inventory_rows = markdown_table(inventory_path, headers)
                count_rows = markdown_table(inventory_path, ["Test kind", "Count"])
                self.assert_unique_rows(count_rows, "Test kind", inventory_path)
                self.assertEqual(len(TEST_KINDS), len(count_rows))

                expected_counts = {
                    test_kind: sum(row["Test kind"] == test_kind for row in inventory_rows)
                    for test_kind in TEST_KINDS
                }
                reported_counts = {
                    row["Test kind"]: int(row["Count"])
                    for row in count_rows
                }
                self.assertEqual(set(TEST_KINDS), set(reported_counts))
                self.assertEqual(expected_counts, reported_counts)

    def test_cucumber_hooks_participate_in_scope_confirmation(self):
        reference_text = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8")
        reference = normalized(reference_text)
        self.assertIn(
            "the skill reads applicable cucumber hooks.",
            reference,
        )
        self.assertIn(
            "the skill uses them to check whether scenarios run bpmn processes or "
            "dmn decisions on a camunda 7 engine.",
            reference,
        )
        scope_confirmation_row = next(
            normalized(line)
            for line in reference_text.splitlines()
            if line.startswith("| A Cucumber `Scenario`")
        )
        self.assertIn(
            "its step definitions or applicable hooks run a bpmn process or dmn "
            "decision on a camunda 7 engine",
            scope_confirmation_row,
        )
        for marker in ("| manual migration |", "| 3 |"):
            row = next(
                normalized(line)
                for line in reference_text.splitlines()
                if line.startswith(marker)
            )
            self.assertIn(
                "cucumber scenarios use camunda 7 apis to run an engine-backed bpmn "
                "process or dmn decision",
                row,
            )
            self.assertIn(
                "the cucumber classification includes applicable hooks, not only steps",
                row,
            )

    def test_spock_feature_methods_have_method_based_test_ids(self):
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
        self.assertIn(
            "the skill includes spock feature methods in groovy classes that extend "
            "`spock.lang.specification`.",
            reference,
        )
        self.assertIn(
            "the skill includes these methods even when they have no `@test` annotation.",
            reference,
        )
        self.assertIn(
            "the skill uses this method-based test id for each spock feature method.",
            reference,
        )

    def test_fluent_built_processes_are_manual_migration(self):
        reference = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8")
        fluent_model_rule = (
            "runs an engine-backed process from a bpmn model built with the "
            "camunda 7 fluent model api"
        )
        test_kind_row = next(
            normalized(line)
            for line in reference.splitlines()
            if line.startswith("| manual migration |")
        )
        precedence_row = next(
            normalized(line) for line in reference.splitlines() if line.startswith("| 3 |")
        )
        self.assertIn(fluent_model_rule, test_kind_row)
        self.assertIn(fluent_model_rule, precedence_row)

    def test_jvm_language_rules_exclude_no_engine_unit_tests(self):
        reference_text = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8")
        reference = normalized(reference_text)
        manual_migration_row = next(
            normalized(line)
            for line in reference_text.splitlines()
            if line.startswith("| manual migration |")
        )
        out_of_scope_row = next(
            normalized(line)
            for line in reference_text.splitlines()
            if line.startswith("| out of scope |")
        )
        self.assertIn(
            "a kotlin or groovy test uses camunda 7 test apis to run an engine-backed "
            "bpmn process or dmn decision",
            manual_migration_row,
        )
        self.assertIn(
            "a kotlin or groovy test uses camunda 7 test apis to run an engine-backed "
            "bpmn process or dmn decision",
            next(
                normalized(line)
                for line in reference_text.splitlines()
                if line.startswith("| 3 |")
            ),
        )
        self.assertIn(
            "the skill marks kotlin/groovy tests as `manual migration` only when they "
            "run an engine-backed bpmn process or dmn decision.",
            reference,
        )
        self.assertIn(
            "the engine must be camunda 7.",
            reference,
        )
        self.assertIn(
            "kotlin or groovy tests that use camunda 7 test apis but run no process "
            "or decision",
            out_of_scope_row,
        )

    def test_expected_report_files_exist(self):
        self.assertTrue(EXPECTED_ASSESSMENT.is_file())
        self.assertTrue(EXPECTED_ASSESSMENT_88.is_file())
        self.assertTrue(EXPECTED_PARITY.is_file())
        self.assertTrue(EXPECTED_TESTS_ONLY.is_file())


if __name__ == "__main__":
    unittest.main()
