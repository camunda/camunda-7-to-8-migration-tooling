from pathlib import Path
import ast
import re
import unittest
import xml.etree.ElementTree as ET


REPO_ROOT = Path(__file__).resolve().parents[3]
FIXTURE = REPO_ROOT / "agentic-migration-skills/fixtures/remote-engine-tests"
SKILL = REPO_ROOT / "agentic-migration-skills/skills/migrate-c7-to-c8-code/SKILL.md"
REFERENCE = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/test-migration.md"
)
ENGINE_REST_PATTERN = (
    REPO_ROOT
    / "code-conversion/patterns/40-test-assertions/60-remote-engine-tests/10-engine-rest-mapping.md"
)
JOB_PATTERN = (
    REPO_ROOT
    / "code-conversion/patterns/40-test-assertions/10-assertions/60-job.md"
)
SPRING_PATTERN = (
    REPO_ROOT
    / "code-conversion/patterns/40-test-assertions/20-test-setup/30-spring-boot-test.md"
)
DEPENDENCIES_PATTERN = REPO_ROOT / "code-conversion/patterns/10-general/dependencies.md"
PATTERN_SOURCES = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/pattern-catalog-sources.md"
)
CHECKLIST = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/code-transform-checklist.md"
)
INTERVIEW_QUESTIONS = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/interview-questions.md"
)
HTTP_TOPOLOGY = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/http-topology-migration.md"
)
EXPECTED = FIXTURE / "expected-c8"
SHARED_SOURCE = (
    FIXTURE
    / "shared-engine/c7-source/src/test/java/org/camunda/example/payment/SharedEnginePaymentTest.java"
)
SHARED_PROPERTIES = FIXTURE / "shared-engine/c7-source/src/test/resources/application-test.properties"
SHARED_REPORT = FIXTURE / "expected-shared-engine/MIGRATION_REPORT.md"
JAVA_PROPERTIES_REMOTE_RUNTIME = re.compile(
    r"(?m)^[ \t\f]*(?:camunda\.process-test\.runtime-mode|runtimeMode)"
    r"[ \t\f]*(?:[=:][ \t\f]*|[ \t\f]+)remote[ \t\f]*\r?$"
)
JAVA_PROPERTIES_UNICODE_ESCAPE = re.compile(
    r"(?<!\\)(?P<escaped_backslashes>(?:\\\\)*)\\u"
    r"(?P<codepoint>[0-9a-fA-F]{4})"
)
YAML_REMOTE_RUNTIME = re.compile(
    r"""(?m)^[ \t]*"""
    r"""(?P<key_quote>['"]?)"""
    r"""(?:runtime-mode|camunda\.process-test\.runtime-mode)(?P=key_quote)"""
    r"""[ \t]*:[ \t]*"""
    r"""(?:&[^\s#]+[ \t]+)?"""
    r"""(?:(?P<value_quote>['"])remote(?P=value_quote)|remote)"""
    r"""(?:[ \t]+#.*)?[ \t]*\r?$"""
)
YAML_REMOTE_RUNTIME_INDIRECTION = re.compile(
    r"""(?m)^[ \t]*"""
    r"""(?P<key_quote>['"]?)"""
    r"""(?:runtime-mode|camunda\.process-test\.runtime-mode)(?P=key_quote)"""
    r"""[ \t]*:[ \t]*(?:![^\s#]+[ \t]+)?[&*][^\s#]+"""
)
YAML_DOUBLE_QUOTED_SCALAR = re.compile(
    r'"(?:\\(?:\r\n|\r|\n|[^\r\n])|[^"\\\r\n])*"'
)
YAML_DOUBLE_QUOTED_LINE_CONTINUATION = re.compile(
    r"(?<!\\)(?P<escaped_backslashes>(?:\\\\)*)\\(?:\r\n|\r|\n)[ \t]*"
)
YAML_UNICODE_ESCAPE = re.compile(
    r"(?<!\\)(?P<escaped_backslashes>(?:\\\\)*)\\"
    r"(?P<escape>x[0-9a-fA-F]{2}|u[0-9a-fA-F]{4}|U[0-9a-fA-F]{8})"
)


def _java_properties_logical_lines(content):
    logical_line = ""
    continuing = False
    for physical_line in re.split(r"\r\n|\n|\r", content):
        if continuing:
            physical_line = physical_line.lstrip(" \t\f")

        trailing_backslashes = len(physical_line) - len(physical_line.rstrip("\\"))
        continuing = trailing_backslashes % 2 == 1
        if continuing:
            physical_line = physical_line[:-1]

        logical_line += physical_line
        if not continuing:
            yield logical_line
            logical_line = ""

    if logical_line:
        yield logical_line


def _unescape_java_properties_unicode_escapes(content):
    return JAVA_PROPERTIES_UNICODE_ESCAPE.sub(
        lambda match: match.group("escaped_backslashes")
        + chr(int(match.group("codepoint"), 16)),
        content,
    )


def _unescape_yaml_double_quoted_unicode_escapes(content):
    def unescape_scalar(match):
        def unescape_escape(escape):
            codepoint = int(escape.group("escape")[1:], 16)
            if codepoint > 0x10FFFF or 0xD800 <= codepoint <= 0xDFFF:
                return escape.group(0)
            return escape.group("escaped_backslashes") + chr(codepoint)

        value = match.group(0)[1:-1]
        value = YAML_DOUBLE_QUOTED_LINE_CONTINUATION.sub(
            lambda continuation: continuation.group("escaped_backslashes"), value
        )
        value = YAML_UNICODE_ESCAPE.sub(unescape_escape, value)
        return f'"{value}"'

    return YAML_DOUBLE_QUOTED_SCALAR.sub(unescape_scalar, content)


