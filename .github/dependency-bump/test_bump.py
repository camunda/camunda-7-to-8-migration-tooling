import json
import subprocess
import tempfile
import unittest
from pathlib import Path

import bump

POM = """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <properties>
    <version.mybatis>3.5.19</version.mybatis>
    <java.release>21</java.release>
  </properties>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.mybatis</groupId>
        <artifactId>mybatis</artifactId>
        <version>${version.mybatis}</version>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <version>2.0.1</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.0</version>
      </plugin>
    </plugins>
  </build>
</project>
"""

PIN = """      <dependency>
        <groupId>org.ow2.asm</groupId>
        <artifactId>asm</artifactId>
        <version>9.9</version>
      </dependency>
    </dependencies>
  </dependencyManagement>"""


class FakeVersions:
    """Numeric-only ordering, enough for these tests."""

    def compare_to(self, versions, target):
        key = lambda v: [int(x) for x in v.split(".")]
        return {v: (key(v) > key(target)) - (key(v) < key(target)) for v in versions}


def node(ga, version, *children):
    g, a = ga.split(":")
    return {"groupId": g, "artifactId": a, "version": version, "children": list(children)}


def write_module(root, path, modules=(), profile_modules=(), tree=None):
    d = root / path
    d.mkdir(parents=True, exist_ok=True)
    mods = "".join(f"<module>{m}</module>" for m in modules)
    prof = "".join(f"<module>{m}</module>" for m in profile_modules)
    (d / "pom.xml").write_text(
        f'<project xmlns="http://maven.apache.org/POM/4.0.0"><modules>{mods}</modules>'
        f"<profiles><profile><id>e2e</id><modules>{prof}</modules></profile></profiles></project>")
    if tree is not None:
        (d / bump.TREE_FILE).write_text(json.dumps(tree))


class GraphTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_versions_come_from_every_module_including_profile_modules(self):
        lib = node("org.mybatis:mybatis", "3.5.19")
        write_module(self.root, ".", modules=["core"], profile_modules=["qa"], tree=node("x:root", "1"))
        write_module(self.root, "core", tree=node("x:core", "1", node("x:a", "1", lib)))
        write_module(self.root, "qa", tree=node("x:qa", "1", node("org.mybatis:mybatis", "3.6.0")))

        summary = bump.summarize(self.root, "org.mybatis:mybatis")

        self.assertEqual(summary["reactorModules"], [".", "core", "qa"])
        self.assertEqual(summary["modules"]["core"]["versions"], ["3.5.19"])
        self.assertEqual(summary["modules"]["core"]["paths"], [["x:a:1", "org.mybatis:mybatis:3.5.19"]])
        self.assertEqual(summary["modules"]["qa"]["versions"], ["3.6.0"])

    def test_a_module_without_a_tree_fails_instead_of_counting_as_updated(self):
        write_module(self.root, ".", profile_modules=["qa"], tree=node("x:root", "1"))
        write_module(self.root, "qa")

        with self.assertRaisesRegex(bump.BumpError, "qa"):
            bump.summarize(self.root, "org.mybatis:mybatis")


class DecideTest(unittest.TestCase):
    def summary(self, **modules):
        return {"modules": {m: {"versions": vs} for m, vs in modules.items()}}

    def test_updated_only_when_every_module_is_at_or_above_target(self):
        self.assertEqual(bump.decide(self.summary(a=["3.6.0"], b=["3.7.1"]), "3.6.0", FakeVersions()),
                         {"state": "updated"})
        decision = bump.decide(self.summary(a=["3.6.0"], b=["3.5.15"]), "3.6.0", FakeVersions())
        self.assertEqual(decision, {"state": "below", "below": {"b": ["3.5.15"]}, "majorUpgrade": False})

    def test_absent_and_major_upgrade(self):
        self.assertEqual(bump.decide(self.summary(), "3.6.0", FakeVersions()), {"state": "absent"})
        self.assertTrue(bump.decide(self.summary(a=["2.9.0"]), "3.0.1", FakeVersions())["majorUpgrade"])

    def test_parses_maven_comparison_output(self):
        out = ("Display parameters as parsed by Maven ...:\n"
               "1. 3.5.19 -> 3.5.19; tokens: [3, 5, 19]\n   3.5.19 < 3.6.0\n"
               "2. 3.6.0 -> 3.6; tokens: [3, 6]\n   3.6.0 > 1.0.0-M1\n"
               "3. 1.0.0-M1 -> 1-milestone-1; tokens: [1, [milestone, [1]]]\n   1.0.0-M1 < 3.6.0\n"
               "4. 3.6.0 -> 3.6; tokens: [3, 6]\n")
        self.assertEqual(bump.MavenVersions.parse(out), [-1, 1, -1])


