/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.element;

import static io.camunda.migration.diagram.converter.NamespaceUri.*;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.convertible.AbstractDataMapperConvertible;
import io.camunda.migration.diagram.converter.convertible.AbstractDataMapperConvertible.MappingDirection;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResult;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformationResultMessageFactory;
import io.camunda.migration.diagram.converter.expression.ExpressionTransformer;
import io.camunda.migration.diagram.converter.expression.FeelReservedWords;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.visitor.AbstractCamundaElementVisitor;
import java.util.Set;
import java.util.regex.Pattern;
import org.camunda.bpm.model.xml.instance.DomElement;

public abstract class InputOutputParameterVisitor extends AbstractCamundaElementVisitor {
  public static final String INPUT_PARAMETER = "inputParameter";
  public static final String OUTPUT_PARAMETER = "outputParameter";
  private static final Pattern FEEL_NUMBER_PATTERN =
      Pattern.compile("-?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)");
  private static final Pattern FEEL_ESCAPED_IDENTIFIER_PATTERN = Pattern.compile("`[^`]+`");
  private static final Set<String> FEEL_LITERAL_VALUES = Set.of("false", "null", "true");

  @Override
  public boolean canBeTransformed(DomElementVisitorContext context) {
    return !isNotStringOrExpression(context.getElement());
  }

  @Override
  protected Message visitCamundaElement(DomElementVisitorContext context) {
    DomElement element = context.getElement();
    String name = element.getAttribute("name");
    MappingDirection direction = findMappingDirection(element);
    if (isScript(element)) {
      // Scripts are handled in ScriptVisitor
      return MessageFactory.inputOutputScript();
    }
    if (isNotStringOrExpression(element)) {
      return MessageFactory.inputOutputParameterIsNoExpression(localName(), name);
    }
    String expression = element.getTextContent();
    ExpressionTransformationResult transformationResult =
        ExpressionTransformer.transformToFeel(
            direction.getName() + " parameter '" + name + "'", expression);
    // Zeebe I/O mapping source is normally FEEL (prefixed with '='). For expressions that cannot
    // be converted to FEEL, keep the original JUEL wrapper (${...}/#{...}) for manual follow-up.
    String source = transformationResult.result();
    // The transformer leaves unwrapped static values unchanged, but an I/O mapping requires FEEL.
    boolean staticValue = isStaticValue(expression, source);
    if (staticValue && !isValidFeelValue(source.strip())) {
      source = toFeelStringLiteral(source);
    }
    if (shouldPrefixFeelMarker(source, transformationResult, staticValue)) {
      source = "=" + source;
    }
    String finalSource = source;
    context.addConversion(
        AbstractDataMapperConvertible.class,
        abstractTaskConversion ->
            abstractTaskConversion.addZeebeIoMapping(direction, finalSource, name));
    if (staticValue) {
      return MessageFactory.noExpressionTransformation();
    }
    return ExpressionTransformationResultMessageFactory.getMessage(
        transformationResult,
        "https://docs.camunda.io/docs/components/concepts/variables/#inputoutput-variable-mappings");
  }

  private boolean shouldPrefixFeelMarker(
      String source, ExpressionTransformationResult transformationResult, boolean staticValue) {
    if (source == null || source.startsWith("=")) {
      return false;
    }
    // Keep original JUEL wrapper intact for unconvertible expressions to support manual follow-up.
    return staticValue
        || !(transformationResult.hasMethodInvocation() || transformationResult.hasExecutionOnly());
  }

  private boolean isStaticValue(String expression, String source) {
    return source != null
        && source.equals(expression)
        && !expression.contains("${")
        && !expression.contains("#{");
  }

  private boolean isValidFeelValue(String source) {
    return FEEL_LITERAL_VALUES.contains(source)
        || FEEL_NUMBER_PATTERN.matcher(source).matches()
        || isFeelStringLiteral(source)
        || isFeelIdentifier(source);
  }

  private boolean isFeelIdentifier(String value) {
    if (FEEL_ESCAPED_IDENTIFIER_PATTERN.matcher(value).matches()) {
      return true;
    }
    if (value.isEmpty() || FeelReservedWords.isReservedWord(value)) {
      return false;
    }
    int offset = 0;
    int codePoint = value.codePointAt(offset);
    if (!Character.isJavaIdentifierStart(codePoint)) {
      return false;
    }
    offset += Character.charCount(codePoint);
    while (offset < value.length()) {
      codePoint = value.codePointAt(offset);
      if (!Character.isJavaIdentifierPart(codePoint) || Character.isWhitespace(codePoint)) {
        return false;
      }
      offset += Character.charCount(codePoint);
    }
    return true;
  }

  private boolean isFeelStringLiteral(String value) {
    if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
      return false;
    }
    boolean escaped = false;
    for (int i = 1; i < value.length() - 1; i++) {
      char character = value.charAt(i);
      if (escaped) {
        escaped = false;
      } else if (character == '\\') {
        escaped = true;
      } else if (character == '"') {
        return false;
      }
    }
    return !escaped;
  }

  private String toFeelStringLiteral(String value) {
    String escaped =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\f", "\\f");
    return "\"" + escaped + "\"";
  }

  private boolean isScript(DomElement element) {
    return element.getChildElements().stream()
        .anyMatch(e -> e.getNamespaceURI().equals(CAMUNDA) && e.getLocalName().equals("script"));
  }

  private MappingDirection findMappingDirection(DomElement element) {
    if (isInputParameter(element.getLocalName())) {
      return MappingDirection.INPUT;
    }
    if (isOutputParameter(element.getLocalName())) {
      return MappingDirection.OUTPUT;
    }
    throw new IllegalStateException("Must be input or output!");
  }

  private boolean isNotStringOrExpression(DomElement element) {
    return !element.getChildElements().isEmpty();
  }

  private boolean isInputParameter(String localName) {
    return INPUT_PARAMETER.equals(localName);
  }

  private boolean isOutputParameter(String localName) {
    return OUTPUT_PARAMETER.equals(localName);
  }

  public static class InputParameterVisitor extends InputOutputParameterVisitor {

    @Override
    public String localName() {
      return INPUT_PARAMETER;
    }
  }

  public static class OutputParameterVisitor extends InputOutputParameterVisitor {

    @Override
    public String localName() {
      return OUTPUT_PARAMETER;
    }
  }
}
