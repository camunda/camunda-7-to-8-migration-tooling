/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents variables grouped by activity instance ID.
 * This provides a more readable alternative to Map<String, Map<String, Object>>.
 */
public record ActivityVariables(Map<String, Map<String, Object>> variablesByActivity) {

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

}
