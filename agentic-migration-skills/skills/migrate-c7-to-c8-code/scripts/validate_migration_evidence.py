#!/usr/bin/env python3
"""Capture migration checks and generate a fail-closed validation gate."""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path, PureWindowsPath
from uuid import uuid4


VALIDATION = Path(".camunda-migration/validation")
INVENTORY = VALIDATION / "step2-inventory.json"
EVIDENCE = VALIDATION / "validation-evidence.json"
LOGS = VALIDATION / "logs"
SUMMARY = VALIDATION / "validation-summary.json"
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
RUNTIME_CHECKS = ("spring_boot_run", "executable_jar", "external_launcher")
SAFE_ENVIRONMENTS = ("local", "non-production")
SKIP_SOURCE_DIRS = {".camunda-migration", ".claude", ".git", ".gradle",
                    ".venv", ".worktree", ".worktrees", "__pycache__",
                    "build", "dist", "node_modules", "target"}
CODE_SUFFIXES = {".cjs", ".cts", ".groovy", ".http", ".java", ".js", ".jsx",
                 ".kt", ".kts", ".mjs", ".mts", ".scala", ".ts", ".tsx"}
DUE_DATE_HINT = re.compile(r"""\bsetJobDuedate\b|/duedate\b|['"`]duedate['"`]""", re.I)
TARGET_VERSION = re.compile(r"8\.\d+\.\d+\Z")


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
    source_digest: str
    issues: list


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


def write_file(root, name, content):
    path = root / name
    if path.is_symlink():
        raise EvidenceError(f"Refusing to replace symlink: {path}")
    if not path.parent.resolve().is_relative_to(root):
        raise EvidenceError(f"Output directory is outside the project: {path.parent}")
    path.parent.mkdir(parents=True, exist_ok=True)
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


def initialize(root):
    inventory = read_json(root / INVENTORY)
    if inventory.get("schema_version") != 1:
        raise EvidenceError("Unsupported Step 2 inventory version")
    modules = strings(inventory.get("modules"), "Step 2 modules")
    models = strings(inventory.get("models"), "Step 2 models")
    if not (modules or models):
        raise EvidenceError("A migration run needs at least one module or model")
    for path in modules + models:
        project_path(root, path, "Step 2 scope")
    inventory["run_id"] = uuid4().hex
    write_json(root, INVENTORY, inventory)
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


