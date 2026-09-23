/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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

final class FormProcessor {

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

  private static final Set<String> STRUCTURAL_PROPERTIES = Set.of("id", "key", "type");
  private static final Set<String> CAMUNDA_CONTEXT_VARIABLES =
      Set.of("authenticatedUserId", "caseExecution", "execution", "task");
  private static final Set<String> FEEL_RESERVED_WORDS =
      Set.of(
          "and",
          "between",
          "else",
          "every",
          "external",
          "false",
          "for",
          "function",
          "if",
          "in",
          "instance",
          "not",
          "null",
          "of",
          "or",
          "return",
          "satisfies",
          "some",
          "then",
          "true");

  private static final Pattern JUEL_EXPRESSION = Pattern.compile("[$#]\\{[^}]*}");
  private static final Pattern SIMPLE_JUEL_EXPRESSION =
      Pattern.compile("[$#]\\{([A-Za-z_][A-Za-z0-9_]*)}");

  private static final String EXECUTION_PLATFORM_FIELD = "executionPlatform";
  private static final String SCHEMA_VERSION_FIELD = "schemaVersion";
  private static final String COMPONENTS_FIELD = "components";
  private static final String ID_FIELD = "id";
  private static final String KEY_FIELD = "key";
  private static final String TYPE_FIELD = "type";
  private static final String LABEL_FIELD = "label";

  private FormProcessor() {}

  static DiagramCheckResult process(String filename, ObjectNode form) {
    DiagramCheckResult result = new DiagramCheckResult();
    result.setFilename(filename);
    result.setConverterVersion(FormProcessor.class.getPackage().getImplementationVersion());
    Map<String, ElementCheckResult> elementResults = new LinkedHashMap<>();
    checkFileLevel(filename, form, elementResults);
    processComponents(form.path(COMPONENTS_FIELD), COMPONENTS_FIELD, elementResults);
    result.setResults(new ArrayList<>(elementResults.values()));
    return result;
  }

  private static void checkFileLevel(
      String filename, ObjectNode form, Map<String, ElementCheckResult> elementResults) {
    ElementCheckResult fileLevel =
        elementResults.computeIfAbsent(
            FormChecker.FORM_ELEMENT_TYPE,
            key ->
                createElementResult(formId(form, filename), FormChecker.FORM_ELEMENT_TYPE, null));
    String executionPlatform = form.path(EXECUTION_PLATFORM_FIELD).asText(null);
    if (FormConverter.TARGET_EXECUTION_PLATFORM.equals(executionPlatform)) {
      addMessage(fileLevel, MessageFactory.formAlreadyCamunda8(executionPlatform));
    }
    JsonNode schemaVersion = form.get(SCHEMA_VERSION_FIELD);
    if (schemaVersion == null || !schemaVersion.isInt()) {
      addMessage(
          fileLevel,
          MessageFactory.formSchemaVersionMissing(
              String.valueOf(FormChecker.LATEST_KNOWN_SCHEMA_VERSION)));
    } else if (schemaVersion.intValue() < FormChecker.LATEST_KNOWN_SCHEMA_VERSION) {
      addMessage(
          fileLevel,
          MessageFactory.formSchemaVersionOutdated(
              String.valueOf(schemaVersion.intValue()),
              String.valueOf(FormChecker.LATEST_KNOWN_SCHEMA_VERSION)));
    }
    if (fileLevel.getMessages().isEmpty()) {
      elementResults.remove(FormChecker.FORM_ELEMENT_TYPE);
    }
  }

  private static void processComponents(
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
      processComponent((ObjectNode) component, componentPath, elementResults);
      processComponents(
          component.path(COMPONENTS_FIELD), componentPath + "/" + COMPONENTS_FIELD, elementResults);
    }
  }

  private static void processComponent(
      ObjectNode component, String path, Map<String, ElementCheckResult> elementResults) {
    String type = component.path(TYPE_FIELD).asText(null);
    String elementName = component.path(LABEL_FIELD).asText(null);
    ElementCheckResult elementResult = null;
    if (type == null || !KNOWN_COMPONENT_TYPES.contains(type)) {
      elementResult = componentResult(component, type, elementName, path, elementResults);
      addMessage(
          elementResult, MessageFactory.formComponentUnknown(type != null ? type : "missing"));
    }
    for (Map.Entry<String, JsonNode> property : component.properties()) {
      String propertyName = property.getKey();
      if (COMPONENTS_FIELD.equals(propertyName)) {
        continue;
      }
      List<Message> messages = new ArrayList<>();
      JsonNode converted = processExpressions(property.getValue(), propertyName, messages);
      if (converted != property.getValue()) {
        component.set(propertyName, converted);
      }
      for (Message message : messages) {
        if (elementResult == null) {
          elementResult = componentResult(component, type, elementName, path, elementResults);
        }
        addMessage(elementResult, message);
      }
    }
  }

  private static JsonNode processExpressions(
      JsonNode node, String propertyName, List<Message> messages) {
    if (node.isTextual()) {
      String value = node.textValue();
      Matcher simpleExpression = SIMPLE_JUEL_EXPRESSION.matcher(value);
      if (!STRUCTURAL_PROPERTIES.contains(propertyName)
          && simpleExpression.matches()
          && isSafeVariableName(simpleExpression.group(1))) {
        String feelExpression = "= " + simpleExpression.group(1);
        messages.add(MessageFactory.formExpressionTransformed(value, feelExpression, propertyName));
        return TextNode.valueOf(feelExpression);
      }
      Matcher juelExpression = JUEL_EXPRESSION.matcher(value);
      while (juelExpression.find()) {
        messages.add(MessageFactory.formJuelExpression(juelExpression.group(), propertyName));
      }
      return node;
    }
    if (node instanceof ObjectNode objectNode) {
      List<String> fieldNames = new ArrayList<>();
      objectNode.fieldNames().forEachRemaining(fieldNames::add);
      for (String fieldName : fieldNames) {
        objectNode.set(
            fieldName, processExpressions(objectNode.get(fieldName), propertyName, messages));
      }
    } else if (node instanceof ArrayNode arrayNode) {
      for (int index = 0; index < arrayNode.size(); index++) {
        arrayNode.set(index, processExpressions(arrayNode.get(index), propertyName, messages));
      }
    }
    return node;
  }

  private static boolean isSafeVariableName(String variableName) {
    return !CAMUNDA_CONTEXT_VARIABLES.contains(variableName)
        && !FEEL_RESERVED_WORDS.contains(variableName);
  }

  private static ElementCheckResult componentResult(
      ObjectNode component,
      String type,
      String elementName,
      String path,
      Map<String, ElementCheckResult> elementResults) {
    String id = componentId(component);
    return elementResults.computeIfAbsent(
        FormChecker.FORM_ELEMENT_TYPE + ":" + path,
        key -> createElementResult(id, type != null ? type : "unknown", elementName));
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
