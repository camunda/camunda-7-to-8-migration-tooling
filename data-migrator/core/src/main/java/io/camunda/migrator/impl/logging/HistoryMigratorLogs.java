/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.impl.logging;

import io.camunda.migrator.HistoryMigrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for HistoryMigrator.
 * Contains all log messages and string constants used in HistoryMigrator.
 */
public class HistoryMigratorLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(HistoryMigrator.class);

  // Skip reason constants
  public static final String SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE = "Missing parent process instance";
  public static final String SKIP_REASON_MISSING_PROCESS_DEFINITION = "Missing process definition";
  public static final String SKIP_REASON_MISSING_PROCESS_INSTANCE_KEY = "Missing process instance key";
  public static final String SKIP_REASON_MISSING_PROCESS_INSTANCE = "Missing process instance";
  public static final String SKIP_REASON_BELONGS_TO_SKIPPED_TASK = "Belongs to a skipped task";
  public static final String SKIP_REASON_MISSING_SCOPE_KEY = "Missing scope key";
  public static final String SKIP_REASON_MISSING_FLOW_NODE = "Missing flow node";
  public static final String SKIP_REASON_MISSING_DECISION_REQUIREMENTS = "Missing decision requirements definition";
  public static final String SKIP_REASON_MISSING_DECISION_DEFINITION = "Missing decision definition";
  public static final String SKIP_REASON_MISSING_PARENT_DECISION_INSTANCE = "Missing parent decision instance";

  // HistoryMigrator Messages
  public static final String MIGRATING_DEFINITIONS = "Migrating {} definitions";
  public static final String MIGRATING_DEFINITION = "Migrating {} definition with C7 ID: [{}]";
  public static final String MIGRATING_DEFINITION_COMPLETE = "Migration of {} definition with C7 ID [{}] completed";

  public static final String SKIPPING_DECISION_DEFINITION = "Migration of historic decision definition with C7 ID [{}] skipped. Decision requirements definition not yet available.";

  public static final String MIGRATING_INSTANCES = "Migrating historic {} instances";
  public static final String MIGRATING_INSTANCE = "Migrating historic {} instance with C7 ID: [{}]";
  public static final String MIGRATING_INSTANCE_COMPLETE =
      "Migration of historic {} instance with C7 ID " + "[{}] completed";
  public static final String SKIPPING_INSTANCE_MISSING_PARENT = "Migration of historic {} instance with C7 ID [{}] skipped. Parent instance not yet available.";
  public static final String SKIPPING_INSTANCE_MISSING_DEFINITION = "Migration of historic {} instance with C7 ID [{}] skipped. {} definition not yet available.";
  public static final String SKIPPING_DECISION_INSTANCE_MISSING_PROCESS_INSTANCE = "Migration of historic decision instance with C7 ID [{}] skipped. Process instance not yet available.";
  public static final String SKIPPING_DECISION_INSTANCE_MISSING_FLOW_NODE_INSTANCE = "Migration of historic decision "
      + "instance with C7 ID [{}] skipped. Flow node instance not yet available.";
  public static final String NOT_MIGRATING_DECISION_INSTANCE = "Not migrating historic decision instance with "
      + "C7 ID: [{}] because it does not originate from a business rule task.";

  public static final String MIGRATING_INCIDENTS = "Migrating historic incidents";
  public static final String MIGRATING_INCIDENT = "Migrating historic incident with C7 ID: [{}]";
  public static final String MIGRATING_INCIDENT_COMPLETED = "Migration of historic incident with C7 ID [{}] completed.";
  public static final String SKIPPING_INCIDENT = "Migration of historic incident with C7 ID [{}] skipped. Process "
      + "instance not yet available.";

  public static final String MIGRATING_VARIABLES = "Migrating historic variables";
  public static final String MIGRATING_VARIABLE = "Migrating historic variables with C7 ID: [{}]";
  public static final String MIGRATING_VARIABLE_COMPLETED = "Migration of historic variable with C7 ID [{}] completed.";
  public static final String SKIPPING_VARIABLE = "Migration of historic variable with C7 ID [{}] skipped.";
  public static final String SKIPPING_VARIABLE_MISSING_FLOW_NODE = SKIPPING_VARIABLE + " Flow node instance not yet available.";
  public static final String SKIPPING_VARIABLE_MISSING_PROCESS = SKIPPING_VARIABLE + " Process instance not yet available.";
  public static final String SKIPPING_VARIABLE_MISSING_TASK = SKIPPING_VARIABLE + " Associated task [{}] was skipped.";
  public static final String SKIPPING_VARIABLE_MISSING_SCOPE = SKIPPING_VARIABLE + " Scope key is not yet available.";

  public static final String MIGRATING_USER_TASKS = "Migrating historic user tasks";
  public static final String MIGRATING_USER_TASK = "Migrating historic user task with C7 ID: [{}]";
  public static final String MIGRATING_USER_TASK_COMPLETED = "Migration of historic user task with C7 ID [{}] completed.";
  public static final String SKIPPING_MIGRATING_USER_TASK_MISSING_FLOW_NODE = "Migration of historic user task with C7 ID [{}] skipped. Flow node instance yet not available.";
  public static final String SKIPPING_USER_TASK_MISSING_PROCESS = "Migration of historic user task with C7 ID [{}] skipped. Process instance yet not available.";

  public static final String MIGRATING_FLOW_NODES = "Migrating historic flow nodes";
  public static final String MIGRATING_FLOW_NODE = "Migrating historic flow nodes with C7 ID: [{}]";
  public static final String MIGRATING_FLOW_NODE_COMPLETED = "Migration of historic flow nodes with C7 ID [{}] completed.";
  public static final String SKIPPING_FLOW_NODE = "Migration of historic flow nodes with C7 ID [{}] skipped. Process instance yet not available.";

  public static final String MIGRATING_DECISION_REQUIREMENTS = "Migrating decision requirements";
  public static final String MIGRATING_DECISION_REQUIREMENT = "Migrating decision requirements with C7 ID: [{}]";
  public static final String MIGRATING_DECISION_REQUIREMENT_COMPLETED = "Migration of decision requirements with C7 ID [{}] completed.";

  public static void migratingDecisionDefinitions() {
    LOGGER.info(MIGRATING_DEFINITIONS, "decision");
  }

  public static void migratingDecisionDefinition(String c7DecisionDefinitionId) {
    LOGGER.debug(MIGRATING_DEFINITION, "decision", c7DecisionDefinitionId);
  }

  public static void migratingDecisionDefinitionCompleted(String c7DecisionDefinitionId) {
    LOGGER.debug(MIGRATING_DEFINITION_COMPLETE, "decision", c7DecisionDefinitionId);
  }

  public static void skippingDecisionDefinition(String c7DecisionDefinitionId) {
    LOGGER.debug(SKIPPING_DECISION_DEFINITION, c7DecisionDefinitionId);
  }

  public static void migratingProcessDefinitions() {
    LOGGER.info(MIGRATING_DEFINITIONS, "process");
  }

  public static void migratingProcessDefinition(String c7ProcessDefinitionId) {
    LOGGER.debug(MIGRATING_DEFINITION, "process", c7ProcessDefinitionId);
  }

  public static void migratingProcessDefinitionCompleted(String c7ProcessDefinitionId) {
    LOGGER.debug(MIGRATING_DEFINITION_COMPLETE, "process", c7ProcessDefinitionId);
  }

  public static void migratingProcessInstances() {
    LOGGER.info(MIGRATING_INSTANCES, "process");
  }

  public static void migratingProcessInstance(String c7ProcessInstanceId) {
    LOGGER.debug(MIGRATING_INSTANCE, "process", c7ProcessInstanceId);
  }

  public static void migratingProcessInstanceCompleted(String c7ProcessInstanceId) {
    LOGGER.debug(MIGRATING_INSTANCE_COMPLETE, "process", c7ProcessInstanceId);
  }

  public static void skippingProcessInstanceDueToMissingParent(String c7ProcessInstanceId) {
    LOGGER.debug(SKIPPING_INSTANCE_MISSING_PARENT, "process", c7ProcessInstanceId);
  }

  public static void skippingProcessInstanceDueToMissingDefinition(String c7ProcessInstanceId) {
    LOGGER.debug(SKIPPING_INSTANCE_MISSING_DEFINITION, "process", c7ProcessInstanceId, "process");
  }

  public static void migratingDecisionInstances() {
    LOGGER.info(MIGRATING_INSTANCES, "decision");
  }

  public static void notMigratingDecisionInstancesNotOriginatingFromBusinessRuleTasks(String c7DecisionInstanceId) {
    LOGGER.debug(NOT_MIGRATING_DECISION_INSTANCE, c7DecisionInstanceId);
  }

  public static void migratingDecisionInstance(String c7DecisionInstanceId) {
    LOGGER.debug(MIGRATING_INSTANCE, "decision", c7DecisionInstanceId);
  }

  public static void migratingDecisionInstanceCompleted(String c7DecisionInstanceId) {
    LOGGER.debug(MIGRATING_INSTANCE_COMPLETE, "decision", c7DecisionInstanceId);
  }

  public static void skippingDecisionInstanceDueToMissingParent(String c7DecisionInstanceId) {
    LOGGER.debug(SKIPPING_INSTANCE_MISSING_PARENT, "decision", c7DecisionInstanceId);
  }

  public static void skippingDecisionInstanceDueToMissingDecisionDefinition(String c7DecisionInstanceId) {
    LOGGER.debug(SKIPPING_INSTANCE_MISSING_DEFINITION, "decision", c7DecisionInstanceId, "decision");
  }

  public static void skippingDecisionInstanceDueToMissingProcessDefinition(String c7DecisionInstanceId) {
    LOGGER.debug(SKIPPING_INSTANCE_MISSING_DEFINITION, "decision", c7DecisionInstanceId, "process");
  }

  public static void skippingDecisionInstanceDueToMissingProcessInstance(String c7DecisionInstanceId) {
    LOGGER.debug(SKIPPING_DECISION_INSTANCE_MISSING_PROCESS_INSTANCE, c7DecisionInstanceId);
  }

  public static void skippingDecisionInstanceDueToMissingFlowNodeInstanceInstance(String c7DecisionInstanceId) {
    LOGGER.debug(SKIPPING_DECISION_INSTANCE_MISSING_FLOW_NODE_INSTANCE, c7DecisionInstanceId);
  }

  public static void migratingHistoricIncidents() {
    LOGGER.info(MIGRATING_INCIDENTS);
  }

  public static void migratingHistoricIncident(String c7IncidentId) {
    LOGGER.debug(MIGRATING_INCIDENT, c7IncidentId);
  }

  public static void migratingHistoricIncidentCompleted(String c7IncidentId) {
    LOGGER.debug(MIGRATING_INCIDENT_COMPLETED, c7IncidentId);
  }

  public static void skippingHistoricIncident(String c7IncidentId) {
    LOGGER.debug(SKIPPING_INCIDENT, c7IncidentId);
  }

  public static void migratingHistoricVariables() {
    LOGGER.info(MIGRATING_VARIABLES);
  }

  public static void migratingHistoricVariable(String c7VariableId) {
    LOGGER.debug(MIGRATING_VARIABLE, c7VariableId);
  }

  public static void migratingHistoricVariableCompleted(String c7VariableId) {
    LOGGER.debug(MIGRATING_VARIABLE_COMPLETED, c7VariableId);
  }

  public static void skippingHistoricVariableDueToMissingFlowNode(String c7VariableId) {
    LOGGER.debug(SKIPPING_VARIABLE_MISSING_FLOW_NODE, c7VariableId);
  }

  public static void skippingHistoricVariableDueToMissingProcessInstance(String c7VariableId) {
    LOGGER.debug(SKIPPING_VARIABLE_MISSING_PROCESS, c7VariableId);
  }

  public static void skippingHistoricVariableDueToMissingTask(String c7VariableId, String taskId) {
    LOGGER.debug(SKIPPING_VARIABLE_MISSING_TASK, c7VariableId, taskId);
  }

  public static void skippingHistoricVariableDueToMissingScopeKey(String c7VariableId) {
    LOGGER.debug(SKIPPING_VARIABLE_MISSING_SCOPE, c7VariableId);
  }

  public static void migratingHistoricUserTasks() {
    LOGGER.info(MIGRATING_USER_TASKS);
  }

  public static void migratingHistoricUserTask(String c7UserTaskId) {
    LOGGER.debug(MIGRATING_USER_TASK, c7UserTaskId);
  }

  public static void migratingHistoricUserTaskCompleted(String c7UserTaskId) {
    LOGGER.debug(MIGRATING_USER_TASK_COMPLETED, c7UserTaskId);
  }

  public static void skippingHistoricUserTaskDueToMissingFlowNode(String c7UserTaskId) {
    LOGGER.debug(SKIPPING_MIGRATING_USER_TASK_MISSING_FLOW_NODE, c7UserTaskId);
  }

  public static void skippingHistoricUserTaskDueToMissingProcessInstance(String c7UserTaskId) {
    LOGGER.debug(SKIPPING_USER_TASK_MISSING_PROCESS, c7UserTaskId);
  }

  public static void migratingHistoricFlowNodes() {
    LOGGER.info(MIGRATING_FLOW_NODES);
  }

  public static void migratingHistoricFlowNode(String c7FlowNodeId) {
    LOGGER.debug(MIGRATING_FLOW_NODE, c7FlowNodeId);
  }

  public static void migratingHistoricFlowNodeCompleted(String c7FlowNodeId) {
    LOGGER.debug(MIGRATING_FLOW_NODE_COMPLETED, c7FlowNodeId);
  }

  public static void skippingHistoricFlowNode(String c7FlowNodeId) {
    LOGGER.debug(SKIPPING_FLOW_NODE, c7FlowNodeId);
  }

  public static void migratingDecisionRequirements() {
    LOGGER.info(MIGRATING_DECISION_REQUIREMENTS);
  }

  public static void migratingDecisionRequirements(String c7DecisionRequirementsId) {
    LOGGER.debug(MIGRATING_DECISION_REQUIREMENT, c7DecisionRequirementsId);
  }

  public static void migratingDecisionRequirementsCompleted(String c7DecisionRequirementsId) {
    LOGGER.debug(MIGRATING_DECISION_REQUIREMENT_COMPLETED, c7DecisionRequirementsId);
  }

  public static void batchFlushFailed(String message) {
    LOGGER.error("Batch flush failed during history migration: {}", message);
  }
}
