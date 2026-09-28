from pathlib import Path
import unittest


REPO_ROOT = Path(__file__).resolve().parents[3]
FIXTURE_PATH = Path(__file__).resolve().with_name("README.md")
CHECKLIST_PATH = (
    REPO_ROOT
    / "agentic-migration-skills"
    / "skills"
    / "migrate-c7-to-c8-code"
    / "references"
    / "code-transform-checklist.md"
)
DEPENDENCIES_PATH = (
    REPO_ROOT / "code-conversion" / "patterns" / "10-general" / "dependencies.md"
)
ALL_IN_ONE_PATH = REPO_ROOT / "code-conversion" / "patterns" / "ALL_IN_ONE.md"
GUIDANCE_TEST_COMMAND = (
    "python3 agentic-migration-skills/fixtures/"
    "grpc-dependency-alignment/test_migration_guidance.py"
)
CHECKLIST_BLOCKED_RULE = (
    "for each readiness-blocking finding, the skill adds an open item "
    "with status `blocked` to `migration_report.md`"
)
DEPENDENCIES_BLOCKED_RULE = (
    "for each readiness-blocking finding, add an open item "
    "with status `blocked` to `migration_report.md`"
)
STARTUP_BLOCKED_RULE = (
    "if startup reports a `linkageerror` or the resolved dependency graph proves an "
    "incompatible family, add an open item with status `blocked` to `migration_report.md` "
    "and mark readiness blocked"
)


def normalized(path: Path) -> str:
    return " ".join(path.read_text().lower().split())


class GrpcDependencyAlignmentGuidanceTest(unittest.TestCase):
    def test_fixture_documents_runnable_guidance_check(self):
        fixture = normalized(FIXTURE_PATH)

        self.assertIn(GUIDANCE_TEST_COMMAND, fixture)
        self.assertIn(
            "the test checks that the dependency workflow and blocked-item contract are documented",
            fixture,
        )
        self.assertIn("it does not run an application migration", fixture)

    def test_focused_startup_probe_imports_only_client_auto_configuration(self):
        fixture = FIXTURE_PATH.read_text()

        self.assertIn(
            "import io.camunda.spring.boot.starter.client.CamundaClientAutoConfiguration;",
            fixture,
        )
        self.assertIn(
            "@ImportAutoConfiguration(CamundaClientAutoConfiguration.class)",
            fixture,
        )
        self.assertNotIn("@EnableAutoConfiguration", fixture)

    def test_readiness_blockers_require_blocked_report_items(self):
        checklist = normalized(CHECKLIST_PATH)
        dependencies = normalized(DEPENDENCIES_PATH)
        all_in_one = normalized(ALL_IN_ONE_PATH)

        self.assertIn(CHECKLIST_BLOCKED_RULE, checklist)
        self.assertIn("mark readiness blocked", checklist)
        self.assertIn(DEPENDENCIES_BLOCKED_RULE, dependencies)
        self.assertIn("record the evidence and the required follow-up", dependencies)
        self.assertIn(STARTUP_BLOCKED_RULE, dependencies)
        self.assertIn(STARTUP_BLOCKED_RULE, all_in_one)

    def test_fixture_preserves_the_observed_runtime_evidence(self):
        fixture = normalized(FIXTURE_PATH)

        for evidence in (
            "`io.grpc:grpc-xds`",
            "`io.grpc:grpc-util`",
            "`io.grpc:grpc-core`",
            "1.66.0",
            "1.79.0",
            "incompatibleclasschangeerror",
            "it does not explain every test error",
            "@springboottest",
            "@test",
            "assertnotnull(camundaclient)",
        ):
            with self.subTest(evidence=evidence):
                self.assertIn(evidence, fixture)


if __name__ == "__main__":
    unittest.main()