def scan_module(root, module, module_paths, hashes):
    path = project_path(root, module, "module", must_exist=True)
    if not path.is_dir():
        raise EvidenceError(f"Module directory is missing: {module}")
    nested = {
        project_path(root, other, "module")
        for other in module_paths
        if other != module and project_path(root, other, "module").is_relative_to(path)
    }
    hits = []

    def walk_error(error):
        raise EvidenceError(f"Cannot scan module {module}: {error}") from error

    for current, directories, files in os.walk(path, followlinks=False, onerror=walk_error):
        directory = Path(current)
        for name in directories:
            candidate = directory / name
            if name not in SKIP_SOURCE_DIRS and candidate not in nested and candidate.is_symlink():
                raise EvidenceError(f"Cannot scan symlinked source directory: {candidate}")
        directories[:] = sorted(
            name for name in directories
            if name not in SKIP_SOURCE_DIRS and directory / name not in nested
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
            for match in DUE_DATE_HINT.finditer(text):
                line = text.count("\n", 0, match.start()) + 1
                column = match.start() - text.rfind("\n", 0, match.start())
                hits.append(f"{relative}:{line}:{column}")
    return hits


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


def requirements(root, evidence):
    modules, models = scope(root, evidence)
    required = {}
    allowed = set()
    timers = {}
    timer_starts = {}
    issues = []
    if "active_timer_update_decision" in evidence:
        issues.append("Active timer update approval is not supported; keep affected flows blocked")
    hashes = {}
    update_hits = {}
    source_ids = {}
    converted_ids = {}
    module_paths = {module["path"] for module in modules}

    def need(category, target, kind, scenario=None, method="command"):
        key = (category, target, kind, scenario)
        if key in required:
            issues.append(f"Duplicate required check: {key}")
        required[key] = method
        allowed.add(key)
        return key

    docker_suites = {}
    for module in modules:
        path = module["path"]
        update_hits[path] = scan_module(root, path, module_paths, hashes)
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
            need("module", path, "tests", name)
            docker_suites[("module", path, "tests", name)] = suite["requires_docker"]
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
    if any(docker_suites.values()):
        need("project", ".", "docker_info")

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
        deployment_sets[name] = entry
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
    for name in ("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
                 "settings.gradle.kts", "gradle.properties"):
        file = root / name
        if file.is_symlink():
            raise EvidenceError(f"Cannot scan symlinked build configuration: {file}")
        if file.is_file():
            hashes[name] = file_digest(file)
    snapshot = {"modules": modules, "models": models, "deployment_sets": declared_sets,
                "files": hashes}
    source_digest = hashlib.sha256(
        json.dumps(snapshot, sort_keys=True).encode("utf-8")
    ).hexdigest()
    return ValidationPlan(
        required, allowed, timers, timer_starts, docker_suites, model_sets,
        duplicates, update_hits, source_digest, issues,
    )


def check_key(check):
    return (check.get("type"), check.get("target"), check.get("kind"), check.get("scenario"))


def needs_safe_environment(key):
    return (
        key[0] == "process"
        or key[0] == "model" and key[2] == "deployment"
        or key[0] == "module" and key[2] in RUNTIME_CHECKS
    )


def parse_evidence_list(value):
    try:
        parsed = json.loads(value) if isinstance(value, str) else None
    except json.JSONDecodeError as exc:
        raise EvidenceError("Non-timer evidence must be valid JSON") from exc
    if not isinstance(parsed, list):
        raise EvidenceError("Non-timer evidence must be a JSON array")
    return parsed


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
        hits = plan.update_hits[target]
        disposition = check.get("disposition")
        evidence = check.get("non_timer_evidence")
        if disposition == "no_updates" and not hits and not evidence:
            return
        if disposition != "non_timer" or not hits or not isinstance(evidence, list):
            raise EvidenceError(f"{key}: block active or unclassified due-date updates")
        locations = [item.get("location") for item in evidence if isinstance(item, dict)]
        if len(locations) != len(evidence) or any(
            not isinstance(location, str) for location in locations
        ) or sorted(locations) != sorted(hits) or any(
            not concrete_reference(item.get("evidence")) for item in evidence
        ):
            raise EvidenceError(f"{key}: classify each detected due-date location with concrete evidence")
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
    category, target, kind, _ = key
    dependencies = []
    if category == "timer" and kind == "preflight":
        dependencies.append(("timer", target, "disposition", None))
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
    return dependencies


def require_prerequisites(plan, checks, key, before=None):
    for dependency in prerequisites(plan, key):
        previous = checks.get(dependency)
        if (
            previous is None or previous[1]["result"] != "passed"
            or previous[1]["method"] != plan.required.get(dependency, "command")
            or previous[1].get("source_digest") != plan.source_digest
            or before is not None and previous[0] >= before
        ):
            raise EvidenceError(f"{key}: {dependency[2]} must pass before execution")


def load_checks(root, evidence, plan, issues):
    checks = {}
    references = evidence.get("checks", [])
    if not isinstance(references, list):
        issues.append("Evidence checks must be an array")
        return checks
    run_id = read_json(root / INVENTORY)["run_id"]
    for index, reference in enumerate(references):
        try:
            path = project_path(root, reference, "check evidence", must_exist=True)
            if not path.is_file() or path.is_symlink() or not path.is_relative_to(root / LOGS):
                raise EvidenceError(f"Check evidence must be a file in {LOGS}: {reference}")
            check = read_json(path)
            key = check_key(check)
            if any(not isinstance(value, str) or not value for value in key[:3]) or (
                key[3] is not None and (not isinstance(key[3], str) or not key[3])
            ):
                raise EvidenceError(f"Malformed check in {reference}")
            if key not in plan.allowed or key in checks:
                raise EvidenceError(f"Unexpected or duplicate check: {key}")
            if check.get("run_id") != run_id:
                raise EvidenceError(f"{key}: check belongs to another migration run")
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
                if result == "passed" and (type(exit_code) is not int or exit_code != 0):
                    raise EvidenceError(f"{key}: passed without exit code 0")
                if result == "failed" and (
                    type(exit_code) is not int or exit_code == 0 and key[0] != "timer"
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
            else:
                raise EvidenceError(f"{key}: unsupported evidence method")
            if result not in ("passed", "failed", "blocked", "unknown", "not_run"):
                raise EvidenceError(f"{key}: unsupported result")
            if not isinstance(check.get("output"), str):
                raise EvidenceError(f"{key}: evidence output must be text")
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


def report(root):
    issues = []
    checks = {}
    plan = None
    try:
        evidence = read_json(root / EVIDENCE)
        plan = requirements(root, evidence)
        issues.extend(plan.issues)
        checks = load_checks(root, evidence, plan, issues)
        for key in sorted(plan.required.keys() - checks.keys(), key=lambda item: tuple(str(value) for value in item)):
            issues.append(f"Missing {key[0]} {key[2]}: {key[1]} {key[3] or ''}".strip())
        for key, (index, check, _) in checks.items():
            method = plan.required.get(key, "command")
            if check["method"] not in (method, "blocked"):
                issues.append(f"{key}: expected {method} evidence")
            if check["result"] != "passed":
                issues.append(f"{key}: {check['result']}: {check['reason']}")
            if check.get("source_digest") != plan.source_digest:
                issues.append(f"{key}: stale source or deployment-set evidence")
            try:
                require_prerequisites(plan, checks, key, before=index)
            except EvidenceError as exc:
                issues.append(str(exc))
            if key in plan.docker_suites and plan.docker_suites[key]:
                probe = checks.get(("project", ".", "docker_info", None))
                if probe is None or probe[0] >= index:
                    issues.append(f"{key}: Docker probe must precede the suite")
                if check.get("failure_class") == "docker_unavailable" and (
                    probe is None or probe[1]["result"] != "failed"
                ):
                    issues.append(f"{key}: Docker was not shown to be unavailable")
        if any(
            key[0] == "module" and key[2] == "tests" and check[1].get("failure_class") == "docker_unavailable"
            and not plan.docker_suites.get(key, False)
            for key, check in checks.items()
        ):
            issues.append("A non-Docker test was classified as Docker unavailable")
    except EvidenceError as exc:
        issues.append(str(exc))
    path = root / REPORT
    if path.is_symlink():
        raise EvidenceError("Refusing to replace a symlinked MIGRATION_REPORT.md")
    existing = path.read_text(encoding="utf-8") if path.exists() else ""
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
    evidence = read_json(root / EVIDENCE)
    plan = requirements(root, evidence)
    if plan.issues:
        raise EvidenceError("; ".join(plan.issues))
    key = (args.type, args.target, args.kind, args.scenario)
    if key not in plan.allowed:
        raise EvidenceError(f"Check is not in the migration scope: {key}")
    method = plan.required.get(key, "command")
    if args.action == "review" and method != "review":
        raise EvidenceError(f"{key} requires an executable command")
    if args.action == "run" and method != "command":
        raise EvidenceError(f"{key} requires a review")
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
    elif args.action == "run" and needs_safe_environment(key):
        if args.environment not in SAFE_ENVIRONMENTS:
            raise EvidenceError("Runtime and deployment checks require a local or non-production target")
    previous_issues = []
    previous = load_checks(root, evidence, plan, previous_issues)
    if previous_issues:
        raise EvidenceError("; ".join(previous_issues))
    if args.action == "run":
        require_prerequisites(plan, previous, key)
    command = None
    exit_code = None
    output = ""
    reason = None
    result = "passed"
    if args.action == "run":
        if args.timeout is not None and args.timeout <= 0:
            raise EvidenceError("Command timeout must be positive")
        command = args.command
        if command and command[0] == "--":
            command = command[1:]
        if not command:
            raise EvidenceError("Supply an executable command after --")
        if not command[0]:
            raise EvidenceError("Executable path cannot be empty")
        if key == ("project", ".", "docker_info", None) and command != ["docker", "info"]:
            raise EvidenceError("The Docker probe must execute docker info directly")
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
    else:
        if not args.reason.strip():
            raise EvidenceError("A blocked check requires a reason")
        result = "blocked"
        reason = args.reason
        output = reason
    check = {
        "run_id": read_json(root / INVENTORY)["run_id"],
        "type": args.type,
        "target": args.target,
        "kind": args.kind,
        "scenario": args.scenario,
        "method": "command" if args.action == "run" else "blocked" if args.action == "block" else "review",
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
        elif args.kind == "tests" and "Could not find a valid Docker environment" in output:
            check["failure_class"] = "testcontainers"
        else:
            check["failure_class"] = "unclassified"
    else:
        check["failure_class"] = None
    digest = hashlib.sha256(json.dumps(key).encode("utf-8")).hexdigest()[:20]
    reference = (LOGS / f"{digest}.json").as_posix()
    write_json(root, Path(reference), check)
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
    actions.add_parser("init", help="Bind the Step 2 scope to a new migration validation run")
    actions.add_parser("report", help="Audit scope and write the validation gate")
    for name in ("run", "review", "block"):
        action = actions.add_parser(name)
        action.add_argument("--type", required=True, choices=("project", "module", "model", "process", "timer", "deployment_set"))
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
            return initialize(root)
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
