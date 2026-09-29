#!/usr/bin/env python3
"""Capture migration checks and generate a fail-closed validation gate."""

import argparse
import ast
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime
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
SOURCE_SUFFIXES = {
    ".conf", ".gradle", ".groovy", ".http", ".ini", ".java", ".js", ".json",
    ".jsx", ".kt", ".kts", ".properties", ".py", ".scala", ".sh", ".toml",
    ".ts", ".tsx", ".xml", ".yaml", ".yml",
}
SKIP_SOURCE_DIRS = {".camunda-migration", ".git", "build", "dist", "node_modules", "target"}
HASH_COMMENT_SUFFIXES = {
    ".conf", ".http", ".ini", ".properties", ".py", ".sh", ".toml", ".yaml", ".yml",
}
CALLER_SOURCE_SUFFIXES = {
    ".gradle", ".groovy", ".java", ".js", ".jsx", ".kt", ".kts", ".py", ".scala",
    ".sh", ".ts", ".tsx",
}
NESTED_BLOCK_COMMENT_SUFFIXES = {".kt", ".kts", ".scala"}
SLASH_COMMENT_SUFFIXES = {
    ".gradle", ".groovy", ".java", ".js", ".jsx", ".kt", ".kts", ".scala", ".ts", ".tsx",
}
DUE_DATE_METHOD = re.compile(r"\bsetJobDuedate\b")
REST_DUE_DATE_UPDATE = re.compile(
    r"""/job/(?:\{[^}]+\}|[^/\s"'?]+)/duedate(?:/recalculate)?""",
    re.IGNORECASE,
)
REST_DUE_DATE_CONCAT = re.compile(
    r"""["'`]/job/["'`]\s*\+\s*[^;{}]+?\s*\+\s*["'`]/duedate(?:/recalculate)?""",
    re.IGNORECASE | re.DOTALL,
)
REST_DUE_DATE_PATH_SEGMENTS = re.compile(
    r"""\b(?:pathSegment|pathSegments|addPathSegment|addPathSegments|path)\s*"""
    r"""\([^;{}]*?["'`]job["'`][^;{}]*?["'`]duedate(?:/recalculate)?["'`]""",
    re.IGNORECASE | re.DOTALL,
)
LATEST_VERSION = re.compile(r"\.\s*latestVersion\s*\(")
PROCESS_CALL = re.compile(r"\b(startProcessInstanceByKey|createProcessInstanceByKey|bpmnProcessId)\s*\(")
PROCESS_REFERENCE = re.compile(
    r"::\s*(startProcessInstanceByKey|createProcessInstanceByKey|bpmnProcessId)\b"
)
CAMUNDA_TARGET_VERSION = re.compile(r"8\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)")
JAVA_METHOD_RETURN_TYPE = re.compile(
    r"(?:void|boolean|byte|short|int|long|char|float|double|"
    r"(?:[A-Za-z_$][A-Za-z0-9_$]*\s*\.\s*)*[A-Za-z_$][A-Za-z0-9_$]*"
    r"(?:\s*<[^;{}()]*>)?(?:\s*\[\])*)"
)
JAVA_METHOD_DECLARATION_TAIL = re.compile(
    r"\s*(?:throws\s+[A-Za-z_$][A-Za-z0-9_$.,<>\s\[\]]*)?\s*(?:\{|;)"
)
INTERPOLATED_SOURCE_CALL = re.compile(
    r"\b(?:startProcessInstanceByKey|createProcessInstanceByKey|bpmnProcessId|"
    r"latestVersion|setJobDuedate)\b"
)
DURATION = re.compile(
    r"P(?=.*\d)"
    r"(?:\d+(?:[.,]\d+)?Y)?"
    r"(?:\d+(?:[.,]\d+)?M)?"
    r"(?:\d+(?:[.,]\d+)?W)?"
    r"(?:\d+(?:[.,]\d+)?D)?"
    r"(?:T(?=\d)(?:\d+(?:[.,]\d+)?H)?"
    r"(?:\d+(?:[.,]\d+)?M)?"
    r"(?:\d+(?:[.,]\d+)?S)?)?"
)
TIMER_DISPOSITIONS = {"add", "change", "preserve", "remove"}
PROCESS_ID_DISPOSITIONS = {"explicit_version", "mapped_rename"}
ACTIVE_TIMER_DISPOSITIONS = {"no_updates", "verified"}


class EvidenceError(ValueError):
    pass


@dataclass
class ValidationPlan:
    required: dict
    allowed: set
    timers: dict
    timer_inventory: dict
    timer_elements_by_model: dict
    docker_suites: dict
    deployment_sets: dict
    models_by_path: dict
    process_ids_by_set: dict
    duplicate_process_ids: dict
    active_timer_updates: dict
    latest_version_calls: dict
    process_callers: dict
    active_timer_update_decision: dict
    source_snapshot_digest: str
    issues: list


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


def mask_source_text(text, suffix):
    code = list(text)
    comment_free = list(text)
    issues = []
    index = 0
    line_comment = False
    block_end = None
    block_depth = 0
    string_quote = None
    string_size = 0
    string_start = 0
    string_prefix = ""
    escaped = False

    def hide(start, end, buffer):
        for position in range(start, end):
            if text[position] not in "\r\n":
                buffer[position] = " "

    while index < len(text):
        char = text[index]
        if line_comment:
            if char == "\n":
                line_comment = False
            else:
                hide(index, index + 1, code)
                hide(index, index + 1, comment_free)
            index += 1
            continue
        if block_end:
            if (
                block_end == "*/"
                and suffix in NESTED_BLOCK_COMMENT_SUFFIXES
                and text.startswith("/*", index)
            ):
                hide(index, index + 2, code)
                hide(index, index + 2, comment_free)
                block_depth += 1
                index += 2
                continue
            if text.startswith(block_end, index):
                end = index + len(block_end)
                hide(index, end, code)
                hide(index, end, comment_free)
                index = end
                block_depth -= 1
                if block_depth == 0:
                    block_end = None
            else:
                hide(index, index + 1, code)
                hide(index, index + 1, comment_free)
                index += 1
            continue
        if string_quote:
            delimiter = string_quote * string_size
            if text.startswith(delimiter, index) and not escaped:
                contents = text[string_start + string_size:index]
                interpolated = (
                    string_quote == "`" and "${" in contents
                    or suffix == ".py"
                    and "f" in string_prefix
                    and "{" in contents and "}" in contents
                    or suffix in {".kt", ".kts"} and "$" in contents
                    or suffix in {".java", ".kt", ".kts"} and r"\{" in contents
                    or suffix in {".gradle", ".groovy"} and "$" in contents
                    or suffix == ".scala"
                    and bool(string_prefix)
                    and "$" in contents
                )
                if interpolated and INTERPOLATED_SOURCE_CALL.search(contents):
                    issues.append(
                        "Cannot statically scan an interpolated string containing a process or timer call"
                    )
                hide(index, index + string_size, code)
                index += string_size
                string_quote = None
                string_size = 0
                string_prefix = ""
                escaped = False
                continue
            if char == "\\" and not escaped:
                escaped = True
            else:
                escaped = False
            hide(index, index + 1, code)
            index += 1
            continue
        if text.startswith("<!--", index):
            block_end = "-->"
            block_depth = 1
            hide(index, index + 4, code)
            hide(index, index + 4, comment_free)
            index += 4
            continue
        if (
            suffix in SLASH_COMMENT_SUFFIXES
            and text.startswith("//", index)
        ):
            line_comment = True
            hide(index, index + 2, code)
            hide(index, index + 2, comment_free)
            index += 2
            continue
        if (
            suffix in SLASH_COMMENT_SUFFIXES
            and text.startswith("/*", index)
        ):
            block_end = "*/"
            block_depth = 1
            hide(index, index + 2, code)
            hide(index, index + 2, comment_free)
            index += 2
            continue
        if suffix in HASH_COMMENT_SUFFIXES and char == "#":
            if suffix == ".py" or index == 0 or text[index - 1].isspace():
                line_comment = True
                hide(index, index + 1, code)
                hide(index, index + 1, comment_free)
                index += 1
                continue
        if char in ("'", '"', "`"):
            string_quote = char
            string_size = (
                3 if char in ("'", '"') and text.startswith(char * 3, index) else 1
            )
            prefix_start = index - 1
            while prefix_start >= 0 and (
                text[prefix_start].isalnum() or text[prefix_start] == "_"
            ):
                prefix_start -= 1
            string_prefix = text[prefix_start + 1:index].lower()
            string_start = index
            hide(index, index + string_size, code)
            index += string_size
            escaped = False
            continue
        index += 1

    if block_end:
        issues.append("Unterminated block comment in source")
    if string_quote:
        issues.append("Unterminated string literal in source")
    return "".join(code), "".join(comment_free), issues


def parse_process_call(text, match):
    argument_start = match.end()
    first_argument_end = None
    depth = 1
    quote = None
    escaped = False
    index = argument_start
    while index < len(text):
        char = text[index]
        if quote is not None:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
        elif text.startswith("//", index):
            end = text.find("\n", index + 2)
            if end < 0:
                return None, len(text)
            index = end
        elif text.startswith("/*", index):
            end = text.find("*/", index + 2)
            if end < 0:
                return None, len(text)
            index = end + 1
        elif char == "#":
            end = text.find("\n", index + 1)
            if end < 0:
                return None, len(text)
            index = end
        elif char in ("'", '"', "`"):
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                end = first_argument_end if first_argument_end is not None else index
                return text[argument_start:end].strip(), index + 1
        elif char == "," and depth == 1 and first_argument_end is None:
            first_argument_end = index
        index += 1
    return None, len(text)


def static_string_value(expression):
    if not isinstance(expression, str):
        return None
    expression = expression.strip()
    if len(expression) >= 2 and expression[0] == "`" and expression[-1] == "`":
        value = expression[1:-1]
        if "${" in value or "\\" in value:
            return None
        return value
    try:
        value = ast.literal_eval(expression)
    except (SyntaxError, ValueError):
        return None
    return value if isinstance(value, str) else None


def reviewable_identifier_expression(expression):
    return isinstance(expression, str) and re.fullmatch(
        r"(?:[A-Za-z_$][A-Za-z0-9_$]*\s*\.\s*)*[A-Za-z_$][A-Za-z0-9_$]*",
        expression.strip(),
    ) is not None


def matching_parenthesis_end(text, opening):
    depth = 0
    for index in range(opening, len(text)):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                return index + 1
    return None


