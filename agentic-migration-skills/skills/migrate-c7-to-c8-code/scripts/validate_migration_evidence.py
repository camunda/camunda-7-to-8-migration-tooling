#!/usr/bin/env python3
"""Validate migration check evidence and write the aggregate gate summary."""

import argparse
import json
import sys
from collections import Counter
from pathlib import Path


SCHEMA_VERSION = 1
RESULTS = {
    "passed",
    "failed",
    "blocked",
    "unknown",
    "not_run",
    "not_applicable",
}
FAILURE_CLASSES = {
    "application",
    "configuration",
    "infrastructure",
    "docker_unavailable",
    "testcontainers",
    "dependency",
    "compatibility",
    "behavior",
    "unknown",
}
ENVIRONMENTS = {
    "local",
    "non-production",
    "production",
    "unknown",
    "not-deployed",
}
SAFE_RUNTIME_ENVIRONMENTS = {"local", "non-production"}
SAFE_TIMER_ENVIRONMENTS = SAFE_RUNTIME_ENVIRONMENTS | {"not-deployed"}

MODULE_CHECKS = (
    "compile",
    "c7_dependencies",
    "c7_imports",
    "migration_todos",
    "legacy_client",
    "business_keys",
    "configuration",
    "eventual_consistency",
    "pagination",
    "worker_adapters",
    "deployment_resources",
    "spring_boot_run",
    "executable_jar",
    "external_launcher",
)
MODULE_CHECKS_REQUIRING_PASS = {
    "compile",
    "c7_dependencies",
    "c7_imports",
    "migration_todos",
    "legacy_client",
    "business_keys",
    "configuration",
    "eventual_consistency",
    "pagination",
    "worker_adapters",
    "deployment_resources",
}
MODEL_CHECKS = (
    "converted_copy",
    "xml_parse",
    "lint",
    "deployment",
    "source_preservation",
    "resource_packaging",
    "findings_verdicts",
    "generated_forms",
    "accepted_forms",
    "form_parsing",
    "form_schema",
    "form_js",
    "form_definition",
    "form_deployment",
    "form_references",
    "form_binding",
    "converter_cleanup",
    "bpmn_di",
    "source_di_provenance",
    "semantic_id_references",
    "task_definition_types",
)
FORM_CHECKS = (
    "accepted_forms",
    "form_parsing",
    "form_schema",
    "form_js",
    "form_definition",
    "form_deployment",
    "form_references",
    "form_binding",
)
FORM_INVENTORY_KINDS = {"generated", "referenced", "form-free-owner"}
MODEL_CHECKS_REQUIRING_PASS = {
    "converted_copy",
    "xml_parse",
    "lint",
    "source_preservation",
    "resource_packaging",
    "findings_verdicts",
    "generated_forms",
    "converter_cleanup",
}
PROCESS_ASSERTIONS = (
    "user_task_type",
    "downstream_message_instance",
    "branch_selection",
    "worker_input_output",
    "incident_behavior",
    "form_resolution",
)

REPORT_START = "<!-- migration-validation-gate:start -->"
REPORT_END = "<!-- migration-validation-gate:end -->"
DEFAULT_SUMMARY_PATH = (
    ".camunda-migration/validation/validation-summary.json"
)
DEFAULT_EVIDENCE_PATH = (
    ".camunda-migration/validation/validation-evidence.json"
)
DEFAULT_INVENTORY_PATH = (
    ".camunda-migration/validation/step2-inventory.json"
)
DEFAULT_REPORT_PATH = "MIGRATION_REPORT.md"
EVIDENCE_LOG_DIRECTORY = ".camunda-migration/validation/logs"
MANUAL_CHECKS = {
    ("module", "migration_todos"),
    ("module", "business_keys"),
    ("module", "eventual_consistency"),
    ("module", "pagination"),
    ("module", "worker_adapters"),
    ("module", "deployment_resources"),
    ("model", "findings_verdicts"),
    ("model", "generated_forms"),
    ("model", "form_references"),
    ("model", "form_binding"),
    ("model", "semantic_id_references"),
    ("model", "task_definition_types"),
}


def is_nonempty_string(value):
    return isinstance(value, str) and bool(value.strip())


def add_expected(
    expected,
    key,
    description,
    allow_not_applicable=True,
    require_pass=False,
    not_applicable_only=False,
    require_nonpass=False,
    safe_environment=None,
):
    if key in expected:
        return False
    expected[key] = {
        "description": description,
        "allow_not_applicable": allow_not_applicable,
        "require_pass": require_pass,
        "not_applicable_only": not_applicable_only,
        "require_nonpass": require_nonpass,
        "safe_environment": safe_environment,
    }
    return True


def reject_extra_fields(value, allowed, location, error):
    for field in sorted(set(value) - set(allowed)):
        error("{} has unknown field {}.".format(location, field))


def check_key(check):
    target_type = check.get("target_type")
    target = check.get("target")
    kind = check.get("kind")
    scenario = check.get("scenario")
    if (
        not isinstance(target_type, str)
        or target_type not in {"project", "module", "model", "process", "timer"}
        or not is_nonempty_string(target)
        or not is_nonempty_string(kind)
        or (scenario is not None and not isinstance(scenario, str))
    ):
        return None
    return (
        target_type,
        target,
        kind,
        scenario,
    )


def check_was_executed(check):
    if not isinstance(check, dict):
        return False
    result = check.get("result")
    if not isinstance(result, str):
        return False
    return result in {"passed", "failed"} or (
        result in {"blocked", "unknown"} and check.get("exit_code") is not None
    )


def validate_project_path(value, project_root, label, expected_type, error):
    if not is_nonempty_string(value):
        error("{} must be a non-empty project-relative path.".format(label))
        return
    normalized = value.replace("\\", "/")
    if (
        normalized.startswith("/")
        or (len(normalized) > 1 and normalized[1] == ":")
        or ".." in normalized.split("/")
    ):
        error("{} must stay inside the project root.".format(label))
        return
    root = project_root.resolve()
    resolved = (root / Path(normalized)).resolve()
    try:
        resolved.relative_to(root)
    except ValueError:
        error("{} resolves outside the project root.".format(label))
        return
    if expected_type == "directory" and not resolved.is_dir():
        error("{} must point to an existing directory.".format(label))
    if expected_type == "file" and not resolved.is_file():
        error("{} must point to an existing file.".format(label))


