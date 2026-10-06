from pathlib import Path
import re
import unittest


REPO_ROOT = Path(__file__).resolve().parents[3]
FIXTURE = REPO_ROOT / "agentic-migration-skills/fixtures/remote-engine-tests"
SKILL = REPO_ROOT / "agentic-migration-skills/skills/migrate-c7-to-c8-code/SKILL.md"
REFERENCE = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/test-migration.md"
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
YAML_REMOTE_RUNTIME = re.compile(
    r"""(?m)^[ \t]*(?:runtime-mode|camunda\.process-test\.runtime-mode)[ \t]*:[ \t]*"""
    r"""(?:(['"])remote\1|remote)[ \t]*(?:#.*)?\r?$"""
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


def _contains_remote_runtime_configuration(content):
    return (
        any(
            JAVA_PROPERTIES_REMOTE_RUNTIME.search(line) is not None
            for line in _java_properties_logical_lines(content)
        )
        or YAML_REMOTE_RUNTIME.search(content) is not None
    )


class RemoteEngineTestMigrationTest(unittest.TestCase):
    def test_skill_points_to_the_reference(self):
        skill = SKILL.read_text()
        self.assertIn(
            "For tests that drive a running Camunda 7 engine, follow `references/test-migration.md`.",
            skill,
        )

    def test_reference_declares_obligations_immediately_after_title(self):
        reference = REFERENCE.read_text()
        declaration = (
            'Every instruction is mandatory. "Never" means MUST NOT. '
            "A preference is marked (SHOULD) and an option is marked (MAY)."
        )
        self.assertTrue(
            reference.startswith(f"# Camunda 7 Test Inventory\n\n{declaration}\n\n")
        )
        self.assertEqual(1, reference.count(declaration))

    def test_test_kind_table_excludes_engine_rest_stubs(self):
        reference = REFERENCE.read_text(encoding="utf-8")
        test_kinds = reference.split("## Test kinds", 1)[1].split(
            "## Remote-engine test migration", 1
        )[0]
        out_of_scope_row = next(
            line
            for line in test_kinds.splitlines()
            if line.startswith("| out of scope |")
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
            if line.startswith("| out of scope (Camunda 8) |")
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
        reference = " ".join(REFERENCE.read_text().lower().split())
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
        reference = " ".join(REFERENCE.read_text().split())
        self.assertIn(
            "The skill classifies an embedded Engine REST call from a "
            "`@SpringBootTest` as a remote-engine test, not a process test.",
            reference,
        )

    def test_scope_boundaries_precede_client_shape_rules(self):
        classification = REFERENCE.read_text().split("## Scope and classification", 1)[1].split(
            "## Runtime and build changes", 1
        )[0]
        self.assertIn(
            "Apply the rows from top to bottom. Stop at the first matching row.",
            classification,
        )
        first_client_shape = classification.index("| Engine REST calls through")
        for boundary in (
            "| Test is already classified as manual migration | Report only | "
            "Preserve the existing manual migration verdict. |",
            "| Test is already classified as manual redesign | Report only | "
            "Preserve the existing manual redesign verdict. |",
            "| Test calls an engine that it does not start",
            "| Unit test of an external-task handler that starts no engine",
            "| WireMock or another Engine REST stub",
            "| Load, performance, or end-to-end UI test against Camunda 7",
        ):
            with self.subTest(boundary=boundary):
                self.assertLess(classification.index(boundary), first_client_shape)

    def test_camunda_8_8_gate_precedes_in_scope_client_shapes(self):
        classification = REFERENCE.read_text().split("## Scope and classification", 1)[1].split(
            "## Runtime and build changes", 1
        )[0]
        version_gate = (
            "| Target is Camunda 8.8 and the test would otherwise be in scope | "
            "Report only | Record `test migration needs Camunda 8.9 or later` in "
            "`MIGRATION_REPORT.md`. |"
        )
        self.assertIn(version_gate, classification)
        for boundary in (
            "| Load, performance, or end-to-end UI test against Camunda 7",
            "| Unit test of an external-task handler that starts no engine",
            "| WireMock or another Engine REST stub",
            "| Test calls an engine that it does not start",
            "| Test is already classified as manual migration | Report only | "
            "Preserve the existing manual migration verdict. |",
            "| Test is already classified as manual redesign | Report only | "
            "Preserve the existing manual redesign verdict. |",
        ):
            with self.subTest(boundary=boundary):
                self.assertLess(
                    classification.index(boundary), classification.index(version_gate)
                )
        self.assertLess(
            classification.index(version_gate),
            classification.index("| Engine REST calls through"),
        )

    def test_shared_engine_definition_matches_report_only_boundary(self):
        reference = REFERENCE.read_text()
        definition_start = reference.index("A shared-engine test calls")
        definition_end = reference.index("\n\n", definition_start)
        shared_engine_definition = reference[definition_start:definition_end]
        classification = reference.split("## Scope and classification", 1)[1].split(
            "## Runtime and build changes", 1
        )[0]
        report_only_rule = next(
            row for row in classification.splitlines() if "| Report only |" in row
        )

        for boundary in ("does not start", "neither local nor a test-owned container"):
            with self.subTest(boundary=boundary):
                self.assertIn(boundary, shared_engine_definition)
                self.assertIn(boundary, report_only_rule)

    def test_skill_classifies_test_engine_calls_before_http_topology(self):
        code_inventory = " ".join(
            SKILL.read_text().split("#### Code Inventory", 1)[1].split(
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
        self.assertIn(
            "When any of these production sources contains a match, inventory its HTTP topology.",
            code_inventory,
        )
        self.assertIn(
            "Exclude test-only Engine REST calls and test-owned servers from the HTTP topology inventory and Question 7.",
            code_inventory,
        )

        question_7 = INTERVIEW_QUESTIONS.read_text().split("## Question 7", 1)[1]
        self.assertIn("only when the production-source inventory identifies", question_7)
        self.assertIn(
            "Do not ask for decisions about test-only Engine REST calls.",
            question_7,
        )

        http_topology = " ".join(HTTP_TOPOLOGY.read_text().split())
        self.assertIn(
            "Classify tests that drive a Camunda 7 engine with `test-migration.md` before building this inventory.",
            http_topology,
        )
        self.assertIn(
            "Exclude test-only Engine REST clients and test-owned servers from the production topology.",
            http_topology,
        )

    def test_step_3_http_topology_gate_uses_production_sources(self):
        step_3_topology = SKILL.read_text().split("15. **HTTP topology**", 1)[1].split(
            "16. **SLF4J providers**", 1
        )[0]
        self.assertIn("when the production-source inventory identifies", step_3_topology)

    def test_code_checklist_http_topology_gate_uses_production_sources(self):
        self.assertIn(
            "When the production-source inventory identifies a Spring web server",
            CHECKLIST.read_text(),
        )

    def test_reference_maps_engine_rest_and_cpt_behaviors(self):
        reference = REFERENCE.read_text(encoding="utf-8")
        normalized_reference = " ".join(reference.lower().split())
        mapping = reference.split("## Engine REST mapping", 1)[1].split("\n## ", 1)[0]
        mapping_rows = [
            row.lower() for row in mapping.splitlines() if row.startswith("| `")
        ]
        for source, target in (
            ("/deployment/create", "@testdeployment"),
            ("/process-definition/key/{key}/start", "newcreateinstancecommand"),
            ("/message", "newcorrelatemessagecommand"),
            ("/signal", "newbroadcastsignalcommand"),
            ("/task/{id}/complete", "completeusertask"),
            ("/task/{id}/claim", "newassignusertaskcommand"),
            ("/external-task/fetchandlock", "completejob"),
            ("/external-task/{id}/bpmnerror", "throwbpmnerrorfromjob"),
            ("/history/process-instance/{id}", "iscompleted"),
            ("/history/activity-instance", "hascompletedelements"),
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

    def test_job_execute_mapping_distinguishes_timer_and_non_timer_jobs(self):
        rows = REFERENCE.read_text().splitlines()
        timer_row = next(
            row
            for row in rows
            if row.startswith("| `POST /job/{id}/execute` for a timer job")
        )
        non_timer_row = next(
            row
            for row in rows
            if row.startswith("| `POST /job/{id}/execute` for a non-timer job")
        )

        self.assertIn("processTestContext.increaseTime(duration)", timer_row)
        self.assertIn("timer element is active", timer_row)
        self.assertIn("Do not advance time", non_timer_row)
        self.assertIn("job type", non_timer_row)

    def test_cpt_artifacts_and_remote_runtime_configuration_match_target(self):
        reference = REFERENCE.read_text()
        self.assertIn(
            "| Spring Boot 3.5.x | `io.camunda:camunda-process-test-spring-boot-3` |",
            reference,
        )
        self.assertIn(
            "| Spring Boot 4.x | `io.camunda:camunda-process-test-spring` |",
            reference,
        )
        runtime_configuration = " ".join(reference.split())
        self.assertIn(
            "Add `io.camunda:camunda-process-test-java` in test scope for non-Spring tests.",
            runtime_configuration,
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
        checklist = CHECKLIST.read_text()
        self.assertIn(
            "[Spring Process Test artifact selection](test-migration.md#runtime-and-build-changes)",
            checklist,
        )

    def test_reference_declares_both_cpt_test_harness_annotations(self):
        runtime_configuration = REFERENCE.read_text().split(
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
        runtime_changes = REFERENCE.read_text().split(
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
        ).read_text()
        c7_worker = (
            FIXTURE
            / "c7-source/src/main/java/org/camunda/example/payment/PaymentWorker.java"
        ).read_text()
        c7_pom = (FIXTURE / "c7-source/pom.xml").read_text()

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
        ).read_text()
        c8_worker = (
            EXPECTED
            / "src/main/java/org/camunda/example/payment/PaymentWorker.java"
        ).read_text()
        c8_bpmn = (EXPECTED / "src/test/resources/converted-c8-payment.bpmn").read_text()
        c8_pom = (EXPECTED / "pom.xml").read_text()

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
        reference = REFERENCE.read_text()
        self.assertIn(
            "processTestContext.mockJobWorker(type).thenComplete(variables)",
            reference,
        )
        self.assertIn("mockJobWorker(type).thenComplete(vars)", reference)

    def test_direct_user_task_completion_sends_variables(self):
        row = next(
            line
            for line in REFERENCE.read_text().splitlines()
            if line.startswith(
                "| `GET /task?processInstanceId=...` then `POST /task/{id}/complete`"
            )
        )

        self.assertIn(
            "client.newCompleteUserTaskCommand(userTaskKey).variables(vars).send().join()",
            row,
        )

    def test_unavailable_baseline_keeps_shared_engine_verdict_manual(self):
        baseline_reporting = REFERENCE.read_text().split(
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

    def test_remote_runtime_guard_detects_java_properties_separators(self):
        for key in ("runtimeMode", "camunda.process-test.runtime-mode"):
            for separator in ("=", " = ", ":", " : ", " ", "\t=\t", "\f:\f", "\t", "\f"):
                with self.subTest(key=key, separator=separator):
                    self.assertTrue(
                        _contains_remote_runtime_configuration(
                            f"{key}{separator}remote"
                        )
                    )

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
        readme = (FIXTURE / "README.md").read_text()
        self.assertIn("c7-source/pom.xml test", readme)
        self.assertIn("expected-c8/pom.xml test", readme)
        self.assertIn("record the baseline as `not run`", readme)


if __name__ == "__main__":
    unittest.main()
