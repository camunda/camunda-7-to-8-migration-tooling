/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating process instances from Camunda 7 to Camunda 8.
 */
@Service
public class ProcessInstanceMigrator extends BaseMigrator<HistoricProcessInstance, ProcessInstanceDbModel> {

  public void migrateProcessInstances() {
    HistoryMigratorLogs.migratingProcessInstances();
    executeMigration(
        HISTORY_PROCESS_INSTANCE,
        c7Client::getHistoricProcessInstance,
        c7Client::fetchAndHandleHistoricProcessInstances,
        this::migrateProcessInstance
    );
  }

  /**
   * Migrates a historic process instance from Camunda 7 to Camunda 8.
   *
   * <p>This method handles the migration of process instances, including their parent-child relationships.
   * The migration process follows these steps:
   * <ol>
   *   <li>Validates that the process definition has been migrated</li>
   *   <li>Resolves parent process instance relationships (for sub-processes)</li>
   *   <li>Converts the C7 process instance to C8 format</li>
   *   <li>Inserts the process instance or marks it as skipped if dependencies are missing</li>
   * </ol>
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *   <li>Parent process instance not yet migrated (for sub-processes) - skipped with {@code SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE}</li>
   * </ul>
   *
   * @param c7ProcessInstance the historic process instance from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion or interception
   */
  public void migrateProcessInstance(HistoricProcessInstance c7ProcessInstance) {
    var c7ProcessInstanceId = c7ProcessInstance.getId();
    if (shouldMigrate(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
      HistoryMigratorLogs.migratingProcessInstance(c7ProcessInstanceId);
      var processDefinitionKey = findProcessDefinitionKey(c7ProcessInstance.getProcessDefinitionId());
      var processDefinitionId = c7ProcessInstance.getProcessDefinitionId();

      var builder = new ProcessInstanceDbModel.ProcessInstanceDbModelBuilder();

      builder.processInstanceKey(getNextKey());
      if (processDefinitionKey != null) {
        builder.processDefinitionKey(processDefinitionKey);
      }

      var c7SuperProcessInstanceId = c7ProcessInstance.getSuperProcessInstanceId();
      if (isMigrated(processDefinitionId, HISTORY_PROCESS_DEFINITION)) {
        if (c7SuperProcessInstanceId != null) {
          var parentInstance = findProcessInstanceByC7Id(c7SuperProcessInstanceId);
          if (parentInstance != null) {
            var parentProcessInstanceKey = parentInstance.processInstanceKey();
            builder.parentProcessInstanceKey(parentProcessInstanceKey);
          }
        }
      }

      var historyCleanupDate = calculateHistoryCleanupDate(
          c7ProcessInstance.getState(),
          c7ProcessInstance.getEndTime(),
          c7ProcessInstance.getRemovalTime());
      var endDate = calculateEndDate(
          c7ProcessInstance.getState(),
          c7ProcessInstance.getEndTime());

      builder
          .historyCleanupDate(historyCleanupDate)
          .endDate(endDate);

      ProcessInstanceDbModel dbModel;
      try {
        dbModel = convert(c7ProcessInstance, builder);
      } catch (EntityInterceptorException | VariableInterceptorException e) {
        handleInterceptorException(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(), e);
        return;
      }

      if (dbModel.processDefinitionKey() == null) {
        markSkipped(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(),
            SKIP_REASON_MISSING_PROCESS_DEFINITION);
        HistoryMigratorLogs.skippingProcessInstanceDueToMissingDefinition(c7ProcessInstanceId);
      } else if (c7SuperProcessInstanceId != null && dbModel.parentProcessInstanceKey() == null) {
        markSkipped(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(),
            SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE);
        HistoryMigratorLogs.skippingProcessInstanceDueToMissingParent(c7ProcessInstanceId);
      } else {
        c8Client.insertProcessInstance(dbModel);
        markMigrated(c7ProcessInstanceId, dbModel.processInstanceKey(), c7ProcessInstance.getStartTime(), HISTORY_PROCESS_INSTANCE);
        HistoryMigratorLogs.migratingProcessInstanceCompleted(c7ProcessInstanceId);
      }
    }
  }
}

