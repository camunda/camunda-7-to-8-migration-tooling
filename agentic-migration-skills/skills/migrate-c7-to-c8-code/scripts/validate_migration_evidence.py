#!/usr/bin/env python3
"""Capture migration checks and generate a fail-closed validation gate."""

import argparse
import hashlib
import html
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path, PureWindowsPath
from uuid import uuid4


VALIDATION = Path(".camunda-migration/validation")
INVENTORY = VALIDATION / "step2-inventory.json"
EVIDENCE = VALIDATION / "validation-evidence.json"
LOGS = VALIDATION / "logs"
SUMMARY = VALIDATION / "validation-summary.json"
TEST_MAPPING = VALIDATION / "test-mapping.json"
REPORT = Path("MIGRATION_REPORT.md")
BPMN = "{http://www.omg.org/spec/BPMN/20100524/MODEL}"
ZEEBE = "{http://camunda.org/schema/zeebe/1.0}"
ASSERTIONS = (
    "user_task_type",
    "downstream_message_instance",
    "branch_selection",
    "worker_input_output",
    "incident_behavior",
    "form_resolution",
)
GATE_HEADING = "## Aggregate validation gate"
TEST_PARITY_HEADING = "## Test Parity"
TEST_COVERAGE_HEADING = "## Test Coverage"
RUNTIME_CHECKS = ("spring_boot_run", "executable_jar", "external_launcher")
SAFE_ENVIRONMENTS = ("local", "non-production")
SKIP_SOURCE_DIRS = {".camunda-migration", ".claude", ".git", ".gradle",
                    ".venv", ".worktree", ".worktrees", "__pycache__",
                    "build", "dist", "node_modules", "target"}
CODE_SUFFIXES = {".cjs", ".cts", ".groovy", ".http", ".java", ".js", ".jsx",
                 ".kt", ".kts", ".mjs", ".mts", ".scala", ".ts", ".tsx"}
NESTED_BLOCK_COMMENT_SUFFIXES = {".kt", ".kts", ".scala"}
DUE_DATE_HINT = re.compile(r"""\bsetJobDuedate\b|/duedate\b|['"`]duedate['"`]""", re.I)
TARGET_VERSION = re.compile(r"8\.\d+\.\d+\Z")
TEST_RESULTS = {"passed", "failed", "skipped", "error"}
TEST_STATUSES = {"migrated", "retired", "manual", "added"}
DEFAULT_JUNIT_REPORTS = (
    "target/surefire-reports/TEST-*.xml",
    "target/failsafe-reports/TEST-*.xml",
    "build/test-results/**/*.xml",
)
DEFAULT_C7_COVERAGE_REPORTS = (
    "target/process-test-coverage/**/report.json",
    "target/process_test_coverage/**/*.json",
)
DEFAULT_CPT_COVERAGE_REPORTS = ("target/coverage-report/report.json",)
TEST_EXECUTION_KINDS = {"tests", "process_path"}
TEST_LEDGER_CHECK_KINDS = {
    "test_freeze",
    "test_repeat",
    "test_parity",
    "coverage_parity",
    "assertion_strength",
    "mock_boundary",
}
TEST_RUN_MODES = {"run", "migrate_only"}
QUESTION_8_DECLINE_REASON = "declined by user (Question 8)"
TEST_SOURCE_COMPILE_GOALS = {"test-compile", "testClasses"}


@dataclass
class ValidationPlan:
    required: dict
    allowed: set
    timers: dict
    timer_starts: dict
    docker_suites: dict
    model_sets: dict
    duplicates: dict
    update_hits: dict
    source_update_locations: dict
    active_timer_locations: dict
    active_timer_decisions: dict
    source_digest: str
    issues: list
    test_contract: dict
    converted_elements: dict
    source_ids: dict
    source_job_types_by_model: dict


class EvidenceError(ValueError):
    pass


def project_path(root, value, label, must_exist=False):
    if (
        not isinstance(value, str)
        or not value
        or "\\" in value
        or Path(value).is_absolute()
        or PureWindowsPath(value).drive
        or ".." in Path(value).parts
    ):
        raise EvidenceError(f"{label} must be a project-relative path: {value!r}")
    try:
        path = (root / value).resolve(strict=must_exist)
        path.relative_to(root)
    except (OSError, RuntimeError, ValueError) as exc:
        raise EvidenceError(f"{label} is missing or outside the project: {value!r}") from exc
    return path


def read_json(path):
    try:
        with path.open(encoding="utf-8") as source:
            value = json.load(source)
    except (OSError, json.JSONDecodeError) as exc:
        raise EvidenceError(f"Cannot read {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise EvidenceError(f"{path} must contain a JSON object")
    return value


def ensure_directory_path(root, path, label):
    try:
        root = root.resolve(strict=True)
        relative = path.relative_to(root)
        if ".." in relative.parts:
            raise ValueError(f"{path} contains a parent traversal")
    except (OSError, RuntimeError, ValueError) as exc:
        raise EvidenceError(f"{label} is outside its allowed root: {path}") from exc

    current = root
    for component in relative.parts:
        current = current / component
        if current.is_symlink():
            raise EvidenceError(f"Refusing symlinked {label}: {current}")
        try:
            current.mkdir(exist_ok=True)
        except OSError as exc:
            raise EvidenceError(f"Cannot create {label}: {current}") from exc
        if current.is_symlink():
            raise EvidenceError(f"Refusing symlinked {label}: {current}")
        try:
            resolved = current.resolve(strict=True)
            resolved.relative_to(root)
        except (OSError, RuntimeError, ValueError) as exc:
            raise EvidenceError(f"{label} is outside its allowed root: {current}") from exc
        if not resolved.is_dir():
            raise EvidenceError(f"{label} is not a directory: {current}")
        current = resolved
    return current


def write_file(root, name, content):
    root = root.resolve(strict=True)
    path = root / name
    parent = ensure_directory_path(root, path.parent, "output directory")
    path = parent / path.name
    if path.is_symlink():
        raise EvidenceError(f"Refusing to replace symlink: {path}")
    output = tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", dir=path.parent, delete=False
    )
    try:
        with output:
            output.write(content)
        os.replace(output.name, path)
    finally:
        Path(output.name).unlink(missing_ok=True)


def write_json(root, name, value):
    write_file(root, name, json.dumps(value, indent=2, ensure_ascii=False) + "\n")


def strings(values, label):
    if (
        not isinstance(values, list)
        or any(not isinstance(item, str) or not item for item in values)
        or len(values) != len(set(values))
    ):
        raise EvidenceError(f"{label} must contain distinct, non-empty strings")
    return values


def read_test_run_mode(inventory):
    if "test_run_mode" not in inventory:
        return None
    mode = inventory["test_run_mode"]
    if not isinstance(mode, str) or mode not in TEST_RUN_MODES:
        raise EvidenceError("Step 2 test_run_mode must be 'run' or 'migrate_only'")
    return mode


def compiles_test_sources(command):
    goals = {argument.rsplit(":", 1)[-1] for argument in command[1:]}
    skips = any(
        argument.removeprefix("-D").startswith("maven.test.skip")
        and argument.removeprefix("-D") != "maven.test.skip=false"
        for argument in command[1:]
    )
    return bool(goals & TEST_SOURCE_COMPILE_GOALS) and not skips


def concrete_reference(value):
    if not isinstance(value, str) or not value.strip():
        return False
    normalized = re.sub(r"[^a-z0-9]+", "-", value.casefold()).strip("-")
    return bool(normalized) and normalized not in {
        "approved", "n-a", "none", "not-applicable", "not-approved",
        "not-reviewed", "not-run", "not-verified", "notapplicable",
        "pending", "tbd", "todo", "unknown", "unresolved",
    } and not normalized.startswith(("not-applicable-", "pending-", "unknown-"))


def file_digest(path):
    try:
        return hashlib.sha256(path.read_bytes()).hexdigest()
    except OSError as exc:
        raise EvidenceError(f"Cannot read migration input {path}: {exc}") from exc


def add_existing_source_file_hash(root, value, label, hashes, *, must_exist=False):
    path = project_path(root, value, label, must_exist=must_exist)
    candidate = root
    for part in Path(value).parts:
        candidate = candidate / part
        if candidate.is_symlink():
            raise EvidenceError(f"Cannot scan symlinked source file: {candidate}")
    if not path.exists():
        return
    if not path.is_file():
        raise EvidenceError(f"{label} is not a regular file: {value}")
    hashes[Path(value).as_posix()] = file_digest(path)


def collect_source_files(
    root, modules, models, test_contract=None, *, require_inventory_files=False
):
    hashes = {}
    module_paths = set(modules)
    for module in modules:
        scan_module(root, module, module_paths, hashes)
    if test_contract is not None and test_contract["mode"] in TEST_RUN_MODES:
        module_paths = set(test_contract["modules"])
        for test in test_contract["tests"]:
            add_existing_source_file_hash(
                root,
                test["file"],
                "Test Inventory file",
                hashes,
                must_exist=require_inventory_files,
            )
        for suite in test_contract["suites"].values():
            module = suite["module"]
            for root_type in ("test_source_roots", "test_resource_roots"):
                for value in suite[root_type]:
                    path = project_path(
                        root, value, f"{module} {suite['name']} {root_type}"
                    )
                    if not path.exists():
                        continue
                    if not path.is_dir():
                        raise EvidenceError(
                            f"{module} {suite['name']} {root_type} is not a directory: {value}"
                        )
                    scan_test_root(root, module, module_paths, path, hashes)
    for model in models:
        path = project_path(root, model, "source model", must_exist=True)
        if path.is_symlink() or not path.is_file():
            raise EvidenceError(f"Source model is not a regular file: {model}")
        hashes[Path(model).as_posix()] = file_digest(path)
        converted_model = Path(model).with_name(f"converted-c8-{Path(model).name}")
        converted_candidate = root / converted_model
        if converted_candidate.is_symlink():
            raise EvidenceError(f"Converted copy is not a regular file: {converted_model}")
        if converted_candidate.exists():
            if not converted_candidate.is_file():
                raise EvidenceError(f"Converted copy is not a regular file: {converted_model}")
            converted_path = project_path(
                root, converted_model.as_posix(), "converted copy", must_exist=True
            )
            hashes[converted_model.as_posix()] = file_digest(converted_path)
    for name in (
        "pom.xml",
        "build.gradle",
        "build.gradle.kts",
        "settings.gradle",
        "settings.gradle.kts",
        "gradle.properties",
    ):
        path = root / name
        if path.is_symlink():
            raise EvidenceError(f"Cannot scan symlinked build configuration: {path}")
        if path.is_file():
            hashes[name] = file_digest(path)
    return hashes


def source_test_contract(test_contract):
    return test_contract_snapshot(test_contract, include_modules=True)


def source_test_contract_matches_snapshot(current, snapshot):
    """Only test_run_mode may change after Step 2, and only from migrate_only to run."""
    if not isinstance(current, dict) or not isinstance(snapshot, dict):
        return current == snapshot
    if current.get("mode") != snapshot.get("mode") and (
        snapshot.get("mode"), current.get("mode")
    ) != ("migrate_only", "run"):
        return False
    return dict(current, mode=None) == dict(snapshot, mode=None)


def test_suite_snapshot(suite):
    snapshot = {
        "module": suite["module"],
        "name": suite["name"],
        "command": suite["command"],
        "test_ids": suite["test_ids"],
        "reports": suite["reports"],
        "coverage_reports": suite["coverage_reports"],
    }
    for root_type in ("test_source_roots", "test_resource_roots"):
        roots = suite.get(root_type, [])
        if roots:
            snapshot[root_type] = roots
    return snapshot


def test_contract_snapshot(test_contract, *, include_modules):
    if test_contract is None:
        return None
    snapshot = {
        "mode": test_contract["mode"],
        "tests": test_contract["tests"],
    }
    if include_modules:
        snapshot["modules"] = test_contract["modules"]
    snapshot["suites"] = [
        test_suite_snapshot(suite) for suite in test_contract["suites"].values()
    ]
    return snapshot


def source_snapshot_digest(modules, models, files, test_contract=None):
    snapshot = {
        "modules": modules,
        "models": models,
        "files": files,
        "test_contract": test_contract,
    }
    # Keep the schema-v1 persisted digest encoding while pinning its default spacing.
    return hashlib.sha256(
        json.dumps(snapshot, sort_keys=True, separators=(", ", ": ")).encode("utf-8")
    ).hexdigest()


def git_head(root):
    try:
        completed = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False,
        )
    except OSError as exc:
        if exc.errno == 2:
            return None
        raise EvidenceError(f"Cannot read the source commit: {exc}") from exc
    return completed.stdout.strip() if completed.returncode == 0 else None


def initialize(root, reset_source_snapshot=False):
    inventory = read_json(root / INVENTORY)
    if inventory.get("schema_version") != 1:
        raise EvidenceError("Unsupported Step 2 inventory version")
    read_test_run_mode(inventory)
    modules = strings(inventory.get("modules"), "Step 2 modules")
    models = strings(inventory.get("models"), "Step 2 models")
    if not (modules or models):
        raise EvidenceError("A migration run needs at least one module or model")
    for path in modules + models:
        project_path(root, path, "Step 2 scope")
    if not reset_source_snapshot and "source_updates" in inventory:
        snapshot = inventory["source_updates"]
        if not isinstance(snapshot, dict) or set(snapshot) != set(modules):
            raise EvidenceError(
                "Pre-migration source snapshot differs from the Step 2 scope; "
                "restore the C7 baseline before init --reset-source-snapshot"
            )
    elif not reset_source_snapshot and "run_id" in inventory:
        raise EvidenceError(
            "Pre-migration source snapshot is missing; "
            "restore the C7 baseline before init --reset-source-snapshot"
        )
    else:
        inventory["source_updates"] = {
            module: scan_module(root, module, set(modules), {}) for module in modules
        }
    if reset_source_snapshot or "source_files" not in inventory:
        if (
            not reset_source_snapshot
            and "run_id" in inventory
            and inventory.get("test_run_mode") == "run"
        ):
            raise EvidenceError(
                "The C7 test baseline cannot be captured after this migration run started; "
                "restore the C7 baseline before init --reset-source-snapshot"
            )
        current_test_contract = (
            test_contract(root, inventory) if "test_run_mode" in inventory else None
        )
        source_files = collect_source_files(
            root,
            modules,
            models,
            current_test_contract,
            require_inventory_files=True,
        )
        test_contract_snapshot = (
            source_test_contract(current_test_contract)
            if current_test_contract is not None
            else None
        )
        inventory["source_files"] = source_files
        inventory["source_snapshot_test_contract"] = test_contract_snapshot
        inventory["source_snapshot_sha256"] = source_snapshot_digest(
            modules, models, source_files, test_contract_snapshot
        )
        inventory["source_snapshot_commit"] = git_head(root)
    elif (
        not isinstance(inventory["source_files"], dict)
        or not isinstance(inventory.get("source_snapshot_sha256"), str)
        or not inventory["source_snapshot_sha256"]
    ):
        raise EvidenceError("Step 2 inventory has an invalid source file snapshot")
    elif (
        inventory.get("test_run_mode") == "run"
        and "source_snapshot_test_contract" not in inventory
    ):
        raise EvidenceError(
            "Step 2 inventory lacks the test contract source snapshot; "
            "restore the C7 baseline before init --reset-source-snapshot"
        )
    elif "test_run_mode" in inventory:
        validate_source_snapshot_test_contract(root, inventory)
    inventory["run_id"] = uuid4().hex
    write_json(root, INVENTORY, inventory)
    if reset_source_snapshot and (root / TEST_MAPPING).exists():
        write_json(
            root,
            TEST_MAPPING,
            {
                "schema_version": 1,
                "baseline": {},
                "tests": [],
                "freeze": {"files": {}},
                "test_changes": [],
                "mock_changes": [],
            },
        )
    evidence_path = root / EVIDENCE
    if evidence_path.exists():
        evidence = read_json(evidence_path)
        evidence["checks"] = []
        write_json(root, EVIDENCE, evidence)
    print("Started a new migration validation run")
    report(root)
    return 0


def scope(root, evidence):
    inventory = read_json(root / INVENTORY)
    if inventory.get("schema_version") != 1 or evidence.get("schema_version") != 1:
        raise EvidenceError("Unsupported inventory or evidence version")
    read_test_run_mode(inventory)
    if not isinstance(inventory.get("run_id"), str) or not inventory["run_id"]:
        raise EvidenceError("Initialize a migration validation run before recording checks")
    modules = evidence.get("modules")
    models = evidence.get("models")
    if not isinstance(modules, list) or not isinstance(models, list) or not (modules or models):
        raise EvidenceError("Evidence must list at least one module or model")
    for kind, entries, key in (
        ("module", modules, "path"),
        ("model", models, "source_path"),
    ):
        original = strings(inventory.get(f"{kind}s"), f"Step 2 {kind}s")
        for path in original:
            project_path(root, path, f"Step 2 {kind}")
        if any(not isinstance(entry, dict) for entry in entries):
            raise EvidenceError(f"Every {kind} must be an object")
        declared = strings([entry.get(key) for entry in entries], f"evidence {kind}s")
        if set(original) != set(declared):
            raise EvidenceError(f"Evidence {kind}s differ from the confirmed Step 2 scope")
        resolved = [project_path(root, path, kind) for path in declared]
        if len(resolved) != len(set(resolved)):
            raise EvidenceError(f"Duplicate {kind} paths resolve to the same location")
    if not isinstance(evidence.get("checks"), list):
        raise EvidenceError("Evidence checks must be an array")
    return modules, models


def model_type(path):
    name = path.lower()
    if name.endswith((".bpmn", ".bpmn20.xml")):
        return "bpmn"
    if name.endswith((".dmn", ".dmn11.xml")):
        return "dmn"
    raise EvidenceError(f"Unsupported model: {path}")


def read_model(root, source, converted):
    kind = model_type(source)
    if kind != model_type(converted):
        raise EvidenceError(f"Source and converted model types differ: {converted}")
    original = project_path(root, source, "source model", must_exist=True)
    copy = project_path(root, converted, "converted copy", must_exist=True)
    if not original.is_file() or not copy.is_file() or original.samefile(copy):
        raise EvidenceError(f"Missing or overwritten source model: {source}")
    try:
        source_document = ET.parse(original).getroot()
        document = ET.parse(copy).getroot()
    except (ET.ParseError, OSError) as exc:
        raise EvidenceError(f"Invalid model XML in {source} or {converted}: {exc}") from exc
    if kind == "bpmn":
        if source_document.tag != f"{BPMN}definitions" or document.tag != f"{BPMN}definitions":
            raise EvidenceError(f"Not BPMN definitions documents: {source}, {converted}")
    elif any(
        node.tag.split("}")[-1] != "definitions" or "DMN" not in node.tag
        for node in (source_document, document)
    ):
        raise EvidenceError(f"Not DMN definitions documents: {source}, {converted}")
    return source_document, document


def source_without_comments(text, suffix):
    characters = list(text)
    index = 0
    block_comment_depth = 0
    string_delimiter = None
    while index < len(text):
        if block_comment_depth:
            if suffix in NESTED_BLOCK_COMMENT_SUFFIXES and text.startswith("/*", index):
                characters[index : index + 2] = "  "
                block_comment_depth += 1
                index += 2
            elif text.startswith("*/", index):
                characters[index : index + 2] = "  "
                block_comment_depth -= 1
                index += 2
            else:
                if text[index] not in "\r\n":
                    characters[index] = " "
                index += 1
        elif string_delimiter:
            if text[index] == "\\":
                index += 2
            elif text.startswith(string_delimiter, index):
                index += len(string_delimiter)
                string_delimiter = None
            else:
                index += 1
        elif text.startswith("//", index) and (
            (suffix != ".http" and (index == 0 or text[index - 1] != ":"))
            or (suffix == ".http" and not text[text.rfind("\n", 0, index) + 1:index].strip())
        ):
            while index < len(text) and text[index] not in "\r\n":
                characters[index] = " "
                index += 1
        elif suffix != ".http" and text.startswith("/*", index):
            characters[index : index + 2] = "  "
            block_comment_depth = 1
            index += 2
        elif suffix == ".http" and text[index] == "#":
            line_start = text.rfind("\n", 0, index) + 1
            if not text[line_start:index].strip():
                while index < len(text) and text[index] not in "\r\n":
                    characters[index] = " "
                    index += 1
            else:
                index += 1
        elif text.startswith('"""', index) or text.startswith("'''", index):
            string_delimiter = text[index : index + 3]
            index += 3
        elif text[index] in "\"'`":
            string_delimiter = text[index]
            index += 1
        else:
            index += 1
    return "".join(characters)


def nested_module_paths(root, module, path, module_paths):
    nested = set()
    for other in module_paths:
        if other == module:
            continue
        other_path = project_path(root, other, "module")
        if other_path.is_relative_to(path):
            nested.add(other_path)
    return nested


def scan_source_directory(root, module, path, nested, hashes, skip_directories):
    hits = {}

    def walk_error(error):
        raise EvidenceError(f"Cannot scan module {module}: {error}") from error

    for current, directories, files in os.walk(path, followlinks=False, onerror=walk_error):
        directory = Path(current)
        for name in directories:
            candidate = directory / name
            if name not in skip_directories and candidate not in nested and candidate.is_symlink():
                raise EvidenceError(f"Cannot scan symlinked source directory: {candidate}")
        directories[:] = sorted(
            name for name in directories
            if name not in skip_directories and directory / name not in nested
        )
        for name in sorted(files):
            file = directory / name
            if file.is_symlink():
                raise EvidenceError(f"Cannot scan symlinked source file: {file}")
            try:
                content = file.read_bytes()
                text = content.decode("utf-8") if file.suffix.lower() in CODE_SUFFIXES else ""
            except (OSError, UnicodeError) as exc:
                raise EvidenceError(f"Cannot scan {file}: {exc}") from exc
            relative = file.relative_to(root).as_posix()
            if relative == REPORT.as_posix():
                continue
            hashes[relative] = hashlib.sha256(content).hexdigest()
            scan_text = source_without_comments(text, file.suffix.lower())
            for match in DUE_DATE_HINT.finditer(scan_text):
                line = scan_text.count("\n", 0, match.start()) + 1
                column = match.start() - scan_text.rfind("\n", 0, match.start())
                hits[f"{relative}:{line}:{column}"] = match.group(0).casefold().strip("'\"`")
    return hits


