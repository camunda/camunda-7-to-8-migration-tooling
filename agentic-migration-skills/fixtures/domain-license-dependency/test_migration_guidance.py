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

        blocked_report_source = BLOCKED_REPORT_PATH.read_text().lower()
        blocked_report = " ".join(blocked_report_source.split())
        self.assertTrue(
            "licenseprovisioningservice.createlicense" in blocked_report,
            "The blocked report must name the create call site.",
        )
        self.assertTrue(
            "licenseprovisioningservice.updatelicense" in blocked_report,
            "The blocked report must name the update call site.",
        )
        self.assertTrue(
            "block both license flows" in blocked_report,
            "The blocked report must state that both flows remain blocked.",
        )

        self.assertIn(
            "## dependency inventory",
            blocked_report,
            "The blocked report must inventory the dependency before its open items.",
        )
        self.assertLess(
            blocked_report.index("## dependency inventory"),
            blocked_report.index("## open items"),
            "The dependency inventory must precede the open items.",
        )
        self.assertIn(
            "| dependency | uses | target compatibility | classification | decision |",
            blocked_report,
            "The dependency inventory must record every required field.",
        )
        dependency_rows = [
            [cell.strip() for cell in row.strip("|").split("|")]
            for row in blocked_report_source.splitlines()
            if row.startswith("| `org.camunda.bpm:license-generator-fixture` |")
        ]
        self.assertEqual(
            len(dependency_rows),
            1,
            "The blocked report must contain one inventory row for the active dependency.",
        )
        dependency, uses, compatibility, classification, decision = dependency_rows[0]
        self.assertIn("licenseprovisioningservice.createlicense", uses)
        self.assertIn("licenseprovisioningservice.updatelicense", uses)
        self.assertIn("license type", uses)
        self.assertIn("membership", uses)
        self.assertIn("incompatible with the target runtime", compatibility)
        self.assertIn("active domain library", classification)
        self.assertIn("review signal", classification)
        self.assertIn("no project-owner-approved replacement", decision)
        self.assertIn("blocking/manual", decision)
        self.assertIn("do not report either flow as migrated", decision)
        self.assertEqual(
            dependency,
            "`org.camunda.bpm:license-generator-fixture`",
        )

        open_items = blocked_report_source.split("## open items", maxsplit=1)[1]
        for call_site in (
            "licenseprovisioningservice.createlicense",
            "licenseprovisioningservice.updatelicense",
        ):
            with self.subTest(call_site=call_site):
                rows = [
                    row for row in open_items.splitlines() if f"`{call_site}`" in row
                ]
                self.assertEqual(len(rows), 1, f"Expected one open item for {call_site}.")
                self.assertIn("`blocking/manual`", rows[0])


if __name__ == "__main__":
    unittest.main()
