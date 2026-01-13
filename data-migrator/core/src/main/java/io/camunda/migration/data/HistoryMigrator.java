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
  }

  public void migrateProcessDefinitions() {
    processDefinitionMigrator.migrateProcessDefinitions();
  }

  public void migrateProcessInstances() {
    processInstanceMigrator.migrateProcessInstances();
  }

  public void migrateFlowNodes() {
    flowNodeMigrator.migrateFlowNodes();
  }

  public void migrateUserTasks() {
    userTaskMigrator.migrateUserTasks();
  }

  public void migrateVariables() {
    variableMigrator.migrateVariables();
  }

  public void migrateIncidents() {
    incidentMigrator.migrateIncidents();
  }

  public void migrateDecisionRequirementsDefinitions() {
    decisionRequirementsMigrator.migrateDecisionRequirementsDefinitions();
  }

  public void migrateDecisionDefinitions() {
    decisionDefinitionMigrator.migrateDecisionDefinitions();
  }

  public void migrateDecisionInstances() {
    decisionInstanceMigrator.migrateDecisionInstances();
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
