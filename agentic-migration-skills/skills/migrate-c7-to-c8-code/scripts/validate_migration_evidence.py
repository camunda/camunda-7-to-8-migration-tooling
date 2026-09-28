#!/usr/bin/env python3
"""Capture migration checks and generate a fail-closed validation gate."""

import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path, PureWindowsPath


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


def scope(root, evidence):
    inventory = read_json(root / INVENTORY)
    if inventory.get("schema_version") != 1 or evidence.get("schema_version") != 1:
        raise EvidenceError("Unsupported inventory or evidence version")
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
    return document


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
    issues = []

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
        if not project_path(root, path, "module").is_dir():
            issues.append(f"Module directory is missing: {path}")
        need("module", path, "compile")
        need("module", path, "review", method="review")
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
            document = read_model(root, source, converted)
        except EvidenceError as exc:
            issues.append(str(exc))
            continue
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
            for start in process.iter(f"{BPMN}startEvent"):
                if start.find(f"{BPMN}timerEventDefinition/{BPMN}timeCycle") is None:
                    continue
                if not start.get("id"):
                    issues.append(f"{target}: repeating timer start lacks an ID")
                    continue
                timers[converted].append(need("timer", f"{target}#{start.get('id')}", "preflight"))
    if len(converted_paths) != len(set(converted_paths)):
        issues.append("Converted copies must be distinct")
    return required, allowed, timers, docker_suites, issues


def check_key(check):
    return (check.get("type"), check.get("target"), check.get("kind"), check.get("scenario"))


def needs_safe_environment(key):
    return (
        key[0] == "process"
        or key[0] == "model" and key[2] == "deployment"
        or key[0] == "module" and key[2] in RUNTIME_CHECKS
    )


