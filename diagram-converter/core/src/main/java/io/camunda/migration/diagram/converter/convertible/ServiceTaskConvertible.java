/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.convertible;

public class ServiceTaskConvertible extends AbstractActivityConvertible
    implements ZeebeJobPriorityConvertible {
  private final ZeebeTaskDefinition zeebeTaskDefinition = new ZeebeTaskDefinition();
  private final ZeebeJobPriorityDefinition zeebeJobPriorityDefinition =
      new ZeebeJobPriorityDefinition();

  public ZeebeTaskDefinition getZeebeTaskDefinition() {
    return zeebeTaskDefinition;
  }

  @Override
  public ZeebeJobPriorityDefinition getZeebeJobPriorityDefinition() {
    return zeebeJobPriorityDefinition;
  }

  public static class ZeebeTaskDefinition {
    private String type;
    private Integer retries;

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Integer getRetries() {
      return retries;
    }

    public void setRetries(Integer retries) {
      this.retries = retries;
    }
  }
}
