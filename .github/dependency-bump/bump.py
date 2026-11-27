#!/usr/bin/env python3
"""Helpers for the dependency bump workflow (.github/workflows/cve-bump.yml).

The resolved Maven dependency graph decides whether a branch reaches a version; nothing else does.
"""

import argparse
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

TREE_FILE = ".bump-tree.json"
# Every module that a profile gates must be in the graph, so these profiles are always active.
PROFILES = "integration,e2e"
DEPENDENCY_PLUGIN = "org.apache.maven.plugins:maven-dependency-plugin:3.8.1"
COORDINATE = re.compile(r"^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+$")
VERSION = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._+-]{0,127}$")
PROPERTY_REF = re.compile(r"^\$\{[A-Za-z0-9._-]+\}$")
MISSING_ARTIFACT = re.compile(r"Could not find artifact|was not found in")
MAX_PATHS_PER_MODULE = 3


class BumpError(Exception):
    pass


def local(tag):
    return tag.rsplit("}", 1)[-1]


def child(element, name):
    for c in element:
        if local(c.tag) == name:
            return c
    return None


def child_text(element, name):
    c = child(element, name)
    return (c.text or "").strip() if c is not None else ""


# Resolved graph


def reactor_modules(root):
    """Paths of every module a POM declares, in any profile, relative to root ('.' for root)."""
    found = []
    pending = [Path(".")]
    while pending:
        module = pending.pop()
        pom = root / module / "pom.xml"
        if not pom.is_file():
            continue
        found.append(module.as_posix())
        project = ET.parse(pom).getroot()
        sections = [project] + [p for p in project.iter() if local(p.tag) == "profile"]
        for section in sections:
            modules = child(section, "modules")
            for m in modules if modules is not None else []:
                if local(m.tag) == "module" and m.text:
                    pending.append(Path(os.path.normpath(module / m.text.strip())))
    return sorted(set(found))


def dependency_paths(node, coordinate, trail=()):
    """Yield (version, path) for every node of `coordinate` below the module's own root node."""
    for c in node.get("children", []):
        ga = f"{c['groupId']}:{c['artifactId']}"
        step = trail + (f"{ga}:{c['version']}",)
        if ga == coordinate:
            yield c["version"], list(step)
        yield from dependency_paths(c, coordinate, step)


def summarize(root, coordinate):
    """Versions of `coordinate` per module, read from the tree files Maven wrote."""
    modules = reactor_modules(root)
    missing = [m for m in modules if not (root / m / TREE_FILE).is_file()]
    if missing:
        raise BumpError("no dependency tree for module(s): " + ", ".join(missing))
    found = {}
    for module in modules:
        tree = json.loads((root / module / TREE_FILE).read_text())
        hits = list(dependency_paths(tree, coordinate))
        if hits:
            found[module] = {
                "versions": sorted({v for v, _ in hits}),
                "paths": [p for _, p in hits[:MAX_PATHS_PER_MODULE]],
            }
    return {"modules": found, "reactorModules": modules}


class MavenVersions:
    """Maven's own version ordering, from the maven-artifact jar of the Maven on PATH."""

    COMPARISON = re.compile(r"^\s+(\S+) (<|==|>) (\S+)$")

    def __init__(self, jar=None):
        self.jar = jar or self._find_jar()
        self.cache = {}

    @staticmethod
    def _find_jar():
        out = subprocess.run(["mvn", "--version"], capture_output=True, text=True, check=True).stdout
        home = re.search(r"^Maven home: (.+)$", out, re.M)
        jars = sorted(Path(home.group(1).strip(), "lib").glob("maven-artifact-*.jar")) if home else []
        if not jars:
            raise BumpError("maven-artifact jar not found in the Maven installation")
        return str(jars[0])

    @classmethod
    def parse(cls, output):
        """Comparison results from the jar's output, one per consecutive argument pair."""
        signs = {"<": -1, "==": 0, ">": 1}
        return [signs[m.group(2)] for m in map(cls.COMPARISON.match, output.splitlines()) if m]

    def compare_to(self, versions, target):
        """Map each version to -1, 0 or 1 relative to target."""
        todo = [v for v in versions if (v, target) not in self.cache]
        if todo:
            args = [x for v in todo for x in (v, target)]
            out = subprocess.run(["java", "-jar", self.jar, *args], capture_output=True, text=True, check=True).stdout
            # Pairs alternate (version, target) and (target, next version); keep the first kind.
            results = self.parse(out)[::2]
            if len(results) != len(todo):
                raise BumpError("unexpected output from Maven's version comparison")
            self.cache.update({(v, target): r for v, r in zip(todo, results)})
        return {v: self.cache[(v, target)] for v in versions}


