from pathlib import Path
import unittest


REPO_ROOT = Path(__file__).resolve().parents[3]
SKILL_PATH = REPO_ROOT / "agentic-migration-skills/skills/migrate-c7-to-c8-code/SKILL.md"
CHECKLIST_PATH = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/code-transform-checklist.md"
)
DEPENDENCIES_PATH = REPO_ROOT / "code-conversion/patterns/10-general/dependencies.md"
BLOCKED_REPORT_PATH = (
    REPO_ROOT
    / "agentic-migration-skills/fixtures/domain-license-dependency/expected-blocked/MIGRATION_REPORT.md"
)


class MigrationGuidanceTest(unittest.TestCase):
    def test_camunda_group_does_not_imply_engine_only_dependency(self):
        skill = " ".join(SKILL_PATH.read_text().lower().split())
        checklist = " ".join(CHECKLIST_PATH.read_text().lower().split())
        dependencies = " ".join(DEPENDENCIES_PATH.read_text().lower().split())

        forbidden_rules = (
            (skill, "no dependency with groupid `org.camunda.bpm` remains"),
            (skill, "no import remains. each one is a missed migration"),
            (checklist, "remove dependencies with groupid `org.camunda.bpm`"),
            (dependencies, "remove all camunda 7 dependencies"),
        )
        for document, rule in forbidden_rules:
            with self.subTest(rule=rule):
                self.assertFalse(rule in document, f"Unconditional cleanup rule remains: {rule}")

        self.assertTrue(
            "inventory every dependency before classification" in dependencies,
            "The dependency catalog must inventory every dependency before classification.",
        )
        self.assertTrue(
            "review signal, not proof" in dependencies,
            "The dependency catalog must classify the group ID as a review signal.",
        )
        self.assertTrue(
            "blocking/manual" in dependencies,
            "The dependency catalog must require a blocker when no replacement is approved.",
        )

        blocked_report = " ".join(BLOCKED_REPORT_PATH.read_text().lower().split())
        self.assertTrue(
            "licenseprovisioningservice.createlicense" in blocked_report,
            "The blocked report must name the create call site.",
        )
        self.assertTrue(
            "licenseprovisioningservice.updatelicense" in blocked_report,
            "The blocked report must name the update call site.",
        )
        self.assertTrue(
            "blocks both license flows" in blocked_report,
            "The blocked report must state that both flows remain blocked.",
        )


if __name__ == "__main__":
    unittest.main()
