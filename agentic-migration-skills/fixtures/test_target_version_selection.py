from pathlib import Path
import unittest


REPO_ROOT = Path(__file__).resolve().parents[1]
INTERVIEW_QUESTIONS = (
    REPO_ROOT
    / "skills/migrate-c7-to-c8-code/references/interview-questions.md"
)
SKILL = REPO_ROOT / "skills/migrate-c7-to-c8-code/SKILL.md"


class TargetVersionSelectionTest(unittest.TestCase):
    def test_latest_stable_target_is_the_default_and_earlier_targets_remain_available(self):
        questions = INTERVIEW_QUESTIONS.read_text(encoding="utf-8")
        target_question = questions.split(
            "## Question 2 - Target Camunda 8 Version", maxsplit=1
        )[1].split("## Question 3 - Migration Scope", maxsplit=1)[0]

        self.assertIn("- 8.10 (latest stable, default)", target_question)
        self.assertIn("- 8.9 (earlier stable)", target_question)
        self.assertIn("- 8.8 -", target_question)
        self.assertNotIn("- 8.10 (next version, not yet GA)", target_question)
        self.assertNotIn("- 8.9 (latest stable)", target_question)

    def test_noninteractive_runs_still_require_an_explicit_target_choice(self):
        skill = " ".join(SKILL.read_text(encoding="utf-8").split())

        self.assertIn(
            "Where the run is non-interactive, take each choice, including the Step 3 "
            "confirmation, only from an explicit statement in the request, the conversation, "
            "or the execution configuration.",
            skill,
        )
