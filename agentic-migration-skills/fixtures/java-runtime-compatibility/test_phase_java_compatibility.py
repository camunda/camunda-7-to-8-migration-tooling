from pathlib import Path
import unittest


REPO_ROOT = Path(__file__).resolve().parents[3]
SKILL_PATH = REPO_ROOT / "agentic-migration-skills/skills/migrate-c7-to-c8-code/SKILL.md"
MODEL_PATH = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/model-migration-approaches.md"
)
CODE_PATH = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/code-migration-approaches.md"
)
INTERVIEW_PATH = (
    REPO_ROOT
    / "agentic-migration-skills/skills/migrate-c7-to-c8-code/references/interview-questions.md"
)
README_PATH = REPO_ROOT / "agentic-migration-skills/README.md"


def normalized(path):
    return " ".join(path.read_text(encoding="utf-8").lower().split())


class PhaseJavaCompatibilityTest(unittest.TestCase):
    def test_each_phase_uses_its_own_java_range(self):
        skill = normalized(SKILL_PATH)
        java_requirements = skill.split("#### java runtime selection", maxsplit=1)[1].split(
            "**minimal, faithful change**", maxsplit=1
        )[0]

        self.assertIn("select java separately for each migration phase", skill)
        self.assertIn("m1 or e1, diagram converter cli", java_requirements)
        self.assertIn("java 21 or later. no upper bound applies", java_requirements)
        self.assertIn("approach a, openrewrite", java_requirements)
        self.assertIn("java 21-25 (`[21,26)`) or a narrower project range", java_requirements)
        self.assertIn("approach b, m2, m3, or assessment-only", java_requirements)
        self.assertIn("no java requirement for that selected path", java_requirements)
        self.assertIn("check any separate m1 or e1 phase independently", java_requirements)
        self.assertIn("do not use one java decision for both phases", java_requirements)

        code = normalized(CODE_PATH)
        self.assertIn("this java check applies only to openrewrite approach a", code)
        self.assertIn("it does not limit m1, e1, m2, or m3", code)

        interview = normalized(INTERVIEW_PATH)
        self.assertIn("preflight java separately for each selected phase", interview)
        self.assertIn("openrewrite uses java 21-25", interview)

    def test_m1_does_not_depend_on_repository_or_openrewrite_builds(self):
        skill = normalized(SKILL_PATH)
        self.assertIn("do not run or require a full repository build to preflight m1 or e1", skill)
        self.assertIn("record its phase and error separately", skill)
        self.assertIn("do not mark m1 or e1 blocked by that unrelated failure", skill)
        self.assertIn("record repository build failures separately from cli execution results", skill)

        model = normalized(MODEL_PATH)
        self.assertIn("do not run or require a repository build to preflight m1 or e1", model)
        self.assertIn("invoke the released cli directly", model)
        self.assertIn("if the java preflight passed and the cli exits nonzero", model)
        self.assertIn("without changing the java verdict", model)

    def test_m1_records_the_runtime_and_offers_alternatives(self):
        skill = normalized(SKILL_PATH)
        self.assertIn("run `-version` on that executable and record its actual major version", skill)
        self.assertIn("never edit shell profiles or global environment settings", skill)
        self.assertIn("record the applicable java range, executable, actual major", skill)
        self.assertIn("record its arguments, exit code, stdout, and stderr", skill)
        self.assertIn("unless the java launcher failed", skill)

        model = normalized(MODEL_PATH)
        for guidance in (skill, model):
            self.assertIn(
                "set `java_home` to the validated jdk home that contains the selected `bin/java` executable",
                guidance,
            )
            self.assertIn(
                "set `path` to `<java_home>/bin` followed by the existing `path`",
                guidance,
            )
            self.assertIn("on windows, use `<java_home>\\bin`", guidance)

        self.assertIn("apply both values only to that phase's process", skill)
        self.assertIn("apply both values only to the m1 or e1 process when needed", model)
        self.assertIn("if the probe fails or the output has no major version", model)
        self.assertIn("do not guess the java version", model)
        self.assertIn("record `java 21+ (no upper bound)`, the executable path, and the actual major", model)
        self.assertIn("issue #2424 records a successful release 0.3.6 conversion under java 26", model)
        self.assertIn("choose m2 (agentic ai), or choose m3 (online converter)", model)

    def test_json_option_boundary_matches_released_cli(self):
        model = normalized(MODEL_PATH)
        self.assertIn("release 0.3.6 does not support `--json`", model)
        self.assertIn("release 0.3.7 introduced this option", model)
        self.assertIn("classify this as a cli capability failure, not a java failure", model)

        readme = normalized(README_PATH)
        self.assertIn("the json report option requires cli release 0.3.7 or later", readme)
        self.assertIn("release 0.3.6 does not support `--json`", readme)


if __name__ == "__main__":
    unittest.main()
