/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.impl.logging;

import static io.camunda.migration.data.impl.persistence.IdKeyMapper.HISTORY_TYPE_NAME_MAP;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for HistoryMigrator.
 * Contains all log messages and string constants used in HistoryMigrator.
 */
public class HistoryMigratorLogs {

  public static final Logger LOGGER = LoggerFactory.getLogger(HistoryMigrator.class);

  // Skip reason constants
  public static final String SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE = "Missing root process instance";
  public static final String SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE = "Missing parent process instance";
  public static final String SKIP_REASON_MISSING_PROCESS_DEFINITION = "Missing process definition";
  public static final String SKIP_REASON_MISSING_PROCESS_INSTANCE = "Missing process instance";
  public static final String SKIP_REASON_BELONGS_TO_SKIPPED_TASK = "Belongs to a skipped task";
  public static final String SKIP_REASON_MISSING_SCOPE_KEY = "Missing scope key";
  public static final String SKIP_REASON_MISSING_FLOW_NODE = "Missing flow node";
  public static final String SKIP_REASON_MISSING_PARENT_FLOW_NODE = "Missing parent flow node";
  public static final String SKIP_REASON_MISSING_DECISION_REQUIREMENTS = "Missing decision requirements definition";
  public static final String SKIP_REASON_MISSING_DECISION_DEFINITION = "Missing decision definition";
  public static final String SKIP_REASON_MISSING_ROOT_DECISION_INSTANCE = "Missing root decision instance";

  // HistoryMigrator Messages
  public static final String MIGRATING = "Migrating {}s.";
  public static final String MIGRATING_DEFINITION = "Migrating {} definition with C7 ID: [{}]";

  public static final String MIGRATING_INSTANCE = "Migrating historic {} instance with C7 ID: [{}]";

  public static final String MIGRATING_INCIDENT = "Migrating historic incident with C7 ID: [{}]";

  public static final String MIGRATING_VARIABLE = "Migrating historic variables with C7 ID: [{}]";

  public static final String MIGRATING_USER_TASK = "Migrating historic user task with C7 ID: [{}]";

  public static final String MIGRATING_FLOW_NODE = "Migrating historic flow nodes with C7 ID: [{}]";

  public static final String MIGRATING_DECISION_REQUIREMENT = "Migrating decision requirements with C7 ID: [{}]";

  public static final String MIGRATING_AUDIT_LOGS = "Migrating audit logs with C7 ID: [{}]";

  public static final String SKIPPING = "Migration of {} with C7 ID [{}] skipped. {}";

  public static final String MIGRATION_COMPLETED = "Migration of {} with C7 ID [{}] completed.";

  public static final String SKIPPING_INTERCEPTOR_ERROR = "Migration of [{}] with C7 ID [{}] skipped." + " Interceptor error: {}";
  public static final String UNSUPPORTED_AUDIT_LOG_ENTITY_TYPE = "Unsupported audit log entity type";
  public static final String UNSUPPORTED_AUDIT_LOG_OPERATION_TYPE = "Unsupported audit log operation type";

  public static void logMigrating(IdKeyMapper.TYPE type) {
    LOGGER.info(MIGRATING, type.getDisplayName());
  }

  public static void migratingDecisionDefinition(String c7DecisionDefinitionId) {
    LOGGER.debug(MIGRATING_DEFINITION, "decision", c7DecisionDefinitionId);
  }

  public static void logMigrationCompleted(C7Entity<?> entity) {
    LOGGER.debug(MIGRATION_COMPLETED, entity.getType().getDisplayName(), entity.getId());
  }

  public static void migratingProcessDefinition(String c7ProcessDefinitionId) {
    LOGGER.debug(MIGRATING_DEFINITION, "process", c7ProcessDefinitionId);
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

  public static void skippingProcessInstanceDueToMissingParentFlowNode(String c7ProcessInstanceId) {
    LOGGER.debug(SKIP_REASON_MISSING_PARENT_FLOW_NODE, "process", c7ProcessInstanceId);
  }

  public static void skippingProcessInstanceDueToMissingRoot(String c7ProcessInstanceId) {
    LOGGER.debug(SKIPPING_INSTANCE_MISSING_ROOT, "process", c7ProcessInstanceId);
  }

  public static void skippingProcessInstanceDueToMissingDefinition(String c7ProcessInstanceId) {
    LOGGER.debug(SKIPPING_INSTANCE_MISSING_DEFINITION, "process", c7ProcessInstanceId, "process");
  }

  public static void migratingDecisionInstances() {
    LOGGER.info(MIGRATING_INSTANCES, "decision");
  }

  public static void migratingDecisionInstance(String c7DecisionInstanceId) {
    LOGGER.debug(MIGRATING_INSTANCE, "decision", c7DecisionInstanceId);
  }

  public static void migratingHistoricIncident(String c7IncidentId) {
    LOGGER.debug(MIGRATING_INCIDENT, c7IncidentId);
  }

  public static void migratingHistoricVariable(String c7VariableId) {
    LOGGER.debug(MIGRATING_VARIABLE, c7VariableId);
  }

  public static void migratingHistoricUserTask(String c7UserTaskId) {
    LOGGER.debug(MIGRATING_USER_TASK, c7UserTaskId);
  }

  public static void logMigratingAuditLogs(String c7AuditLogId) {
    LOGGER.debug(MIGRATING_AUDIT_LOGS, c7AuditLogId);
  }

  public static void migratingHistoricFlowNode(String c7FlowNodeId) {
    LOGGER.debug(MIGRATING_FLOW_NODE, c7FlowNodeId);
  }

  public static void migratingDecisionRequirements(String c7DecisionRequirementsId) {
    LOGGER.debug(MIGRATING_DECISION_REQUIREMENT, c7DecisionRequirementsId);
  }

  public static void logSkipping(EntitySkippedException e) {
    C7Entity<?> c7Entity = e.getC7Entity();
    LOGGER.debug(SKIPPING, c7Entity.getType().getDisplayName(), c7Entity.getId(), e.getMessage());
  }

  public static void skippingEntityDueToInterceptorError(EntitySkippedException e) {
    C7Entity<?> c7Entity = e.getC7Entity();
    LOGGER.warn(SKIPPING_INTERCEPTOR_ERROR, HISTORY_TYPE_NAME_MAP.get(c7Entity.unwrap().getClass()), c7Entity.getId(), e.getMessage());
  }

}
