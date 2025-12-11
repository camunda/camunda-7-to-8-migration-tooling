/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl;

import static io.camunda.migration.data.constants.MigratorConstants.LEGACY_ID_VAR_NAME;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.impl.clients.C8Client;
import io.camunda.migration.data.impl.model.ActivityVariables;
import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableInvocation;
import io.camunda.migration.data.interceptor.VariableTypeDetector;
import io.camunda.migration.data.impl.logging.VariableServiceLogs;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.runtime.VariableInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Centralized service for handling all variable-related operations in the RuntimeMigrator.
 * This service consolidates variable retrieval, transformation, and management logic
 * that was previously scattered across multiple classes.
 */
@Service
public class VariableService {

  @Autowired
  protected C7Client c7Client;

  @Autowired
  protected C8Client c8Client;

  @Autowired(required = false)
  protected List<VariableInterceptor> configuredVariableInterceptors;

  /**
   * Retrieves and processes all variables for a process instance, including global variables
   * with the legacyId added.
   *
   * @param c7ProcessInstanceId the C7 process instance ID
   * @return processed global variables ready for C8 process instance creation
   */
  public Map<String, Object> getGlobalVariables(String c7ProcessInstanceId) {
    ActivityVariables activityVariables = processVariablesToActivityGroups(c7Client.getAllVariables(c7ProcessInstanceId));
    Map<String, Object> globalVariables = activityVariables.getGlobalVariables(c7ProcessInstanceId);

    // Add legacyId for tracking purposes
    globalVariables.put(LEGACY_ID_VAR_NAME, c7ProcessInstanceId);

    return globalVariables;
  }

  /**
   * Retrieves and processes local variables for an activity instance.
   *
   * @param activityInstanceId the activity instance ID
   * @param subProcessInstanceId optional subprocess instance ID to include as C7 ID
   * @return processed local variables
   */
  public Map<String, Object> getLocalVariables(String activityInstanceId,
                                               String subProcessInstanceId) {
    Map<String, Object> localVariables = processVariablesToMapSingleActivity(c7Client.getLocalVariables(activityInstanceId));

    // Add legacyId for subprocess tracking if present
    if (subProcessInstanceId != null) {
      localVariables.put(LEGACY_ID_VAR_NAME, subProcessInstanceId);
    }

    return localVariables;
  }

  /**
   * Checks if a job was started externally (not through migration) by verifying
   * the presence of the legacyId variable.
   *
   * @param job the activated job to check
   * @return true if the job was started externally, false if it's a migrated job
   */
  public boolean isExternallyStartedJob(ActivatedJob job) {
    return !job.getVariables().contains(LEGACY_ID_VAR_NAME);
  }

  /**
   * Retrieves the legacyId from a job's variables.
   *
   * @param job the activated job
   * @return the legacyId from the job variables
   */
  public String getC7IdFromJob(ActivatedJob job) {
    return (String) c8Client.getJobVariable(job, LEGACY_ID_VAR_NAME);
  }

  /**
   * Checks if there are any configured variable interceptors.
   *
   * @return true if interceptors are configured, false otherwise
   */
  protected boolean hasInterceptors() {
    return configuredVariableInterceptors != null && !configuredVariableInterceptors.isEmpty();
  }

  /**
   * Processes a list of variable instances and converts them to ActivityVariables record.
   * This provides a more readable way to work with variables grouped by activity.
   *
   * @param variables the list of variable instances to process
   * @return ActivityVariables containing variables grouped by activity instance
   */
  public ActivityVariables processVariablesToActivityGroups(List<VariableInstance> variables) {
    Map<String, Map<String, Object>> result = new HashMap<>();

    for (VariableInstance variable : variables) {
      VariableInvocation variableInvocation = new VariableInvocation((VariableInstanceEntity) variable);
      executeInterceptors(variableInvocation);

      String activityInstanceId = variable.getActivityInstanceId();
      Map<String, Object> variableMap = result.computeIfAbsent(activityInstanceId, k -> new HashMap<>());
      variableMap.put(variableInvocation.getMigrationVariable().getName(), variableInvocation.getMigrationVariable().getValue());
    }

    return new ActivityVariables(result);
  }

  /**
   * Processes a list of variable instances and converts them to a single variable map.
   * This method applies all configured interceptors during processing.
   *
   * @param variables the list of variable instances to process
   * @return map of variable name to value pairs
   */
  public Map<String, Object> processVariablesToMapSingleActivity(List<VariableInstance> variables) {
    Map<String, Object> variableResult = new HashMap<>();

    for (VariableInstance variable : variables) {
      VariableInvocation variableInvocation = new VariableInvocation((VariableInstanceEntity) variable);
      executeInterceptors(variableInvocation);

      variableResult.put(variableInvocation.getMigrationVariable().getName(),
          variableInvocation.getMigrationVariable().getValue());
    }

    return variableResult;
  }

  /**
   * Executes all configured variable interceptors on the given variable invocation.
   * Only interceptors that support the variable's type will be called.
   *
   * @param variableInvocation the variable invocation to process
   * @throws VariableInterceptorException if any interceptor fails
   */
  protected void executeInterceptors(VariableInvocation variableInvocation) {
    if (hasInterceptors()) {
      for (VariableInterceptor interceptor : configuredVariableInterceptors) {
        // Only execute interceptors that support this variable type using Camunda's native types
        if (VariableTypeDetector.supportsVariable(interceptor, variableInvocation)) {
          try {
            interceptor.execute(variableInvocation);
          } catch (Exception ex) {
            String interceptorName = interceptor.getClass().getSimpleName();
            String variableName = variableInvocation.getC7Variable().getName();
            VariableServiceLogs.logInterceptorWarn(interceptorName, variableName);

            if (ex instanceof VariableInterceptorException) {
              throw ex;
            } else {
              throw new VariableInterceptorException(VariableServiceLogs.formatInterceptorWarn(interceptorName, variableName), ex);
            }
          }
        }
      }
    }
  }

}
