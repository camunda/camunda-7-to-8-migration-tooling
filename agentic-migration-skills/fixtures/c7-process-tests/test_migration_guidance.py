from collections import Counter
import fnmatch
import re
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


FIXTURE = Path(__file__).resolve().parent
REPO_ROOT = FIXTURE.parents[2]
C7_SOURCE = FIXTURE / "c7-source"
EXPECTED_C8 = FIXTURE / "expected-c8"
EXPECTED_ASSESSMENT = FIXTURE / "expected-assessment" / "test-inventory.md"
EXPECTED_ASSESSMENT_88 = FIXTURE / "expected-assessment-8.8" / "test-inventory.md"
EXPECTED_PARITY = FIXTURE / "expected-run" / "test-parity.md"
EXPECTED_TESTS_ONLY = FIXTURE / "expected-tests-only" / "MIGRATION_REPORT.md"
MIGRATION_SKILL = (
    REPO_ROOT / "agentic-migration-skills/skills/migrate-c7-to-c8-code/SKILL.md"
)
SHARED_ENGINE_REASON = (
    "CPT deletes all runtime data between tests, so the test needs a dedicated Camunda 8 runtime."
)
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
MAVEN_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
METHOD_RE = re.compile(
    r"(?m)^\s*(?:(?:public|protected|private)\s+)?(?:static\s+)?"
    r"(?:[\w$<>?,.\[\]]+\s+)+([A-Za-z_$][\w$]*)\s*\("
)
JUNIT3_METHOD_RE = re.compile(r"(?m)^\s*public\s+void\s+(test[A-Za-z_$][\w$]*)\s*\(")
TEST_ID_RE = re.compile(r"[\w.-]+:[\w.]+#[A-Za-z_$][\w$]*")
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
MIGRATE_TO_CPT = "Migrate to CPT"
MIGRATE_LOWER_PRIORITY = "Migrate (lower priority)"
MIGRATED_HANDLINGS = ("Migrate", MIGRATE_TO_CPT, MIGRATE_LOWER_PRIORITY)
REPORT_ONLY_REASONS = {
    "scenario test": "scenario-test migration procedure is defined",
}
LEGACY_TEST_IDS = {
    "engine-tests-legacy:com.camunda.fixture.order.FulfillmentScenarioTest#"
    "shouldCompleteWorkAfterTwoDailyReminders",
    "engine-tests-legacy:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#"
    "shouldStartMessageProcess",
    "engine-tests-legacy:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#"
    "shouldCountCompletedVisitsSeparately",
    "engine-tests-legacy:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#"
    "shouldCountMixedFinishedVisitsByOutcome",
}


def test_method_ids(project_root):
    ids = set()
    root = ET.parse(project_root / "pom.xml").getroot()
    modules = root.findall("m:modules/m:module", MAVEN_NAMESPACE)

    for module_element in modules:
        module_name = (module_element.text or "").strip()
        module_root = (project_root / module_name).resolve()
        module_pom = ET.parse(module_root / "pom.xml").getroot()
        build = module_pom.find("m:build", MAVEN_NAMESPACE)
        test_source_directory = (
            build.findtext("m:testSourceDirectory", namespaces=MAVEN_NAMESPACE)
            if build is not None
            else None
        )
        test_source_directory = (
            test_source_directory.strip()
            if test_source_directory and test_source_directory.strip()
            else "${project.basedir}/src/test/java"
        )
        test_source_directory = test_source_directory.replace(
            "${project.basedir}", str(module_root)
        )
        if "${" in test_source_directory:
            raise AssertionError(
                "Unsupported testSourceDirectory expression in {}".format(module_root)
            )
        source_root = Path(test_source_directory)
        if not source_root.is_absolute():
            source_root = module_root / source_root
        source_root = source_root.resolve()
        if not source_root.is_dir():
            continue

        compiler_plugin = None
        if build is not None:
            compiler_plugin = next(
                (
                    plugin
                    for plugin in build.findall("m:plugins/m:plugin", MAVEN_NAMESPACE)
                    if plugin.findtext("m:artifactId", namespaces=MAVEN_NAMESPACE)
                    == "maven-compiler-plugin"
                ),
                None,
            )
        include_patterns = (
            [
                (include.text or "").strip()
                for include in compiler_plugin.findall(
                    "m:configuration/m:testIncludes/m:testInclude", MAVEN_NAMESPACE
                )
                if include.text and include.text.strip()
            ]
            if compiler_plugin is not None
            else []
        )

        for source_file in sorted(source_root.rglob("*.java")):
            relative_source_file = source_file.relative_to(source_root).as_posix()
            if include_patterns and not any(
                fnmatch.fnmatchcase(relative_source_file, pattern)
                for pattern in include_patterns
            ):
                continue

            source = source_file.read_text(encoding="utf-8")
            package_match = PACKAGE_RE.search(source)
            class_match = CLASS_RE.search(source)
            if package_match is None or class_match is None:
                continue

            class_name = "{}.{}".format(package_match.group(1), class_match.group(1))
            for annotation in TEST_ANNOTATION_RE.finditer(source):
                method = METHOD_RE.search(source[annotation.end() :])
                if method is not None:
                    ids.add("{}:{}#{}".format(module_name, class_name, method.group(1)))

            if "extends ProcessEngineTestCase" in source:
                for method in JUNIT3_METHOD_RE.finditer(source):
                    ids.add("{}:{}#{}".format(module_name, class_name, method.group(1)))
    return ids


def markdown_character_is_escaped(line, index):
    backslash_count = 0
    previous = index - 1
    while previous >= 0 and line[previous] == "\\":
        backslash_count += 1
        previous -= 1
    return backslash_count % 2 == 1


def markdown_table_cells(line):
    line = line.strip()
    cell_start = 1 if line.startswith("|") else 0
    line_end = len(line)
    if (
        line_end > cell_start
        and line[line_end - 1] == "|"
        and not markdown_character_is_escaped(line, line_end - 1)
    ):
        line_end -= 1

    cells = []
    for index in range(cell_start, line_end):
        if line[index] == "|" and not markdown_character_is_escaped(line, index):
            cells.append(line[cell_start:index].strip())
            cell_start = index + 1
    cells.append(line[cell_start:line_end].strip())
    return cells


def markdown_table_has_delimiter(line):
    code_delimiter_length = 0
    index = 0
    while index < len(line):
        if line[index] == "`":
            delimiter_end = index + 1
            while delimiter_end < len(line) and line[delimiter_end] == "`":
                delimiter_end += 1
            delimiter_length = delimiter_end - index
            if code_delimiter_length == 0:
                if not markdown_character_is_escaped(line, index):
                    closing_index = delimiter_end
                    while closing_index < len(line):
                        if line[closing_index] == "`":
                            closing_end = closing_index + 1
                            while (
                                closing_end < len(line)
                                and line[closing_end] == "`"
                            ):
                                closing_end += 1
                            if closing_end - closing_index == delimiter_length:
                                code_delimiter_length = delimiter_length
                                break
                            closing_index = closing_end
                        else:
                            closing_index += 1
            elif delimiter_length == code_delimiter_length:
                code_delimiter_length = 0
            index = delimiter_end
            continue

        if (
            line[index] == "|"
            and code_delimiter_length == 0
            and not markdown_character_is_escaped(line, index)
        ):
            return True
        index += 1
    return False


def java_method_body(source, method_name):
    signature = re.compile(
        r"(?m)^[ \t]*(?:(?:public|protected|private)\s+)?(?:static\s+)?"
        r"(?:[\w$<>?,.\[\]]+\s+)+"
        + re.escape(method_name)
        + r"\s*\([^)]*\)\s*\{"
    )
    match = signature.search(source)
    if match is None:
        raise AssertionError("Could not find Java method {}.".format(method_name))

    opening_brace = match.end() - 1

    def skip_quoted_literal(start, delimiter):
        index = start + len(delimiter)
        while index < len(source):
            if source[index] == "\\":
                index += 2
            elif source.startswith(delimiter, index):
                return index + len(delimiter)
            else:
                index += 1
        return len(source)

    depth = 0
    index = opening_brace
    while index < len(source):
        if source.startswith("//", index):
            index += 2
            while index < len(source) and source[index] not in "\r\n":
                index += 1
            continue
        if source.startswith("/*", index):
            comment_end = source.find("*/", index + 2)
            if comment_end == -1:
                break
            index = comment_end + 2
            continue
        if source.startswith('"""', index):
            index = skip_quoted_literal(index, '"""')
            continue
        if source[index] == '"':
            index = skip_quoted_literal(index, '"')
            continue
        if source[index] == "'":
            index = skip_quoted_literal(index, "'")
            continue
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening_brace + 1 : index]
        index += 1

    raise AssertionError("Could not find the end of Java method {}.".format(method_name))


def markdown_table(path, required_headers):
    lines = path.read_text(encoding="utf-8").splitlines()
    for index, line in enumerate(lines):
        if "|" not in line:
            continue
        headers = [cell.replace("`", "") for cell in markdown_table_cells(line)]
        if headers != required_headers:
            continue

        rows = []
        for row in lines[index + 2 :]:
            if "|" not in row:
                break
            values = [cell.strip("`") for cell in markdown_table_cells(row)]
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


