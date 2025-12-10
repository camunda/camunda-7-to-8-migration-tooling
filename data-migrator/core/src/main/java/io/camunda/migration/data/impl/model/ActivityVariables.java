/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents variables grouped by activity instance ID.
 * This provides a more readable alternative to Map<String, Map<String, Object>>.
 */
public class ActivityVariables {

  protected Map<String, Map<String, Object>> variablesByActivity;

  public ActivityVariables(Map<String, Map<String, Object>> variablesByActivity) {
    this.variablesByActivity = variablesByActivity;
  }

  /**
   * Gets variables for a specific activity instance.
   *
   * @param activityInstanceId the activity instance ID
   * @return map of variable names to values for the activity, or empty map if not found
   */
  public Map<String, Object> getVariablesForActivity(String activityInstanceId) {
    return variablesByActivity.getOrDefault(activityInstanceId, new HashMap<>());
  }

  /**
   * Gets the global variables (variables at the process instance level).
   *
   * @param processInstanceId the process instance ID
   * @return map of global variable names to values, or empty map if not found
   */
  public Map<String, Object> getGlobalVariables(String processInstanceId) {
    return getVariablesForActivity(processInstanceId);
  }

  public Map<String, Map<String, Object>> variablesByActivity() {
    return variablesByActivity;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this)
      return true;
    if (obj == null || obj.getClass() != this.getClass())
      return false;
    var that = (ActivityVariables) obj;
    return Objects.equals(this.variablesByActivity, that.variablesByActivity);
  }

  @Override
  public int hashCode() {
    return Objects.hash(variablesByActivity);
  }

  @Override
  public String toString() {
    return "ActivityVariables[" + "variablesByActivity=" + variablesByActivity + ']';
  }

}
