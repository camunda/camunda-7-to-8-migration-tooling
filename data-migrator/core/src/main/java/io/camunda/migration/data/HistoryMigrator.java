/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data;

import static io.camunda.migration.data.MigratorMode.LIST_SKIPPED;
import static io.camunda.migration.data.MigratorMode.MIGRATE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.getHistoryTypes;

import io.camunda.migration.data.config.C8DataSourceConfigured;
import io.camunda.migration.data.impl.history.DecisionDefinitionMigrator;
import io.camunda.migration.data.impl.history.DecisionInstanceMigrator;
import io.camunda.migration.data.impl.history.DecisionRequirementsMigrator;
import io.camunda.migration.data.impl.history.FlowNodeMigrator;
import io.camunda.migration.data.impl.history.IncidentMigrator;
import io.camunda.migration.data.impl.history.ProcessDefinitionMigrator;
import io.camunda.migration.data.impl.history.ProcessInstanceMigrator;
import io.camunda.migration.data.impl.history.UserTaskMigrator;
import io.camunda.migration.data.impl.history.VariableMigrator;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.util.ExceptionUtils;
import io.camunda.migration.data.impl.util.PrintUtils;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(C8DataSourceConfigured.class)
public class HistoryMigrator {

  // Migrator Services

  @Autowired
  protected ProcessDefinitionMigrator processDefinitionMigrator;

  @Autowired
  protected ProcessInstanceMigrator processInstanceMigrator;

  @Autowired
  protected FlowNodeMigrator flowNodeMigrator;

  @Autowired
  protected UserTaskMigrator userTaskMigrator;

  @Autowired
  protected VariableMigrator variableMigrator;

  @Autowired
  protected IncidentMigrator incidentMigrator;

  @Autowired
  protected DecisionRequirementsMigrator decisionRequirementsMigrator;

  @Autowired
  protected DecisionDefinitionMigrator decisionDefinitionMigrator;

  @Autowired
  protected DecisionInstanceMigrator decisionInstanceMigrator;

  @Autowired
  protected DbClient dbClient;

  protected MigratorMode mode = MIGRATE;

  protected List<TYPE> requestedEntityTypes;

  /**
   * Safely flushes the batch, catching and logging any exceptions.
   * For history migration, we log errors but continue processing.
   */
  protected void safeFlushBatch() {
    try {
      dbClient.flushBatch();
    } catch (Exception e) {
      io.camunda.migration.data.impl.logging.HistoryMigratorLogs.batchFlushFailed(e.getMessage());
      dbClient.clearFailedBatchKeys();
    }
  }

  public void start() {
    try {
      ExceptionUtils.setContext(ExceptionUtils.ExceptionContext.HISTORY);
      if (LIST_SKIPPED.equals(mode)) {
        printSkippedHistoryEntities();
      } else {
        migrate();
      }
    } finally {
      ExceptionUtils.clearContext();
    }
  }

  protected void printSkippedHistoryEntities() {
    if(requestedEntityTypes == null ||  requestedEntityTypes.isEmpty()) {
      getHistoryTypes().forEach(this::printSkippedEntitiesForType);
    } else {
      requestedEntityTypes.forEach(this::printSkippedEntitiesForType);
    }
  }

  protected void printSkippedEntitiesForType(TYPE type) {
    PrintUtils.printSkippedInstancesHeader(dbClient.countSkippedByType(type), type);
    dbClient.listSkippedEntitiesByType(type);
  }

  public void migrate() {
    try {
      migrateProcessDefinitions();
      migrateProcessInstances();
      migrateFlowNodes();
      migrateUserTasks();
      migrateVariables();
      migrateIncidents();
      migrateDecisionRequirementsDefinitions();
      migrateDecisionDefinitions();
      migrateDecisionInstances();
    } finally {
      // Ensure batch is flushed at the end
      safeFlushBatch();
    }
  }

  public void migrateProcessDefinitions() {
    try {
      processDefinitionMigrator.migrate();
    } finally {
      safeFlushBatch();
    }
  }

  public void migrateProcessInstances() {
    try {
      processInstanceMigrator.migrate();
    } finally {
      safeFlushBatch();
    }
  }

  public void migrateFlowNodes() {
    try {
      flowNodeMigrator.migrate();
    } finally {
      safeFlushBatch();
    }
  }

  public void migrateUserTasks() {
    try {
      userTaskMigrator.migrate();
    } finally {
      safeFlushBatch();
    }
  }

  public void migrateVariables() {
    try {
      variableMigrator.migrate();
    } finally {
      safeFlushBatch();
    }
  }

  public void migrateIncidents() {
    try {
      incidentMigrator.migrate();
    } finally {
      safeFlushBatch();
    }
  }

  public void migrateDecisionRequirementsDefinitions() {
    try {
      decisionRequirementsMigrator.migrate();
    } finally {
      safeFlushBatch();
    }
  }

  public void migrateDecisionDefinitions() {
    try {
      decisionDefinitionMigrator.migrate();
    } finally {
      safeFlushBatch();
    }
  }

  public void migrateDecisionInstances() {
    try {
      decisionInstanceMigrator.migrate();
    } finally {
      safeFlushBatch();
    }
  }

  public void setRequestedEntityTypes(List<TYPE> requestedEntityTypes) {
    this.requestedEntityTypes = requestedEntityTypes;
  }

  public void setMode(MigratorMode mode) {
    this.mode = mode;
    decisionDefinitionMigrator.setMode(mode);
    decisionInstanceMigrator.setMode(mode);
    decisionRequirementsMigrator.setMode(mode);
    flowNodeMigrator.setMode(mode);
    incidentMigrator.setMode(mode);
    processDefinitionMigrator.setMode(mode);
    processInstanceMigrator.setMode(mode);
    userTaskMigrator.setMode(mode);
    variableMigrator.setMode(mode);
  }
}
