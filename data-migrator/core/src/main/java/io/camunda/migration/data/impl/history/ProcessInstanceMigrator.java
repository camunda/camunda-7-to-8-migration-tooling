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
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
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
public class ProcessInstanceMigrator extends BaseMigrator<HistoricProcessInstance> {

  @Override
  public void migrate() {
    HistoryMigratorLogs.migratingProcessInstances();
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_PROCESS_INSTANCE, idKeyDbModel -> {
        HistoricProcessInstance historicProcessInstance = c7Client.getHistoricProcessInstance(idKeyDbModel.getC7Id());
        self.migrateOne(historicProcessInstance);
      });
    } else {
      Date createTime = dbClient.findLatestCreateTimeByType(HISTORY_PROCESS_INSTANCE);
      c7Client.fetchAndHandleHistoricProcessInstances(self::migrateOne, createTime);
    }
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
  public void migrateOne(HistoricProcessInstance c7ProcessInstance) {
    String c7ProcessInstanceId = c7ProcessInstance.getId();
    if (shouldMigrate(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
      HistoryMigratorLogs.migratingProcessInstance(c7ProcessInstanceId);
      Long processDefinitionKey = findProcessDefinitionKey(c7ProcessInstance.getProcessDefinitionId());
      String processDefinitionId = c7ProcessInstance.getProcessDefinitionId();

      try {
        ProcessInstanceDbModel.ProcessInstanceDbModelBuilder processInstanceDbModelBuilder = configureProcessInstanceBuilder(
            c7ProcessInstance, c7ProcessInstanceId, processDefinitionKey, processDefinitionId);
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7ProcessInstance,
            HistoricProcessInstance.class, processInstanceDbModelBuilder);

        String c7SuperProcessInstanceId = c7ProcessInstance.getSuperProcessInstanceId();
        String c7RootProcessInstanceId = c7ProcessInstance.getRootProcessInstanceId();
        validateDependenciesAndInsert(c7ProcessInstance, context, c7ProcessInstanceId, c7SuperProcessInstanceId,
            c7RootProcessInstanceId);
      } catch (EntityInterceptorException | VariableInterceptorException e) {

        handleInterceptorException(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(), e);
      }
    }
  }

  /**
   * Configures the process instance builder with keys, parent/root relationships, and dates.
   *
   * @param c7ProcessInstance the historic process instance from Camunda 7
   * @param c7ProcessInstanceId the C7 process instance ID
   * @param processDefinitionKey the C8 process definition key
   * @param processDefinitionId the C7 process definition ID
   * @return the configured builder
   */
  protected ProcessInstanceDbModel.ProcessInstanceDbModelBuilder configureProcessInstanceBuilder(
      HistoricProcessInstance c7ProcessInstance,
      String c7ProcessInstanceId,
      Long processDefinitionKey,
      String processDefinitionId) {

    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder = new ProcessInstanceDbModel.ProcessInstanceDbModelBuilder();

    Long processInstanceKey = getNextKey();
    builder.processInstanceKey(processInstanceKey);
    if (processDefinitionKey != null) {
      builder.processDefinitionKey(processDefinitionKey);
    }

    resolveParentAndRootKeys(builder, c7ProcessInstance, c7ProcessInstanceId, processInstanceKey, processDefinitionId);
    setProcessInstanceDates(builder, c7ProcessInstance);

    return builder;
  }

  /**
   * Resolves and sets parent and root process instance keys on the builder.
   *
   * @param builder the process instance builder
   * @param c7ProcessInstance the historic process instance from Camunda 7
   * @param c7ProcessInstanceId the C7 process instance ID
   * @param processInstanceKey the C8 process instance key
   * @param processDefinitionId the C7 process definition ID
   */
  protected void resolveParentAndRootKeys(
      ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder,
      HistoricProcessInstance c7ProcessInstance,
      String c7ProcessInstanceId,
      Long processInstanceKey,
      String processDefinitionId) {

    String c7SuperProcessInstanceId = c7ProcessInstance.getSuperProcessInstanceId();
    String c7RootProcessInstanceId = c7ProcessInstance.getRootProcessInstanceId();

    if (isMigrated(processDefinitionId, HISTORY_PROCESS_DEFINITION)) {
      if (c7SuperProcessInstanceId != null) {
        ProcessInstanceEntity parentInstance = findProcessInstanceByC7Id(c7SuperProcessInstanceId);
        if (parentInstance != null) {
          Long parentProcessInstanceKey = parentInstance.processInstanceKey();
          builder.parentProcessInstanceKey(parentProcessInstanceKey);
        }
      }

      if (c7RootProcessInstanceId != null && c7RootProcessInstanceId.equals(c7ProcessInstanceId)) {
        builder.rootProcessInstanceKey(processInstanceKey);
      } else if (c7RootProcessInstanceId != null && isMigrated(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
        ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
        if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
          builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey());
        }
      }
    }
  }

  /**
   * Calculates and sets history cleanup date and end date on the builder.
   *
   * @param builder the process instance builder
   * @param c7ProcessInstance the historic process instance from Camunda 7
   */
  protected void setProcessInstanceDates(
      ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder,
      HistoricProcessInstance c7ProcessInstance) {

    Date c7EndTime = c7ProcessInstance.getEndTime();
    var c8EndTime = calculateEndDate(c7EndTime);

    var c8HistoryCleanupDate = calculateHistoryCleanupDate(c8EndTime, c7ProcessInstance.getRemovalTime());

    builder
        .historyCleanupDate(c8HistoryCleanupDate)
        .endDate(c8EndTime);
  }

  protected ProcessInstanceDbModel convertProcessInstance(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder = (ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  /**
   * Validates dependencies and inserts the process instance or marks it as skipped.
   *
   * @param c7ProcessInstance the historic process instance from Camunda 7
   * @param context the entity conversion context
   * @param c7ProcessInstanceId the C7 process instance ID
   * @param c7SuperProcessInstanceId the C7 super process instance ID (null if not a sub-process)
   * @param c7RootProcessInstanceId the C7 root process instance ID (might be same as current instance ID)
   */
  protected void validateDependenciesAndInsert(HistoricProcessInstance c7ProcessInstance,
                                               EntityConversionContext<?, ?> context,
                                               String c7ProcessInstanceId,
                                               String c7SuperProcessInstanceId,
                                               String c7RootProcessInstanceId) {

    ProcessInstanceDbModel dbModel = convertProcessInstance(context);
    if (dbModel.processDefinitionKey() == null) {
      markSkipped(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(),
          SKIP_REASON_MISSING_PROCESS_DEFINITION);
      HistoryMigratorLogs.skippingProcessInstanceDueToMissingDefinition(c7ProcessInstanceId);
    } else if (c7SuperProcessInstanceId != null && dbModel.parentProcessInstanceKey() == null) {
      markSkipped(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(),
          SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE);
      HistoryMigratorLogs.skippingProcessInstanceDueToMissingParent(c7ProcessInstanceId);
    } else if (c7RootProcessInstanceId != null && dbModel.rootProcessInstanceKey() == null) {
      markSkipped(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(),
          SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
      HistoryMigratorLogs.skippingProcessInstanceDueToMissingRoot(c7ProcessInstanceId);
    } else {
      insertProcessInstance(c7ProcessInstance, dbModel, c7ProcessInstanceId);
    }
  }

  protected void insertProcessInstance(HistoricProcessInstance c7ProcessInstance,
                                       ProcessInstanceDbModel dbModel,
                                       String c7ProcessInstanceId) {
    c8Client.insertProcessInstance(dbModel);
    markMigrated(c7ProcessInstanceId, dbModel.processInstanceKey(), c7ProcessInstance.getStartTime(), HISTORY_PROCESS_INSTANCE);
    HistoryMigratorLogs.migratingProcessInstanceCompleted(c7ProcessInstanceId);
  }

}