def reference_table_separator_errors(lines):
    errors = []
    for index, header_line in enumerate(lines[:-1]):
        if not markdown_table_has_delimiter(header_line) or (
            index > 0 and markdown_table_has_delimiter(lines[index - 1])
        ):
            continue

        header_cells = markdown_table_cells(header_line)
        if len(header_cells) < 2:
            continue

        separator = lines[index + 1]
        missing_pipes = "|" not in separator
        header_has_outer_pipes = (
            header_line.strip().startswith("|")
            or header_line.strip().endswith("|")
        )
        if missing_pipes:
            if (
                not header_has_outer_pipes
                and not re.fullmatch(r"[\s:-]*-[\s:-]*", separator)
            ):
                continue

            separator_cells = separator.split()
        else:
            separator_cells = markdown_table_cells(separator)

        if not separator_cells:
            errors.append(
                "Invalid table separator on line {}: {}".format(
                    index + 2, separator
                )
            )
            continue

        invalid_cells = [
            cell
            for cell in separator_cells
            if not re.fullmatch(r":?-{3,}:?", cell)
        ]
        if invalid_cells:
            errors.append(
                "Invalid table separator cell on line {}: {}".format(
                    index + 2, separator
                )
            )
            continue

        if missing_pipes:
            if (
                not header_has_outer_pipes
                and len(header_cells) != len(separator_cells)
            ):
                errors.append(
                    "Table header and separator must have matching column counts on line {}: {}".format(
                        index + 1, header_line
                    )
                )
                continue
            errors.append(
                "Table separator must contain pipe delimiters on line {}: {}".format(
                    index + 2, separator
                )
            )
            continue

        if len(header_cells) != len(separator_cells):
            errors.append(
                "Table header and separator must have matching column counts on line {}: {}".format(
                    index + 1, separator
                )
            )
    return errors


def normalized(value):
    return " ".join(value.lower().split())


def fixture_files(root, pattern):
    return sorted(
        path
        for path in root.rglob(pattern)
        if "target" not in path.relative_to(root).parts
    )


def migrated_test_parity_errors(inventory, parity_by_id, cpt_test_ids):
    errors = []
    for row in inventory:
        if row["Handling"] not in MIGRATED_HANDLINGS:
            continue

        test_id = row["Test ID"]
        parity_row = parity_by_id.get(test_id)
        if parity_row is None:
            errors.append("Missing parity row for {}".format(test_id))
            continue

        if parity_row["Verdict"] != "migrated":
            errors.append("Expected migrated verdict for {}".format(test_id))
        mapped_ids = TEST_ID_RE.findall(parity_row["CPT Test ID(s)"])
        if not mapped_ids:
            errors.append("Missing CPT test mapping for {}".format(test_id))
        for mapped_id in mapped_ids:
            if mapped_id not in cpt_test_ids:
                errors.append(
                    "Unknown CPT test mapping {} for {}".format(mapped_id, test_id)
                )
    return errors


def non_ears_conditional_rules(markdown):
    conditional = re.compile(
        r"\b(?:only\s+when|unless|until|when|while|if|where)\b",
        flags=re.IGNORECASE,
    )
    if_then_rule = re.compile(
        r"^if\s+(.+?),\s*then\s+(.+)$",
        flags=re.IGNORECASE,
    )
    non_ears_temporal_starter = re.compile(
        r"^(?:before|after|once|whenever|provided(?:\s+that)?|as\s+long\s+as|as\s+soon\s+as)\b",
        flags=re.IGNORECASE,
    )
    blocks = []
    current_lines = []
    current_start = 1
    violations = []
    in_code_block = False

    def flush_block():
        nonlocal current_lines
        if current_lines:
            blocks.append((current_start, " ".join(current_lines)))
            current_lines = []

    for line_number, line in enumerate(markdown.splitlines(), start=1):
        if line.lstrip().startswith("```"):
            flush_block()
            in_code_block = not in_code_block
            continue
        if in_code_block:
            continue
        line = re.sub(r"`[^`]*`", "CODE", line)
        if not line.strip():
            flush_block()
            continue
        if line.lstrip().startswith("|"):
            flush_block()
            blocks.extend((line_number, cell) for cell in line.split("|"))
            continue
        if re.match(r"^\s*(?:[-*+]|\d+\.)\s+", line):
            flush_block()
            current_start = line_number
            current_lines.append(
                re.sub(r"^\s*(?:[-*+]|\d+\.)\s+", "", line).strip()
            )
            continue
        if re.match(r"^\s*#{1,6}\s+", line):
            flush_block()
            current_start = line_number
            current_lines.append(re.sub(r"^\s*#{1,6}\s+", "", line).strip())
            continue
        if not current_lines:
            current_start = line_number
        current_lines.append(line.strip())

    flush_block()

    for line_number, block in blocks:
        for sentence in re.split(r"(?<=[.!?])\s+", block):
            sentence = sentence.strip()
            if not sentence:
                continue

            if non_ears_temporal_starter.match(sentence):
                violations.append("{}: {}".format(line_number, sentence))
                continue

            matches = list(conditional.finditer(sentence))
            if not matches:
                continue

            starts_with_trigger = (
                matches[0].start() == 0
                and matches[0].group().lower() in {"when", "while", "if", "where"}
            )
            starts_with_if = (
                starts_with_trigger and matches[0].group().lower() == "if"
            )
            if_match = if_then_rule.match(sentence) if starts_with_if else None
            incomplete_if_rule = starts_with_if and (
                if_match is None
                or not re.search(r"\w", if_match.group(1))
                or not re.search(r"\w", if_match.group(2))
            )
            if not starts_with_trigger or len(matches) > 1 or incomplete_if_rule:
                violations.append("{}: {}".format(line_number, sentence))

    return violations


