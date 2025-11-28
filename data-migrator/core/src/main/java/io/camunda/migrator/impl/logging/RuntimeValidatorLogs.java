/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.logging;

import io.camunda.migrator.impl.RuntimeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs debug messages for RuntimeValidator operations
 */
public class RuntimeValidatorLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(RuntimeValidator.class);

  // RuntimeValidator Messages
  public static final String VALIDATE_C7_PROCESS_INSTANCE = "Validate C7 process instance by ID: {}";
  public static final String COLLECTING_ACTIVE_DESCENDANT_ACTIVITIES_VALIDATION = "Collecting active descendant activity instances for C7 ID [{}]";
  public static final String FOUND_ACTIVE_ACTIVITIES_TO_VALIDATE = "Found {} active activity instances to validate";
  public static final String JOB_TYPE_VALIDATION_DISABLED = "Job type validation is disabled, skipping execution listener validation";

  // RuntimeValidator Error Messages
  public static final String MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR = "Found multi-instance loop characteristics for flow node with id [%s] in C7 process instance.";
  public static final String NO_NONE_START_EVENT_ERROR = "C8 process definition [id: %s, version: %d] should have a None Start Event.";
  public static final String NO_EXECUTION_LISTENER_OF_TYPE_ERROR = "No execution listener of type '%s' found on start event [%s] for C8 process definition [id: %s, version: %d]. At least one '%s' listener is required.";
  public static final String FLOW_NODE_NOT_EXISTS_ERROR = "Flow node with id [%s] doesn't exist in the equivalent deployed C8 model.";
  public static final String NO_C8_DEPLOYMENT_ERROR = "No C8 deployment found for process ID [%s] required for instance with C7 ID [%s].";
  public static final String NO_C8_TENANT_DEPLOYMENT_ERROR = "No C8 deployment found for process ID [%s] and tenant [%s] required for instance with C7 ID [%s].";
  public static final String FAILED_TO_PARSE_BPMN_MODEL = "Failed to parse BPMN model from XML";
  public static final String CALL_ACTIVITY_LEGACY_ID_ERROR = "Found call activity with propagateAllParentVariables=false for flow node with id [%s] in C8 process. This is not supported by the migrator unless there is an explicit mapping for the 'legacyId' variable, as it would lead to orphaned sub-process instances.";
  public static final String TENANT_ID_ERROR = "Found a process instance with tenant id [%s] that is not configured for migration.";
  public static final String ACTIVE_JOINING_PARALLEL_GATEWAY_ERROR = "Found active joining parallel gateway with id [%s] for process instance [%s]. This is currently not supported by the migrator.";

  public static void validateC7ProcessInstance(String c7ProcessInstanceId) {
    LOGGER.debug(VALIDATE_C7_PROCESS_INSTANCE, c7ProcessInstanceId);
  }

  public static void collectingActiveDescendantActivitiesValidation(String processInstanceId) {
    LOGGER.debug(COLLECTING_ACTIVE_DESCENDANT_ACTIVITIES_VALIDATION, processInstanceId);
  }

  public static void foundActiveActivitiesToValidate(int size) {
    LOGGER.debug(FOUND_ACTIVE_ACTIVITIES_TO_VALIDATE, size);
  }

  public static void jobTypeValidationDisabled() {
    LOGGER.debug(JOB_TYPE_VALIDATION_DISABLED);
  }
}
