from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


REPO_ROOT = Path(__file__).resolve().parents[3]
FIXTURE_ROOT = REPO_ROOT / "agentic-migration-skills/fixtures/slf4j-provider"
CHECKLIST_PATH = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/code-transform-checklist.md"
)
POM_PATH = FIXTURE_ROOT / "expected-c8/pom.xml"
WALKTHROUGH_PATH = FIXTURE_ROOT / "README.md"
MAVEN_NS = {"m": "http://maven.apache.org/POM/4.0.0"}


class Slf4jProviderScopeTest(unittest.TestCase):
    def test_runtime_classpath_uses_the_dependency_plugin_user_property(self):
        checklist = " ".join(CHECKLIST_PATH.read_text().split())

        self.assertIn("dependency:build-classpath -DincludeScope=runtime", checklist)
        self.assertNotIn(
            "dependency:build-classpath -Dmdep.includeScope=runtime",
            checklist,
        )
        self.assertIn("`-Dmdep.outputFile`", checklist)
        self.assertIn(
            "Do not use a test classpath as evidence for the launch path.",
            checklist,
        )

    def test_provider_verdict_requires_one_initialized_non_nop_provider(self):
        checklist = " ".join(CHECKLIST_PATH.read_text().split())

        self.assertIn(
            "| The compatible-provider count is exactly one. The provider initializes. "
            "The factory is not `NOPLoggerFactory`. No conflict appears. | None | "
            "**PASS** | Record the evidence. |",
            checklist,
        )
        self.assertIn(
            "| The API is missing, no compatible provider initializes, the factory is "
            "`NOPLoggerFactory`, or initialization fails. | None | Open finding |",
            checklist,
        )

    def test_fixture_has_a_test_only_provider_and_a_runtime_control(self):
        project = ET.parse(POM_PATH).getroot()
        properties = project.find("m:properties", MAVEN_NS)
        dependencies = project.findall("m:dependencies/m:dependency", MAVEN_NS)
        provider = next(
            dependency
            for dependency in dependencies
            if dependency.findtext("m:artifactId", namespaces=MAVEN_NS)
            == "logback-classic"
        )
        api = next(
            dependency
            for dependency in dependencies
            if dependency.findtext("m:artifactId", namespaces=MAVEN_NS) == "slf4j-api"
        )
        runtime_profile = next(
            profile
            for profile in project.findall("m:profiles/m:profile", MAVEN_NS)
            if profile.findtext("m:id", namespaces=MAVEN_NS) == "with-runtime-provider"
        )

        self.assertIsNotNone(properties)
        self.assertEqual(
            "test",
            properties.findtext("m:slf4j.provider.scope", namespaces=MAVEN_NS),
        )
        self.assertEqual(
            "${slf4j.provider.scope}",
            provider.findtext("m:scope", namespaces=MAVEN_NS),
        )
        self.assertEqual(
            "compile",
            api.findtext("m:scope", default="compile", namespaces=MAVEN_NS),
        )
        self.assertEqual(
            "runtime",
            runtime_profile.findtext(
                "m:properties/m:slf4j.provider.scope",
                namespaces=MAVEN_NS,
            ),
        )

    def test_walkthrough_checks_the_runtime_graph_and_both_factory_outcomes(self):
        walkthrough = WALKTHROUGH_PATH.read_text()

        for required_evidence in (
            "dependency:build-classpath -Dmdep.includeScope=runtime",
            "dependency:build-classpath -DincludeScope=runtime",
            "dependency:tree -Dscope=runtime",
            "BOOT-INF/lib/logback-classic",
            "-Pwith-runtime-provider",
            "org.slf4j.helpers.NOPLoggerFactory",
            "ch.qos.logback.classic.LoggerContext",
            "exactly one compatible provider",
        ):
            with self.subTest(evidence=required_evidence):
                self.assertIn(required_evidence, walkthrough)


if __name__ == "__main__":
    unittest.main()