class MigrationGuidanceTest(unittest.TestCase):
    def assert_unique_rows(self, rows, identifier_column, report_path):
        identifiers = [row[identifier_column] for row in rows]
        self.assertEqual(
            len(identifiers),
            len(set(identifiers)),
            "{} contains duplicate {} values.".format(report_path, identifier_column),
        )

    def assert_test_kind_counts(self, inventory_path, inventory_rows):
        count_rows = markdown_table(inventory_path, ["Test kind", "Count"])
        self.assert_unique_rows(count_rows, "Test kind", inventory_path)
        expected_counts = {row["Test kind"]: int(row["Count"]) for row in count_rows}
        actual_counts = Counter(row["Test kind"] for row in inventory_rows)
        for test_kind in expected_counts:
            actual_counts.setdefault(test_kind, 0)
        self.assertEqual(
            actual_counts,
            expected_counts,
            "{} must count every test kind exactly.".format(inventory_path),
        )

    def test_fixture_file_discovery_excludes_maven_targets(self):
        with tempfile.TemporaryDirectory() as temp_directory:
            root = Path(temp_directory)
            source = root / "module/src/main/resources/diagram.bpmn"
            generated = root / "module/target/classes/diagram.bpmn"
            source.parent.mkdir(parents=True)
            generated.parent.mkdir(parents=True)
            source.touch()
            generated.touch()

            self.assertEqual([source], fixture_files(root, "diagram.bpmn"))

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
            "ScenarioMappingEdgeCasesTest",
            "SupportCaseTest",
            "FluentModelTest",
            "CheckStockDelegateTest",
            "ChargePaymentDelegateFakeTest",
            "PriceCalculatorTest",
            "MockitoAnnotationTest",
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

    def test_inventory_count_summaries_match_each_test_kind(self):
        headers = ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"]
        for inventory_path in (EXPECTED_ASSESSMENT, EXPECTED_ASSESSMENT_88):
            with self.subTest(inventory=inventory_path):
                inventory = markdown_table(inventory_path, headers)
                self.assert_test_kind_counts(inventory_path, inventory)

    def test_inventory_includes_legacy_module_test_source_set(self):
        actual_legacy_ids = {
            test_id
            for test_id in test_method_ids(C7_SOURCE)
            if test_id.startswith("engine-tests-legacy:")
        }

        self.assertEqual(LEGACY_TEST_IDS, actual_legacy_ids)

    def test_legacy_module_tests_map_to_primary_cpt_suite(self):
        parity = markdown_table(
            EXPECTED_PARITY,
            ["Camunda 7 Test ID", "CPT Test ID(s)", "Verdict", "Notes"],
        )
        parity_by_id = {row["Camunda 7 Test ID"]: row for row in parity}

        for legacy_id in sorted(LEGACY_TEST_IDS):
            with self.subTest(test_id=legacy_id):
                cpt_id = legacy_id.replace("engine-tests-legacy:", "engine-tests:", 1)
                self.assertEqual(cpt_id, parity_by_id[legacy_id]["CPT Test ID(s)"])

    def test_tests_only_report_keeps_legacy_executions_blocked(self):
        rows = markdown_table(
            EXPECTED_TESTS_ONLY,
            ["Test ID", "Expected CPT test", "Status", "Reason"],
        )
        self.assert_unique_rows(rows, "Test ID", EXPECTED_TESTS_ONLY)
        rows_by_id = {row["Test ID"]: row for row in rows}

        for legacy_id in sorted(LEGACY_TEST_IDS):
            cpt_id = legacy_id.split(":", 1)[1].rsplit(".", 1)[-1]
            with self.subTest(test_id=legacy_id):
                self.assertIn(legacy_id, rows_by_id)
                self.assertEqual(rows_by_id[legacy_id]["Expected CPT test"], cpt_id)
                self.assertEqual(rows_by_id[legacy_id]["Status"], "blocked")
                self.assertEqual(
                    rows_by_id[legacy_id]["Reason"],
                    "declined by user (Question 8)",
                )

    def test_shared_test_sources_migrate_once_and_preserve_unrelated_tests(self):
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
        self.assertIn(
            "when the skill plans a target-build change, it checks test-source roots, "
            "test filters, and resource processing in every maven module or gradle source set.",
            reference,
        )
        self.assertIn(
            "where a project uses maven, the skill checks each module's `testsourcedirectory`, compiler "
            "include patterns, `resources`, and `testresources` declarations.",
            reference,
        )
        self.assertIn(
            "where a project uses gradle, the skill checks each test source set and its "
            "test-task include and exclude patterns.",
            reference,
        )
        self.assertIn(
            "the skill checks source-set resource directories and matching resource-processing "
            "tasks such as `processresources` and `processtestresources`.",
            reference,
        )
        self.assertIn(
            "the skill checks their filters and output paths.",
            reference,
        )
        self.assertNotIn("target reactor", reference)

        source_set_actions = markdown_table(
            TEST_MIGRATION_REFERENCE,
            ["Source-set condition", "Migration action"],
        )
        duplicate_source_actions = [
            row
            for row in source_set_actions
            if "multiple c7 modules" in row["Source-set condition"].lower()
        ]
        self.assertEqual(1, len(duplicate_source_actions))
        self.assertIn(
            "migrate that source only once",
            duplicate_source_actions[0]["Migration action"].lower(),
        )
        self.assertIn(
            "map duplicate module executions to one cpt test id",
            duplicate_source_actions[0]["Migration action"].lower(),
        )

        redundant_module_actions = [
            row
            for row in source_set_actions
            if "target module" in row["Source-set condition"].lower()
        ]
        self.assertEqual(2, len(redundant_module_actions))
        remove_action = next(
            row
            for row in redundant_module_actions
            if "remove the module" in row["Migration action"].lower()
        )
        for requirement in (
            "no test sources outside the shared set",
            "unique resources",
            "resource-processing behavior",
            "main outputs",
            "generated outputs",
            "build responsibilities",
        ):
            self.assertIn(requirement, remove_action["Source-set condition"].lower())

        preserve_action = next(
            row
            for row in redundant_module_actions
            if "preserve every unique" in row["Migration action"].lower()
        )
        self.assertIn(
            "resource",
            preserve_action["Migration action"].lower(),
        )
        self.assertIn(
            "resource-processing rule",
            preserve_action["Migration action"].lower(),
        )
        self.assertIn(
            "output",
            preserve_action["Migration action"].lower(),
        )
        self.assertIn(
            "the skill retains the target module",
            preserve_action["Migration action"].lower(),
        )

        target_project = ET.parse(EXPECTED_C8 / "pom.xml").getroot()
        target_modules = {
            (module.text or "").strip()
            for module in target_project.findall("m:modules/m:module", MAVEN_NAMESPACE)
        }
        self.assertNotIn("engine-tests-legacy", target_modules)

    def test_module_removal_inspects_configured_resource_processing(self):
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
        for requirement in (
            "compiler include patterns, `resources`, and `testresources` declarations",
            "resource filters, includes, excludes, and `targetpath` settings",
            "plugins or tasks that copy or generate resources",
            "matching resource-processing tasks such as `processresources` and `processtestresources`",
            "the skill checks their filters and output paths",
            "shared resource root can produce unique output",
        ):
            self.assertIn(requirement, reference)

        legacy_pom = ET.parse(C7_SOURCE / "engine-tests-legacy/pom.xml").getroot()
        legacy_build = legacy_pom.find("m:build", MAVEN_NAMESPACE)
        self.assertIsNotNone(legacy_build)
        self.assertEqual(
            "${project.basedir}/../engine-tests/src/main/resources",
            legacy_build.findtext(
                "m:resources/m:resource/m:directory", namespaces=MAVEN_NAMESPACE
            ),
        )
        self.assertEqual(
            "${project.basedir}/../engine-tests/src/test/resources",
            legacy_build.findtext(
                "m:testResources/m:testResource/m:directory",
                namespaces=MAVEN_NAMESPACE,
            ),
        )
        self.assertTrue(
            (C7_SOURCE / "engine-tests/src/main/resources/order.bpmn").is_file()
        )
        self.assertTrue(
            (
                C7_SOURCE
                / "engine-tests/src/test/resources/com/camunda/fixture/order/"
                "LegacyOrderTest.testStockMissing.bpmn"
            ).is_file()
        )

        target_resources = EXPECTED_C8 / "engine-tests/src/main/resources"
        self.assertTrue((target_resources / "converted-c8-order.bpmn").is_file())
        self.assertTrue(
            (
                target_resources
                / "converted-c8-LegacyOrderTest.testStockMissing.bpmn"
            ).is_file()
        )

    def test_converted_bpmn_copies_do_not_add_di_to_sources_without_di(self):
        namespace = {"bpmndi": "http://www.omg.org/spec/BPMN/20100524/DI"}
        converted_files = fixture_files(EXPECTED_C8, "converted-c8-*.bpmn")
        no_di_sources = 0

        for converted_file in converted_files:
            source_name = converted_file.name.removeprefix("converted-c8-")
            source_files = fixture_files(C7_SOURCE, source_name)
            self.assertEqual(
                1,
                len(source_files),
                "Expected one C7 source for {}.".format(converted_file),
            )
            source_root = ET.parse(source_files[0]).getroot()
            if source_root.findall(".//bpmndi:BPMNDiagram", namespace):
                continue

            no_di_sources += 1
            converted_root = ET.parse(converted_file).getroot()
            with self.subTest(source=source_files[0], converted=converted_file):
                self.assertEqual(
                    [],
                    converted_root.findall(".//bpmndi:BPMNDiagram", namespace),
                    "A converted copy must not manufacture DI absent from its source.",
                )

        self.assertGreater(no_di_sources, 0)

    def test_linear_repeated_external_task_mapping_does_not_register_worker_mock(self):
        mappings = markdown_table(
            TEST_MIGRATION_REFERENCE,
            [
                "Camunda Platform Scenario",
                "Camunda Process Test 8.9 or later",
                "Notes",
            ],
        )
        linear_actions = [
            row
            for row in mappings
            if "repeated external-task actions on a linear path"
            in row["Camunda Platform Scenario"].lower()
        ]

        self.assertEqual(1, len(linear_actions))
        self.assertIn(
            "does not register a worker mock",
            linear_actions[0]["Notes"].lower(),
        )

    def test_scenario_test_guidance_is_loaded_during_step_two(self):
        skill = normalized(MIGRATION_SKILL.read_text(encoding="utf-8"))
        step_two_start = skill.index("### step 2: assessment (always runs)")
        step_three_start = skill.index("### step 3: execute migration", step_two_start)
        step_two = skill[step_two_start:step_three_start]

        self.assertIn("scenario test inventory", step_two)
        self.assertIn("references/test-migration.md", step_two)

    def test_fulfillment_parity_note_matches_bounded_time_guidance(self):
        parity = markdown_table(
            EXPECTED_PARITY,
            ["Camunda 7 Test ID", "CPT Test ID(s)", "Verdict", "Notes"],
        )
        time_rule = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
        test_id = (
            "engine-tests:com.camunda.fixture.order.FulfillmentScenarioTest#"
            "shouldCompleteWorkAfterTwoDailyReminders"
        )
        notes = {row["Camunda 7 Test ID"]: row for row in parity}[test_id]["Notes"]
        cpt_source = (
            EXPECTED_C8
            / "engine-tests/src/test/java/com/camunda/fixture/order/"
            "FulfillmentScenarioTest.java"
        ).read_text(encoding="utf-8")

        self.assertIn("steps no longer than the shortest timer period", time_rule)
        self.assertIn(
            "one valid schedule uses five 12-hour increments",
            time_rule,
        )
        self.assertIn(
            "when a scenario stub uses `defer(period, action)` and the total time increase "
            "reaches `period`, the skill runs the deferred action.",
            time_rule,
        )
        self.assertNotIn("increases time by one day twice", time_rule)
        self.assertEqual(5, cpt_source.count("increaseTime(Duration.ofHours(12))"))
        self.assertIn("one scenario test", notes)
        self.assertIn("five 12-hour steps", notes)
        self.assertNotIn("repeats the scenario", notes)
        self.assertNotIn("advances time twice", notes)

    def test_inventory_rejects_duplicate_test_ids(self):
        duplicate_rows = [
            {"Test ID": "engine-tests:com.camunda.fixture.order.OrderProcessTest#approves"},
            {"Test ID": "engine-tests:com.camunda.fixture.order.OrderProcessTest#approves"},
        ]

        with self.assertRaisesRegex(AssertionError, "duplicate Test ID"):
            self.assert_unique_rows(duplicate_rows, "Test ID", "test inventory")

    def test_markdown_table_supports_optional_outer_pipes(self):
        tables = (
            ("| First | Second |", "|---|---|", "| one | two |"),
            ("| First | Second", "|---|---", "| one | two"),
            ("First | Second |", "---|---|", "one | two |"),
            ("First | Second", "---|---", "one | two"),
            ("  | First | Second |  ", "  | --- | --- |  ", "  | one | two |  "),
            ("  First | Second  ", "  --- | ---  ", "  one | two  "),
        )
        for header, separator, data in tables:
            with self.subTest(header=header, separator=separator):
                with tempfile.TemporaryDirectory() as directory:
                    path = Path(directory) / "table.md"
                    path.write_text(
                        "\n".join((header, separator, data)),
                        encoding="utf-8",
                    )
                    self.assertEqual(
                        [{"First": "one", "Second": "two"}],
                        markdown_table(path, ["First", "Second"]),
                    )

    def test_markdown_table_preserves_escaped_pipes_in_headers(self):
        lines = ("| First \\| alias | Second |", "|---|---|", "| left | right |")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "table.md"
            path.write_text("\n".join(lines), encoding="utf-8")
            self.assertEqual(
                [{r"First \| alias": "left", "Second": "right"}],
                markdown_table(path, [r"First \| alias", "Second"]),
            )

    def test_markdown_table_preserves_escaped_pipes_in_rows(self):
        lines = (
            "| First | Second |",
            "|---|---|",
            r"| left \| middle | trailing \|",
        )
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "table.md"
            path.write_text("\n".join(lines), encoding="utf-8")
            self.assertEqual(
                [{"First": r"left \| middle", "Second": r"trailing \|"}],
                markdown_table(path, ["First", "Second"]),
            )

    def test_reference_tables_have_matching_header_and_separator_columns(self):
        lines = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8").splitlines()
        errors = reference_table_separator_errors(lines)
        self.assertEqual(
            [],
            errors,
            "{}:\n{}".format(TEST_MIGRATION_REFERENCE, "\n".join(errors)),
        )

    def test_reference_table_separator_supports_escaped_header_pipes(self):
        self.assertEqual(
            [],
            reference_table_separator_errors(
                [r"| First \| alias | Second |", "|---|---|"]
            ),
        )

    def test_reference_table_rejects_malformed_separator_cells(self):
        header = "| First | Second | Third | Fourth |"
        for separator in (
            "|---||---|---|",
            "||---|---|---|",
            "|---|---|---||",
            "|--|--|--|--|",
            "|--||--|--|",
            "|---|--|---|---|",
            "|---|---x|---|---|",
            "| value | value |",
        ):
            with self.subTest(separator=separator):
                errors = reference_table_separator_errors([header, separator])
                self.assertTrue(errors)
                self.assertIn("Invalid table separator cell", errors[0])

    def test_reference_table_separator_cells_support_optional_outer_pipes(self):
        headers = (
            "| First | Second |",
            "| First | Second",
            "First | Second |",
            "First | Second",
            "  | First | Second |  ",
            "  First | Second  ",
        )
        separators = (
            ("|---|---|", True),
            ("|---|---", True),
            ("---|---|", True),
            ("---|---", True),
            ("  | --- | --- |  ", True),
            ("  --- | ---  ", True),
            ("|---|value|", False),
            ("|---|value", False),
            ("---|value|", False),
            ("--- | value", False),
            ("  --- | value  ", False),
        )
        for header in headers:
            for separator, valid in separators:
                with self.subTest(header=header, separator=separator):
                    errors = reference_table_separator_errors([header, separator])
                    if valid:
                        self.assertEqual([], errors)
                    else:
                        self.assertTrue(errors)
                        self.assertIn("Invalid table separator cell", errors[0])

    def test_reference_table_rejects_pipe_less_separators(self):
        cases = (
            ("| First | Second |", "--- ---", "pipe delimiters"),
            ("First | Second", ":--- :---", "pipe delimiters"),
            ("| First | Second |", "------", "pipe delimiters"),
            ("| First | Second |", "-- ---", "Invalid table separator cell"),
            ("| First | Second |", ": :", "Invalid table separator cell"),
            ("| First | Second |", "", "Invalid table separator"),
            ("| First | Second |", "  ", "Invalid table separator"),
        )
        for header, separator, expected_error in cases:
            with self.subTest(header=header, separator=separator):
                errors = reference_table_separator_errors([header, separator])
                self.assertTrue(errors)
                self.assertIn(expected_error, errors[0])

    def test_reference_table_rejects_pipe_less_separator_column_mismatches(self):
        cases = (
            ("First | Second", "---"),
            ("First | Second", "---", "one | two"),
            ("First | Second", "--- --- ---", "one | two"),
            ("First ` | Second", "---"),
        )
        for lines in cases:
            with self.subTest(lines=lines):
                errors = reference_table_separator_errors(lines)
                self.assertTrue(errors)
                self.assertIn("matching column counts", errors[0])

    def test_reference_table_separator_validator_ignores_prose_with_pipes(self):
        lines = [
            "Use `first | second` for the choice.",
            "---",
            "The `left | right` text belongs to this example.",
            "Keep this prose line after another prose line.",
        ]

        self.assertEqual([], reference_table_separator_errors(lines))
        self.assertEqual(
            [],
            reference_table_separator_errors(
                ["Use left \\| right for the choice.", "---", "Keep reading."]
            ),
        )
        self.assertEqual(
            [],
            reference_table_separator_errors(
                ["Use input | output mapping for the task.", ": :", "Keep reading."]
            ),
        )

    def test_reference_table_data_rows_are_not_separators(self):
        lines = [
            "| First | Second |",
            "|---|---|",
            "| --- | value |",
        ]

        self.assertEqual([], reference_table_separator_errors(lines))

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
        self.assertIn("from any configuration source", signal)
        self.assertIn("does not start the engine", signal)
        self.assertIn("neither local nor a test-owned container", signal)
        self.assertIn("the test runs no process or decision", signal)
        self.assertIn("remote-engine test", requirement)
        self.assertIn("report only", requirement)
        self.assertIn(
            "cpt deletes all runtime data between tests, so the test needs a dedicated "
            "camunda 8 runtime.",
            requirement,
        )
        self.assertNotIn("shared environment", requirement)

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

    def test_remote_engine_tests_require_process_or_decision_execution(self):
        reference_text = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8")
        inventory_scope_rows = markdown_table(
            TEST_MIGRATION_REFERENCE,
            [
                "Runs a BPMN process or DMN decision on a Camunda 7 engine",
                "Uses a framework or approach that existed for Camunda 7",
                "Matches the shared-engine exception in Scope confirmation",
                "Scope decision",
            ],
        )
        shared_engine_scope_row = next(
            row
            for row in inventory_scope_rows
            if row["Runs a BPMN process or DMN decision on a Camunda 7 engine"] == "No"
            and row["Matches the shared-engine exception in Scope confirmation"]
            == "Yes"
        )
        no_process_scope_row = next(
            row
            for row in inventory_scope_rows
            if row["Runs a BPMN process or DMN decision on a Camunda 7 engine"] == "No"
            and row["Matches the shared-engine exception in Scope confirmation"]
            == "No"
        )
        self.assertLess(
            inventory_scope_rows.index(shared_engine_scope_row),
            inventory_scope_rows.index(no_process_scope_row),
        )
        self.assertIn(
            "the skill includes the test in scope as `remote-engine test` with "
            "`report only` handling",
            normalized(shared_engine_scope_row["Scope decision"]),
        )
        self.assertIn(
            "the skill excludes the test from scope",
            normalized(no_process_scope_row["Scope decision"]),
        )

        remote_engine_row = next(
            normalized(line)
            for line in reference_text.splitlines()
            if line.startswith("| 5 | remote-engine test |")
        )
        out_of_scope_row = next(
            normalized(line)
            for line in reference_text.splitlines()
            if line.startswith("| 8 | out of scope |")
        )
        self.assertIn("runs a bpmn process or dmn decision", remote_engine_row)
        for signal in (
            "camunda-platform-7-rest-client-spring-boot",
            "@externaltasksubscription",
            "the test may start the c7 engine with a testcontainers image or docker compose",
        ):
            with self.subTest(signal=signal):
                self.assertIn(signal, remote_engine_row)
        self.assertIn(
            "the skill classifies every other test that runs no process or decision as "
            "out of scope, including a test that only deploys a model.",
            out_of_scope_row,
        )
        self.assertIn(
            "when the shared-engine exception in scope confirmation applies, the skill "
            "classifies that test as a remote-engine test instead.",
            out_of_scope_row,
        )
        confirmation_rows = markdown_table(
            TEST_MIGRATION_REFERENCE,
            ["Signal", "Confirmation required"],
        )
        no_process_rule = next(
            row
            for row in confirmation_rows
            if "does not match the shared-engine exception"
            in normalized(row["Signal"])
        )
        self.assertIn(
            "the skill classifies the test as out of scope",
            normalized(no_process_rule["Confirmation required"]),
        )

        shared_engine = next(
            row
            for row in confirmation_rows
            if "shared engine url" in normalized(row["Signal"])
        )
        self.assertIn(
            "reads a shared engine url from any configuration source, does not start "
            "the engine, and the engine is neither local nor a test-owned "
            "container. the test runs no process or decision",
            normalized(shared_engine["Signal"]),
        )
        self.assertIn(
            "keep it as `remote-engine test` and use `report only` handling",
            normalized(shared_engine["Confirmation required"]),
        )

        overrides = markdown_table(
            TEST_MIGRATION_REFERENCE,
            ["Condition", "Handling", "Reason or note"],
        )
        shared_engine_override = next(
            row
            for row in overrides
            if "shared engine url" in normalized(row["Condition"])
        )
        self.assertEqual("Report only", shared_engine_override["Handling"])
        self.assertIn(
            "record the exact shared-engine reason below",
            normalized(shared_engine_override["Reason or note"]),
        )
        self.assertIn(
            "append `test migration needs camunda 8.9 or later`",
            normalized(shared_engine_override["Reason or note"]),
        )

        headers = ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"]
        for inventory_path, handling in (
            (EXPECTED_ASSESSMENT, "Migrate (lower priority)"),
            (EXPECTED_ASSESSMENT_88, "Report only"),
        ):
            payment_test = next(
                row
                for row in markdown_table(inventory_path, headers)
                if row["Test ID"].endswith(
                    "PaymentWorkerIT#chargesPaymentThroughEngineRest"
                )
            )
            self.assertEqual("remote-engine test", payment_test["Test kind"])
            self.assertEqual(handling, payment_test["Handling"])
            if inventory_path == EXPECTED_ASSESSMENT_88:
                self.assertIn(
                    "test migration needs camunda 8.9 or later",
                    normalized(payment_test["Notes"]),
                )

    def test_clockutil_timer_utility_does_not_trigger_manual_redesign(self):
        reference_text = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8")
        reference = normalized(reference_text)
        self.assertIn(
            "clockutil` used to control timers is a supported test utility.",
            reference,
        )
        self.assertIn(
            "the skill does not assign `manual redesign` based on `clockutil` alone.",
            reference,
        )

        manual_redesign_row = next(
            normalized(line)
            for line in reference_text.splitlines()
            if line.startswith("| 2 | manual redesign |")
        )
        self.assertIn("unsupported engine internals", manual_redesign_row)
        self.assertIn("clockutil", manual_redesign_row)
        self.assertIn("does not trigger this signal by itself", manual_redesign_row)

        confirmation_rows = markdown_table(
            TEST_MIGRATION_REFERENCE,
            ["Signal", "Confirmation required"],
        )
        clockutil_row = next(
            row
            for row in confirmation_rows
            if "clockutil" in normalized(row["Signal"])
        )
        self.assertIn(
            "supported test utility",
            normalized(clockutil_row["Confirmation required"]),
        )

        priority_rows = markdown_table(
            TEST_MIGRATION_REFERENCE,
            ["Priority", "Test kind", "Detect by", "Handling"],
        )
        engine_internal_row = next(
            row for row in priority_rows if row["Test kind"] == "manual redesign"
        )
        internal_signal = normalized(engine_internal_row["Detect by"])
        self.assertIn("unsupported engine internals", internal_signal)
        self.assertIn("clockutil", internal_signal)
        self.assertIn("does not trigger this signal by itself", internal_signal)

        timer_source = (
            C7_SOURCE
            / "engine-tests/src/test/java/com/camunda/fixture/order/OrderTimerTest.java"
        ).read_text(encoding="utf-8")
        self.assertIn("org.camunda.bpm.engine.impl.util.ClockUtil", timer_source)
        headers = ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"]
        for inventory_path, handling in (
            (EXPECTED_ASSESSMENT, "Migrate to CPT"),
            (EXPECTED_ASSESSMENT_88, "Report only"),
        ):
            timer_rows = [
                row
                for row in markdown_table(inventory_path, headers)
                if row["Test ID"].endswith("OrderTimerTest#escalatesAfterOneDay")
            ]
            self.assertEqual(len(timer_rows), 1)
            self.assertEqual("process test", timer_rows[0]["Test kind"])
            self.assertEqual(handling, timer_rows[0]["Handling"])

    def test_scenario_test_classification_is_method_scoped(self):
        inventory = markdown_table(
            EXPECTED_ASSESSMENT,
            ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"],
        )
        for row in inventory:
            if row["Test kind"] != "scenario test":
                continue

            source = (C7_SOURCE / row["File"]).read_text(encoding="utf-8")
            method_name = row["Test ID"].rsplit("#", 1)[1]
            with self.subTest(test_id=row["Test ID"]):
                self.assertRegex(
                    java_method_body(source, method_name),
                    r"\bScenario\.(?:run|use)\s*\(",
                )

    def test_mixed_manual_methods_preserve_required_class_setup(self):
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
        self.assertIn(
            "when the skill changes a class's setup, it first inspects every test method "
            "and each method's shared scenario runner, `processscenario` mock, c7 "
            "engine rule, and deployment dependencies.",
            reference,
        )
        setup_actions = markdown_table(
            TEST_MIGRATION_REFERENCE,
            ["Retained method condition", "Class setup action"],
        )
        manual_actions = [
            row
            for row in setup_actions
            if "manual method" in row["Retained method condition"].lower()
        ]
        self.assertEqual(1, len(manual_actions))
        self.assertIn(
            "the skill moves migrated methods to a separate cpt class",
            normalized(manual_actions[0]["Class setup action"]),
        )
        self.assertIn(
            "while a retained manual method needs c7 scenario setup, the skill moves "
            "migrated methods to a separate cpt class or retains the scenario runner, "
            "`processscenario` mock, c7 engine rule, and deployments",
            normalized(manual_actions[0]["Class setup action"]),
        )

    def test_prepare_step_removes_shared_scenario_setup_only_when_unused(self):
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))

        self.assertIn(
            "4. when no retained method needs the `processscenario` mock or scenario "
            "runner setup, the skill removes both.",
            reference,
        )

    def test_scenario_mapping_removes_shared_setup_only_when_unused(self):
        scenario_mappings = markdown_table(
            TEST_MIGRATION_REFERENCE,
            ["Camunda Platform Scenario", "Camunda Process Test 8.9 or later", "Notes"],
        )
        mock_mapping = [
            row
            for row in scenario_mappings
            if "@mock processscenario" in row["Camunda Platform Scenario"].lower()
        ]
        self.assertEqual(1, len(mock_mapping))
        self.assertIn(
            "when no retained method needs c7 scenario setup, the skill removes the "
            "mock and scenario runner setup",
            normalized(mock_mapping[0]["Camunda Process Test 8.9 or later"]),
        )

    def test_skill_scope_rules_use_ears_triggers(self):
        skill = MIGRATION_SKILL.read_text(encoding="utf-8")
        normalized_skill = normalized(skill)
        unqualified_scope_rules = [
            line.strip()
            for line in skill.splitlines()
            if re.match(
                r"^\s*For\b(?!\s+(?:each|every|example)\b)",
                line,
                flags=re.IGNORECASE,
            )
        ]

        self.assertEqual([], unqualified_scope_rules)
        self.assertIn(
            "when code migration includes camunda platform scenario tests, follow "
            "`references/test-migration.md`.",
            normalized_skill,
        )

    def test_reference_conditional_rules_use_ears_triggers(self):
        reference = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8")
        non_ears_for_openers = re.findall(
            r"(?:^|[.!?]\s+|\|\s*|[-*]\s+)"
            r"(For\s+(?!each\b|every\b|example\b)[^.!?\n]*)",
            reference,
            flags=re.IGNORECASE | re.MULTILINE,
        )

        self.assertEqual([], non_ears_for_openers)
        self.assertEqual([], non_ears_conditional_rules(reference))
        for marker in ("only when", "unless", "until", "when"):
            with self.subTest(marker=marker):
                self.assertTrue(
                    non_ears_conditional_rules(
                        "The skill removes this {} the rule applies.".format(marker)
                    )
                )
        non_ears_temporal_starters = (
            ("before", "Before the next step, the skill checks the state."),
            (
                "after",
                "After each step, the skill asserts the expected timer effect.",
            ),
            ("once", "Once the step completes, the skill checks the state."),
            ("whenever", "Whenever the step completes, the skill checks the state."),
            (
                "provided that",
                "Provided that the step completes, the skill checks the state.",
            ),
            (
                "as long as",
                "As long as the step is active, the skill checks the state.",
            ),
            (
                "as soon as",
                "As soon as the step completes, the skill checks the state.",
            ),
        )
        for marker, rule in non_ears_temporal_starters:
            with self.subTest(marker=marker):
                self.assertTrue(non_ears_conditional_rules(rule))
        self.assertTrue(
            non_ears_conditional_rules(
                "The skill waits for the result\nwhen the test is complete."
            )
        )

    def test_if_rules_require_a_complete_condition_and_response(self):
        malformed_rules = (
            "If a dependency remains, the skill keeps it.",
            "If a dependency remains, then.",
            "If a dependency remains, then ,",
            "If a dependency remains then the skill keeps it.",
            "If, then the skill keeps it.",
            "If ..., then the skill keeps it.",
        )
        for rule in malformed_rules:
            with self.subTest(rule=rule):
                self.assertTrue(non_ears_conditional_rules(rule))

        self.assertEqual(
            [],
            non_ears_conditional_rules(
                "If a dependency remains, then the skill keeps it."
            ),
        )

    def test_build_resource_checks_are_scoped_by_platform(self):
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
        start = reference.index("when the skill plans a target-build change")
        end = reference.index("| source-set condition", start)
        sentences = re.split(r"(?<=[.!?])\s+", reference[start:end])
        platform_markers = {
            "maven": ("testsourcedirectory", "compiler include patterns", "targetpath"),
            "gradle": (
                "test source set",
                "test-task include and exclude patterns",
                "`processresources`",
                "`processtestresources`",
                "their filters and output paths",
            ),
        }

        for platform, markers in platform_markers.items():
            for marker in markers:
                with self.subTest(platform=platform, marker=marker):
                    sentence = next(
                        (sentence for sentence in sentences if marker in sentence),
                        None,
                    )
                    self.assertTrue(
                        sentence is not None
                        and sentence.startswith(
                            "where a project uses {}, ".format(platform)
                        ),
                        "{} check is not scoped to {}: {}".format(
                            marker, platform, sentence
                        ),
                    )

    def test_linear_paths_may_replace_cpt_conditionals_with_sequential_calls(self):
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))

        self.assertIn(
            "the skill uses a cpt conditional behavior for each user-task, message, "
            "signal, event-gateway, or conditional-event stub.",
            reference,
        )
        self.assertIn(
            "when the process path is linear, the skill may use sequential cpt calls "
            "instead of conditional behaviors. (may)",
            reference,
        )

    def test_java_method_body_ignores_braces_in_non_code(self):
        snippets = (
            ("string opening brace", 'String value = "{";'),
            ("string closing brace", 'String value = "}";'),
            ("escaped quote in string", r'String value = "\"}";'),
            ("character opening brace", "char value = '{';"),
            ("character closing brace", "char value = '}';"),
            ("line comment opening brace", "// {"),
            ("line comment closing brace", "// }"),
            ("block comment opening brace", "/* { */"),
            ("block comment closing brace", "/* } */"),
            ("text block opening brace", 'String value = """\n{\n""";'),
            ("text block closing brace", 'String value = """\n}\n""";'),
        )

        for context, snippet in snippets:
            source = "\n".join(
                (
                    "class Sample {",
                    "  void target() {",
                    "    " + snippet,
                    "    bodyMarker();",
                    "  }",
                    "  void afterTarget() {}",
                    "}",
                )
            )
            with self.subTest(context=context):
                method = java_method_body(source, "target")
                self.assertIn("bodyMarker();", method)
                self.assertNotIn("afterTarget", method)


    def test_c8_test_apis_have_one_precedence_row(self):
        priority_rows = markdown_table(
            TEST_MIGRATION_REFERENCE,
            ["Priority", "Test kind", "Detect by", "Handling"],
        )
        c8_rows = [
            row
            for row in priority_rows
            if row["Test kind"] == "out of scope (Camunda 8)"
        ]

        self.assertEqual(1, len(c8_rows))
        self.assertEqual("1", c8_rows[0]["Priority"])
        detect_by = normalized(c8_rows[0]["Detect by"])
        self.assertIn("io.camunda.zeebe.process.test.*", detect_by)
        self.assertIn("io.camunda.process.test.*", detect_by)
        self.assertNotIn("without running a c7 engine", detect_by)


    def test_camunda_8_8_inventory_applies_version_gate(self):
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
                is_shared_engine_test = test_id.endswith(
                    "SharedEngineSmokeIT#readsConfiguredSharedEngine"
                )
                if other["Test kind"] == "remote-engine test":
                    other_notes = normalized(other["Notes"])
                    self.assertIn(version_reason, other_notes)
                    if is_shared_engine_test:
                        self.assertIn(normalized(SHARED_ENGINE_REASON), other_notes)
                    else:
                        self.assertNotIn(normalized(SHARED_ENGINE_REASON), other_notes)
                    self.assertNotIn(
                        normalized(
                            "Report only until the remote-engine migration "
                            "procedure is defined"
                        ),
                        other_notes,
                    )

                if row["Test kind"] == "manual redesign":
                    self.assertEqual(other["Handling"], "Report only")
                    self.assertEqual(other["Notes"], row["Notes"])
                    continue

                if row["Handling"] == "Not part of test migration":
                    self.assertEqual(other["Handling"], row["Handling"])
                    self.assertEqual(other["Notes"], row["Notes"])
                    continue

                if row["Handling"] == "Report only":
                    self.assertEqual(other["Handling"], "Report only")
                    self.assertIn(
                        normalized("test migration needs Camunda 8.9 or later"),
                        normalized(other["Notes"]),
                    )
                    reason = REPORT_ONLY_REASONS.get(row["Test kind"])
                    if (
                        row["Test kind"] == "remote-engine test"
                        and is_shared_engine_test
                    ):
                        reason = normalized(SHARED_ENGINE_REASON)
                    if reason is not None:
                        self.assertIn(reason, normalized(other["Notes"]))
                    source_note_id = row["Notes"].split(";", 1)[0].strip()
                    self.assertIn(
                        normalized(source_note_id), normalized(other["Notes"])
                    )
                    continue

                self.assertIn(
                    row["Handling"],
                    MIGRATED_HANDLINGS,
                )
                self.assertEqual(other["Handling"], "Report only")
                self.assertIn(version_reason, normalized(other["Notes"]))

    def test_inventory_records_the_mocks_modifier_for_detected_tests(self):
        expected_mock_tests = {
            "engine-tests:com.camunda.fixture.order.OrderProcessTest#approvesAndShipsOrder",
            "engine-tests:com.camunda.fixture.order.FulfillmentScenarioTest#"
            "shouldCompleteWorkAfterTwoDailyReminders",
            "engine-tests-legacy:com.camunda.fixture.order.FulfillmentScenarioTest#"
            "shouldCompleteWorkAfterTwoDailyReminders",
            "engine-tests:com.camunda.fixture.order.OrderMockitoTest#"
            "registersWholeDelegateAndExecutionListenerMocks",
            "engine-tests:com.camunda.fixture.order.OrderMockitoTest#"
            "registersDelegateOutputAndVerifiesItsInvocation",
            "engine-tests:com.camunda.fixture.order.OrderMockitoTest#routesDelegateBpmnError",
            "engine-tests:com.camunda.fixture.order.OrderMockitoTest#"
            "throwsWhenTheSynchronousDelegateFails",
            "engine-tests:com.camunda.fixture.order.OrderAutoMockTest#"
            "autoMocksDelegatesAndTracksCoverage",
            "engine-tests:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#"
            "shouldStartMessageProcess",
            "engine-tests-legacy:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#"
            "shouldStartMessageProcess",
            "engine-tests:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#"
            "shouldCountCompletedVisitsSeparately",
            "engine-tests-legacy:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#"
            "shouldCountCompletedVisitsSeparately",
            "engine-tests:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#"
            "shouldCountMixedFinishedVisitsByOutcome",
            "engine-tests-legacy:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#"
            "shouldCountMixedFinishedVisitsByOutcome",
            "spring-boot-app:com.camunda.fixture.subscription.SubscriptionProcessTest#"
            "activatesSubscription",
            "spring-boot-app:com.camunda.fixture.subscription.SubscriptionEndpointTest#"
            "startsSubscriptionFromHttp",
            "spring-boot-app:com.camunda.fixture.subscription.ActivateDelegateMockTest#"
            "mocksDelegateBean",
            "spring-boot-app:com.camunda.fixture.subscription.SubscriptionStandaloneTest#"
            "startsSubscriptionWithoutSpring",
        }
        scenario_test = (
            C7_SOURCE
            / "engine-tests/src/test/java/com/camunda/fixture/order/FulfillmentScenarioTest.java"
        ).read_text(encoding="utf-8")
        self.assertIn("@Mock private ProcessScenario process;", scenario_test)
        self.assertIn('.withMockedProcess("shipping")', scenario_test)
        headers = ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"]

        for inventory_path in (EXPECTED_ASSESSMENT, EXPECTED_ASSESSMENT_88):
            rows = markdown_table(inventory_path, headers)
            recorded_mock_tests = {
                row["Test ID"]
                for row in rows
                if "mocks"
                in {
                    normalized(signal.strip().strip("`"))
                    for signal in row["Signals"].split(";")
                }
            }
            with self.subTest(inventory=inventory_path):
                self.assertEqual(expected_mock_tests, recorded_mock_tests)

    def test_inventory_does_not_mark_a_concrete_registered_listener_as_mocked(self):
        test_id = (
            "engine-tests:com.camunda.fixture.order.OrderTimerTest#escalatesAfterOneDay"
        )
        order_timer_test = (
            C7_SOURCE
            / "engine-tests/src/test/java/com/camunda/fixture/order/OrderTimerTest.java"
        ).read_text(encoding="utf-8")
        listener = (
            C7_SOURCE
            / "engine-tests/src/main/java/com/camunda/fixture/order/OrderAuditListener.java"
        ).read_text(encoding="utf-8")
        self.assertIn(
            'Mocks.register("orderAuditListener", new OrderAuditListener())',
            order_timer_test,
        )
        self.assertIn('execution.setVariable("auditStarted", true)', listener)

        headers = ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"]
        for inventory_path in (EXPECTED_ASSESSMENT, EXPECTED_ASSESSMENT_88):
            rows = markdown_table(inventory_path, headers)
            row = next(row for row in rows if row["Test ID"] == test_id)
            signals = {
                normalized(signal.strip().strip("`"))
                for signal in row["Signals"].split(";")
            }
            with self.subTest(inventory=inventory_path):
                self.assertNotIn("mocks", signals)

    def test_concrete_listener_side_effect_is_preserved_in_cpt_fixture(self):
        c7_test = (
            C7_SOURCE
            / "engine-tests/src/test/java/com/camunda/fixture/order/OrderTimerTest.java"
        ).read_text(encoding="utf-8")
        c8_test = (
            EXPECTED_C8
            / "engine-tests/src/test/java/com/camunda/fixture/order/OrderTimerTest.java"
        ).read_text(encoding="utf-8")

        self.assertIn(
            'assertThat(instance).variables().containsEntry("auditStarted", true)',
            c7_test,
        )
        self.assertIn('.hasVariable("auditStarted", true)', c8_test)

    def test_mock_modifier_detection_is_completed_during_step_two(self):
        skill = (
            REPO_ROOT / "agentic-migration-skills/skills/migrate-c7-to-c8-code/SKILL.md"
        ).read_text(encoding="utf-8")
        step_two, step_three = skill.split("### Step 3: Execute Migration", 1)

        self.assertIn("For every in-scope test method, detect mock signals", step_two)
        self.assertIn("detect mock signals from the original C7 test source", step_two)
        self.assertIn("Record the source-derived `mocks` modifier", step_two)
        self.assertIn(
            "whose Step 2 Test Inventory `Signals` column contains `mocks`",
            step_three,
        )
        self.assertNotIn("derives the `mocks` modifier from source", step_three)

    def test_camunda_8_8_inventory_preserves_signals_across_targets(self):
        headers = ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"]
        inventory = markdown_table(EXPECTED_ASSESSMENT, headers)
        inventory_88 = markdown_table(EXPECTED_ASSESSMENT_88, headers)
        self.assert_unique_rows(inventory, "Test ID", EXPECTED_ASSESSMENT)
        self.assert_unique_rows(inventory_88, "Test ID", EXPECTED_ASSESSMENT_88)
        rows_89 = {row["Test ID"]: row for row in inventory}
        rows_88 = {row["Test ID"]: row for row in inventory_88}

        self.assertEqual(set(rows_89), set(rows_88))
        for test_id, row in rows_89.items():
            with self.subTest(test_id=test_id):
                other = rows_88[test_id]
                for column in ("File", "Test kind", "Signals", "Models"):
                    self.assertEqual(other[column], row[column])
                if row["Handling"].startswith("Migrate"):
                    self.assertEqual(other["Handling"], "Report only")
                    self.assertIn(
                        normalized("test migration needs Camunda 8.9 or later"),
                        normalized(other["Notes"]),
                    )
                else:
                    self.assertEqual(other["Handling"], row["Handling"])
                    if other["Notes"] != row["Notes"]:
                        self.assertIn(
                            normalized("test migration needs Camunda 8.9 or later"),
                            normalized(other["Notes"]),
                        )

   def test_inventory_and_parity_match_supported_test_migration_rules(self):
        inventory = markdown_table(
            EXPECTED_ASSESSMENT,
            ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"],
        )
        parity = markdown_table(
            EXPECTED_PARITY,
            ["Camunda 7 Test ID", "CPT Test ID(s)", "Verdict", "Notes"],
        )
        parity_by_id = {row["Camunda 7 Test ID"]: row for row in parity}
        cpt_test_ids = test_method_ids(EXPECTED_C8)

        for row in inventory:
            test_id = row["Test ID"]
            test_kind = row["Test kind"]
            if test_kind == "process test":
                expected_handling = MIGRATE_TO_CPT
            elif test_kind == "decision test":
                expected_handling = "Migrate"
            elif test_kind == "scenario test":
                expected_handling = MIGRATE_LOWER_PRIORITY
            elif test_kind == "remote-engine test":
                expected_handling = (
                    MIGRATE_LOWER_PRIORITY
                    if test_id.endswith(
                        "PaymentWorkerIT#chargesPaymentThroughEngineRest"
                    )
                    else "Report only"
                )
            elif test_kind in REPORT_ONLY_REASONS:
                expected_handling = "Report only"
            else:
                continue

            with self.subTest(test_id=test_id):
                self.assertEqual(row["Handling"], expected_handling)
                if expected_handling != "Report only":
                    continue

                parity_row = parity_by_id.get(test_id)
                self.assertIsNotNone(parity_row, "Missing parity row for {}".format(test_id))
                self.assertEqual(parity_row["Verdict"], "manual")

                shared_engine = (
                    test_kind == "remote-engine test"
                    and normalized(SHARED_ENGINE_REASON) in normalized(row["Notes"])
                )
                reason = (
                    normalized(SHARED_ENGINE_REASON)
                    if shared_engine
                    else REPORT_ONLY_REASONS[test_kind]
                )
                self.assertIn(reason, normalized(row["Notes"]))
                self.assertIn(reason, normalized(parity_row["Notes"]))
                mapped_ids = TEST_ID_RE.findall(parity_row["CPT Test ID(s)"])
                if shared_engine:
                    self.assertEqual(parity_row["CPT Test ID(s)"], "—")
                else:
                    self.assertTrue(mapped_ids, "Missing manual CPT mapping for {}".format(test_id))
                    for mapped_id in mapped_ids:
                        with self.subTest(test_id=test_id, cpt_test=mapped_id):
                            self.assertIn(mapped_id, cpt_test_ids)

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

        self.assertEqual(
            [],
            migrated_test_parity_errors(inventory, parity_by_id, cpt_test_ids),
        )

        required_verdicts = {
            "engine-tests:com.camunda.fixture.order.SupportCaseTest#startsSupportCase":
                ("retired", "CMMN has no Camunda 8 equivalent"),
            "engine-tests:com.camunda.fixture.order.FluentModelTest#buildsAndStartsModel":
                ("retired", "model built in Java, migrated by hand later"),
            "remote-engine:com.camunda.fixture.payment.SharedEngineSmokeIT#readsConfiguredSharedEngine":
                ("manual", SHARED_ENGINE_REASON),
        }
        for test_id, (verdict, note) in required_verdicts.items():
            with self.subTest(test_id=test_id):
                self.assertIn(test_id, parity_by_id)
                self.assertEqual(parity_by_id[test_id]["Verdict"], verdict)
                self.assertIn(note.lower(), normalized(parity_by_id[test_id]["Notes"]))

        shared_engine_test_id = (
            "remote-engine:com.camunda.fixture.payment.SharedEngineSmokeIT#readsConfiguredSharedEngine"
        )
        self.assertEqual(parity_by_id[shared_engine_test_id]["Verdict"], "manual")
        self.assertEqual(
            parity_by_id[shared_engine_test_id]["Notes"],
            SHARED_ENGINE_REASON,
        )

    def test_lower_priority_scenarios_require_valid_primary_parity_rows(self):
        test_id = (
            "engine-tests:com.camunda.fixture.order."
            "ScenarioMappingEdgeCasesTest#shouldStartMessageProcess"
        )
        inventory = [{"Test ID": test_id, "Handling": MIGRATE_LOWER_PRIORITY}]
        valid_row = {
            "Verdict": "migrated",
            "CPT Test ID(s)": test_id,
        }
        self.assertEqual(
            [],
            migrated_test_parity_errors(
                inventory, {test_id: valid_row}, {test_id}
            ),
        )

        unknown_cpt_id = test_id.rsplit("#", 1)[0] + "#missingCptTest"
        invalid_cases = (
            ("missing row", {}, ["Missing parity row for {}".format(test_id)]),
            (
                "non-migrated verdict",
                {test_id: {"Verdict": "manual", "CPT Test ID(s)": test_id}},
                ["Expected migrated verdict for {}".format(test_id)],
            ),
            (
                "missing CPT mapping",
                {test_id: {"Verdict": "migrated", "CPT Test ID(s)": "—"}},
                ["Missing CPT test mapping for {}".format(test_id)],
            ),
            (
                "unknown CPT mapping",
                {
                    test_id: {
                        "Verdict": "migrated",
                        "CPT Test ID(s)": unknown_cpt_id,
                    }
                },
                [
                    "Unknown CPT test mapping {} for {}".format(
                        unknown_cpt_id, test_id
                    )
                ],
            ),
        )
        for case, parity_by_id, expected_errors in invalid_cases:
            with self.subTest(case=case):
                self.assertEqual(
                    expected_errors,
                    migrated_test_parity_errors(
                        inventory, parity_by_id, {test_id}
                    ),
                )

    def test_shared_engine_reason_is_reported_in_both_assessments(self):
        headers = ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"]
        shared_engine_test_id = (
            "remote-engine:com.camunda.fixture.payment.SharedEngineSmokeIT#readsConfiguredSharedEngine"
        )

        for assessment_path in (EXPECTED_ASSESSMENT, EXPECTED_ASSESSMENT_88):
            rows = {
                row["Test ID"]: row for row in markdown_table(assessment_path, headers)
            }
            with self.subTest(assessment=assessment_path):
                self.assertIn(shared_engine_test_id, rows)
                self.assertEqual(rows[shared_engine_test_id]["Handling"], "Report only")
                expected_notes = "R2; {}".format(SHARED_ENGINE_REASON)
                if assessment_path == EXPECTED_ASSESSMENT_88:
                    expected_notes += " test migration needs Camunda 8.9 or later"
                self.assertEqual(
                    rows[shared_engine_test_id]["Notes"],
                    expected_notes,
                )

    def test_every_converted_job_type_has_java_worker_or_mock(self):
        java_source = "\n".join(
            path.read_text(encoding="utf-8") for path in fixture_files(EXPECTED_C8, "*.java")
        )
        job_types = set()
        for model in fixture_files(EXPECTED_C8, "converted-c8-*.bpmn"):
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

    def test_reference_contains_engine_scope_and_test_kind_table(self):
        self.assertTrue(
            TEST_MIGRATION_REFERENCE.is_file(),
            "The test migration reference must exist.",
        )
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
        scope_rows = markdown_table(
            TEST_MIGRATION_REFERENCE,
            [
                "Runs a BPMN process or DMN decision on a Camunda 7 engine",
                "Uses a framework or approach that existed for Camunda 7",
                "Matches the shared-engine exception in Scope confirmation",
                "Scope decision",
            ],
        )
        self.assertEqual(
            [
                {
                    "Runs a BPMN process or DMN decision on a Camunda 7 engine": "Yes",
                    "Uses a framework or approach that existed for Camunda 7": "Yes",
                    "Matches the shared-engine exception in Scope confirmation": "Any",
                    "Scope decision": (
                        "When both prerequisites are met, the skill includes the "
                        "test in scope."
                    ),
                },
                {
                    "Runs a BPMN process or DMN decision on a Camunda 7 engine": "No",
                    "Uses a framework or approach that existed for Camunda 7": "Any",
                    "Matches the shared-engine exception in Scope confirmation": "Yes",
                    "Scope decision": (
                        "The skill includes the test in scope as "
                        "`remote-engine test` with `Report only` handling."
                    ),
                },
                {
                    "Runs a BPMN process or DMN decision on a Camunda 7 engine": "No",
                    "Uses a framework or approach that existed for Camunda 7": "Any",
                    "Matches the shared-engine exception in Scope confirmation": "No",
                    "Scope decision": (
                        "The skill excludes the test from scope."
                    ),
                },
                {
                    "Runs a BPMN process or DMN decision on a Camunda 7 engine": "Yes",
                    "Uses a framework or approach that existed for Camunda 7": "No",
                    "Matches the shared-engine exception in Scope confirmation": "Any",
                    "Scope decision": (
                        "If a test does not use a framework or approach that existed "
                        "for Camunda 7, then the skill excludes the test from scope."
                    ),
                },
            ],
            scope_rows,
        )
        self.assertIn(
            "the skill classifies tests by executed engine behavior, not assertion type.",
            reference,
        )
        self.assertIn("| priority | test kind | detect by | handling |", reference)
        process_test_row = next(
            row
            for row in markdown_table(
                TEST_MIGRATION_REFERENCE,
                ["Priority", "Test kind", "Detect by", "Handling"],
            )
            if row["Test kind"] == "process test"
        )
        self.assertEqual("Migrate to CPT", process_test_row["Handling"])
        self.assertIn(
            "when the target is camunda 8.9 or later, the skill migrates every test with "
            "test kind `decision test`.",
            reference,
        )
        self.assertNotIn("engine-test migration procedure is undefined", reference)
        self.assertIn("| modifier | detect by | used by |", reference)
        self.assertIn("| `mocks` |", reference)
        self.assertIn("cpt (`io.camunda.process.test.*`)", reference)
        self.assertIn(
            "the test inventory records `mocks` in its `signals` column for every in-scope test method",
            reference,
        )
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

    def test_step_4_gate_covers_selected_remote_engine_tests(self):
        tests_gate = normalized(
            MIGRATION_SKILL.read_text(encoding="utf-8")
            .split("9. **Tests** —", 1)[1]
            .split("\n10.", 1)[0]
        )
        self.assertIn(
            "when the target is camunda 8.9 or later, verify that every process test "
            "with handling `migrate to cpt` and every remote-engine test with handling "
            "`migrate (lower priority)` were migrated by following "
            "`references/test-migration.md`.",
            tests_gate,
        )
        self.assertIn(
            "when the target is camunda 8.8, verify that each such process test and "
            "remote-engine test keeps `report only` handling with the reason "
            "`test migration needs camunda 8.9 or later`.",
            tests_gate,
        )

    def test_cucumber_scenarios_have_discovery_and_stable_ids(self):
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
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
            "when the skill checks for camunda 7 process or decision calls, it inspects "
            "both step-definition methods and constructor-registered lambda steps.",
            "cucumber scenarios use camunda 7 apis to run an engine-backed bpmn process "
            "or dmn decision",
            "the cucumber classification includes applicable hooks, not only steps",
            "list every test with handling `report only` by test id",
            "apply the table from top to bottom. the first matching row assigns one test "
            "kind and handling.",
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
            "cmmn tests and tests that use unsupported camunda engine internals do not meet this scope rule.",
            reference,
        )
        self.assertIn(
            "the skill inventories cmmn tests and tests that use unsupported engine internals as "
            "`manual redesign` with "
            "`report only` handling.",
            reference,
        )
        scope_confirmation_row = next(
            normalized(line)
            for line in reference_text.splitlines()
            if line.startswith("| A test uses CMMN")
        )
        self.assertIn("unsupported camunda engine internals", scope_confirmation_row)
        self.assertIn(
            "the skill marks the test as `manual redesign` and uses `report only` handling.",
            scope_confirmation_row,
        )
        self.assertIn(
            "the skill applies this classification to tests that do not run a bpmn "
            "process or dmn decision.",
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
                "BPMN model built with the Camunda fluent model API, such as Bpmn.createExecutableProcess()",
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
        manual_migration_row = next(
            normalized(line)
            for line in reference_text.splitlines()
            if line.startswith("| 3 | manual migration |")
        )
        self.assertIn(
            "cucumber scenarios use camunda 7 apis to run an engine-backed bpmn "
            "process or dmn decision",
            manual_migration_row,
        )
        self.assertIn(
            "the cucumber classification includes applicable hooks, not only steps",
            manual_migration_row,
        )

    def test_spock_feature_methods_have_method_based_test_ids(self):
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
        self.assertIn(
            "the skill includes spock feature methods in groovy classes that extend "
            "`spock.lang.specification`.",
            reference,
        )
        self.assertIn(
            "the skill includes these methods without a `@test` annotation.",
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
            if line.startswith("| 3 | manual migration |")
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
            if line.startswith("| 3 | manual migration |")
        )
        out_of_scope_row = next(
            normalized(line)
            for line in reference_text.splitlines()
            if line.startswith("| 8 | out of scope |")
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
            "where kotlin or groovy tests run an engine-backed bpmn process or dmn "
            "decision on c7, the skill marks them as `manual migration`.",
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

    def test_reference_declares_instruction_convention_below_title(self):
        lines = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8").splitlines()

        self.assertEqual("# Test Migration", lines[0])
        self.assertEqual(
            normalized(
                'Every instruction in this reference is mandatory. "Never" means MUST NOT. '
                "A preference is marked (SHOULD) and an option is marked (MAY)."
            ),
            normalized(" ".join(lines[2:4])),
        )

    def test_readme_documents_c7_process_test_fixture_once(self):
        readme = (
            REPO_ROOT / "agentic-migration-skills/README.md"
        ).read_text(encoding="utf-8")

        self.assertEqual(1, readme.count("fixtures/c7-process-tests"))
        self.assertIn("inventories JUnit 3/4/5", readme)

    def test_w3_walkthrough_matches_remote_engine_parity(self):
        readme = (FIXTURE / "README.md").read_text(encoding="utf-8")
        walkthrough = normalized(
            next(line for line in readme.splitlines() if line.startswith("| W3 |"))
        )
        self.assertIn(
            "migrates selected process, decision, scenario, and remote-engine tests "
            "with available cpt procedures",
            walkthrough,
        )
        self.assertIn(
            "marks the paymentworker remote-engine test as migrated",
            walkthrough,
        )
        self.assertIn("keeps the shared-engine test manual", walkthrough)

        parity = markdown_table(
            EXPECTED_PARITY,
            ["Camunda 7 Test ID", "CPT Test ID(s)", "Verdict", "Notes"],
        )
        payment_test = next(
            row
            for row in parity
            if row["Camunda 7 Test ID"].endswith(
                "PaymentWorkerIT#chargesPaymentThroughEngineRest"
            )
        )
        shared_engine_test = next(
            row
            for row in parity
            if row["Camunda 7 Test ID"].endswith(
                "SharedEngineSmokeIT#readsConfiguredSharedEngine"
            )
        )
        self.assertEqual("migrated", payment_test["Verdict"])
        self.assertEqual("manual", shared_engine_test["Verdict"])

    def test_scenario_fixture_covers_retained_mockito_annotations(self):
        source_path = (
            C7_SOURCE
            / "engine-tests/src/test/java/com/camunda/fixture/order/"
            "ScenarioMappingEdgeCasesTest.java"
        )
        migrated_path = (
            EXPECTED_C8
            / "engine-tests/src/test/java/com/camunda/fixture/order/"
            "ScenarioMappingEdgeCasesTest.java"
        )
        required_annotations = ("@Spy", "@Captor", "@InjectMocks")

        for path in (source_path, migrated_path):
            source = path.read_text(encoding="utf-8")
            with self.subTest(path=path):
                for annotation in required_annotations:
                    self.assertIn(annotation, source)
                self.assertIn("MockitoAnnotations.openMocks(this)", source)
                method = java_method_body(source, "shouldCountCompletedVisitsSeparately")
                self.assertIn("collaboratorService.lookup", method)
                self.assertIn("orderIdCaptor.capture()", method)

    def test_cpt_detection_signal_uses_balanced_inline_code(self):
        reference = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8")
        self.assertIn("CPT (`io.camunda.process.test.*`)", reference)

    def test_counted_completion_mapping_preserves_exact_count(self):
        reference = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8")
        self.assertIn(
            '| `verify(process, times(n)).hasCompleted("E")` | '
            'Assert `hasCompletedElement("E", n)`. | '
            'The skill preserves the exact completed-element count. |',
            reference,
        )
        migrated = (
            EXPECTED_C8
            / "engine-tests/src/test/java/com/camunda/fixture/order/"
            "ScenarioMappingEdgeCasesTest.java"
        ).read_text(encoding="utf-8")
        self.assertIn('.hasCompletedElement("MixedWork", 2)', migrated)

    def test_scenario_user_task_wait_and_completion_share_instance_key(self):
        reference = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8")
        wait_state_behavior = reference.split("### Wait-state behavior", 1)[1].split(
            "### Scenario-to-CPT mapping", 1
        )[0]
        self.assertIn("processInstance.getProcessInstanceKey()", wait_state_behavior)
        self.assertIn("byKey(processInstanceKey)", wait_state_behavior)
        self.assertIn('byElementId("Review", processInstanceKey)', wait_state_behavior)
        self.assertNotIn("byProcessId(processId)", wait_state_behavior)
        self.assertNotIn('completeUserTask("Review", variables)', wait_state_behavior)

        mappings = markdown_table(
            TEST_MIGRATION_REFERENCE,
            [
                "Camunda Platform Scenario",
                "Camunda Process Test 8.9 or later",
                "Notes",
            ],
        )
        user_task_mappings = [
            row
            for row in mappings
            if 'waitsAtUserTask("X")' in row["Camunda Platform Scenario"]
        ]
        self.assertEqual(1, len(user_task_mappings))
        conversion = user_task_mappings[0]["Camunda Process Test 8.9 or later"]
        self.assertIn("byKey(processInstanceKey)", conversion)
        self.assertIn('byElementId("X", processInstanceKey)', conversion)

    def test_wait_state_targets_explain_instance_scope(self):
        reference = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8")
        wait_state_behavior = reference.split("### Wait-state behavior", 1)[1].split(
            "### Scenario-to-CPT mapping", 1
        )[0]
        normalized_behavior = normalized(wait_state_behavior)
        self.assertNotIn(
            "The skill scopes the condition and action to the same process instance key",
            wait_state_behavior,
        )
        self.assertIn(
            "where the corresponding cpt api accepts a process-instance selector, "
            "the skill scopes a condition or action to the scenario instance",
            normalized_behavior,
        )
        self.assertIn(
            "the message action targets a message name and evaluated correlation key, "
            "not the scenario start result's process-instance key",
            normalized_behavior,
        )
        self.assertIn(
            "the signal action broadcasts by signal name and can also advance another "
            "process instance waiting for that signal",
            normalized_behavior,
        )

    def test_scenario_gate_accepts_inventory_handling(self):
        headers = ["Test ID", "File", "Test kind", "Signals", "Models", "Handling", "Notes"]
        inventory = markdown_table(EXPECTED_ASSESSMENT, headers)
        scenario_handling = {
            row["Handling"] for row in inventory if row["Test kind"] == "scenario test"
        }
        self.assertIn(MIGRATE_LOWER_PRIORITY, scenario_handling)

        gate = markdown_table(
            TEST_MIGRATION_REFERENCE,
            ["Selected code approach", "Step 2 Test Inventory Handling", "Action"],
        )
        enabled_handling = {
            row["Step 2 Test Inventory Handling"]
            for row in gate
            if row["Selected code approach"] == "Approach A or B"
            and row["Action"].startswith("Apply the preparation")
        }
        self.assertIn(MIGRATE_LOWER_PRIORITY, enabled_handling)

    def test_reference_uses_ears_form_for_conditional_requirements(self):
        reference = TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8")
        conditional_sentences = re.findall(
            r"\bif\b[^.]*\.",
            reference,
            flags=re.IGNORECASE | re.DOTALL,
        )
        self.assertTrue(conditional_sentences, "Expected conditional requirements in the reference.")
        for sentence in conditional_sentences:
            with self.subTest(sentence=sentence.strip()):
                self.assertRegex(
                    sentence,
                    r"\bthen\b",
                    "Conditional requirements must use the EARS 'If ..., then ...' form.",
                )

    def test_reference_distinguishes_registry_bindings_from_test_doubles(self):
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
        self.assertIn("registry bindings, not mock evidence by themselves", reference)
        self.assertIn(
            "when the registered value is a test double, the skill counts the call as mock evidence",
            reference,
        )
        self.assertIn(
            "a spring `@mockbean` or `@mockitobean` used by the process qualifies "
            "whether it mocks a collaborator, delegate, or listener.",
            reference,
        )
        spring_delegate_test = (
            C7_SOURCE
            / "spring-boot-app/src/test/java/com/camunda/fixture/subscription/"
            "ActivateDelegateMockTest.java"
        ).read_text(encoding="utf-8")
        spring_collaborator_test = (
            C7_SOURCE
            / "spring-boot-app/src/test/java/com/camunda/fixture/subscription/"
            "SubscriptionProcessTest.java"
        ).read_text(encoding="utf-8")
        self.assertIn("@MockBean", spring_delegate_test)
        self.assertIn("JavaDelegate", spring_delegate_test)
        self.assertIn("@MockBean", spring_collaborator_test)
        self.assertIn("BillingClient", spring_collaborator_test)

    def test_reference_approves_only_new_cpt_decision_mocks(self):
        reference = normalized(TEST_MIGRATION_REFERENCE.read_text(encoding="utf-8"))
        self.assertIn("existing c7 decision mock", reference)
        self.assertIn("same-boundary migration", reference)
        self.assertIn("needs no additional approval", reference)
        self.assertIn("ask the user before adding a cpt decision mock", reference)
    def test_expected_report_files_exist(self):
        self.assertTrue(EXPECTED_ASSESSMENT.is_file())
        self.assertTrue(EXPECTED_ASSESSMENT_88.is_file())
        self.assertTrue(EXPECTED_PARITY.is_file())
        self.assertTrue(EXPECTED_TESTS_ONLY.is_file())


if __name__ == "__main__":
    unittest.main()