def load_step2_inventory(project_root, error):
    inventory_path = project_root / DEFAULT_INVENTORY_PATH
    resolved_inventory_path = inventory_path.resolve()
    try:
        resolved_inventory_path.relative_to(project_root)
    except ValueError:
        error("The Step 2 inventory path resolves outside the project root.")
        return None
    if not resolved_inventory_path.is_file():
        error(
            "The Step 2 inventory file is missing or is not a file at {}."
            .format(DEFAULT_INVENTORY_PATH)
        )
        return None
    try:
        inventory = json.loads(
            resolved_inventory_path.read_text(encoding="utf-8")
        )
    except (OSError, UnicodeError, json.JSONDecodeError) as exception:
        error(
            "The Step 2 inventory file could not be read: {}.".format(
                exception
            )
        )
        return None
    if not isinstance(inventory, dict):
        error("The Step 2 inventory must contain a JSON object.")
        return None

    reject_extra_fields(
        inventory,
        {"schema_version", "modules", "models"},
        "The Step 2 inventory",
        error,
    )
    schema_version = inventory.get("schema_version")
    if (
        not isinstance(schema_version, int)
        or isinstance(schema_version, bool)
        or schema_version != 1
    ):
        error("The Step 2 inventory schema_version must be 1.")

    inventory_paths = {}
    for field, expected_type in (("modules", "directory"), ("models", "file")):
        paths = inventory.get(field)
        if not isinstance(paths, list):
            error("The Step 2 inventory {} field must be an array.".format(field))
            inventory_paths[field] = set()
            continue
        normalized_paths = set()
        for index, path in enumerate(paths):
            location = "Step 2 inventory {}[{}]".format(field, index)
            if not is_nonempty_string(path):
                error("{} must be a non-empty project-relative path.".format(location))
                continue
            validate_project_path(path, project_root, location, expected_type, error)
            normalized_path = path.replace("\\", "/")
            if normalized_path in normalized_paths:
                error("{} duplicates a path.".format(location))
            normalized_paths.add(normalized_path)
        inventory_paths[field] = normalized_paths

    if not inventory_paths.get("modules") and not inventory_paths.get("models"):
        error("The Step 2 inventory must list at least one module or model.")
    return inventory_paths


def label_for_key(key):
    target_type, target, kind, scenario = key
    label = "{} {} / {}".format(target_type, target, kind)
    if scenario is not None:
        label += " ({})".format(scenario)
    return label