class CheckPomTest(unittest.TestCase):
    def check(self, new):
        return bump.check_pom(POM, new, "pom.xml")

    def test_version_property_and_dependency_version_may_change(self):
        new = POM.replace("<version.mybatis>3.5.19<", "<version.mybatis>3.6.0<").replace(
            "<version>2.0.1</version>", "<version>2.0.17</version>")
        self.assertEqual(self.check(new), [])

    def test_a_version_pin_may_be_added_to_dependency_management(self):
        new = POM.replace("    </dependencies>\n  </dependencyManagement>", PIN)
        self.assertEqual(self.check(new), [])

    def test_anything_else_is_rejected(self):
        rejected = {
            "plugin version": POM.replace("<version>3.5.0</version>", "<version>3.6.0</version>"),
            "non-version property": POM.replace("<java.release>21<", "<java.release>17<"),
            "property expression": POM.replace(">3.5.19<", ">${evil}<"),
            "added plugin": POM.replace("    </plugins>", "      <plugin><artifactId>x</artifactId></plugin>\n    </plugins>"),
            "removed dependency": POM.replace(POM[POM.index("  <dependencies>\n    <dependency>"):POM.index("  <build>")], ""),
            "pin outside dependencyManagement": POM.replace("    </dependency>\n  </dependencies>\n  <build>",
                                                            "    </dependency>\n" + PIN.split("    </dependencies>")[0].replace("      <", "    <") + "  </dependencies>\n  <build>"),
        }
        for name, new in rejected.items():
            with self.subTest(name):
                self.assertNotEqual(self.check(new), [])

    def test_only_existing_pom_files_may_change(self):
        self.assertEqual(bump.check_changes(["M\tcore/pom.xml"]), [])
        for line in ["A\tnew/pom.xml", "M\t.mvn/extensions.xml", "D\tpom.xml"]:
            with self.subTest(line):
                self.assertNotEqual(bump.check_changes([line]), [])
        self.assertNotEqual(bump.check_changes([]), [])


class CheckPatchTest(unittest.TestCase):
    def test_checks_the_staged_change_against_the_base_commit(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            run = lambda *a: subprocess.run(["git", "-C", tmp, *a], check=True, capture_output=True)
            run("init", "-q")
            (repo / "pom.xml").write_text(POM)
            run("add", ".")
            run("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "base")
            (repo / "pom.xml").write_text(POM.replace(">3.5.19<", ">3.6.0<"))
            run("add", ".")
            self.assertEqual(bump.check_patch(repo, "HEAD"), [])
            (repo / "pom.xml").write_text(POM.replace(">21<", ">17<"))
            self.assertEqual(bump.check_patch(repo, "HEAD"), [], "unstaged edits are not part of the change")
            run("add", ".")
            self.assertNotEqual(bump.check_patch(repo, "HEAD"), [])


class ProposalTest(unittest.TestCase):
    def test_keeps_only_pr_numbers_and_a_short_plain_reason(self):
        text = json.dumps({"pullRequests": [12, "13", True, -1, 14], "reason": "line\none\x1b[31m" + "x" * 400, "x": 1})
        proposal = bump.read_proposal(text)
        self.assertEqual(proposal["pullRequests"], [12, 14])
        self.assertTrue(proposal["reason"].startswith("line one[31m"))
        self.assertEqual(len(proposal["reason"]), 300)
        self.assertEqual(bump.read_proposal("not json"), {"pullRequests": [], "reason": ""})


if __name__ == "__main__":
    unittest.main()
