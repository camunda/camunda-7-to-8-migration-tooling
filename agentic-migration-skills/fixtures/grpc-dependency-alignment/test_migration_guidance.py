from pathlib import Path
import unittest


REPO_ROOT = Path(__file__).resolve().parents[3]
FIXTURE_PATH = REPO_ROOT / "agentic-migration-skills/fixtures/grpc-dependency-alignment/README.md"
CHECKLIST_PATH = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/code-transform-checklist.md"
)
DEPENDENCIES_PATH = REPO_ROOT / "code-conversion/patterns/10-general/dependencies.md"
ALL_IN_ONE_PATH = REPO_ROOT / "code-conversion/patterns/ALL_IN_ONE.md"


class GrpcDependencyAlignmentGuidanceTest(unittest.TestCase):
    def test_focused_startup_probe_imports_only_client_auto_configuration(self):
        fixture = " ".join(FIXTURE_PATH.read_text().split())

        self.assertIn(
            "import io.camunda.spring.boot.starter.client.CamundaClientAutoConfiguration;",
            fixture,
        )
        self.assertIn(
            "@ImportAutoConfiguration(CamundaClientAutoConfiguration.class)",
            fixture,
        )
        self.assertNotIn("@EnableAutoConfiguration", fixture)

    def test_linkage_error_guidance_blocks_readiness(self):
        checklist = " ".join(CHECKLIST_PATH.read_text().lower().split())

        self.assertIn(
            "add an open item with status `blocked` to `migration_report.md` and mark readiness blocked",
            checklist,
        )

    def test_dependency_pattern_marks_incompatible_family_as_blocked(self):
        dependencies = " ".join(DEPENDENCIES_PATH.read_text().lower().split())
        all_in_one = " ".join(ALL_IN_ONE_PATH.read_text().lower().split())
        expected_rule = (
            "if startup reports a `linkageerror` or the resolved dependency graph proves an "
            "incompatible family, add an open item with status `blocked` to `migration_report.md` "
            "and mark readiness blocked"
        )

        self.assertIn(expected_rule, dependencies)
        self.assertIn(expected_rule, all_in_one)


if __name__ == "__main__":
    unittest.main()
