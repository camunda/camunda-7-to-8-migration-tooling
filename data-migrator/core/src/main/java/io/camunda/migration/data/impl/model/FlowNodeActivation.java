/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.model;

import java.util.Map;
import java.util.Objects;

/**
 * Represents the activation data for a specific activity during process instance migration.
 * Contains the variables that should be applied when activating the activity.
 */
public class FlowNodeActivation {
  protected String activityId;
  protected Map<String, Object> variables;

  public FlowNodeActivation(String activityId, Map<String, Object> variables) {
    this.activityId = activityId;
    this.variables = variables;
  }

  public String activityId() {
    return activityId;
  }

  public Map<String, Object> variables() {
    return variables;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this)
      return true;
    if (obj == null || obj.getClass() != this.getClass())
      return false;
    var that = (FlowNodeActivation) obj;
    return Objects.equals(this.activityId, that.activityId) && Objects.equals(this.variables, that.variables);
  }

  @Override
  public int hashCode() {
    return Objects.hash(activityId, variables);
  }

  @Override
  public String toString() {
    return "FlowNodeActivation[" + "activityId=" + activityId + ", " + "variables=" + variables + ']';
  }

}
