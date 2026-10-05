from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


FIXTURE = Path(__file__).resolve().parent
SKILL_ROOT = FIXTURE.parents[1] / "skills/migrate-c7-to-c8-code"
REFERENCE = SKILL_ROOT / "references/test-migration.md"
PACKAGE_README = SKILL_ROOT.parents[1] / "README.md"
EXPECTED_POM = FIXTURE / "expected-c8/pom.xml"
SOURCE_TEST = (
    FIXTURE
    / "c7-source/src/test/java/org/camunda/bpm/example/springprocess/SpringProcessTest.java"
)
EXPECTED_TEST = (
    FIXTURE
    / "expected-c8/src/test/java/org/camunda/bpm/example/springprocess/test/SpringProcessTest.java"
)
EXPECTED_WORKERS = (
    FIXTURE
    / "expected-c8/src/main/java/org/camunda/bpm/example/springprocess/worker/OrderWorkers.java"
)
TEST_APPLICATION = (
    FIXTURE
    / "expected-c8/src/test/java/org/camunda/bpm/example/springprocess/testapp/TestProcessApplication.java"
)
EXPECTED_MANUAL_REPORT = (
    FIXTURE / "manual-without-bootstrap/expected-c8/MIGRATION_REPORT.md"
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
                self.assertIn(required, reference)
        self.assertIn(
            "process-test migration to Camunda Process Test (CPT)", package_readme
        )

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
        expected_workers = EXPECTED_WORKERS.read_text()
        application = TEST_APPLICATION.read_text()
        application_startup_resources = (
            FIXTURE
            / "expected-c8/src/main/java/org/camunda/bpm/example/springprocess/application/SpringProcessApplication.java"
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

    def test_missing_non_bootstrap_has_manual_reason(self):
        reference = REFERENCE.read_text().lower()
        source_test = (
            FIXTURE
            / "manual-without-bootstrap/c7-source/src/test/java/org/camunda/bpm/example/manual/ManualSpringProcessTest.java"
        ).read_text()
        source_context = (
            FIXTURE
            / "manual-without-bootstrap/c7-source/src/test/resources/spring-engine-context.xml"
        ).read_text()
        expected_report = EXPECTED_MANUAL_REPORT.read_text().lower()

        self.assertIn("@ContextConfiguration", source_test)
        self.assertIn("SpringProcessEngineConfiguration", source_context)
        self.assertIn("@camundaprocesstest", reference)
        self.assertIn("manual migration", reference)
        self.assertIn("reusable `camundaclient` worker bootstrap", expected_report)


if __name__ == "__main__":
    unittest.main()