def validate_manifest(data, project_root, excluded_evidence_paths=None):
    errors = []
    expected = {}
    docker_probe_needed = False
    docker_test_keys = set()
    process_start_checks = {}
    timer_order_requirements = []

    def error(message):
        errors.append(message)

    project_root = project_root.resolve()
    excluded_paths = {
        (project_root / DEFAULT_EVIDENCE_PATH).resolve(),
        (project_root / DEFAULT_INVENTORY_PATH).resolve(),
        (project_root / DEFAULT_SUMMARY_PATH).resolve(),
        (project_root / DEFAULT_REPORT_PATH).resolve(),
    }
    for output_path in excluded_evidence_paths or ():
        output_path = Path(output_path)
        if not output_path.is_absolute():
            output_path = project_root / output_path
        excluded_paths.add(output_path.resolve())

    if not isinstance(data, dict):
        return {
            "readiness": "not_ready",
            "counts": {
                "required": 0,
                "passed": 0,
                "failed": 0,
                "blocked": 0,
                "unknown": 0,
                "not_run": 0,
                "not_applicable": 0,
                "missing": 0,
                "invalid": 1,
            },
            "blockers": ["The evidence manifest must contain a JSON object."],
        }

    step2_inventory = load_step2_inventory(project_root, error)
    reject_extra_fields(
        data,
        {"schema_version", "mode", "modules", "models", "checks"},
        "The evidence manifest",
        error,
    )
    schema_version = data.get("schema_version")
    if (
        not isinstance(schema_version, int)
        or isinstance(schema_version, bool)
        or schema_version != SCHEMA_VERSION
    ):
        error(
            "The evidence manifest schema_version must be {}.".format(
                SCHEMA_VERSION
            )
        )
    if data.get("mode") != "migration":
        error("The evidence gate only accepts a full migration manifest.")

    modules = data.get("modules")
    models = data.get("models")
    checks = data.get("checks")
    if not isinstance(modules, list):
        error("The evidence manifest modules field must be an array.")
        modules = []
    if not isinstance(models, list):
        error("The evidence manifest models field must be an array.")
        models = []
    if not isinstance(checks, list):
        error("The evidence manifest checks field must be an array.")
        checks = []
    if not modules and not models:
        error("The evidence manifest must list at least one module or model.")

    module_paths = set()
    for index, module in enumerate(modules):
        location = "modules[{}]".format(index)
        if not isinstance(module, dict):
            error("{} must be an object.".format(location))
            continue
        reject_extra_fields(
            module,
            {"path", "runtime_mode", "test_suites"},
            location,
            error,
        )
        path = module.get("path")
        runtime_mode = module.get("runtime_mode")
        test_suites = module.get("test_suites")
        if not is_nonempty_string(path):
            error("{} must have a non-empty project-relative path.".format(location))
            continue
        validate_project_path(
            path,
            project_root,
            "{} path".format(location),
            "directory",
            error,
        )
        normalized_path = path.replace("\\", "/")
        if normalized_path in module_paths:
            error("The module path {} appears more than once.".format(path))
        module_paths.add(normalized_path)
        if not isinstance(runtime_mode, str) or runtime_mode not in {
            "spring-boot",
            "external-launcher",
            "none",
        }:
            error(
                "{} runtime_mode must be spring-boot, external-launcher, or none."
                .format(location)
            )
            continue
        if not isinstance(test_suites, list):
            error("{} test_suites must be an array.".format(location))
            test_suites = []

        for kind in MODULE_CHECKS:
            key = ("module", path, kind, None)
            required_runtime_mode = {
                "spring_boot_run": "spring-boot",
                "executable_jar": "spring-boot",
                "external_launcher": "external-launcher",
            }.get(kind)
            not_applicable_only = (
                required_runtime_mode is not None
                and runtime_mode != required_runtime_mode
            )
            must_run = (
                required_runtime_mode is not None
                and runtime_mode == required_runtime_mode
            )
            require_pass = kind in MODULE_CHECKS_REQUIRING_PASS or must_run
            safe_environment = (
                SAFE_RUNTIME_ENVIRONMENTS
                if must_run
                else None
            )
            add_expected(
                expected,
                key,
                label_for_key(key),
                allow_not_applicable=not require_pass and not not_applicable_only,
                require_pass=require_pass,
                not_applicable_only=not_applicable_only,
                safe_environment=safe_environment,
            )

        suite_names = set()
        if not test_suites:
            key = ("module", path, "tests", "no-tests")
            add_expected(
                expected,
                key,
                label_for_key(key),
                allow_not_applicable=False,
                require_pass=False,
                require_nonpass=True,
            )
        for suite_index, suite in enumerate(test_suites):
            suite_location = "{} test_suites[{}]".format(location, suite_index)
            if not isinstance(suite, dict):
                error("{} must be an object.".format(suite_location))
                continue
            reject_extra_fields(
                suite,
                {"name", "requires_docker"},
                suite_location,
                error,
            )
            name = suite.get("name")
            requires_docker = suite.get("requires_docker")
            if not is_nonempty_string(name):
                error("{} name must be a non-empty string.".format(suite_location))
                continue
            if name in suite_names:
                error("{} has a duplicate test suite name {}.".format(location, name))
            suite_names.add(name)
            if not isinstance(requires_docker, bool):
                error(
                    "{} requires_docker must be true or false.".format(
                        suite_location
                    )
                )
            elif requires_docker:
                docker_probe_needed = True
            key = ("module", path, "tests", name)
            add_expected(
                expected,
                key,
                label_for_key(key),
                allow_not_applicable=False,
                require_pass=True,
            )
            if requires_docker is True:
                docker_test_keys.add(key)

    model_paths = set()
    model_source_paths = set()
    for index, model in enumerate(models):
        location = "models[{}]".format(index)
        if not isinstance(model, dict):
            error("{} must be an object.".format(location))
            continue
        reject_extra_fields(
            model,
            {
                "path",
                "source_path",
                "type",
                "approach",
                "deployable",
                "source_has_di",
                "form_inventory",
                "processes",
                "recurring_timer_starts",
            },
            location,
            error,
        )
        path = model.get("path")
        source_path = model.get("source_path")
        model_type = model.get("type")
        approach = model.get("approach")
        deployable = model.get("deployable")
        source_has_di = model.get("source_has_di")
        processes = model.get("processes")
        timer_starts = model.get("recurring_timer_starts")
        if not is_nonempty_string(path):
            error("{} must have a non-empty converted model path.".format(location))
            continue
        validate_project_path(
            path,
            project_root,
            "{} path".format(location),
            "file",
            error,
        )
        if path in model_paths:
            error("The model path {} appears more than once.".format(path))
        model_paths.add(path)
        if not is_nonempty_string(source_path):
            error("{} must have a non-empty source_path.".format(location))
        else:
            validate_project_path(
                source_path,
                project_root,
                "{} source_path".format(location),
                "file",
                error,
            )
            normalized_source_path = source_path.replace("\\", "/")
            if normalized_source_path in model_source_paths:
                error(
                    "The model source_path {} appears more than once."
                    .format(source_path)
                )
            model_source_paths.add(normalized_source_path)
            resolved_model_path = (
                project_root.resolve() / Path(path.replace("\\", "/"))
            ).resolve()
            resolved_source_path = (
                project_root.resolve() / Path(source_path.replace("\\", "/"))
            ).resolve()
            same_file = resolved_model_path == resolved_source_path
            if (
                not same_file
                and resolved_model_path.is_file()
                and resolved_source_path.is_file()
            ):
                same_file = resolved_model_path.samefile(resolved_source_path)
            if same_file:
                error(
                    "{} source and converted paths identify the same file."
                    .format(location)
                )
        if not isinstance(model_type, str) or model_type not in {"bpmn", "dmn"}:
            error("{} type must be bpmn or dmn.".format(location))
        if not isinstance(approach, str) or approach not in {"M1", "M2", "M3", "E1"}:
            error("{} approach must be M1, M2, M3, or E1.".format(location))
        if not isinstance(deployable, bool):
            error("{} deployable must be true or false.".format(location))
        if not isinstance(source_has_di, bool):
            error("{} source_has_di must be true or false.".format(location))
        if not isinstance(processes, list):
            error("{} processes must be an array.".format(location))
            processes = []
        if not isinstance(timer_starts, list):
            error("{} recurring_timer_starts must be an array.".format(location))
            timer_starts = []

        form_inventory = model.get("form_inventory")
        if not isinstance(form_inventory, list):
            error("{} form_inventory must be an array.".format(location))
            form_inventory = []
        form_inventory_ids = set()
        form_check_applicability = {
            kind: False for kind in FORM_CHECKS
        }
        for form_index, form in enumerate(form_inventory):
            form_location = "{} form_inventory[{}]".format(location, form_index)
            if not isinstance(form, dict):
                error("{} must be an object.".format(form_location))
                continue
            form_fields = {
                "id",
                "kind",
                "accepted",
                "schema_applicable",
                "form_js_applicable",
                "binding_required",
            }
            reject_extra_fields(form, form_fields, form_location, error)
            for field in sorted(form_fields - set(form)):
                error("{} is missing {}.".format(form_location, field))
            form_id = form.get("id")
            if not is_nonempty_string(form_id):
                error("{} id must be a non-empty string.".format(form_location))
            elif form_id in form_inventory_ids:
                error("{} duplicates form inventory id {}.".format(form_location, form_id))
            else:
                form_inventory_ids.add(form_id)
            form_kind = form.get("kind")
            if not isinstance(form_kind, str) or form_kind not in FORM_INVENTORY_KINDS:
                error(
                    "{} kind must be generated, referenced, or form-free-owner."
                    .format(form_location)
                )
            accepted = form.get("accepted")
            if not isinstance(accepted, bool):
                error("{} accepted must be true or false.".format(form_location))
            applicability_fields = (
                "schema_applicable",
                "form_js_applicable",
                "binding_required",
            )
            for field in applicability_fields:
                if not isinstance(form.get(field), bool):
                    error(
                        "{} {} must be true or false.".format(form_location, field)
                    )
            is_accepted = accepted is True
            schema_applicable = form.get("schema_applicable") is True
            form_js_applicable = form.get("form_js_applicable") is True
            binding_required = form.get("binding_required") is True
            if form_kind == "form-free-owner" and (
                is_accepted
                or schema_applicable
                or form_js_applicable
                or binding_required
            ):
                error(
                    "{} form-free-owner cannot have accepted form checks."
                    .format(form_location)
                )
            elif not is_accepted and (
                schema_applicable or form_js_applicable or binding_required
            ):
                error(
                    "{} cannot require form checks when accepted is false."
                    .format(form_location)
                )
            if is_accepted and form_kind in {"generated", "referenced"}:
                for kind in (
                    "accepted_forms",
                    "form_parsing",
                    "form_definition",
                    "form_deployment",
                ):
                    form_check_applicability[kind] = True
            if schema_applicable:
                form_check_applicability["form_schema"] = True
            if form_js_applicable:
                form_check_applicability["form_js"] = True
            if form_kind in {"referenced", "form-free-owner"}:
                form_check_applicability["form_references"] = True
            if binding_required:
                form_check_applicability["form_binding"] = True

        for kind in MODEL_CHECKS:
            key = ("model", path, kind, None)
            not_applicable_only = False
            require_pass = kind in MODEL_CHECKS_REQUIRING_PASS
            safe_environment = None
            if kind == "deployment":
                not_applicable_only = deployable is False
                require_pass = deployable is True
                safe_environment = SAFE_RUNTIME_ENVIRONMENTS
            elif kind == "bpmn_di":
                not_applicable_only = model_type == "dmn"
                require_pass = model_type == "bpmn"
            elif kind == "source_di_provenance":
                not_applicable_only = model_type == "dmn" or source_has_di is True
                require_pass = model_type == "bpmn" and source_has_di is False
            elif kind == "task_definition_types":
                not_applicable_only = not (
                    model_type == "bpmn" and approach == "M2"
                )
                require_pass = model_type == "bpmn" and approach == "M2"
            elif kind in FORM_CHECKS:
                require_pass = form_check_applicability[kind]
                not_applicable_only = not require_pass
            add_expected(
                expected,
                key,
                label_for_key(key),
                allow_not_applicable=not require_pass and not not_applicable_only,
                require_pass=require_pass,
                not_applicable_only=not_applicable_only,
                safe_environment=safe_environment,
            )

        process_ids = set()
        executable_process_ids = set()
        for process_index, process in enumerate(processes):
            process_location = "{} processes[{}]".format(location, process_index)
            if not isinstance(process, dict):
                error("{} must be an object.".format(process_location))
                continue
            reject_extra_fields(
                process,
                {
                    "id",
                    "executable",
                    "standalone_entry_point",
                    "direct_start_scenarios",
                    "covering_test",
                    "reason",
                    "assertion_applicability",
                },
                process_location,
                error,
            )
            for field in (
                "id",
                "executable",
                "standalone_entry_point",
                "direct_start_scenarios",
                "covering_test",
                "reason",
            ):
                if field not in process:
                    error("{} is missing {}.".format(process_location, field))
            process_id = process.get("id")
            executable = process.get("executable")
            standalone = process.get("standalone_entry_point")
            scenarios = process.get("direct_start_scenarios")
            covering_test = process.get("covering_test")
            process_reason = process.get("reason")
            if not is_nonempty_string(process_id) or "#" in process_id:
                error(
                    "{} id must be a non-empty string without #.".format(
                        process_location
                    )
                )
                continue
            if process_id in process_ids:
                error(
                    "{} has duplicate process id {}.".format(location, process_id)
                )
            process_ids.add(process_id)
            if not isinstance(executable, bool):
                error("{} executable must be true or false.".format(process_location))
                continue
            if not isinstance(standalone, bool):
                error(
                    "{} standalone_entry_point must be true or false.".format(
                        process_location
                    )
                )
                continue
            if not isinstance(scenarios, list):
                error(
                    "{} direct_start_scenarios must be an array.".format(
                        process_location
                    )
                )
                scenarios = []
            if covering_test is not None and not isinstance(covering_test, str):
                error(
                    "{} covering_test must be a string or null.".format(
                        process_location
                    )
                )
            if process_reason is not None and not isinstance(process_reason, str):
                error(
                    "{} reason must be a string or null.".format(process_location)
                )

            process_target = "{}#{}".format(path, process_id)
            process_run_keys = []
            if not executable:
                if not is_nonempty_string(process_reason):
                    error(
                        "{} needs a reason when it is not executable.".format(
                            process_location
                        )
                    )
                if standalone or scenarios or covering_test is not None:
                    error(
                        "{} cannot list start scenarios or a covering test when "
                        "it is not executable.".format(process_location)
                    )
                continue

            assertion_applicability = process.get("assertion_applicability")
            if "assertion_applicability" not in process:
                error(
                    "{} needs assertion_applicability for every behavior check."
                    .format(process_location)
                )
            if not isinstance(assertion_applicability, dict):
                error(
                    "{} assertion_applicability must be an object.".format(
                        process_location
                    )
                )
                assertion_applicability = {}
            else:
                reject_extra_fields(
                    assertion_applicability,
                    set(PROCESS_ASSERTIONS),
                    "{} assertion_applicability".format(process_location),
                    error,
                )

            executable_process_ids.add(process_id)
            if standalone:
                if not scenarios or "normal" not in scenarios:
                    error(
                        "{} needs a normal direct-start scenario.".format(
                            process_location
                        )
                    )
                if covering_test is not None:
                    error(
                        "{} must not set covering_test for a standalone process."
                        .format(process_location)
                    )
                scenario_names = set()
                for scenario in scenarios:
                    if not is_nonempty_string(scenario):
                        error(
                            "{} has an empty direct-start scenario.".format(
                                process_location
                            )
                        )
                        continue
                    if scenario in scenario_names:
                        error(
                            "{} has a duplicate direct-start scenario {}.".format(
                                process_location, scenario
                            )
                        )
                    scenario_names.add(scenario)
                    key = ("process", process_target, "direct_start", scenario)
                    add_expected(
                        expected,
                        key,
                        label_for_key(key),
                        allow_not_applicable=False,
                        require_pass=True,
                        safe_environment=SAFE_RUNTIME_ENVIRONMENTS,
                    )
                    process_run_keys.append(key)
            else:
                if scenarios:
                    error(
                        "{} must not list direct-start scenarios when it is not "
                        "a standalone entry point.".format(process_location)
                    )
                if not is_nonempty_string(process_reason):
                    error(
                        "{} needs a reason when it is not a standalone entry point."
                        .format(process_location)
                    )
                if not is_nonempty_string(covering_test):
                    error(
                        "{} needs a covering_test when it is not a standalone "
                        "entry point.".format(process_location)
                    )
                key = ("process", process_target, "process_coverage", None)
                add_expected(
                    expected,
                    key,
                    label_for_key(key),
                    allow_not_applicable=False,
                    require_pass=True,
                    safe_environment=SAFE_RUNTIME_ENVIRONMENTS,
                )
                process_run_keys.append(key)

            for kind in PROCESS_ASSERTIONS:
                applicable = assertion_applicability.get(kind)
                if not isinstance(applicable, bool):
                    error(
                        "{} assertion_applicability.{} must be true or false."
                        .format(process_location, kind)
                    )
                    applicable = True
                key = ("process", process_target, kind, None)
                add_expected(
                    expected,
                    key,
                    label_for_key(key),
                    allow_not_applicable=not applicable,
                    require_pass=applicable,
                    not_applicable_only=not applicable,
                    safe_environment=SAFE_RUNTIME_ENVIRONMENTS,
                )
            process_start_checks[(path, process_id)] = process_run_keys

        timer_ids = set()
        for timer_index, timer in enumerate(timer_starts):
            timer_location = "{} recurring_timer_starts[{}]".format(
                location, timer_index
            )
            if not isinstance(timer, dict):
                error("{} must be an object.".format(timer_location))
                continue
            reject_extra_fields(
                timer,
                {"process_id", "id"},
                timer_location,
                error,
            )
            process_id = timer.get("process_id")
            timer_id = timer.get("id")
            if (
                not isinstance(process_id, str)
                or process_id not in executable_process_ids
            ):
                error(
                    "{} refers to an unknown or non-executable process {}.".format(
                        timer_location, process_id
                    )
                )
                continue
            if not is_nonempty_string(timer_id) or "#" in timer_id:
                error(
                    "{} id must be a non-empty string without #.".format(
                        timer_location
                    )
                )
                continue
            timer_key = (process_id, timer_id)
            if timer_key in timer_ids:
                error(
                    "{} duplicates a repeating timer start.".format(timer_location)
                )
            timer_ids.add(timer_key)
            timer_target = "{}#{}#{}".format(path, process_id, timer_id)
            key = ("timer", timer_target, "timer_preflight", None)
            add_expected(
                expected,
                key,
                label_for_key(key),
                allow_not_applicable=False,
                require_pass=True,
                safe_environment=SAFE_TIMER_ENVIRONMENTS,
            )
            timer_order_requirements.append(
                (
                    key,
                    ("model", path, "deployment", None),
                    process_start_checks.get((path, process_id), []),
                )
            )

    if step2_inventory is not None:
        omitted_modules = sorted(step2_inventory["modules"] - module_paths)
        extra_modules = sorted(module_paths - step2_inventory["modules"])
        omitted_models = sorted(
            step2_inventory["models"] - model_source_paths
        )
        extra_models = sorted(model_source_paths - step2_inventory["models"])
        if omitted_modules:
            error(
                "The evidence manifest omits Step 2 module inventory path(s): {}."
                .format(", ".join(omitted_modules))
            )
        if extra_modules:
            error(
                "The evidence manifest lists module path(s) absent from the Step 2 "
                "inventory: {}.".format(", ".join(extra_modules))
            )
        if omitted_models:
            error(
                "The evidence manifest omits Step 2 model inventory path(s): {}."
                .format(", ".join(omitted_models))
            )
        if extra_models:
            error(
                "The evidence manifest lists model source path(s) absent from the "
                "Step 2 inventory: {}.".format(", ".join(extra_models))
            )

    for check in checks:
        if not isinstance(check, dict):
            continue
        failure_class = check.get("failure_class")
        failure_detail = " ".join(
            str(check.get(field) or "")
            for field in ("reason", "blocker_reason")
        ).lower()
        if (
            check.get("target_type") == "module"
            and check.get("kind") == "tests"
            and (
                (
                    isinstance(failure_class, str)
                    and failure_class in {"docker_unavailable", "testcontainers"}
                )
                or "docker" in failure_detail
                or "testcontainers" in failure_detail
            )
        ):
            docker_probe_needed = True
            key = check_key(check)
            if key is not None:
                docker_test_keys.add(key)

    if docker_probe_needed:
        key = ("project", "docker", "docker_info", None)
        add_expected(
            expected,
            key,
            label_for_key(key),
            allow_not_applicable=False,
            require_pass=False,
        )

    counts = Counter()
    seen = {}
    seen_indices = {}
    test_evidence_paths = {}
    test_commands = {}
    for index, check in enumerate(checks):
        location = "checks[{}]".format(index)
        if not isinstance(check, dict):
            error("{} must be an object.".format(location))
            continue
        target_type = check.get("target_type")
        kind = check.get("kind")
        required_fields = {
            "target_type",
            "target",
            "kind",
            "scenario",
            "method",
            "command",
            "exit_code",
            "result",
            "evidence_path",
            "reason",
            "blocker_reason",
            "failure_class",
            "environment",
        }
        if target_type == "timer" and kind == "timer_preflight":
            required_fields.add("isolation_or_cleanup_plan")
        allowed_fields = required_fields | {"isolation_or_cleanup_plan"}
        reject_extra_fields(
            check,
            allowed_fields,
            location,
            error,
        )
        missing_fields = required_fields - set(check)
        for field in sorted(missing_fields):
            error("{} is missing {}.".format(location, field))

        key = check_key(check)
        result = check.get("result")
        if isinstance(result, str) and result in RESULTS:
            counts[result] += 1
        else:
            error("{} has an invalid result.".format(location))
        if key not in expected:
            error("{} does not match a required check.".format(location))
        elif key in seen:
            error("{} duplicates {}.".format(location, label_for_key(key)))
        else:
            seen[key] = check
            seen_indices[key] = index

        method = check.get("method")
        command = check.get("command")
        exit_code = check.get("exit_code")
        evidence_path = check.get("evidence_path")
        isolation_or_cleanup_plan = check.get("isolation_or_cleanup_plan")
        reason = check.get("reason")
        blocker_reason = check.get("blocker_reason")
        failure_class = check.get("failure_class")
        environment = check.get("environment")

        if not isinstance(method, str) or method not in {
            "command",
            "manual",
            "not_applicable",
        }:
            error("{} method must be command, manual, or not_applicable.".format(location))
        if method == "manual" and (target_type, kind) not in MANUAL_CHECKS:
            error(
                "{} cannot use manual method for {} {}.".format(
                    location, target_type, kind
                )
            )
        if not is_nonempty_string(command):
            error("{} command must be a non-empty string.".format(location))
        if exit_code is not None and (
            not isinstance(exit_code, int) or isinstance(exit_code, bool)
        ):
            error("{} exit_code must be an integer or null.".format(location))
        if target_type == "timer" and kind == "timer_preflight":
            if not is_nonempty_string(isolation_or_cleanup_plan):
                error(
                    "{} timer_preflight needs a non-empty "
                    "isolation_or_cleanup_plan."
                    .format(location)
                )
        elif isolation_or_cleanup_plan is not None:
            error(
                "{} isolation_or_cleanup_plan is only valid for timer_preflight "
                "checks."
                .format(location)
            )
        resolved_evidence_path = None
        if evidence_path is not None:
            resolved_evidence_path = validate_evidence_path(
                evidence_path,
                project_root,
                "{} evidence_path".format(location),
                error,
                excluded_paths,
            )
        if (
            target_type == "module"
            and kind == "tests"
            and resolved_evidence_path is not None
        ):
            suite_target = check.get("target")
            previous_suites = test_evidence_paths.setdefault(suite_target, [])
            duplicate_suite = next(
                (
                    previous
                    for previous in previous_suites
                    if resolved_evidence_path.samefile(previous[0])
                ),
                None,
            )
            if duplicate_suite is not None:
                error(
                    "Module {} test suites must use distinct evidence files ({} "
                    "and {}).".format(
                        suite_target,
                        duplicate_suite[1],
                        check.get("scenario"),
                    )
                )
            else:
                previous_suites.append(
                    (resolved_evidence_path, check.get("scenario"))
                )
        if (
            target_type == "module"
            and kind == "tests"
            and is_nonempty_string(command)
        ):
            command_key = (check.get("target"), command)
            if command_key in test_commands:
                error(
                    "Module {} test suites must use distinct commands ({} and {})."
                    .format(
                        check.get("target"),
                        test_commands[command_key],
                        check.get("scenario"),
                    )
                )
            else:
                test_commands[command_key] = check.get("scenario")
        if environment is not None and (
            not isinstance(environment, str) or environment not in ENVIRONMENTS
        ):
            error("{} has an invalid environment.".format(location))

        if result == "passed":
            if method == "command" and exit_code != 0:
                error("{} cannot pass without exit code 0.".format(location))
            if method == "manual" and exit_code is not None:
                error("{} manual checks must use a null exit_code.".format(location))
            if method == "not_applicable":
                error("{} cannot pass with method not_applicable.".format(location))
            if not is_nonempty_string(evidence_path):
                error("{} needs a non-empty evidence_path to pass.".format(location))
            if reason is not None or blocker_reason is not None:
                error("{} cannot set a reason when it passes.".format(location))
            if failure_class is not None:
                error("{} cannot set failure_class when it passes.".format(location))
        elif result == "failed":
            if not is_nonempty_string(reason):
                error("{} needs a reason when it fails.".format(location))
            if (
                not is_nonempty_string(failure_class)
                or failure_class not in FAILURE_CLASSES
            ):
                error("{} needs a valid failure_class when it fails.".format(location))
            if blocker_reason is not None:
                error("{} cannot set blocker_reason for a failed check.".format(location))
            if method == "command" and exit_code is None:
                error("{} needs an exit_code. Use blocked or not_run if it did not run."
                      .format(location))
            if not is_nonempty_string(evidence_path):
                error("{} needs evidence_path when it fails.".format(location))
        elif isinstance(result, str) and result in {"blocked", "unknown", "not_run"}:
            if not is_nonempty_string(blocker_reason):
                error("{} needs blocker_reason for {}.".format(location, result))
            if (
                not is_nonempty_string(failure_class)
                or failure_class not in FAILURE_CLASSES
            ):
                error("{} needs a valid failure_class for {}.".format(location, result))
            if reason is not None:
                error("{} must use blocker_reason instead of reason for {}."
                      .format(location, result))
            if result == "not_run":
                if exit_code is not None or evidence_path is not None:
                    error("{} not_run checks must have null exit_code and evidence_path."
                          .format(location))
            elif exit_code is not None and not is_nonempty_string(evidence_path):
                error("{} needs evidence_path when the command returned an exit code."
                      .format(location))
        elif result == "not_applicable":
            if method != "not_applicable":
                error("{} must use method not_applicable.".format(location))
            if exit_code is not None or evidence_path is not None:
                error("{} not_applicable checks must have null exit_code and evidence_path."
                      .format(location))
            if not is_nonempty_string(reason):
                error("{} needs a reason when it is not applicable.".format(location))
            if blocker_reason is not None or failure_class is not None:
                error("{} cannot set blocker_reason or failure_class when not applicable."
                      .format(location))

        expectation = expected.get(key)
        if expectation is not None:
            if (
                expectation["not_applicable_only"]
                and result != "not_applicable"
            ):
                error(
                    "{} must be not_applicable for this target.".format(
                        expectation["description"]
                    )
                )
            if expectation["require_pass"] and result != "passed":
                error(
                    "{} must pass for this target.".format(
                        expectation["description"]
                    )
                )
            if expectation["require_nonpass"] and result == "passed":
                error(
                    "{} cannot pass when no test suite exists.".format(
                        expectation["description"]
                    )
                )
            if (
                not expectation["allow_not_applicable"]
                and not expectation["not_applicable_only"]
                and result == "not_applicable"
            ):
                error(
                    "{} cannot be not_applicable for this target.".format(
                        expectation["description"]
                    )
                )
            command_ran = isinstance(result, str) and (
                result in {"passed", "failed"}
                or (result in {"blocked", "unknown"} and exit_code is not None)
            )
            if expectation["safe_environment"] and result != "not_applicable":
                unsafe_environment = (
                    environment == "production"
                    or (
                        command_ran
                        and (
                            not isinstance(environment, str)
                            or environment not in expectation["safe_environment"]
                        )
                    )
                )
                if unsafe_environment:
                    error(
                        "{} used an unsafe or unknown environment.".format(
                            expectation["description"]
                        )
                    )

        if (
            check.get("target_type") == "project"
            and check.get("kind") == "docker_info"
            and isinstance(command, str)
            and "docker info" not in command.lower()
        ):
            error("{} must run docker info.".format(location))

    missing_count = 0
    blockers = []
    for key, expectation in expected.items():
        check = seen.get(key)
        if check is None:
            missing_count += 1
            blockers.append(
                "Missing required evidence for {}.".format(
                    expectation["description"]
                )
            )
            continue
        result = check.get("result")
        if not isinstance(result, str) or result not in {
            "passed",
            "not_applicable",
        }:
            detail = check.get("blocker_reason") or check.get("reason")
            if is_nonempty_string(detail):
                blockers.append(
                    "{}: {} - {}".format(
                        expectation["description"], result, detail.strip()
                    )
                )
            else:
                blockers.append(
                    "{}: {}.".format(expectation["description"], result)
                )

    docker_key = ("project", "docker", "docker_info", None)
    docker_check = seen.get(docker_key)
    if docker_check is not None:
        for check in checks:
            if not isinstance(check, dict):
                continue
            failure_class = check.get("failure_class")
            docker_result = docker_check.get("result")
            conflict = (
                failure_class == "docker_unavailable"
                and (
                    not isinstance(docker_result, str)
                    or docker_result not in {"failed", "blocked"}
                )
            ) or (
                failure_class == "testcontainers"
                and docker_result != "passed"
            )
            if conflict:
                message = (
                    "The docker info probe conflicts with the test failure class."
                )
                errors.append(message)

    for test_key in docker_test_keys:
        if test_key not in seen:
            continue
        docker_position = seen_indices.get(docker_key)
        test_position = seen_indices.get(test_key)
        if (
            docker_position is not None
            and test_position is not None
            and docker_position >= test_position
        ):
            message = "The docker info probe must precede its dependent test suite."
            errors.append(message)

    for timer_key, deployment_key, process_keys in timer_order_requirements:
        timer_check = seen.get(timer_key)
        if not isinstance(timer_check, dict) or timer_check.get("result") != "passed":
            continue
        timer_position = seen_indices.get(timer_key)
        for check_key_to_order in [deployment_key] + process_keys:
            if not check_was_executed(seen.get(check_key_to_order)):
                continue
            action_position = seen_indices.get(check_key_to_order)
            if (
                timer_position is not None
                and action_position is not None
                and timer_position >= action_position
            ):
                message = (
                    "The repeating timer preflight must precede deployment "
                    "and process starts."
                )
                errors.append(message)

    for issue in errors:
        blockers.append("Invalid evidence: {}.".format(issue))

    required_count = len(expected)
    not_ready_results = counts["failed"] + counts["blocked"] + counts["unknown"] + counts["not_run"]
    readiness = (
        "ready"
        if missing_count == 0 and not errors and not_ready_results == 0
        else "not_ready"
    )
    return {
        "readiness": readiness,
        "counts": {
            "required": required_count,
            "passed": counts["passed"],
            "failed": counts["failed"],
            "blocked": counts["blocked"],
            "unknown": counts["unknown"],
            "not_run": counts["not_run"],
            "not_applicable": counts["not_applicable"],
            "missing": missing_count,
            "invalid": len(errors),
        },
        "blockers": blockers,
    }


