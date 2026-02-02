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
import io.camunda.migration.data.impl.history.migrator.DecisionDefinitionMigrator;
import io.camunda.migration.data.impl.history.migrator.DecisionInstanceMigrator;
import io.camunda.migration.data.impl.history.migrator.DecisionRequirementsMigrator;
import io.camunda.migration.data.impl.history.migrator.FlowNodeMigrator;
import io.camunda.migration.data.impl.history.migrator.IncidentMigrator;
import io.camunda.migration.data.impl.history.migrator.ProcessDefinitionMigrator;
import io.camunda.migration.data.impl.history.migrator.ProcessInstanceMigrator;
import io.camunda.migration.data.impl.history.migrator.UserTaskMigrator;
import io.camunda.migration.data.impl.history.migrator.VariableMigrator;
import io.camunda.migration.data.impl.history.AuditLogMigrator;
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
  protected AuditLogMigrator auditLogMigrator;

  @Autowired
  protected DbClient dbClient;

  protected MigratorMode mode = MIGRATE;

  protected List<TYPE> requestedEntityTypes;

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
    migrateProcessDefinitions();
    migrateProcessInstances();
    migrateFlowNodes();
    migrateUserTasks();
    migrateVariables();
    migrateIncidents();
    migrateDecisionRequirementsDefinitions();
    migrateDecisionDefinitions();
    migrateDecisionInstances();
    migrateAuditLogs();
  }

  public void migrateProcessDefinitions() {
    processDefinitionMigrator.migrateAll();
  }

  public void migrateProcessInstances() {
    processInstanceMigrator.migrateAll();
  }

  public void migrateFlowNodes() {
    flowNodeMigrator.migrateAll();
  }

  public void migrateUserTasks() {
    userTaskMigrator.migrateAll();
  }

  public void migrateVariables() {
    variableMigrator.migrateAll();
  }

  public void migrateIncidents() {
    incidentMigrator.migrateAll();
  }

  public void migrateDecisionRequirementsDefinitions() {
    decisionRequirementsMigrator.migrateAll();
  }

  public void migrateDecisionDefinitions() {
    decisionDefinitionMigrator.migrateAll();
  }

  public void migrateDecisionInstances() {
    decisionInstanceMigrator.migrateAll();
  }

  public void migrateAuditLogs() {
    auditLogMigrator.migrate();
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
    auditLogMigrator.setMode(mode);
  }
}