def scan_module(root, module, module_paths, hashes):
    path = project_path(root, module, "module", must_exist=True)
    if not path.is_dir():
        raise EvidenceError(f"Module directory is missing: {module}")
    nested = nested_module_paths(root, module, path, module_paths)
    return scan_source_directory(root, module, path, nested, hashes, SKIP_SOURCE_DIRS)


def scan_test_root(root, module, module_paths, path, hashes):
    nested = nested_module_paths(root, module, path, module_paths)
    return scan_source_directory(root, module, path, nested, hashes, ())


def valid_migrated_caller_location(root, module, location, hashes):
    match = re.fullmatch(r"(.+):([1-9]\d*):([1-9]\d*)", location)
    if match is None:
        return False
    source = project_path(root, match.group(1), "migrated caller", must_exist=True)
    module_path = project_path(root, module, "module", must_exist=True)
    relative = source.relative_to(root).as_posix()
    if (
        not source.is_file()
        or not source.is_relative_to(module_path)
        or relative not in hashes
        or source.suffix.lower() not in CODE_SUFFIXES
    ):
        return False
    try:
        lines = source.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError) as exc:
        raise EvidenceError(f"Cannot read migrated caller {source}: {exc}") from exc
    line, column = int(match.group(2)), int(match.group(3))
    return line <= len(lines) and column <= len(lines[line - 1])


def exact_location_is_mentioned(text, location):
    return (
        re.search(
            rf"(?<![\w./\\-]){re.escape(location)}(?![\w/\\-])",
            text,
        )
        is not None
    )


def repeating_starts(document):
    starts = {}
    for process in document.findall(f"{BPMN}process"):
        for start in process.findall(f"{BPMN}startEvent"):
            cycle = start.find(f"{BPMN}timerEventDefinition/{BPMN}timeCycle")
            if cycle is None:
                continue
            if not process.get("id") or not start.get("id"):
                raise EvidenceError("Repeating timer start lacks a process or event ID")
            key = (process.get("id"), start.get("id"))
            if key in starts:
                raise EvidenceError(f"Duplicate repeating timer start: {key}")
            starts[key] = "".join(cycle.itertext()).strip()
    return starts


def single_outgoing_flow(node, flows):
    actual = [flow for flow in flows if flow.get("sourceRef") == node.get("id")]
    declared = [ref.text for ref in node.findall(f"{BPMN}outgoing")]
    return actual[0] if len(actual) == 1 and declared == [actual[0].get("id")] else None


def supports_message_rearm(
    document,
    process,
    timer_id,
    message_name,
    correlation_key,
    date_variable,
    message_date_variable,
    rearm_process_id,
    rearm_call_activity_id,
):
    timer = next(
        (
            element
            for element in process.iter()
            if element.get("id") == timer_id
            and element.tag == f"{BPMN}intermediateCatchEvent"
            and any(child.tag == f"{BPMN}timerEventDefinition" for child in element)
        ),
        None,
    )
    timer_date = (
        timer.find(f"{BPMN}timerEventDefinition/{BPMN}timeDate")
        if timer is not None
        else None
    )
    timer_expression = (
        "".join(timer_date.itertext()).strip() if timer_date is not None else ""
    )
    messages = [
        message for message in document.findall(f"{BPMN}message")
        if message.get("name") == message_name
    ]
    if (
        re.fullmatch(rf"=\s*{re.escape(date_variable)}", timer_expression) is None
        or len(messages) != 1
        or not messages[0].get("id")
    ):
        return False
    subscription = messages[0].find(f"{BPMN}extensionElements/{ZEEBE}subscription")
    if subscription is None or subscription.get("correlationKey") != f"={correlation_key}":
        return False
    message_id = messages[0].get("id")
    parent_process = next(
        (
            candidate
            for candidate in document.findall(f"{BPMN}process")
            if candidate.get("id") == rearm_process_id
        ),
        None,
    )
    if parent_process is None or parent_process is process:
        return False
    call_activity = next(
        (
            element
            for element in parent_process.findall(f"{BPMN}callActivity")
            if element.get("id") == rearm_call_activity_id
        ),
        None,
    )
    if call_activity is None:
        return False
    called_element = call_activity.find(
        f"{BPMN}extensionElements/{ZEEBE}calledElement"
    )
    input_mappings = call_activity.findall(
        f"{BPMN}extensionElements/{ZEEBE}ioMapping/{ZEEBE}input"
    )
    if (
        called_element is None
        or called_element.get("processId") != process.get("id")
        or not any(
            mapping.get("source") == f"={date_variable}"
            and mapping.get("target") == date_variable
            for mapping in input_mappings
        )
    ):
        return False
    message_boundaries = [
        boundary
        for boundary in parent_process.findall(f"{BPMN}boundaryEvent")
        if boundary.get("attachedToRef") == rearm_call_activity_id
        and boundary.get("cancelActivity") not in ("false", "0")
        and any(
            definition.tag == f"{BPMN}messageEventDefinition"
            and definition.get("messageRef") == message_id
            for definition in boundary
        )
    ]
    if len(message_boundaries) != 1:
        return False
    flows = parent_process.findall(f"{BPMN}sequenceFlow")
    ids = [flow.get("id") for flow in flows]
    if None in ids or len(ids) != len(set(ids)):
        return False
    boundary = message_boundaries[0]
    date_outputs = boundary.findall(
        f"{BPMN}extensionElements/{ZEEBE}ioMapping/{ZEEBE}output"
    )
    if not any(
        output.get("source") == f"={message_date_variable}"
        and output.get("target") == date_variable
        for output in date_outputs
    ):
        return False
    boundary_flow = single_outgoing_flow(boundary, flows)
    if boundary_flow is None:
        return False
    merge = next(
        (
            element
            for element in parent_process.findall(f"{BPMN}exclusiveGateway")
            if element.get("id") == boundary_flow.get("targetRef")
        ),
        None,
    )
    if merge is None:
        return False
    incoming = {ref.text for ref in merge.findall(f"{BPMN}incoming")}
    if (
        len(incoming) < 2
        or boundary_flow.get("id") not in incoming
        or incoming != {flow.get("id") for flow in flows
                        if flow.get("targetRef") == merge.get("id")}
    ):
        return False
    reentry = single_outgoing_flow(merge, flows)
    return (
        reentry is not None
        and reentry.get("targetRef") == rearm_call_activity_id
        and reentry.get("id") in {
            ref.text for ref in call_activity.findall(f"{BPMN}incoming")
        }
    )


def process_assertions(process):
    tags = {element.tag for element in process.iter()}
    found = set()
    if f"{BPMN}userTask" in tags:
        found.add("user_task_type")
    if f"{BPMN}messageEventDefinition" in tags or any(
        element.get("messageRef") for element in process.iter()
    ):
        found.add("downstream_message_instance")
    if tags.intersection(
        {f"{BPMN}{name}" for name in ("exclusiveGateway", "inclusiveGateway", "eventBasedGateway")}
    ):
        found.add("branch_selection")
    if tags.intersection({f"{ZEEBE}taskDefinition", f"{ZEEBE}ioMapping"}):
        found.update(("worker_input_output", "incident_behavior"))
    if f"{BPMN}errorEventDefinition" in tags:
        found.add("incident_behavior")
    if f"{ZEEBE}formDefinition" in tags:
        found.add("form_resolution")
    return found


def markdown_cells(line):
    """Split on unescaped pipes and decode escaped pipes and backslashes."""
    text = line.strip()
    if not text.startswith("|"):
        return []
    cells = []
    cell = []
    backslashes = 0
    trailing_delimiter = False
    for character in text[1:]:
        if character == "\\":
            cell.append(character)
            backslashes += 1
            trailing_delimiter = False
            continue
        if character == "|" and backslashes % 2:
            cell.pop()
            cell.append(character)
            backslashes = 0
            trailing_delimiter = False
            continue
        if character == "|":
            cells.append("".join(cell).strip())
            cell = []
            trailing_delimiter = True
        else:
            cell.append(character)
            trailing_delimiter = False
        backslashes = 0
    if not trailing_delimiter:
        cells.append("".join(cell).strip())
    return [re.sub(r"\\([\\|])", r"\1", value) for value in cells]


def plain_markdown_cell(value):
    value = re.sub(r"<br\s*/?>", " ", value, flags=re.I)
    return value.replace("**", "").replace("__", "").strip().strip("`").strip()


def test_id_parts(test_id):
    if not isinstance(test_id, str) or ":" not in test_id or "#" not in test_id:
        raise EvidenceError(f"Invalid Test Inventory ID: {test_id!r}")
    module, qualified_method = test_id.split(":", 1)
    class_name, _, method = qualified_method.partition("#")
    feature_path = Path(class_name)
    valid_feature_path = (
        class_name.casefold().endswith(".feature")
        and not feature_path.is_absolute()
        and not PureWindowsPath(class_name).drive
        and "\\" not in class_name
        and ".." not in feature_path.parts
    )
    if (
        not module
        or not class_name
        or not method.strip()
        or (
            re.fullmatch(r"[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*", class_name)
            is None
            and not valid_feature_path
        )
    ):
        raise EvidenceError(f"Invalid Test Inventory ID: {test_id!r}")
    return module, class_name, method


def markdown_table_separator(row):
    return bool(row) and all(re.fullmatch(r":?-{3,}:?", cell) for cell in row)


INVENTORY_COLUMNS = {"test id", "file", "test kind", "handling"}
TEST_INVENTORY_HEADING = re.compile(r"^#{1,6}\s+test inventory\b", re.IGNORECASE)
MARKDOWN_HEADING = re.compile(r"^#{1,6}\s+")


def test_inventory_section(lines):
    """Return the table lines under the single Test Inventory heading, or None."""
    starts = [
        index for index, line in enumerate(lines)
        if TEST_INVENTORY_HEADING.match(line.strip())
    ]
    if len(starts) > 1:
        raise EvidenceError("MIGRATION_REPORT.md has more than one Test Inventory section")
    start = starts[0] + 1 if starts else len(lines)
    end = next(
        (
            index for index in range(start, len(lines))
            if MARKDOWN_HEADING.match(lines[index].strip())
        ),
        len(lines),
    )
    for index, line in enumerate(lines):
        if start <= index < end:
            continue
        names = {plain_markdown_cell(cell).casefold() for cell in markdown_cells(line)}
        if INVENTORY_COLUMNS.issubset(names):
            raise EvidenceError(
                "MIGRATION_REPORT.md Test Inventory table must be under the Test Inventory heading"
            )
    if not starts:
        return None
    for line in lines[start:end]:
        if "|" in line and not markdown_cells(line):
            raise EvidenceError(
                "MIGRATION_REPORT.md Test Inventory rows must start with |"
            )
    return [line for line in lines[start:end] if markdown_cells(line)]


def test_report_inventory(root, *, required=True):
    path = root / REPORT
    reject_symlink_components(root, path, "MIGRATION_REPORT.md")
    lines = path.read_text(encoding="utf-8").splitlines() if path.is_file() else []
    table = test_inventory_section(lines)
    if not table:
        if required:
            raise EvidenceError(
                "test_run_mode is run but MIGRATION_REPORT.md has no Test Inventory"
            )
        return []
    names = [plain_markdown_cell(cell).casefold() for cell in markdown_cells(table[0])]
    if not INVENTORY_COLUMNS.issubset(names):
        raise EvidenceError("MIGRATION_REPORT.md Test Inventory table has no valid header")
    columns = {name: position for position, name in enumerate(names)}
    rows = []
    for line in table[1:]:
        row = markdown_cells(line)
        if markdown_table_separator(row):
            continue
        if len(row) != len(names):
            raise EvidenceError(
                "Test Inventory row does not match its table header; "
                "put other tables under their own heading"
            )
        values = [plain_markdown_cell(cell) for cell in row]
        test_id = values[columns["test id"]]
        file_path = values[columns["file"]]
        test_kind = values[columns["test kind"]]
        handling_text = re.sub(
            r"[\s_-]+", " ", values[columns["handling"]].casefold()
        ).strip()
        if handling_text.startswith("migrate"):
            handling = "Migrate"
        elif handling_text.startswith("report only"):
            handling = "Report only"
        elif handling_text == "not part of test migration":
            continue
        else:
            raise EvidenceError(
                f"{test_id}: unsupported Test Inventory handling "
                f"{values[columns['handling']]!r}"
            )
        module, class_name, method = test_id_parts(test_id)
        if not test_kind:
            raise EvidenceError(f"{test_id}: Test Inventory kind is empty")
        if not file_path:
            raise EvidenceError(f"{test_id}: Test Inventory file path is empty")
        source_file = project_path(root, file_path, "Test Inventory file")
        module_root = project_path(root, module, "Test Inventory module")
        try:
            source_file.relative_to(module_root)
        except ValueError as exc:
            raise EvidenceError(
                f"{test_id}: Test Inventory file is outside its module"
            ) from exc
        rows.append(
            {
                "id": test_id,
                "module": module,
                "file": Path(file_path).as_posix(),
                "class_name": class_name,
                "method": method,
                "test_kind": test_kind,
                "handling": handling,
                "models": values[columns["models"]] if "models" in columns else "",
            }
        )
    ids = [row["id"] for row in rows]
    if len(ids) != len(set(ids)):
        raise EvidenceError("Test Inventory IDs must be unique")
    return rows


def report_patterns(value, default, label):
    if value is None:
        return list(default)
    try:
        patterns = strings(value, label)
    except EvidenceError:
        raise
    for pattern in patterns:
        if (
            "\\" in pattern
            or "\0" in pattern
            or Path(pattern).is_absolute()
            or PureWindowsPath(pattern).drive
            or ".." in Path(pattern).parts
        ):
            raise EvidenceError(f"{label} must use project-relative patterns")
    return patterns


def test_directory_roots(root, module, module_paths, values, label):
    roots = strings(values, label)
    module_path = project_path(root, module, "Step 2 test suite module", must_exist=True)
    if not module_path.is_dir():
        raise EvidenceError(f"Step 2 test suite module is not a directory: {module}")
    nested_modules = []
    for other in module_paths:
        if other == module:
            continue
        other_path = project_path(root, other, "Step 2 module")
        if other_path != module_path and other_path.is_relative_to(module_path):
            nested_modules.append(other_path)

    normalized = []
    for value in roots:
        path = project_path(root, value, label)
        if path == module_path or not path.is_relative_to(module_path):
            raise EvidenceError(
                f"{label} must be inside a subdirectory of suite module {module!r}"
            )
        if any(path.is_relative_to(nested) for nested in nested_modules):
            raise EvidenceError(f"{label} must not include another Step 2 module: {value!r}")
        if path.exists() and not path.is_dir():
            raise EvidenceError(f"{label} is not a directory: {value!r}")
        normalized.append(path.relative_to(root).as_posix())
    return normalized


def test_contract(root, inventory):
    mode = read_test_run_mode(inventory)
    if mode is None:
        if any(
            test["handling"] == "Migrate"
            for test in test_report_inventory(root, required=False)
        ):
            raise EvidenceError(
                "Step 2 test_run_mode is required when the Test Inventory contains "
                "migratable tests"
            )
        return {
            "mode": None,
            "tests": [],
            "suites": {},
            "test_suites": {},
            "modules": inventory.get("modules", []),
        }

    tests = test_report_inventory(root, required=False)
    module_paths = set(strings(inventory.get("modules"), "Step 2 modules"))
    test_by_id = {test["id"]: test for test in tests}
    for test in tests:
        if test["module"] not in module_paths:
            raise EvidenceError(
                f"{test['id']}: Test Inventory module is outside the Step 2 scope"
            )

    migrate_ids = {
        test["id"] for test in tests if test["handling"] == "Migrate"
    }
    suite_entries = inventory.get("test_suites", [])
    if not isinstance(suite_entries, list):
        raise EvidenceError("Step 2 test_suites must be an array")
    suites = {}
    test_suites = {}
    for entry in suite_entries:
        if not isinstance(entry, dict):
            raise EvidenceError("Every Step 2 test suite must be an object")
        module = entry.get("module")
        name = entry.get("name")
        command = entry.get("command")
        test_ids = entry.get("test_ids")
        if (
            not isinstance(module, str)
            or module not in module_paths
            or not isinstance(name, str)
            or not name
            or not isinstance(command, list)
            or not command
            or any(not isinstance(part, str) or not part for part in command)
        ):
            raise EvidenceError("Step 2 test suite needs a module, name, and command")
        key = (module, name)
        if key in suites:
            raise EvidenceError(f"{module}: duplicate Step 2 test suite {name}")
        test_ids = strings(test_ids, f"{module} {name} Test IDs")
        if not test_ids:
            raise EvidenceError(f"{module} {name}: list the Test Inventory IDs in this suite")
        for test_id in test_ids:
            if test_id not in test_by_id:
                raise EvidenceError(
                    f"{module} {name}: {test_id} is missing from the Test Inventory"
                )
            if test_by_id[test_id]["module"] != module:
                raise EvidenceError(
                    f"{test_id}: test suite module differs from the Test Inventory"
                )
            test_suites.setdefault(test_id, []).append(key)
        suites[key] = {
            "module": module,
            "name": name,
            "command": command,
            "test_ids": test_ids,
            "reports": report_patterns(
                entry.get("reports"), DEFAULT_JUNIT_REPORTS, f"{module} {name} reports"
            ),
            "coverage_reports": report_patterns(
                entry.get("coverage_reports"),
                DEFAULT_C7_COVERAGE_REPORTS,
                f"{module} {name} coverage_reports",
            ),
            "test_source_roots": test_directory_roots(
                root,
                module,
                module_paths,
                entry.get("test_source_roots", []),
                f"{module} {name} test_source_roots",
            ),
            "test_resource_roots": test_directory_roots(
                root,
                module,
                module_paths,
                entry.get("test_resource_roots", []),
                f"{module} {name} test_resource_roots",
            ),
            "migrate_test_ids": [test_id for test_id in test_ids if test_id in migrate_ids],
        }

    if mode == "run":
        for test_id in migrate_ids:
            if not test_suites.get(test_id):
                raise EvidenceError(
                    f"{test_id}: no Step 2 test suite records this migrated test"
                )
    return {
        "mode": mode,
        "tests": tests,
        "suites": suites,
        "test_suites": test_suites,
        "modules": sorted(module_paths),
    }


def validate_source_snapshot_test_contract(root, inventory):
    if "test_run_mode" not in inventory:
        return None
    current_test_contract = test_contract(root, inventory)
    if not source_test_contract_matches_snapshot(
        source_test_contract(current_test_contract),
        inventory.get("source_snapshot_test_contract"),
    ):
        raise EvidenceError(
            "Test Inventory or C7 suite commands changed after the Step 2 snapshot"
        )
    if current_test_contract["mode"] in TEST_RUN_MODES:
        missing_snapshot_files = sorted(
            {
                test["file"]
                for test in current_test_contract["tests"]
                if test["file"] not in inventory["source_files"]
            }
        )
        if missing_snapshot_files:
            raise EvidenceError(
                "C7 source snapshot omits Test Inventory file(s): "
                + ", ".join(missing_snapshot_files)
            )
    return current_test_contract


def valid_test_results(test_results):
    if not isinstance(test_results, dict):
        return False
    for test_id, test_result in test_results.items():
        if (
            not isinstance(test_id, str)
            or not test_id
            or not isinstance(test_result, dict)
            or not isinstance(test_result.get("result"), str)
            or test_result["result"] not in TEST_RESULTS
        ):
            return False
        invocations = test_result.get("invocations", [])
        if not isinstance(invocations, list) or any(
            not isinstance(status, str) or status not in TEST_RESULTS
            for status in invocations
        ):
            return False
    return True


def baseline_suite_has_valid_shape(suite):
    if (
        not isinstance(suite.get("module"), str)
        or not suite["module"]
        or not isinstance(suite.get("suite"), str)
        or not suite["suite"]
        or not isinstance(suite.get("result"), str)
        or suite["result"] not in ("passed", "failed", "blocked")
    ):
        return False

    if not valid_test_results(suite.get("test_results", {})):
        return False

    coverage_by_process = suite.get("coverage_by_process", {})
    if not isinstance(coverage_by_process, dict) or any(
        not isinstance(process_id, str)
        or not process_id
        or not isinstance(elements, list)
        or any(not isinstance(element, str) or not element for element in elements)
        for process_id, elements in coverage_by_process.items()
    ):
        return False
    if "coverage_available" in suite and type(suite["coverage_available"]) is not bool:
        return False
    return True


