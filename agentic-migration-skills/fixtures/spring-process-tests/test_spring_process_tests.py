from pathlib import Path
import subprocess
import unittest
import xml.etree.ElementTree as ET


FIXTURE = Path(__file__).resolve().parent
SKILL_ROOT = FIXTURE.parents[1] / "skills/migrate-c7-to-c8-code"
SKILL = SKILL_ROOT / "SKILL.md"
REFERENCE = SKILL_ROOT / "references/test-migration.md"
CODE_CHECKLIST = SKILL_ROOT / "references/code-transform-checklist.md"
PACKAGE_README = SKILL_ROOT.parents[1] / "README.md"
EXPECTED_POM = FIXTURE / "expected-c8/pom.xml"
SOURCE_TEST = (
    FIXTURE
    / "c7-source/src/test/java/org/camunda/bpm/example/springprocess/SpringProcessTest.java"
)
SOURCE_CONTROLLER = (
    FIXTURE
    / "c7-source/src/main/java/org/camunda/bpm/example/springprocess/api/OrderController.java"
)
EXPECTED_TEST = (
    FIXTURE
    / "expected-c8/src/test/java/org/camunda/bpm/example/springprocess/SpringProcessTest.java"
)
EXPECTED_CONTROLLER = (
    FIXTURE
    / "expected-c8/src/main/java/org/camunda/bpm/example/springprocess/api/OrderController.java"
)
EXPECTED_WORKERS = (
    FIXTURE
    / "expected-c8/src/main/java/org/camunda/bpm/example/springprocess/worker/OrderWorkers.java"
)
TEST_APPLICATION = (
    FIXTURE
    / "expected-c8/src/test/java/org/camunda/bpm/example/springprocesstest/TestProcessApplication.java"
)
EXPECTED_MANUAL_REPORT = (
    FIXTURE / "manual-without-bootstrap/expected-c8/MIGRATION_REPORT.md"
)
EXPECTED_STANDALONE_REPORT = (
    FIXTURE / "standalone-task-only/expected-c8/MIGRATION_REPORT.md"
)
MANUAL_SOURCE = FIXTURE / "manual-without-bootstrap/c7-source"
MANUAL_POM = MANUAL_SOURCE / "pom.xml"
MANUAL_SOURCE_TEST = (
    FIXTURE
    / "manual-without-bootstrap/c7-source/src/test/java/org/camunda/bpm/example/manual/ManualSpringProcessTest.java"
)
MANUAL_SOURCE_CONTEXT = (
    FIXTURE / "manual-without-bootstrap/c7-source/src/test/resources/spring-engine-context.xml"
)
MANUAL_PROCESS = (
    FIXTURE / "manual-without-bootstrap/c7-source/src/test/resources/manual-process.bpmn"
)
MANUAL_WORKER = (
    FIXTURE
    / "manual-without-bootstrap/c7-source/src/main/java/org/camunda/bpm/example/manual/ManualProcessWorker.java"
)
STANDALONE_TASK_TEST = (
    FIXTURE
    / "standalone-task-only/c7-source/src/test/java/org/camunda/bpm/example/standalone/StandaloneTaskTest.java"
)
STANDALONE_TASK_REPORT = (
    FIXTURE / "standalone-task-only/expected-c8/MIGRATION_REPORT.md"
)
EXPECTED_MODELS = FIXTURE / "expected-c8/src/main/resources/processes"
SOURCE_APPLICATION = (
    FIXTURE
    / "c7-source/src/main/java/org/camunda/bpm/example/springprocess/SpringProcessApplication.java"
)
EXPECTED_APPLICATION = (
    FIXTURE
    / "expected-c8/src/main/java/org/camunda/bpm/example/springprocess/SpringProcessApplication.java"
)


TEST_KINDS = (
    "out of scope (Camunda 8)",
    "manual redesign",
    "manual migration",
    "scenario test",
    "remote-engine test",
    "decision test",
    "process test",
    "out of scope",
)