def process_call_version(text, call_end, operation):
    if operation in ("startProcessInstanceByKey", "createProcessInstanceByKey"):
        return "latest_version", "known", []
    selections = []
    latest_version_starts = []
    index = call_end
    while index < len(text):
        while index < len(text) and text[index].isspace():
            index += 1
        method = re.match(r"\.\s*([A-Za-z_$][A-Za-z0-9_$]*)\s*\(", text[index:])
        if method is None:
            break
        name = method.group(1)
        if name == "latestVersion":
            latest_version_starts.append(index)
        opening = index + method.end() - 1
        end = matching_parenthesis_end(text, opening)
        if end is None:
            return "unknown", "dynamic", latest_version_starts
        arguments = text[opening + 1:end - 1].strip()
        if name == "latestVersion":
            selections.append(
                ("latest_version", "known")
                if not arguments
                else ("unknown", "dynamic")
            )
        elif name == "version":
            if re.fullmatch(r"\d+", arguments) and arguments.strip("0"):
                selections.append(("explicit_version", "known"))
            else:
                resolution = (
                    "reviewable"
                    if reviewable_identifier_expression(arguments)
                    else "dynamic"
                )
                selections.append(("unknown", resolution))
        index = end
    if len(selections) == 1:
        return selections[0][0], selections[0][1], latest_version_starts
    return "unknown", "dynamic", latest_version_starts


def is_process_method_declaration(text, match, call_end, source_suffix):
    prefix_start = max(
        text.rfind(delimiter, 0, match.start())
        for delimiter in (";", "{", "}")
    )
    prefix = text[prefix_start + 1:match.start()]
    tail = text[call_end:]

    if source_suffix in (".java", ".groovy", ".gradle"):
        prefix = re.sub(
            r"@[A-Za-z_$][A-Za-z0-9_$.]*(?:\s*\([^()]*\))?",
            " ",
            prefix,
        ).strip()
        prefix = re.sub(
            r"^(?:(?:public|protected|private|abstract|static|final|native|"
            r"synchronized|default|strictfp|override|open|internal)\s+)*",
            "",
            prefix,
        )
        prefix = re.sub(r"^<[^;{}()]+>\s*", "", prefix)
        return (
            prefix not in {
                "return", "throw", "new", "if", "while", "for", "switch",
                "case", "else", "try", "catch", "finally", "assert", "yield",
            }
            and JAVA_METHOD_RETURN_TYPE.fullmatch(prefix) is not None
            and JAVA_METHOD_DECLARATION_TAIL.match(tail) is not None
        )
    if source_suffix in (".js", ".jsx", ".ts", ".tsx"):
        modifiers = {
            "abstract", "async", "declare", "default", "function", "override",
            "private", "protected", "public", "readonly", "static",
        }
        return (
            all(token in modifiers for token in prefix.split())
            and re.match(r"\s*(?::\s*[^;{}\n]+)?\s*\{", tail) is not None
        )
    if source_suffix in (".kt", ".kts"):
        return (
            re.search(r"\bfun(?:\s*<[^>]+>)?\s*$", prefix.strip()) is not None
            and re.match(r"\s*(?::\s*[^={}\n]+)?\s*(?:\{|=)", tail) is not None
        )
    if source_suffix == ".scala":
        return (
            re.search(r"\bdef(?:\s+\[[^\]]+\])?\s*$", prefix.strip()) is not None
            and re.match(r"\s*(?::\s*[^{}\n=]+)?\s*(?:=|\{)", tail) is not None
        )
    if source_suffix == ".py":
        return (
            re.search(r"\b(?:async\s+)?def\s*$", prefix.strip()) is not None
            and re.match(r"\s*(?:->\s*[^:\n]+)?\s*:", tail) is not None
        )
    if source_suffix == ".sh":
        return (
            prefix.strip() in ("", "function")
            and re.match(r"\s*\{", tail) is not None
        )
    return False


def scan_module_sources(root, module):
    updates = []
    latest_versions = []
    process_callers = []
    source_snapshot = {}
    issues = []
    try:
        module_root = project_path(root, module, "module", must_exist=True)
    except EvidenceError:
        return updates, latest_versions, process_callers, source_snapshot, issues
    if not module_root.is_dir():
        return updates, latest_versions, process_callers, source_snapshot, issues
    def walk_error(error):
        location = error.filename or module
        issues.append(f"Cannot scan {location} for migration evidence: {error}")

    for current, directories, files in os.walk(
        module_root,
        followlinks=False,
        onerror=walk_error,
    ):
        current_path = Path(current)
        included_directories = []
        for name in sorted(directories):
            directory = current_path / name
            if name in SKIP_SOURCE_DIRS:
                continue
            if directory.is_symlink():
                issues.append(
                    f"Cannot scan symlinked in-scope source directory: {directory.relative_to(root)}"
                )
                continue
            included_directories.append(name)
        directories[:] = included_directories
        for name in sorted(files):
            path = current_path / name
            if path.suffix.lower() not in SOURCE_SUFFIXES:
                continue
            if path.is_symlink():
                issues.append(
                    f"Cannot scan symlinked in-scope source file: {path.relative_to(root)}"
                )
                continue
            try:
                content = path.read_bytes()
                text = content.decode("utf-8")
            except (OSError, UnicodeError) as exc:
                issues.append(f"Cannot scan {path.relative_to(root)} for migration evidence: {exc}")
                continue
            relative = path.relative_to(root).as_posix()
            source_snapshot[relative] = hashlib.sha256(content).hexdigest()
            is_caller_source = path.suffix.lower() in CALLER_SOURCE_SUFFIXES
            code_text, comment_free_text, lexical_issues = mask_source_text(text, path.suffix.lower())
            issues.extend(
                f"{relative}: {issue}"
                for issue in lexical_issues
                if is_caller_source or issue != "Unterminated string literal in source"
            )
            if is_caller_source:
                for match in DUE_DATE_METHOD.finditer(code_text):
                    line_number = code_text.count("\n", 0, match.start()) + 1
                    updates.append({
                        "location": f"{relative}:{line_number}",
                        "kind": "setJobDuedate",
                    })
                chained_latest_version_calls = {}
                for match in PROCESS_CALL.finditer(code_text):
                    operation = match.group(1)
                    argument, call_end = parse_process_call(text, match)
                    if is_process_method_declaration(
                        code_text,
                        match,
                        call_end,
                        path.suffix.lower(),
                    ):
                        continue
                    process_id = static_string_value(argument)
                    if not isinstance(process_id, str) or not process_id:
                        process_id = "unknown"
                        process_id_resolution = (
                            "reviewable"
                            if reviewable_identifier_expression(argument)
                            else "dynamic"
                        )
                    else:
                        process_id_resolution = "known"
                    (
                        version_selection,
                        version_selection_resolution,
                        latest_version_starts,
                    ) = process_call_version(code_text, call_end, operation)
                    line_number = code_text.count("\n", 0, match.start()) + 1
                    process_call_location = f"{relative}:{line_number}"
                    for start in latest_version_starts:
                        chained_latest_version_calls[start] = process_call_location
                    process_callers.append({
                        "module": module,
                        "location": process_call_location,
                        "process_id": process_id,
                        "process_id_resolution": process_id_resolution,
                        "operation": operation,
                        "version_selection": version_selection,
                        "version_selection_resolution": version_selection_resolution,
                    })
                for match in LATEST_VERSION.finditer(code_text):
                    line_number = code_text.count("\n", 0, match.start()) + 1
                    hit = {
                        "module": module,
                        "location": f"{relative}:{line_number}",
                    }
                    process_call_location = chained_latest_version_calls.get(match.start())
                    if process_call_location is not None:
                        hit["process_call_location"] = process_call_location
                    latest_versions.append(hit)
                for match in PROCESS_REFERENCE.finditer(code_text):
                    line_number = code_text.count("\n", 0, match.start()) + 1
                    process_callers.append({
                        "module": module,
                        "location": f"{relative}:{line_number}",
                        "process_id": "unknown",
                        "process_id_resolution": "dynamic",
                        "operation": match.group(1),
                        "version_selection": "unknown",
                        "version_selection_resolution": "dynamic",
                    })
            rest_matches = {}
            for pattern in (
                REST_DUE_DATE_UPDATE,
                REST_DUE_DATE_CONCAT,
                REST_DUE_DATE_PATH_SEGMENTS,
            ):
                for match in pattern.finditer(comment_free_text):
                    rest_matches[(match.start(), match.end())] = match
            for match in sorted(rest_matches.values(), key=lambda item: item.start()):
                line_number = comment_free_text.count("\n", 0, match.start()) + 1
                updates.append({
                    "location": f"{relative}:{line_number}",
                    "kind": "REST due-date endpoint",
                })
    return updates, latest_versions, process_callers, source_snapshot, issues


def repeating_starts(document):
    starts = {}
    issues = []
    for process in document.findall(f"{BPMN}process"):
        process_id = process.get("id")
        for start in process.findall(f"{BPMN}startEvent"):
            cycle = start.find(f"{BPMN}timerEventDefinition/{BPMN}timeCycle")
            if cycle is None:
                continue
            start_id = start.get("id")
            if not process_id or not start_id:
                issues.append("Repeating timer start lacks a process or event ID")
                continue
            key = (process_id, start_id)
            if key in starts:
                issues.append(f"Repeating timer start is duplicated: {process_id}#{start_id}")
                continue
            starts[key] = "".join(cycle.itertext()).strip()
    return starts, issues


def timer_elements(document):
    elements = set()
    issues = []
    event_types = {
        f"{BPMN}startEvent",
        f"{BPMN}intermediateCatchEvent",
        f"{BPMN}boundaryEvent",
    }
    for process in document.findall(f"{BPMN}process"):
        process_id = process.get("id")
        for event in process.iter():
            if (
                event.tag not in event_types
                or event.find(f"{BPMN}timerEventDefinition") is None
            ):
                continue
            timer_id = event.get("id")
            if not process_id or not timer_id:
                issues.append("Timer event lacks a process or element ID")
                continue
            key = (process_id, timer_id)
            if key in elements:
                issues.append(f"Timer event is duplicated: {process_id}#{timer_id}")
                continue
            elements.add(key)
    return elements, issues


_CRON_MONTHS = {
    "JAN": 1,
    "FEB": 2,
    "MAR": 3,
    "APR": 4,
    "MAY": 5,
    "JUN": 6,
    "JUL": 7,
    "AUG": 8,
    "SEP": 9,
    "OCT": 10,
    "NOV": 11,
    "DEC": 12,
}
_CRON_INTEGER_MAX = 2_147_483_647
_CRON_WEEKDAYS = {
    "SUN": 7,
    "MON": 1,
    "TUE": 2,
    "WED": 3,
    "THU": 4,
    "FRI": 5,
    "SAT": 6,
}


def _cron_value(value, minimum, maximum, names=None):
    named_value = names.get(value.upper()) if names is not None else None
    if named_value is not None:
        return named_value
    if not re.fullmatch(r"[0-9]+", value):
        return None
    normalized = value.lstrip("0") or "0"
    upper_bound = str(maximum)
    if len(normalized) > len(upper_bound) or (
        len(normalized) == len(upper_bound) and normalized > upper_bound
    ):
        return None
    number = int(normalized)
    return number if number >= minimum else None


def _cron_day_of_month_special(value):
    if value in ("L", "LW"):
        return True
    offset = re.fullmatch(r"L-([0-9]+)", value)
    if offset is not None:
        return _cron_value(offset.group(1), 1, _CRON_INTEGER_MAX) is not None
    nearest_weekday = re.fullmatch(r"([0-9]+)W", value)
    return nearest_weekday is not None and (
        _cron_value(nearest_weekday.group(1), 1, 31) is not None
    )


