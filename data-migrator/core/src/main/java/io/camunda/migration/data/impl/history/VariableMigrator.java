/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_BELONGS_TO_SKIPPED_TASK;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_SCOPE_KEY;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_VARIABLE;

import io.camunda.db.rdbms.sql.VariableMapper.BatchInsertVariablesDto;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating variables from Camunda 7 to Camunda 8.
 */
@Service
public class VariableMigrator extends BaseMigrator<HistoricVariableInstance> {

  @Override
  public void migrate() {
    HistoryMigratorLogs.migratingHistoricVariables();

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_VARIABLE, idKeyDbModel -> {
        HistoricVariableInstance historicVariableInstance = c7Client.getHistoricVariableInstance(idKeyDbModel.getC7Id());
        self.migrateOne(historicVariableInstance);
      });
    } else {
      Date createTime = dbClient.findLatestCreateTimeByType(HISTORY_VARIABLE);
      c7Client.fetchAndHandleHistoricVariables(self::migrateOne, createTime);
    }
  }

  /**
   * Migrates a historic variable instance from Camunda 7 to Camunda 8.
   *
   * <p>Variables can be scoped to either:
   * <ul>
   *   <li>A user task (taskId != null)</li>
   *   <li>A process instance with optional activity instance scope</li>
   * </ul>
   *
   * <p>This method validates that all parent entities (process instance, task, activity instance)
   * have been migrated before attempting to migrate the variable.
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *   <li>User task not yet migrated (for task-scoped variables) - skipped with {@code SKIP_REASON_BELONGS_TO_SKIPPED_TASK}</li>
   *   <li>Scope key missing (flow node or process instance) - skipped with {@code SKIP_REASON_MISSING_SCOPE_KEY}</li>
   * </ul>
   *
   * @param c7Variable the historic variable instance from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  @Override
  public void migrateOne(HistoricVariableInstance c7Variable) {
    String c7VariableId = c7Variable.getId();
    if (shouldMigrate(c7VariableId, HISTORY_VARIABLE)) {
      HistoryMigratorLogs.migratingHistoricVariable(c7VariableId);

      try {
        VariableDbModel.VariableDbModelBuilder variableDbModelBuilder = new VariableDbModel.VariableDbModelBuilder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7Variable, HistoricVariableInstance.class, variableDbModelBuilder);

        String c7ProcessInstanceId = c7Variable.getProcessInstanceId();
        if (c7ProcessInstanceId != null && isMigrated(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
          ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7ProcessInstanceId);
          Long processInstanceKey = processInstance.processInstanceKey();
          variableDbModelBuilder.processInstanceKey(processInstanceKey);
          OffsetDateTime historyCleanupDate = calculateHistoryCleanupDateForChild(processInstance.endDate(), c7Variable.getRemovalTime());
          variableDbModelBuilder.historyCleanupDate(historyCleanupDate);
        }

        String activityInstanceId = c7Variable.getActivityInstanceId();
        if (isMigrated(activityInstanceId, HISTORY_FLOW_NODE) || isMigrated(activityInstanceId,
            HISTORY_PROCESS_INSTANCE)) {
          Long scopeKey = findScopeKey(activityInstanceId);
          if (scopeKey != null) {
            variableDbModelBuilder.scopeKey(scopeKey);
          }
        }

        processVariableConversion(c7Variable, context, c7VariableId);
      } catch (EntityInterceptorException | VariableInterceptorException e) {
        handleInterceptorException(c7VariableId, HISTORY_VARIABLE, c7Variable.getCreateTime(), e);
      }
    }
  }

  protected void processVariableConversion(HistoricVariableInstance c7Variable,
                                           EntityConversionContext<?, ?> context,
                                           String c7VariableId) {
    VariableDbModel dbModel = convertVariable(context);

    if (dbModel.processInstanceKey() == null) {
      markSkipped(c7VariableId, TYPE.HISTORY_VARIABLE, c7Variable.getCreateTime(),
          SKIP_REASON_MISSING_PROCESS_INSTANCE);
      HistoryMigratorLogs.skippingHistoricVariableDueToMissingProcessInstance(c7VariableId);
    } else if (dbModel.scopeKey() == null) {
      String skipReason =
          c7Variable.getTaskId() != null ? SKIP_REASON_BELONGS_TO_SKIPPED_TASK : SKIP_REASON_MISSING_SCOPE_KEY;
      markSkipped(c7VariableId, TYPE.HISTORY_VARIABLE, c7Variable.getCreateTime(), skipReason);

      if (c7Variable.getTaskId() != null) {
        HistoryMigratorLogs.skippingHistoricVariableDueToMissingTask(c7VariableId, c7Variable.getTaskId());
      } else {
        HistoryMigratorLogs.skippingHistoricVariableDueToMissingScopeKey(c7VariableId);
      }
    } else {
      insertVariable(c7Variable, dbModel, c7VariableId);
    }
  }

  protected void insertVariable(HistoricVariableInstance c7Variable, VariableDbModel dbModel, String c7VariableId) {
    c8Client.insertVariable(new BatchInsertVariablesDto(List.of(dbModel)));
    markMigrated(c7VariableId, dbModel.variableKey(), c7Variable.getCreateTime(), HISTORY_VARIABLE);
    HistoryMigratorLogs.migratingHistoricVariableCompleted(c7VariableId);
  }

  protected VariableDbModel convertVariable(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    VariableDbModel.VariableDbModelBuilder builder = (VariableDbModel.VariableDbModelBuilder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }
}

