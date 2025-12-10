/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.visitor;

import static io.camunda.migration.model.converter.NamespaceUri.*;

import io.camunda.migration.model.converter.DomElementVisitorContext;
import io.camunda.migration.model.converter.version.SemanticVersion;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractDmnElementVisitor extends AbstractElementVisitor {
  @Override
  protected List<String> namespaceUri() {
    return Arrays.asList(DMN);
  }

  @Override
  protected final void visitElement(DomElementVisitorContext context) {
    visitDmnElement(context);
  }

  protected abstract void visitDmnElement(DomElementVisitorContext context);

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return SemanticVersion._8_0;
  }
}