def _cron_day_of_week_special(value):
    last_weekday = re.fullmatch(r"(.+)L", value)
    if last_weekday is not None:
        return _cron_value(last_weekday.group(1), 0, 7, _CRON_WEEKDAYS) is not None
    nth_weekday = re.fullmatch(r"(.+)#([0-9]+)", value)
    return nth_weekday is not None and (
        _cron_value(nth_weekday.group(1), 0, 7, _CRON_WEEKDAYS) is not None
        and _cron_value(nth_weekday.group(2), 1, _CRON_INTEGER_MAX) is not None
    )


def _valid_cron_field(
    field, minimum, maximum, names=None, question=False, special=None
):
    if question and field == "?":
        return True
    for item in field.upper().split(","):
        if not item:
            return False
        if special is not None and special(item):
            continue
        base, separator, step = item.partition("/")
        if separator and (
            "/" in step or _cron_value(step, 1, _CRON_INTEGER_MAX) is None
        ):
            return False
        if base == "*":
            continue
        if base == "?":
            return False
        endpoints = base.split("-")
        if len(endpoints) == 1:
            if _cron_value(endpoints[0], minimum, maximum, names) is None:
                return False
        elif len(endpoints) == 2:
            start = _cron_value(endpoints[0], minimum, maximum, names)
            end = _cron_value(endpoints[1], minimum, maximum, names)
            if minimum == 0 and maximum == 7 and start == 7:
                start = 0
            if start is None or end is None or start > end:
                return False
        else:
            return False
    return True


def cycle_details(expression):
    parts = expression.split("/")
    if len(parts) in (2, 3) and re.fullmatch(r"R\d*", parts[0]):
        if len(parts) == 3:
            try:
                datetime.fromisoformat(parts[1].replace("Z", "+00:00"))
            except ValueError:
                return None
        interval = parts[-1]
        if not DURATION.fullmatch(interval):
            return None
        count = parts[0][1:]
        if count and int(count) < 1:
            return None
        return {
            "cycle_type": "iso_8601",
            "interval": interval,
            "repetitions": int(count) if count else None,
        }
    cron_fields = [field for field in expression.split(" ") if field]
    if (
        len(cron_fields) == 6
        and _valid_cron_field(cron_fields[0], 0, 59)
        and _valid_cron_field(cron_fields[1], 0, 59)
        and _valid_cron_field(cron_fields[2], 0, 23)
        and _valid_cron_field(
            cron_fields[3], 1, 31, question=True, special=_cron_day_of_month_special
        )
        and _valid_cron_field(cron_fields[4], 1, 12, names=_CRON_MONTHS)
        and _valid_cron_field(
            cron_fields[5],
            0,
            7,
            names=_CRON_WEEKDAYS,
            question=True,
            special=_cron_day_of_week_special,
        )
    ):
        return {
            "cycle_type": "cron",
            "interval": None,
            "repetitions": None,
        }
    return None


def parse_json_option(value, label):
    if value is None:
        return None
    if not isinstance(value, str):
        raise EvidenceError(f"{label} must be supplied as JSON text")
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError as exc:
        raise EvidenceError(f"{label} must be valid JSON") from exc
    if not isinstance(parsed, list):
        raise EvidenceError(f"{label} must be a JSON array")
    return parsed


def parse_json_object_option(value, label):
    if value is None:
        return None
    if not isinstance(value, str):
        raise EvidenceError(f"{label} must be supplied as JSON text")
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError as exc:
        raise EvidenceError(f"{label} must be valid JSON") from exc
    if not isinstance(parsed, dict):
        raise EvidenceError(f"{label} must be a JSON object")
    return parsed


