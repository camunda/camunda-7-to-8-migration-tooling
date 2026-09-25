#!/usr/bin/env python3
"""Validate migration check evidence and write the aggregate gate summary."""

import argparse
import json
import re
import shlex
import sys
import xml.etree.ElementTree as ET
import zipfile
from collections import Counter
from pathlib import Path


SCHEMA_VERSION = 1
BPMN_MODEL_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL"
BPMN_DI_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/DI"
BPMN_DEFINITIONS_TAG = "{{{}}}definitions".format(BPMN_MODEL_NAMESPACE)
BPMN_PROCESS_TAG = "{{{}}}process".format(BPMN_MODEL_NAMESPACE)
BPMN_USER_TASK_TAG = "{{{}}}userTask".format(BPMN_MODEL_NAMESPACE)
BPMN_START_EVENT_TAG = "{{{}}}startEvent".format(BPMN_MODEL_NAMESPACE)
BPMN_EXTENSION_ELEMENTS_TAG = "{{{}}}extensionElements".format(
    BPMN_MODEL_NAMESPACE
)
CAMUNDA_BPMN_NAMESPACE = "http://camunda.org/schema/1.0/bpmn"
CAMUNDA_FORM_DATA_TAG = "{{{}}}formData".format(CAMUNDA_BPMN_NAMESPACE)
CAMUNDA_FORM_PROPERTY_TAG = "{{{}}}formProperty".format(
    CAMUNDA_BPMN_NAMESPACE
)
CAMUNDA_FORM_KEY_ATTRIBUTE = "{{{}}}formKey".format(CAMUNDA_BPMN_NAMESPACE)
CAMUNDA_FORM_REF_ATTRIBUTE = "{{{}}}formRef".format(CAMUNDA_BPMN_NAMESPACE)
DMN_MODEL_NAMESPACES = {
    "http://www.omg.org/spec/DMN/20151101/dmn.xsd",
    "http://www.omg.org/spec/DMN/20180521/MODEL/",
    "https://www.omg.org/spec/DMN/20180521/MODEL/",
    "http://www.omg.org/spec/DMN/20191111/MODEL/",
    "https://www.omg.org/spec/DMN/20191111/MODEL/",
}
DMN_DEFINITIONS_TAGS = {
    "{{{}}}definitions".format(namespace)
    for namespace in DMN_MODEL_NAMESPACES
}
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
SPRING_BOOT_LAUNCH_CHECKS = ("spring_boot_run", "executable_jar")
RUNTIME_SOURCE_SUFFIXES = {".java", ".kt", ".groovy", ".scala"}
TEST_SOURCE_SUFFIXES = RUNTIME_SOURCE_SUFFIXES
SPRING_BOOT_ENTRY_POINT_PATTERN = re.compile(
    r"@(?:org\s*\.\s*springframework\s*\.\s*boot\s*\.\s*)?"
    r"SpringBootConfiguration\b"
    r"|@(?:org\s*\.\s*springframework\s*\.\s*boot\s*\.\s*"
    r"autoconfigure\s*\.\s*)?"
    r"(?:SpringBootApplication|EnableAutoConfiguration)\b"
    r"|(?:org\s*\.\s*springframework\s*\.\s*boot\s*\.\s*)?"
    r"SpringApplication\s*\.\s*run\s*\("
)
MAIN_ENTRY_POINT_PATTERN = re.compile(
    r"\b(?:static\s+void\s+main|fun\s+main|def\s+main)\s*\("
    r"|\bextends\s+(?:scala\.)?App\b|@main\s+def\b"
)
EXECUTABLE_JAR_MANIFEST_PATTERN = re.compile(
    r"(?im)^(?:main-class|start-class):\s*\S+"
)
POM_MAIN_CLASS_TAGS = {
    "mainClass",
    "main-class",
    "start-class",
    "spring-boot.run.main-class",
}
GRADLE_TEST_SUITE_PATTERNS = (
    re.compile(
        r'''\b(?:tasks\s*\.\s*)?(?:register|named)\s*'''
        r'''<\s*(?:[\w.]+\.)?Test\s*>\s*'''
        r'''\(\s*["'](?P<name>[^"']+)["']'''
    ),
    re.compile(
        r'''\b(?:tasks\s*\.\s*)?(?:register|create|named)\s*\(\s*'''
        r'''["'](?P<name>[^"']+)["']\s*,\s*'''
        r'''(?:[\w.]+\.)?Test(?:::class|\.class)?\s*\)'''
    ),
    re.compile(
        r'''\btask\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*\(\s*'''
        r'''type\s*:\s*(?:[\w.]+\.)?Test(?:\.class|::class)?\s*\)'''
    ),
    re.compile(
        r'''\bregister\s*<\s*(?:[\w.]+\.)?JvmTestSuite\s*>\s*'''
        r'''\(\s*["'](?P<name>[^"']+)["']'''
    ),
    re.compile(
        r'''\b(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s+by\s+registering\s*'''
        r'''\(\s*(?:[\w.]+\.)?JvmTestSuite(?:\.class|::class)?\s*\)'''
    ),
)
GRADLE_PLUGINS_BLOCK_PATTERN = re.compile(
    r"\bplugins\s*\{([^{}]*)\}", re.DOTALL
)
GRADLE_TEST_PLUGIN_IDS = (
    "java",
    "java-library",
    "application",
    "groovy",
    "scala",
    "org.jetbrains.kotlin.jvm",
)
GRADLE_TEST_PLUGIN_ID_PATTERN = re.compile(
    r'''\bid\s*(?:\(\s*)?["'](?:'''
    + "|".join(re.escape(plugin_id) for plugin_id in GRADLE_TEST_PLUGIN_IDS)
    + r''')["']'''
)
GRADLE_KOTLIN_JVM_PLUGIN_PATTERN = re.compile(
    r'''\bkotlin\s*\(\s*["']jvm["']\s*\)'''
)
GRADLE_BARE_TEST_PLUGIN_PATTERN = re.compile(
    r"(?m)(?:^|[;{])\s*(?:java|java-library|application|groovy|scala)"
    r"\s*(?=[;}]|$)"
)
GRADLE_APPLIED_TEST_PLUGIN_PATTERN = re.compile(
    r'''\b(?:apply\s*(?:\(\s*)?plugin\s*[:=]|plugins\s*\.\s*apply\s*\()\s*'''
    r'''["'](?:'''
    + "|".join(re.escape(plugin_id) for plugin_id in GRADLE_TEST_PLUGIN_IDS)
    + r''')["']'''
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
    ("process", "worker_input_inventory"),
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


def command_invokes_docker_info(command):
    if not isinstance(command, str):
        return False
    try:
        arguments = shlex.split(command)
    except ValueError:
        return False
    while arguments and re.fullmatch(
        r"[A-Za-z_][A-Za-z0-9_]*=.*", arguments[0]
    ):
        arguments.pop(0)
    return (
        len(arguments) >= 2
        and Path(arguments[0]).name == "docker"
        and arguments[1] == "info"
    )


def validate_project_path(value, project_root, label, expected_type, error):
    if not is_nonempty_string(value):
        error("{} must be a non-empty project-relative path.".format(label))
        return None
    normalized = value.replace("\\", "/")
    if (
        normalized.startswith("/")
        or (len(normalized) > 1 and normalized[1] == ":")
        or ".." in normalized.split("/")
    ):
        error("{} must stay inside the project root.".format(label))
        return None
    root = project_root.resolve()
    resolved = (root / Path(normalized)).resolve()
    try:
        resolved.relative_to(root)
    except ValueError:
        error("{} resolves outside the project root.".format(label))
        return None
    if expected_type == "directory" and not resolved.is_dir():
        error("{} must point to an existing directory.".format(label))
    if expected_type == "file" and not resolved.is_file():
        error("{} must point to an existing file.".format(label))
    return resolved


def canonical_project_path(value, project_root):
    root = project_root.resolve()
    resolved = (root / Path(value.replace("\\", "/"))).resolve()
    try:
        return resolved.relative_to(root).as_posix()
    except ValueError:
        return str(resolved)


def paths_identify_same_file(left, right):
    if left == right:
        return True
    return left.is_file() and right.is_file() and left.samefile(right)


def path_is_within_root(path, root):
    try:
        path.relative_to(root)
    except ValueError:
        return False
    return True


def resolve_runtime_scan_path(path, project_root, location, error):
    try:
        resolved_path = Path(path).resolve()
    except (OSError, RuntimeError) as exception:
        error(
            "{} cannot verify runtime_mode because {} cannot be resolved: "
            "{}.".format(location, path, exception)
        )
        return None
    if not path_is_within_root(resolved_path, project_root):
        error(
            "{} cannot verify runtime_mode because {} resolves outside the "
            "project root.".format(location, path)
        )
        return None
    return resolved_path


def detect_module_runtime_entry_points(module_root, project_root, location, error):
    project_root = Path(project_root).resolve()
    module_root = resolve_runtime_scan_path(
        module_root, project_root, location, error
    )
    if module_root is None:
        return []

    entry_points = []
    pom_path = module_root / "pom.xml"
    resolved_pom_path = resolve_runtime_scan_path(
        pom_path, project_root, location, error
    )
    if resolved_pom_path is not None and resolved_pom_path.is_file():
        try:
            pom_root = ET.parse(str(resolved_pom_path)).getroot()
        except (OSError, ET.ParseError, UnicodeError) as exception:
            error(
                "{} cannot verify runtime_mode because pom.xml cannot be read: "
                "{}.".format(location, exception)
            )
        else:
            if any(
                isinstance(element.tag, str)
                and element.tag.rsplit("}", 1)[-1] in POM_MAIN_CLASS_TAGS
                and is_nonempty_string(element.text)
                for element in pom_root.iter()
            ):
                entry_points.append("pom.xml configures a main class")

    source_root = module_root / "src" / "main"
    resolved_source_root = resolve_runtime_scan_path(
        source_root, project_root, location, error
    )
    if resolved_source_root is not None and resolved_source_root.is_dir():
        for source_path in sorted(source_root.rglob("*")):
            if source_path.suffix.lower() not in RUNTIME_SOURCE_SUFFIXES:
                continue
            resolved_source_path = resolve_runtime_scan_path(
                source_path, project_root, location, error
            )
            if (
                resolved_source_path is None
                or not resolved_source_path.is_file()
            ):
                continue
            try:
                source_text = resolved_source_path.read_text(encoding="utf-8")
            except (OSError, UnicodeError) as exception:
                error(
                    "{} cannot verify runtime_mode because {} cannot be read: "
                    "{}.".format(
                        location,
                        source_path.relative_to(module_root).as_posix(),
                        exception,
                    )
                )
                continue
            source_relative_path = source_path.relative_to(module_root).as_posix()
            if SPRING_BOOT_ENTRY_POINT_PATTERN.search(source_text):
                entry_points.append(
                    "{} contains Spring Boot startup code.".format(
                        source_relative_path
                    )
                )
            elif MAIN_ENTRY_POINT_PATTERN.search(source_text):
                entry_points.append(
                    "{} declares a main entry point.".format(
                        source_relative_path
                    )
                )

    target_directory = module_root / "target"
    resolved_target_directory = resolve_runtime_scan_path(
        target_directory, project_root, location, error
    )
    if (
        resolved_target_directory is not None
        and resolved_target_directory.is_dir()
    ):
        for jar_path in sorted(target_directory.glob("*.jar")):
            resolved_jar_path = resolve_runtime_scan_path(
                jar_path, project_root, location, error
            )
            if (
                resolved_jar_path is None
                or not resolved_jar_path.is_file()
                or not zipfile.is_zipfile(resolved_jar_path)
            ):
                continue
            try:
                with zipfile.ZipFile(resolved_jar_path) as archive:
                    manifest = archive.read("META-INF/MANIFEST.MF").decode(
                        "utf-8", errors="replace"
                    )
            except KeyError:
                continue
            except (OSError, RuntimeError, zipfile.BadZipFile) as exception:
                error(
                    "{} cannot verify runtime_mode because {} cannot be read: "
                    "{}.".format(
                        location,
                        jar_path.relative_to(module_root).as_posix(),
                        exception,
                    )
                )
                continue
            if EXECUTABLE_JAR_MANIFEST_PATTERN.search(manifest):
                entry_points.append(
                    "{} declares an executable JAR entry point.".format(
                        jar_path.relative_to(module_root).as_posix()
                    )
                )

    return entry_points


def xml_local_name(tag):
    return tag.rsplit("}", 1)[-1] if isinstance(tag, str) else ""


def xml_children(element, name):
    return [
        child
        for child in element
        if xml_local_name(child.tag) == name
    ]


def xml_child_text(element, name):
    for child in xml_children(element, name):
        return (child.text or "").strip()
    return ""


def project_ancestor_directories(module_root, project_root):
    module_root = Path(module_root).resolve()
    project_root = Path(project_root).resolve()
    if module_root != project_root and project_root not in module_root.parents:
        return []

    directories = []
    current = module_root
    while True:
        directories.append(current)
        if current == project_root:
            break
        current = current.parent
    return directories


def module_has_test_sources(module_root):
    test_source_root = Path(module_root) / "src" / "test"
    if not test_source_root.is_dir():
        return False
    return any(
        source_file.is_file()
        and source_file.suffix.lower() in TEST_SOURCE_SUFFIXES
        for source_file in test_source_root.rglob("*")
    )


def maven_test_suites(pom_roots, module_root):
    active_plugins = []
    managed_plugins = []
    for pom_root in pom_roots:
        build_sections = list(xml_children(pom_root, "build"))
        for profiles in xml_children(pom_root, "profiles"):
            for profile in xml_children(profiles, "profile"):
                build_sections.extend(xml_children(profile, "build"))

        for build in build_sections:
            for plugins in xml_children(build, "plugins"):
                active_plugins.extend(xml_children(plugins, "plugin"))
            for plugin_management in xml_children(build, "pluginManagement"):
                for plugins in xml_children(plugin_management, "plugins"):
                    managed_plugins.extend(xml_children(plugins, "plugin"))

    suite_names = set()
    if pom_roots and (
        any(
            xml_child_text(plugin, "artifactId") == "maven-surefire-plugin"
            for plugin in active_plugins
        )
        or module_has_test_sources(module_root)
    ):
        suite_names.add("unit")

    if not any(
        xml_child_text(plugin, "artifactId") == "maven-failsafe-plugin"
        for plugin in active_plugins
    ):
        return suite_names

    for plugin in active_plugins + managed_plugins:
        if xml_child_text(plugin, "artifactId") != "maven-failsafe-plugin":
            continue
        for executions in xml_children(plugin, "executions"):
            for execution in xml_children(executions, "execution"):
                goals = {
                    (goal.text or "").strip()
                    for goal_container in xml_children(execution, "goals")
                    for goal in xml_children(goal_container, "goal")
                }
                if goals.intersection({"integration-test", "verify"}):
                    suite_names.add(
                        xml_child_text(execution, "id") or "integration"
                    )
    return suite_names


def gradle_test_suites(script_text):
    return {
        match.group("name")
        for pattern in GRADLE_TEST_SUITE_PATTERNS
        for match in pattern.finditer(script_text)
    }


def gradle_declares_default_test_suite(script_text):
    for match in GRADLE_PLUGINS_BLOCK_PATTERN.finditer(script_text):
        plugins = match.group(1)
        if (
            GRADLE_TEST_PLUGIN_ID_PATTERN.search(plugins)
            or GRADLE_KOTLIN_JVM_PLUGIN_PATTERN.search(plugins)
            or GRADLE_BARE_TEST_PLUGIN_PATTERN.search(plugins)
        ):
            return True
    return bool(GRADLE_APPLIED_TEST_PLUGIN_PATTERN.search(script_text))


def detect_module_test_suites(module_root, project_root, location, error):
    suite_names = set()
    pom_roots = []
    gradle_build_found = False
    gradle_default_test_found = False
    project_root = Path(project_root).resolve()
    for directory in project_ancestor_directories(module_root, project_root):
        pom_path = directory / "pom.xml"
        if pom_path.is_file():
            resolved_pom_path = pom_path.resolve()
            if not path_is_within_root(resolved_pom_path, project_root):
                error(
                    "{} cannot verify test suites because {} resolves outside "
                    "the project root.".format(location, pom_path)
                )
                continue
            try:
                pom_root = ET.parse(str(resolved_pom_path)).getroot()
            except (OSError, ET.ParseError, UnicodeError) as exception:
                error(
                    "{} cannot verify test suites because {} cannot be read: "
                    "{}.".format(location, resolved_pom_path, exception)
                )
            else:
                pom_roots.append(pom_root)

        for build_file in ("build.gradle", "build.gradle.kts"):
            build_path = directory / build_file
            if not build_path.is_file():
                continue
            gradle_build_found = True
            resolved_build_path = build_path.resolve()
            if not path_is_within_root(resolved_build_path, project_root):
                error(
                    "{} cannot verify test suites because {} resolves outside "
                    "the project root.".format(location, build_path)
                )
                continue
            try:
                script_text = resolved_build_path.read_text(encoding="utf-8")
            except (OSError, UnicodeError) as exception:
                error(
                    "{} cannot verify test suites because {} cannot be read: "
                    "{}.".format(location, resolved_build_path, exception)
                )
                continue
            suite_names.update(gradle_test_suites(script_text))
            gradle_default_test_found = (
                gradle_default_test_found
                or gradle_declares_default_test_suite(script_text)
            )
    if gradle_build_found and (
        gradle_default_test_found or module_has_test_sources(module_root)
    ):
        suite_names.add("test")
    suite_names.update(maven_test_suites(pom_roots, module_root))
    return suite_names


def read_source_form_inventory(root, location, error):
    if root is None:
        return Counter()
    if root.tag != BPMN_DEFINITIONS_TAG:
        error(
            "{} source model must have a BPMN definitions root to verify its "
            "form inventory.".format(location)
        )
        return Counter()

    inventory = Counter()
    identities = {kind: set() for kind in FORM_INVENTORY_KINDS}
    reference_ids = set()
    event_definition_suffix = "EventDefinition"
    for process in root.findall(BPMN_PROCESS_TAG):
        process_start_events = set(process.findall(BPMN_START_EVENT_TAG))
        for owner in process.iter():
            if owner.tag not in {BPMN_USER_TASK_TAG, BPMN_START_EVENT_TAG}:
                continue
            generated_form = False
            references = set()
            owner_id = owner.get("id") or "unknown"
            for attribute in (
                CAMUNDA_FORM_KEY_ATTRIBUTE,
                CAMUNDA_FORM_REF_ATTRIBUTE,
            ):
                if attribute in owner.attrib:
                    references.add(
                        owner.get(attribute)
                        or "{}:{}".format(xml_local_name(attribute), owner_id)
                    )

            extension_elements = owner.find(BPMN_EXTENSION_ELEMENTS_TAG)
            if extension_elements is not None:
                for extension in extension_elements:
                    if extension.tag in {
                        CAMUNDA_FORM_DATA_TAG,
                        CAMUNDA_FORM_PROPERTY_TAG,
                    }:
                        generated_form = True
                    elif xml_local_name(extension.tag) == "formDefinition":
                        form_references = {
                            extension.get("formId"),
                            extension.get("externalReference"),
                        }
                        form_references.discard(None)
                        if form_references:
                            references.update(form_references)
                        else:
                            references.add("formDefinition:{}".format(owner_id))

            if generated_form:
                inventory["generated"] += 1
                if not is_nonempty_string(owner.get("id")):
                    error(
                        "{} source generated-form owner must have a non-empty "
                        "id.".format(location)
                    )
                identities["generated"].add(owner_id)
            reference_ids.update(references)

            process_level_none_start = (
                owner.tag == BPMN_START_EVENT_TAG
                and owner in process_start_events
                and not any(
                    child.tag.startswith("{{{}}}".format(BPMN_MODEL_NAMESPACE))
                    and xml_local_name(child.tag).endswith(
                        event_definition_suffix
                    )
                    for child in owner
                    if isinstance(child.tag, str)
                )
            )
            if (
                not generated_form
                and not references
                and (
                    owner.tag == BPMN_USER_TASK_TAG
                    or process_level_none_start
                )
            ):
                inventory["form-free-owner"] += 1
                if not is_nonempty_string(owner.get("id")):
                    error(
                        "{} source form-free owner must have a non-empty id."
                        .format(location)
                    )
                identities["form-free-owner"].add(owner_id)

    inventory["referenced"] = len(reference_ids)
    identities["referenced"].update(reference_ids)
    return inventory, identities


def read_converted_model(path, location, error):
    return read_model(path, location, "converted model", error)


def read_source_model(path, location, error):
    return read_model(path, location, "source model", error)


def read_model(path, location, model_label, error):
    try:
        root = ET.parse(str(path)).getroot()
    except (OSError, ET.ParseError, UnicodeError) as exception:
        error(
            "{} {} cannot be parsed to identify its type: {}."
            .format(location, model_label, exception)
        )
        return None, None

    if root.tag == BPMN_DEFINITIONS_TAG:
        return "bpmn", root
    if root.tag in DMN_DEFINITIONS_TAGS:
        return "dmn", root
    error(
        "{} {} must have a BPMN or DMN definitions root."
        .format(location, model_label)
    )
    return None, None


def source_bpmn_has_di(root):
    return any(
        isinstance(element.tag, str)
        and element.tag.startswith("{{{}}}".format(BPMN_DI_NAMESPACE))
        for element in root.iter()
    )


def read_bpmn_inventory(root, location, error):
    if root.tag != BPMN_DEFINITIONS_TAG:
        error(
            "{} converted BPMN must have a BPMN definitions root."
            .format(location)
        )
        return None

    process_tag = "{{{}}}process".format(BPMN_MODEL_NAMESPACE)
    start_event_tag = "{{{}}}startEvent".format(BPMN_MODEL_NAMESPACE)
    timer_definition_tag = (
        "{{{}}}timerEventDefinition".format(BPMN_MODEL_NAMESPACE)
    )
    time_cycle_tag = "{{{}}}timeCycle".format(BPMN_MODEL_NAMESPACE)
    processes = {}
    repeating_timer_starts = set()
    for index, process in enumerate(root.findall(process_tag)):
        process_location = "{} converted BPMN process[{}]".format(
            location, index
        )
        process_id = process.get("id")
        if not is_nonempty_string(process_id):
            error("{} must have a non-empty id.".format(process_location))
            continue
        if process_id in processes:
            error(
                "{} has duplicate process id {}.".format(location, process_id)
            )
            continue

        executable_value = process.get("isExecutable", "false").strip().lower()
        if executable_value in {"true", "1"}:
            executable = True
        elif executable_value in {"false", "0"}:
            executable = False
        else:
            error(
                "{} isExecutable must be a valid XML boolean.".format(
                    process_location
                )
            )
            continue
        processes[process_id] = executable
        for start_index, start_event in enumerate(
            process.iter(start_event_tag)
        ):
            if not any(
                timer_definition.find(time_cycle_tag) is not None
                for timer_definition in start_event.findall(
                    timer_definition_tag
                )
            ):
                continue
            timer_id = start_event.get("id")
            timer_location = "{} repeating timer start[{}]".format(
                process_location, start_index
            )
            if not is_nonempty_string(timer_id):
                error("{} must have a non-empty id.".format(timer_location))
                continue
            timer_key = (process_id, timer_id)
            if timer_key in repeating_timer_starts:
                error(
                    "{} has duplicate repeating timer start id {}."
                    .format(process_location, timer_id)
                )
                continue
            repeating_timer_starts.add(timer_key)
    return processes, repeating_timer_starts


def resolve_contained_path(value, project_root, label):
    path = Path(value)
    resolved = (
        path.resolve()
        if path.is_absolute()
        else (project_root / path).resolve()
    )
    try:
        resolved.relative_to(project_root)
    except ValueError:
        raise ValueError("{} must stay inside the project root.".format(label))
    return resolved


def resolve_evidence_path(value, project_root):
    if not is_nonempty_string(value):
        raise ValueError("The evidence path must be a non-empty relative path.")
    path = Path(value)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError("The evidence path must stay inside the project root.")
    return resolve_contained_path(path, project_root, "The evidence path")


def resolve_summary_path(value, project_root):
    if not is_nonempty_string(value):
        raise ValueError("The summary path must be a non-empty relative path.")
    path = Path(value)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError("The summary path must stay inside the project root.")
    return resolve_contained_path(path, project_root, "The summary path")


def resolve_report_path(value, project_root):
    if not is_nonempty_string(value):
        raise ValueError("The report path must be a non-empty path.")
    return resolve_contained_path(value, project_root, "The report path")


def validate_cli_paths(evidence_path, summary_path, report_path, project_root):
    evidence_candidate = Path(evidence_path)
    if not evidence_candidate.is_absolute():
        evidence_candidate = project_root / evidence_candidate
    evidence_candidate = evidence_candidate.resolve()
    resolved_summary = resolve_summary_path(summary_path, project_root)
    resolved_report = (
        resolve_report_path(report_path, project_root)
        if report_path
        else None
    )
    destinations = [
        ("evidence", evidence_candidate),
        ("summary", resolved_summary),
    ]
    if resolved_report is not None:
        destinations.append(("report", resolved_report))

    for index, (left_name, left_path) in enumerate(destinations):
        for right_name, right_path in destinations[index + 1 :]:
            if paths_identify_same_file(left_path, right_path):
                raise ValueError(
                    "The {} and {} paths must not identify the same file."
                    .format(left_name, right_name)
                )

    output_paths = [("summary", resolved_summary)]
    if resolved_report is not None:
        output_paths.append(("report", resolved_report))
    reserved_paths = [
        (
            "Step 2 inventory",
            (project_root / DEFAULT_INVENTORY_PATH).resolve(),
        ),
        (
            "default evidence",
            (project_root / DEFAULT_EVIDENCE_PATH).resolve(),
        ),
        (
            "default summary",
            (project_root / DEFAULT_SUMMARY_PATH).resolve(),
        ),
        (
            "default report",
            (project_root / DEFAULT_REPORT_PATH).resolve(),
        ),
    ]
    matching_default_outputs = {
        ("summary", "default summary"),
        ("report", "default report"),
    }
    for output_name, output_path in output_paths:
        for reserved_name, reserved_path in reserved_paths:
            if (
                output_path == reserved_path
                and (output_name, reserved_name) in matching_default_outputs
            ):
                # Only the matching output may use its reserved default path.
                continue
            if paths_identify_same_file(output_path, reserved_path):
                raise ValueError(
                    "The {} and {} paths must not identify the same file."
                    .format(output_name, reserved_name)
                )
    return resolved_summary, resolved_report


def validate_cli_outputs_against_evidence(data, output_paths, project_root):
    if not isinstance(data, dict) or not isinstance(data.get("checks"), list):
        return

    evidence_paths = []
    for check in data["checks"]:
        if not isinstance(check, dict):
            continue
        evidence_path = check.get("evidence_path")
        if not is_nonempty_string(evidence_path):
            continue
        candidate = Path(evidence_path.replace("\\", "/"))
        if not candidate.is_absolute():
            candidate = project_root / candidate
        try:
            evidence_paths.append(candidate.resolve())
        except (OSError, RuntimeError):
            continue

    for output_name, output_path in output_paths:
        for evidence_path in evidence_paths:
            if paths_identify_same_file(output_path, evidence_path):
                raise ValueError(
                    "The {} path must not identify an evidence file".format(
                        output_name
                    )
                )


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
            if field == "modules":
                normalized_path = canonical_project_path(path, project_root)
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
    spring_boot_launch_requirements = []

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
        resolved_module_path = validate_project_path(
            path,
            project_root,
            "{} path".format(location),
            "directory",
            error,
        )
        if resolved_module_path is None:
            continue
        canonical_path = canonical_project_path(path, project_root)
        if canonical_path in module_paths:
            error(
                "The module path {} appears more than once after resolving "
                "the path.".format(path)
            )
        module_paths.add(canonical_path)
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
        configured_test_suites = detect_module_test_suites(
            resolved_module_path,
            project_root,
            location,
            error,
        )
        declared_test_suites = {
            suite.get("name")
            for suite in test_suites
            if isinstance(suite, dict) and is_nonempty_string(suite.get("name"))
        }
        for suite_name in sorted(configured_test_suites - declared_test_suites):
            error(
                "{} test_suites omits build-configured suite {}.".format(
                    location, suite_name
                )
            )
        for suite_name in sorted(declared_test_suites - configured_test_suites):
            error(
                "{} test_suites declares suite {} that is not configured or "
                "discoverable.".format(location, suite_name)
            )

        if runtime_mode == "none" and not Path(canonical_path).is_absolute():
            entry_points = detect_module_runtime_entry_points(
                resolved_module_path,
                project_root,
                location,
                error,
            )
            if entry_points:
                error(
                    "{} runtime_mode none conflicts with detected runtime "
                    "entry point(s): {}.".format(
                        location,
                        "; ".join(entry_points),
                    )
                )

        if runtime_mode == "spring-boot":
            spring_boot_launch_requirements.append(
                (
                    path,
                    [
                        ("module", path, kind, None)
                        for kind in SPRING_BOOT_LAUNCH_CHECKS
                    ],
                )
            )

        for kind in MODULE_CHECKS:
            key = ("module", path, kind, None)
            spring_boot_launch_alternative = (
                runtime_mode == "spring-boot"
                and kind in SPRING_BOOT_LAUNCH_CHECKS
            )
            required_runtime_mode = {
                "spring_boot_run": "spring-boot",
                "executable_jar": "spring-boot",
                "external_launcher": "external-launcher",
            }.get(kind)
            not_applicable_only = (
                required_runtime_mode is not None
                and runtime_mode != required_runtime_mode
                and not spring_boot_launch_alternative
            )
            must_run = (
                required_runtime_mode is not None
                and runtime_mode == required_runtime_mode
                and not spring_boot_launch_alternative
            )
            require_pass = kind in MODULE_CHECKS_REQUIRING_PASS or must_run
            safe_environment = (
                SAFE_RUNTIME_ENVIRONMENTS
                if must_run or spring_boot_launch_alternative
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

    model_paths = []
    model_source_paths = set()
    model_source_file_paths = []
    converted_model_file_paths = []
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
        resolved_model_path = validate_project_path(
            path,
            project_root,
            "{} path".format(location),
            "file",
            error,
        )
        if resolved_model_path is not None:
            if any(
                paths_identify_same_file(resolved_model_path, previous_path)
                for previous_path in model_paths
            ):
                error(
                    "The converted model path {} appears more than once."
                    .format(path)
                )
            model_paths.append(resolved_model_path)
            converted_model_file_paths.append((resolved_model_path, location))
        resolved_source_path = None
        if not is_nonempty_string(source_path):
            error("{} must have a non-empty source_path.".format(location))
        else:
            resolved_source_path = validate_project_path(
                source_path,
                project_root,
                "{} source_path".format(location),
                "file",
                error,
            )
            normalized_source_path = source_path.replace("\\", "/")
            model_source_paths.add(normalized_source_path)
            if resolved_source_path is not None:
                previous_source = next(
                    (
                        previous_location
                        for previous_path, previous_location
                        in model_source_file_paths
                        if paths_identify_same_file(
                            resolved_source_path, previous_path
                        )
                    ),
                    None,
                )
                if previous_source is not None:
                    error(
                        "{} source_path identifies the same file as {}."
                        .format(location, previous_source)
                    )
                model_source_file_paths.append(
                    (resolved_source_path, location)
                )
                if (
                    resolved_model_path is not None
                    and paths_identify_same_file(
                        resolved_model_path, resolved_source_path
                    )
                ):
                    error(
                        "{} source and converted paths identify the same file."
                        .format(location)
                    )
        declared_model_type = model_type
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
        converted_model_type = None
        converted_model_root = None
        if resolved_model_path is not None and resolved_model_path.is_file():
            converted_model_type, converted_model_root = read_converted_model(
                resolved_model_path, location, error
            )
        source_model_type = None
        source_model_root = None
        if (
            resolved_source_path is not None
            and resolved_source_path.is_file()
            and path_is_within_root(
                resolved_source_path, project_root.resolve()
            )
        ):
            source_model_type, source_model_root = read_source_model(
                resolved_source_path, location, error
            )
        if source_model_type is not None:
            if declared_model_type != source_model_type:
                error(
                    "{} declared type {} does not match source XML type {}."
                    .format(location, declared_model_type, source_model_type)
                )
            if source_model_type == "bpmn":
                parsed_source_has_di = source_bpmn_has_di(source_model_root)
                if (
                    isinstance(source_has_di, bool)
                    and source_has_di != parsed_source_has_di
                ):
                    error(
                        "{} source_has_di declaration does not match parsed "
                        "source BPMN DI.".format(location)
                    )
                source_has_di = parsed_source_has_di
            else:
                source_has_di = None
        else:
            source_has_di = None
        if converted_model_type is not None:
            if declared_model_type != converted_model_type:
                error(
                    "{} declared type {} does not match converted XML type {}."
                    .format(location, declared_model_type, converted_model_type)
                )
            model_type = converted_model_type

        bpmn_process_inventory = None
        bpmn_timer_start_inventory = None
        if converted_model_type == "bpmn" and converted_model_root is not None:
            bpmn_inventory = read_bpmn_inventory(
                converted_model_root, location, error
            )
            if bpmn_inventory is not None:
                (
                    bpmn_process_inventory,
                    bpmn_timer_start_inventory,
                ) = bpmn_inventory
        if not isinstance(timer_starts, list):
            error("{} recurring_timer_starts must be an array.".format(location))
            timer_starts = []
        if model_type == "dmn":
            if processes:
                error("{} processes must be empty for DMN models.".format(location))
                processes = []
            if timer_starts:
                error(
                    "{} recurring_timer_starts must be empty for DMN models."
                    .format(location)
                )
                timer_starts = []

        form_inventory = model.get("form_inventory")
        if not isinstance(form_inventory, list):
            error("{} form_inventory must be an array.".format(location))
            form_inventory = []
        form_inventory_ids = set()
        declared_form_inventory = Counter()
        declared_form_identities = {
            kind: set() for kind in FORM_INVENTORY_KINDS
        }
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
            else:
                declared_form_inventory[form_kind] += 1
                if is_nonempty_string(form_id):
                    declared_form_identities[form_kind].add(form_id)
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

        source_form_inventory = Counter()
        source_form_identities = {
            kind: set() for kind in FORM_INVENTORY_KINDS
        }
        if source_model_type == "bpmn" and source_model_root is not None:
            (
                source_form_inventory,
                source_form_identities,
            ) = read_source_form_inventory(
                source_model_root, location, error
            )
        for form_kind in sorted(FORM_INVENTORY_KINDS):
            if source_form_inventory[form_kind] != declared_form_inventory[
                form_kind
            ]:
                error(
                    "{} source form inventory detects {} {} record(s), but "
                    "form_inventory declares {}.".format(
                        location,
                        source_form_inventory[form_kind],
                        form_kind,
                        declared_form_inventory[form_kind],
                    )
                )
            missing_ids = sorted(
                source_form_identities[form_kind]
                - declared_form_identities[form_kind]
            )
            unexpected_ids = sorted(
                declared_form_identities[form_kind]
                - source_form_identities[form_kind]
            )
            if missing_ids or unexpected_ids:
                error(
                    "{} source form inventory identities do not match "
                    "detected {} IDs (missing: {}; unexpected: {}).".format(
                        location,
                        form_kind,
                        ", ".join(missing_ids) or "none",
                        ", ".join(unexpected_ids) or "none",
                    )
                )

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
        process_executability = {}
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
                    "missing_worker_input_scenarios",
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
                "missing_worker_input_scenarios",
                "covering_test",
                "reason",
            ):
                if field not in process:
                    error("{} is missing {}.".format(process_location, field))
            process_id = process.get("id")
            executable = process.get("executable")
            standalone = process.get("standalone_entry_point")
            scenarios = process.get("direct_start_scenarios")
            missing_worker_input_scenarios = process.get(
                "missing_worker_input_scenarios"
            )
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
            if isinstance(executable, bool):
                process_executability[process_id] = executable
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
            if not isinstance(missing_worker_input_scenarios, list):
                error(
                    "{} missing_worker_input_scenarios must be an array."
                    .format(process_location)
                )
                missing_worker_input_scenarios = []
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
            missing_worker_input_scenario_names = set()
            for scenario in missing_worker_input_scenarios:
                if not is_nonempty_string(scenario):
                    error(
                        "{} has an empty missing-worker-input scenario."
                        .format(process_location)
                    )
                    continue
                if scenario == "normal":
                    error(
                        "{} missing_worker_input_scenarios cannot include "
                        "normal.".format(process_location)
                    )
                    continue
                if scenario in missing_worker_input_scenario_names:
                    error(
                        "{} has a duplicate missing-worker-input scenario {}."
                        .format(process_location, scenario)
                    )
                missing_worker_input_scenario_names.add(scenario)
            if not executable:
                if not is_nonempty_string(process_reason):
                    error(
                        "{} needs a reason when it is not executable.".format(
                            process_location
                        )
                    )
                if (
                    standalone
                    or scenarios
                    or missing_worker_input_scenarios
                    or covering_test is not None
                ):
                    error(
                        "{} cannot list start scenarios, worker-input scenarios, "
                        "or a covering test when it is not executable."
                        .format(process_location)
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
                if not (
                    {"normal"} | missing_worker_input_scenario_names
                ).issubset(scenario_names):
                    error(
                        "{} direct_start_scenarios must include normal and "
                        "every worker-input inventory scenario."
                        .format(process_location)
                    )
                inventory_key = (
                    "process",
                    process_target,
                    "worker_input_inventory",
                    None,
                )
                add_expected(
                    expected,
                    inventory_key,
                    label_for_key(inventory_key),
                    allow_not_applicable=False,
                    require_pass=True,
                )
                required_scenarios = (
                    scenario_names
                    | missing_worker_input_scenario_names
                    | {"normal"}
                )
                for scenario in sorted(required_scenarios):
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
                if missing_worker_input_scenarios:
                    error(
                        "{} must not list missing-worker-input scenarios when "
                        "it is not a standalone entry point.".format(
                            process_location
                        )
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

        if bpmn_process_inventory is not None:
            actual_process_ids = set(bpmn_process_inventory)
            for process_id in sorted(actual_process_ids - process_ids):
                error(
                    "{} process inventory omits BPMN process {}."
                    .format(location, process_id)
                )
            for process_id in sorted(process_ids - actual_process_ids):
                error(
                    "{} process inventory includes unknown BPMN process {}."
                    .format(location, process_id)
                )
            for process_id in sorted(actual_process_ids & process_ids):
                if (
                    process_id in process_executability
                    and process_executability[process_id]
                    != bpmn_process_inventory[process_id]
                ):
                    error(
                        "{} process {} executable value does not match BPMN."
                        .format(location, process_id)
                    )

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
                or process_id not in process_ids
            ):
                error(
                    "{} refers to an unknown process {}.".format(
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
            if process_id not in executable_process_ids:
                continue
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

        if bpmn_timer_start_inventory is not None:
            for process_id, timer_id in sorted(
                bpmn_timer_start_inventory - timer_ids
            ):
                error(
                    "{} recurring_timer_starts omits converted BPMN "
                    "repeating timer start {} in process {}."
                    .format(location, timer_id, process_id)
                )
            for process_id, timer_id in sorted(
                timer_ids - bpmn_timer_start_inventory
            ):
                error(
                    "{} recurring_timer_starts includes unknown converted "
                    "BPMN repeating timer start {} in process {}."
                    .format(location, timer_id, process_id)
                )

    for converted_path, converted_location in converted_model_file_paths:
        for source_path, source_location in model_source_file_paths:
            if converted_location == source_location:
                continue
            if paths_identify_same_file(converted_path, source_path):
                error(
                    "{} converted model path identifies source model {}."
                    .format(converted_location, source_location)
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
    check_evidence_identities = {}
    test_evidence_paths = []
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
            resolved_evidence_path is not None
            and isinstance(result, str)
            and result in {"passed", "failed", "blocked", "unknown"}
        ):
            evidence_stat = resolved_evidence_path.stat()
            evidence_identity = (evidence_stat.st_dev, evidence_stat.st_ino)
            duplicate_evidence = check_evidence_identities.get(
                evidence_identity
            )
            if duplicate_evidence is not None:
                error(
                    "{} reuses the evidence file for {}. Each executed check "
                    "must use a distinct evidence file".format(
                        location, duplicate_evidence
                    )
                )
            else:
                check_evidence_identities[evidence_identity] = location
        if (
            target_type == "module"
            and kind == "tests"
            and resolved_evidence_path is not None
        ):
            duplicate_suite = next(
                (
                    previous
                    for previous in test_evidence_paths
                    if resolved_evidence_path.samefile(previous[0])
                ),
                None,
            )
            if duplicate_suite is not None:
                error(
                    "Duplicate suite evidence: test suites must use distinct "
                    "evidence files (module {} "
                    "scenario {} and module {} scenario {}).".format(
                        duplicate_suite[1],
                        duplicate_suite[2],
                        check.get("target"),
                        check.get("scenario"),
                    )
                )
            else:
                test_evidence_paths.append(
                    (
                        resolved_evidence_path,
                        check.get("target"),
                        check.get("scenario"),
                    )
                )
        if (
            target_type == "module"
            and kind == "tests"
            and is_nonempty_string(command)
        ):
            if command in test_commands:
                error(
                    "Duplicate suite command: test suites must use distinct "
                    "commands (module {} "
                    "scenario {} and module {} scenario {}).".format(
                        test_commands[command][0],
                        test_commands[command][1],
                        check.get("target"),
                        check.get("scenario"),
                    )
                )
            else:
                test_commands[command] = (
                    check.get("target"),
                    check.get("scenario"),
                )
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
            and not command_invokes_docker_info(command)
        ):
            error("{} must invoke docker info.".format(location))

    for path, launch_keys in spring_boot_launch_requirements:
        launch_results = [
            seen.get(key, {}).get("result")
            for key in launch_keys
        ]
        if launch_results.count("not_applicable") != 1:
            error(
                "Module {} must mark exactly one Spring Boot launch check "
                "not_applicable.".format(path)
            )

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


def next_report_section(contents, offset):
    match = re.search(r"(?m)^##[ \t]+", contents[offset:])
    if match is None:
        return None
    return offset + match.start()


def find_markerless_legacy_gate(contents):
    heading_pattern = re.compile(
        r"(?m)^## Aggregate validation gate[ \t]*$"
    )
    status_pattern = re.compile(r"(?m)^\*\*Validation gate:\*\*[^\n]*$")
    for status in status_pattern.finditer(contents):
        marker_start = contents.rfind(REPORT_START, 0, status.start())
        marker_end = contents.rfind(REPORT_END, 0, status.start())
        if marker_start > marker_end:
            continue
        for heading in reversed(
            [
                match
                for match in heading_pattern.finditer(contents)
                if match.start() < status.start()
            ]
        ):
            section_end = next_report_section(contents, heading.end())
            if section_end is None or status.start() < section_end:
                if section_end is None:
                    return status.start(), status.end()
                return heading.start(), section_end
        return status.start(), status.end()
    return None


def find_report_marker_regions(contents):
    marker_pattern = re.compile(
        "{}|{}".format(re.escape(REPORT_START), re.escape(REPORT_END))
    )
    regions = []
    open_start = None
    for marker in marker_pattern.finditer(contents):
        if marker.group() == REPORT_START:
            if open_start is not None:
                return None
            open_start = marker.start()
        elif open_start is None:
            return None
        else:
            regions.append((open_start, marker.end()))
            open_start = None
    if open_start is not None:
        return None
    return regions


def remove_report_region(contents, start, end):
    prefix = contents[:start].rstrip()
    suffix = contents[end:].lstrip()
    if prefix and suffix:
        return prefix + "\n\n" + suffix
    return prefix or suffix


def remove_malformed_report_gate(contents):
    original = contents
    while True:
        legacy_gate = find_markerless_legacy_gate(contents)
        if legacy_gate is None:
            break
        start, end = legacy_gate
        if end == len(contents):
            return original, False
        contents = remove_report_region(contents, start, end)

    marker_regions = find_report_marker_regions(contents)
    if marker_regions is not None:
        for start, end in reversed(marker_regions):
            contents = remove_report_region(contents, start, end)
        return contents, contents != original

    start_positions = [
        match.start() for match in re.finditer(re.escape(REPORT_START), contents)
    ]
    end_positions = [
        match.start() for match in re.finditer(re.escape(REPORT_END), contents)
    ]
    marker_positions = start_positions + end_positions
    if not marker_positions:
        return contents, contents != original

    first_marker = min(marker_positions)
    gate_heading = contents.rfind(
        "## Aggregate validation gate", 0, first_marker
    )
    gate_status = contents.rfind("**Validation gate:**", 0, first_marker)
    if gate_heading >= 0:
        start = gate_heading
    elif gate_status >= 0:
        start = contents.rfind("\n", 0, gate_status) + 1
    else:
        start = first_marker

    if not start_positions:
        end = max(end_positions) + len(REPORT_END)
    else:
        last_start = max(start_positions)
        ends_after_last_start = [
            position for position in end_positions if position > last_start
        ]
        if ends_after_last_start:
            end = max(ends_after_last_start) + len(REPORT_END)
        else:
            marker_end = last_start + len(REPORT_START)
            next_heading = next_report_section(contents, marker_end)
            if next_heading is not None:
                heading_end = contents.find("\n", next_heading)
                if heading_end < 0:
                    heading_end = len(contents)
                if contents[next_heading:heading_end].strip() == (
                    "## Aggregate validation gate"
                ):
                    next_heading = next_report_section(
                        contents, heading_end + 1
                    )
            if next_heading is None:
                if contents[marker_end:].strip():
                    return original, False
                end = len(contents)
            else:
                end = next_heading

    return remove_report_region(contents, start, end), True


def write_report(report_path, summary, project_root, summary_path):
    resolved = resolve_report_path(report_path, project_root)
    if resolved.exists():
        contents = resolved.read_text(encoding="utf-8")
    else:
        contents = ""
    has_start = REPORT_START in contents
    has_end = REPORT_END in contents
    markers_in_order = (
        has_start
        and has_end
        and contents.index(REPORT_START) < contents.index(REPORT_END)
    )
    malformed = (
        has_start != has_end
        or contents.count(REPORT_START) > 1
        or contents.count(REPORT_END) > 1
        or (has_start and has_end and not markers_in_order)
        or find_markerless_legacy_gate(contents) is not None
    )
    if malformed:
        summary["readiness"] = "not_ready"
        summary["counts"]["invalid"] += 1
        summary["blockers"].append(
            "The report contains a malformed validation gate block."
        )
        contents, removed_gate = remove_malformed_report_gate(contents)
        if not removed_gate:
            return malformed
        has_start = False
        has_end = False

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
    return malformed


def write_summary(summary_path, summary, project_root):
    resolved = resolve_summary_path(summary_path, project_root)
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

    try:
        project_root = Path(args.project_root).resolve()
    except (OSError, RuntimeError) as exception:
        summary = make_input_error(
            "The project root cannot be resolved: {}.".format(exception)
        )
        print(json.dumps(summary, indent=2, sort_keys=True))
        return 1
    project_root_exists = project_root.is_dir()
    resolved_summary = None
    resolved_report = None
    cli_path_error = None
    output_path_error = None
    if project_root_exists:
        try:
            resolved_summary, resolved_report = validate_cli_paths(
                args.evidence,
                args.summary,
                args.report,
                project_root,
            )
        except (OSError, ValueError, RuntimeError) as exception:
            cli_path_error = exception

    if not project_root_exists:
        summary = make_input_error("The project root does not exist.")
    elif cli_path_error is not None:
        summary = make_input_error(
            "The validation paths cannot be used: {}.".format(cli_path_error)
        )
    else:
        try:
            resolved_evidence = resolve_evidence_path(
                args.evidence, project_root
            )
            data = json.loads(resolved_evidence.read_text(encoding="utf-8"))
            output_paths = [("summary", resolved_summary)]
            if resolved_report is not None:
                output_paths.append(("report", resolved_report))
            try:
                validate_cli_outputs_against_evidence(
                    data, output_paths, project_root
                )
            except (OSError, ValueError, RuntimeError) as exception:
                output_path_error = exception
            excluded_evidence_paths = {
                resolved_evidence,
                resolved_summary,
            }
            if resolved_report is not None:
                excluded_evidence_paths.add(resolved_report)
            summary = validate_manifest(
                data, project_root, excluded_evidence_paths
            )
        except (
            OSError,
            ValueError,
            json.JSONDecodeError,
            RuntimeError,
        ) as exception:
            summary = make_input_error(
                "The evidence manifest or one of its inputs cannot be "
                "validated: {}.".format(exception)
            )
        if output_path_error is not None:
            cli_path_error = output_path_error
            summary["readiness"] = "not_ready"
            summary["counts"]["invalid"] += 1
            summary["blockers"].append(
                "The validation paths cannot be used: {}.".format(
                    output_path_error
                )
            )

    summary_written = False
    if project_root_exists and cli_path_error is None:
        try:
            write_summary(args.summary, summary, project_root)
            summary_written = True
        except (OSError, ValueError, RuntimeError) as exception:
            summary["readiness"] = "not_ready"
            summary["counts"]["invalid"] += 1
            summary["blockers"].append(
                "The validation summary cannot be written: {}.".format(exception)
            )

    report_failed = False
    if args.report and project_root_exists and cli_path_error is None:
        try:
            report_failed = write_report(
                args.report, summary, project_root, args.summary
            )
        except (OSError, ValueError, RuntimeError) as exception:
            summary["readiness"] = "not_ready"
            summary["counts"]["invalid"] += 1
            summary["blockers"].append(
                "The validation report cannot be updated: {}.".format(exception)
            )
            report_failed = True

    if report_failed and summary_written:
        try:
            write_summary(args.summary, summary, project_root)
        except (OSError, ValueError, RuntimeError) as exception:
            summary["counts"]["invalid"] += 1
            summary["blockers"].append(
                "The not-ready summary cannot be rewritten: {}.".format(exception)
            )

    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0 if summary["readiness"] == "ready" else 1


if __name__ == "__main__":
    sys.exit(main())