class ManualSpringFixtureTest(unittest.TestCase):
    def test_manual_without_bootstrap_runs_spring_process(self):
        completed = subprocess.run(
            ["mvn", "-B", "-ntp", "-f", str(MANUAL_POM), "test"],
            cwd=MANUAL_SOURCE,
            text=True,
            capture_output=True,
            check=False,
            timeout=300,
        )
        self.assertEqual(
            0,
            completed.returncode,
            completed.stdout + "\n" + completed.stderr,
        )
        self.assertIn("Tests run: 1", completed.stdout)

    def test_expected_reports_include_every_test_kind_count(self):
        for report_path, counted_kind in (
            (EXPECTED_MANUAL_REPORT, "process test"),
            (EXPECTED_STANDALONE_REPORT, "out of scope"),
        ):
            with self.subTest(report=report_path):
                report = report_path.read_text()
                count_section = report.split("### Test kind counts", 1)[1]
                counts = {}
                for line in count_section.splitlines():
                    if not line.startswith("|"):
                        continue
                    cells = [cell.strip() for cell in line.strip("|").split("|")]
                    if len(cells) == 2 and cells[0] in TEST_KINDS:
                        counts[cells[0]] = int(cells[1])

                expected_counts = {test_kind: 0 for test_kind in TEST_KINDS}
                expected_counts[counted_kind] = 1
                self.assertEqual(expected_counts, counts)