def source_snapshot_digest(snapshot):
    payload = json.dumps(snapshot, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def active_timer_decision(evidence):
    decision = evidence.get("active_timer_update_decision")
    if not isinstance(decision, dict):
        decision = {}
    return {
        "status": decision.get("status", "unresolved"),
        "approval_reference": decision.get("approval_reference"),
        "alternative_evidence_reference": decision.get("alternative_evidence_reference"),
        "target_version": decision.get("target_version"),
    }


def concrete_reference(value):
    if not isinstance(value, str) or not value.strip():
        return False
    normalized = re.sub(r"[^a-z0-9]+", "-", value.strip().casefold()).strip("-")
    return bool(normalized) and normalized not in {
        "none",
        "n-a",
        "not-approved",
        "not-reviewed",
        "not-run",
        "not-verified",
        "pending",
        "pending-review",
        "tbd",
        "todo",
        "unresolved",
        "unknown",
    } and not normalized.startswith("pending-")


def parse_timezone_aware_timestamp(value, label):
    if not isinstance(value, str) or not value.strip():
        raise EvidenceError(f"{label} must be a timezone-qualified ISO-8601 timestamp")
    try:
        parsed = datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    except ValueError as exc:
        raise EvidenceError(
            f"{label} must be a timezone-qualified ISO-8601 timestamp"
        ) from exc
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise EvidenceError(f"{label} must include a timezone")
    return parsed


def active_timer_decision_is_approved(decision, target_version=None):
    return (
        isinstance(decision, dict)
        and decision.get("status") == "approved"
        and concrete_reference(decision.get("approval_reference"))
        and concrete_reference(decision.get("alternative_evidence_reference"))
        and is_camunda_target_version(decision.get("target_version"))
        and (
            target_version is None
            or (
                is_camunda_target_version(target_version)
                and decision["target_version"] == target_version
            )
        )
    )


def is_camunda_target_version(value):
    return (
        isinstance(value, str)
        and CAMUNDA_TARGET_VERSION.fullmatch(value) is not None
    )


def validate_timer_observation(
    observation,
    inventory,
    environment,
    target_disposable,
    target_version,
    cleanup_plan,
):
    if not isinstance(observation, dict):
        raise EvidenceError("Timer preflight needs machine-readable timer observation evidence")
    deployment = observation.get("deployment")
    if not isinstance(deployment, dict) or deployment.get("performed") is not True:
        raise EvidenceError("Timer observation must prove that deployment was performed")
    if not concrete_reference(deployment.get("reference")):
        raise EvidenceError("Timer observation needs a deployment evidence reference")
    if (
        environment not in SAFE_ENVIRONMENTS
        or deployment.get("environment") != environment
        or target_disposable is not True
        or deployment.get("target_disposable") is not True
    ):
        raise EvidenceError("Timer observation must identify the disposable local or non-production target")
    if (
        not is_camunda_target_version(target_version)
        or deployment.get("target_version") != target_version
    ):
        raise EvidenceError(
            "Timer observation target version must be a concrete Camunda 8 version "
            "and match the selected target"
        )
    if not isinstance(cleanup_plan, str) or not cleanup_plan.strip():
        raise EvidenceError("Timer preflight needs a cleanup plan")

    observed = observation.get("observation")
    if not isinstance(observed, dict):
        raise EvidenceError("Timer observation must identify the timer-start behavior")
    if (
        observed.get("process_id") != inventory.get("process_id")
        or observed.get("start_id") != inventory.get("start_id")
        or observed.get("cycle") != inventory.get("converted_cycle")
        or type(observed.get("instances_started")) is not int
        or observed["instances_started"] < 1
    ):
        raise EvidenceError(
            "Timer observation must match the expected process, start event, cycle, and show a started instance"
        )

    cleanup = observation.get("cleanup")
    if not isinstance(cleanup, dict) or cleanup.get("completed") is not True:
        raise EvidenceError("Timer observation must prove cleanup completed")
    if not concrete_reference(cleanup.get("evidence_reference")):
        raise EvidenceError("Timer observation needs a cleanup evidence reference")


def timer_elements_for_module(plan, module):
    if module == ".":
        set_names = {
            model.get("deployment_set")
            for model in plan.models_by_path.values()
            if model.get("module") == "."
        }
    else:
        set_names = {
            name
            for name, entry in plan.deployment_sets.items()
            if module in entry["modules"]
        }
    model_paths = {
        model_path
        for name in set_names
        if name in plan.deployment_sets
        for model_path in plan.deployment_sets[name]["models"]
    }
    return {
        model_path: plan.timer_elements_by_model[model_path]
        for model_path in model_paths
        if model_path in plan.timer_elements_by_model
    }


def normalize_non_timer_update_evidence(evidence, hits):
    if not isinstance(evidence, list):
        raise EvidenceError("Non-timer update evidence must be a JSON array")
    hit_counts = {}
    for hit in hits:
        key = (hit["location"], hit["kind"])
        hit_counts[key] = hit_counts.get(key, 0) + 1
    remaining_counts = dict(hit_counts)
    normalized = []
    for item in evidence:
        if not isinstance(item, dict):
            raise EvidenceError("Non-timer update evidence contains an invalid record")
        location = item.get("location")
        kind = item.get("kind")
        reference = item.get("evidence")
        key = (location, kind)
        if (
            not isinstance(location, str)
            or not isinstance(kind, str)
            or key not in remaining_counts
            or remaining_counts[key] == 0
        ):
            raise EvidenceError(
                "Non-timer update evidence must match a detected due-date update"
            )
        if not concrete_reference(reference):
            raise EvidenceError(
                "Non-timer update evidence must include a concrete explanation or reference"
            )
        remaining_counts[key] -= 1
        normalized.append({
            "location": location,
            "kind": kind,
            "evidence": reference.strip(),
        })
    classified_counts = {
        key: count - remaining_counts[key]
        for key, count in hit_counts.items()
    }
    active_hits = []
    for hit in hits:
        key = (hit["location"], hit["kind"])
        if classified_counts[key] > 0:
            classified_counts[key] -= 1
        else:
            active_hits.append(hit)
    return sorted(
        normalized,
        key=lambda item: (item["location"], item["kind"], item["evidence"]),
    ), active_hits


def normalize_affected_timer_inventory(inventory, hits, timer_elements_by_model=None):
    if not isinstance(inventory, list):
        raise EvidenceError("Active timer review needs a machine-readable affected timer inventory")
    expected_locations = {hit["location"] for hit in hits}
    if hits and not inventory:
        raise EvidenceError("Affected timer inventory must identify every active BPMN timer")
    if not hits and inventory:
        raise EvidenceError("Affected timer inventory is unexpected when no updates were detected")

    normalized = []
    covered_locations = set()
    seen_timers = set()
    for timer in inventory:
        if not isinstance(timer, dict):
            raise EvidenceError("Affected timer inventory contains an invalid timer record")
        model_path = timer.get("model_path")
        process_id = timer.get("process_id")
        timer_id = timer.get("timer_id")
        if not all(concrete_reference(value) for value in (model_path, process_id, timer_id)):
            raise EvidenceError("Affected timer inventory must identify each model, process, and timer")
        if timer_elements_by_model is not None and (
            model_path not in timer_elements_by_model
            or (process_id, timer_id) not in timer_elements_by_model[model_path]
        ):
            raise EvidenceError("Affected timer inventory must identify an in-scope BPMN timer")
        timer_key = (model_path, process_id, timer_id)
        if timer_key in seen_timers:
            raise EvidenceError("Affected timer inventory contains a duplicate model process timer")
        seen_timers.add(timer_key)
        try:
            source_locations = strings(
                timer.get("source_locations"),
                "Affected timer source locations",
            )
        except EvidenceError as exc:
            raise EvidenceError(
                "Affected timer inventory must map every detected update source to a BPMN timer"
            ) from exc
        if not source_locations or set(source_locations) - expected_locations:
            raise EvidenceError(
                "Affected timer inventory must map every detected update source to a BPMN timer"
            )
        covered_locations.update(source_locations)
        normalized.append({
            "model_path": model_path,
            "process_id": process_id,
            "timer_id": timer_id,
            "source_locations": sorted(source_locations),
        })
    if covered_locations != expected_locations:
        raise EvidenceError(
            "Affected timer inventory must map every detected update source to a BPMN timer"
        )
    return sorted(
        normalized,
        key=lambda timer: (
            timer["model_path"],
            timer["process_id"],
            timer["timer_id"],
        ),
    )


def validate_active_timer_update_observation(
    observation,
    hits,
    affected_timer_inventory,
    decision,
    environment,
    target_disposable,
    target_version,
    cleanup_plan,
):
    if not isinstance(observation, dict):
        raise EvidenceError("Active timer runtime check needs machine-readable observation evidence")
    if not isinstance(hits, list) or not hits:
        raise EvidenceError("Active timer runtime check needs the detected update source inventory")
    affected_timers = normalize_affected_timer_inventory(affected_timer_inventory, hits)
    expected_timers = {
        (timer["model_path"], timer["process_id"], timer["timer_id"]): set(
            timer["source_locations"]
        )
        for timer in affected_timers
    }
    deployment = observation.get("deployment")
    if not isinstance(deployment, dict) or deployment.get("performed") is not True:
        raise EvidenceError("Active timer observation must prove that deployment was performed")
    if not concrete_reference(deployment.get("reference")):
        raise EvidenceError("Active timer observation needs a deployment evidence reference")
    if (
        environment not in SAFE_ENVIRONMENTS
        or deployment.get("environment") != environment
        or target_disposable is not True
        or deployment.get("target_disposable") is not True
    ):
        raise EvidenceError("Active timer observation must identify the disposable local or non-production target")
    if (
        not active_timer_decision_is_approved(decision, target_version)
        or deployment.get("target_version") != target_version
    ):
        raise EvidenceError("Active timer observation target must match the approved decision")
    if not isinstance(cleanup_plan, str) or not cleanup_plan.strip():
        raise EvidenceError("Active timer runtime check needs a cleanup plan")

    observed = observation.get("observation")
    if not isinstance(observed, dict):
        raise EvidenceError("Active timer observation must identify the timer update behavior")
    expected_locations = {hit["location"] for hit in hits}
    timer_observations = observed.get("timers")
    if (
        not concrete_reference(observed.get("evidence_reference"))
        or not isinstance(timer_observations, list)
        or not timer_observations
    ):
        raise EvidenceError("Active timer observation must list every affected process timer")
    covered_locations = set()
    seen_timers = set()
    for timer in timer_observations:
        if not isinstance(timer, dict):
            raise EvidenceError("Active timer observation contains an invalid timer record")
        model_path = timer.get("model_path")
        process_id = timer.get("process_id")
        timer_id = timer.get("timer_id")
        if not all(concrete_reference(value) for value in (model_path, process_id, timer_id)):
            raise EvidenceError("Active timer observation must identify each affected model process timer")
        timer_key = (model_path, process_id, timer_id)
        if timer_key in seen_timers:
            raise EvidenceError("Active timer observation contains a duplicate model process timer")
        if timer_key not in expected_timers:
            raise EvidenceError(
                "Active timer observation does not match the reviewed affected timer inventory"
            )
        seen_timers.add(timer_key)
        try:
            source_locations = strings(
                timer.get("source_locations"),
                "Active timer source locations",
            )
        except EvidenceError as exc:
            raise EvidenceError(
                "Active timer observation must cover every detected update source"
            ) from exc
        if not source_locations or set(source_locations) != expected_timers[timer_key]:
            raise EvidenceError(
                "Active timer observation must cover every detected update source and match "
                "the reviewed affected timer inventory"
            )
        covered_locations.update(source_locations)
        if (
            timer.get("active_before_updates") is not True
            or type(timer.get("updates_applied")) is not int
            or timer["updates_applied"] != 2
            or type(timer.get("obsolete_deadlines_fired")) is not int
            or timer["obsolete_deadlines_fired"] != 0
            or type(timer.get("final_deadline_fired")) is not int
            or timer["final_deadline_fired"] != 1
        ):
            raise EvidenceError(
                "Active timer observation must prove two updates per affected timer, "
                "no obsolete deadlines, and one final deadline"
            )
        if not concrete_reference(timer.get("process_instance_id")):
            raise EvidenceError(
                "Active timer observation must identify the process instance for the final deadline"
            )
        requested_final_deadline = parse_timezone_aware_timestamp(
            timer.get("requested_final_deadline"),
            "Requested final deadline",
        )
        final_deadline_fired_at = parse_timezone_aware_timestamp(
            timer.get("final_deadline_fired_at"),
            "Final deadline firing time",
        )
        if final_deadline_fired_at < requested_final_deadline:
            raise EvidenceError(
                "Active timer final deadline fired before the requested final deadline"
            )
    if (
        seen_timers != set(expected_timers)
        or covered_locations != expected_locations
    ):
        raise EvidenceError(
            "Active timer observation must match the reviewed affected timer inventory"
        )

    cleanup = observation.get("cleanup")
    if not isinstance(cleanup, dict) or cleanup.get("completed") is not True:
        raise EvidenceError("Active timer observation must prove cleanup completed")
    if not concrete_reference(cleanup.get("evidence_reference")):
        raise EvidenceError("Active timer observation needs a cleanup evidence reference")


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
    timer_inventory = {}
    timer_elements_by_model = {}
    issues = []
    deployment_sets = {}
    process_ids_by_set = {}
    duplicate_process_ids = {}
    active_timer_updates = {}
    latest_version_calls = {}
    process_callers = {}
    input_snapshot = {}
    active_timer_update_decision = active_timer_decision(evidence)
    executable_by_model = {}
    process_ids_by_model = {}
    source_process_ids_by_model = {}
    model_by_path = {}
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
        module_root = project_path(root, path, "module")
        if not module_root.is_dir():
            issues.append(f"Module directory is missing: {path}")
        updates, latest, callers, module_snapshot, scan_issues = scan_module_sources(root, path)
        issues.extend(scan_issues)
        active_timer_updates[path] = updates
        latest_version_calls[path] = latest
        process_callers[path] = callers
        input_snapshot.update(module_snapshot)
        need("module", path, "compile")
        need("module", path, "review", method="review")
        need("module", path, "active_timer_updates", method="review")
        allowed.add(("module", path, "active_timer_update_runtime", None))
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
    if not modules:
        active_timer_updates["."] = []
        latest_version_calls["."] = []
        process_callers["."] = []
        need("project", ".", "active_timer_updates", method="review")
        allowed.add(("project", ".", "active_timer_update_runtime", None))

    converted_paths = []
    for model in models:
        source = model["source_path"]
        converted = model.get("path")
        if not isinstance(converted, str):
            issues.append(f"{source}: missing converted copy path")
            continue
        copy_path = project_path(root, converted, "converted copy")
        converted_paths.append(copy_path)
        model_by_path[converted] = model
        need("model", converted, "lint")
        need("model", converted, "deployment")
        need("model", converted, "review", method="review")
        timers[converted] = []
        module = model.get("module")
        deployment_set = model.get("deployment_set")
        if not isinstance(module, str) or not module:
            issues.append(f"{converted}: identify its owning module")
        elif module_paths and module not in module_paths:
            issues.append(f"{converted}: unknown owning module {module}")
        elif not module_paths and module != ".":
            issues.append(f"{converted}: use module '.' when the project has no code modules")
        if not isinstance(deployment_set, str) or not deployment_set:
            issues.append(f"{converted}: identify its deployment set")
        try:
            source_document, document = read_model(root, source, converted)
        except EvidenceError as exc:
            issues.append(str(exc))
            continue
        for input_path in (source, converted):
            try:
                resolved = project_path(root, input_path, "model snapshot input", must_exist=True)
                if not resolved.is_file():
                    raise EvidenceError(f"Model snapshot input is not a file: {input_path}")
                input_snapshot[resolved.relative_to(root).as_posix()] = hashlib.sha256(
                    resolved.read_bytes()
                ).hexdigest()
            except (EvidenceError, OSError) as exc:
                issues.append(str(exc))
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
        all_processes = document.findall(f"{BPMN}process")
        all_process_ids = [process.get("id") for process in all_processes]
        if any(not process_id for process_id in all_process_ids):
            issues.append(f"{converted}: BPMN process definition lacks an ID")
        if len(all_process_ids) != len(set(all_process_ids)):
            issues.append(f"{converted}: duplicate BPMN process IDs within one model")
        process_ids_by_model[converted] = {
            process_id for process_id in all_process_ids if process_id
        }
        source_process_ids_by_model[converted] = {
            process.get("id")
            for process in source_document.findall(f"{BPMN}process")
            if process.get("id")
        }
        timer_elements_by_model[converted], timer_element_issues = timer_elements(document)
        issues.extend(f"{converted}: {issue}" for issue in timer_element_issues)
        executable = {
            process.get("id"): process
            for process in all_processes
            if process.get("isExecutable") in ("true", "1")
        }
        executable_by_model[converted] = set(executable)
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
        source_timers, source_timer_issues = repeating_starts(source_document)
        converted_timers, converted_timer_issues = repeating_starts(document)
        issues.extend(f"{source}: {issue}" for issue in source_timer_issues)
        issues.extend(f"{converted}: {issue}" for issue in converted_timer_issues)
        for process_id, start_id in sorted(set(source_timers) | set(converted_timers)):
            source_cycle = source_timers.get((process_id, start_id))
            converted_cycle = converted_timers.get((process_id, start_id))
            if source_cycle is None:
                disposition = "add"
            elif converted_cycle is None:
                disposition = "remove"
            elif source_cycle == converted_cycle:
                disposition = "preserve"
            else:
                disposition = "change"
            target = f"{converted}#{process_id}#{start_id}"
            disposition_key = need("timer", target, "disposition", method="review")
            expected_cycle = converted_cycle if converted_cycle is not None else source_cycle
            details = cycle_details(expected_cycle)
            if details is None:
                issues.append(f"{target}: unresolved repeating timer expression {expected_cycle!r}")
                details = {
                    "cycle_type": None,
                    "interval": None,
                    "repetitions": None,
                }
            inventory = {
                "model": converted,
                "module": module,
                "deployment_set": deployment_set,
                "process_id": process_id,
                "start_id": start_id,
                "source_cycle": source_cycle,
                "converted_cycle": converted_cycle,
                "cycle_type": details["cycle_type"],
                "interval": details["interval"],
                "repetitions": details["repetitions"],
                "automatic_start_effect": "Each firing starts a process instance.",
                "expected_disposition": disposition,
            }
            timer_inventory[disposition_key] = inventory
            if converted_cycle is not None:
                preflight_key = need("timer", target, "preflight")
                timer_inventory[preflight_key] = inventory
                timers[converted].append(preflight_key)
    if len(converted_paths) != len(set(converted_paths)):
        issues.append("Converted copies must be distinct")

    declared_sets = evidence.get("deployment_sets")
    if not isinstance(declared_sets, list):
        issues.append("Evidence deployment_sets must be an array")
        declared_sets = []
    for entry in declared_sets:
        if not isinstance(entry, dict):
            issues.append("Every deployment set must be an object")
            continue
        name = entry.get("name")
        if not isinstance(name, str) or not name:
            issues.append("Every deployment set needs a name")
            continue
        if name in deployment_sets:
            issues.append(f"Duplicate deployment set: {name}")
            continue
        try:
            set_models = strings(entry.get("models"), f"{name} models")
            set_modules = strings(entry.get("modules"), f"{name} modules")
        except EvidenceError as exc:
            issues.append(str(exc))
            continue
        unknown_models = set(set_models) - set(model_by_path)
        unknown_modules = set(set_modules) - module_paths
        if unknown_models:
            issues.append(f"{name}: unknown converted models {sorted(unknown_models)}")
        if unknown_modules:
            issues.append(f"{name}: unknown modules {sorted(unknown_modules)}")
        deployment_sets[name] = {"models": set_models, "modules": set_modules}
        need("deployment_set", name, "preflight", method="review")
        ids = {}
        source_ids = {}
        for model_path in set_models:
            model = model_by_path.get(model_path)
            if model is None:
                continue
            if model.get("deployment_set") != name:
                issues.append(f"{model_path}: deployment set membership does not match {name}")
            if model.get("module") not in set_modules and not (
                model.get("module") == "." and not module_paths and not set_modules
            ):
                issues.append(f"{model_path}: owning module is not listed in deployment set {name}")
            for process_id in process_ids_by_model.get(model_path, set()):
                ids.setdefault(process_id, []).append(model_path)
            for process_id in source_process_ids_by_model.get(model_path, set()):
                source_ids.setdefault(process_id, []).append(model_path)
        process_ids_by_set[name] = ids
        for process_id in set(ids) | set(source_ids):
            model_paths = sorted(set(ids.get(process_id, [])) | set(source_ids.get(process_id, [])))
            if len(ids.get(process_id, [])) > 1 or len(source_ids.get(process_id, [])) > 1:
                key = (name, process_id)
                duplicate_process_ids[key] = model_paths
                need("deployment_set", name, "duplicate_process_id", process_id, method="review")

    memberships = {}
    for name, entry in deployment_sets.items():
        for model_path in entry["models"]:
            memberships[model_path] = memberships.get(model_path, 0) + 1
    for converted in model_by_path:
        if memberships.get(converted, 0) != 1:
            issues.append(f"{converted}: must belong to exactly one declared deployment set")
        model = model_by_path[converted]
        set_name = model.get("deployment_set")
        if not isinstance(set_name, str) or set_name not in deployment_sets:
            issues.append(f"{converted}: names an undeclared deployment set")
    for module in module_paths:
        if not any(module in entry["modules"] for entry in deployment_sets.values()):
            issues.append(f"{module}: include the module in its deployment set inventory")

    return ValidationPlan(
        required=required,
        allowed=allowed,
        timers=timers,
        timer_inventory=timer_inventory,
        timer_elements_by_model=timer_elements_by_model,
        docker_suites=docker_suites,
        deployment_sets=deployment_sets,
        models_by_path=model_by_path,
        process_ids_by_set=process_ids_by_set,
        duplicate_process_ids=duplicate_process_ids,
        active_timer_updates=active_timer_updates,
        latest_version_calls=latest_version_calls,
        process_callers=process_callers,
        active_timer_update_decision=active_timer_update_decision,
        source_snapshot_digest=source_snapshot_digest(input_snapshot),
        issues=issues,
    )


def check_key(check):
    return (check.get("type"), check.get("target"), check.get("kind"), check.get("scenario"))


def needs_safe_environment(key):
    return (
        key[0] == "process"
        or key[0] == "model" and key[2] == "deployment"
        or key[0] in ("module", "project")
        and key[2] in (*RUNTIME_CHECKS, "active_timer_update_runtime")
    )


def load_checks(root, evidence, allowed, issues):
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
            if key not in allowed or key in checks:
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
            if key[0] == "timer" and key[2] == "preflight" and result == "passed":
                if check.get("environment") not in SAFE_ENVIRONMENTS:
                    raise EvidenceError(f"{key}: timer preflight needs a local or non-production target")
                if (
                    check.get("target_disposable") is not True
                    or not isinstance(check.get("cleanup_plan"), str)
                    or not check["cleanup_plan"].strip()
                ):
                    raise EvidenceError(f"{key}: timer preflight needs an explicitly disposable target and cleanup plan")
                if not is_camunda_target_version(check.get("target_version")):
                    raise EvidenceError(
                        f"{key}: timer preflight needs a concrete Camunda 8 target version"
                    )
            if needs_safe_environment(key) and result == "passed":
                if check.get("environment") not in SAFE_ENVIRONMENTS:
                    raise EvidenceError(f"{key}: production or unknown runtime target")
                if key[2] == "active_timer_update_runtime":
                    if (
                        check.get("target_disposable") is not True
                        or not isinstance(check.get("cleanup_plan"), str)
                        or not check["cleanup_plan"].strip()
                    ):
                        raise EvidenceError(f"{key}: active timer validation needs a disposable target and cleanup plan")
                    if not is_camunda_target_version(check.get("target_version")):
                        raise EvidenceError(
                            f"{key}: active timer validation needs a concrete Camunda 8 target version"
                        )
                    validate_active_timer_update_observation(
                        check.get("active_timer_update_observation"),
                        check.get(
                            "active_timer_update_active_hits",
                            check.get("active_timer_update_hits"),
                        ),
                        check.get("active_timer_update_inventory"),
                        check.get("active_timer_update_decision"),
                        check.get("environment"),
                        check.get("target_disposable"),
                        check.get("target_version"),
                        check.get("cleanup_plan"),
                    )
            checks[key] = (index, check, reference)
        except EvidenceError as exc:
            issues.append(str(exc))
    return checks


def deployment_set_callers(plan, name, module):
    process_ids = set(plan.process_ids_by_set.get(name, {})) | {
        process_id
        for set_name, process_id in plan.duplicate_process_ids
        if set_name == name
    }
    return [
        hit
        for hit in plan.process_callers.get(module, [])
        if hit["process_id"] == "unknown" or hit["process_id"] in process_ids
    ]


def deployment_set_latest_version_calls(plan, name, module):
    relevant_locations = {
        hit["location"]
        for hit in deployment_set_callers(plan, name, module)
    }
    scanned_locations = {
        hit["location"]
        for hit in plan.process_callers.get(module, [])
    }
    return [
        hit
        for hit in plan.latest_version_calls.get(module, [])
        if hit.get("process_call_location") is None
        and (
            hit["location"] in relevant_locations
            or hit["location"] not in scanned_locations
        )
    ]


def caller_hit_can_be_resolved(hit):
    return (
        hit["process_id"] != "unknown"
        or hit.get("process_id_resolution") == "reviewable"
    ) and (
        hit["version_selection"] != "unknown"
        or hit.get("version_selection_resolution") == "reviewable"
    )


def deployment_set_snapshot(plan, name):
    entry = plan.deployment_sets[name]
    return {
        "name": name,
        "models": sorted(entry["models"]),
        "modules": sorted(entry["modules"]),
        "process_ids": {
            process_id: sorted(model_paths)
            for process_id, model_paths in sorted(plan.process_ids_by_set.get(name, {}).items())
        },
        "latest_version_calls": sorted(
            (
                hit
                for module in entry["modules"]
                for hit in deployment_set_latest_version_calls(plan, name, module)
            ),
            key=lambda hit: (hit["module"], hit["location"]),
        ),
        "process_callers": sorted(
            (
                hit
                for module in entry["modules"]
                for hit in deployment_set_callers(plan, name, module)
            ),
            key=lambda hit: (
                hit["module"],
                hit["location"],
                hit["operation"],
                hit["process_id"],
            ),
        ),
    }


def validate_caller_inventory(root, plan, name, check, checks, issues):
    entry = plan.deployment_sets[name]
    callers = check.get("caller_inventory")
    if not isinstance(callers, list):
        issues.append(f"{name}: deployment preflight needs a machine-readable caller inventory")
        return []
    process_ids = set(plan.process_ids_by_set.get(name, {})) | {
        process_id
        for set_name, process_id in plan.duplicate_process_ids
        if set_name == name
    }
    validated = []
    seen = set()
    for caller in callers:
        if not isinstance(caller, dict):
            issues.append(f"{name}: invalid process caller record")
            continue
        module = caller.get("module")
        location = caller.get("location")
        process_id = caller.get("process_id")
        operation = caller.get("operation")
        selection = caller.get("version_selection")
        if (
            module not in entry["modules"]
            or not isinstance(location, str)
            or not isinstance(process_id, str) or not process_id
            or operation not in (
                "startProcessInstanceByKey",
                "createProcessInstanceByKey",
                "bpmnProcessId",
                "other",
            )
            or selection not in ("latest_version", "explicit_version", "unknown")
        ):
            issues.append(f"{name}: malformed process caller record {caller}")
            continue
        try:
            file_name, line_number = location.rsplit(":", 1)
            if not line_number.isdigit() or int(line_number) < 1:
                raise ValueError
            caller_path = project_path(root, file_name, "caller location", must_exist=True)
            module_root = project_path(root, module, "caller module", must_exist=True)
            if not caller_path.is_file() or not caller_path.is_relative_to(module_root):
                raise ValueError
        except (EvidenceError, ValueError):
            issues.append(f"{name}: caller location is outside its module or missing: {location}")
            continue
        if selection == "unknown" or process_id == "unknown":
            issues.append(f"{name}: unresolved process caller at {location}")
        identity = (module, location, process_id, operation, selection)
        if identity in seen:
            issues.append(f"{name}: duplicate caller record at {location}")
        seen.add(identity)
        validated.append(caller)
    mapped_ids = {
        mapping.get("to_process_id")
        for key, (_, record, _) in checks.items()
        if key[0] == "deployment_set" and key[1] == name
        and key[2] == "duplicate_process_id"
        and record.get("disposition") == "mapped_rename"
        for mapping in record.get("rename_mappings", [])
        if isinstance(mapping, dict)
        and isinstance(mapping.get("to_process_id"), str)
    }
    detected_process_sites = set()
    standalone_latest_version_sites = set()
    for module in entry["modules"]:
        detected_callers = deployment_set_callers(plan, name, module)
        detected_process_sites.update(
            (hit["module"], hit["location"], hit["operation"])
            for hit in detected_callers
        )
        process_locations = {
            (hit["module"], hit["location"])
            for hit in detected_callers
        }
        standalone_latest_version_sites.update(
            (module, hit["location"])
            for hit in deployment_set_latest_version_calls(plan, name, module)
            if (module, hit["location"]) not in process_locations
        )
    records_by_site = {}
    for index, caller in enumerate(validated):
        site = (caller["module"], caller["location"], caller["operation"])
        records_by_site.setdefault(site, []).append(index)
        if (
            caller["process_id"] != "unknown"
            and caller["process_id"] not in process_ids
        ):
            matching_hits = [
                hit
                for hit in deployment_set_callers(plan, name, caller["module"])
                if (
                    hit["module"],
                    hit["location"],
                    hit["operation"],
                ) == site
                and caller_hit_can_be_resolved(hit)
                and (
                    hit["process_id"] == "unknown"
                    or hit["process_id"] == caller["process_id"]
                )
                and (
                    hit["version_selection"] == "unknown"
                    or hit["version_selection"] == caller["version_selection"]
                )
            ]
            if not matching_hits and caller["process_id"] not in mapped_ids:
                issues.append(
                    f"{name}: caller names an unknown process ID {caller['process_id']}"
                )
    listed_callers = {
        (
            caller["module"],
            caller["location"],
            caller["process_id"],
            caller["operation"],
            caller["version_selection"],
        )
        for caller in validated
    }
    matched_records = set()
    for module in entry["modules"]:
        detected_callers = deployment_set_callers(plan, name, module)
        for hit in (
            hit
            for hit in detected_callers
            if hit["process_id"] != "unknown"
            and hit["version_selection"] != "unknown"
        ):
            identity = (
                hit["module"],
                hit["location"],
                hit["process_id"],
                hit["operation"],
                hit["version_selection"],
            )
            if identity not in listed_callers:
                issues.append(
                    f"{name}: detected process caller is missing from the inventory: "
                    f'{hit["operation"]}({hit["process_id"]}) at {hit["location"]}'
                )
            else:
                exact_matches = [
                    index
                    for index, caller in enumerate(validated)
                    if index not in matched_records
                    and (
                        caller["module"],
                        caller["location"],
                        caller["process_id"],
                        caller["operation"],
                        caller["version_selection"],
                    ) == identity
                ]
                if exact_matches:
                    matched_records.add(exact_matches[0])
        for hit in (
            hit
            for hit in detected_callers
            if hit["process_id"] == "unknown"
            or hit["version_selection"] == "unknown"
        ):
            if not caller_hit_can_be_resolved(hit):
                issues.append(f"{name}: unresolved process caller at {hit['location']}")
                continue
            site = (hit["module"], hit["location"], hit["operation"])
            candidates = [
                index
                for index in records_by_site.get(site, [])
                if index not in matched_records
            ]
            if len(candidates) != 1:
                issues.append(
                    f"{name}: reviewable caller needs one resolved inventory record at "
                    f"{hit['location']}"
                )
                continue
            index = candidates[0]
            caller = validated[index]
            if (
                caller["process_id"] == "unknown"
                or caller["version_selection"] == "unknown"
                or hit["process_id"] != "unknown"
                and caller["process_id"] != hit["process_id"]
                or hit["version_selection"] != "unknown"
                and caller["version_selection"] != hit["version_selection"]
            ):
                issues.append(f"{name}: unresolved process caller at {hit['location']}")
                continue
            matched_records.add(index)
        for hit in deployment_set_latest_version_calls(plan, name, module):
            chained_call_records = [
                index
                for index, caller in enumerate(validated)
                if index in matched_records
                and caller["module"] == module
                and caller["location"] == hit["location"]
                and caller["operation"] != "other"
                and caller["version_selection"] == "latest_version"
            ]
            if chained_call_records:
                continue
            matching_records = [
                index
                for index, caller in enumerate(validated)
                if index not in matched_records
                and caller["module"] == module
                and caller["location"] == hit["location"]
                and caller["operation"] == "other"
                and caller["version_selection"] == "latest_version"
            ]
            if not matching_records:
                issues.append(
                    f"{name}: latestVersion caller is missing from the inventory: "
                    f"{hit['location']}"
                )
            elif len(matching_records) != 1:
                issues.append(
                    f"{name}: standalone latestVersion needs one matching inventory record "
                    f"at {hit['location']}"
                )
            else:
                matched_records.add(matching_records[0])
    for caller in validated:
        site = (caller["module"], caller["location"], caller["operation"])
        if site not in detected_process_sites and not (
            caller["operation"] == "other"
            and caller["version_selection"] == "latest_version"
            and (caller["module"], caller["location"])
            in standalone_latest_version_sites
        ):
            issues.append(
                f"{name}: caller inventory record has no detected process call or standalone "
                f"latestVersion site at {caller['location']}"
            )
    return validated


def validate_deployment_set_evidence(root, plan, checks, issues, deployment_set=None):
    names = [deployment_set] if deployment_set is not None else plan.deployment_sets
    for name in names:
        if name not in plan.deployment_sets:
            issues.append(f"{name}: unknown deployment set")
            continue
        key = ("deployment_set", name, "preflight", None)
        recorded = checks.get(key)
        callers = []
        if recorded is not None:
            check = recorded[1]
            expected = deployment_set_snapshot(plan, name)
            if check.get("deployment_set_inventory") != expected:
                issues.append(f"{name}: deployment set or latestVersion inventory changed after its preflight")
            if check["result"] == "passed":
                callers = validate_caller_inventory(root, plan, name, check, checks, issues)
        for (set_name, process_id), model_paths in plan.duplicate_process_ids.items():
            if set_name != name:
                continue
            collision_key = ("deployment_set", name, "duplicate_process_id", process_id)
            collision = checks.get(collision_key)
            if collision is None:
                issues.append(
                    f"{name}: duplicate process ID {process_id} across {', '.join(model_paths)} "
                    "needs an explicit version or mapped rename decision"
                )
                continue
            decision = collision[1]
            expected_collision = {
                "deployment_set": name,
                "process_id": process_id,
                "models": model_paths,
            }
            if decision.get("process_id_collision") != expected_collision:
                issues.append(f"{name}: duplicate process ID inventory changed for {process_id}")
            disposition = decision.get("disposition")
            if not isinstance(disposition, str) or disposition not in PROCESS_ID_DISPOSITIONS:
                issues.append(f"{name}: duplicate process ID {process_id} needs an explicit-version or mapped-rename decision")
                continue
            matching_callers = [caller for caller in callers if caller.get("process_id") == process_id]
            if not matching_callers and disposition == "explicit_version":
                issues.append(
                    f"{name}: duplicate process ID {process_id} needs a non-empty caller inventory"
                )
            if disposition == "explicit_version":
                if process_id not in plan.process_ids_by_set.get(name, {}):
                    issues.append(f"{name}: explicit-version decision references removed process ID {process_id}")
                if any(caller.get("version_selection") != "explicit_version" for caller in matching_callers):
                    issues.append(f"{name}: callers of duplicate process ID {process_id} still use latestVersion or are unresolved")
                if decision.get("rename_mappings") not in (None, []):
                    issues.append(f"{name}: explicit-version decision must not include rename mappings")
            else:
                mappings = decision.get("rename_mappings")
                if not isinstance(mappings, list):
                    issues.append(f"{name}: mapped rename for {process_id} needs an old-to-new mapping")
                    continue
                mapped_models = set()
                new_ids = set()
                valid = True
                for mapping in mappings:
                    if not isinstance(mapping, dict):
                        valid = False
                        continue
                    model = mapping.get("model")
                    old_id = mapping.get("from_process_id")
                    new_id = mapping.get("to_process_id")
                    if (
                        not isinstance(model, str) or model not in model_paths
                        or old_id != process_id
                        or not isinstance(new_id, str) or not new_id or new_id == process_id
                        or model in mapped_models or new_id in new_ids
                    ):
                        valid = False
                    if isinstance(model, str):
                        mapped_models.add(model)
                    if isinstance(new_id, str):
                        new_ids.add(new_id)
                converted_ids = plan.process_ids_by_set.get(name, {})
                existing_ids = {
                    converted_id
                    for converted_id, paths in converted_ids.items()
                    if any(model not in model_paths for model in paths)
                }
                if (
                    not valid or mapped_models != set(model_paths) or new_ids.intersection(existing_ids)
                    or any(
                        mapping.get("model") not in converted_ids.get(mapping.get("to_process_id"), [])
                        or mapping.get("model") in converted_ids.get(process_id, [])
                        for mapping in mappings if isinstance(mapping, dict)
                    )
                ):
                    issues.append(f"{name}: incomplete or conflicting rename mapping for {process_id}")
                if any(caller.get("process_id") == process_id for caller in callers):
                    issues.append(f"{name}: callers still reference renamed process ID {process_id}")
                if any(
                    len(converted_ids.get(converted_id, [])) > 1
                    for converted_id in new_ids | {process_id}
                ):
                    issues.append(
                        f"{name}: mapped rename for {process_id} remains blocked while converted models still collide"
                    )


def validate_timer_inventory(plan, checks, issues, model_path=None):
    for key, expected in plan.timer_inventory.items():
        if model_path is not None and expected.get("model") != model_path:
            continue
        recorded = checks.get(key)
        if recorded is None:
            continue
        check = recorded[1]
        if check.get("timer_inventory") != expected:
            issues.append(f"{key}: timer cycle or deployment inventory changed after it was recorded")
        if key[2] == "preflight" and check["result"] == "passed":
            try:
                validate_timer_observation(
                    check.get("timer_observation"),
                    expected,
                    check.get("environment"),
                    check.get("target_disposable"),
                    check.get("target_version"),
                    check.get("cleanup_plan"),
                )
            except EvidenceError as exc:
                issues.append(f"{key}: {exc}")
        if key[2] == "disposition" and check["result"] == "passed":
            if check.get("disposition") != expected["expected_disposition"]:
                issues.append(
                    f"{key}: expected timer disposition {expected['expected_disposition']}, "
                    f"not {check.get('disposition')}"
                )


def validate_active_timer_updates(plan, checks, issues):
    decision = plan.active_timer_update_decision
    for target, hits in plan.active_timer_updates.items():
        category = "module" if target != "." else "project"
        key = (category, target, "active_timer_updates", None)
        recorded = checks.get(key)
        runtime_key = (category, target, "active_timer_update_runtime", None)
        runtime = checks.get(runtime_key)
        if recorded and recorded[1].get("active_timer_update_hits") != hits:
            issues.append(f"{key}: active timer update inventory changed after its review")
        if runtime and runtime[1].get("active_timer_update_hits") != hits:
            issues.append(f"{runtime_key}: active timer update inventory changed after runtime validation")
        if not hits:
            if recorded and recorded[1]["result"] == "passed":
                if recorded[1].get("disposition") != "no_updates":
                    issues.append(f"{key}: no active timer updates were detected; record no_updates")
                if recorded[1].get("active_timer_update_inventory") != []:
                    issues.append(f"{key}: no_updates review must have an empty affected timer inventory")
                try:
                    normalize_non_timer_update_evidence(
                        recorded[1].get("non_timer_update_evidence", []),
                        hits,
                    )
                except EvidenceError as exc:
                    issues.append(f"{key}: {exc}")
            if runtime:
                issues.append(f"{runtime_key}: runtime evidence is unexpected without an active timer update")
            continue
        if recorded is None:
            if not active_timer_decision_is_approved(decision):
                issues.append(
                    f"{target}: active timer updates need an approved project decision and "
                    "evidence for a supported C8 alternative"
                )
            locations = sorted({f'{hit["kind"]} at {hit["location"]}' for hit in hits})
            repeated = " repeated update references" if len(hits) > 1 else ""
            issues.append(
                f"{target}: active C7 timer due-date update{repeated} detected at "
                f"{', '.join(locations)}; record the blocking finding"
            )
            continue
        if recorded[1]["result"] != "passed":
            locations = sorted({f'{hit["kind"]} at {hit["location"]}' for hit in hits})
            repeated = " repeated update references" if len(hits) > 1 else ""
            issues.append(
                f"{target}: active C7 timer due-date update{repeated} remains blocked at "
                f"{', '.join(locations)}"
            )
            continue
        check = recorded[1]
        try:
            _, active_hits = normalize_non_timer_update_evidence(
                check.get("non_timer_update_evidence", []),
                hits,
            )
        except EvidenceError as exc:
            issues.append(f"{key}: {exc}")
            active_hits = hits
        if not active_hits:
            if check.get("disposition") != "non_timer":
                issues.append(f"{key}: classify every detected update as non-timer before clearing it")
            if check.get("active_timer_update_inventory") != []:
                issues.append(f"{key}: non-timer review must have an empty affected timer inventory")
            if runtime:
                issues.append(f"{runtime_key}: runtime evidence is unexpected without an active timer update")
            continue
        if not active_timer_decision_is_approved(decision):
            issues.append(
                f"{target}: active timer updates need an approved project decision and "
                "evidence for a supported C8 alternative"
            )
        locations = sorted({f'{hit["kind"]} at {hit["location"]}' for hit in active_hits})
        repeated = " repeated update references" if len(active_hits) > 1 else ""
        if check.get("disposition") != "verified":
            issues.append(
                f"{key}: detected active timer updates must stay blocked until verified at "
                f"{', '.join(locations)}"
            )
            continue
        try:
            affected_timers = normalize_affected_timer_inventory(
                check.get("active_timer_update_inventory"),
                active_hits,
                timer_elements_for_module(plan, target),
            )
        except EvidenceError as exc:
            issues.append(f"{key}: {exc}")
            continue
        if check.get("active_timer_update_decision") != decision:
            issues.append(f"{key}: approved project decision changed after the review")
        if not active_timer_decision_is_approved(check.get("active_timer_update_decision")):
            issues.append(f"{key}: verified timer handling needs approved project-decision status and support evidence")
        if runtime is None:
            issues.append(f"Missing {category} active_timer_update_runtime: {target}")
        elif runtime[0] <= recorded[0]:
            issues.append(f"{runtime_key}: runtime verification must follow the approved review")
        elif runtime[1].get(
            "active_timer_update_active_hits",
            runtime[1].get("active_timer_update_hits"),
        ) != active_hits:
            issues.append(f"{runtime_key}: runtime update sources differ from the reviewed active-timer sources")
        elif runtime[1].get("active_timer_update_inventory") != affected_timers:
            issues.append(f"{runtime_key}: runtime timer inventory differs from the approved review")
        elif runtime[1]["result"] != "passed":
            issues.append(f"{runtime_key}: supported replacement needs passing disposable-target runtime evidence")
        else:
            runtime_decision = runtime[1].get("active_timer_update_decision")
            if runtime_decision != decision:
                issues.append(f"{runtime_key}: runtime evidence does not match the current project decision")
            if not active_timer_decision_is_approved(runtime_decision, runtime[1].get("target_version")):
                issues.append(f"{runtime_key}: runtime target does not match an approved project decision")


def check_matches_current_inputs(plan, key, check):
    if check.get("source_snapshot_sha256") != plan.source_snapshot_digest:
        return False
    if key[0] == "deployment_set" and key[2] == "preflight":
        return check.get("deployment_set_inventory") == deployment_set_snapshot(plan, key[1])
    if key[0] == "deployment_set" and key[2] == "duplicate_process_id":
        expected = {
            "deployment_set": key[1],
            "process_id": key[3],
            "models": plan.duplicate_process_ids.get((key[1], key[3])),
        }
        return check.get("process_id_collision") == expected
    if key[0] == "timer":
        return check.get("timer_inventory") == plan.timer_inventory.get(key)
    if key[2] in ("active_timer_updates", "active_timer_update_runtime"):
        hits = plan.active_timer_updates.get(key[1], [])
        if (
            check.get("active_timer_update_hits") != hits
            or check.get("active_timer_update_decision")
            != plan.active_timer_update_decision
        ):
            return False
        try:
            _, active_hits = normalize_non_timer_update_evidence(
                check.get("non_timer_update_evidence", []),
                hits,
            )
            inventory = normalize_affected_timer_inventory(
                check.get("active_timer_update_inventory"),
                active_hits,
                timer_elements_for_module(plan, key[1]),
            )
        except EvidenceError:
            return False
        return inventory == check.get("active_timer_update_inventory")
    return True


def require_fresh_passed_check(previous, key, plan, requirement):
    recorded = previous.get(key)
    if recorded is None or recorded[1].get("result") != "passed":
        raise EvidenceError(requirement)
    if not check_matches_current_inputs(plan, key, recorded[1]):
        raise EvidenceError(
            f"{key}: stale evidence cannot authorize a dependent command; rerun this check"
        )
    return recorded


def validate_source_snapshots(plan, checks, issues):
    for key, (_, check, _) in checks.items():
        if check.get("source_snapshot_sha256") != plan.source_snapshot_digest:
            issues.append(
                f"{key}: evidence is stale because an in-scope model or caller source changed; "
                "reinitialize or rerun the check"
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


def report(root):
    issues = []
    checks = {}
    plan = None
    try:
        evidence = read_json(root / EVIDENCE)
        plan = requirements(root, evidence)
        issues.extend(plan.issues)
        checks = load_checks(root, evidence, plan.allowed, issues)
        validate_source_snapshots(plan, checks, issues)
        validate_deployment_set_evidence(root, plan, checks, issues)
        validate_timer_inventory(plan, checks, issues)
        validate_active_timer_updates(plan, checks, issues)
        for key in sorted(plan.required.keys() - checks.keys(), key=lambda item: tuple(str(value) for value in item)):
            issues.append(f"Missing {key[0]} {key[2]}: {key[1]} {key[3] or ''}".strip())
        for key, (index, check, _) in checks.items():
            method = plan.required.get(key, "command")
            if check["method"] not in (method, "blocked"):
                issues.append(f"{key}: expected {method} evidence")
            if check["result"] != "passed":
                issues.append(f"{key}: {check['result']}: {check['reason']}")
            if key in plan.docker_suites and plan.docker_suites[key]:
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
                if inventory in plan.required:
                    review = checks.get(inventory)
                    if review is None or review[0] >= index or review[1]["result"] != "passed":
                        issues.append(f"{key}: worker input review must precede process execution")
            if key[0] == "timer" and key[2] == "preflight":
                disposition_key = ("timer", key[1], "disposition", key[3])
                disposition = checks.get(disposition_key)
                if (
                    disposition is None or disposition[0] >= index
                    or disposition[1]["result"] != "passed"
                ):
                    issues.append(f"{key}: timer disposition must pass before runtime preflight")
            if key[0] in ("model", "process") and (
                key[0] == "process" or key[2] == "deployment"
            ):
                model = key[1].split("#", 1)[0]
                model_entry = plan.models_by_path.get(model)
                if model_entry is not None:
                    set_name = model_entry.get("deployment_set")
                    if isinstance(set_name, str):
                        deployment_key = ("deployment_set", set_name, "preflight", None)
                        deployment = checks.get(deployment_key)
                        if (
                            deployment is None or deployment[0] >= index
                            or deployment[1]["result"] != "passed"
                        ):
                            issues.append(f"{key}: deployment-set preflight must pass before execution")
                        for collision_set, process_id in plan.duplicate_process_ids:
                            if collision_set != set_name:
                                continue
                            collision_key = (
                                "deployment_set", set_name, "duplicate_process_id", process_id
                            )
                            collision = checks.get(collision_key)
                            if (
                                collision is None or collision[0] >= index
                                or collision[1]["result"] != "passed"
                            ):
                                issues.append(f"{key}: duplicate process ID disposition must pass before execution")
                    else:
                        issues.append(f"{key}: process has no deployment-set assignment")
                for timer in plan.timers.get(model, []):
                    preflight = checks.get(timer)
                    if preflight is None or preflight[0] >= index or preflight[1]["result"] != "passed":
                        issues.append(f"{key}: timer preflight must pass before execution")
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
        "source_snapshot_sha256": plan.source_snapshot_digest if plan else None,
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
    if key[0] == "timer" and key[2] == "preflight" and args.action == "run":
        if args.environment not in SAFE_ENVIRONMENTS:
            raise EvidenceError("Timer preflight needs a local or non-production target")
        if not args.target_disposable or not args.cleanup_plan:
            raise EvidenceError("Timer preflight needs an explicitly disposable target and cleanup plan")
        if not is_camunda_target_version(args.target_version):
            raise EvidenceError(
                "Timer preflight needs a concrete Camunda 8 target version"
            )
    elif args.action == "run" and needs_safe_environment(key):
        if args.environment not in SAFE_ENVIRONMENTS:
            raise EvidenceError("Runtime and deployment checks require a local or non-production target")
    previous_issues = []
    previous = load_checks(root, evidence, plan.allowed, previous_issues)
    if previous_issues:
        raise EvidenceError("; ".join(previous_issues))
    active_timer_update_inventory = None
    active_timer_update_active_hits = None
    non_timer_update_evidence = None
    if key[0] in ("module", "project") and key[2] == "active_timer_update_runtime":
        hits = plan.active_timer_updates.get(key[1], [])
        inventory_key = (key[0], key[1], "active_timer_updates", None)
        inventory = require_fresh_passed_check(
            previous,
            inventory_key,
            plan,
            "Review and approve the active timer alternative before runtime validation",
        )
        non_timer_update_evidence, active_timer_update_active_hits = (
            normalize_non_timer_update_evidence(
                inventory[1].get("non_timer_update_evidence", []),
                hits,
            )
        )
        if (
            inventory[1].get("disposition") != "verified"
            or not active_timer_update_active_hits
            or inventory[1].get("active_timer_update_decision") != plan.active_timer_update_decision
            or not active_timer_decision_is_approved(
                plan.active_timer_update_decision,
                args.target_version,
            )
        ):
            raise EvidenceError("Review and approve the active timer alternative before runtime validation")
        if not args.target_disposable or not args.cleanup_plan:
            raise EvidenceError("Active timer validation needs an explicitly disposable target and cleanup plan")
        if not is_camunda_target_version(args.target_version):
            raise EvidenceError(
                "Active timer validation needs a concrete Camunda 8 target version"
            )
        active_timer_update_inventory = normalize_affected_timer_inventory(
            inventory[1].get("active_timer_update_inventory"),
            active_timer_update_active_hits,
            timer_elements_for_module(plan, key[1]),
        )
    if key[0] == "timer" and key[2] == "preflight":
        disposition_key = ("timer", key[1], "disposition", key[3])
        require_fresh_passed_check(
            previous,
            disposition_key,
            plan,
            "Record the timer disposition before runtime preflight",
        )
    caller_inventory = None
    rename_mappings = None
    timer_observation = None
    active_timer_update_observation = None
    if args.action == "run" and args.type == "model" and args.kind == "deployment":
        require_fresh_passed_check(
            previous,
            ("model", args.target, "lint", None),
            plan,
            "Lint must pass before deployment",
        )
    if args.action == "run" and (
        args.type == "process" or args.type == "model" and args.kind == "deployment"
    ):
        model = args.target.split("#", 1)[0]
        model_entry = plan.models_by_path.get(model)
        if model_entry is None or not isinstance(model_entry.get("deployment_set"), str):
            raise EvidenceError("Identify the model's deployment set before execution")
        set_name = model_entry["deployment_set"]
        deployment_key = ("deployment_set", set_name, "preflight", None)
        require_fresh_passed_check(
            previous,
            deployment_key,
            plan,
            "Deployment-set preflight must pass before deployment or process execution",
        )
        for collision_set, process_id in plan.duplicate_process_ids:
            if collision_set != set_name:
                continue
            collision_key = (
                "deployment_set", set_name, "duplicate_process_id", process_id
            )
            require_fresh_passed_check(
                previous,
                collision_key,
                plan,
                "Resolve duplicate process IDs before deployment or process execution",
            )
        for timer_key in plan.timers.get(model, []):
            require_fresh_passed_check(
                previous,
                timer_key,
                plan,
                "Timer preflight must pass before deployment or process execution",
            )
        for timer_key, timer_expected in plan.timer_inventory.items():
            if timer_key[2] != "disposition" or timer_expected.get("model") != model:
                continue
            require_fresh_passed_check(
                previous,
                timer_key,
                plan,
                "Review the timer disposition before deployment or process execution",
            )
        timer_issues = []
        validate_timer_inventory(
            plan,
            previous,
            timer_issues,
            model_path=model,
        )
        if timer_issues:
            raise EvidenceError(
                "Timer evidence is invalid before execution: "
                + "; ".join(timer_issues)
            )
        deployment_issues = []
        validate_deployment_set_evidence(
            root,
            plan,
            previous,
            deployment_issues,
            deployment_set=set_name,
        )
        if deployment_issues:
            raise EvidenceError(
                "Deployment-set evidence is invalid before execution: "
                + "; ".join(deployment_issues)
            )
    if args.action == "run" and args.type == "process" and args.kind == "process_path":
        inventory = ("process", args.target, "worker_input_inventory", None)
        if inventory in plan.required:
            require_fresh_passed_check(
                previous,
                inventory,
                plan,
                "Review worker inputs before testing a direct process start",
            )
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
        if key[0] == "deployment_set" and key[2] == "preflight":
            caller_inventory = parse_json_option(
                args.caller_inventory_json, "Caller inventory"
            )
            if caller_inventory is None:
                raise EvidenceError("Deployment-set preflight needs --caller-inventory-json")
        if key[0] == "deployment_set" and key[2] == "duplicate_process_id":
            if args.disposition not in PROCESS_ID_DISPOSITIONS:
                raise EvidenceError("Duplicate process IDs need explicit_version or mapped_rename disposition")
            rename_mappings = parse_json_option(
                args.rename_mappings_json, "Rename mappings"
            )
            if args.disposition == "mapped_rename" and rename_mappings is None:
                raise EvidenceError("Mapped rename needs --rename-mappings-json")
            if args.disposition == "explicit_version" and rename_mappings:
                raise EvidenceError("Explicit-version disposition cannot include rename mappings")
        if key[0] == "timer" and key[2] == "disposition":
            expected = plan.timer_inventory[key]["expected_disposition"]
            if args.disposition != expected:
                raise EvidenceError(f"Timer disposition must be {expected}")
        if key[2] == "active_timer_updates":
            hits = plan.active_timer_updates.get(key[1], [])
            evidence_input = parse_json_option(
                args.non_timer_update_evidence_json,
                "Non-timer update evidence",
            )
            non_timer_update_evidence, active_timer_update_active_hits = (
                normalize_non_timer_update_evidence(
                    evidence_input if evidence_input is not None else [],
                    hits,
                )
            )
            expected = (
                "no_updates"
                if not hits
                else "non_timer"
                if not active_timer_update_active_hits
                else "verified"
            )
            if args.disposition == "non_timer" and active_timer_update_active_hits:
                raise EvidenceError(
                    "non-timer update evidence must classify every detected due-date update"
                )
            if args.disposition != expected:
                raise EvidenceError(
                    f"Active timer update review must use {expected}; block unresolved updates"
                )
            if expected == "non_timer" and not non_timer_update_evidence:
                raise EvidenceError("Non-timer update evidence is required to clear detected updates")
            if expected == "verified" and not active_timer_decision_is_approved(
                plan.active_timer_update_decision
            ):
                raise EvidenceError(
                    "Active timer handling needs an approved project decision and evidence "
                    "for a supported C8 alternative"
                )
            affected_timer_inventory = parse_json_option(
                args.affected_timers_json,
                "Affected timer inventory",
            )
            active_timer_update_inventory = normalize_affected_timer_inventory(
                affected_timer_inventory if affected_timer_inventory is not None else [],
                active_timer_update_active_hits,
                timer_elements_for_module(plan, key[1]),
            )
    else:
        if not args.reason.strip():
            raise EvidenceError("A blocked check requires a reason")
        result = "blocked"
        reason = args.reason
        output = reason
    if key[0] == "timer" and key[2] == "preflight" and args.action == "run" and result == "passed":
        timer_observation = parse_json_object_option(
            args.timer_observation_json,
            "Timer observation evidence",
        )
        validate_timer_observation(
            timer_observation,
            plan.timer_inventory[key],
            args.environment,
            args.target_disposable,
            args.target_version,
            args.cleanup_plan,
        )
    if (
        key[0] in ("module", "project")
        and key[2] == "active_timer_update_runtime"
        and args.action == "run"
        and result == "passed"
    ):
        active_timer_update_observation = parse_json_object_option(
            args.active_timer_update_observation_json,
            "Active timer observation evidence",
        )
        validate_active_timer_update_observation(
            active_timer_update_observation,
            active_timer_update_active_hits,
            active_timer_update_inventory,
            plan.active_timer_update_decision,
            args.environment,
            args.target_disposable,
            args.target_version,
            args.cleanup_plan,
        )
    failure_class = None
    if result != "passed":
        if args.kind == "docker_info":
            failure_class = "docker_unavailable"
        elif (
            args.action == "block"
            and plan.docker_suites.get(key)
            and previous.get(("project", ".", "docker_info", None), (None, {}))[1].get("result")
            == "failed"
        ):
            failure_class = "docker_unavailable"
        elif args.kind == "tests" and "Could not find a valid Docker environment" in output:
            failure_class = "testcontainers"
        else:
            failure_class = "unclassified"
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
        "failure_class": failure_class,
        "environment": args.environment,
        "target_version": args.target_version,
        "target_disposable": args.target_disposable,
        "cleanup_plan": args.cleanup_plan,
        "disposition": args.disposition,
        "active_timer_update_decision": (
            plan.active_timer_update_decision
            if key[2] in ("active_timer_updates", "active_timer_update_runtime")
            else None
        ),
        "caller_inventory": caller_inventory if args.action == "review" and key[0] == "deployment_set" and key[2] == "preflight" else None,
        "rename_mappings": rename_mappings if args.action == "review" and key[0] == "deployment_set" and key[2] == "duplicate_process_id" else None,
        "deployment_set_inventory": deployment_set_snapshot(plan, args.target) if key[0] == "deployment_set" and key[2] == "preflight" else None,
        "process_id_collision": (
            {
                "deployment_set": args.target,
                "process_id": args.scenario,
                "models": plan.duplicate_process_ids[(args.target, args.scenario)],
            }
            if key[0] == "deployment_set" and key[2] == "duplicate_process_id"
            else None
        ),
        "timer_inventory": plan.timer_inventory.get(key),
        "timer_observation": timer_observation,
        "active_timer_update_observation": active_timer_update_observation,
        "active_timer_update_inventory": (
            active_timer_update_inventory
            if key[2] in ("active_timer_updates", "active_timer_update_runtime")
            else None
        ),
        "non_timer_update_evidence": (
            non_timer_update_evidence
            if key[2] in ("active_timer_updates", "active_timer_update_runtime")
            else None
        ),
        "source_snapshot_sha256": plan.source_snapshot_digest,
        "active_timer_update_hits": (
            plan.active_timer_updates.get(args.target, [])
            if key[2] in ("active_timer_updates", "active_timer_update_runtime")
            else None
        ),
        "active_timer_update_active_hits": (
            active_timer_update_active_hits
            if key[2] in ("active_timer_updates", "active_timer_update_runtime")
            else None
        ),
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
    plan = requirements(root, evidence)
    checks = load_checks(root, evidence, plan.allowed, plan.issues)
    if plan.issues:
        raise EvidenceError("; ".join(plan.issues))
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
        action.add_argument(
            "--type", required=True,
            choices=("project", "module", "model", "process", "timer", "deployment_set"),
        )
        action.add_argument("--target", required=True)
        action.add_argument("--kind", required=True)
        action.add_argument("--scenario")
        action.add_argument("--environment", choices=SAFE_ENVIRONMENTS)
        action.add_argument("--target-version")
        action.add_argument("--target-disposable", action="store_true")
        action.add_argument("--cleanup-plan", "--isolation-plan", dest="cleanup_plan")
        action.add_argument("--disposition")
        action.add_argument("--timer-observation-json")
        action.add_argument("--active-timer-update-observation-json")
        action.add_argument("--affected-timers-json")
        action.add_argument("--non-timer-update-evidence-json")
        action.add_argument("--caller-inventory-json")
        action.add_argument("--rename-mappings-json")
        if name == "run":
            action.add_argument("--timeout", type=int, default=300)
            action.add_argument("command", nargs=argparse.REMAINDER)
        elif name == "review":
            action.add_argument("--note", required=True)
        else:
            action.add_argument("--reason", required=True)
    classification = actions.add_parser("classify", help="Annotate a failed check after reviewing its output")
    classification.add_argument(
        "--type", required=True,
        choices=("module", "model", "process", "timer", "deployment_set"),
    )
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
