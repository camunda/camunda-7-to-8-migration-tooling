#!/usr/bin/env python3
"""Validate FEEL prefixes and leftover expression-language attributes in model copies."""

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL"
CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn"
ZEEBE_NS = "http://camunda.org/schema/zeebe/1.0"
BPMN = f"{{{BPMN_NS}}}"
ZEEBE = f"{{{ZEEBE_NS}}}"
FEEL_LANGUAGES = {"feel", "juel"}
CONDITION_TAGS = {BPMN + "conditionExpression", BPMN + "condition"}
JOB_TYPE_SOURCE_ATTRIBUTES = {"topic"}
ASSIGNMENT_SOURCE_ATTRIBUTES = {"assignee", "candidateGroups", "candidateUsers"}
NUMBER_LITERAL = re.compile(
    r"[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?\Z"
)
QUOTED_LITERAL = re.compile(r"""(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')\Z""")


def local_name(name):
    return name.rsplit("}", 1)[-1]


def namespace(name):
    if name.startswith("{"):
        return name[1:].split("}", 1)[0]
    return ""


def text_content(element):
    return "".join(element.itertext()).strip()


def has_feel_script(element):
    for script in element.iter():
        script_format = local_attribute(script, "scriptFormat")
        if (
            namespace(script.tag) == CAMUNDA_NS
            and local_name(script.tag) == "script"
            and script_format is not None
            and script_format.strip().casefold() == "feel"
        ):
            return True
    return False


def is_feel_literal(value):
    candidate = value.strip()
    return (
        candidate.casefold() in {"true", "false", "null"}
        or bool(NUMBER_LITERAL.fullmatch(candidate))
        or bool(QUOTED_LITERAL.fullmatch(candidate))
    )


def has_feel_prefix(value):
    return value.strip().startswith("=")


def has_expression_marker(value):
    return value.strip().startswith("=") or "${" in value or "#{" in value


def is_dynamic_source(value):
    return has_expression_marker(value)


def parent_index(root):
    return {child: parent for parent in root.iter() for child in parent}


def id_index(root):
    result = {}
    for element in root.iter():
        identifier = element.get("id")
        if identifier and namespace(element.tag) == BPMN_NS:
            result.setdefault(identifier, []).append(element)
    return result


def nearest_bpmn_id(element, parents):
    current = element
    while current is not None:
        identifier = current.get("id")
        if identifier and namespace(current.tag) == BPMN_NS:
            return identifier
        current = parents.get(current)
    return None


def conditional_event_owner(element, parents):
    current = element
    while current is not None:
        if namespace(current.tag) == BPMN_NS and local_name(current.tag).endswith("Event"):
            return current
        current = parents.get(current)
    return None


def element_context(element, parents):
    current = element
    while current is not None:
        identifier = current.get("id")
        if identifier:
            return f"{local_name(current.tag)}#{identifier}"
        current = parents.get(current)
    return local_name(element.tag)


def local_attribute(element, name):
    for attribute, value in element.attrib.items():
        if local_name(attribute) == name:
            return value
    return None


def camunda_attribute_values(element, names):
    return [
        (local_name(attribute), value)
        for attribute, value in element.attrib.items()
        if namespace(attribute) == CAMUNDA_NS and local_name(attribute) in names
    ]


def converted_owner(source_element, source_parents, converted_ids, path, field, errors):
    identifier = nearest_bpmn_id(source_element, source_parents)
    if not identifier:
        errors.append(f"{path}: cannot pair the dynamic source {field} because it has no XML ID")
        return None
    candidates = converted_ids.get(identifier, [])
    if len(candidates) != 1:
        errors.append(
            f"{path}: cannot pair the dynamic source {field} with one converted element "
            f"for XML ID {identifier!r}"
        )
        return None
    return candidates[0]


def require_prefix(path, element, field, value, parents, errors, context=None):
    if not has_feel_prefix(value):
        location = context or element_context(element, parents)
        errors.append(
            f"{path}: {location} {field} has a value without the required leading '='"
        )


def check_condition_language(path, element, parents, errors):
    language = local_attribute(element, "language")
    normalized_language = language.strip().casefold() if language is not None else ""
    if not normalized_language or normalized_language in FEEL_LANGUAGES:
        return True
    errors.append(
        f"{path}: {element_context(element, parents)} {local_name(element.tag)} uses "
        f"unsupported condition language {language.strip()!r}; "
        "an explicit redesign is required"
    )
    return False


def check_feel_slots(root, path, parents, errors):
    for element in root.iter():
        if element.tag not in CONDITION_TAGS:
            continue
        if not check_condition_language(path, element, parents, errors):
            continue
        value = text_content(element)
        if value and not is_feel_literal(value):
            require_prefix(
                path,
                element,
                local_name(element.tag),
                value,
                parents,
                errors,
            )


