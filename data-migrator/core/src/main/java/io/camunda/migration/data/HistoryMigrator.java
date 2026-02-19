/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data;

import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.getHistoryTypes;

import io.camunda.migration.data.config.C8DataSourceConfigured;
import io.camunda.migration.data.impl.history.migrator.HistoryEntityMigrator;
import io.camunda.migration.data.impl.history.migrator.DecisionDefinitionMigrator;
import io.camunda.migration.data.impl.history.migrator.DecisionInstanceMigrator;
import io.camunda.migration.data.impl.history.migrator.DecisionRequirementsMigrator;
import io.camunda.migration.data.impl.history.migrator.FlowNodeMigrator;
import io.camunda.migration.data.impl.history.migrator.FormMigrator;
import io.camunda.migration.data.impl.history.migrator.IncidentMigrator;
import io.camunda.migration.data.impl.history.migrator.JobMigrator;
import io.camunda.migration.data.impl.history.migrator.ProcessDefinitionMigrator;
import io.camunda.migration.data.impl.history.migrator.ProcessInstanceMigrator;
import io.camunda.migration.data.impl.history.migrator.UserTaskMigrator;
import io.camunda.migration.data.impl.history.migrator.VariableMigrator;
import io.camunda.migration.data.impl.history.migrator.AuditLogMigrator;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.impl.util.ExceptionUtils;
import io.camunda.migration.data.impl.util.PrintUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(C8DataSourceConfigured.class)
public class HistoryMigrator {

  @Autowired
  protected FormMigrator formMigrator;

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
  protected JobMigrator jobMigrator;

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

  protected List<HistoryEntityMigrator<?, ?>> getMigrators() {
    return List.of(
        formMigrator,
        processDefinitionMigrator,
        processInstanceMigrator,
        flowNodeMigrator,
        userTaskMigrator,
        variableMigrator,
        jobMigrator,
        incidentMigrator,
        decisionRequirementsMigrator,
        decisionDefinitionMigrator,
        decisionInstanceMigrator,
        auditLogMigrator
    );
  }

  public void printSkippedHistoryEntities(List<IdKeyMapper.TYPE> requestedEntityTypes) {
    try {
      ExceptionUtils.setContext(ExceptionUtils.ExceptionContext.HISTORY);

      if (requestedEntityTypes == null || requestedEntityTypes.isEmpty()) {
        getHistoryTypes().forEach(this::printSkippedEntitiesForType);
      } else {
        requestedEntityTypes.forEach(this::printSkippedEntitiesForType);
      }

    } finally {
      ExceptionUtils.clearContext();
    }
  }

  protected void printSkippedEntitiesForType(TYPE type) {
    PrintUtils.printSkippedInstancesHeader(dbClient.countSkippedByType(type), type);
    dbClient.listSkippedEntitiesByType(type);
  }

  public void retry() {
    try {
      ExceptionUtils.setContext(ExceptionUtils.ExceptionContext.HISTORY);

      getMigrators().forEach(migrator -> migrator.retry()
          .forEach(HistoryMigratorLogs::logSkippingWarn));

    } finally {
      ExceptionUtils.clearContext();
    }
  }

  public void migrate() {
    try {
      ExceptionUtils.setContext(ExceptionUtils.ExceptionContext.HISTORY);

      getMigrators().forEach(HistoryEntityMigrator::migrate);

      List<EntitySkippedException> permanentlySkippedExceptions = new ArrayList<>();

      long previousSkippedCount = Long.MAX_VALUE;
      long currentSkippedCount = dbClient.countSkipped();

      while (currentSkippedCount > 0 && currentSkippedCount < previousSkippedCount) {
        permanentlySkippedExceptions.clear(); // Only keep exceptions from the latest retry loop
        previousSkippedCount = currentSkippedCount;
        getMigrators().forEach(migrator -> permanentlySkippedExceptions.addAll(migrator.retry()));
        currentSkippedCount = dbClient.countSkipped();
      }

      // Log all permanently skipped entities after retrying
      permanentlySkippedExceptions.forEach(HistoryMigratorLogs::logSkippingWarn);
    } finally {
      ExceptionUtils.clearContext();
    }
  }

  public void migrateByType(TYPE type) {
    HistoryEntityMigrator<?, ?> migrator = getMigrators().stream()
        .filter(m -> m.getType() == type)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No migrator found for type: " + type));

    migrator.migrate();
  }

  public void retryByType(TYPE type) {
    HistoryEntityMigrator<?, ?> migrator = getMigrators().stream()
        .filter(m -> m.getType() == type)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No migrator found for type: " + type));

    migrator.retry();
  }

}
