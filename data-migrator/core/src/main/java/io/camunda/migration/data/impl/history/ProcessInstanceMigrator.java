/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.time.OffsetDateTime;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class responsible for migrating process instances from Camunda 7 to Camunda 8.
 */
@Service
public class ProcessInstanceMigrator extends BaseMigrator {
  public void migrateProcessInstances() {
    HistoryMigratorLogs.migratingProcessInstances();
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_PROCESS_INSTANCE, idKeyDbModel -> {
        HistoricProcessInstance historicProcessInstance = c7Client.getHistoricProcessInstance(idKeyDbModel.getC7Id());
        migrateProcessInstance(historicProcessInstance);
      });
    } else {
      c7Client.fetchAndHandleHistoricProcessInstances(this::migrateProcessInstance,
          dbClient.findLatestCreateTimeByType(HISTORY_PROCESS_INSTANCE));
    }
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
  @Transactional
  public void migrateProcessInstance(HistoricProcessInstance c7ProcessInstance) {
    String c7ProcessInstanceId = c7ProcessInstance.getId();
    if (shouldMigrate(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
      HistoryMigratorLogs.migratingProcessInstance(c7ProcessInstanceId);
      Long processDefinitionKey = findProcessDefinitionKey(c7ProcessInstance.getProcessDefinitionId());
      String processDefinitionId = c7ProcessInstance.getProcessDefinitionId();

      try {
        ProcessInstanceDbModel.ProcessInstanceDbModelBuilder processInstanceDbModelBuilder = new ProcessInstanceDbModel.ProcessInstanceDbModelBuilder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7ProcessInstance,
            HistoricProcessInstance.class, processInstanceDbModelBuilder);

        processInstanceDbModelBuilder.processInstanceKey(getNextKey());
        if (processDefinitionKey != null) {
          processInstanceDbModelBuilder.processDefinitionKey(processDefinitionKey);
        }

        String c7SuperProcessInstanceId = c7ProcessInstance.getSuperProcessInstanceId();
        if (isMigrated(processDefinitionId, HISTORY_PROCESS_DEFINITION)) {
          if (c7SuperProcessInstanceId != null) {
            ProcessInstanceEntity parentInstance = findProcessInstanceByC7Id(c7SuperProcessInstanceId);
            if (parentInstance != null) {
              Long parentProcessInstanceKey = parentInstance.processInstanceKey();
              processInstanceDbModelBuilder.parentProcessInstanceKey(parentProcessInstanceKey);
            }
          }
        }

        OffsetDateTime historyCleanupDate = calculateHistoryCleanupDate(
            c7ProcessInstance.getState(),
            c7ProcessInstance.getEndTime(),
            c7ProcessInstance.getRemovalTime());
        OffsetDateTime endDate = calculateEndDate(
            c7ProcessInstance.getState(),
            c7ProcessInstance.getEndTime());

        processInstanceDbModelBuilder
            .historyCleanupDate(historyCleanupDate)
            .endDate(endDate);
        ProcessInstanceDbModel dbModel = convertProcessInstance(context);
        if (dbModel.processDefinitionKey() == null) {
          markSkipped(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(),
              SKIP_REASON_MISSING_PROCESS_DEFINITION);
          HistoryMigratorLogs.skippingProcessInstanceDueToMissingDefinition(c7ProcessInstanceId);
        } else if (c7SuperProcessInstanceId != null && dbModel.parentProcessInstanceKey() == null) {
          markSkipped(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(),
              SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE);
          HistoryMigratorLogs.skippingProcessInstanceDueToMissingParent(c7ProcessInstanceId);
        } else {
          insertProcessInstance(c7ProcessInstance, dbModel, c7ProcessInstanceId);
        }
      } catch (EntityInterceptorException | VariableInterceptorException e) {
        handleInterceptorException(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(), e);
      }
    }
  }

  protected ProcessInstanceDbModel convertProcessInstance(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder = (ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  protected void insertProcessInstance(HistoricProcessInstance c7ProcessInstance,
                                       ProcessInstanceDbModel dbModel,
                                       String c7ProcessInstanceId) {
    c8Client.insertProcessInstance(dbModel);
    markMigrated(c7ProcessInstanceId, dbModel.processInstanceKey(), c7ProcessInstance.getStartTime(),
        HISTORY_PROCESS_INSTANCE);
    HistoryMigratorLogs.migratingProcessInstanceCompleted(c7ProcessInstanceId);
  }
}

