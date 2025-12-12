/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl;

import static io.camunda.migration.model.converter.NamespaceUri.*;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.NamespaceUri;
import io.camunda.migration.model.converter.version.SemanticVersion;
import io.camunda.migration.model.converter.visitor.AbstractDmnElementVisitor;
import org.camunda.bpm.model.xml.instance.DomElement;

public class DmnDefinitionsVisitor extends AbstractDmnElementVisitor {
  private static final String VERSION_HEADER = "executionPlatformVersion";
  private static final String PLATFORM_HEADER = "executionPlatform";
  private static final String CONVERTER_VERSION_HEADER = "converterVersion";
  private static final String PLATFORM_VALUE = "Camunda Cloud";
  private static final String ZEEBE_NAMESPACE_NAME = "zeebe";
  private static final String CONVERSION_NAMESPACE_NAME = "conversion";
  private static final String MODELER_NAMESPACE_NAME = "modeler";

  @Override
  protected void visitDmnElement(DomElementVisitorContext context) {
    SemanticVersion desiredVersion =
        SemanticVersion.parse(context.getProperties().getPlatformVersion());
    DomElement element = context.getElement();
    String executionPlatform = element.getAttribute(NamespaceUri.MODELER, VERSION_HEADER);
    if (executionPlatform != null && executionPlatform.startsWith("8")) {
      throw new RuntimeException("This diagram is already a Camunda 8 diagram");
    }
    element.registerNamespace(MODELER_NAMESPACE_NAME, NamespaceUri.MODELER);
    element.registerNamespace(ZEEBE_NAMESPACE_NAME, NamespaceUri.ZEEBE);
    element.registerNamespace(CONVERSION_NAMESPACE_NAME, CONVERSION);
    element.setAttribute(NamespaceUri.MODELER, PLATFORM_HEADER, PLATFORM_VALUE);
    element.setAttribute(
        NamespaceUri.MODELER, VERSION_HEADER, desiredVersion.getPatchZeroVersion());
    element.setAttribute(
        CONVERSION, CONVERTER_VERSION_HEADER, getClass().getPackage().getImplementationVersion());
  }

  @Override
  public String localName() {
    return "definitions";
  }
}
