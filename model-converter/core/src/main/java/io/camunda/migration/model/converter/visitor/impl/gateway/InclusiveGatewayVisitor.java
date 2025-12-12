/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor.impl.gateway;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.NamespaceUri;
import io.camunda.migration.model.converter.convertible.Convertible;
import io.camunda.migration.model.converter.convertible.InclusiveGatewayConvertible;
import io.camunda.migration.model.converter.message.Message;
import io.camunda.migration.model.converter.message.MessageFactory;
import io.camunda.migration.model.converter.version.SemanticVersion;
import io.camunda.migration.model.converter.visitor.AbstractGatewayVisitor;
import org.camunda.bpm.model.xml.instance.DomElement;

public class InclusiveGatewayVisitor extends AbstractGatewayVisitor {
  private static final SemanticVersion FORK_AVAILABLE_VERSION = SemanticVersion._8_1;
  private static final SemanticVersion JOIN_AVAILABLE_VERSION = SemanticVersion._8_6;
  public static final String ELEMENT_LOCAL_NAME = "inclusiveGateway";

  @Override
  public String localName() {
    return ELEMENT_LOCAL_NAME;
  }

  private boolean isNotJoining(DomElement element) {
    return element.getChildElements().stream()
            .filter(e -> e.getNamespaceURI().equals(NamespaceUri.BPMN))
            .filter(e -> e.getLocalName().equals("incoming"))
            .count()
        <= 1;
  }

  @Override
  protected Convertible createConvertible(DomElementVisitorContext context) {
    return new InclusiveGatewayConvertible();
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    if (isNotJoining(context.getElement())) {
      return FORK_AVAILABLE_VERSION;
    }
    return JOIN_AVAILABLE_VERSION;
  }

  @Override
  protected Message cannotBeConvertedMessage(DomElementVisitorContext context) {
    boolean forkAvailable =
        !isNotSupportedInDesiredVersion(
            FORK_AVAILABLE_VERSION,
            SemanticVersion.parse(context.getProperties().getPlatformVersion()));
    if (forkAvailable && !isNotJoining(context.getElement())) {
      return MessageFactory.inclusiveGatewayJoin();
    }
    return super.cannotBeConvertedMessage(context);
  }
}