def check_legacy_attributes(root, path, parents, errors):
    is_bpmn = root.tag == BPMN + "definitions"
    for element in root.iter():
        for attribute, value in element.attrib.items():
            name = local_name(attribute)
            if name == "language" and value.strip().casefold() in FEEL_LANGUAGES:
                errors.append(
                    f"{path}: {element_context(element, parents)} retains "
                    f'{name}="{value.strip()}"'
                )
            elif is_bpmn and name == "expressionLanguage":
                errors.append(
                    f"{path}: {element_context(element, parents)} retains a BPMN "
                    "expressionLanguage attribute"
                )


def check_unprefixed_expression_attributes(root, path, parents, errors):
    expression_attributes = {
        ZEEBE + "input": {"source"},
        ZEEBE + "output": {"source"},
        ZEEBE + "calledElement": {"processId"},
        ZEEBE + "subscription": {"correlationKey"},
        ZEEBE + "taskDefinition": {"type"},
        ZEEBE + "assignmentDefinition": ASSIGNMENT_SOURCE_ATTRIBUTES,
        ZEEBE + "formDefinition": {"formId"},
    }
    for element in root.iter():
        for attribute in expression_attributes.get(element.tag, set()):
            value = element.get(attribute)
            if value is None:
                continue
            needs_expression_prefix = (
                (element.tag == ZEEBE + "subscription" and attribute == "correlationKey")
                or "${" in value
                or "#{" in value
            )
            if not has_feel_prefix(value) and needs_expression_prefix:
                require_prefix(
                    path,
                    element,
                    f"@{attribute}",
                    value,
                    parents,
                    errors,
                )


def check_mapped_attribute(
    path,
    source_element,
    source_value,
    source_parents,
    converted_ids,
    target_tag,
    target_attribute,
    field,
    errors,
):
    if not is_dynamic_source(source_value):
        return
    owner = converted_owner(
        source_element, source_parents, converted_ids, path, field, errors
    )
    if owner is None:
        return
    targets = [element for element in owner.iter() if element.tag == target_tag]
    if not targets:
        errors.append(
            f"{path}: {element_context(owner, {})} is missing the converted {field}"
        )
        return
    for target in targets:
        value = target.get(target_attribute)
        if value is None:
            errors.append(
                f"{path}: {element_context(target, {})} is missing the dynamic {field} "
                f"attribute {target_attribute!r}"
            )
        else:
            require_prefix(
                path,
                target,
                f"@{target_attribute}",
                value,
                {},
                errors,
                context=element_context(owner, {}),
            )


def check_source_condition(
    source_condition,
    source_owner,
    source_parents,
    converted_ids,
    path,
    field,
    errors,
):
    value = text_content(source_condition)
    language = local_attribute(source_condition, "language")
    if not check_condition_language(path, source_condition, source_parents, errors):
        return
    dynamic = (
        is_dynamic_source(value)
        or (language is not None and language.strip().casefold() in FEEL_LANGUAGES)
        or (value and not is_feel_literal(value))
    )
    if not dynamic:
        return
    pairing_source = source_owner
    if (
        field == "condition"
        and source_owner is not None
        and source_owner.tag == BPMN + "conditionalEventDefinition"
    ):
        definition_id = source_owner.get("id")
        definition_matches = (
            converted_ids.get(definition_id, []) if definition_id else []
        )
        if len(definition_matches) != 1:
            event_owner = conditional_event_owner(source_owner, source_parents)
            if event_owner is not None:
                pairing_source = event_owner
    owner = converted_owner(
        pairing_source, source_parents, converted_ids, path, field, errors
    )
    if owner is None:
        return
    targets = list(owner.iter(BPMN + field))
    if not targets:
        errors.append(
            f"{path}: {element_context(owner, {})} is missing its converted {field}"
        )
        return
    for target in targets:
        require_prefix(
            path,
            target,
            field,
            text_content(target),
            {},
            errors,
            context=element_context(owner, {}),
        )


