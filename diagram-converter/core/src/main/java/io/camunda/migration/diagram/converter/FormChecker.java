/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.migration.diagram.converter.DiagramCheckResult.ElementCheckMessage;
import io.camunda.migration.diagram.converter.DiagramCheckResult.ElementCheckResult;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes Camunda 7 form files ({@code *.form}) and reports findings for content that needs manual
 * review when migrating to Camunda 8. Unlike BPMN/DMN analysis, forms are JSON documents, so this
 * checker operates on the parsed JSON tree instead of the DOM visitor pipeline. It takes a
 * conservative approach: everything that is not trivially migratable is reported for review, no
 * content is transformed.
 *
 * <p>Findings are reported as {@link ElementCheckResult}s so they appear in the same analysis
 * outputs (JSON, CSV, XLSX, markdown, webapp) as BPMN/DMN findings. File-level findings (schema
 * version, target platform) are attached to a synthetic element of type {@value
 * #FORM_ELEMENT_TYPE}; component-level findings (JUEL expressions, unknown component types) are
 * attached to the component they belong to.
 */
public class FormChecker {

  /** Synthetic element type used for findings that apply to the form file as a whole. */
  public static final String FORM_ELEMENT_TYPE = "form";

  /**
   * Highest form-js schema version known to this converter. Forms with an older (or missing) schema
   * version are flagged for review.
   */
  public static final int LATEST_KNOWN_SCHEMA_VERSION = 18;

  /**
   * Component types provided by form-js on recent Camunda 8 versions. Anything else is flagged for
   * review.
   */
  private static final Set<String> KNOWN_COMPONENT_TYPES =
      Set.of(
          "button",
          "checkbox",
          "checklist",
          "datetime",
          "documentPreview",
          "dynamiclist",
          "expression",
          "filepicker",
          "group",
          "html",
          "iframe",
          "image",
          "number",
          "radio",
          "select",
          "separator",
          "spacer",
          "table",
          "taglist",
          "text",
          "textarea",
          "textfield");

  private static final Pattern JUEL_EXPRESSION = Pattern.compile("[$#]\\{[^}]*}");

  private static final String EXECUTION_PLATFORM_FIELD = "executionPlatform";
  private static final String SCHEMA_VERSION_FIELD = "schemaVersion";
  private static final String COMPONENTS_FIELD = "components";
  private static final String ID_FIELD = "id";
  private static final String KEY_FIELD = "key";
  private static final String TYPE_FIELD = "type";
  private static final String LABEL_FIELD = "label";

  private FormChecker() {}

  /**
   * Checks the given form JSON and returns the findings.
   *
   * @param filename the name of the form file, used in the result
   * @param formContent the content of a form file
   * @param properties the converter properties, providing the target platform version
   * @return the check result containing one entry per finding
   * @throws IllegalArgumentException if the content is not valid JSON or not a JSON object, or the
   *     platform version is missing or invalid
   */
  public static DiagramCheckResult check(
      String filename, String formContent, ConverterProperties properties) {
    // validates the target platform version up front, consistent with FormConverter.convert; it
    // is also the basis for future version-dependent findings
    FormConverter.targetVersion(properties);
    JsonNode root = FormConverter.parse(formContent);
    if (!(root instanceof ObjectNode form)) {
      throw new IllegalArgumentException(
          "Form content must be a JSON object, but was: " + root.getNodeType());
    }
    DiagramCheckResult result = new DiagramCheckResult();
    result.setFilename(filename);
    result.setConverterVersion(FormChecker.class.getPackage().getImplementationVersion());
    Map<String, ElementCheckResult> elementResults = new LinkedHashMap<>();
    checkFileLevel(filename, form, elementResults);
    checkComponents(form.path(COMPONENTS_FIELD), COMPONENTS_FIELD, elementResults);
    result.getResults().addAll(elementResults.values());
    return result;
  }

  private static void checkFileLevel(
      String filename, ObjectNode form, Map<String, ElementCheckResult> elementResults) {
    ElementCheckResult fileLevel =
        elementResults.computeIfAbsent(
            FORM_ELEMENT_TYPE,
            key -> createElementResult(formId(form, filename), FORM_ELEMENT_TYPE, null));
    String executionPlatform = form.path(EXECUTION_PLATFORM_FIELD).asText(null);
    if (FormConverter.TARGET_EXECUTION_PLATFORM.equals(executionPlatform)) {
      addMessage(fileLevel, MessageFactory.formAlreadyCamunda8(executionPlatform));
    }
    JsonNode schemaVersion = form.get(SCHEMA_VERSION_FIELD);
    if (schemaVersion == null || !schemaVersion.isInt()) {
      addMessage(
          fileLevel,
          MessageFactory.formSchemaVersionMissing(String.valueOf(LATEST_KNOWN_SCHEMA_VERSION)));
    } else if (schemaVersion.intValue() < LATEST_KNOWN_SCHEMA_VERSION) {
      addMessage(
          fileLevel,
          MessageFactory.formSchemaVersionOutdated(
              String.valueOf(schemaVersion.intValue()),
              String.valueOf(LATEST_KNOWN_SCHEMA_VERSION)));
    }
    if (fileLevel.getMessages().isEmpty()) {
      elementResults.remove(FORM_ELEMENT_TYPE);
    }
  }

  private static void checkComponents(
      JsonNode components, String path, Map<String, ElementCheckResult> elementResults) {
    if (!components.isArray()) {
      return;
    }
    int index = 0;
    for (JsonNode component : components) {
      String componentPath = path + "/" + index++;
      if (!component.isObject()) {
        continue;
      }
      checkComponent((ObjectNode) component, componentPath, elementResults);
      // containers (e.g. group, dynamiclist) nest their own components
      checkComponents(
          component.path(COMPONENTS_FIELD), componentPath + "/" + COMPONENTS_FIELD, elementResults);
    }
  }

  private static void checkComponent(
      ObjectNode component, String path, Map<String, ElementCheckResult> elementResults) {
    String type = component.path(TYPE_FIELD).asText(null);
    ElementCheckResult elementResult = null;
    if (type == null || !KNOWN_COMPONENT_TYPES.contains(type)) {
      elementResult = componentResult(component, type, path, elementResults);
      addMessage(
          elementResult, MessageFactory.formComponentUnknown(type != null ? type : "missing"));
    }
    for (Map.Entry<String, JsonNode> property : component.properties()) {
      if (COMPONENTS_FIELD.equals(property.getKey())) {
        // nested components are checked as components of their own, not as properties here
        continue;
      }
      List<String> expressions = new ArrayList<>();
      collectJuelExpressions(property.getValue(), expressions);
      for (String expression : expressions) {
        if (elementResult == null) {
          elementResult = componentResult(component, type, path, elementResults);
        }
        addMessage(elementResult, MessageFactory.formJuelExpression(expression, property.getKey()));
      }
    }
  }

  private static ElementCheckResult componentResult(
      ObjectNode component,
      String type,
      String path,
      Map<String, ElementCheckResult> elementResults) {
    String id = componentId(component);
    // key the map by the component's position in the JSON tree: the key/id/type fallback used as
    // display id is not necessarily unique (e.g. several keyless textfields)
    return elementResults.computeIfAbsent(
        FORM_ELEMENT_TYPE + ":" + path,
        key ->
            createElementResult(
                id, type != null ? type : "unknown", component.path(LABEL_FIELD).asText(null)));
  }

  private static void collectJuelExpressions(JsonNode node, List<String> expressions) {
    if (node.isTextual()) {
      Matcher matcher = JUEL_EXPRESSION.matcher(node.textValue());
      while (matcher.find()) {
        expressions.add(matcher.group());
      }
    } else if (node.isContainerNode()) {
      node.forEach(child -> collectJuelExpressions(child, expressions));
    }
  }

  private static ElementCheckResult createElementResult(
      String elementId, String elementType, String elementName) {
    ElementCheckResult elementResult = new ElementCheckResult();
    elementResult.setElementId(elementId);
    elementResult.setElementType(elementType);
    elementResult.setElementName(elementName);
    return elementResult;
  }

  private static void addMessage(ElementCheckResult elementResult, Message message) {
    ElementCheckMessage checkMessage = new ElementCheckMessage();
    checkMessage.setSeverity(message.getSeverity());
    checkMessage.setMessage(message.getMessage());
    checkMessage.setLink(message.getLink());
    checkMessage.setId(message.getId());
    elementResult.getMessages().add(checkMessage);
  }

  private static String formId(ObjectNode form, String filename) {
    String id = form.path(ID_FIELD).asText(null);
    return id != null ? id : filename;
  }

  private static String componentId(ObjectNode component) {
    String key = component.path(KEY_FIELD).asText(null);
    if (key != null) {
      return key;
    }
    String id = component.path(ID_FIELD).asText(null);
    return id != null ? id : component.path(TYPE_FIELD).asText("unknown");
  }
}
