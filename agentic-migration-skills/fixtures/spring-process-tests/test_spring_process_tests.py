from pathlib import Path
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
    / "expected-c8/src/test/java/org/camunda/bpm/example/springprocess/test/SpringProcessTest.java"
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
EXPECTED_MODELS = FIXTURE / "expected-c8/src/main/resources/processes"
SOURCE_APPLICATION = (
    FIXTURE
    / "c7-source/src/main/java/org/camunda/bpm/example/springprocess/SpringProcessApplication.java"
)
EXPECTED_APPLICATION = (
    FIXTURE
    / "expected-c8/src/main/java/org/camunda/bpm/example/springprocess/SpringProcessApplication.java"
)


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
        for required in (
            "every scope",
            "Assessment only",
            "Approach C",
            "Test ID",
            "test kind",
            "modifiers",
            "models",
            "MIGRATION_REPORT.md",
        ):
            with self.subTest(required=required):
                self.assertIn(
                    required.lower(),
                    inventory_section.lower(),
                    msg=f"Missing {required!r} from Step 2 inventory",
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
            "Kotlin or Groovy tests that use C7 test APIs",
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
            "<module path>:<fully qualified class name>#<method>",
            "| Test ID | File | Test kind | Signals | Models | Handling | Notes |",
            "Camunda 8.9 or later",
            "test migration needs Camunda 8.9 or later",
            "When the target version is 8.8, the skill still detects every test.",
            "It sets each in-scope test's handling to Report only",
        ):
            with self.subTest(required=required):
                self.assertIn(
                    required.lower(),
                    reference.lower(),
                    msg=f"Missing inventory rule {required!r}",
                )

    def test_inventory_classification_uses_execution_and_special_case_precedence(self):
        reference = REFERENCE.read_text()

        for required in (
            "Apply the table from top to bottom.",
            "The first matching row assigns one test kind and handling.",
            "The skill classifies tests by executed engine behavior, not assertion type.",
            "A real C7 process or decision test remains in scope when it asserts only endpoint responses or downstream side effects.",
            "The skill records assertion gaps in the Test Inventory's Notes column for migration review.",
            "`@Deployment` is model-resolution evidence, not a test-kind signal by itself.",
            "mocked `RuntimeService`",
            "`ProcessEnginePlugin`",
            "`DecisionService`",
        ):
            with self.subTest(required=required):
                self.assertIn(required, reference, msg=f"Missing classification rule {required!r}")

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
        self.assertIn("does not execute a real c7 engine", rows[-1][2].lower())
        self.assertIn("calls an endpoint that starts a process", rows[6][2])

    def test_spring_migration_requires_cpt_selected_inventory_rows(self):
        reference = REFERENCE.read_text()

        self.assertIn(
            "The skill applies Spring test migration only to process or decision test rows with the `Spring` modifier and handling `Migrate to CPT`.",
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

    def test_cpt_dependency_requires_inventory_selected_migrations(self):
        checklist = CODE_CHECKLIST.read_text()

        self.assertIn(
            "When at least one Test Inventory row has handling `Migrate to CPT`, the skill selects the CPT dependency",
            checklist,
        )

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
            "Create `MIGRATION_REPORT.md` when it does not exist",
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

    def test_missing_non_bootstrap_has_manual_reason(self):
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
            "manual migration",
        ):
            with self.subTest(required=required):
                self.assertIn(required, inventory)
        self.assertIn("counts by test kind: process test 1", inventory)
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


if __name__ == "__main__":
    unittest.main()