def major(version):
    m = re.match(r"\d+", version)
    return int(m.group()) if m else None


def decide(summary, target, versions):
    """updated, absent, or below with the versions under target per module."""
    found = summary["modules"]
    if not found:
        return {"state": "absent"}
    all_versions = sorted({v for m in found.values() for v in m["versions"]})
    order = versions.compare_to(all_versions, target)
    below = {mod: [v for v in m["versions"] if order[v] < 0] for mod, m in found.items()}
    below = {mod: vs for mod, vs in below.items() if vs}
    if not below:
        return {"state": "updated"}
    target_major = major(target)
    lowest = {v for vs in below.values() for v in vs}
    jump = target_major is not None and any(major(v) is not None and major(v) < target_major for v in lowest)
    return {"state": "below", "below": below, "majorUpgrade": jump}


def run_maven_tree(root, log):
    """Write a tree file per module. Returns 0, 3 when an artifact doesn't exist, else 2."""
    for stale in root.rglob(TREE_FILE):
        stale.unlink()
    # Maven needs no tokens, so it gets a minimal environment.
    env = {k: os.environ[k] for k in ("PATH", "HOME", "JAVA_HOME", "LANG", "TMPDIR") if k in os.environ}
    cmd = ["mvn", "-B", "--no-transfer-progress", f"-P{PROFILES}", "-DskipTests",
           "-DoutputType=json", f"-DoutputFile={TREE_FILE}", f"{DEPENDENCY_PLUGIN}:tree"]
    with open(log, "w") as out:
        rc = subprocess.run(cmd, cwd=root, env=env, stdout=out, stderr=subprocess.STDOUT).returncode
    if rc == 0:
        return 0
    text = Path(log).read_text(errors="replace")
    print("\n".join(text.splitlines()[-40:]), file=sys.stderr)
    return 3 if MISSING_ARTIFACT.search(text) else 2


# Version-only change check


def element_key(element):
    name = local(element.tag)
    if name in ("dependency", "plugin", "exclusion", "extension"):
        return (name, child_text(element, "groupId"), child_text(element, "artifactId"))
    if name == "profile":
        return (name, child_text(element, "id"))
    return (name,)


def is_added_pin(element, lineage):
    """A new <dependency> with only coordinates and a version, in a dependencyManagement section."""
    if local(element.tag) != "dependency" or len(lineage) < 2:
        return False
    if local(lineage[-1].tag) != "dependencies" or local(lineage[-2].tag) != "dependencyManagement":
        return False
    if sorted(local(c.tag) for c in element) != ["artifactId", "groupId", "version"]:
        return False
    coordinate = f"{child_text(element, 'groupId')}:{child_text(element, 'artifactId')}"
    version = child_text(element, "version")
    return bool(COORDINATE.match(coordinate)) and bool(VERSION.match(version) or PROPERTY_REF.match(version))


def is_version_property(element, lineage):
    return bool(lineage) and local(lineage[-1].tag) == "properties" and "version" in local(element.tag).lower()


def is_added_property(element, lineage):
    return is_version_property(element, lineage) and len(element) == 0 and bool(VERSION.match((element.text or "").strip()))


def version_text_allowed(element, lineage):
    """Text that may change: a dependency's <version>, or a version property."""
    if local(element.tag) == "version":
        return bool(lineage) and local(lineage[-1].tag) == "dependency"
    return is_version_property(element, lineage)


def compare_elements(old, new, lineage, errors, where):
    here = "/".join([local(p.tag) for p in lineage] + [local(new.tag)])
    if old.tag != new.tag or old.attrib != new.attrib:
        errors.append(f"{where}: {here} changed")
        return
    old_text, new_text = (old.text or "").strip(), (new.text or "").strip()
    if old_text != new_text and not (version_text_allowed(new, lineage) and VERSION.match(new_text) and len(new) == 0):
        errors.append(f"{where}: {here} changed from '{old_text}' to '{new_text}'")
    lineage = lineage + [new]
    old_children = list(old)
    i = 0
    for c in new:
        if i < len(old_children) and element_key(old_children[i]) == element_key(c):
            compare_elements(old_children[i], c, lineage, errors, where)
            i += 1
        elif not (is_added_pin(c, lineage) or is_added_property(c, lineage)):
            errors.append(f"{where}: {here}/{local(c.tag)} added or moved")
            return
    if i < len(old_children):
        errors.append(f"{where}: {here}/{local(old_children[i].tag)} removed")


