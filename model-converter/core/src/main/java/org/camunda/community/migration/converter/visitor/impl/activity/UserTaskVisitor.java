/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.visitor.impl.activity;

import org.camunda.community.migration.converter.DomElementVisitorContext;
import org.camunda.community.migration.converter.convertible.Convertible;
import org.camunda.community.migration.converter.convertible.UserTaskConvertible;
import org.camunda.community.migration.converter.version.SemanticVersion;
import org.camunda.community.migration.converter.visitor.AbstractActivityVisitor;

public class UserTaskVisitor extends AbstractActivityVisitor {
  @Override
  public String localName() {
    return "userTask";
  }

  @Override
  protected Convertible createConvertible(DomElementVisitorContext context) {
    return new UserTaskConvertible();
  }

  @Override
  protected SemanticVersion availableFrom(DomElementVisitorContext context) {
    return SemanticVersion._8_0;
  }

  @Override
  protected void postCreationVisitor(DomElementVisitorContext context) {
    if (SemanticVersion.parse(context.getProperties().getPlatformVersion()).ordinal()
        >= SemanticVersion._8_5.ordinal()) {
      context.addConversion(UserTaskConvertible.class, c -> c.setZeebeUserTask(true));
    } else {
      context.addConversion(UserTaskConvertible.class, c -> c.setZeebeUserTask(false));
    }
  }
}