def read_test_mapping(root, required=False):
    path = root / TEST_MAPPING
    reject_symlink_components(root, path, "test parity ledger")
    if not path.exists():
        if required:
            raise EvidenceError(f"Missing test parity ledger: {path}")
        return None
    try:
        path.resolve(strict=True).relative_to(root)
    except (OSError, ValueError) as exc:
        raise EvidenceError(f"Test parity ledger is outside the project: {path}") from exc
    mapping = read_json(path)
    if mapping.get("schema_version") != 1:
        raise EvidenceError("Unsupported test parity ledger version")
    if (
        not isinstance(mapping.get("baseline"), dict)
        or not isinstance(mapping.get("tests"), list)
        or not isinstance(mapping.get("freeze"), dict)
        or not isinstance(mapping["freeze"].get("files"), dict)
        or not isinstance(mapping.get("test_changes"), list)
        or not isinstance(mapping.get("mock_changes"), list)
    ):
        raise EvidenceError("Test parity ledger has an invalid shape")
    baseline = mapping["baseline"]
    if (
        "suites" not in baseline and baseline
    ):
        raise EvidenceError("Test parity ledger baseline lacks its suite records")
    if "suites" not in baseline:
        baseline["suites"] = []
    if (
        not isinstance(baseline["suites"], list)
        or any(
            not isinstance(suite, dict) or not baseline_suite_has_valid_shape(suite)
            for suite in baseline["suites"]
        )
    ) or (
        "coverage" in baseline and not isinstance(baseline["coverage"], dict)
    ) or (
        "coverage_available" in baseline
        and type(baseline["coverage_available"]) is not bool
    ):
        raise EvidenceError("Test parity ledger baseline has an invalid shape")
    coverage_available, coverage = aggregate_baseline_coverage(baseline["suites"])
    if (
        baseline.get("coverage_available", False) is not coverage_available
        or baseline.get("coverage", {}) != coverage
    ):
        raise EvidenceError("C7 coverage aggregate differs from its suite records")
    if baseline.get("source_digest") or baseline["suites"]:
        inventory = read_json(root / INVENTORY)
        if baseline.get("source_digest") != inventory.get("source_snapshot_sha256"):
            raise EvidenceError(
                "Test parity ledger belongs to a different C7 source snapshot; "
                "restore that baseline before resetting the source snapshot"
            )
        if baseline.get("commit") != inventory.get("source_snapshot_commit"):
            raise EvidenceError("Test parity ledger C7 commit differs from the Step 2 snapshot")
    return mapping