def validate_evidence_path(value, project_root, label, error, excluded_paths):
    if not is_nonempty_string(value):
        error("{} must be a non-empty relative path.".format(label))
        return None
    normalized = value.replace("\\", "/")
    path = Path(normalized)
    if (
        path.is_absolute()
        or (len(normalized) > 1 and normalized[1] == ":")
        or ".." in path.parts
    ):
        error("{} must stay inside the project root.".format(label))
        return None
    root = project_root.resolve()
    resolved = (root / path).resolve()
    try:
        resolved.relative_to(root)
    except ValueError:
        error("{} resolves outside the project root.".format(label))
        return None
    logs_root = (root / EVIDENCE_LOG_DIRECTORY).resolve()
    try:
        resolved.relative_to(logs_root)
    except ValueError:
        error(
            "{} must be inside {}.".format(label, EVIDENCE_LOG_DIRECTORY)
        )
        return None
    if resolved in excluded_paths:
        error("{} cannot reference a generated validation file.".format(label))
        return None
    if not resolved.is_file() or resolved.stat().st_size == 0:
        error("{} must point to a non-empty evidence file.".format(label))
        return None
    if any(
        excluded_path.is_file() and resolved.samefile(excluded_path)
        for excluded_path in excluded_paths
    ):
        error("{} cannot reference a generated validation file.".format(label))
        return None
    return resolved