def check_source_expressions(source_root, converted_root, path, errors):
    source_parents = parent_index(source_root)
    converted_ids = id_index(converted_root)

    for field in ("conditionExpression", "condition"):
        for source_condition in source_root.iter(BPMN + field):
            check_source_condition(
                source_condition,
                source_parents.get(source_condition),
                source_parents,
                converted_ids,
                path,
                field,
                errors,
            )

    for source_parameter in source_root.iter():
        parameter_tag = local_name(source_parameter.tag)
        parameter_namespace = namespace(source_parameter.tag)
        if parameter_namespace != CAMUNDA_NS or parameter_tag not in {
            "inputParameter",
            "outputParameter",
        }:
            continue
        source_value = text_content(source_parameter)
        language = local_attribute(source_parameter, "language")
        if not (
            is_dynamic_source(source_value)
            or (language is not None and language.strip().casefold() in FEEL_LANGUAGES)
            or has_feel_script(source_parameter)
        ):
            continue
        owner = converted_owner(
            source_parameter,
            source_parents,
            converted_ids,
            path,
            parameter_tag,
            errors,
        )
        if owner is None:
            continue
        target_tag = ZEEBE + (
            "input" if parameter_tag == "inputParameter" else "output"
        )
        target_name = source_parameter.get("name")
        targets = [
            element
            for element in owner.iter()
            if element.tag == target_tag and element.get("target") == target_name
        ]
        if not targets:
            errors.append(
                f"{path}: {element_context(owner, {})} is missing the converted "
                f"zeebe:{local_name(target_tag)} mapping for {parameter_tag} "
                f"named {target_name!r}"
            )
            continue
        for target in targets:
            value = target.get("source")
            if value is None:
                errors.append(
                    f"{path}: {element_context(target, {})} is missing the dynamic @source"
                )
            else:
                require_prefix(
                    path,
                    target,
                    "@source",
                    value,
                    {},
                    errors,
                    context=element_context(owner, {}),
                )

    for source_element in source_root.iter():
        language = local_attribute(source_element, "language")
        for attribute, source_value in source_element.attrib.items():
            if local_name(attribute) != "correlationKey":
                continue
            dynamic = is_dynamic_source(source_value) or (
                language is not None and language.strip().casefold() in FEEL_LANGUAGES
            )
            if not dynamic:
                continue
            owner = converted_owner(
                source_element,
                source_parents,
                converted_ids,
                path,
                "correlationKey",
                errors,
            )
            if owner is None:
                continue
            targets = [
                element for element in owner.iter() if element.tag == ZEEBE + "subscription"
            ]
            if not targets:
                errors.append(
                    f"{path}: {element_context(owner, {})} is missing the converted "
                    "zeebe:subscription"
                )
                continue
            for target in targets:
                value = target.get("correlationKey")
                if value is None:
                    errors.append(
                        f"{path}: {element_context(owner, {})} is missing the dynamic "
                        "zeebe:subscription/@correlationKey"
                    )
                else:
                    require_prefix(
                        path,
                        target,
                        "@correlationKey",
                        value,
                        {},
                        errors,
                        context=element_context(owner, {}),
                    )

    for source_call in source_root.iter(BPMN + "callActivity"):
        value = source_call.get("calledElement", "")
        if is_dynamic_source(value):
            check_mapped_attribute(
                path,
                source_call,
                value,
                source_parents,
                converted_ids,
                ZEEBE + "calledElement",
                "processId",
                "zeebe:calledElement/@processId",
                errors,
            )

    for source_element in source_root.iter():
        for name, value in camunda_attribute_values(
            source_element, JOB_TYPE_SOURCE_ATTRIBUTES
        ):
            if is_dynamic_source(value):
                check_mapped_attribute(
                    path,
                    source_element,
                    value,
                    source_parents,
                    converted_ids,
                    ZEEBE + "taskDefinition",
                    "type",
                    f"zeebe:taskDefinition/@type from camunda:{name}",
                    errors,
                )

        for name, value in camunda_attribute_values(
            source_element, ASSIGNMENT_SOURCE_ATTRIBUTES
        ):
            if is_dynamic_source(value):
                check_mapped_attribute(
                    path,
                    source_element,
                    value,
                    source_parents,
                    converted_ids,
                    ZEEBE + "assignmentDefinition",
                    name,
                    f"zeebe:assignmentDefinition/@{name}",
                    errors,
                )

        form_ref = next(
            (
                value
                for name, value in camunda_attribute_values(source_element, {"formRef"})
            ),
            None,
        )
        if form_ref is not None and is_dynamic_source(form_ref):
            check_mapped_attribute(
                path,
                source_element,
                form_ref,
                source_parents,
                converted_ids,
                ZEEBE + "formDefinition",
                "formId",
                "zeebe:formDefinition/@formId",
                errors,
            )


def validate_pair(source_path, converted_path):
    errors = []
    if source_path.resolve() == converted_path.resolve():
        return [f"{converted_path}: source and converted paths must be different"]
    try:
        source_root = ET.parse(source_path).getroot()
    except (OSError, ET.ParseError) as error:
        return [f"{source_path}: cannot parse source XML: {error}"]
    try:
        converted_root = ET.parse(converted_path).getroot()
    except (OSError, ET.ParseError) as error:
        return [f"{converted_path}: cannot parse converted XML: {error}"]

    converted_parents = parent_index(converted_root)
    check_feel_slots(converted_root, str(converted_path), converted_parents, errors)
    check_legacy_attributes(converted_root, str(converted_path), converted_parents, errors)
    check_unprefixed_expression_attributes(
        converted_root, str(converted_path), converted_parents, errors
    )
    check_source_expressions(source_root, converted_root, str(converted_path), errors)
    return list(dict.fromkeys(errors))


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Check FEEL prefixes and leftover expression-language attributes."
    )
    parser.add_argument(
        "--pair",
        action="append",
        nargs=2,
        metavar=("SOURCE", "CONVERTED"),
        required=True,
        help="source model and its converted-c8 copy; repeat for every model",
    )
    arguments = parser.parse_args(argv)

    errors = []
    for source, converted in arguments.pair:
        errors.extend(validate_pair(Path(source), Path(converted)))
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(f"PASS: checked {len(arguments.pair)} source and converted model pair(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
