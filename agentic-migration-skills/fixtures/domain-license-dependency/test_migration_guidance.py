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
            "blocked" in dependencies and "manual follow-up" in dependencies,
            "The dependency catalog must require a blocked status with manual follow-up.",
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
            "both license flows incomplete" in blocked_report,
            "The blocked report must state that both flows remain incomplete.",
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
        self.assertIn("blocked", decision)
        self.assertIn("manual follow-up", decision)
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
                self.assertIn("project owner must approve", rows[0])
                self.assertIn("`blocked`", rows[0])

    def test_unconfirmed_compatibility_blocks_migration(self):
        skill = " ".join(SKILL_PATH.read_text().lower().split())
        checklist = " ".join(CHECKLIST_PATH.read_text().lower().split())
        dependencies = " ".join(DEPENDENCIES_PATH.read_text().lower().split())
        unknown_compatibility = dependencies.split(
            "| active library with unknown target compatibility", maxsplit=1
        )[1].split("| active library is incompatible", maxsplit=1)[0]

        self.assertIn("if compatibility remains unconfirmed", unknown_compatibility)
        self.assertIn("leave the active code unchanged", unknown_compatibility)
        self.assertIn("record each affected call site as `blocked`", unknown_compatibility)
        self.assertIn("manual follow-up", unknown_compatibility)
        self.assertIn("do not report those flows as migrated", unknown_compatibility)
        self.assertIn("record each affected call site as `blocked`", skill)
        self.assertIn("if target compatibility remains unconfirmed", checklist)

    def test_blocked_report_uses_the_skill_status_contract(self):
        skill = " ".join(SKILL_PATH.read_text().lower().split())
        checklist = " ".join(CHECKLIST_PATH.read_text().lower().split())
        report = " ".join(BLOCKED_REPORT_PATH.read_text().lower().split())

        self.assertIn(
            "set each open item to status `open`, `blocked`, or `resolved`",
            skill,
        )
        self.assertIn("set each query follow-up to status `open`", checklist)
        self.assertIn("no item has `deferred` or `blocked` status", skill)
        self.assertIn("an `open` item is a team decision", skill)
        self.assertIn(
            "it does not block completion unless it prevents an in-scope documentation change "
            "or a required readiness check",
            skill,
        )
        self.assertIn("the summary always lists every open item", skill)
        self.assertIn("| call site | manual follow-up | status |", report)
        self.assertNotIn("`blocking/manual`", report)


if __name__ == "__main__":
    unittest.main()