def check_pom(old_xml, new_xml, where):
    """Errors for anything in new_xml beyond changed versions and added version pins."""
    try:
        old, new = ET.fromstring(old_xml), ET.fromstring(new_xml)
    except ET.ParseError as e:
        return [f"{where}: not valid XML ({e})"]
    errors = []
    compare_elements(old, new, [], errors, where)
    return errors


def check_changes(name_status):
    """Errors for changes that aren't modifications of existing pom.xml files."""
    if not name_status:
        return ["the change is empty"]
    errors = []
    for line in name_status:
        status, _, path = line.partition("\t")
        if status != "M" or Path(path).name != "pom.xml":
            errors.append(f"{path}: only existing pom.xml files may change (status {status})")
    return errors


def git(repo, *args):
    return subprocess.run(["git", "-C", str(repo), *args], capture_output=True, text=True, check=True).stdout


def check_patch(repo, base):
    """Validate the staged change of `repo` against commit `base`."""
    name_status = [l for l in git(repo, "diff", "--cached", "--no-renames", "--name-status", base).splitlines() if l]
    errors = check_changes(name_status)
    if errors:
        return errors
    for line in name_status:
        path = line.split("\t", 1)[1]
        errors += check_pom(git(repo, "show", f"{base}:{path}"), git(repo, "show", f":{path}"), path)
    return errors


# Proposal and PR text


def read_proposal(text):
    """The model's result reduced to PR numbers and a short single-line reason."""
    try:
        data = json.loads(text)
    except ValueError:
        data = None
    if not isinstance(data, dict):
        data = {}
    prs = data.get("pullRequests")
    prs = [n for n in prs if type(n) is int and n > 0][:5] if isinstance(prs, list) else []
    reason = data.get("reason")
    reason = "".join(ch for ch in " ".join(reason.split()) if ch.isprintable())[:300] if isinstance(reason, str) else ""
    return {"pullRequests": prs, "reason": reason}


def pr_body(coordinate, target, branch, before, after, run_url):
    lines = [
        f"Brings `{coordinate}` to `{target}` or later on `{branch}`.",
        "",
        "Resolved versions per module (`mvn dependency:tree`):",
        "",
        "| Module | Before | After |",
        "| --- | --- | --- |",
    ]
    for m in sorted(set(before["modules"]) | set(after["modules"])):
        b = ", ".join(before["modules"].get(m, {}).get("versions", [])) or "-"
        a = ", ".join(after["modules"].get(m, {}).get("versions", [])) or "-"
        lines.append(f"| `{m}` | {b} | {a} |")
    lines += ["", f"Opened by the dependency bump workflow: {run_url}"]
    return "\n".join(lines) + "\n"


# Command line


def cmd_resolve(args):
    root = Path(args.repo)
    rc = run_maven_tree(root, args.log)
    if rc:
        return rc
    summary = summarize(root, args.dependency)
    summary["decision"] = decide(summary, args.target, MavenVersions())
    Path(args.out).write_text(json.dumps(summary, indent=2))
    for module, m in sorted(summary["modules"].items()):
        print(f"{module}: {', '.join(m['versions'])}")
    print(f"decision: {summary['decision']['state']}")
    return 0


def cmd_check_patch(args):
    errors = check_patch(Path(args.repo), args.base)
    for e in errors:
        print(e)
    return 1 if errors else 0


def cmd_proposal(args):
    path = Path(args.file)
    print(json.dumps(read_proposal(path.read_text() if path.is_file() else "")))
    return 0


def cmd_pr_body(args):
    before, after = (json.loads(Path(p).read_text()) for p in (args.before, args.after))
    sys.stdout.write(pr_body(args.dependency, args.target, args.branch, before, after, args.run_url))
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("resolve", help="resolve the graph and decide; exit 3 when an artifact doesn't exist")
    for name in ("repo", "dependency", "target", "out", "log"):
        p.add_argument(f"--{name}", required=True)
    p.set_defaults(func=cmd_resolve)

    p = sub.add_parser("check-patch", help="fail unless the staged change only changes versions")
    p.add_argument("--repo", required=True)
    p.add_argument("--base", required=True)
    p.set_defaults(func=cmd_check_patch)

    p = sub.add_parser("proposal", help="read the model's result")
    p.add_argument("--file", required=True)
    p.set_defaults(func=cmd_proposal)

    p = sub.add_parser("pr-body", help="describe a verified change")
    for name in ("dependency", "target", "branch", "before", "after", "run-url"):
        p.add_argument(f"--{name}", required=True)
    p.set_defaults(func=cmd_pr_body)

    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except BumpError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
