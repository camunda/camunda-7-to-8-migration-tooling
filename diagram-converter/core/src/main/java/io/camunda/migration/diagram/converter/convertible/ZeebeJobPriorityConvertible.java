/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.convertible;

/**
 * Marks a convertible whose target Camunda 8 element accepts {@code <zeebe:jobPriorityDefinition>}
 * inside its {@code extensionElements}. Implemented by the small set of convertibles that map onto
 * job-worker-priority-bearing C8 elements (process and service-task-style activities).
 */
public interface ZeebeJobPriorityConvertible extends Convertible {
  ZeebeJobPriorityDefinition getZeebeJobPriorityDefinition();

  class ZeebeJobPriorityDefinition {
    private String priority;

    public String getPriority() {
      return priority;
    }

    public void setPriority(String priority) {
      this.priority = priority;
    }
  }
}