def markdown_text(value):
    return " ".join(str(value).split()).replace("`", "\\`")


def render_report_block(summary, summary_path):
    counts = summary["counts"]
    status = "READY" if summary["readiness"] == "ready" else "NOT READY"
    lines = [
        REPORT_START,
        "## Aggregate validation gate",
        "",
        "**Validation gate:** **{}**".format(status),
        "",
        "Machine-readable summary: `{}`.".format(markdown_text(summary_path)),
        "",
        (
            "Required checks: {required}. Passed: {passed}. Not applicable: "
            "{not_applicable}. Failed: {failed}. Blocked: {blocked}. Unknown: "
            "{unknown}. Not run: {not_run}. Missing: {missing}. Invalid: {invalid}."
        ).format(**counts),
        "",
    ]
    if summary["readiness"] == "ready":
        lines.append(
            "Every required check passed or has an explicit not-applicable reason. "
            "This gate does not replace the migration exit criteria."
        )
    else:
        lines.append(
            "Do not report the migration as ready. Resolve the blockers or record "
            "them as open follow-up work."
        )
        if summary["blockers"]:
            lines.extend(["", "Blockers:"])
            for blocker in summary["blockers"][:30]:
                lines.append("- {}".format(markdown_text(blocker)))
            remaining = len(summary["blockers"]) - 30
            if remaining > 0:
                lines.append("- {} more blocker(s) are listed in the JSON output."
                             .format(remaining))
    lines.extend(["", REPORT_END])
    return "\n".join(lines)