def _contains_remote_runtime_configuration(content):
    yaml_content = _unescape_yaml_double_quoted_unicode_escapes(content)
    yaml_content = yaml_content.replace("\r\n", "\n").replace("\r", "\n")
    return (
        any(
            JAVA_PROPERTIES_REMOTE_RUNTIME.search(
                _unescape_java_properties_unicode_escapes(line)
            )
            is not None
            for line in _java_properties_logical_lines(content)
        )
        or YAML_REMOTE_RUNTIME.search(yaml_content) is not None
        or YAML_REMOTE_RUNTIME_INDIRECTION.search(yaml_content) is not None
    )


class RemoteEngineTestMigrationTest(unittest.TestCase):
    def test_skill_points_to_the_reference(self):
        skill = " ".join(SKILL.read_text(encoding="utf-8").split())
        self.assertIn(
            "The skill follows `references/test-migration.md` for every Test Inventory row that it migrates.",
            skill,
        )

    def test_reference_declares_obligations_immediately_after_title(self):
        reference = " ".join(REFERENCE.read_text(encoding="utf-8").split())
        declaration = (
            'Every instruction in this reference is mandatory. "Never" means MUST NOT. '
            "A preference is marked (SHOULD) and an option is marked (MAY)."
        )
        self.assertTrue(reference.startswith(f"# Test Migration {declaration} "))
        self.assertEqual(1, reference.count(declaration))

    def test_test_kind_table_excludes_engine_rest_stubs(self):
        reference = REFERENCE.read_text(encoding="utf-8")
        test_kinds = reference.split("## Test kinds", 1)[1].split(
            "## Remote-engine test migration", 1
        )[0]
        out_of_scope_row = next(
            line
            for line in test_kinds.splitlines()
            if line.startswith("| 8 | out of scope |")
        )

        self.assertIn("WireMock or another Engine REST stub", out_of_scope_row)

    def test_camunda_8_package_patterns_use_closed_code_spans(self):
        reference = REFERENCE.read_text(encoding="utf-8")
        test_kinds = reference.split("## Test kinds", 1)[1].split(
            "## Remote-engine test migration", 1
        )[0]
        camunda_8_row = next(
            line
            for line in test_kinds.splitlines()
            if line.startswith("| 1 | out of scope (Camunda 8) |")
        )

        self.assertIn(
            "Zeebe Process Test (`io.camunda.zeebe.process.test.*`)", camunda_8_row
        )
        self.assertIn("CPT (`io.camunda.process.test.*`)", camunda_8_row)

    def test_shared_engine_test_requires_explicit_opt_in(self):
        shared_test = SHARED_SOURCE.read_text(encoding="utf-8")
        readme = (FIXTURE / "README.md").read_text(encoding="utf-8")

        self.assertIn(
            '@EnabledIfSystemProperty(named = "shared-engine.test.enabled", matches = "true")',
            shared_test,
        )
        self.assertIn("-Dshared-engine.test.enabled=true", readme)
        self.assertIn("starts the deployed `payment` process", readme)
        self.assertIn("completion with `charged=true`", readme)
        self.assertIn("override `test.engine-rest-url`", readme.lower())
        self.assertIn("-Dtest.engine-rest-url=", readme)

    def test_shared_engine_fixture_has_build_and_runnable_command(self):
        shared_pom = ET.parse(FIXTURE / "shared-engine/c7-source/pom.xml").getroot()
        namespace = "{http://maven.apache.org/POM/4.0.0}"
        dependencies = {
            (
                dependency.findtext(f"{namespace}groupId"),
                dependency.findtext(f"{namespace}artifactId"),
                dependency.findtext(f"{namespace}scope") or "compile",
            )
            for dependency in shared_pom.findall(f".//{namespace}dependency")
        }
        readme = (FIXTURE / "README.md").read_text(encoding="utf-8")

        self.assertIn(
            (
                "org.camunda.bpm.springboot",
                "camunda-bpm-spring-boot-starter-external-task-client",
                "test",
            ),
            dependencies,
        )
        self.assertIn(("org.springframework.boot", "spring-boot-starter-test", "test"), dependencies)
        self.assertIn(("org.springframework.boot", "spring-boot-starter-web", "test"), dependencies)
        self.assertIn("shared-engine/c7-source/pom.xml test", readme)
        self.assertIn("-Dshared-engine.test.enabled=true", readme)
        self.assertIn(
            "-Dtest.engine-rest-url=http://shared-engine.example.invalid/engine-rest",
            readme,
        )

    def test_reference_classifies_remote_engine_tests_and_boundaries(self):
        reference = " ".join(REFERENCE.read_text(encoding="utf-8").lower().split())
        for term in (
            "restassured",
            "resttemplate",
            "testresttemplate",
            "webclient",
            "generated openapi clients",
            "externaltaskclient",
            "@externaltasksubscription",
            "camunda/camunda-bpm-platform",
            "docker compose",
            "random_port",
            "report only",
            "wiremock",
            "load, performance, or end-to-end ui test",
        ):
            with self.subTest(term=term):
                self.assertIn(term, reference)

    def test_remote_engine_kind_excludes_embedded_engine_rest_from_process_test(self):
        reference = " ".join(REFERENCE.read_text(encoding="utf-8").split())
        self.assertIn(
            "The skill classifies an embedded Engine REST call from a "
            "`@SpringBootTest` as a remote-engine test, not a process test.",
            reference,
        )

    def test_scope_boundaries_precede_client_shape_rules(self):
        classification = REFERENCE.read_text(encoding="utf-8").split("## Scope and classification", 1)[1].split(
            "## Runtime and build changes", 1
        )[0]
        self.assertIn(
            "Apply the rows below from top to bottom. Stop at the first matching row.",
            classification,
        )
        self.assertIn(
            "A test that the table classifies as manual migration, manual redesign, or out of "
            "scope keeps that classification and handling.",
            " ".join(classification.split()),
        )
        first_client_shape = classification.index("| Engine REST calls through")
        for boundary in (
            "| Load, performance, or end-to-end UI test against Camunda 7",
            "| Unit test of an external-task handler that starts no engine",
            "| WireMock or another Engine REST stub",
        ):
            with self.subTest(boundary=boundary):
                self.assertLess(classification.index(boundary), first_client_shape)

    def test_camunda_8_8_gate_precedes_in_scope_client_shapes(self):
        reference = REFERENCE.read_text(encoding="utf-8")
        classification = reference.split("## Scope and classification", 1)[1].split(
            "## Runtime and build changes", 1
        )[0]
        version_gate = (
            "Apply the [Camunda 8.8 target](#camunda-88-target) table before any "
            "client-shape row."
        )
        self.assertIn(version_gate, classification)
        self.assertLess(
            classification.index(version_gate),
            classification.index("| Load, performance, or end-to-end UI test against Camunda 7"),
        )
        camunda_88 = reference.split("## Camunda 8.8 target", 1)[1].split("\n## ", 1)[0]
        for handling in (
            "| Migrate | Report only |",
            "| Migrate to CPT | Report only |",
            "| Migrate (lower priority) | Report only |",
        ):
            with self.subTest(handling=handling):
                self.assertIn(handling, camunda_88)

    def test_shared_engine_definition_matches_report_only_boundary(self):
        reference = REFERENCE.read_text(encoding="utf-8")
        definition_start = reference.index("A **shared-engine test**")
        definition_end = reference.index("\n\n", definition_start)
        shared_engine_definition = " ".join(
            reference[definition_start:definition_end].lower().split()
        )
        handling_overrides = reference.split("## Handling overrides", 1)[1].split(
            "\n## ", 1
        )[0]
        shared_override_rule = next(
            row
            for row in handling_overrides.splitlines()
            if row.startswith("| A shared-engine test |")
        )
        shared_reason = (
            "CPT deletes all runtime data between tests, so the test needs a dedicated "
            "Camunda 8 runtime."
        )
        self.assertIn(shared_reason, handling_overrides)
        self.assertEqual(1, reference.count(shared_reason))
        self.assertIn(
            "When a test uses a shared engine, the skill records this exact reason in "
            "`MIGRATION_REPORT.md`:",
            handling_overrides,
        )
        self.assertIn("| Report only |", shared_override_rule)
        self.assertIn(
            "Where the target is Camunda 8.8, append `test migration needs "
            "Camunda 8.9 or later`",
            shared_override_rule,
        )
        for boundary in (
            "from any configuration source",
            "does not start",
            "neither local nor a test-owned container",
        ):
            with self.subTest(boundary=boundary):
                self.assertIn(boundary, shared_engine_definition)
        self.assertNotIn("environment variable", shared_engine_definition)
        classification = reference.split("## Scope and classification", 1)[1].split(
            "## Runtime and build changes", 1
        )[0]
        self.assertIn(
            "A shared-engine test keeps its [handling override](#handling-overrides).",
            classification,
        )

    def test_skill_classifies_test_engine_calls_before_http_topology(self):
        code_inventory = " ".join(
            SKILL.read_text(encoding="utf-8").split("#### Code Inventory", 1)[1].split(
                "#### Model Inventory", 1
            )[0].split()
        )
        self.assertLess(
            code_inventory.index("Classify each Camunda 7 test that drives a running engine"),
            code_inventory.index(
                "Search production code, application configuration, production build files, "
                "scripts, and deployment configuration"
            ),
        )
        self.assertIn(
            "Search production code, application configuration, production build files, "
            "scripts, and deployment configuration",
            code_inventory,
        )
        test_only_build_filter = (
            "The skill excludes dependencies declared only in test scope and plugin "
            "executions bound only to test phases from production-source evidence."
        )
        self.assertIn(test_only_build_filter, code_inventory)
        self.assertIn(
            "When any of these production sources contains a match, inventory its HTTP topology.",
            code_inventory,
        )
        self.assertIn(
            "Exclude test-only Engine REST calls and test-owned servers from the HTTP topology inventory and Question 7.",
            code_inventory,
        )

        question_7 = INTERVIEW_QUESTIONS.read_text(encoding="utf-8").split("## Question 7", 1)[1]
        self.assertIn("only when the production-source inventory identifies", question_7)
        self.assertIn(
            "Do not ask for decisions about test-only Engine REST calls.",
            question_7,
        )

        http_topology = " ".join(HTTP_TOPOLOGY.read_text(encoding="utf-8").split())
        self.assertIn(
            "`SKILL.md` Step 2 defines the production sources that trigger this procedure.",
            http_topology,
        )
        self.assertNotIn(test_only_build_filter, http_topology)

    def test_step_3_http_topology_gate_uses_production_sources(self):
        step_3_topology = SKILL.read_text(encoding="utf-8").split("15. **HTTP topology**", 1)[1].split(
            "16. **SLF4J providers**", 1
        )[0]
        self.assertIn("when the production-source inventory identifies", step_3_topology)

    def test_code_checklist_http_topology_gate_uses_production_sources(self):
        self.assertIn(
            "When the production-source inventory identifies a Spring web server",
            CHECKLIST.read_text(encoding="utf-8"),
        )

    def test_reference_maps_engine_rest_and_cpt_behaviors(self):
        reference = REFERENCE.read_text(encoding="utf-8")
        normalized_reference = " ".join(reference.lower().split())
        mapping = ENGINE_REST_PATTERN.read_text(encoding="utf-8")
        mapping_rows = [
            row.lower() for row in mapping.splitlines() if row.startswith("| `")
        ]
        self.assertIn(
            "40-test-assertions/60-remote-engine-tests/10-engine-rest-mapping.md",
            reference,
        )
        self.assertIn(
            "Test kind `remote-engine test` | "
            "`40-test-assertions/60-remote-engine-tests/10-engine-rest-mapping.md`",
            PATTERN_SOURCES.read_text(encoding="utf-8"),
        )
        for source, target in (
            ("/deployment/create", "@testdeployment"),
            ("/process-definition/key/{key}/start", "newcreateinstancecommand"),
            ("/message", "newcorrelatemessagecommand"),
            ("/task/{id}/complete", "completeusertask"),
            ("/task/{id}/claim", "newassignusertaskcommand"),
            ("/task/{id}/assignee", "newassignusertaskcommand"),
            ("/external-task/fetchandlock", "completejob"),
            ("/external-task/{id}/bpmnerror", "throwbpmnerrorfromjob"),
            ("/history/process-instance/{id}", "iscompleted"),
            ("/history/variable-instance", "hasvariable"),
            ("/incident?processinstanceid", "hasactiveincidents"),
        ):
            with self.subTest(source=source):
                matching_rows = [row for row in mapping_rows if source.lower() in row]
                self.assertEqual(1, len(matching_rows), f"Expected one mapping row for {source}")
                self.assertIn(target, matching_rows[0])
        self.assertIn(
            "cpt deletes all runtime data between tests, so the test needs a dedicated camunda 8 runtime.",
            normalized_reference,
        )

    def test_dependency_changes_keep_independent_rules_separate(self):
        dependency_rules = " ".join(
            REFERENCE.read_text(encoding="utf-8")
            .split("### Dependency changes", 1)[1]
            .split("### Recipe-assisted migration", 1)[0]
            .split()
        )

        self.assertIn(
            "The skill keeps dependencies with remaining production or test consumers.",
            dependency_rules,
        )
        self.assertIn(
            "The skill aligns AssertJ with the version required by CPT.",
            dependency_rules,
        )
        self.assertNotIn("consumers and aligns AssertJ", dependency_rules)

    def test_signal_mapping_preserves_request_fields_and_execution_scope(self):
        mapping = ENGINE_REST_PATTERN.read_text(encoding="utf-8")
        signal_rows = [
            row.lower()
            for row in mapping.splitlines()
            if row.startswith("| `POST /signal`")
        ]

        self.assertEqual(2, len(signal_rows))
        broadcast_row = next(
            row for row in signal_rows if "without `executionid`" in row
        )
        self.assertIn("newbroadcastsignalcommand", broadcast_row)
        self.assertIn("variables(vars)", broadcast_row)
        self.assertIn("tenantid(tenantid)", broadcast_row)
        self.assertIn("withouttenantid", broadcast_row)
        self.assertIn("no equivalent", broadcast_row)
        execution_row = next(
            row for row in signal_rows if "with `executionid`" in row
        )
        self.assertIn("manual redesign", execution_row)
        self.assertIn("all matching subscriptions", execution_row)

    def test_start_mapping_preserves_optional_business_key(self):
        mapping = ENGINE_REST_PATTERN.read_text(encoding="utf-8")
        start_row = next(
            row
            for row in mapping.splitlines()
            if row.startswith("| `POST /process-definition/key/{key}/start`")
        )

        self.assertIn(".businessId(businessKey)", start_row)
        self.assertIn("when the request includes", start_row.lower())
        self.assertIn("8.9", start_row)
        for request_field in (
            "caseInstanceId",
            "startInstructions",
            "skipCustomListeners",
            "skipIoMappings",
            "withVariablesInReturn",
        ):
            with self.subTest(request_field=request_field):
                self.assertIn(request_field.lower(), start_row.lower())
        self.assertIn("manual migration", start_row.lower())
        self.assertIn("create-with-result", start_row.lower())
        self.assertIn("map `startinstructions` explicitly", start_row.lower())
        self.assertIn(
            "no matching request options for `caseinstanceid`, "
            "`skipcustomlisteners`, or `skipiomappings`",
            start_row.lower(),
        )
        self.assertIn("only when this wait matches the test", start_row.lower())

    def test_bpmn_error_mapping_preserves_optional_error_message(self):
        mapping = ENGINE_REST_PATTERN.read_text(encoding="utf-8")
        bpmn_error_row = next(
            row
            for row in mapping.splitlines()
            if row.startswith("| `POST /external-task/{id}/bpmnError`")
        )

        self.assertIn(
            "throwBpmnErrorFromJob(type, code, errorMessage, vars)",
            bpmn_error_row,
        )
        self.assertIn("when supplied", bpmn_error_row.lower())
        self.assertIn("three-argument", bpmn_error_row.lower())
        self.assertIn("preserve", bpmn_error_row.lower())

    def test_message_mapping_preserves_supported_fields_and_marks_gaps(self):
        mapping = ENGINE_REST_PATTERN.read_text(encoding="utf-8")
        message_rows = [
            row
            for row in mapping.splitlines()
            if row.startswith("| `POST /message`")
        ]
        normalized_mapping = " ".join(mapping.lower().split())
        message_fields = normalized_mapping.split(
            "### message request fields", 1
        )[1]

        self.assertEqual(1, len(message_rows))
        self.assertIn("message request fields", message_rows[0].lower())
        for supported_mapping in (
            "`processvariables` | map to `.variables(vars)`",
            "`tenantid` | map to `.tenantid(tenantid)`",
            "`correlationkeys` | map to `.correlationkey(key)`",
        ):
            with self.subTest(supported_mapping=supported_mapping):
                self.assertIn(supported_mapping, message_fields)
        for unsupported_field in (
            "`businesskey`",
            "`localcorrelationkeys`",
            "`processinstanceid`",
            "`withouttenantid`",
            "`processvariableslocal`",
            "`processvariablestotriggeredscope`",
            "`all`",
            "`resultenabled`",
            "`variablesinresultenabled`",
        ):
            with self.subTest(unsupported_field=unsupported_field):
                self.assertIn(unsupported_field, message_fields)
        self.assertIn("manual migration", message_fields)
        self.assertIn("is not the c8 `correlationkey` or `businessid`", message_fields)
        self.assertIn("at most once per process", message_fields)

    def test_assignment_mapping_preserves_claim_reassignment_and_unassignment(self):
        mapping = ENGINE_REST_PATTERN.read_text(encoding="utf-8")
        assignment_rows = [
            row.lower()
            for row in mapping.splitlines()
            if row.startswith("| `POST /task/{id}/")
            and ("claim`" in row or "assignee`" in row)
        ]
        claim_row = next(row for row in assignment_rows if "/claim`" in row)
        assignee_row = next(row for row in assignment_rows if "/assignee`" in row)

        self.assertEqual(2, len(assignment_rows))
        self.assertIn(".allowoverride(false)", claim_row)
        self.assertIn(".allowoverride(true)", assignee_row)
        self.assertIn("null", assignee_row)
        self.assertIn("manual migration", assignee_row)

    def test_activity_history_mapping_preserves_requested_states_and_filters(self):
        mapping = ENGINE_REST_PATTERN.read_text(encoding="utf-8")
        activity_rows = [
            row.lower()
            for row in mapping.splitlines()
            if "/history/activity-instance" in row
        ]

        self.assertEqual(2, len(activity_rows))
        completed_row = next(
            row for row in activity_rows if "completed activity ids" in row
        )
        self.assertIn("hascompletedelements", completed_row)
        self.assertIn("order", completed_row)
        self.assertIn("canceled", completed_row)
        self.assertIn("terminated", completed_row)

        filtered_row = next(row for row in activity_rows if "unfinished" in row)
        for filter_name in ("canceled", "assignee", "time", "count"):
            with self.subTest(filter_name=filter_name):
                self.assertIn(filter_name, filtered_row)
        self.assertIn("newelementinstancesearchrequest", filtered_row)
        self.assertIn("manual", filtered_row)

    def test_job_execute_mapping_distinguishes_timer_and_non_timer_jobs(self):
        reference_text = REFERENCE.read_text(encoding="utf-8")
        rest_rows = ENGINE_REST_PATTERN.read_text(encoding="utf-8").splitlines()
        process_rows = JOB_PATTERN.read_text(encoding="utf-8").splitlines()
        timer_row = next(
            row
            for row in rest_rows
            if row.startswith("| `POST /job/{id}/execute` for a timer job")
        )
        non_timer_row = next(
            row
            for row in rest_rows
            if row.startswith("| `POST /job/{id}/execute` for a non-timer job")
        )
        process_timer_row = next(
            row
            for row in process_rows
            if row.startswith(
                "| `execute(job())` or `managementService.executeJob(id)` for a timer"
            )
        )
        waiting_timer_rules = reference_text.split("## Waiting, timers, and variables", 1)[1].split(
            "\n## ", 1
        )[0]

        self.assertIn("processTestContext.increaseTime(duration)", timer_row)
        self.assertIn("[Engine REST mapping](#engine-rest-mapping)", waiting_timer_rules)
        for timer_rule in (timer_row, process_timer_row):
            normalized_rule = " ".join(timer_rule.split()).lower()
            with self.subTest(timer_rule=timer_rule[:80]):
                self.assertIn("timer catch event", normalized_rule)
                self.assertIn("boundary timer", normalized_rule)
                self.assertIn("attached activity", normalized_rule)
                self.assertIn(
                    "does not expose a boundary timer as an active element",
                    normalized_rule,
                )
        self.assertIn("do not advance time", non_timer_row.lower())
        self.assertIn("job type", non_timer_row)

    def test_cpt_artifacts_and_remote_runtime_configuration_match_target(self):
        reference = REFERENCE.read_text(encoding="utf-8")
        spring_pattern = SPRING_PATTERN.read_text(encoding="utf-8")
        dependencies = DEPENDENCIES_PATTERN.read_text(encoding="utf-8")
        self.assertIn(
            "| Spring Boot 3.5.x with `camunda-spring-boot-3-starter` | "
            "`io.camunda:camunda-process-test-spring-boot-3` |",
            spring_pattern,
        )
        self.assertIn(
            "| Spring Boot 4.x with `camunda-spring-boot-starter` | "
            "`io.camunda:camunda-process-test-spring` |",
            spring_pattern,
        )
        runtime_changes = reference.split("## Runtime and build changes", 1)[1].split(
            "## Worker behavior", 1
        )[0]
        self.assertIn("10-general/dependencies.md", runtime_changes)
        runtime_configuration = " ".join(reference.split())
        self.assertIn(
            "camunda-process-test-java",
            dependencies,
        )
        self.assertIn(
            "| No explicit request for remote mode | Spring or plain Java test | Use the default runtime. Never configure remote mode. |",
            runtime_configuration,
        )
        self.assertIn(
            "| Explicit request for remote mode and a dedicated local Camunda 8 runtime | Spring test | Set Spring property `camunda.process-test.runtime-mode` to `remote` in `application.properties` or `application.yml` (MAY). |",
            runtime_configuration,
        )
        self.assertIn(
            "| Explicit request for remote mode and a dedicated local Camunda 8 runtime | Plain Java test | Add `src/test/resources/camunda-container-runtime.properties` with `runtimeMode=remote` (MAY). |",
            runtime_configuration,
        )
        checklist = CHECKLIST.read_text(encoding="utf-8")
        self.assertIn(
            "[Spring Process Test artifact selection](test-migration.md#harness-and-dependencies)",
            checklist,
        )

    def test_reference_declares_both_cpt_test_harness_annotations(self):
        runtime_configuration = REFERENCE.read_text(encoding="utf-8").split(
            "## Runtime and build changes", 1
        )[1].split("## Worker behavior", 1)[0]
        runtime_configuration = " ".join(runtime_configuration.split())
        self.assertIn(
            "Annotate each migrated JUnit test that uses plain Java with `@CamundaProcessTest` to register `CamundaProcessTestExtension`.",
            runtime_configuration,
        )
        self.assertIn(
            "Annotate each migrated Spring CPT test with `@CamundaSpringProcessTest` to start the Spring Process Test harness.",
            runtime_configuration,
        )
        self.assertIn(
            "The artifact dependency alone does not start the CPT runtime or inject the CPT client and context fields.",
            runtime_configuration,
        )

    def test_managed_runtime_rule_is_declared_once(self):
        runtime_changes = REFERENCE.read_text(encoding="utf-8").split(
            "## Runtime and build changes", 1
        )[1].split("## Worker behavior", 1)[0]
        self.assertEqual(
            1,
            runtime_changes.count("CPT-managed Testcontainers runtime"),
        )

    def test_c7_baseline_uses_engine_rest_and_a_real_external_task_worker(self):
        c7_test = (
            FIXTURE
            / "c7-source/src/test/java/org/camunda/example/payment/PaymentWorkerTest.java"
        ).read_text(encoding="utf-8")
        c7_worker = (
            FIXTURE
            / "c7-source/src/main/java/org/camunda/example/payment/PaymentWorker.java"
        ).read_text(encoding="utf-8")
        c7_pom = (FIXTURE / "c7-source/pom.xml").read_text(encoding="utf-8")

        for term in (
            "@Container",
            "camunda/camunda-bpm-platform:run-7.24.0",
            "/deployment/create",
            "/process-definition/key/payment/start",
            "/history/process-instance/",
            "await().atMost",
        ):
            with self.subTest(term=term):
                self.assertIn(term, c7_test)
        self.assertIn('@ExternalTaskSubscription("charge-payment")', c7_worker)
        self.assertIn("camunda-bpm-spring-boot-starter-external-task-client", c7_pom)
        self.assertIn("<artifactId>junit-jupiter</artifactId>", c7_pom)

    def test_cpt_test_runs_the_migrated_worker_and_preserves_parity(self):
        c8_test = (
            EXPECTED
            / "src/test/java/org/camunda/example/payment/PaymentWorkerTest.java"
        ).read_text(encoding="utf-8")
        c8_worker = (
            EXPECTED
            / "src/main/java/org/camunda/example/payment/PaymentWorker.java"
        ).read_text(encoding="utf-8")
        c8_bpmn = (EXPECTED / "src/test/resources/converted-c8-payment.bpmn").read_text(encoding="utf-8")
        c8_pom = (EXPECTED / "pom.xml").read_text(encoding="utf-8")

        self.assertIn("@CamundaSpringProcessTest", c8_test)
        self.assertIn('@TestDeployment(resources = "converted-c8-payment.bpmn")', c8_test)
        self.assertIn("newCreateInstanceCommand()", c8_test)
        self.assertIn('hasVariable("charged", true)', c8_test)
        self.assertIn('@JobWorker(type = "charge-payment")', c8_worker)
        self.assertIn('type="charge-payment"', c8_bpmn)
        self.assertIn("<artifactId>camunda-process-test-spring</artifactId>", c8_pom)
        self.assertNotIn("camunda-bpm-spring-boot-starter-external-task-client", c8_pom)
        self.assertNotIn("<groupId>org.testcontainers</groupId>", c8_pom)

    def test_mock_worker_guidance_configures_job_completion(self):
        reference = REFERENCE.read_text(encoding="utf-8")
        engine_rest = ENGINE_REST_PATTERN.read_text(encoding="utf-8")
        self.assertIn(
            "processTestContext.mockJobWorker(type).thenComplete(variables)",
            reference,
        )
        self.assertIn("mockJobWorker(type).thenComplete(vars)", engine_rest)

    def test_plain_java_workers_are_closed_after_each_test(self):
        worker_guidance = REFERENCE.read_text(encoding="utf-8").split(
            "## Worker behavior", 1
        )[1].split("\n## ", 1)[0]

        self.assertIn(
            "Without Spring, open the migrated worker in `@BeforeEach` with the "
            "injected `CamundaClient`.",
            worker_guidance,
        )
        self.assertIn("Store each returned `JobWorker` in a field.", worker_guidance)
        self.assertIn("Close each stored `JobWorker` in `@AfterEach`.", worker_guidance)

    def test_direct_user_task_completion_sends_variables(self):
        row = next(
            line
            for line in ENGINE_REST_PATTERN.read_text(encoding="utf-8").splitlines()
            if line.startswith(
                "| `GET /task?processInstanceId=...` then `POST /task/{id}/complete`"
            )
        )

        self.assertIn(
            "client.newCompleteUserTaskCommand(userTaskKey).variables(vars).send().join()",
            row,
        )

    def test_unavailable_baseline_keeps_shared_engine_verdict_manual(self):
        baseline_reporting = REFERENCE.read_text(encoding="utf-8").split(
            "## Baseline and parity reporting", 1
        )[1].split("\n## ", 1)[0]

        self.assertIn(
            "| In-scope test whose baseline did not run | `not run` |",
            baseline_reporting,
        )
        self.assertIn(
            "| Shared-engine test, whether its baseline ran or not | `manual` |",
            baseline_reporting,
        )

    def test_shared_engine_case_is_manual_and_runs_a_process_without_starting_an_engine(self):
        shared_test = SHARED_SOURCE.read_text(encoding="utf-8")
        shared_properties = SHARED_PROPERTIES.read_text(encoding="utf-8")
        report = SHARED_REPORT.read_text(encoding="utf-8")
        self.assertIn("| Camunda 7 test | Verdict | Reason |", report)
        report_row = next(
            line
            for line in report.splitlines()
            if line.startswith("| `SharedEnginePaymentTest`")
        )

        self.assertIn('@Value("${test.engine-rest-url}")', shared_test)
        self.assertIn("/process-definition/key/payment/start", shared_test)
        self.assertIn("ExternalTaskClient.create()", shared_test)
        self.assertIn('subscribe("charge-payment")', shared_test)
        self.assertIn("/history/process-instance/", shared_test)
        self.assertIn('isEqualTo("COMPLETED")', shared_test)
        self.assertIn('"charged"', shared_test)
        self.assertIn("externalTaskClient.stop()", shared_test)
        self.assertIn("shared-c7.example.invalid/engine-rest", shared_properties)
        self.assertNotIn("@Testcontainers", shared_test)
        self.assertNotIn("GenericContainer", shared_test)
        self.assertIn('@ActiveProfiles("test")', shared_test)
        self.assertEqual(
            report_row,
            "| `SharedEnginePaymentTest` | `manual` | CPT deletes all runtime data between tests, so the test needs a dedicated Camunda 8 runtime. |",
        )

    def test_shared_engine_worker_is_scoped_to_its_unique_business_key(self):
        shared_test = SHARED_SOURCE.read_text(encoding="utf-8")

        self.assertIn("UUID.randomUUID().toString()", shared_test)
        self.assertRegex(
            shared_test,
            r'subscribe\("charge-payment"\)\s*\.businessKey\(businessKey\)',
        )
        self.assertRegex(shared_test, r'Map\.of\(\s*"businessKey",\s*businessKey')

    def test_expected_project_does_not_configure_remote_runtime(self):
        for path in EXPECTED.rglob("*"):
            if not path.is_file() or "target" in path.relative_to(EXPECTED).parts:
                continue
            content = path.read_text(encoding="utf-8")
            self.assertFalse(_contains_remote_runtime_configuration(content), path)
            self.assertNotIn("camunda.bpm.client.base-url", content)
            self.assertNotIn("camunda/camunda-bpm-platform", content)
            self.assertNotIn("/engine-rest", content)

    def test_fixture_path_reads_use_explicit_utf8_encoding(self):
        source = Path(__file__).read_text(encoding="utf-8")
        tree = ast.parse(source)
        missing_utf8 = [
            call.lineno
            for call in ast.walk(tree)
            if isinstance(call, ast.Call)
            and isinstance(call.func, ast.Attribute)
            and call.func.attr == "read_text"
            and not any(
                keyword.arg == "encoding"
                and isinstance(keyword.value, ast.Constant)
                and keyword.value.value == "utf-8"
                for keyword in call.keywords
            )
        ]

        self.assertEqual([], missing_utf8)

    def test_remote_runtime_guard_detects_java_properties_separators(self):
        for key in ("runtimeMode", "camunda.process-test.runtime-mode"):
            for separator in ("=", " = ", ":", " : ", " ", "\t=\t", "\f:\f", "\t", "\f"):
                with self.subTest(key=key, separator=separator):
                    self.assertTrue(
                        _contains_remote_runtime_configuration(
                            f"{key}{separator}remote"
                        )
                    )

    def test_remote_runtime_guard_detects_java_properties_unicode_escapes(self):
        for setting in (
            r"runtimeMode=\u0072emote",
            r"camunda.process-test.runtime-mode=\u0072emote",
            r"runtime\u004dode=remote",
        ):
            with self.subTest(setting=setting):
                self.assertTrue(_contains_remote_runtime_configuration(setting))

        for setting in (
            r"runtimeMode=\\u0072emote",
            r"runtimeMode=\\\u0072emote",
            r"runtimeMode=\\\\\u0072emote",
            r"runtime\u004dode=\\\u0072emote",
        ):
            with self.subTest(setting=setting):
                self.assertFalse(_contains_remote_runtime_configuration(setting))

    def test_remote_runtime_guard_detects_java_properties_continuations(self):
        for setting in (
            "runtimeMode=\\\n    remote",
            "runtimeMode=re\\\n  mote",
            "camunda.process-test.runtime-mode:\\\r\n  remote",
            "runtimeMode=\\\n  \\\n  remote",
        ):
            with self.subTest(setting=setting):
                self.assertTrue(_contains_remote_runtime_configuration(setting))

        for setting in (
            "runtimeMode=\\\\\n  remote",
            "runtimeMode=\\ \n  remote",
        ):
            with self.subTest(setting=setting):
                self.assertFalse(_contains_remote_runtime_configuration(setting))

    def test_remote_runtime_guard_detects_yaml_anchors_and_aliases(self):
        for setting in (
            "runtime-mode: &mode remote",
            'camunda.process-test.runtime-mode: &mode "remote"',
            "runtime-mode: &mode.name remote",
            "runtime-mode: &mode\n  remote",
            "mode.name: &mode.name remote\nruntime-mode: *mode.name",
            "mode: &mode remote\nruntime-mode: *mode",
            "runtime-mode: *mode",
        ):
            with self.subTest(setting=setting):
                self.assertTrue(_contains_remote_runtime_configuration(setting))

    def test_remote_runtime_guard_detects_yaml_unicode_escapes(self):
        for setting in (
            r'camunda.process-test.runtime-mode: "\u0072emote"',
            r'camunda.process-test.runtime-mode: "\x72emote"',
            r'camunda.process-test.runtime-mode: "\U00000072emote"',
            r'"runtime-\u006dode": "remote"',
        ):
            with self.subTest(setting=setting):
                self.assertTrue(_contains_remote_runtime_configuration(setting))

        for setting in (
            r"runtime-mode: '\u0072emote'",
            r'runtime-mode: "\\u0072emote"',
            r'runtime-mode: "\\\u0072emote"',
            r"runtime-mode: \u0072emote",
            r'runtime-mode: "\u0072emote-ish"',
        ):
            with self.subTest(setting=setting):
                self.assertFalse(_contains_remote_runtime_configuration(setting))

    def test_remote_runtime_guard_detects_yaml_escaped_line_continuations(self):
        for line_break in ("\n", "\r\n", "\r"):
            nested_key = line_break.join(
                ("camunda:", "  process-test:", "    runtime-mode:")
            )
            for first_line in (r"\u0072e", "re"):
                setting = (
                    nested_key
                    + ' "'
                    + first_line
                    + "\\"
                    + line_break
                    + '      mote"'
                )
                with self.subTest(
                    line_break=repr(line_break), first_line=first_line
                ):
                    self.assertTrue(
                        _contains_remote_runtime_configuration(setting)
                    )

        for setting in (
            'runtime-mode: "re\\\\\n  mote"',
            'runtime-mode: "re\n  mote"',
        ):
            with self.subTest(setting=setting):
                self.assertFalse(_contains_remote_runtime_configuration(setting))

    def test_remote_runtime_guard_detects_yaml_values_and_ignores_non_config(self):
        for setting in (
            "runtime-mode: remote",
            "runtime-mode:   remote",
            'runtime-mode: "remote"',
            "runtime-mode: 'remote'",
            "runtime-mode: remote # YAML comment",
            "camunda.process-test.runtime-mode: remote",
            'camunda.process-test.runtime-mode: "remote"',
            "  camunda.process-test.runtime-mode : 'remote' # Spring YAML comment",
        ):
            with self.subTest(setting=setting):
                self.assertTrue(_contains_remote_runtime_configuration(setting))

        for setting in (
            "# runtimeMode=remote",
            "! runtimeMode=remote",
            "runtimeMode=local",
            "runtimeMode=remote-ish",
            "runtimeMode=remote # trailing text is part of a Java property value",
            "# runtime-mode: remote",
            'runtime-mode: "remote\'',
            "camunda.process-test.runtime-mode: local",
            "camunda.process-test.runtime-mode: remote-ish",
            "# camunda.process-test.runtime-mode: \"remote\"",
            "camunda.process-test.runtime-mode: \"remote'",
        ):
            with self.subTest(setting=setting):
                self.assertFalse(_contains_remote_runtime_configuration(setting))

    def test_readme_documents_baseline_and_cpt_test_commands(self):
        readme = (FIXTURE / "README.md").read_text(encoding="utf-8")
        self.assertIn("c7-source/pom.xml test", readme)
        self.assertIn("expected-c8/pom.xml test", readme)
        self.assertIn("record the baseline as `not run`", readme)


if __name__ == "__main__":
    unittest.main()