def json_digest(value):
    return hashlib.sha256(
        json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()


def module_suite_digest(module, suite):
    return hashlib.sha256(f"{module}\0{suite}".encode("utf-8")).hexdigest()[:20]


def reject_symlink_components(root, path, label):
    try:
        relative = path.relative_to(root)
    except ValueError as exc:
        raise EvidenceError(f"{label} is outside its module: {path}") from exc
    current = root
    for component in relative.parts:
        current = current / component
        if current.is_symlink():
            raise EvidenceError(f"Refusing symlinked {label}: {current}")


def discover_reports(root, module, patterns, label):
    module_root = project_path(root, module, f"{label} module", must_exist=True)
    reject_symlink_components(root, root / module, f"{label} module")
    found = set()
    for pattern in patterns:
        try:
            candidates = module_root.glob(pattern)
            for path in candidates:
                reject_symlink_components(module_root, path, label)
                if not path.is_file():
                    continue
                try:
                    path.resolve(strict=True).relative_to(module_root.resolve(strict=True))
                except (OSError, ValueError) as exc:
                    raise EvidenceError(f"{label} is outside its module: {path}") from exc
                found.add(path)
        except OSError as exc:
            raise EvidenceError(f"Cannot scan {label} in {module}: {exc}") from exc
    return sorted(found)


def report_signatures(paths):
    signatures = {}
    for path in paths:
        try:
            stat = path.stat()
        except OSError as exc:
            raise EvidenceError(f"Cannot inspect report {path}: {exc}") from exc
        signatures[path] = (file_digest(path), stat.st_mtime_ns)
    return signatures


def fresh_reports(root, module, patterns, before, label):
    after = discover_reports(root, module, patterns, label)
    signatures = report_signatures(after)
    return [
        path for path in after
        if path not in before or signatures[path] != before[path]
    ]


def copy_reports(root, module, reports, destination):
    root = root.resolve(strict=True)
    module_root = project_path(root, module, "report module", must_exist=True)
    reject_symlink_components(root, root / module, "report module")
    destination_root = ensure_directory_path(
        root, root / destination, "report destination"
    )
    copied = []
    for source in reports:
        reject_symlink_components(module_root, source, "report")
        try:
            relative = source.resolve(strict=True).relative_to(module_root.resolve(strict=True))
        except (OSError, ValueError) as exc:
            raise EvidenceError(f"Report is outside its module: {source}") from exc
        target = destination_root / relative
        target_parent = ensure_directory_path(
            destination_root, target.parent, "report destination"
        )
        try:
            target_parent.relative_to(destination_root)
        except ValueError as exc:
            raise EvidenceError(
                f"Report destination is outside its destination root: {target_parent}"
            ) from exc
        target = target_parent / target.name
        if target.is_symlink():
            raise EvidenceError(f"Refusing to replace symlinked report: {target}")
        temporary_path = None
        try:
            with tempfile.NamedTemporaryFile(dir=target_parent, delete=False) as temporary:
                temporary_path = Path(temporary.name)
            shutil.copy2(source, temporary_path)
            os.replace(temporary_path, target)
        except OSError as exc:
            raise EvidenceError(f"Cannot preserve report {source}: {exc}") from exc
        finally:
            if temporary_path is not None:
                temporary_path.unlink(missing_ok=True)
        copied.append(target.relative_to(root).as_posix())
    return copied


def junit_status(testcase):
    statuses = {
        element.tag.rsplit("}", 1)[-1]
        for element in testcase
    }
    if "error" in statuses:
        return "error"
    if "failure" in statuses:
        return "failed"
    if "skipped" in statuses:
        return "skipped"
    return "passed"


def aggregate_test_status(statuses):
    for status in ("error", "failed", "skipped"):
        if status in statuses:
            return status
    return "passed"


def junit_method_name(name):
    return re.sub(r"(?:\([^()]*\)|\[[^\]]*\])+$", "", name).strip()


def parse_junit_report(path, module, expected_test_ids):
    try:
        document = ET.parse(path)
    except (OSError, ET.ParseError) as exc:
        raise EvidenceError(f"Cannot parse JUnit report {path}: {exc}") from exc
    cases = {}
    for testcase in document.getroot().iter():
        if testcase.tag.rsplit("}", 1)[-1] != "testcase":
            continue
        class_name = testcase.get("classname")
        report_name = testcase.get("name", "")
        exact_test_id = f"{module}:{class_name}#{report_name}"
        method = (
            report_name
            if exact_test_id in expected_test_ids
            else junit_method_name(report_name)
        )
        if not class_name or not method:
            raise EvidenceError(f"JUnit report has a test without a class or method: {path}")
        test_id = f"{module}:{class_name}#{method}"
        cases.setdefault(test_id, []).append(junit_status(testcase))
    return cases


def parse_junit_reports(paths, module, expected_test_ids=None):
    expected_test_ids = set(expected_test_ids or ())
    invocations = {}
    for path in paths:
        for test_id, statuses in parse_junit_report(
            path, module, expected_test_ids
        ).items():
            invocations.setdefault(test_id, []).extend(statuses)
    return {
        test_id: {
            "result": aggregate_test_status(statuses),
            "invocations": statuses,
            "invocation_count": len(statuses),
        }
        for test_id, statuses in sorted(invocations.items())
    }


def parse_c7_coverage_report(path):
    try:
        with path.open(encoding="utf-8") as report_file:
            report = json.load(report_file)
    except (OSError, json.JSONDecodeError) as exc:
        raise EvidenceError(f"Cannot parse Camunda 7 coverage report {path}: {exc}") from exc
    if not isinstance(report, dict) or not isinstance(report.get("suites"), list):
        raise EvidenceError(f"Unsupported Camunda 7 coverage report: {path}")
    coverage = {}

    def visit(value):
        if isinstance(value, dict):
            source = value.get("source")
            process_id = value.get("modelKey")
            element_id = value.get("definitionKey")
            if source in ("FLOW_NODE", "SEQUENCE_FLOW"):
                if isinstance(process_id, str) and process_id and isinstance(element_id, str) and element_id:
                    coverage.setdefault(process_id, set()).add(element_id)
            for nested in value.values():
                visit(nested)
        elif isinstance(value, list):
            for nested in value:
                visit(nested)

    visit(report["suites"])
    return coverage


def parse_cpt_coverage_report(path):
    try:
        with path.open(encoding="utf-8") as report_file:
            report = json.load(report_file)
    except (OSError, json.JSONDecodeError) as exc:
        raise EvidenceError(f"Cannot parse CPT coverage report {path}: {exc}") from exc
    processes = (
        report.get("processCoverages", report.get("coverages"))
        if isinstance(report, dict)
        else None
    )
    decisions = report.get("decisionCoverages", []) if isinstance(report, dict) else None
    if not isinstance(processes, list) or not isinstance(decisions, list):
        raise EvidenceError(f"Unsupported CPT coverage report: {path}")
    coverage = {}
    for process in processes:
        if not isinstance(process, dict):
            raise EvidenceError(f"CPT coverage report has an invalid process entry: {path}")
        process_id = process.get("processDefinitionId")
        elements = process.get("completedElements")
        flows = process.get("takenSequenceFlows")
        if (
            not isinstance(process_id, str)
            or not process_id
            or not isinstance(elements, list)
            or not isinstance(flows, list)
            or any(not isinstance(item, str) or not item for item in elements + flows)
        ):
            raise EvidenceError(f"CPT coverage report has invalid element IDs: {path}")
        coverage.setdefault(process_id, set()).update(elements + flows)
    decision_coverage = {}
    for decision in decisions:
        if not isinstance(decision, dict):
            raise EvidenceError(f"CPT coverage report has an invalid decision entry: {path}")
        decision_id = decision.get("decisionDefinitionId")
        rule_ids = decision.get("matchedRuleIds", [])
        if (
            not isinstance(decision_id, str)
            or not decision_id
            or not isinstance(rule_ids, list)
            or any(not isinstance(item, str) or not item for item in rule_ids)
        ):
            raise EvidenceError(f"CPT coverage report has invalid decision IDs: {path}")
        decision_coverage.setdefault(decision_id, set()).update(rule_ids)
    return coverage, decision_coverage


def coverage_json(coverage):
    return {process_id: sorted(element_ids) for process_id, element_ids in sorted(coverage.items())}


def current_test_files(root, plan, mapping=None):
    cpt_test_ids = expected_cpt_test_ids(mapping)
    migrated_test_ids = mapped_migrated_test_ids(mapping)
    migrated_tests = [
        test
        for test in plan.test_contract["tests"]
        if test["id"] in migrated_test_ids
        or mapping is None and test["handling"] == "Migrate"
    ]
    migrated_modules = {test["module"] for test in migrated_tests}
    module_paths = set(plan.test_contract.get("modules", migrated_modules))
    cpt_modules = set()
    for test_id in cpt_test_ids:
        module, separator, _ = test_id.partition(":")
        if separator and module in module_paths:
            cpt_modules.add(module)
    modules = sorted(migrated_modules | cpt_modules)
    files = {}
    inventory_files = {Path(test["file"]).as_posix() for test in migrated_tests}
    roots_by_module = {module: set() for module in modules}
    for suite in plan.test_contract["suites"].values():
        module = suite["module"]
        if module not in roots_by_module or not suite_has_cpt_tests(suite, mapping):
            continue
        for root_type in ("test_source_roots", "test_resource_roots"):
            for value in suite[root_type]:
                path = project_path(
                    root,
                    value,
                    f"{module} {suite['name']} {root_type}",
                    must_exist=True,
                )
                if not path.is_dir():
                    raise EvidenceError(
                        f"{module} {suite['name']} {root_type} is not a directory: {value}"
                    )
                roots_by_module[module].add(path.relative_to(root))
    for module in modules:
        hashes = {}
        for test in migrated_tests:
            if test["module"] == module:
                add_existing_source_file_hash(
                    root, test["file"], "Test Inventory file", hashes
                )
        scan_module(root, module, module_paths, hashes)
        for test_root in sorted(roots_by_module[module]):
            scan_test_root(root, module, module_paths, root / test_root, hashes)
        for path, digest in hashes.items():
            relative_path = Path(path)
            if (
                path in inventory_files
                or "/src/test/" in f"/{path}"
                or any(
                    relative_path.is_relative_to(test_root)
                    for test_root in roots_by_module[module]
                )
            ):
                files[path] = f"sha256:{digest}"
    return dict(sorted(files.items()))


def test_mapping_digest(mapping, kind):
    if kind == "baseline":
        value = mapping.get("baseline", {})
    elif kind == "freeze":
        value = mapping.get("freeze", {})
    elif kind == "review":
        value = {
            "tests": mapping.get("tests", []),
            "freeze": mapping.get("freeze", {}),
            "mock_changes": mapping.get("mock_changes", []),
        }
        test_changes = mapping.get("test_changes", [])
        if test_changes:
            value["test_changes"] = test_changes
    else:
        value = {
            "baseline": mapping.get("baseline", {}),
            "tests": mapping.get("tests", []),
            "freeze": mapping.get("freeze", {}),
            "test_changes": mapping.get("test_changes", []),
            "mock_changes": mapping.get("mock_changes", []),
        }
    return json_digest(value)


def recorded_test_freeze_digest(root, mapping):
    key = ("project", ".", "test_freeze", None)
    reference = check_reference(key)
    log_path = root / reference
    reject_symlink_components(root, log_path, "validator-owned test freeze evidence")
    if not log_path.exists():
        return None
    path = project_path(root, reference, "validator-owned test freeze evidence", must_exist=True)
    if not path.is_file() or path.is_symlink() or not path.is_relative_to(root / LOGS):
        raise EvidenceError(f"{key}: invalid validator-owned test freeze evidence")
    check = read_json(path)
    digest = check.get("test_mapping_digest")
    run_id = check.get("run_id")
    if (
        check_key(check) != key
        or check.get("method") != "snapshot"
        or check.get("result") != "passed"
        or not isinstance(run_id, str)
        or not run_id
        or not isinstance(digest, str)
        or re.fullmatch(r"[0-9a-f]{64}", digest) is None
    ):
        raise EvidenceError(f"{key}: invalid validator-owned test freeze evidence")
    if not mapping["freeze"]["files"] and run_id != read_json(
        root / INVENTORY
    ).get("run_id"):
        return None
    return digest


def validate_test_freeze(root, plan, mapping):
    if not expected_cpt_test_ids(mapping):
        return []
    issues = []
    current = current_test_files(root, plan, mapping)
    frozen = mapping["freeze"]["files"]
    if not current and not frozen:
        issues.append("test_freeze: no migrated test files or resources were found")
    try:
        recorded_digest = recorded_test_freeze_digest(root, mapping)
    except EvidenceError as exc:
        issues.append(str(exc))
    else:
        if recorded_digest is None:
            if frozen:
                issues.append(
                    "test_freeze: frozen test hashes have no validator-owned snapshot"
                )
        elif recorded_digest != test_mapping_digest(mapping, "freeze"):
            issues.append(
                "test_freeze: frozen test hashes differ from the validator-owned snapshot"
            )
    for path, digest in frozen.items():
        try:
            project_path(root, path, "frozen test file")
        except EvidenceError as exc:
            issues.append(str(exc))
        if not isinstance(digest, str) or re.fullmatch(r"sha256:[0-9a-f]{64}", digest) is None:
            issues.append(f"test_freeze: invalid frozen hash for {path}")

    changes = mapping["test_changes"]
    approvals = {}
    for change in changes:
        if not isinstance(change, dict):
            issues.append("test_changes entries must be objects")
            continue
        path = change.get("file")
        old_hash = change.get("old_hash")
        new_hash = change.get("new_hash")
        reason = change.get("reason")
        approved_by = change.get("approved_by")
        try:
            project_path(root, path, "approved test change")
        except EvidenceError as exc:
            issues.append(str(exc))
            continue
        if (
            old_hash is not None
            and (
                not isinstance(old_hash, str)
                or re.fullmatch(r"sha256:[0-9a-f]{64}", old_hash) is None
            )
        ) or (
            new_hash is not None
            and (
                not isinstance(new_hash, str)
                or re.fullmatch(r"sha256:[0-9a-f]{64}", new_hash) is None
            )
        ):
            issues.append(f"{path}: test_changes has an invalid old_hash or new_hash")
            continue
        if not concrete_reference(reason) or not concrete_reference(approved_by):
            issues.append(f"{path}: test_changes needs a reason and approver")
            continue
        if path in approvals:
            issues.append(f"{path}: duplicate test_changes approval")
            continue
        approvals[path] = (old_hash, new_hash)

    used_approvals = set()
    for path in sorted(set(frozen) | set(current)):
        old_hash = frozen.get(path)
        new_hash = current.get(path)
        if old_hash == new_hash:
            continue
        if approvals.get(path) != (old_hash, new_hash):
            issues.append(f"{path}: frozen test file changed without an approved test_changes entry")
        else:
            used_approvals.add(path)
    for path in sorted(set(approvals) - used_approvals):
        issues.append(f"{path}: test_changes approval does not match a current file change")
    return issues


def record_test_freeze(root, plan, mapping):
    if not expected_cpt_test_ids(mapping):
        raise EvidenceError("test_freeze: no migrated or added CPT tests remain")
    rows = test_rows_by_id(mapping)
    for test in plan.test_contract["tests"]:
        if test["handling"] != "Migrate":
            continue
        ledger = rows.get(test["id"])
        if ledger is None or ledger.get("status") not in ("migrated", "retired"):
            raise EvidenceError(
                f"{test['id']}: record a migrated or approved retired status before freezing tests"
            )
        if ledger.get("status") == "retired" and not approved_retirement(ledger):
            raise EvidenceError(f"{test['id']}: retired test needs an approved reason before freeze")
    if mapping["freeze"]["files"]:
        issues = validate_test_freeze(root, plan, mapping)
        if issues:
            raise EvidenceError("; ".join(issues))
        return {
            "method": "snapshot",
            "result": "passed",
            "reason": None,
            "output": (
                f"Verified the existing hashes for "
                f"{len(mapping['freeze']['files'])} test files and resources."
            ),
            "test_mapping_digest": test_mapping_digest(mapping, "freeze"),
        }
    if recorded_test_freeze_digest(root, mapping) is not None:
        raise EvidenceError(
            "test_freeze: a validator-owned snapshot cannot be replaced by a new freeze"
        )
    current = current_test_files(root, plan, mapping)
    if not current:
        raise EvidenceError("test_freeze: no migrated test files or resources were found")
    mapping["freeze"]["files"] = current
    write_json(root, TEST_MAPPING, mapping)
    return {
        "method": "snapshot",
        "result": "passed",
        "reason": None,
        "output": f"Recorded SHA-256 hashes for {len(current)} test files and resources.",
        "test_mapping_digest": test_mapping_digest(mapping, "freeze"),
    }


def test_rows_by_id(mapping):
    rows = {}
    for test in mapping["tests"]:
        if not isinstance(test, dict):
            raise EvidenceError("Every test parity ledger entry must be an object")
        test_id = test.get("c7_id")
        if test_id is None and test.get("status") == "added":
            continue
        if not isinstance(test_id, str) or not test_id:
            raise EvidenceError("Test parity ledger entry needs a C7 test ID")
        if test_id in rows:
            raise EvidenceError(f"Duplicate test parity ledger entry: {test_id}")
        rows[test_id] = test
    return rows


def mapped_cpt_test_ids(contract, rows):
    mapped_c8_ids = set()
    for inventory_test in contract["tests"]:
        test = rows.get(inventory_test["id"])
        if test is None or test.get("status") != "migrated":
            continue
        c8_ids = test.get("c8_ids")
        if isinstance(c8_ids, list):
            mapped_c8_ids.update(
                c8_id for c8_id in c8_ids if isinstance(c8_id, str) and c8_id
            )
    return mapped_c8_ids


def expected_cpt_test_ids(mapping):
    if mapping is None:
        return set()
    test_rows_by_id(mapping)
    expected_ids = set()
    for test in mapping["tests"]:
        if test.get("status") not in ("migrated", "added"):
            continue
        c8_ids = test.get("c8_ids")
        if isinstance(c8_ids, list):
            expected_ids.update(
                c8_id for c8_id in c8_ids if isinstance(c8_id, str) and c8_id
            )
    return expected_ids


def mapped_migrated_test_ids(mapping):
    if mapping is None:
        return set()
    return {
        test_id
        for test_id, test in test_rows_by_id(mapping).items()
        if test.get("status") == "migrated"
    }


def suite_report_only_test_ids(suite, contract):
    inventory_tests = {test["id"]: test for test in contract["tests"]}
    return [
        test_id
        for test_id in suite["test_ids"]
        if inventory_tests.get(test_id, {}).get("handling") == "Report only"
    ]


def suite_requires_c7_baseline(suite, contract, mapping):
    if suite["migrate_test_ids"]:
        return True
    if mapping is None:
        return False
    rows = test_rows_by_id(mapping)
    return any(
        rows.get(test_id, {}).get("status") in ("migrated", "retired")
        for test_id in suite_report_only_test_ids(suite, contract)
    )


def test_validation_enabled(contract, migrated_test_ids, mapping):
    has_added_tests = mapping is not None and any(
        isinstance(test, dict) and test.get("status") == "added"
        for test in mapping["tests"]
    )
    rows = test_rows_by_id(mapping) if mapping is not None else {}
    has_retired_report_only_tests = any(
        test["handling"] == "Report only"
        and rows.get(test["id"], {}).get("status") == "retired"
        for test in contract["tests"]
    )
    return contract["mode"] == "run" and (
        bool(migrated_test_ids)
        or bool(expected_cpt_test_ids(mapping))
        or has_added_tests
        or has_retired_report_only_tests
        or any(test["handling"] == "Migrate" for test in contract["tests"])
    )


def suite_has_added_cpt_tests(module, suite_name, mapping):
    if mapping is None:
        return False
    return any(
        isinstance(test, dict)
        and test.get("status") == "added"
        and test.get("suite") == suite_name
        and isinstance(test.get("c8_ids"), list)
        and any(
            isinstance(c8_id, str) and c8_id.startswith(f"{module}:")
            for c8_id in test["c8_ids"]
        )
        for test in mapping["tests"]
    )


def suite_has_cpt_tests(suite, mapping):
    if mapping is None:
        return False
    rows = test_rows_by_id(mapping)
    test_ids = set(suite.get("test_ids", suite.get("migrate_test_ids", [])))
    for baseline_suite in mapping.get("baseline", {}).get("suites", []):
        if (
            baseline_suite.get("module") != suite.get("module")
            or baseline_suite.get("suite") != suite.get("name")
        ):
            continue
        for test_id in baseline_suite.get("test_results", {}):
            test = rows.get(test_id)
            if (
                test_id not in test_ids
                and isinstance(test, dict)
                and test.get("handling") == "Report only"
            ):
                test_ids.add(test_id)
    for test_id in test_ids:
        test = rows.get(test_id)
        c8_ids = test.get("c8_ids") if isinstance(test, dict) else None
        if (
            isinstance(test, dict)
            and test.get("status") == "migrated"
            and isinstance(c8_ids, list)
            and any(isinstance(c8_id, str) and c8_id for c8_id in c8_ids)
        ):
            return True
    return suite_has_added_cpt_tests(suite.get("module"), suite.get("name"), mapping)


def normalized_mock(value):
    if not isinstance(value, str) or not value.strip():
        raise EvidenceError("Each mock entry must be a non-blank string")
    text = value.strip()
    patterns = (
        ("auto-mock", r"\bautoMock\s*\(\s*['\"]([^'\"]+)['\"]"),
        ("job-worker", r"\bmockJobWorker\s*\(\s*['\"]([^'\"]+)['\"]"),
        ("child-process", r"\bmockChildProcess\s*\(\s*['\"]([^'\"]+)['\"]"),
        ("dmn-decision", r"\bmockDmnDecision\s*\(\s*['\"]([^'\"]+)['\"]"),
        ("component", r"\bMocks\.register\s*\(\s*['\"]([^'\"]+)['\"]"),
        ("component", r"\bMocks\.register\s*\(\s*([A-Za-z_$][\w$]*)"),
    )
    for kind, pattern in patterns:
        match = re.search(pattern, text)
        if match:
            return kind, re.sub(r"[^a-z0-9]+", "", match.group(1).casefold())
    annotation = re.search(r"@(?:MockitoBean|MockBean)\s+([\w.$]+)", text)
    if annotation:
        return "component", re.sub(r"[^a-z0-9]+", "", annotation.group(1).casefold())
    token = re.search(r"[A-Za-z_$][\w$.-]*", text)
    if token is None:
        raise EvidenceError(f"Cannot identify the mocked component in {value!r}")
    return "component", re.sub(r"[^a-z0-9]+", "", token.group(0).casefold())


def auto_mocked_job_types(c7_mocks, source_job_types_by_model):
    job_types = set()
    for value in c7_mocks:
        for match in re.finditer(r"\bautoMock\s*\(\s*(['\"])([^'\"]+)\1", value):
            resource = match.group(2)
            matching_models = [
                source_path
                for source_path in source_job_types_by_model
                if source_path == resource or source_path.endswith(f"/{resource}")
            ]
            if len(matching_models) == 1:
                job_types.update(source_job_types_by_model[matching_models[0]])
    return job_types


def mock_job_worker_type(value):
    match = re.search(r"\bmockJobWorker\s*\(\s*(['\"])([^'\"]+)\1", value)
    return match.group(2) if match else None


def test_mock_issues(test, mapping, mapped_c8_ids, source_job_types_by_model=None):
    issues = []
    test_id = test.get("c7_id")
    c8_ids = test.get("c8_ids")
    mocks = test.get("mocks")
    if (
        not isinstance(c8_ids, list)
        or not c8_ids
        or any(not isinstance(test_id, str) or not test_id for test_id in c8_ids)
        or not isinstance(mocks, dict)
    ):
        return [f"{test_id}: migrated test needs c8_ids and a mocks object"]
    c7_mocks = mocks.get("c7")
    c8_mocks = mocks.get("c8")
    if (
        not isinstance(c7_mocks, list)
        or any(not isinstance(value, str) or not value.strip() for value in c7_mocks)
        or not isinstance(c8_mocks, list)
        or any(not isinstance(value, str) or not value.strip() for value in c8_mocks)
    ):
        return [f"{test_id}: mocks.c7 and mocks.c8 must be arrays of non-blank strings"]

    if "c8_by_test_id" in mocks:
        c8_mocks_by_test_id = mocks["c8_by_test_id"]
        if (
            not isinstance(c8_mocks_by_test_id, dict)
            or set(c8_mocks_by_test_id) != set(c8_ids)
        ):
            return [
                f"{test_id}: mocks.c8_by_test_id must have exactly the c8_ids as keys"
            ]
        if any(
            not isinstance(values, list)
            or any(not isinstance(value, str) or not value.strip() for value in values)
            for values in c8_mocks_by_test_id.values()
        ):
            return [
                f"{test_id}: mocks.c8_by_test_id values must be arrays of non-blank strings"
            ]
        mapped_c8_mocks = {
            value
            for values in c8_mocks_by_test_id.values()
            for value in values
        }
        if mapped_c8_mocks != set(c8_mocks):
            return [
                f"{test_id}: mocks.c8 must match the union of mocks.c8_by_test_id"
            ]
    elif len(c8_ids) > 1 and c8_mocks:
        return [
            f"{test_id}: mocks.c8_by_test_id is required when a split test has C8 mocks"
        ]
    elif len(c8_ids) == 1:
        c8_mocks_by_test_id = {c8_ids[0]: c8_mocks}
    else:
        c8_mocks_by_test_id = {c8_id: [] for c8_id in c8_ids}

    c7_normalized = {normalized_mock(value) for value in c7_mocks}
    allowed_job_types = auto_mocked_job_types(
        c7_mocks, source_job_types_by_model or {}
    )
    approvals = {}
    for change in mapping["mock_changes"]:
        if not isinstance(change, dict):
            issues.append("mock_changes entries must be objects")
            continue
        cpt_test_id = change.get("cpt_test_id")
        if not isinstance(cpt_test_id, str) or cpt_test_id not in mapped_c8_ids:
            issues.append(
                f"{test_id}: mock_changes references unmapped CPT test {cpt_test_id!r}"
            )
            continue
        if cpt_test_id not in c8_ids:
            continue
        mock = change.get("mock")
        reason = change.get("reason")
        approved_by = change.get("approved_by")
        if (
            not isinstance(mock, str)
            or not mock.strip()
            or not concrete_reference(reason)
            or not concrete_reference(approved_by)
        ):
            issues.append(
                f"{test_id}: mock_changes needs a mapped CPT test, mock, reason, and approver"
            )
            continue
        approval_key = (cpt_test_id, mock)
        if approval_key in approvals:
            issues.append(f"{test_id}: duplicate mock_changes approval for {mock}")
            continue
        approvals[approval_key] = change

    used_approvals = set()
    for c8_id in c8_ids:
        for c8_mock in c8_mocks_by_test_id[c8_id]:
            normalized = normalized_mock(c8_mock)
            if normalized in c7_normalized or (
                normalized[0] == "job-worker"
                and mock_job_worker_type(c8_mock) in allowed_job_types
            ):
                continue
            approval_key = (c8_id, c8_mock)
            if approval_key not in approvals:
                issues.append(
                    f"{test_id}: CPT test {c8_id} adds unapproved mock {c8_mock}"
                )
            else:
                used_approvals.add(approval_key)
    for cpt_test_id, mock in sorted(set(approvals) - used_approvals):
        issues.append(f"{test_id}: mock_changes approval is not used by {cpt_test_id}: {mock}")
    return issues


def test_repeat_checks(plan, checks):
    results = {}
    for key, entry in checks.items():
        if key[0] != "module" or key[2] != "test_repeat":
            continue
        test_runs = entry[1].get("test_runs")
        if not isinstance(test_runs, list) or len(test_runs) != 2:
            continue
        results[(key[1], key[3])] = test_runs
    return results


def module_test_suites(contract):
    return contract.get("cpt_suites") or contract["suites"]


def ledger_row_suite_keys(test, contract, mapping):
    if test.get("status") == "added":
        return [
            suite_key
            for suite_key in module_test_suites(contract)
            if suite_key[1] == test.get("suite")
        ]
    test_id = test.get("c7_id")
    keys = {
        suite_key
        for suite_key, suite in contract["suites"].items()
        if test_id in suite.get("test_ids", suite.get("migrate_test_ids", []))
    }
    for baseline_suite in mapping.get("baseline", {}).get("suites", []):
        if isinstance(baseline_suite, dict) and test_id in baseline_suite.get(
            "test_results", {}
        ):
            keys.add((baseline_suite.get("module"), baseline_suite.get("suite")))
    return sorted(key for key in keys if key in contract["suites"])


def cpt_test_results(test_id, repeat_runs, suite_keys):
    module, _, _ = test_id_parts(test_id)
    matched = [[], []]
    found = False
    for suite_key in suite_keys:
        if suite_key[0] != module:
            continue
        runs = repeat_runs.get(suite_key)
        if runs is None:
            continue
        for index, run in enumerate(runs):
            results = run.get("test_results", {})
            if test_id in results:
                found = True
                matched[index].append(results[test_id].get("result"))
    if not found:
        return None
    return [aggregate_test_status(statuses) if statuses else None for statuses in matched]


def approved_retirement(test):
    retirement = test.get("retirement")
    return (
        isinstance(retirement, dict)
        and concrete_reference(retirement.get("reason"))
        and concrete_reference(retirement.get("approved_by"))
    )


def baseline_suite_matches_check(baseline, check):
    return all(
        baseline.get(field) == check.get(field)
        for field in (
            "result",
            "test_results",
            "coverage_by_process",
            "coverage_available",
        )
    )


def test_parity_issues(plan, checks, mapping):
    issues = []
    contract = plan.test_contract
    expected_suites = {
        suite_key
        for suite_key, suite in contract["suites"].items()
        if suite_requires_c7_baseline(suite, contract, mapping)
    }
    baseline_entries = {}
    for suite in mapping["baseline"].get("suites", []):
        if not isinstance(suite, dict):
            issues.append("C7 baseline suite entries must be objects")
            continue
        suite_key = (suite.get("module"), suite.get("suite"))
        if suite_key in baseline_entries:
            issues.append(f"{suite_key}: duplicate C7 baseline suite entry")
        baseline_entries[suite_key] = suite
    verified_baseline_suites = []
    for suite_key in sorted(expected_suites | baseline_entries.keys()):
        key = ("module", suite_key[0], "c7_baseline", suite_key[1])
        entry = checks.get(key)
        baseline = baseline_entries.get(suite_key)
        if entry is None:
            if suite_key in expected_suites:
                issues.append(f"{suite_key}: C7 baseline has not run")
            else:
                issues.append(
                    f"{suite_key}: test parity ledger has no matching "
                    "validator-owned C7 baseline check"
                )
            continue
        check = entry[1]
        if check.get("result") != "passed":
            issues.append(
                f"{suite_key}: C7 baseline is {check.get('result')}: "
                f"{check.get('reason') or 'not verified'}"
            )
        if baseline is None:
            if suite_key in expected_suites:
                issues.append(f"{suite_key}: test parity ledger has no C7 baseline record")
            continue
        if not baseline_suite_matches_check(baseline, check):
            issues.append(f"{suite_key}: test parity ledger differs from its baseline log")
            continue
        if check.get("result") == "passed":
            verified_baseline_suites.append(baseline)

    actual_baseline = aggregate_baseline_results(
        contract, verified_baseline_suites
    )
    rows = test_rows_by_id(mapping)
    repeat_runs = test_repeat_checks(plan, checks)
    cpt_id_owners = {}

    def claim_cpt_id(c8_id, owner):
        previous_owner = cpt_id_owners.get(c8_id)
        if previous_owner is None:
            cpt_id_owners[c8_id] = owner
        else:
            issues.append(
                f"{c8_id}: mapped from multiple test parity entries "
                f"({previous_owner} and {owner})"
            )

    for inventory_test in contract["tests"]:
        test_id = inventory_test["id"]
        test = rows.get(test_id)
        expected_result = actual_baseline.get(test_id, {}).get("c7_result")
        if test is None:
            if inventory_test["handling"] == "Migrate" or expected_result == "passed":
                issues.append(f"{test_id}: test parity ledger entry is missing")
            continue
        if (
            test.get("test_kind") != inventory_test["test_kind"]
            or test.get("handling") != inventory_test["handling"]
        ):
            issues.append(f"{test_id}: ledger kind or handling differs from the Test Inventory")
        if test.get("c7_result") != expected_result:
            issues.append(f"{test_id}: C7 result differs from the captured baseline reports")
        status = test.get("status")
        if status not in TEST_STATUSES:
            issues.append(f"{test_id}: ledger status must be migrated, retired, manual, or added")
            continue
        if (
            status in ("migrated", "retired")
            and inventory_test["handling"] == "Report only"
            and expected_result is None
        ):
            issues.append(
                f"{test_id}: {status} Report only test requires a captured C7 baseline"
            )
        if status == "manual":
            if test.get("handling") == "Migrate" or expected_result == "passed":
                issues.append(f"{test_id}: manual test is not verified")
            continue
        if status == "retired":
            if not approved_retirement(test):
                issues.append(f"{test_id}: retired test needs an approved reason")
            if test.get("c8_ids"):
                issues.append(f"{test_id}: retired test cannot map to CPT tests")
            continue
        if status != "migrated":
            issues.append(f"{test_id}: a C7 test cannot use status added")
            continue
        c8_ids = test.get("c8_ids")
        if (
            not isinstance(c8_ids, list)
            or not c8_ids
            or any(not isinstance(c8_id, str) or not c8_id for c8_id in c8_ids)
            or len(c8_ids) != len(set(c8_ids))
        ):
            issues.append(f"{test_id}: migrated test needs distinct c8_ids")
            continue
        suite_keys = ledger_row_suite_keys(test, contract, mapping)
        for c8_id in c8_ids:
            claim_cpt_id(c8_id, f"migrated C7 test {test_id}")
            try:
                cpt_results = cpt_test_results(c8_id, repeat_runs, suite_keys)
            except EvidenceError as exc:
                issues.append(str(exc))
                continue
            if cpt_results is None:
                issues.append(f"{test_id}: mapped CPT test {c8_id} is missing from both runs")
                continue
            if expected_result == "passed" and cpt_results != ["passed", "passed"]:
                issues.append(
                    f"{test_id}: mapped CPT test {c8_id} must pass in both runs, "
                    f"received {cpt_results[0]} and {cpt_results[1]}"
                )

    added_test_index = 0
    for test in mapping["tests"]:
        if test.get("status") != "added":
            continue
        added_test_index += 1
        c8_ids = test.get("c8_ids")
        if (
            not isinstance(c8_ids, list)
            or not c8_ids
            or any(not isinstance(c8_id, str) or not c8_id for c8_id in c8_ids)
        ):
            issues.append("Added CPT tests need one or more c8_ids")
            continue
        if not isinstance(test.get("suite"), str) or not test["suite"]:
            issues.append("Added CPT tests need the name of the suite that runs them")
        if len(c8_ids) != len(set(c8_ids)):
            issues.append("Added CPT tests need distinct c8_ids")
        suite_keys = ledger_row_suite_keys(test, contract, mapping)
        for c8_id in dict.fromkeys(c8_ids):
            claim_cpt_id(c8_id, f"added CPT test {added_test_index}")
            module, _, _ = test_id_parts(c8_id)
            if (module, test.get("suite")) not in module_test_suites(contract):
                issues.append(
                    f"Added CPT test {c8_id} names unknown suite {test.get('suite')} "
                    f"in module {module}"
                )
                continue
            try:
                cpt_results = cpt_test_results(c8_id, repeat_runs, suite_keys)
            except EvidenceError as exc:
                issues.append(str(exc))
                continue
            if cpt_results != ["passed", "passed"]:
                issues.append(
                    f"Added CPT test {c8_id} must pass in both runs"
                )
    return issues


def coverage_parity_issues(plan, checks, mapping):
    issues = []
    contract = plan.test_contract
    has_cpt_tests = bool(expected_cpt_test_ids(mapping))
    repeat_runs = test_repeat_checks(plan, checks)
    cpt_coverage = [{}, {}]
    cpt_decisions = [{}, {}]
    for suite_key, suite in contract["suites"].items():
        if not suite_has_cpt_tests(suite, mapping):
            continue
        runs = repeat_runs.get(suite_key)
        if runs is None:
            issues.append(f"{suite_key}: CPT coverage needs two recorded test runs")
            continue
        for index, run in enumerate(runs):
            if run.get("coverage_available") is not True:
                issues.append(f"{suite_key}: CPT coverage report is missing from run {index + 1}")
                continue
            for process_id, elements in run.get("coverage_by_process", {}).items():
                if (
                    not isinstance(process_id, str)
                    or not process_id
                    or not isinstance(elements, list)
                    or any(not isinstance(item, str) or not item for item in elements)
                ):
                    issues.append(f"{suite_key}: CPT run {index + 1} has invalid process coverage")
                    continue
                cpt_coverage[index].setdefault(process_id, set()).update(elements)
            for decision_id, rule_ids in run.get("decision_coverage_by_id", {}).items():
                if (
                    not isinstance(decision_id, str)
                    or not decision_id
                    or not isinstance(rule_ids, list)
                    or any(not isinstance(item, str) or not item for item in rule_ids)
                ):
                    issues.append(f"{suite_key}: CPT run {index + 1} has invalid decision coverage")
                    continue
                cpt_decisions[index].setdefault(decision_id, set()).update(rule_ids)

    baseline = mapping["baseline"]
    baseline_coverage_available, source_coverage = aggregate_baseline_coverage(
        baseline.get("suites", [])
    )
    if (
        baseline.get("coverage_available", False) is not baseline_coverage_available
        or baseline.get("coverage", {}) != source_coverage
    ):
        issues.append("C7 coverage aggregate differs from its suite records")
    normalized_source_coverage = {}
    process_mappings = {}
    retained_c7_elements = {}
    c7_processes_by_cpt_process = {}
    converted_model_paths_by_source_process = {}
    converted_model_paths_by_cpt_process = {}
    for converted_path, process_ids in plan.source_ids.items():
        for process_id in process_ids:
            converted_model_paths_by_source_process.setdefault(process_id, set()).add(
                converted_path
            )
    for converted_path, process_id in plan.converted_elements:
        converted_model_paths_by_cpt_process.setdefault(process_id, set()).add(
            converted_path
        )
    ambiguous_cpt_process_ids = set()
    if not baseline_coverage_available:
        note = "No Camunda 7 coverage baseline."
    else:
        note = "Camunda 7 coverage baseline captured."
    if not has_cpt_tests:
        note += " No migrated or added CPT tests remain for target comparison."
    if baseline_coverage_available:
        for process_id, elements in source_coverage.items():
            if (
                not isinstance(process_id, str)
                or not process_id
                or not isinstance(elements, list)
                or any(
                not isinstance(element_id, str) or not element_id for element_id in elements
                )
            ):
                issues.append(f"{process_id}: C7 coverage element IDs are invalid")
                continue
            source_elements = set(elements)
            normalized_source_coverage[process_id] = sorted(source_elements)
            converted_paths = converted_model_paths_by_source_process.get(process_id, set())
            if (
                len(converted_paths) > 1
                and any(
                    source_elements & model_elements
                    for (converted_path, _), model_elements
                    in plan.converted_elements.items()
                    if converted_path in converted_paths
                )
            ):
                issues.append(
                    f"{process_id}: C7 coverage is ambiguous across source models"
                )
                continue
            matching_processes = [
                (model, converted_process, model_elements)
                for (model, converted_process), model_elements
                in plan.converted_elements.items()
                if model in converted_paths and converted_process == process_id
            ]
            if len(matching_processes) > 1:
                issues.append(
                    f"{process_id}: C7 coverage maps to multiple converted processes"
                )
                continue
            if not matching_processes:
                matching_processes = [
                    (model, converted_process, model_elements)
                    for (model, converted_process), model_elements
                    in plan.converted_elements.items()
                    if model in converted_paths and source_elements & model_elements
                ]
                if len(matching_processes) > 1:
                    issues.append(
                        f"{process_id}: C7 coverage maps to multiple renamed CPT processes"
                    )
                    continue
            process_mappings[process_id] = [
                converted_process for _, converted_process, _ in matching_processes
            ]
            retained = set()
            if not matching_processes:
                retained_c7_elements[process_id] = []
                continue
            for model, converted_process, model_elements in matching_processes:
                expected = source_elements & model_elements
                retained.update(expected)
                if expected and len(
                    converted_model_paths_by_cpt_process.get(converted_process, set())
                ) > 1:
                    ambiguous_cpt_process_ids.add(converted_process)
                    continue
                if expected:
                    c7_processes_by_cpt_process.setdefault(converted_process, set()).add(
                        process_id
                    )
                if has_cpt_tests:
                    for run_index in range(2):
                        missing = expected - cpt_coverage[run_index].get(
                            converted_process, set()
                        )
                        if missing:
                            issues.append(
                                f"{model}#{converted_process}: CPT run {run_index + 1} "
                                "lost C7-covered elements: " + ", ".join(sorted(missing))
                            )
            retained_c7_elements[process_id] = sorted(retained)
        for converted_process in sorted(ambiguous_cpt_process_ids):
            model_paths = sorted(converted_model_paths_by_cpt_process[converted_process])
            issues.append(
                f"{converted_process}: CPT coverage process ID appears in multiple converted models: "
                + ", ".join(model_paths)
            )
        for converted_process, source_processes in sorted(
            c7_processes_by_cpt_process.items()
        ):
            if len(source_processes) > 1:
                issues.append(
                    f"{converted_process}: multiple C7 process IDs map to the same CPT process: "
                    + ", ".join(sorted(source_processes))
                )
    output = {
        "baseline_note": note,
        "c7_coverage": normalized_source_coverage,
        "process_mappings": process_mappings,
        "retained_c7_elements": retained_c7_elements,
        "cpt_run_1": coverage_json(cpt_coverage[0]),
        "cpt_run_2": coverage_json(cpt_coverage[1]),
        "cpt_decisions_run_1": coverage_json(cpt_decisions[0]),
        "cpt_decisions_run_2": coverage_json(cpt_decisions[1]),
    }
    return issues, output


def run_cpt_test_suite(
    root,
    module,
    suite_name,
    command,
    timeout,
    run_number,
    suite_config,
    expected_test_ids=None,
):
    reports = report_patterns(
        suite_config.get("reports"), DEFAULT_JUNIT_REPORTS, f"{module} {suite_name} JUnit reports"
    )
    coverage_reports = report_patterns(
        suite_config.get("coverage_reports"),
        DEFAULT_CPT_COVERAGE_REPORTS,
        f"{module} {suite_name} CPT coverage reports",
    )
    before_junit = report_signatures(
        discover_reports(root, module, reports, "JUnit report")
    )
    before_coverage = report_signatures(
        discover_reports(root, module, coverage_reports, "CPT coverage report")
    )
    run_output = ""
    exit_code = None
    reason = None
    run_result = "blocked"
    test_results = {}
    coverage = {}
    decision_coverage = {}
    junit_copies = []
    coverage_copies = []
    try:
        completed = subprocess.run(
            command,
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            errors="replace",
            timeout=timeout,
            check=False,
        )
        exit_code = completed.returncode
        run_output = completed.stdout
        run_result = "failed" if exit_code != 0 else "passed"
    except subprocess.TimeoutExpired as exc:
        reason = f"Command timed out after {timeout} seconds"
        run_output = (
            exc.stdout.decode("utf-8", errors="replace")
            if isinstance(exc.stdout, bytes)
            else exc.stdout or ""
        )
    except OSError as exc:
        reason = str(exc)
        run_output = reason

    if reason is None:
        try:
            junit_paths = fresh_reports(
                root, module, reports, before_junit, "JUnit report"
            )
            if not junit_paths:
                raise EvidenceError("The CPT command produced no fresh JUnit XML reports")
            test_results = parse_junit_reports(
                junit_paths, module, expected_test_ids
            )
            coverage_paths = fresh_reports(
                root,
                module,
                coverage_reports,
                before_coverage,
                "CPT coverage report",
            )
            for path in coverage_paths:
                process_coverage, report_decisions = parse_cpt_coverage_report(path)
                for process_id, elements in process_coverage.items():
                    coverage.setdefault(process_id, set()).update(elements)
                for decision_id, rule_ids in report_decisions.items():
                    decision_coverage.setdefault(decision_id, set()).update(rule_ids)
            digest = module_suite_digest(module, suite_name)
            junit_copies = copy_reports(
                root,
                module,
                junit_paths,
                VALIDATION / "cpt" / digest / f"run-{run_number}" / "junit",
            )
            coverage_copies = copy_reports(
                root,
                module,
                coverage_paths,
                VALIDATION / "cpt" / digest / f"run-{run_number}" / "coverage",
            )
        except EvidenceError as exc:
            run_result = "failed"
            reason = str(exc)
    if exit_code != 0 and reason is None:
        run_result = "failed"
    return {
        "exit_code": exit_code,
        "result": run_result,
        "reason": reason,
        "reports": junit_copies,
        "test_results": test_results,
        "coverage_reports": coverage_copies,
        "coverage_by_process": coverage_json(coverage),
        "decision_coverage_by_id": coverage_json(decision_coverage),
        "coverage_available": bool(coverage_copies),
        "output": run_output,
    }


def test_runs_match(first, second):
    if set(first) != set(second):
        return False
    return all(
        first[test_id].get("result") == second[test_id].get("result")
        and sorted(first[test_id].get("invocations", []))
        == sorted(second[test_id].get("invocations", []))
        for test_id in first
    )


def record_test_repeat(root, plan, args, mapping):
    suite_key = (args.target, args.scenario)
    suite_config = plan.test_contract["cpt_suites"].get(suite_key)
    if suite_config is None:
        raise EvidenceError(f"{suite_key}: CPT test suite is not in validation-evidence.json")
    command = list(args.command or [])
    if command and command[0] == "--":
        command = command[1:]
    if not command or not command[0]:
        raise EvidenceError("Supply the CPT test command after --")

    expected_test_ids = expected_cpt_test_ids(mapping)
    runs = [
        run_cpt_test_suite(
            root,
            args.target,
            args.scenario,
            command,
            args.timeout,
            run_number,
            suite_config,
            expected_test_ids,
        )
        for run_number in (1, 2)
    ]
    reason = None
    if any(run["result"] == "blocked" for run in runs):
        reason = next(run["reason"] for run in runs if run["result"] == "blocked")
        result = "blocked"
    elif not test_runs_match(runs[0]["test_results"], runs[1]["test_results"]):
        changed = sorted(
            set(runs[0]["test_results"]) | set(runs[1]["test_results"])
        )
        changed = [
            test_id for test_id in changed
            if runs[0]["test_results"].get(test_id) != runs[1]["test_results"].get(test_id)
        ]
        reason = "CPT repeat results differ for: " + ", ".join(changed)
        result = "failed"
    elif any(run["result"] == "failed" for run in runs):
        reason = next(run["reason"] for run in runs if run["reason"]) if any(
            run["reason"] for run in runs
        ) else "A CPT test run failed"
        result = "failed"
    else:
        result = "passed"

    exit_codes = [run["exit_code"] for run in runs]
    exit_code = None if result == "blocked" else next(
        (code for code in exit_codes if code not in (None, 0)), 0
    )
    output = "\n".join(
        f"--- CPT run {index} ---\n{run['output']}"
        for index, run in enumerate(runs, start=1)
    )
    return {
        "command": command,
        "exit_code": exit_code,
        "result": result,
        "reason": reason,
        "test_runs": [
            {
                "run": index,
                "exit_code": run["exit_code"],
                "result": run["result"],
                "reason": run["reason"],
                "reports": run["reports"],
                "test_results": run["test_results"],
                "coverage_reports": run["coverage_reports"],
                "coverage_by_process": run["coverage_by_process"],
                "decision_coverage_by_id": run["decision_coverage_by_id"],
                "coverage_available": run["coverage_available"],
            }
            for index, run in enumerate(runs, start=1)
        ],
        "test_mapping_digest": test_mapping_digest(mapping, "all"),
        "output": output,
    }


def requirements(root, evidence):
    modules, models = scope(root, evidence)
    inventory = read_json(root / INVENTORY)
    required = {}
    allowed = set()
    timers = {}
    timer_starts = {}
    issues = []
    hashes = {}
    update_hits = {}
    current_updates = {}
    try:
        tests = test_contract(root, inventory)
    except EvidenceError as exc:
        issues.append(str(exc))
        tests = {
            "mode": inventory.get("test_run_mode"),
            "tests": [],
            "suites": {},
            "test_suites": {},
            "modules": inventory.get("modules", []),
        }
    if (
        "test_run_mode" in inventory
        and not source_test_contract_matches_snapshot(
            source_test_contract(tests),
            inventory.get("source_snapshot_test_contract"),
        )
    ):
        issues.append(
            "Test Inventory or C7 suite commands changed after the Step 2 snapshot; "
            "restore the C7 baseline before resetting the source snapshot"
        )
    migrated_test_ids = set()
    mapping = None
    if tests["mode"] == "run":
        try:
            mapping = read_test_mapping(root)
            migrated_test_ids = mapped_migrated_test_ids(mapping)
        except EvidenceError as exc:
            issues.append(str(exc))
            mapping = None
            migrated_test_ids = set()
    test_enabled = test_validation_enabled(tests, migrated_test_ids, mapping)
    if test_enabled:
        test_by_id = {test["id"]: test for test in tests["tests"]}
        for test_id in migrated_test_ids:
            if test_id not in test_by_id:
                issues.append(
                    f"unmapped CPT test {test_id}: migrated test is missing from the Test Inventory"
                )
            elif not tests["test_suites"].get(test_id):
                issues.append(
                    f"{test_id}: no Step 2 test suite records this migrated test"
                )
    source_updates = inventory.get("source_updates")
    if (
        not isinstance(source_updates, dict)
        or set(source_updates) != {module["path"] for module in modules}
    ):
        issues.append(
            "Step 2 inventory lacks the pre-migration due-date source snapshot; run init before conversion"
        )
        source_updates = {}
    source_update_locations = {}
    for module in modules:
        path = module["path"]
        hits = source_updates.get(path)
        if not isinstance(hits, dict) or any(
            not isinstance(operation, str)
            or operation not in {"setjobduedate", "/duedate", "duedate"}
            for operation in hits.values()
        ):
            issues.append(f"{path}: invalid pre-migration due-date source snapshot")
            hits = {}
        try:
            source_update_locations[path] = strings(
                list(hits), f"{path} pre-migration due-date locations"
            )
        except EvidenceError as exc:
            issues.append(str(exc))
            source_update_locations[path] = []
    source_ids = {}
    converted_ids = {}
    converted_documents = {}
    converted_elements = {}
    source_job_types_by_model = {}
    module_paths = {module["path"] for module in modules}

    def need(category, target, kind, scenario=None, method="command"):
        key = (category, target, kind, scenario)
        if key in required:
            issues.append(f"Duplicate required check: {key}")
        required[key] = method
        allowed.add(key)
        return key

    test_run_mode = read_test_run_mode(read_json(root / INVENTORY))
    docker_suites = {}
    cpt_suites = {}
    module_suite_keys = set()
    for module in modules:
        path = module["path"]
        current_updates[path] = scan_module(root, path, module_paths, hashes)
        update_hits[path] = list(current_updates[path])
        need("module", path, "compile")
        need("module", path, "review", method="review")
        need("module", path, "active_timer_updates", method="review")
        suites = module.get("test_suites")
        if not isinstance(suites, list) or not suites:
            issues.append(f"{path}: list every independent test suite")
            continue
        names = []
        for suite in suites:
            if not isinstance(suite, dict):
                issues.append(f"{path}: invalid test suite")
                continue
            name = suite.get("name")
            if not isinstance(name, str) or not name or type(suite.get("requires_docker")) is not bool:
                issues.append(f"{path}: invalid test suite name or Docker requirement")
                continue
            names.append(name)
            suite_key = (path, name)
            module_suite_keys.add(suite_key)
            cpt_suites[suite_key] = suite
            step2_suite = tests["suites"].get(suite_key)
            if test_enabled and (
                step2_suite and suite_has_cpt_tests(step2_suite, mapping)
                or suite_has_added_cpt_tests(path, name, mapping)
            ):
                check_kind = "test_repeat"
            else:
                check_kind = "tests"
            check_key = ("module", path, check_kind, name)
            need("module", path, check_kind, name)
            docker_suites[check_key] = suite["requires_docker"]
        if len(names) != len(set(names)):
            issues.append(f"{path}: duplicate test suite")
        runtime = module.get("runtime_mode")
        if runtime == "spring-boot":
            need("module", path, "configuration")
            need("module", path, "spring_boot_run")
            need("module", path, "executable_jar")
        elif runtime == "external-launcher":
            need("module", path, "configuration")
            need("module", path, "external_launcher")
        elif runtime != "none":
            issues.append(f"{path}: invalid runtime mode")
    if any(docker_suites.values()) and test_run_mode != "migrate_only":
        need("project", ".", "docker_info")
    if test_enabled:
        for suite_key, suite in tests["suites"].items():
            has_cpt_tests = suite_has_cpt_tests(suite, mapping)
            needs_c7_baseline = suite_requires_c7_baseline(suite, tests, mapping)
            if not has_cpt_tests and not needs_c7_baseline:
                continue
            module, name = suite_key
            if suite_key not in module_suite_keys:
                issues.append(
                    f"{module} {name}: Step 2 test suite is missing from validation-evidence.json"
                )
                if has_cpt_tests:
                    need("module", module, "test_repeat", name)
            if needs_c7_baseline:
                need("module", module, "c7_baseline", name)
        if expected_cpt_test_ids(mapping):
            need("project", ".", "test_freeze", method="snapshot")
        need("project", ".", "test_parity", method="computed")
        need("project", ".", "coverage_parity", method="computed")
        reviewed_classes = set()
        for test in tests["tests"]:
            if test["id"] not in migrated_test_ids:
                continue
            class_target = f"{test['module']}:{test['class_name']}"
            if class_target not in reviewed_classes:
                need("test", class_target, "assertion_strength", method="review")
                reviewed_classes.add(class_target)
            need("test", test["id"], "mock_boundary", method="review")
    tests["cpt_suites"] = cpt_suites

    converted_paths = []
    for model in models:
        source = model["source_path"]
        converted = model.get("path")
        if not isinstance(converted, str):
            issues.append(f"{source}: missing converted copy path")
            continue
        copy_path = project_path(root, converted, "converted copy")
        converted_paths.append(copy_path)
        need("model", converted, "lint")
        need("model", converted, "deployment")
        need("model", converted, "review", method="review")
        timers[converted] = []
        try:
            source_document, document = read_model(root, source, converted)
        except EvidenceError as exc:
            issues.append(str(exc))
            continue
        converted_documents[converted] = document
        if model_type(source) == "bpmn":
            job_types = set()
            for definition in document.findall(f".//{ZEEBE}taskDefinition"):
                job_type = definition.get("type")
                if (
                    isinstance(job_type, str)
                    and job_type.strip()
                    and job_type == job_type.strip()
                    and not job_type.startswith("=")
                ):
                    job_types.add(job_type)
            source_job_types_by_model[source] = job_types
        for process in document.findall(f"{BPMN}process"):
            process_id = process.get("id")
            if process_id:
                converted_elements[(converted, process_id)] = {
                    element.get("id")
                    for element in process.iter()
                    if element.get("id")
                }
        for path in (source, converted):
            file = project_path(root, path, "model snapshot", must_exist=True)
            hashes[file.relative_to(root).as_posix()] = file_digest(file)
        processes = model.get("processes")
        if not isinstance(processes, list) or any(not isinstance(item, dict) for item in processes):
            issues.append(f"{converted}: invalid process inventory")
            continue
        declared = [item.get("id") for item in processes]
        if any(not isinstance(pid, str) or not pid for pid in declared) or len(set(declared)) != len(declared):
            issues.append(f"{converted}: duplicate or missing process ID")
            continue
        if model_type(converted) == "dmn":
            if declared:
                issues.append(f"{converted}: DMN cannot contain executable processes")
            continue
        for inventory, tree in ((source_ids, source_document), (converted_ids, document)):
            ids = [process.get("id") for process in tree.findall(f"{BPMN}process")]
            if not all(ids) or len(ids) != len(set(ids)):
                issues.append(f"{converted}: BPMN process IDs are missing or repeated")
            inventory[converted] = {pid for pid in ids if pid}
        executable = {
            process.get("id"): process
            for process in document.findall(f"{BPMN}process")
            if process.get("isExecutable") in ("true", "1")
        }
        if set(declared) != set(executable):
            issues.append(f"{converted}: process inventory differs from executable BPMN processes")
        for item in processes:
            pid = item["id"]
            if pid not in executable:
                continue
            process = executable[pid]
            target = f"{converted}#{pid}"
            standalone = item.get("standalone")
            scenarios = item.get("scenarios")
            if standalone is True:
                if not isinstance(scenarios, list) or "normal" not in scenarios:
                    issues.append(f"{target}: direct-start scenarios must include normal")
                    continue
                try:
                    strings(scenarios, f"{target} scenarios")
                except EvidenceError as exc:
                    issues.append(str(exc))
                    continue
                need("process", target, "worker_input_inventory", method="review")
                for scenario in scenarios:
                    need("process", target, "process_path", scenario)
            elif standalone is False and isinstance(item.get("covering_test"), str) and item["covering_test"]:
                if scenarios != []:
                    issues.append(f"{target}: a non-standalone process has no direct-start scenarios")
                need("process", target, "process_path", item["covering_test"])
            else:
                issues.append(f"{target}: mark standalone or name its covering test")
            for assertion in process_assertions(process):
                need("process", target, assertion)
            allowed.update(("process", target, assertion, None) for assertion in ASSERTIONS)
        source_timers = repeating_starts(source_document)
        converted_timers = repeating_starts(document)
        for (process_id, start_id) in sorted(source_timers.keys() | converted_timers.keys()):
            old = source_timers.get((process_id, start_id))
            new = converted_timers.get((process_id, start_id))
            cycle = new if new is not None else old
            if new is not None and (
                not new or new.startswith("=") or "${" in new or "#{" in new
            ):
                issues.append(f"{converted}#{process_id}#{start_id}: unresolved timer cycle")
            disposition = ("add" if old is None else "remove" if new is None
                           else "preserve" if old == new else "change")
            target = f"{converted}#{process_id}#{start_id}"
            review = need("timer", target, "disposition", method="review")
            timer_starts[review] = {"disposition": disposition, "model_path": converted,
                                   "process_id": process_id, "start_id": start_id, "cycle": cycle}
            if new is not None:
                preflight = need("timer", target, "preflight")
                timer_starts[preflight] = timer_starts[review]
                timers[converted].append(preflight)
    if len(converted_paths) != len(set(converted_paths)):
        issues.append("Converted copies must be distinct")

    active_timer_locations = {}
    active_timer_decisions = {}
    decision = evidence.get("active_timer_update_decision")
    if decision is not None:
        if (
            not isinstance(decision, dict)
            or decision.get("status") != "approved"
            or decision.get("strategy") != "message_rearm"
            or not concrete_reference(decision.get("reference"))
            or not isinstance(decision.get("target_version"), str)
            or TARGET_VERSION.fullmatch(decision["target_version"]) is None
            or not isinstance(decision.get("updates"), list)
            or not decision["updates"]
        ):
            issues.append(
                "Active timer updates need an approved message_rearm decision, "
                "a concrete reference, target version, and timer mapping"
            )
        else:
            source_targets = {}
            seen_migrated_caller_locations = set()
            for entry in decision["updates"]:
                if not isinstance(entry, dict):
                    issues.append("Each active timer update needs a timer mapping")
                    continue
                caller_mappings = entry.get("caller_mappings")
                if not isinstance(caller_mappings, list) or not caller_mappings:
                    issues.append(
                        "Each active timer update needs at least one caller mapping"
                    )
                    continue
                model_path = entry.get("model_path")
                process_id = entry.get("process_id")
                rearm_process_id = entry.get("rearm_process_id")
                rearm_call_activity_id = entry.get("rearm_call_activity_id")
                timer_id = entry.get("timer_id")
                message_name = entry.get("message_name")
                correlation_key = entry.get("correlation_key_variable")
                date_variable = entry.get("date_variable")
                message_date_variable = entry.get("message_date_variable")
                if (
                    not isinstance(model_path, str)
                    or model_path not in converted_documents
                    or not isinstance(process_id, str)
                    or process_id not in converted_ids.get(model_path, set())
                    or not isinstance(rearm_process_id, str)
                    or rearm_process_id not in converted_ids.get(model_path, set())
                    or not isinstance(rearm_call_activity_id, str)
                    or not rearm_call_activity_id
                    or not isinstance(timer_id, str)
                    or not timer_id
                    or not isinstance(message_name, str)
                    or not message_name
                    or not isinstance(correlation_key, str)
                    or not correlation_key
                    or not isinstance(date_variable, str)
                    or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*", date_variable)
                    or not isinstance(message_date_variable, str)
                    or not re.fullmatch(
                        r"[A-Za-z][A-Za-z0-9_]*", message_date_variable
                    )
                ):
                    issues.append("Active timer update mapping has invalid scope or identifiers")
                    continue
                document = converted_documents[model_path]
                process = next(
                    (
                        candidate
                        for candidate in document.findall(f"{BPMN}process")
                        if candidate.get("id") == process_id
                    ),
                    None,
                )
                if process is None or process.get("isExecutable") not in ("true", "1"):
                    issues.append(
                        f"{model_path}#{process_id}: message_rearm mapping requires an executable BPMN process"
                    )
                    continue
                rearm_process = next(
                    (
                        candidate
                        for candidate in document.findall(f"{BPMN}process")
                        if candidate.get("id") == rearm_process_id
                    ),
                    None,
                )
                if (
                    rearm_process is None
                    or rearm_process.get("isExecutable") not in ("true", "1")
                ):
                    issues.append(
                        f"{model_path}#{rearm_process_id}: rearm call activity must belong to an executable BPMN process"
                    )
                    continue
                if not supports_message_rearm(
                    document,
                    process,
                    timer_id,
                    message_name,
                    correlation_key,
                    date_variable,
                    message_date_variable,
                    rearm_process_id,
                    rearm_call_activity_id,
                ):
                    issues.append(
                        f"{model_path}#{process_id}: converted model lacks the mapped message-driven timer rearm path"
                    )
                    continue
                target = f"{model_path}#{process_id}#{timer_id}"
                details = {
                    "target": target,
                    "model_path": model_path,
                    "process_id": process_id,
                    "rearm_process_id": rearm_process_id,
                    "rearm_call_activity_id": rearm_call_activity_id,
                    "timer_id": timer_id,
                    "strategy": decision["strategy"],
                    "message_name": message_name,
                    "correlation_key_variable": correlation_key,
                    "date_variable": date_variable,
                    "message_date_variable": message_date_variable,
                    "target_version": decision["target_version"],
                    "reference": decision["reference"],
                }
                existing = active_timer_decisions.get(target)
                if existing is not None and any(
                    existing[field] != details[field] for field in details
                ):
                    issues.append(f"{target}: conflicting active timer update decisions")
                    continue
                valid_callers = []
                for caller in caller_mappings:
                    if not isinstance(caller, dict):
                        issues.append(f"{target}: each caller mapping must be an object")
                        continue
                    module = caller.get("module")
                    source_locations = caller.get("source_locations")
                    migrated_caller_location = caller.get("migrated_caller_location")
                    if (
                        not isinstance(module, str)
                        or module not in module_paths
                        or not isinstance(source_locations, list)
                        or not source_locations
                        or any(
                            not isinstance(location, str) or not location
                            for location in source_locations
                        )
                        or len(source_locations) != len(set(source_locations))
                        or not isinstance(migrated_caller_location, str)
                        or not migrated_caller_location
                    ):
                        issues.append(f"{target}: caller mapping has invalid scope or locations")
                        continue
                    if any(
                        location not in source_update_locations.get(module, [])
                        for location in source_locations
                    ):
                        issues.append(
                            f"{module}: active timer mapping cites an undetected pre-migration due-date location"
                        )
                        continue
                    if any(
                        location in update_hits.get(module, [])
                        for location in source_locations
                    ):
                        issues.append(
                            f"{module}: mapped C7 due-date location remains in the migrated source"
                        )
                        continue
                    retained = {
                        current_location
                        for location in source_locations
                        for current_location, operation in current_updates[module].items()
                        if location.rsplit(":", 2)[0]
                        == current_location.rsplit(":", 2)[0]
                        and operation == source_updates[module][location]
                    }
                    if retained:
                        issues.append(
                            f"{module}: mapped C7 due-date location remains in the migrated source at "
                            + ", ".join(sorted(retained))
                        )
                        continue
                    try:
                        caller_is_valid = valid_migrated_caller_location(
                            root, module, migrated_caller_location, hashes
                        )
                    except EvidenceError as exc:
                        issues.append(str(exc))
                        continue
                    if (
                        not caller_is_valid
                        or migrated_caller_location in update_hits.get(module, [])
                    ):
                        issues.append(
                            f"{module}: migrated caller location does not identify current module source code"
                        )
                        continue
                    if any(
                        source_targets.get((module, location), target) != target
                        for location in source_locations
                    ):
                        issues.append(
                            "A due-date location cannot map to different timers"
                        )
                        continue
                    if migrated_caller_location in seen_migrated_caller_locations:
                        issues.append(
                            f"{module}: migrated caller location can map to only one due-date caller"
                        )
                        continue
                    seen_migrated_caller_locations.add(migrated_caller_location)
                    valid_callers.append(
                        {
                            "module": module,
                            "source_locations": source_locations,
                            "migrated_caller_location": migrated_caller_location,
                        }
                    )
                if not valid_callers:
                    continue
                if existing is None:
                    details["modules"] = []
                    details["caller_mappings"] = []
                    active_timer_decisions[target] = details
                    need("timer", target, "active_instance_reschedule")
                active_decision = active_timer_decisions[target]
                for caller in valid_callers:
                    if caller["module"] not in active_decision["modules"]:
                        active_decision["modules"].append(caller["module"])
                    active_decision["caller_mappings"].append(caller)
                    for location in caller["source_locations"]:
                        source_targets[(caller["module"], location)] = target
                    active_timer_locations.setdefault(caller["module"], set()).update(
                        caller["source_locations"]
                    )
            if not active_timer_decisions:
                issues.append("Approved active timer decision does not map a detected timer update")

    deployment_sets = {}
    model_sets = {}
    duplicates = {}
    declared_sets = evidence.get("deployment_sets", [])
    if not isinstance(declared_sets, list) or (models and not declared_sets):
        issues.append("Declare the intended deployment sets for all models")
        declared_sets = []
    for entry in declared_sets:
        if not isinstance(entry, dict) or not isinstance(entry.get("name"), str) or not entry["name"]:
            issues.append("Every deployment set needs a name, modules, and models")
            continue
        name = entry["name"]
        if name in deployment_sets:
            issues.append(f"Duplicate deployment set: {name}")
            continue
        try:
            set_modules = strings(entry.get("modules"), f"{name} modules")
            set_models = strings(entry.get("models"), f"{name} models")
        except EvidenceError as exc:
            issues.append(str(exc))
            continue
        if set(set_modules) - module_paths or set(set_models) - set(timers):
            issues.append(f"{name}: deployment set includes unknown modules or models")
        deployment_sets[name] = {"modules": set_modules, "models": set_models}
        need("deployment_set", name, "preflight", method="review")
        for model_path in set_models:
            if model_path in model_sets:
                issues.append(f"{model_path}: model belongs to multiple deployment sets")
            model_sets[model_path] = name
        for inventory in (source_ids, converted_ids):
            ids = {}
            for model_path in set_models:
                for process_id in inventory.get(model_path, set()):
                    ids.setdefault(process_id, []).append(model_path)
            for process_id, paths in ids.items():
                if len(paths) > 1:
                    duplicates.setdefault((name, process_id), {})[
                        "source" if inventory is source_ids else "converted"
                    ] = paths
    for name, process_id in duplicates:
        need("deployment_set", name, "duplicate_process_id", process_id, method="review")
    if set(model_sets) != set(timers):
        issues.append("Each converted model must belong to exactly one deployment set")
    if models and module_paths - {path for group in deployment_sets.values() for path in group["modules"]}:
        issues.append("Include each migrated module in its deployment set inventory")
    for details in active_timer_decisions.values():
        for module in details["modules"]:
            if not any(
                module in group["modules"] and details["model_path"] in group["models"]
                for group in deployment_sets.values()
            ):
                issues.append(
                    f"{details['target']}: module {module} and its mapped model must share the same deployment set"
                )
    for name in ("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
                 "settings.gradle.kts", "gradle.properties"):
        file = root / name
        if file.is_symlink():
            raise EvidenceError(f"Cannot scan symlinked build configuration: {file}")
        if file.is_file():
            hashes[name] = file_digest(file)
    snapshot = {
        "modules": modules,
        "models": models,
        "deployment_sets": declared_sets,
        "source_update_locations": source_update_locations,
        "active_timer_update_decision": decision,
        "test_contract": test_contract_snapshot(tests, include_modules=False),
        "files": hashes,
    }
    source_digest = hashlib.sha256(
        json.dumps(snapshot, sort_keys=True).encode("utf-8")
    ).hexdigest()
    return ValidationPlan(
        required, allowed, timers, timer_starts, docker_suites, model_sets,
        duplicates, update_hits, source_update_locations, active_timer_locations,
        active_timer_decisions, source_digest, issues, tests, converted_elements,
        source_ids, source_job_types_by_model,
    )


def check_reference(key):
    digest = hashlib.sha256(json.dumps(key).encode("utf-8")).hexdigest()[:20]
    return (LOGS / f"{digest}.json").as_posix()


def write_check_log(root, key, check):
    reference = check_reference(key)
    write_json(root, Path(reference), check)
    return reference


def empty_test_mapping(inventory):
    return {
        "schema_version": 1,
        "baseline": {
            "commit": inventory.get("source_snapshot_commit"),
            "source_digest": inventory.get("source_snapshot_sha256"),
            "suites": [],
            "coverage_available": False,
            "coverage": {},
            "continue_without_baseline": None,
        },
        "tests": [],
        "freeze": {"files": {}},
        "test_changes": [],
        "mock_changes": [],
    }


def ensure_test_mapping(root, inventory):
    mapping = read_test_mapping(root) or empty_test_mapping(inventory)
    if not mapping["baseline"].get("source_digest"):
        mapping["baseline"] = empty_test_mapping(inventory)["baseline"]
    return mapping


def verify_unchanged_source(root, inventory):
    expected = inventory.get("source_files")
    if not isinstance(expected, dict):
        raise EvidenceError("Step 2 inventory lacks the C7 source file snapshot")
    current_test_contract = validate_source_snapshot_test_contract(root, inventory)
    current = collect_source_files(
        root,
        strings(inventory.get("modules"), "Step 2 modules"),
        strings(inventory.get("models"), "Step 2 models"),
        current_test_contract,
        require_inventory_files=True,
    )
    changed = sorted(
        path
        for path in set(expected) | set(current)
        if expected.get(path) != current.get(path)
    )
    if changed:
        raise EvidenceError(
            "C7 baseline must run before source changes: " + ", ".join(changed[:10])
        )
    digest = source_snapshot_digest(
        inventory["modules"],
        inventory["models"],
        expected,
        inventory.get("source_snapshot_test_contract"),
    )
    if digest != inventory.get("source_snapshot_sha256"):
        raise EvidenceError("Step 2 C7 source snapshot digest is invalid")


def aggregate_baseline_results(contract, baseline_suites):
    suite_results = {
        (suite.get("module"), suite.get("suite")): suite
        for suite in baseline_suites
        if isinstance(suite, dict)
    }
    results = {}
    for test in contract["tests"]:
        memberships = contract["test_suites"].get(test["id"], [])
        if not memberships and test["handling"] == "Report only":
            memberships = [
                suite_key
                for suite_key, suite in suite_results.items()
                if suite_key[0] == test["module"]
                and isinstance(suite.get("test_results"), dict)
                and test["id"] in suite["test_results"]
            ]
        if not memberships:
            continue
        suite_statuses = []
        invocation_statuses = []
        all_captured = True
        for suite_key in memberships:
            suite = suite_results.get(suite_key)
            if suite is None or suite.get("result") != "passed":
                all_captured = False
                continue
            test_result = suite.get("test_results", {}).get(test["id"])
            if not isinstance(test_result, dict):
                all_captured = False
                continue
            suite_statuses.append(test_result.get("result"))
            invocation_statuses.extend(test_result.get("invocations", []))
        if all_captured and suite_statuses:
            results[test["id"]] = {
                "test_kind": test["test_kind"],
                "handling": test["handling"],
                "c7_result": aggregate_test_status(suite_statuses),
                "c7_invocations": invocation_statuses,
            }
        else:
            results[test["id"]] = {
                "test_kind": test["test_kind"],
                "handling": test["handling"],
                "c7_result": None,
                "c7_invocations": invocation_statuses,
            }
    return results


def update_baseline_ledger(mapping, contract):
    previous = {test.get("c7_id"): test for test in mapping["tests"] if isinstance(test, dict)}
    results = aggregate_baseline_results(contract, mapping["baseline"]["suites"])
    updated = []
    for test in contract["tests"]:
        current = dict(previous.get(test["id"], {}))
        current.update(
            c7_id=test["id"],
            test_kind=test["test_kind"],
            handling=test["handling"],
            c7_result=results.get(test["id"], {}).get("c7_result"),
        )
        if test["handling"] == "Report only" and current.get("status") is None:
            current["status"] = "manual"
        updated.append(current)
    added = [
        test for test in mapping["tests"]
        if isinstance(test, dict) and test.get("status") == "added"
    ]
    mapping["tests"] = updated + added
    update_baseline_ledger_coverage(mapping)


def aggregate_baseline_coverage(baseline_suites):
    coverage = {}
    available = False
    for suite in baseline_suites:
        if suite.get("result") != "passed":
            continue
        if suite.get("coverage_available") is True:
            available = True
        for process_id, elements in suite.get("coverage_by_process", {}).items():
            coverage.setdefault(process_id, set()).update(elements)
    return available, coverage_json(coverage)


def update_baseline_ledger_coverage(mapping):
    available, coverage = aggregate_baseline_coverage(mapping["baseline"]["suites"])
    mapping["baseline"]["coverage_available"] = available
    mapping["baseline"]["coverage"] = coverage


def record_c7_baseline(root, args):
    if args.action not in ("run", "block"):
        raise EvidenceError("C7 baseline accepts only run or block")
    inventory = read_json(root / INVENTORY)
    contract = test_contract(root, inventory)
    if contract["mode"] != "run":
        raise EvidenceError("C7 baseline checks require test_run_mode run")
    key = (args.type, args.target, args.kind, args.scenario)
    suite_key = (args.target, args.scenario)
    suite = contract["suites"].get(suite_key)
    if key[0] != "module" or key[2] != "c7_baseline" or suite is None:
        raise EvidenceError(f"Unexpected C7 baseline check: {key}")
    if (
        not suite["migrate_test_ids"]
        and not suite_report_only_test_ids(suite, contract)
    ):
        raise EvidenceError(
            f"{key}: the suite has no Test Inventory tests marked Migrate or Report only"
        )
    mapping = ensure_test_mapping(root, inventory)
    command = list(getattr(args, "command", []) or [])
    if command and command[0] == "--":
        command = command[1:]
    if args.action == "run":
        if command != suite["command"]:
            raise EvidenceError(
                f"{key}: run the exact command recorded in the Step 2 test inventory"
            )
        if args.timeout is not None and args.timeout <= 0:
            raise EvidenceError("Command timeout must be positive")
        verify_unchanged_source(root, inventory)

    suite_digest = module_suite_digest(args.target, args.scenario)
    suite_root = VALIDATION / "baseline" / suite_digest
    previous_junit = {}
    previous_coverage = {}
    if args.action == "run":
        previous_junit = report_signatures(
            discover_reports(root, args.target, suite["reports"], "JUnit report")
        )
        previous_coverage = report_signatures(
            discover_reports(root, args.target, suite["coverage_reports"], "C7 coverage report")
        )
    output = ""
    exit_code = None
    reason = None
    result = "blocked"
    method = "blocked"
    junit_results = {}
    coverage = {}
    junit_paths = []
    coverage_paths = []
    junit_copies = []
    coverage_copies = []
    if args.action == "block":
        reason = args.reason
        output = reason
    elif args.action == "run":
        method = "command"
        try:
            completed = subprocess.run(
                command,
                cwd=root,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                errors="replace",
                timeout=args.timeout,
                check=False,
            )
            exit_code = completed.returncode
            output = completed.stdout
        except subprocess.TimeoutExpired as exc:
            reason = f"Command timed out after {args.timeout} seconds"
            output = (
                exc.stdout.decode("utf-8", errors="replace")
                if isinstance(exc.stdout, bytes)
                else exc.stdout or ""
            )
        except OSError as exc:
            reason = str(exc)
            output = reason
        if reason is None:
            try:
                junit_paths = fresh_reports(
                    root, args.target, suite["reports"], previous_junit, "JUnit report"
                )
                if not junit_paths:
                    raise EvidenceError("The baseline command produced no fresh JUnit XML reports")
                junit_copies = copy_reports(
                    root, args.target, junit_paths, suite_root / "junit"
                )
                inventory_test_ids = {
                    test["id"]
                    for test in contract["tests"]
                    if test["module"] == args.target
                }
                junit_results = parse_junit_reports(
                    junit_paths, args.target, inventory_test_ids
                )
                missing = sorted(set(suite["test_ids"]) - set(junit_results))
                if missing:
                    raise EvidenceError(
                        "JUnit reports omit Test Inventory IDs: " + ", ".join(missing)
                    )
                coverage_paths = fresh_reports(
                    root,
                    args.target,
                    suite["coverage_reports"],
                    previous_coverage,
                    "C7 coverage report",
                )
                coverage_copies = copy_reports(
                    root, args.target, coverage_paths, suite_root / "coverage"
                )
                for path in coverage_paths:
                    for process_id, elements in parse_c7_coverage_report(path).items():
                        coverage.setdefault(process_id, set()).update(elements)
                verify_unchanged_source(root, inventory)
                result = "passed"
            except EvidenceError as exc:
                result = "failed"
                reason = str(exc)

    reference = check_reference(key)
    suite_record = {
        "module": args.target,
        "suite": args.scenario,
        "command": command if args.action == "run" else None,
        "result": result,
        "reason": reason,
        "reports": (suite_root / "junit").as_posix() if junit_copies else None,
        "report_files": junit_copies,
        "coverage_reports": (suite_root / "coverage").as_posix() if coverage_copies else None,
        "coverage_report_files": coverage_copies,
        "coverage_available": bool(coverage_paths),
        "coverage_by_process": coverage_json(coverage),
        "test_results": junit_results,
        "evidence_path": reference,
    }
    baseline_suites = mapping["baseline"]["suites"]
    baseline_suites[:] = [
        item
        for item in baseline_suites
        if (item.get("module"), item.get("suite")) != suite_key
    ]
    baseline_suites.append(suite_record)
    update_baseline_ledger(mapping, contract)
    write_json(root, TEST_MAPPING, mapping)

    check = {
        "run_id": inventory.get("run_id"),
        "type": args.type,
        "target": args.target,
        "kind": args.kind,
        "scenario": args.scenario,
        "method": method,
        "command": command if args.action == "run" else None,
        "exit_code": exit_code,
        "result": result,
        "reason": reason,
        "source_digest": inventory.get("source_snapshot_sha256"),
        "baseline_commit": inventory.get("source_snapshot_commit"),
        "reports": junit_copies,
        "test_results": junit_results,
        "coverage_reports": coverage_copies,
        "coverage_by_process": coverage_json(coverage),
        "coverage_available": bool(coverage_paths),
        "output": output,
    }
    write_check_log(root, key, check)
    if args.action == "run" and result == "passed":
        status_counts = {
            status: sum(
                test_result["result"] == status
                for test_result in junit_results.values()
            )
            for status in ("passed", "failed", "skipped", "error")
        }
        print(
            f"RECORDED C7 baseline {args.target}/{args.scenario}: "
            + ", ".join(f"{count} {status}" for status, count in status_counts.items())
        )
    else:
        print(f"{result.upper()} {args.type} {args.target} {args.kind} {args.scenario or ''}")
    if output and args.action == "run":
        print(output, end="" if output.endswith("\n") else "\n")
    return 0 if result == "passed" else 1


def check_key(check):
    return (check.get("type"), check.get("target"), check.get("kind"), check.get("scenario"))


def needs_safe_environment(key):
    return (
        key[0] == "process"
        or key[0] == "model" and key[2] == "deployment"
        or key[0] == "module" and key[2] in RUNTIME_CHECKS
        or key[0] == "timer" and key[2] in ("preflight", "active_instance_reschedule")
    )


def parse_evidence_list(value):
    try:
        parsed = json.loads(value) if isinstance(value, str) else None
    except json.JSONDecodeError as exc:
        raise EvidenceError("Non-timer evidence must be valid JSON") from exc
    if not isinstance(parsed, list):
        raise EvidenceError("Non-timer evidence must be a JSON array")
    return parsed


def parse_observation_time(value):
    if not isinstance(value, str):
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    return parsed if parsed.tzinfo is not None else None


def validate_active_timer_observation(plan, key, check):
    decision = plan.active_timer_decisions[key[1]]
    if (
        check.get("environment") != "local"
        or check.get("target_disposable") is not True
        or check.get("target_version") != decision["target_version"]
        or not isinstance(check.get("isolation_plan"), str)
        or not check["isolation_plan"].strip()
    ):
        raise EvidenceError(
            f"{key}: active timer rescheduling needs the approved version on an isolated disposable local target"
        )
    output = check["output"].strip().splitlines()
    if not output:
        raise EvidenceError(f"{key}: live fixture must emit one JSON observation")
    try:
        result = json.loads(output[-1])
    except json.JSONDecodeError as exc:
        raise EvidenceError(f"{key}: live fixture must end with a JSON observation") from exc
    if not isinstance(result, dict):
        raise EvidenceError(f"{key}: live fixture observation must be an object")
    deployment = result.get("deployment")
    if not isinstance(deployment, dict) or (
        deployment.get("performed") is not True
        or not concrete_reference(deployment.get("reference"))
        or deployment.get("environment") != check["environment"]
        or deployment.get("target_disposable") is not True
        or deployment.get("target_version") != decision["target_version"]
    ):
        raise EvidenceError(f"{key}: live fixture lacks a matching disposable deployment")
    cleanup = result.get("cleanup")
    if not isinstance(cleanup, dict) or (
        cleanup.get("completed") is not True
        or not concrete_reference(cleanup.get("evidence_reference"))
    ):
        raise EvidenceError(f"{key}: live fixture must prove disposable-target cleanup")
    observation = result.get("observation")
    if not isinstance(observation, dict):
        raise EvidenceError(f"{key}: live fixture observation is missing")

    active_timer = observation.get("active_timer")
    if not isinstance(active_timer, dict) or any(
        active_timer.get(field) != decision[field]
        for field in (
            "model_path",
            "process_id",
            "rearm_process_id",
            "rearm_call_activity_id",
            "timer_id",
            "strategy",
            "message_name",
            "correlation_key_variable",
            "date_variable",
            "message_date_variable",
        )
    ):
        raise EvidenceError(f"{key}: live result does not match the approved timer mapping")
    updates = active_timer.get("updates")
    if not isinstance(updates, list) or len(updates) != 2 or any(
        not isinstance(update, dict)
        or update.get("correlated") is not True
        or update.get("timer_active_before_update") is not True
        for update in updates
    ):
        raise EvidenceError(
            f"{key}: live fixture must prove the timer was active before two correlated date updates"
        )
    deadlines = [
        parse_observation_time(updates[0].get("old_deadline")),
        parse_observation_time(updates[0].get("new_deadline")),
        parse_observation_time(updates[1].get("new_deadline")),
    ]
    if (
        any(deadline is None for deadline in deadlines)
        or updates[1].get("old_deadline") != updates[0].get("new_deadline")
    ):
        raise EvidenceError(f"{key}: two date updates must record sequential deadlines")
    first_change = deadlines[1] - deadlines[0]
    second_change = deadlines[2] - deadlines[1]
    if (
        first_change == timedelta(0)
        or second_change == timedelta(0)
        or (first_change > timedelta(0)) == (second_change > timedelta(0))
    ):
        raise EvidenceError(
            f"{key}: two date updates must include an earlier and a later deadline"
        )
    final_deadline = deadlines[2]
    if any(
        type(update.get("old_deadline_fire_count")) is not int
        or update["old_deadline_fire_count"] != 0
        or (checked := parse_observation_time(update.get("checked_after_old_deadline"))) is None
        or not deadlines[index] < checked < final_deadline
        for index, update in enumerate(updates)
    ):
        raise EvidenceError(f"{key}: check zero fires after each obsolete deadline")
    last_active_at = parse_observation_time(
        active_timer.get("final_deadline_last_active_at")
    )
    fired_at = parse_observation_time(active_timer.get("final_deadline_fired_at"))
    if (
        type(active_timer.get("final_deadline_fire_count")) is not int
        or active_timer["final_deadline_fire_count"] != 1
        or last_active_at is None
        or last_active_at >= final_deadline
        or final_deadline - last_active_at > timedelta(seconds=5)
        or fired_at is None
        or fired_at < final_deadline
    ):
        raise EvidenceError(
            f"{key}: final deadline must remain active immediately before firing exactly once"
        )


def validate_risk_check(plan, key, check):
    if check["result"] != "passed":
        return
    category, target, kind, scenario = key
    if category == "deployment_set" or (category == "timer" and kind == "disposition"):
        if not concrete_reference(check.get("reference")):
            raise EvidenceError(f"{key}: cite a concrete approval and review reference")
    if category == "deployment_set" and kind == "duplicate_process_id":
        collision = plan.duplicates[(target, scenario)]
        disposition = check.get("disposition")
        if disposition == "explicit_version":
            if "converted" not in collision:
                raise EvidenceError(f"{key}: a source collision needs a mapped rename")
            if any(
                timer[2] == "preflight"
                and details["process_id"] == scenario
                and details["model_path"] in collision["converted"]
                for timer, details in plan.timer_starts.items()
            ):
                raise EvidenceError(f"{key}: explicit versions cannot preserve a colliding timer start")
        elif disposition != "mapped_rename" or "converted" in collision:
            raise EvidenceError(f"{key}: rename colliding IDs or review explicit-version callers")
    if category == "timer" and kind == "disposition":
        if check.get("disposition") != plan.timer_starts[key]["disposition"]:
            raise EvidenceError(f"{key}: timer disposition does not match the source and converted copies")
    if category == "module" and kind == "active_timer_updates":
        source_hits = set(plan.source_update_locations[target])
        current_hits = set(plan.update_hits[target])
        disposition = check.get("disposition")
        evidence = check.get("non_timer_evidence")
        active_locations = plan.active_timer_locations.get(target, set())
        if current_hits & active_locations:
            raise EvidenceError(f"{key}: a mapped C7 due-date call remains in the migrated source")
        non_timer_locations = (source_hits - active_locations) | (current_hits - source_hits)
        if disposition == "no_updates" and not source_hits and not current_hits and not evidence:
            return
        if active_locations:
            active_decisions = [
                decision
                for decision in plan.active_timer_decisions.values()
                if target in decision["modules"]
            ]
            expected_references = {decision["reference"] for decision in active_decisions}
            if check.get("reference") not in expected_references:
                raise EvidenceError(f"{key}: cite the approved active-timer decision")
            if any(
                not all(
                    exact_location_is_mentioned(check["output"], detail)
                    for detail in (
                        *(
                            caller["migrated_caller_location"]
                            for caller in decision["caller_mappings"]
                            if caller["module"] == target
                        ),
                        decision["message_name"],
                        decision["correlation_key_variable"],
                        decision["message_date_variable"],
                        decision["date_variable"],
                    )
                )
                for decision in active_decisions
            ):
                raise EvidenceError(
                    f"{key}: review note must identify the migrated caller and message mapping"
                )
            expected_disposition = "mixed" if non_timer_locations else "message_rearm"
            if disposition != expected_disposition:
                raise EvidenceError(f"{key}: active timer updates require approved message_rearm evidence")
        elif disposition != "non_timer":
            raise EvidenceError(f"{key}: block active or unclassified due-date updates")
        if non_timer_locations:
            if not isinstance(evidence, list):
                raise EvidenceError(f"{key}: classify each non-timer due-date location")
            locations = [
                item.get("location") for item in evidence if isinstance(item, dict)
            ]
            if len(locations) != len(evidence) or len(locations) != len(set(locations)) or any(
                not isinstance(location, str) for location in locations
            ) or set(locations) != non_timer_locations or any(
                not concrete_reference(item.get("evidence")) for item in evidence
            ):
                raise EvidenceError(
                    f"{key}: classify each non-timer due-date location with concrete evidence"
                )
        elif evidence:
            raise EvidenceError(f"{key}: non-timer evidence must not classify an approved active update")
    if category == "timer" and kind == "active_instance_reschedule":
        validate_active_timer_observation(plan, key, check)
    if category == "timer" and kind == "preflight":
        if (
            check.get("environment") not in SAFE_ENVIRONMENTS
            or check.get("target_disposable") is not True
            or not isinstance(check.get("isolation_plan"), str)
            or not check["isolation_plan"].strip()
            or not isinstance(check.get("target_version"), str)
            or TARGET_VERSION.fullmatch(check["target_version"]) is None
        ):
            raise EvidenceError(f"{key}: use a disposable local or non-production target and cleanup plan")
        output = check["output"].strip().splitlines()
        if not output:
            raise EvidenceError(f"{key}: timer test must emit an observation")
        try:
            observation = json.loads(output[-1])
        except json.JSONDecodeError as exc:
            raise EvidenceError(f"{key}: timer test must end with one JSON observation line") from exc
        if not isinstance(observation, dict):
            raise EvidenceError(f"{key}: timer observation must be an object")
        deployment = observation.get("deployment")
        observed = observation.get("observation")
        cleanup = observation.get("cleanup")
        expected = plan.timer_starts[key]
        if not isinstance(deployment, dict) or (
            deployment.get("performed") is not True
            or not concrete_reference(deployment.get("reference"))
            or deployment.get("environment") != check["environment"]
            or deployment.get("target_disposable") is not True
            or deployment.get("target_version") != check["target_version"]
        ):
            raise EvidenceError(f"{key}: timer observation lacks a matching deployment")
        if not isinstance(observed, dict) or any(
            observed.get(field) != expected[field]
            for field in ("model_path", "process_id", "start_id", "cycle")
        ) or type(observed.get("instances_started")) is not int or observed["instances_started"] < 1:
            raise EvidenceError(f"{key}: timer observation must match this model and its timer start")
        if not isinstance(cleanup, dict) or (
            cleanup.get("completed") is not True
            or not concrete_reference(cleanup.get("evidence_reference"))
        ):
            raise EvidenceError(f"{key}: timer observation needs completed cleanup evidence")


def prerequisites(plan, key):
    category, target, kind, scenario = key
    dependencies = []
    if category == "timer" and kind == "preflight":
        dependencies.append(("timer", target, "disposition", None))
    if category == "timer" and kind == "active_instance_reschedule":
        decision = plan.active_timer_decisions[target]
        dependencies.extend(
            ("module", module, "active_timer_updates", None)
            for module in decision["modules"]
        )
        dependencies.extend(
            (("model", decision["model_path"], "lint", None),
             ("model", decision["model_path"], "review", None))
        )
        deployment_set = plan.model_sets.get(decision["model_path"])
        if deployment_set is not None:
            dependencies.append(("deployment_set", deployment_set, "preflight", None))
    if category == "model" and kind == "deployment":
        dependencies.append(("model", target, "lint", None))
    if category == "process" and kind == "process_path":
        review = ("process", target, "worker_input_inventory", None)
        if review in plan.required:
            dependencies.append(review)
    if (category == "process" and kind != "worker_input_inventory") or (
        category == "model" and kind == "deployment"
    ):
        model = target.split("#", 1)[0]
        deployment_set = plan.model_sets.get(model)
        if deployment_set is not None:
            dependencies.append(("deployment_set", deployment_set, "preflight", None))
            dependencies.extend(
                ("deployment_set", name, "duplicate_process_id", process_id)
                for name, process_id in plan.duplicates if name == deployment_set
            )
        for timer in plan.timers.get(model, []):
            dependencies.extend((("timer", timer[1], "disposition", None), timer))
    if category == "project" and kind == "test_freeze":
        dependencies.extend(
            check for check in plan.required if check[2] == "c7_baseline"
        )
    if category == "module" and kind == "test_repeat":
        freeze_check = ("project", ".", "test_freeze", None)
        baseline_check = ("module", target, "c7_baseline", scenario)
        if freeze_check in plan.required:
            dependencies.append(freeze_check)
        if baseline_check in plan.required:
            dependencies.append(baseline_check)
    if category == "project" and kind in ("test_parity", "coverage_parity"):
        freeze_check = ("project", ".", "test_freeze", None)
        if freeze_check in plan.required:
            dependencies.append(freeze_check)
        dependencies.extend(
            check for check in plan.required if check[2] in ("c7_baseline", "test_repeat")
        )
        if kind == "test_parity":
            dependencies.extend(
                check
                for check in plan.required
                if check[2] in ("assertion_strength", "mock_boundary")
            )
    return dependencies


def approved_test_continuation(root):
    mapping = read_test_mapping(root)
    if mapping is None:
        return False
    decision = mapping["baseline"].get("continue_without_baseline")
    return (
        isinstance(decision, dict)
        and decision.get("decision") == "continue"
        and concrete_reference(decision.get("approved_by"))
        and concrete_reference(decision.get("reason"))
    )


def expected_check_digest(root, plan, key):
    if key[2] == "c7_baseline":
        inventory = read_json(root / INVENTORY)
        return inventory.get("source_snapshot_sha256")
    if key[2] == "test_freeze":
        return None
    return plan.source_digest


def require_prerequisites(root, plan, checks, key, before=None):
    for dependency in prerequisites(plan, key):
        previous = checks.get(dependency)
        approved_blocked_baseline = (
            dependency[2] == "c7_baseline"
            and previous is not None
            and previous[1]["result"] != "passed"
            and approved_test_continuation(root)
        )
        diagnostic_after_failed_suite = (
            key[2] in ("test_parity", "coverage_parity")
            and dependency[2] == "test_repeat"
            and previous is not None
            and previous[1]["result"] in ("failed", "blocked")
        )
        expected_digest = expected_check_digest(root, plan, dependency)
        if (
            previous is None
            or previous[1]["result"] != "passed"
            and not approved_blocked_baseline
            and not diagnostic_after_failed_suite
            or previous[1]["method"] not in (plan.required.get(dependency, "command"), "blocked")
            or expected_digest is not None
            and previous[1].get("source_digest") != expected_digest
            or before is not None and previous[0] >= before
        ):
            raise EvidenceError(f"{key}: {dependency[2]} must pass before execution")


def load_checks(root, evidence, plan, issues):
    checks = {}
    references = evidence.get("checks", [])
    if not isinstance(references, list):
        issues.append("Evidence checks must be an array")
        return checks
    inventory = read_json(root / INVENTORY)
    run_id = inventory["run_id"]
    mapping = None
    if plan.test_contract["mode"] == "run":
        try:
            mapping = read_test_mapping(root)
        except EvidenceError as exc:
            issues.append(str(exc))
        if mapping is not None:
            baseline_references = [
                suite.get("evidence_path")
                for suite in mapping["baseline"].get("suites", [])
                if isinstance(suite, dict) and isinstance(suite.get("evidence_path"), str)
            ]
            references = baseline_references + [
                reference for reference in references if reference not in baseline_references
            ]
    for index, reference in enumerate(references):
        try:
            reject_symlink_components(root, root / reference, "check evidence")
            path = project_path(root, reference, "check evidence", must_exist=True)
            if not path.is_file() or path.is_symlink() or not path.is_relative_to(root / LOGS):
                raise EvidenceError(f"Check evidence must be a file in {LOGS}: {reference}")
            check = read_json(path)
            key = check_key(check)
            if any(not isinstance(value, str) or not value for value in key[:3]) or (
                key[3] is not None and (not isinstance(key[3], str) or not key[3])
            ):
                raise EvidenceError(f"Malformed check in {reference}")
            if check.get("run_id") != run_id and not (
                key[2] == "c7_baseline"
                and check.get("source_digest") == inventory.get("source_snapshot_sha256")
            ):
                raise EvidenceError(f"{key}: check belongs to another migration run")
            if (
                mapping is not None
                and key not in plan.allowed
                and obsolete_test_check_key(key, plan, mapping)
            ):
                continue
            if mapping is not None and key[2] in TEST_LEDGER_CHECK_KINDS:
                digest_kind = (
                    "freeze"
                    if key[2] == "test_freeze"
                    else "review"
                    if key[2] in ("assertion_strength", "mock_boundary")
                    else "all"
                )
                expected_mapping_digest = test_mapping_digest(mapping, digest_kind)
                recorded_mapping_digest = check.get("test_mapping_digest")
                if recorded_mapping_digest != expected_mapping_digest:
                    if (
                        isinstance(recorded_mapping_digest, str)
                        and re.fullmatch(r"[0-9a-f]{64}", recorded_mapping_digest)
                        and (
                            key in plan.allowed
                            or obsolete_test_check_key(key, plan, mapping)
                        )
                    ):
                        continue
                    if (
                        not isinstance(recorded_mapping_digest, str)
                        or re.fullmatch(r"[0-9a-f]{64}", recorded_mapping_digest) is None
                    ):
                        raise EvidenceError(
                            f"{key}: test parity ledger changed after this check"
                        )
            if key not in plan.allowed or key in checks:
                raise EvidenceError(f"Unexpected or duplicate check: {key}")
            method = check.get("method")
            result = check.get("result")
            command = check.get("command")
            exit_code = check.get("exit_code")
            reason = check.get("reason")
            if method == "command":
                if (
                    not isinstance(command, list) or not command
                    or not isinstance(command[0], str) or not command[0]
                    or any(not isinstance(part, str) for part in command)
                ):
                    raise EvidenceError(f"{key}: missing command")
                c7_baseline_captured = (
                    key[2] == "c7_baseline"
                    and isinstance(check.get("reports"), list)
                    and bool(check["reports"])
                    and isinstance(check.get("test_results"), dict)
                    and bool(check["test_results"])
                )
                if result == "passed" and (
                    type(exit_code) is not int
                    or exit_code != 0 and not c7_baseline_captured
                ):
                    raise EvidenceError(f"{key}: passed without exit code 0")
                if result == "failed" and (
                    type(exit_code) is not int
                    or exit_code == 0
                    and key[0] != "timer"
                    and key[2] != "c7_baseline"
                    and not (
                        key[2] == "test_repeat"
                        and isinstance(check.get("test_runs"), list)
                        and len(check["test_runs"]) == 2
                    )
                ):
                    raise EvidenceError(f"{key}: failed without a nonzero exit code or invalid timer evidence")
                if result == "blocked" and exit_code is not None:
                    raise EvidenceError(f"{key}: blocked command has an exit code")
            elif method in ("review", "blocked"):
                if command is not None or exit_code is not None:
                    raise EvidenceError(f"{key}: unexecuted check has a command result")
                if method == "blocked" and result not in ("blocked", "unknown", "not_run"):
                    raise EvidenceError(f"{key}: unexecuted check cannot pass")
                if method == "review" and result not in ("passed", "failed", "blocked"):
                    raise EvidenceError(f"{key}: invalid review result")
                if result == "passed" and not str(check.get("output", "")).strip():
                    raise EvidenceError(f"{key}: passing review lacks evidence")
            elif method in ("snapshot", "computed"):
                if command is not None or exit_code is not None:
                    raise EvidenceError(f"{key}: {method} evidence cannot have a command result")
                if result not in ("passed", "failed", "blocked"):
                    raise EvidenceError(f"{key}: invalid {method} result")
                if result == "passed" and not str(check.get("output", "")).strip():
                    raise EvidenceError(f"{key}: passing {method} evidence lacks output")
            else:
                raise EvidenceError(f"{key}: unsupported evidence method")
            if result not in ("passed", "failed", "blocked", "unknown", "not_run"):
                raise EvidenceError(f"{key}: unsupported result")
            if not isinstance(check.get("output"), str):
                raise EvidenceError(f"{key}: evidence output must be text")
            if key[2] == "test_repeat":
                test_runs = check.get("test_runs")
                if not isinstance(test_runs, list) or len(test_runs) != 2:
                    raise EvidenceError(f"{key}: repeat evidence must contain two test runs")
                for run in test_runs:
                    if (
                        not isinstance(run, dict)
                        or not valid_test_results(run.get("test_results"))
                        or not isinstance(run.get("coverage_by_process"), dict)
                        or not isinstance(run.get("decision_coverage_by_id"), dict)
                        or type(run.get("coverage_available")) is not bool
                    ):
                        raise EvidenceError(f"{key}: repeat evidence has an invalid run")
            if result != "passed" and (not isinstance(reason, str) or not reason.strip()):
                raise EvidenceError(f"{key}: non-passing check needs a reason")
            if needs_safe_environment(key) and result == "passed":
                if check.get("environment") not in SAFE_ENVIRONMENTS:
                    raise EvidenceError(f"{key}: production or unknown runtime target")
            if check.get("source_digest") == plan.source_digest:
                validate_risk_check(plan, key, check)
            checks[key] = (index, check, reference)
        except EvidenceError as exc:
            issues.append(str(exc))
    return checks


def validate_declined_test_checks(root, plan, checks, issues):
    if read_test_run_mode(read_json(root / INVENTORY)) != "migrate_only":
        return
    for key in plan.required:
        entry = checks.get(key)
        if key[2] not in TEST_EXECUTION_KINDS or entry is None:
            continue
        check = entry[1]
        if check.get("result") != "blocked" or check.get("reason") != QUESTION_8_DECLINE_REASON:
            issues.append(
                f"{key}: Question 8 migrate_only checks must be blocked with reason "
                f"{QUESTION_8_DECLINE_REASON!r}"
            )


def report_text(existing, gate, issues):
    lines = existing.splitlines(keepends=True)
    kept = []
    removing = False
    unmarked = False
    for line in lines:
        if line.rstrip() == GATE_HEADING:
            removing = True
            continue
        if removing and line.startswith("## "):
            removing = False
        if removing:
            continue
        if "**Validation gate:**" in line or "migration-validation-gate:" in line:
            unmarked = True
            continue
        kept.append(line)
    if kept and not kept[-1].endswith("\n"):
        kept[-1] += "\n"
    if kept and kept[-1].strip():
        kept.append("\n")
    kept.extend(
        [
            f"{GATE_HEADING}\n",
            "<!-- migration-validation-gate:start -->\n",
            f"**Validation gate:** **{gate}**\n",
            "\n",
            f"Evidence: `{SUMMARY.as_posix()}`. Checks needing attention: {len(issues)}.\n",
        ]
    )
    if issues:
        kept.append("\n")
        for issue in issues[:20]:
            text = issue.replace("\n", " ")
            kept.append(f"- {text}\n")
        if len(issues) > 20:
            kept.append(f"- {len(issues) - 20} more in the evidence summary.\n")
    kept.append("<!-- migration-validation-gate:end -->\n")
    return "".join(kept), unmarked


def obsolete_test_check_key(key, plan, mapping):
    if key == ("project", ".", "test_freeze", None):
        return not expected_cpt_test_ids(mapping)
    if (
        key[0] == "module"
        and key[2] == "test_repeat"
        and key[3] is not None
        and (key[1], key[3]) in plan.test_contract["suites"]
    ):
        return not suite_has_cpt_tests(
            plan.test_contract["suites"][(key[1], key[3])], mapping
        )
    if key[0] != "test" or key[3] is not None:
        return False
    rows = test_rows_by_id(mapping)
    inventory_tests = {
        test["id"]: test
        for test in plan.test_contract["tests"]
        if test["handling"] == "Migrate"
    }
    if key[2] == "mock_boundary":
        inventory_test = inventory_tests.get(key[1])
        ledger_test = rows.get(key[1])
        return (
            inventory_test is not None
            and ledger_test is not None
            and ledger_test.get("status") == "retired"
        )
    if key[2] == "assertion_strength":
        inventory_classes = {
            f"{test['module']}:{test['class_name']}"
            for test in inventory_tests.values()
        }
        if key[1] not in inventory_classes:
            return False
        return not any(
            f"{test['module']}:{test['class_name']}" == key[1]
            and rows.get(test_id, {}).get("status") == "migrated"
            for test_id, test in inventory_tests.items()
        )
    return False


def markdown_cell(value):
    return (
        html.escape(str(value), quote=False)
        .replace("\\", "\\\\")
        .replace("|", "\\|")
        .replace("\n", " ")
    )


def replace_machine_section(existing, heading, content):
    lines = existing.splitlines(keepends=True)
    kept = []
    removing = False
    for line in lines:
        if line.rstrip() == heading:
            removing = True
            continue
        if removing and line.startswith("## "):
            removing = False
        if not removing:
            kept.append(line)
    if content is None:
        return "".join(kept)
    while kept and not kept[-1].strip():
        kept.pop()
    if kept:
        kept.append("\n")
    kept.extend([f"{heading}\n", "\n", content.rstrip() + "\n"])
    return "".join(kept)


def render_test_parity(plan, checks, mapping):
    lines = [
        "<!-- migration-test-parity:start -->",
        "| Camunda 7 test | Camunda 7 result | CPT tests | CPT result (run 1 and run 2) | Status | Notes |",
        "|---|---|---|---|---|---|",
    ]
    repeat_runs = test_repeat_checks(plan, checks)
    tests = mapping.get("tests", [])
    if not tests:
        lines.append("| — | not recorded | — | not recorded | not verified | Test mapping is missing. |")
    for test in tests:
        if not isinstance(test, dict):
            continue
        c7_id = test.get("c7_id") or "Added CPT test"
        c8_ids = test.get("c8_ids")
        if not isinstance(c8_ids, list):
            c8_ids = []
        else:
            c8_ids = [c8_id for c8_id in c8_ids if isinstance(c8_id, str)]
        run_values = [[], []]
        suite_keys = ledger_row_suite_keys(test, plan.test_contract, mapping)
        for c8_id in c8_ids:
            try:
                results = cpt_test_results(c8_id, repeat_runs, suite_keys)
            except EvidenceError:
                results = None
            if results is None:
                run_values[0].append("missing")
                run_values[1].append("missing")
            else:
                for index, result in enumerate(results):
                    run_values[index].append(result or "missing")
        cpt_result = (
            f"run 1: {', '.join(sorted(set(run_values[0])))}, "
            f"run 2: {', '.join(sorted(set(run_values[1])))}"
            if c8_ids
            else "—"
        )
        status = test.get("status") or "not mapped"
        notes = []
        retirement = test.get("retirement")
        if isinstance(retirement, dict) and retirement.get("reason"):
            notes.append(
                f"Retired: {retirement['reason']} "
                f"(approved by {retirement.get('approved_by') or 'not recorded'})"
            )
        if test.get("handling") == "Report only" and status == "manual":
            notes.append("Report only; not verified.")
        lines.append(
            "| "
            + " | ".join(
                markdown_cell(value)
                for value in (
                    c7_id,
                    test.get("c7_result") or "not recorded",
                    ", ".join(c8_ids) or "—",
                    cpt_result,
                    status,
                    " ".join(notes) or "—",
                )
            )
            + " |"
        )
    test_changes = [
        change for change in mapping.get("test_changes", [])
        if isinstance(change, dict)
    ]
    if test_changes:
        lines.extend(["", "**Approved test changes:**"])
        for change in test_changes:
            lines.append(
                f"- `{markdown_cell(change.get('file', ''))}`: "
                f"{markdown_cell(change.get('reason', ''))}. "
                f"Changed from `{markdown_cell(change.get('old_hash') or 'absent')}` "
                f"to `{markdown_cell(change.get('new_hash') or 'absent')}`. "
                f"(approved by {markdown_cell(change.get('approved_by', ''))})"
            )
    mock_changes = [
        change for change in mapping.get("mock_changes", [])
        if isinstance(change, dict)
    ]
    if mock_changes:
        lines.extend(["", "**Approved mock changes:**"])
        for change in mock_changes:
            lines.append(
                f"- `{markdown_cell(change.get('cpt_test_id', ''))}`: "
                f"`{markdown_cell(change.get('mock', ''))}`. "
                f"{markdown_cell(change.get('reason', ''))} "
                f"(approved by {markdown_cell(change.get('approved_by', ''))})"
            )
    lines.append("<!-- migration-test-parity:end -->")
    return "\n".join(lines)


def render_test_coverage(plan, coverage_output):
    c7 = coverage_output.get("c7_coverage", {})
    run1 = coverage_output.get("cpt_run_1", {})
    run2 = coverage_output.get("cpt_run_2", {})
    decisions1 = coverage_output.get("cpt_decisions_run_1", {})
    decisions2 = coverage_output.get("cpt_decisions_run_2", {})
    process_ids = sorted(set(c7) | set(run1) | set(run2))
    decision_ids = sorted(set(decisions1) | set(decisions2))
    lines = [
        "<!-- migration-test-coverage:start -->",
        "| Process or decision | Camunda 7 covered elements | CPT run 1 | CPT run 2 | Status | Notes |",
        "|---|---|---|---|---|---|",
    ]
    if not process_ids and not decision_ids:
        c7_summary = (
            "No Camunda 7 coverage baseline"
            if coverage_output.get("baseline_note", "").startswith("No Camunda 7")
            else "No covered process elements"
        )
        lines.append(
            f"| — | {c7_summary} | — | — | not comparable | "
            f"{coverage_output.get('baseline_note', '')} |"
        )
    for process_id in process_ids:
        c7_elements = c7.get(process_id, [])
        converted_processes = coverage_output.get("process_mappings", {}).get(
            process_id,
            [process_id]
            if any(process == process_id for _, process in plan.converted_elements)
            else [],
        )
        run1_elements = sorted(
            {
                element
                for converted_process in converted_processes
                for element in run1.get(converted_process, [])
            }
        )
        run2_elements = sorted(
            {
                element
                for converted_process in converted_processes
                for element in run2.get(converted_process, [])
            }
        )
        retained = set(
            coverage_output.get("retained_c7_elements", {}).get(
                process_id, c7_elements
            )
        )
        removed = sorted(set(c7_elements) - retained)
        exists = bool(converted_processes)
        if not c7_elements:
            status = "CPT coverage"
        elif not coverage_output.get("baseline_note", "").startswith("Camunda 7"):
            status = "not comparable"
        elif not exists:
            status = "converted process absent"
        else:
            status = (
                "passed"
                if retained.issubset(set(run1_elements))
                and retained.issubset(set(run2_elements))
                else "coverage gap"
            )
        notes = [coverage_output.get("baseline_note", "")]
        if converted_processes and converted_processes != [process_id]:
            notes.append("Converted process IDs: " + ", ".join(converted_processes))
        if removed:
            notes.append("C7 IDs absent from converted copies: " + ", ".join(removed))
        lines.append(
            "| "
            + " | ".join(
                markdown_cell(value)
                for value in (
                    process_id,
                    ", ".join(c7_elements) or "—",
                    ", ".join(run1_elements) or "—",
                    ", ".join(run2_elements) or "—",
                    status,
                    " ".join(note for note in notes if note),
                )
            )
            + " |"
        )
    for decision_id in decision_ids:
        lines.append(
            "| "
            + " | ".join(
                markdown_cell(value)
                for value in (
                    f"DMN {decision_id}",
                    "No C7 decision coverage",
                    ", ".join(decisions1.get(decision_id, [])) or "—",
                    ", ".join(decisions2.get(decision_id, [])) or "—",
                    "CPT coverage",
                    coverage_output.get("baseline_note", "")
                    + " Camunda 8 decision coverage only.",
                )
            )
            + " |"
        )
    lines.append("<!-- migration-test-coverage:end -->")
    return "\n".join(lines)


def report(root):
    issues = []
    checks = {}
    plan = None
    mapping = None
    test_validation = False
    coverage_output = {
        "baseline_note": "No Camunda 7 coverage baseline.",
        "c7_coverage": {},
        "cpt_run_1": {},
        "cpt_run_2": {},
    }
    try:
        evidence = read_json(root / EVIDENCE)
        plan = requirements(root, evidence)
        issues.extend(plan.issues)
        checks = load_checks(root, evidence, plan, issues)
        validate_declined_test_checks(root, plan, checks, issues)
        migrate_only = read_test_run_mode(read_json(root / INVENTORY)) == "migrate_only"
        for key in sorted(plan.required.keys() - checks.keys(), key=lambda item: tuple(str(value) for value in item)):
            issues.append(f"Missing {key[0]} {key[2]}: {key[1]} {key[3] or ''}".strip())
        for key, (index, check, _) in checks.items():
            method = plan.required.get(key, "command")
            if check["method"] not in (method, "blocked"):
                issues.append(f"{key}: expected {method} evidence")
            if check["result"] != "passed":
                issues.append(f"{key}: {check['result']}: {check['reason']}")
            expected_digest = expected_check_digest(root, plan, key)
            if (
                expected_digest is not None
                and check.get("source_digest") != expected_digest
            ):
                issues.append(f"{key}: stale source or deployment-set evidence")
            if key[2] == "c7_baseline" and check.get("baseline_commit") != read_json(
                root / INVENTORY
            ).get("source_snapshot_commit"):
                issues.append(f"{key}: C7 baseline commit differs from the Step 2 snapshot")
            try:
                require_prerequisites(root, plan, checks, key, before=index)
            except EvidenceError as exc:
                issues.append(str(exc))
            if not migrate_only and plan.docker_suites.get(key):
                probe = checks.get(("project", ".", "docker_info", None))
                if probe is None or probe[0] >= index:
                    issues.append(f"{key}: Docker probe must precede the suite")
                if check.get("failure_class") == "docker_unavailable" and (
                    probe is None or probe[1]["result"] != "failed"
                ):
                    issues.append(f"{key}: Docker was not shown to be unavailable")
        if any(
            key[0] == "module" and key[2] in ("tests", "test_repeat")
            and check[1].get("failure_class") == "docker_unavailable"
            and not plan.docker_suites.get(key, False)
            for key, check in checks.items()
        ):
            issues.append("A non-Docker test was classified as Docker unavailable")
        if plan.test_contract["mode"] == "run":
            try:
                mapping = read_test_mapping(root)
                test_validation = test_validation_enabled(
                    plan.test_contract, mapped_migrated_test_ids(mapping), mapping
                )
                if test_validation:
                    if mapping is None:
                        mapping = read_test_mapping(root, required=True)
                    rows = test_rows_by_id(mapping)
                    mapped_c8_ids = mapped_cpt_test_ids(plan.test_contract, rows)
                    issues.extend(validate_test_freeze(root, plan, mapping))
                    issues.extend(test_parity_issues(plan, checks, mapping))
                    coverage_issues, coverage_output = coverage_parity_issues(
                        plan, checks, mapping
                    )
                    issues.extend(coverage_issues)
                    for test in mapping["tests"]:
                        if isinstance(test, dict) and test.get("status") == "migrated":
                            issues.extend(
                                test_mock_issues(
                                    test,
                                    mapping,
                                    mapped_c8_ids,
                                    plan.source_job_types_by_model,
                                )
                            )
            except EvidenceError as exc:
                issues.append(str(exc))
    except EvidenceError as exc:
        issues.append(str(exc))
    path = root / REPORT
    if path.is_symlink():
        raise EvidenceError("Refusing to replace a symlinked MIGRATION_REPORT.md")
    existing = path.read_text(encoding="utf-8") if path.exists() else ""
    if plan is not None and test_validation:
        report_mapping = mapping or {
            "tests": [],
            "test_changes": [],
            "mock_changes": [],
            "baseline": {"coverage_available": False, "coverage": {}},
        }
        existing = replace_machine_section(
            existing, TEST_PARITY_HEADING, render_test_parity(plan, checks, report_mapping)
        )
        existing = replace_machine_section(
            existing, TEST_COVERAGE_HEADING, render_test_coverage(plan, coverage_output)
        )
    else:
        existing = replace_machine_section(existing, TEST_PARITY_HEADING, None)
        existing = replace_machine_section(existing, TEST_COVERAGE_HEADING, None)
    issues = list(dict.fromkeys(issues))
    _, unmarked = report_text(existing, "NOT READY", issues)
    if unmarked:
        issues.append("Found an unmarked validation gate in MIGRATION_REPORT.md")
    gate = "NOT READY" if issues else "READY"
    summary = {
        "schema_version": 1,
        "gate": gate,
        "source_snapshot_sha256": plan.source_digest if plan else None,
        "issues": issues,
        "checks": [
            {**{key: value for key, value in check.items() if key != "output"}, "evidence_path": reference}
            for _, check, reference in checks.values()
        ],
    }
    write_json(root, SUMMARY, summary)
    block, _ = report_text(existing, gate, issues)
    write_file(root, REPORT, block)
    print(f"{gate}: {len(issues)} validation issue(s). See {root / SUMMARY}")
    return 0 if gate == "READY" else 1


def record(root, args):
    if args.kind == "c7_baseline":
        return record_c7_baseline(root, args)
    evidence = read_json(root / EVIDENCE)
    plan = requirements(root, evidence)
    if plan.issues:
        raise EvidenceError("; ".join(plan.issues))
    key = (args.type, args.target, args.kind, args.scenario)
    if key not in plan.allowed:
        raise EvidenceError(f"Check is not in the migration scope: {key}")
    test_run_mode = read_test_run_mode(read_json(root / INVENTORY))
    if test_run_mode == "migrate_only" and key[2] in TEST_EXECUTION_KINDS:
        if args.action != "block":
            raise EvidenceError(f"{key}: Question 8 selected Migrate tests only; record a blocker")
        if args.reason != QUESTION_8_DECLINE_REASON:
            raise EvidenceError(f"{key}: use the exact Question 8 reason {QUESTION_8_DECLINE_REASON!r}")
    method = plan.required.get(key, "command")
    if args.action == "review" and method != "review":
        raise EvidenceError(f"{key} requires an executable command")
    if args.action == "run" and method not in ("command", "snapshot", "computed"):
        raise EvidenceError(f"{key} requires a review")
    if args.action == "block" and method in ("snapshot", "computed"):
        raise EvidenceError(f"{key} is generated by the validator and cannot be blocked")
    if args.non_timer_evidence_json is not None and (
        args.action != "review" or key[2] != "active_timer_updates"
    ):
        raise EvidenceError("Non-timer evidence is only valid for active timer update reviews")
    if args.type == "timer" and args.action == "run":
        if (
            args.environment not in SAFE_ENVIRONMENTS
            or not args.isolation_plan
            or not args.target_disposable
            or not isinstance(args.target_version, str)
            or TARGET_VERSION.fullmatch(args.target_version) is None
        ):
            raise EvidenceError("Timer preflight needs a disposable local or non-production target, version, and cleanup plan")
        if key[2] == "active_instance_reschedule":
            decision = plan.active_timer_decisions[key[1]]
            if args.environment != "local" or args.target_version != decision["target_version"]:
                raise EvidenceError(
                    f"{key}: active timer rescheduling needs the approved version on an isolated disposable local target"
                )
    elif args.action == "run" and needs_safe_environment(key):
        if args.environment not in SAFE_ENVIRONMENTS:
            raise EvidenceError("Runtime and deployment checks require a local or non-production target")
    previous_issues = []
    previous = load_checks(root, evidence, plan, previous_issues)
    if previous_issues:
        raise EvidenceError("; ".join(previous_issues))
    if args.action == "run":
        require_prerequisites(root, plan, previous, key)
    command = None
    exit_code = None
    output = ""
    reason = None
    result = "passed"
    extra = {}
    check_method = "command" if args.action == "run" else "review"
    mapping = None
    if plan.test_contract["mode"] == "run" and key[2] in TEST_LEDGER_CHECK_KINDS:
        mapping = read_test_mapping(root, required=True)
    if args.action == "run" and method == "snapshot":
        command = list(args.command or [])
        if command and command[0] == "--":
            command = command[1:]
        if command:
            raise EvidenceError(f"{key}: test_freeze does not run a command")
        command = None
        snapshot = record_test_freeze(root, plan, mapping)
        check_method = snapshot["method"]
        result = snapshot["result"]
        reason = snapshot["reason"]
        output = snapshot["output"]
        extra["test_mapping_digest"] = snapshot["test_mapping_digest"]
    elif args.action == "run" and method == "computed":
        command = list(args.command or [])
        if command and command[0] == "--":
            command = command[1:]
        if command:
            raise EvidenceError(f"{key}: computed checks do not run a command")
        command = None
        if key[2] == "test_parity":
            computed_issues = test_parity_issues(plan, previous, mapping)
            details = {"issues": computed_issues}
        else:
            computed_issues, details = coverage_parity_issues(plan, previous, mapping)
            details["issues"] = computed_issues
        check_method = "computed"
        result = "failed" if computed_issues else "passed"
        reason = "; ".join(computed_issues) if computed_issues else None
        output = json.dumps(details, indent=2, ensure_ascii=False)
        extra["test_mapping_digest"] = test_mapping_digest(mapping, "all")
    elif args.action == "run" and key[2] == "test_repeat":
        if args.timeout is not None and args.timeout <= 0:
            raise EvidenceError("Command timeout must be positive")
        repeated = record_test_repeat(root, plan, args, mapping)
        command = repeated["command"]
        exit_code = repeated["exit_code"]
        result = repeated["result"]
        reason = repeated["reason"]
        output = repeated["output"]
        extra["test_runs"] = repeated["test_runs"]
        extra["test_mapping_digest"] = repeated["test_mapping_digest"]
    elif args.action == "run":
        if args.timeout is not None and args.timeout <= 0:
            raise EvidenceError("Command timeout must be positive")
        command = list(args.command or [])
        if command and command[0] == "--":
            command = command[1:]
        if not command:
            raise EvidenceError("Supply an executable command after --")
        if not command[0]:
            raise EvidenceError("Executable path cannot be empty")
        if key == ("project", ".", "docker_info", None) and command != ["docker", "info"]:
            raise EvidenceError("The Docker probe must execute docker info directly")
        if (test_run_mode == "migrate_only" and key[0] == "module" and key[2] == "compile"
                and not compiles_test_sources(command)):
            raise EvidenceError(
                f"{key}: Question 8 Migrate tests only requires test-source compilation, "
                "such as mvn test-compile or the Gradle testClasses task"
            )
        try:
            completed = subprocess.run(
                command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, errors="replace", timeout=args.timeout, check=False,
            )
            exit_code = completed.returncode
            output = completed.stdout
            if exit_code != 0:
                result = "failed"
                reason = f"Command exited with code {exit_code}"
        except subprocess.TimeoutExpired as exc:
            result = "blocked"
            reason = str(exc)
            output = (exc.stdout or b"").decode("utf-8", errors="replace") if isinstance(exc.stdout, bytes) else exc.stdout or ""
            output += (exc.stderr or b"").decode("utf-8", errors="replace") if isinstance(exc.stderr, bytes) else exc.stderr or ""
            output += f"\n{reason}"
        except OSError as exc:
            result = "blocked"
            reason = str(exc)
            output = reason
    elif args.action == "review":
        output = args.note
        if not output.strip():
            raise EvidenceError("A review requires a non-empty evidence note")
        if key[2] == "assertion_strength":
            if key[1] not in output or not re.search(r"\bassert(?:ion)?s?\b", output, re.I):
                raise EvidenceError(
                    f"{key}: review note must name the test class and its assertions"
                )
            extra["test_mapping_digest"] = test_mapping_digest(mapping, "review")
        elif key[2] == "mock_boundary":
            rows = test_rows_by_id(mapping)
            test = rows.get(key[1])
            if test is None:
                raise EvidenceError(f"{key}: test parity ledger entry is missing")
            if key[1] not in output:
                raise EvidenceError(f"{key}: review note must name the C7 test ID")
            mapped_c8_ids = mapped_cpt_test_ids(plan.test_contract, rows)
            mock_issues = test_mock_issues(
                test,
                mapping,
                mapped_c8_ids,
                plan.source_job_types_by_model,
            )
            if mock_issues:
                raise EvidenceError("; ".join(mock_issues))
            extra["test_mapping_digest"] = test_mapping_digest(mapping, "review")
    else:
        if not args.reason.strip():
            raise EvidenceError("A blocked check requires a reason")
        result = "blocked"
        check_method = "blocked"
        reason = args.reason
        output = reason
    check = {
        "run_id": read_json(root / INVENTORY)["run_id"],
        "type": args.type,
        "target": args.target,
        "kind": args.kind,
        "scenario": args.scenario,
        "method": check_method,
        "command": command,
        "exit_code": exit_code,
        "result": result,
        "reason": reason,
        "environment": args.environment,
        "isolation_plan": args.isolation_plan,
        "target_disposable": args.target_disposable,
        "target_version": args.target_version,
        "source_digest": plan.source_digest,
        "reference": args.reference,
        "disposition": args.disposition,
        "non_timer_evidence": (
            parse_evidence_list(args.non_timer_evidence_json)
            if args.action == "review" and args.non_timer_evidence_json is not None
            else None
        ),
        "output": output,
        **extra,
    }
    try:
        validate_risk_check(plan, key, check)
    except EvidenceError as exc:
        if args.action != "run" or key[0] != "timer":
            raise
        check.update(result="failed", reason=str(exc))
    result = check["result"]
    if result != "passed":
        if args.kind == "docker_info" or (
            args.action == "block" and plan.docker_suites.get(key)
            and previous.get(("project", ".", "docker_info", None), (None, {}))[1].get("result") == "failed"
        ):
            check["failure_class"] = "docker_unavailable"
        elif args.kind in ("tests", "test_repeat") and "Could not find a valid Docker environment" in output:
            check["failure_class"] = "testcontainers"
        else:
            check["failure_class"] = "unclassified"
    else:
        check["failure_class"] = None
    reference = write_check_log(root, key, check)
    evidence["checks"] = [path for path in evidence["checks"] if path != reference] + [reference]
    write_json(root, EVIDENCE, evidence)
    print(f"{result.upper()} {args.type} {args.target} {args.kind} {args.scenario or ''}")
    if output and args.action == "run":
        print(output, end="" if output.endswith("\n") else "\n")
    return 0 if result == "passed" else 1


def classify(root, args):
    evidence = read_json(root / EVIDENCE)
    plan = requirements(root, evidence)
    issues = list(plan.issues)
    checks = load_checks(root, evidence, plan, issues)
    if issues:
        raise EvidenceError("; ".join(issues))
    key = (args.type, args.target, args.kind, args.scenario)
    if key not in plan.allowed or key not in checks or checks[key][1]["result"] == "passed":
        raise EvidenceError(f"Only a recorded non-passing check can be classified: {key}")
    if not args.reason.strip():
        raise EvidenceError("Classification requires a reason")
    if args.failure_class == "docker_unavailable":
        probe = checks.get(("project", ".", "docker_info", None))
        if not plan.docker_suites.get(key) or probe is None or probe[1]["result"] != "failed":
            raise EvidenceError("Docker unavailable requires a failed Docker probe and a Docker-dependent suite")
    _, check, reference = checks[key]
    check["failure_class"] = args.failure_class
    check["reason"] = args.reason
    write_json(root, Path(reference), check)
    print(f"Classified {key} as {args.failure_class}; it still blocks readiness")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", type=Path, default=Path("."))
    actions = parser.add_subparsers(dest="action", required=True)
    init = actions.add_parser("init", help="Bind the Step 2 scope to a new migration validation run")
    init.add_argument(
        "--reset-source-snapshot", action="store_true",
        help="Replace the due-date snapshot only after restoring the C7 baseline",
    )
    actions.add_parser("report", help="Audit scope and write the validation gate")
    for name in ("run", "review", "block"):
        action = actions.add_parser(name)
        action.add_argument(
            "--type",
            required=True,
            choices=("project", "module", "model", "process", "timer", "deployment_set", "test"),
        )
        action.add_argument("--target", required=True)
        action.add_argument("--kind", required=True)
        action.add_argument("--scenario")
        action.add_argument("--environment", choices=("local", "non-production"))
        action.add_argument("--isolation-plan")
        action.add_argument("--target-disposable", action="store_true")
        action.add_argument("--target-version")
        action.add_argument("--reference")
        action.add_argument("--disposition")
        action.add_argument("--non-timer-evidence-json")
        if name == "run":
            action.add_argument("--timeout", type=int, default=300)
            action.add_argument("command", nargs=argparse.REMAINDER)
        elif name == "review":
            action.add_argument("--note", required=True)
        else:
            action.add_argument("--reason", required=True)
    classification = actions.add_parser("classify", help="Annotate a failed check after reviewing its output")
    classification.add_argument("--type", required=True, choices=("module", "model", "process", "timer"))
    classification.add_argument("--target", required=True)
    classification.add_argument("--kind", required=True)
    classification.add_argument("--scenario")
    classification.add_argument(
        "--failure-class", required=True,
        choices=("application", "testcontainers", "docker_unavailable", "infrastructure", "compatibility", "unknown"),
    )
    classification.add_argument("--reason", required=True)
    args = parser.parse_args()
    try:
        root = args.project_root.resolve(strict=True)
        if not root.is_dir():
            raise EvidenceError(f"Not a project directory: {root}")
        if args.action == "init":
            return initialize(root, args.reset_source_snapshot)
        if args.action == "report":
            return report(root)
        if args.action == "classify":
            return classify(root, args)
        return record(root, args)
    except (EvidenceError, OSError) as exc:
        print(f"Validation error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
