/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.visitor.impl.element;

import io.camunda.migration.diagram.converter.DomElementVisitorContext;
import io.camunda.migration.diagram.converter.NamespaceUri;
import io.camunda.migration.diagram.converter.message.EmptyMessage;
import io.camunda.migration.diagram.converter.message.Message;
import io.camunda.migration.diagram.converter.message.MessageFactory;
import io.camunda.migration.diagram.converter.version.SemanticVersion;
import io.camunda.migration.diagram.converter.visitor.AbstractCamundaElementVisitor;
import java.util.List;
import org.camunda.bpm.model.xml.instance.DomElement;

public abstract class FieldContentVisitor extends AbstractCamundaElementVisitor {

  @Override
  protected Message visitCamundaElement(DomElementVisitorContext context) {
    if (isStaticExecutionListenerField(context)) {
      return new EmptyMessage();
    }
    return MessageFactory.fieldContent(context.getElement().getLocalName());
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    if ("field".equals(context.getElement().getLocalName())
        && isStaticExecutionListenerField(context)) {
      return SemanticVersion._8_10;
    }
    return super.availableFrom(context);
  }

  @Override
  public boolean canBeTransformed(DomElementVisitorContext context) {
    return isStaticExecutionListenerField(context)
        && SemanticVersion.parse(context.getProperties().getPlatformVersion()).ordinal()
            >= SemanticVersion._8_10.ordinal();
  }

  private boolean isStaticExecutionListenerField(DomElementVisitorContext context) {
    DomElement field = findField(context.getElement());
    return field != null && isStaticExecutionListenerField(field);
  }

  static boolean isStaticExecutionListenerField(DomElement field) {
    if (!isSupportedExecutionListener(field)) {
      return false;
    }
    String name = field.getAttribute("name");
    return name != null
        && !name.isBlank()
        && (field.hasAttribute("stringValue") || hasNestedStringValue(field));
  }

  static String getStaticExecutionListenerFieldValue(DomElement field) {
    if (field.hasAttribute("stringValue")) {
      return field.getAttribute("stringValue");
    }
    return field.getChildElements().get(0).getTextContent();
  }

  private static boolean hasNestedStringValue(DomElement field) {
    List<DomElement> children = field.getChildElements();
    return children.size() == 1
        && "string".equals(children.get(0).getLocalName())
        && NamespaceUri.CAMUNDA.equals(children.get(0).getNamespaceURI());
  }

  private static DomElement findField(DomElement element) {
    if ("field".equals(element.getLocalName())) {
      return element;
    }
    DomElement parent = element.getParentElement();
    return parent != null && "field".equals(parent.getLocalName()) ? parent : null;
  }

  private static boolean isSupportedExecutionListener(DomElement field) {
    DomElement listener = field.getParentElement();
    return listener != null
        && NamespaceUri.CAMUNDA.equals(listener.getNamespaceURI())
        && "executionListener".equals(listener.getLocalName())
        && ("start".equals(listener.getAttribute("event"))
            || "end".equals(listener.getAttribute("event")));
  }

  public static class FieldVisitor extends FieldContentVisitor {
    @Override
    public String localName() {
      return "field";
    }
  }

  public static class ExpressionVisitor extends FieldContentVisitor {
    @Override
    public String localName() {
      return "expression";
    }
  }

  public static class StringVisitor extends FieldContentVisitor {
    @Override
    public String localName() {
      return "string";
    }
  }
}