def write_report(report_path, summary, project_root, summary_path):
    path = Path(report_path)
    if path.is_absolute():
        resolved = path.resolve()
    else:
        resolved = (project_root / path).resolve()
    root = project_root.resolve()
    try:
        resolved.relative_to(root)
    except ValueError:
        raise ValueError("The report path must stay inside the project root.")

    if resolved.exists():
        contents = resolved.read_text(encoding="utf-8")
    else:
        contents = ""
    has_start = REPORT_START in contents
    has_end = REPORT_END in contents
    if has_start != has_end or contents.count(REPORT_START) > 1 or contents.count(REPORT_END) > 1:
        raise ValueError("The report contains an incomplete validation gate block.")

    block = render_report_block(summary, summary_path)
    if has_start:
        start = contents.index(REPORT_START)
        end = contents.index(REPORT_END, start) + len(REPORT_END)
        contents = contents[:start] + block + contents[end:]
    else:
        contents = contents.rstrip()
        if contents:
            contents += "\n\n"
        contents += block + "\n"
    resolved.write_text(contents, encoding="utf-8")


def write_summary(summary_path, summary, project_root):
    path = Path(summary_path)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError("The summary path must stay inside the project root.")
    resolved = (project_root / path).resolve()
    root = project_root.resolve()
    try:
        resolved.relative_to(root)
    except ValueError:
        raise ValueError("The summary path must stay inside the project root.")
    resolved.parent.mkdir(parents=True, exist_ok=True)
    resolved.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def make_input_error(message):
    return {
        "readiness": "not_ready",
        "counts": {
            "required": 0,
            "passed": 0,
            "failed": 0,
            "blocked": 0,
            "unknown": 0,
            "not_run": 0,
            "not_applicable": 0,
            "missing": 0,
            "invalid": 1,
        },
        "blockers": [message],
    }


