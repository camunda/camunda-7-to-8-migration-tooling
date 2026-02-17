/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_BELONGS_TO_SKIPPED_TASK;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_SCOPE_KEY;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_VARIABLE;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel.VariableDbModelBuilder;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.search.entities.ProcessInstanceEntity;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating variables from Camunda 7 to Camunda 8.
 */
@Service
public class VariableMigrator extends BaseMigrator<HistoricVariableInstance, VariableDbModel> {

  @Autowired
  protected VendorDatabaseProperties vendorDatabaseProperties;

  @Override
  public void migrateAll() {
    fetchAndRetry(
        HISTORY_VARIABLE,
        c7Client::getHistoricVariableInstance,
        c7Client::fetchAndHandleHistoricVariables
    );
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
   *   <li>Root process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE}</li>
   *   <li>Interceptor error during conversion - skipped with the exception message</li>
   * </ul>
   *
   * @param c7Variable the historic variable instance from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion (handled internally, entity marked as skipped)
   * @throws VariableInterceptorException if an error occurs during variable interception (handled internally, entity marked as skipped)
   */
  @Override
  public Long migrateTransactionally(HistoricVariableInstance c7Variable) {
    String c7VariableId = c7Variable.getId();
    if (shouldMigrate(c7VariableId, HISTORY_VARIABLE)) {
      HistoryMigratorLogs.migratingHistoricVariable(c7VariableId);

      var builder = new VariableDbModelBuilder();

      String c7ProcessInstanceId = c7Variable.getProcessInstanceId();
      if (c7ProcessInstanceId != null && isMigrated(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
        ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7ProcessInstanceId);
        Long processInstanceKey = processInstance.processInstanceKey();
        builder.processInstanceKey(processInstanceKey);

        String c7RootProcessInstanceId = c7Variable.getRootProcessInstanceId();
        if (c7RootProcessInstanceId != null && isMigrated(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
          ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
          if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
            builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey());
          }
        }
      }

      String activityInstanceId = c7Variable.getActivityInstanceId();
      if (isMigrated(activityInstanceId, HISTORY_FLOW_NODE) || isMigrated(activityInstanceId, HISTORY_PROCESS_INSTANCE)) {
        Long scopeKey = findScopeKey(activityInstanceId);
        if (scopeKey != null) {
          builder.scopeKey(scopeKey)
              .elementInstanceKey(scopeKey);
        }
      }

      VariableDbModel dbModel = convertAndTruncateDbModel(c7Variable, builder);

      if (dbModel.processInstanceKey() == null) {
        throw new EntitySkippedException(c7Variable, SKIP_REASON_MISSING_PROCESS_INSTANCE);
      }

      if (dbModel.scopeKey() == null) {
        String message = c7Variable.getTaskId() != null ? SKIP_REASON_BELONGS_TO_SKIPPED_TASK : SKIP_REASON_MISSING_SCOPE_KEY;
        throw new EntitySkippedException(c7Variable, message);
      }

      if (dbModel.rootProcessInstanceKey() == null) {
        throw new EntitySkippedException(c7Variable, SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
      }

      c8Client.insertVariable(dbModel);

      return dbModel.variableKey();
    }

    return null;
  }

  protected @NonNull VariableDbModel convertAndTruncateDbModel(HistoricVariableInstance c7Variable,
                                                             VariableDbModelBuilder builder) {
    VariableDbModel dbModel = convert(C7Entity.of(c7Variable), builder);
    return dbModel.truncateValue(vendorDatabaseProperties.variableValuePreviewSize(),
        vendorDatabaseProperties.charColumnMaxBytes());
  }

}

