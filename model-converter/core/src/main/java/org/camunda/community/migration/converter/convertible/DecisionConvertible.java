/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.convertible;

public class DecisionConvertible extends AbstractDmnConvertible
    implements ZeebeVersionTagConvertible {
  private String zeebeVersionTag;

  @Override
  public String getZeebeVersionTag() {
    return zeebeVersionTag;
  }

  @Override
  public void setZeebeVersionTag(String zeebeVersionTag) {
    this.zeebeVersionTag = zeebeVersionTag;
  }
}
