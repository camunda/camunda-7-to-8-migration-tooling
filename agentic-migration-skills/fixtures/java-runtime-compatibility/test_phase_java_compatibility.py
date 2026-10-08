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
FIXTURE_README_PATH = Path(__file__).resolve().parent / "README.md"


def normalized(path):
    return " ".join(path.read_text(encoding="utf-8").lower().split())


class PhaseJavaCompatibilityTest(unittest.TestCase):
    def test_each_phase_uses_its_own_java_range(self):
        skill = normalized(SKILL_PATH)
        java_requirements = skill.split("#### java runtime selection", maxsplit=1)[1].split(
            "### step 2: assessment", maxsplit=1
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
        self.assertIn("recipes require java 21-25", interview)
        self.assertIn("java runtime table in `skill.md`", interview)

    def test_m1_does_not_depend_on_repository_or_openrewrite_builds(self):
        skill = normalized(SKILL_PATH)
        self.assertIn("do not run or require a full repository build to preflight m1 or e1", skill)
        self.assertIn("record its phase and error separately", skill)
        self.assertIn("do not mark m1 or e1 blocked by that unrelated failure", skill)
        self.assertIn("record repository build failures separately from cli execution results", skill)

        self.assertIn("invoke the released cli directly", skill)

        model = normalized(MODEL_PATH)
        self.assertIn(
            "validate the cli runtime with the java runtime procedure in `skill.md`", model
        )
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
        self.assertIn(
            "set `path` to `<java_home>/bin` followed by the existing `path`",
            skill,
        )
        self.assertIn("on windows, use `<java_home>\\bin`", skill)

        self.assertIn("apply both values only to that phase's process", skill)
        self.assertIn("if the probe fails or the output has no major version", skill)
        self.assertIn("do not guess the java version", skill)
        self.assertIn("use the validated absolute executable for every cli invocation", model)
        self.assertIn("record `java 21+ (no upper bound)`, the executable path, and the actual major", model)
        self.assertIn("choose m2 (agentic ai), or choose m3 (online converter)", model)
        self.assertIn(
            "issue #2424 records a successful release 0.3.6 conversion under java 26",
            normalized(FIXTURE_README_PATH),
        )

    def test_json_option_boundary_matches_released_cli(self):
        model = normalized(MODEL_PATH)
        self.assertIn("release 0.3.6 does not support `--json`", model)
        self.assertIn("release 0.3.7 introduced this option", model)
        self.assertIn("classify this as a cli capability failure, not a java failure", model)

        readme = normalized(README_PATH)
        self.assertIn("the json report option requires cli release 0.3.7 or later", readme)
        self.assertIn("release 0.3.6 does not support `--json`", readme)

    def test_java_phase_environment_uses_a_verified_runtime_home(self):
        guidance = normalized(SKILL_PATH)
        self.assertIn(
            "read the `java.home` property from the selected executable's "
            "`-xshowsettings:properties -version` output",
            guidance,
        )
        self.assertIn("use that property value as the candidate `java_home`", guidance)
        self.assertIn("never derive it from the executable path", guidance)
        self.assertIn(
            "before setting the phase environment, run `<java_home>/bin/java -version` "
            "and confirm that it reports the same major as the selected executable",
            guidance,
        )
        self.assertIn(
            "if the property is missing or its `bin/java` is missing or reports a "
            "different major, ask for another jdk",
            guidance,
        )
        self.assertIn("set `java_home` to the validated property value", guidance)
        self.assertIn("on windows, use `<java_home>\\bin\\java.exe`", guidance)
        for path in (MODEL_PATH, CODE_PATH):
            with self.subTest(path=path):
                phase = normalized(path)
                self.assertIn("the java runtime procedure in `skill.md`", phase)
                self.assertNotIn("use that property value as the candidate `java_home`", phase)


if __name__ == "__main__":
    unittest.main()