class SpringProcessTestFixtureTest(unittest.TestCase):
    def test_reference_covers_boot_variants_and_deployment_rules(self):
        reference = " ".join(REFERENCE.read_text().split())
        package_readme = PACKAGE_README.read_text()

        for required in (
            "camunda-process-test-spring",
            "camunda-process-test-spring-boot-3",
            "camunda-process-test-java",
            "@CamundaSpringProcessTest",
            "@CamundaProcessTest",
            "@ExtendWith(SpringExtension.class)",
            "@MockitoBean",
            "@TestDeployment",
            "CPT 8.9 or later",
            "camunda.client.worker.override.",
            "manual migration",
        ):
            with self.subTest(required=required):
                self.assertIn(required, reference, msg=f"Missing {required!r}")
        self.assertIn(
            "process-test migration to Camunda Process Test (CPT)", package_readme
        )

    def test_step_two_builds_test_inventory_before_model_inventory(self):
        skill = SKILL.read_text()
        checklist = CODE_CHECKLIST.read_text()

        code_inventory = skill.index("#### Code Inventory")
        test_inventory = skill.index("#### Test Inventory")
        model_inventory = skill.index("#### Model Inventory")
        self.assertLess(code_inventory, test_inventory)
        self.assertLess(test_inventory, model_inventory)

        inventory_section = skill[test_inventory:model_inventory]
        inventory_lines = [
            line.strip() for line in inventory_section.splitlines() if line.strip()
        ]
        self.assertEqual(
            inventory_lines,
            [
                "#### Test Inventory",
                "When the skill reaches Step 2, it follows `references/test-migration.md` for the test inventory procedure.",
            ],
        )

        summary_start = skill.index("#### Summary")
        summary_end = skill.index("#### Custom incident notifications", summary_start)
        summary = skill[summary_start:summary_end].lower()
        self.assertIn("test counts", summary)
        self.assertIn("report only", summary)
        self.assertIn("`references/test-migration.md`", checklist)
        self.assertNotIn(
            "| `@Test` + Camunda 7 test rules | Test code |",
            checklist,
        )
        self.assertIn(
            "When the skill migrates Camunda 7 decision tests or Spring process tests, it follows `references/test-migration.md` for their migration.",
            " ".join(skill.split()),
        )

    def test_inventory_reference_defines_kinds_modifiers_models_and_report(self):
        reference = " ".join(REFERENCE.read_text().split())

        for required in (
            "| Priority | Test kind | Detect by | Handling |",
            "every test method",
            "including tests marked out of scope",
            "process test",
            "decision test",
            "scenario test",
            "remote-engine test",
            "manual migration",
            "manual redesign",
            "out of scope (Camunda 8)",
            "A Kotlin or Groovy test uses Camunda 7 test APIs to run an engine-backed BPMN process or DMN decision.",
            "plain Java tests",
            "camunda.cfg.xml",
            "org.camunda.bpm.extension:camunda-bpm-junit5",
            "| Modifier | Detect by | Used by |",
            "mocks",
            "org.camunda.community.mockito.*",
            "coverage",
            "time",
            "Spring",
            "`@Deployment(resources = ...)`",
            "`src/test/resources`",
            "programmatic deployment",
            "@EnableProcessApplication",
            "Test ID",
            "<module path>:<fully qualified concrete test class name>#<method>",
            "| Test ID | File | Test kind | Signals | Models | Handling | Notes |",
            "Camunda 8.9 or later",
            "test migration needs Camunda 8.9 or later",
            "When the target version is Camunda 8.8, the skill detects every test.",
            "| Migrate | Report only | `test migration needs Camunda 8.9 or later` |",
            "| Migrate to CPT | Report only | `test migration needs Camunda 8.9 or later` |",
        ):
            with self.subTest(required=required):
                self.assertIn(
                    required.lower(),
                    reference.lower(),
                    msg=f"Missing inventory rule {required!r}",
                )

    def test_inventory_maps_inherited_methods_to_concrete_classes(self):
        reference = " ".join(REFERENCE.read_text().split())

        for required in (
            "The skill inventories every test method declared or inherited by each concrete test class in the scanned test source sets.",
            "For each inherited method, the skill creates one row for every concrete test class that executes it.",
            "The Test ID uses the concrete class and method name.",
            "The File column names the source file that declares the method.",
            "The skill does not create a row for an abstract class by itself.",
        ):
            with self.subTest(reference_rule=required):
                self.assertIn(required, reference)

    def test_inventory_classification_uses_execution_and_special_case_precedence(self):
        reference = REFERENCE.read_text()
        normalized_reference = " ".join(reference.split())

        for required in (
            "Apply the table from top to bottom.",
            "The first matching row assigns one test kind and handling.",
            "The skill classifies tests by executed engine behavior, not assertion type.",
            "Test rules, extensions, dependencies, and API references alone do not prove that a test executed a BPMN process or DMN decision.",
            "When a real C7 process or decision test asserts only endpoint responses or downstream side effects, the skill keeps the test in scope.",
            "The skill records assertion gaps in the Test Inventory's Notes column for migration review.",
            "The skill verifies that a direct service call resolves to a real C7 engine in the test or its shared configuration.",
            "The skill requires a completed task's `processInstanceId` to identify an executed BPMN process before `TaskService.complete(...)` is a process-test signal.",
            "`TaskService.newTask()` without a process instance is not a process-test signal.",
            "| BPMN model built with the Camunda fluent model API, such as `Bpmn.createExecutableProcess()` | Record the model as programmatically built and note the manual migration reason in `Notes` | Report only |",
            "`@Deployment` is model-resolution evidence, not a test-kind signal by itself.",
            "mocked `RuntimeService`",
            "`ProcessEnginePlugin`",
            "`DecisionService`",
        ):
            with self.subTest(required=required):
                self.assertIn(
                    required,
                    normalized_reference,
                    msg=f"Missing classification rule {required!r}",
                )

        table = reference.split(
            "| Priority | Test kind | Detect by | Handling |", 1
        )[1].split("\n\n", 1)[0]
        rows = [
            [cell.strip() for cell in line.split("|")[1:-1]]
            for line in table.splitlines()
            if line.startswith("| ") and line.split("|")[1].strip().isdigit()
        ]
        self.assertEqual(
            [(row[0], row[1].lower()) for row in rows],
            [
                ("1", "out of scope (camunda 8)"),
                ("2", "manual redesign"),
                ("3", "manual migration"),
                ("4", "scenario test"),
                ("5", "remote-engine test"),
                ("6", "decision test"),
                ("7", "process test"),
                ("8", "out of scope"),
            ],
        )
        self.assertIn("CMMN", rows[1][2])
        self.assertIn("ProcessEnginePlugin", rows[1][2])
        self.assertIn(
            "does not execute a real c7 bpmn process or dmn decision",
            rows[-1][2].lower(),
        )
        self.assertIn("standalone tasks created with `TaskService.newTask()`", rows[-1][2])
        self.assertIn("It may call a Spring Boot endpoint that starts a process", rows[6][2])
        self.assertIn("on a real C7 engine", rows[6][2])
        self.assertIn(
            "It may call a real C7 engine's `RuntimeService` to start a process",
            rows[6][2],
        )
        self.assertIn("`startProcessInstanceByKey(...)`", rows[6][2])
        self.assertIn(
            "`TaskService` to complete a task with a non-null `processInstanceId`",
            rows[6][2],
        )
        self.assertIn("mocked `RuntimeService`", rows[-1][2])
        self.assertEqual(rows[3][3], "Migrate (lower priority)")
        self.assertEqual(rows[4][3], "Report only")
        self.assertEqual(rows[5][3], "Report only")
        self.assertEqual(rows[6][3], "Migrate to CPT")
        self.assertIn(
            "If the separate DMN migration work in #3203 is incomplete, then the skill keeps decision tests at `Report only`.",
            normalized_reference,
        )
        self.assertIn(
            "| Migrate to CPT | Report only |",
            normalized_reference,
        )
        manual_source = MANUAL_SOURCE_TEST.read_text()
        spring_context = MANUAL_SOURCE_CONTEXT.read_text()
        self.assertIn("runtimeService.startProcessInstanceByKey", manual_source)
        self.assertIn("SpringProcessEngineConfiguration", spring_context)
        self.assertIn("ProcessEngineFactoryBean", spring_context)

    def test_spring_migration_requires_cpt_selected_inventory_rows(self):
        reference = REFERENCE.read_text()

        self.assertIn(
            "The skill applies Spring test migration only to process test rows with the `Spring` modifier and handling `Migrate to CPT`.",
            reference,
        )
        self.assertIn(
            "A Spring test slice that uses only mocked C7 APIs is out of scope.",
            reference,
        )
        self.assertIn(
            "| `@WebMvcTest`, `@DataJpaTest`, or another Spring test slice without execution against a real C7 engine | Out of scope. |",
            reference,
        )
        self.assertNotIn(
            "When a Spring test slice calls a Camunda 7 API, the skill includes it.",
            reference,
        )

    def test_fixture_walkthrough_attributes_before_each_to_test_class(self):
        readme = " ".join((FIXTURE / "README.md").read_text().split())
        expected_test = EXPECTED_TEST.read_text()

        self.assertIn("void repeatStartupHookForEachTest()", expected_test)
        self.assertIn("startupProcessStarter.startStartupProcess(camundaClient)", expected_test)
        self.assertIn(
            "`SpringProcessTest` uses `@BeforeEach` to repeat the startup hook because CPT deletes runtime data after each test.",
            readme,
        )
        self.assertNotIn("Its `@BeforeEach` method", readme)

    def test_non_boot_spring_migration_replaces_junit4_runner_and_annotations(self):
        reference = " ".join(REFERENCE.read_text().split())

        self.assertIn(
            "When the skill migrates a non-Boot Spring test that uses JUnit 4, it replaces its runner with `@ExtendWith(SpringExtension.class)`.",
            reference,
        )
        self.assertIn(
            "The skill replaces JUnit 4 test and lifecycle annotations and assertions with JUnit 5 equivalents. It updates their imports.",
            reference,
        )
        self.assertIn(
            "| `@RunWith(SpringJUnit4ClassRunner.class)` or `@RunWith(SpringRunner.class)` | `@ExtendWith(SpringExtension.class)` without `@RunWith`. |",
            reference,
        )

    def test_cpt_dependency_cleanup_preserves_remaining_c7_test_consumers(self):
        reference = " ".join(REFERENCE.read_text().split())

        self.assertIn(
            "The skill removes each C7 test dependency that no remaining test or production code uses after migration.",
            reference,
        )
        for artifact in (
            "`camunda-bpm-spring-boot-starter-test`",
            "`camunda-bpm-junit5`",
            "`camunda-bpm-assert`",
        ):
            with self.subTest(artifact=artifact):
                self.assertIn(artifact, reference)
        self.assertIn(
            "When production code or an unmigrated test uses a dependency, the skill keeps it.",
            reference,
        )

    def test_cpt_dependency_requires_inventory_selected_migrations(self):
        checklist = " ".join(CODE_CHECKLIST.read_text().split())

        self.assertIn(
            "When at least one Test Inventory row has the `Spring` modifier and handling `Migrate to CPT`, the skill selects the CPT dependency",
            checklist,
        )

    def test_endpoint_migration_preserves_start_complete_and_correlation_operations(self):
        reference = " ".join(REFERENCE.read_text().split())

        self.assertIn(
            "The skill preserves the endpoint operation that the test exercises.",
            reference,
        )
        self.assertIn(
            "The skill maps the original C7 operation to the equivalent `CamundaClient` operation.",
            reference,
        )
        self.assertNotIn(
            "The endpoint starts the process through `CamundaClient`.",
            reference,
        )
        for operation in (
            "| Starts a BPMN process | Starts the same process through `CamundaClient`. |",
            "| Completes a process-backed task | Completes the same task through `CamundaClient`. |",
            "| Correlates a message | Correlates the same message through `CamundaClient`. |",
        ):
            with self.subTest(operation=operation):
                self.assertIn(operation, reference)

    def test_standalone_task_completion_is_not_a_process_test(self):
        reference = " ".join(REFERENCE.read_text().split())
        source = STANDALONE_TASK_TEST.read_text()
        report = " ".join(STANDALONE_TASK_REPORT.read_text().split()).lower()
        inventory = report.split("## test inventory", 1)[1]

        self.assertIn("taskService.newTask()", source)
        self.assertIn("assertNull(task.getProcessInstanceId());", source)
        self.assertIn("taskService.complete(task.getId());", source)
        self.assertNotIn("startProcessInstanceByKey", source)
        self.assertIn(
            "`TaskService.newTask()` without a process instance is not a process-test signal.",
            reference,
        )
        self.assertIn("| out of scope |", inventory)
        self.assertIn("not part of test migration", inventory)
        self.assertIn("without executing a bpmn process", inventory)

    def test_target_89_converted_models_use_zeebe_user_task_extensions(self):
        namespace = {
            "bpmn": "http://www.omg.org/spec/BPMN/20100524/MODEL",
            "zeebe": "http://camunda.org/schema/zeebe/1.0",
        }
        models = sorted(EXPECTED_MODELS.glob("converted-c8-*.bpmn"))

        self.assertTrue(models, "Expected at least one converted Camunda 8 BPMN model")
        for model in models:
            with self.subTest(model=model.name):
                root = ET.parse(model).getroot()
                user_tasks = root.findall(".//bpmn:userTask", namespace)
                self.assertTrue(user_tasks, "Expected a converted user task")
                for user_task in user_tasks:
                    extensions = user_task.find("bpmn:extensionElements", namespace)
                    self.assertIsNotNone(extensions, user_task.get("id"))
                    self.assertEqual(
                        len(extensions.findall("zeebe:userTask", namespace)),
                        1,
                        user_task.get("id"),
                    )

    def test_test_parity_record_has_a_location_format_and_creation_rule(self):
        reference = " ".join(REFERENCE.read_text().split())

        for required in (
            "## Test Parity record",
            "`MIGRATION_REPORT.md` at the project root",
            "When `MIGRATION_REPORT.md` does not exist, the skill creates it",
            "before changing the boundary",
            "| Test ID | C7 boundary | Approved C8 boundary | Approver | Reason |",
        ):
            with self.subTest(required=required):
                self.assertIn(required, reference, msg=f"Missing {required!r}")

    def test_startup_hook_rules_preserve_each_original_action(self):
        reference = REFERENCE.read_text()

        self.assertIn(
            "The skill replays each startup-hook action after CPT starts the test runtime.",
            reference,
        )
        for action in (
            "| Deploys resources | The test app adds the converted copies to `@Deployment`. |",
            "| Starts a process | The test calls the startup method in `@BeforeEach` with `CamundaClient`. |",
            "| Sends a message | The test sends the equivalent message in `@BeforeEach`. |",
        ):
            with self.subTest(action=action):
                self.assertIn(action, reference)

    def test_rfc_preference_markers_are_explicit(self):
        for line in REFERENCE.read_text().splitlines():
            if "SHOULD" in line:
                self.assertIn("(SHOULD)", line)

    def test_boot_starter_matches_cpt_dependency(self):
        namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
        pom = ET.parse(EXPECTED_POM).getroot()
        self.assertEqual(
            pom.findtext("./m:parent/m:version", namespaces=namespace), "4.1.1"
        )
        dependencies = {
            dependency.findtext("m:artifactId", namespaces=namespace): (
                dependency.findtext("m:groupId", namespaces=namespace),
                dependency.findtext("m:scope", default="compile", namespaces=namespace),
            )
            for dependency in pom.findall("./m:dependencies/m:dependency", namespace)
        }

        self.assertEqual(dependencies["camunda-spring-boot-starter"][0], "io.camunda")
        self.assertEqual(
            dependencies["camunda-process-test-spring"], ("io.camunda", "test")
        )
        self.assertEqual(
            dependencies["spring-boot-starter-webmvc-test"],
            ("org.springframework.boot", "test"),
        )
        self.assertNotIn("camunda-bpm-spring-boot-starter-test", dependencies)
        self.assertNotIn("h2", dependencies)
        self.assertNotIn("camunda-spring-boot-3-starter", dependencies)
        self.assertNotIn("camunda-process-test-spring-boot-3", dependencies)
        self.assertNotIn("camunda-process-test-java", dependencies)

    def test_source_fixture_covers_spring_mocks_and_startup(self):
        source_test = SOURCE_TEST.read_text()
        startup_runner = (
            FIXTURE
            / "c7-source/src/main/java/org/camunda/bpm/example/springprocess/startup/StartupProcessRunner.java"
        ).read_text()

        for required in (
            "@RunWith(SpringRunner.class)",
            "@SpringBootTest",
            "@Autowired private RuntimeService",
            "@MockBean private PaymentService",
            "@MockBean private ShipOrderDelegate",
            ".perform(",
            "BpmnAwareTests.init(processEngine)",
        ):
            with self.subTest(required=required):
                self.assertIn(required, source_test)
        self.assertIn("implements CommandLineRunner", startup_runner)

    def test_cpt_fixture_preserves_endpoint_and_worker_mock_boundaries(self):
        source_test = SOURCE_TEST.read_text()
        expected_test = EXPECTED_TEST.read_text()
        source_controller = SOURCE_CONTROLLER.read_text()
        expected_controller = EXPECTED_CONTROLLER.read_text()
        expected_workers = EXPECTED_WORKERS.read_text()
        application = TEST_APPLICATION.read_text()
        application_startup_resources = (
            FIXTURE
            / "expected-c8/src/main/java/org/camunda/bpm/example/springprocess/SpringProcessApplication.java"
        ).read_text()
        worker_override = (
            FIXTURE / "expected-c8/src/test/resources/application.properties"
        ).read_text()
        self.assertNotIn("camunda.bpm.", worker_override)

        for required in (
            "@SpringBootTest(classes = TestProcessApplication.class)",
            "@CamundaSpringProcessTest",
            "@Autowired private CamundaClient",
            "@Autowired private CamundaProcessTestContext",
            "@MockitoBean private PaymentService",
            "processTestContext.mockJobWorker(\"ship-order\").thenComplete()",
            "startupProcessStarter.startStartupProcess(camundaClient)",
            ".perform(",
            "createsAnOrderThroughTheApplicationEndpoint",
            "startsAnOrderFromTheStartupHook",
            "hasCompletedElements",
            "hasActiveElements",
            "timeout(10_000)",
        ):
            with self.subTest(required=required):
                self.assertIn(required, expected_test)
        self.assertIn("createsAnOrderThroughTheApplicationEndpoint", source_test)
        self.assertIn("startsAnOrderFromTheStartupHook", source_test)
        for controller in (source_controller, expected_controller):
            self.assertIn("ResponseEntity<Void> createOrder", controller)
            self.assertIn("return ResponseEntity.accepted().build();", controller)
        self.assertNotIn("StartedOrder", expected_controller)
        for test_source in (source_test, expected_test):
            self.assertIn('.andExpect(content().string(""))', test_source)
        self.assertIn('@JobWorker(type = "charge-payment")', expected_workers)
        self.assertIn("paymentService.charge(amount)", expected_workers)
        self.assertIn("@Deployment", application)
        self.assertIn("scanBasePackages", application)
        self.assertIn("converted-c8-order.bpmn", application)
        self.assertIn("converted-c8-startup-order.bpmn", application)
        self.assertIn("converted-c8-startup-order.bpmn", application_startup_resources)
        self.assertIn(
            "camunda.client.worker.override.ship-order.enabled=false", worker_override
        )
        self.assertNotEqual(
            EXPECTED_TEST.parent.as_posix(), TEST_APPLICATION.parent.as_posix()
        )

    def test_migrated_test_preserves_source_package_and_path(self):
        source_package = next(
            line
            for line in SOURCE_TEST.read_text().splitlines()
            if line.startswith("package ")
        )
        expected_package = next(
            line
            for line in EXPECTED_TEST.read_text().splitlines()
            if line.startswith("package ")
        )
        self.assertEqual(expected_package, source_package)
        self.assertEqual(
            EXPECTED_TEST.relative_to(FIXTURE / "expected-c8"),
            SOURCE_TEST.relative_to(FIXTURE / "c7-source"),
        )

    def test_c8_production_package_matches_source_and_test_app_is_outside_its_root(self):
        source_application = SOURCE_APPLICATION.read_text()
        expected_application = EXPECTED_APPLICATION.read_text()
        test_application = TEST_APPLICATION.read_text()
        reference = " ".join(REFERENCE.read_text().split())

        source_package = next(
            line for line in source_application.splitlines() if line.startswith("package ")
        )
        expected_package = next(
            line for line in expected_application.splitlines() if line.startswith("package ")
        )
        test_package = next(
            line for line in test_application.splitlines() if line.startswith("package ")
        )
        self.assertEqual(expected_package, source_package)
        self.assertNotEqual(test_package, expected_package)
        self.assertFalse(
            test_package.removeprefix("package ")
            .removesuffix(";")
            .startswith("org.camunda.bpm.example.springprocess.")
        )
        self.assertIn(
            "The test app uses a package outside the production application's component-scan root.",
            reference,
        )

    def test_missing_non_bootstrap_is_report_only_with_manual_reason(self):
        reference = REFERENCE.read_text().lower()
        source_test = MANUAL_SOURCE_TEST.read_text()
        source_context = MANUAL_SOURCE_CONTEXT.read_text()
        expected_report = EXPECTED_MANUAL_REPORT.read_text().lower()

        self.assertIn("@ContextConfiguration", source_test)
        self.assertIn("SpringProcessEngineConfiguration", source_context)
        self.assertIn("@camundaprocesstest", reference)
        self.assertIn("manual migration", reference)
        inventory = expected_report.split("## test inventory", 1)[1].split(
            "## test migration", 1
        )[0]
        for required in (
            "manual-without-bootstrap:org.camunda.bpm.example.manual.manualspringprocesstest#startsaprocesswiththespringengine",
            "| test id | file | test kind | signals | models | handling | notes |",
            "process test",
            "spring modifier",
            "manual-process.bpmn",
            "report only",
            "manual migration:",
        ):
            with self.subTest(required=required):
                self.assertIn(required, inventory)
        self.assertIn(
            "| a shared engine in a war or `processes.xml` application-server deployment | report only. record the manual migration reason in notes. |",
            reference,
        )
        manual_row = next(
            line for line in inventory.splitlines() if "manualspringprocesstest#" in line
        )
        manual_cells = [cell.strip() for cell in manual_row.split("|")[1:-1]]
        self.assertEqual(manual_cells[5].lower(), "report only")
        self.assertIn("manual migration", manual_cells[6].lower())
        self.assertIn("### test kind counts", expected_report)
        self.assertTrue(MANUAL_PROCESS.is_file())
        self.assertTrue(MANUAL_WORKER.is_file())
        namespace = {
            "bpmn": "http://www.omg.org/spec/BPMN/20100524/MODEL",
            "spring": "http://www.springframework.org/schema/beans",
        }
        process = ET.parse(MANUAL_PROCESS).getroot().find(
            ".//bpmn:process[@id='manual-process']", namespace
        )
        self.assertIsNotNone(process)
        worker_task = process.find(
            ".//bpmn:serviceTask[@id='manual-worker']", namespace
        )
        self.assertIsNotNone(worker_task)
        self.assertEqual(
            worker_task.get("{http://camunda.org/schema/1.0/bpmn}class"),
            "org.camunda.bpm.example.manual.ManualProcessWorker",
        )
        source_context = ET.parse(MANUAL_SOURCE_CONTEXT).getroot()
        process_engine_configuration = source_context.find(
            ".//spring:bean[@id='processEngineConfiguration']", namespace
        )
        self.assertIsNotNone(process_engine_configuration)
        self.assertEqual(
            process_engine_configuration.get("class"),
            "org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration",
        )
        runtime_service = source_context.find(
            ".//spring:bean[@id='runtimeService']", namespace
        )
        self.assertIsNotNone(runtime_service)
        self.assertEqual(runtime_service.get("factory-bean"), "processEngine")
        self.assertEqual(runtime_service.get("factory-method"), "getRuntimeService")
        deployment = source_context.find(
            ".//spring:property[@name='deploymentResources']", namespace
        )
        self.assertIsNotNone(deployment)
        self.assertIn("manual-process.bpmn", "".join(deployment.itertext()))
        self.assertIn("manualWorkerExecuted", source_test)
        self.assertIn("assertEquals(", source_test)
        self.assertIn("Boolean.TRUE", source_test)
        manual_worker = MANUAL_WORKER.read_text()
        self.assertIn("implements JavaDelegate", manual_worker)
        self.assertIn(
            'execution.setVariable("manualWorkerExecuted", true);', manual_worker
        )
        self.assertIn("manualprocessworker", expected_report)
        self.assertIn("reusable `camundaclient` worker bootstrap", expected_report)

    def test_manual_migration_cases_use_report_only_handling(self):
        reference = " ".join(REFERENCE.read_text().split())

        self.assertIn(
            "If the application has no usable worker bootstrap, then the skill sets the test's handling to `Report only`.",
            reference,
        )
        self.assertIn(
            "The skill records the manual migration reason in the Notes column of `MIGRATION_REPORT.md`.",
            reference,
        )


if __name__ == "__main__":
    unittest.main()