def main():
    parser = argparse.ArgumentParser(
        description="Validate migration evidence and update its report gate."
    )
    parser.add_argument(
        "--project-root", default=".", help="migration project root (default: .)"
    )
    parser.add_argument(
        "--evidence",
        default=DEFAULT_EVIDENCE_PATH,
        help="evidence manifest path relative to the project root",
    )
    parser.add_argument(
        "--report",
        help="MIGRATION_REPORT.md path to update with the generated gate block",
    )
    parser.add_argument(
        "--summary",
        default=DEFAULT_SUMMARY_PATH,
        help="JSON gate summary path relative to the project root",
    )
    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()
    project_root_exists = project_root.is_dir()
    if not project_root_exists:
        summary = make_input_error("The project root does not exist.")
    else:
        try:
            evidence_path = Path(args.evidence)
            if evidence_path.is_absolute() or ".." in evidence_path.parts:
                raise ValueError("The evidence path must stay inside the project root.")
            resolved_evidence = (project_root / evidence_path).resolve()
            resolved_evidence.relative_to(project_root)
            data = json.loads(resolved_evidence.read_text(encoding="utf-8"))
            excluded_evidence_paths = {
                resolved_evidence,
                (project_root / Path(args.summary)).resolve(),
            }
            if args.report:
                excluded_evidence_paths.add(
                    (project_root / Path(args.report)).resolve()
                )
            summary = validate_manifest(
                data, project_root, excluded_evidence_paths
            )
        except (OSError, ValueError, json.JSONDecodeError) as exception:
            summary = make_input_error(
                "The evidence manifest cannot be read: {}.".format(exception)
            )

    summary_written = False
    if project_root_exists:
        try:
            write_summary(args.summary, summary, project_root)
            summary_written = True
        except (OSError, ValueError) as exception:
            summary["readiness"] = "not_ready"
            summary["counts"]["invalid"] += 1
            summary["blockers"].append(
                "The validation summary cannot be written: {}.".format(exception)
            )

    report_failed = False
    if args.report and project_root_exists:
        try:
            write_report(args.report, summary, project_root, args.summary)
        except (OSError, ValueError) as exception:
            summary["readiness"] = "not_ready"
            summary["counts"]["invalid"] += 1
            summary["blockers"].append(
                "The validation report cannot be updated: {}.".format(exception)
            )
            report_failed = True

    if report_failed and summary_written:
        try:
            write_summary(args.summary, summary, project_root)
        except (OSError, ValueError) as exception:
            summary["counts"]["invalid"] += 1
            summary["blockers"].append(
                "The not-ready summary cannot be rewritten: {}.".format(exception)
            )

    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0 if summary["readiness"] == "ready" else 1


if __name__ == "__main__":
    sys.exit(main())