def load_checks(root, evidence, allowed, issues):
    checks = {}
    references = evidence.get("checks", [])
    if not isinstance(references, list):
        issues.append("Evidence checks must be an array")
        return checks
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
            if key not in allowed or key in checks:
                raise EvidenceError(f"Unexpected or duplicate check: {key}")
            method = check.get("method")
            result = check.get("result")
            command = check.get("command")
            exit_code = check.get("exit_code")
            reason = check.get("reason")
            if method == "command":
                if not isinstance(command, list) or not command or not all(
                    isinstance(part, str) and part for part in command
                ):
                    raise EvidenceError(f"{key}: missing command")
                if result == "passed" and (type(exit_code) is not int or exit_code != 0):
                    raise EvidenceError(f"{key}: passed without exit code 0")
                if result == "failed" and (type(exit_code) is not int or exit_code == 0):
                    raise EvidenceError(f"{key}: failed without a nonzero exit code")
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
            if key[0] == "timer" and result == "passed" and (
                check.get("environment") != "local" or not check.get("isolation_plan")
            ):
                raise EvidenceError(f"{key}: timer preflight needs local isolation or cleanup")
            if needs_safe_environment(key) and result == "passed":
                if check.get("environment") not in ("local", "non-production"):
                    raise EvidenceError(f"{key}: production or unknown runtime target")
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
    try:
        evidence = read_json(root / EVIDENCE)
        required, allowed, timers, docker_suites, scope_issues = requirements(root, evidence)
        issues.extend(scope_issues)
        checks = load_checks(root, evidence, allowed, issues)
        for key in sorted(required.keys() - checks.keys(), key=lambda item: tuple(str(value) for value in item)):
            issues.append(f"Missing {key[0]} {key[2]}: {key[1]} {key[3] or ''}".strip())
        for key, (index, check, _) in checks.items():
            method = required.get(key, "command")
            if check["method"] not in (method, "blocked"):
                issues.append(f"{key}: expected {method} evidence")
            if check["result"] != "passed":
                issues.append(f"{key}: {check['result']}: {check['reason']}")
            if key in docker_suites and docker_suites[key]:
                probe = checks.get(("project", ".", "docker_info", None))
                if probe is None or probe[0] >= index:
                    issues.append(f"{key}: Docker probe must precede the suite")
                if check.get("failure_class") == "docker_unavailable" and (
                    probe is None or probe[1]["result"] != "failed"
                ):
                    issues.append(f"{key}: Docker was not shown to be unavailable")
            if key[0] == "model" and key[2] == "deployment":
                lint = checks.get(("model", key[1], "lint", None))
                if lint is None or lint[0] >= index:
                    issues.append(f"{key}: lint must precede deployment")
            if key[0] == "process" and key[2] == "process_path":
                inventory = ("process", key[1], "worker_input_inventory", None)
                if inventory in required:
                    review = checks.get(inventory)
                    if review is None or review[0] >= index or review[1]["result"] != "passed":
                        issues.append(f"{key}: worker input review must precede process execution")
            if key[0] in ("model", "process") and (
                key[0] == "process" or key[2] == "deployment"
            ):
                model = key[1].split("#", 1)[0]
                for timer in timers.get(model, []):
                    preflight = checks.get(timer)
                    if preflight is None or preflight[0] >= index or preflight[1]["result"] != "passed":
                        issues.append(f"{key}: timer preflight must pass before execution")
        if any(
            key[0] == "module" and key[2] == "tests" and check[1].get("failure_class") == "docker_unavailable"
            and not docker_suites.get(key, False)
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
    required, allowed, timers, docker_suites, issues = requirements(root, evidence)
    if issues:
        raise EvidenceError("; ".join(issues))
    key = (args.type, args.target, args.kind, args.scenario)
    if key not in allowed:
        raise EvidenceError(f"Check is not in the migration scope: {key}")
    method = required.get(key, "command")
    if args.action == "review" and method != "review":
        raise EvidenceError(f"{key} requires an executable command")
    if args.action == "run" and method != "command":
        raise EvidenceError(f"{key} requires a review")
    if args.type == "timer" and args.action == "run":
        if args.environment != "local" or not args.isolation_plan:
            raise EvidenceError("Timer preflight needs a local target and an isolation or cleanup plan")
    elif args.action == "run" and needs_safe_environment(key):
        if args.environment not in ("local", "non-production"):
            raise EvidenceError("Runtime and deployment checks require a local or non-production target")
    previous_issues = []
    previous = load_checks(root, evidence, allowed, previous_issues)
    if previous_issues:
        raise EvidenceError("; ".join(previous_issues))
    if args.action == "run" and args.type == "model" and args.kind == "deployment":
        lint = previous.get(("model", args.target, "lint", None))
        if lint is None or lint[1]["result"] != "passed":
            raise EvidenceError("Lint must pass before deployment")
    if args.action == "run" and (args.type == "process" or args.type == "model" and args.kind == "deployment"):
        model = args.target.split("#", 1)[0]
        if any(timer not in previous or previous[timer][1]["result"] != "passed" for timer in timers.get(model, [])):
            raise EvidenceError("Timer preflight must pass before deployment or process execution")
    if args.action == "run" and args.type == "process" and args.kind == "process_path":
        inventory = ("process", args.target, "worker_input_inventory", None)
        if inventory in required and (
            inventory not in previous or previous[inventory][1]["result"] != "passed"
        ):
            raise EvidenceError("Review worker inputs before testing a direct process start")
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
    failure_class = None
    if result != "passed":
        if args.kind == "docker_info":
            failure_class = "docker_unavailable"
        elif (
            args.action == "block"
            and docker_suites.get(key)
            and previous.get(("project", ".", "docker_info", None), (None, {}))[1].get("result")
            == "failed"
        ):
            failure_class = "docker_unavailable"
        elif args.kind == "tests" and "Could not find a valid Docker environment" in output:
            failure_class = "testcontainers"
        else:
            failure_class = "unclassified"
    check = {
        "type": args.type,
        "target": args.target,
        "kind": args.kind,
        "scenario": args.scenario,
        "method": "command" if args.action == "run" else "blocked" if args.action == "block" else "review",
        "command": command,
        "exit_code": exit_code,
        "result": result,
        "reason": reason,
        "failure_class": failure_class,
        "environment": args.environment,
        "isolation_plan": args.isolation_plan,
        "recorded_at": datetime.now(timezone.utc).isoformat(),
        "output": output,
    }
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
    required, allowed, _, docker_suites, issues = requirements(root, evidence)
    checks = load_checks(root, evidence, allowed, issues)
    if issues:
        raise EvidenceError("; ".join(issues))
    key = (args.type, args.target, args.kind, args.scenario)
    if key not in allowed or key not in checks or checks[key][1]["result"] == "passed":
        raise EvidenceError(f"Only a recorded non-passing check can be classified: {key}")
    if not args.reason.strip():
        raise EvidenceError("Classification requires a reason")
    if args.failure_class == "docker_unavailable":
        probe = checks.get(("project", ".", "docker_info", None))
        if not docker_suites.get(key) or probe is None or probe[1]["result"] != "failed":
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
    actions.add_parser("report", help="Audit scope and write the validation gate")
    for name in ("run", "review", "block"):
        action = actions.add_parser(name)
        action.add_argument("--type", required=True, choices=("project", "module", "model", "process", "timer"))
        action.add_argument("--target", required=True)
        action.add_argument("--kind", required=True)
        action.add_argument("--scenario")
        action.add_argument("--environment", choices=("local", "non-production"))
        action.add_argument("--isolation-plan")
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
