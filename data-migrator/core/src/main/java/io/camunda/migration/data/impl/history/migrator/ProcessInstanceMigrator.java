/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Date;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating process instances from Camunda 7 to Camunda 8.
 */
@Service
public class ProcessInstanceMigrator extends BaseMigrator<HistoricProcessInstance, ProcessInstanceDbModel> {

  @Override
  public void migrateAll() {
    fetchAndRetry(
        HISTORY_PROCESS_INSTANCE,
        c7Client::getHistoricProcessInstance,
        c7Client::fetchAndHandleHistoricProcessInstances
    );
  }

  /**
   * Migrates a historic process instance from Camunda 7 to Camunda 8.
   *
   * <p>This method handles the migration of process instances, including their parent-child relationships.
   * The migration process follows these steps:
   * <ol>
   *   <li>Checks if the process instance should be migrated based on the current mode</li>
   *   <li>Configures the process instance builder with keys, parent/root relationships, and dates</li>
   *   <li>Creates entity conversion context for interceptor processing</li>
   *   <li>Converts the C7 process instance to C8 format</li>
   *   <li>Validates dependencies and either inserts or marks as skipped</li>
   * </ol>
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *   <li>Parent process instance not yet migrated (for call activities/sub-processes) - skipped with {@code SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE}</li>
   *   <li>Root process instance not yet migrated (when part of a process hierarchy) - skipped with {@code SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE}</li>
   *   <li>Interceptor error during conversion - skipped with the exception message</li>
   * </ul>
   *
   * @param c7ProcessInstance the historic process instance from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion (handled internally, entity marked as skipped)
   * @throws VariableInterceptorException if an error occurs during variable interception (handled internally, entity marked as skipped)
   */
  @Override
  public Long migrateTransactionally(HistoricProcessInstance c7ProcessInstance) {
    String c7ProcessInstanceId = c7ProcessInstance.getId();
    if (shouldMigrate(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
      HistoryMigratorLogs.migratingProcessInstance(c7ProcessInstanceId);
      Long processDefinitionKey = findProcessDefinitionKey(c7ProcessInstance.getProcessDefinitionId());
      String processDefinitionId = c7ProcessInstance.getProcessDefinitionId();

      var builder = new ProcessInstanceDbModelBuilder();

      Long processInstanceKey = getNextKey();
      builder.processInstanceKey(processInstanceKey);
      if (processDefinitionKey != null) {
        builder.processDefinitionKey(processDefinitionKey);
      }

      if (isMigrated(processDefinitionId, HISTORY_PROCESS_DEFINITION)) {
        String c7SuperProcessInstanceId = c7ProcessInstance.getSuperProcessInstanceId();
        if (c7SuperProcessInstanceId != null) {
          ProcessInstanceEntity parentInstance = findProcessInstanceByC7Id(c7SuperProcessInstanceId);
          if (parentInstance != null) {
            Long parentProcessInstanceKey = parentInstance.processInstanceKey();
            builder.parentProcessInstanceKey(parentProcessInstanceKey);
          }
        }

        String c7RootProcessInstanceId = c7ProcessInstance.getRootProcessInstanceId();
        if (c7RootProcessInstanceId != null && c7RootProcessInstanceId.equals(c7ProcessInstanceId)) {
          builder.rootProcessInstanceKey(processInstanceKey);
        } else if (c7RootProcessInstanceId != null && isMigrated(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
          ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
          if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
            builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey());
          }
        }
      }

      Date c7EndTime = c7ProcessInstance.getEndTime();
      var c8EndTime = calculateEndDate(c7EndTime);
      var c8HistoryCleanupDate = calculateHistoryCleanupDate(c8EndTime, c7ProcessInstance.getRemovalTime());

      builder
          .historyCleanupDate(c8HistoryCleanupDate)
          .endDate(c8EndTime);

      ProcessInstanceDbModel dbModel = convert(C7Entity.of(c7ProcessInstance), builder);

      if (dbModel.processDefinitionKey() == null) {
        throw new EntitySkippedException(c7ProcessInstance, SKIP_REASON_MISSING_PROCESS_DEFINITION);
      }

      if (c7ProcessInstance.getSuperProcessInstanceId() != null && dbModel.parentProcessInstanceKey() == null) {
        throw new EntitySkippedException(c7ProcessInstance, SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE);
      }

      if (dbModel.rootProcessInstanceKey() == null) {
        throw new EntitySkippedException(c7ProcessInstance, SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
      }

      c8Client.insertProcessInstance(dbModel);

      return dbModel.processInstanceKey();
    }

    return null;
  }

}

