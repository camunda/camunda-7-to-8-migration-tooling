/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FORM;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_UNSUPPORTED_CMMN_TASKS;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_UNSUPPORTED_SA_TASKS;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigratingHistoricUserTask;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FORM_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_USER_TASK;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.Date;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating user tasks from Camunda 7 to Camunda 8.
 */
@Service
public class UserTaskMigrator extends HistoryEntityMigrator<HistoricTaskInstance, UserTaskDbModel> {

  @Override
  public BiConsumer<Consumer<HistoricTaskInstance>, Date> fetchForMigrateHandler() {
    return c7Client::fetchAndHandleHistoricUserTasks;
  }

  @Override
  public Function<String, HistoricTaskInstance> fetchForRetryHandler() {
    return c7Client::getHistoricTaskInstance;
  }

  @Override
  public IdKeyMapper.TYPE getType() {
    return HISTORY_USER_TASK;
  }

  /**
   * Migrates a historic user task from Camunda 7 to Camunda 8.
   *
   * <p>User tasks represent work items that need to be completed by human users.
   * This method validates that all parent entities (process instance, flow node instance)
   * have been migrated before attempting to migrate the user task.
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *   <li>Flow node instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_FLOW_NODE}</li>
   *   <li>Root process instance not yet migrated  - skipped with {@code SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE}</li>
   *   <li>Interceptor error during conversion - skipped with the exception message</li>
   * </ul>
   *
   * @param c7UserTask the historic user task from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion (handled internally, entity marked as skipped)
   */
  @Override
  public Long migrateTransactionally(HistoricTaskInstance c7UserTask) {
    var c7UserTaskId = c7UserTask.getId();
    if (shouldMigrate(c7UserTaskId, HISTORY_USER_TASK)) {
      logMigratingHistoricUserTask(c7UserTaskId);

      if (c7UserTask.getCaseInstanceId() != null) {
        throw new EntitySkippedException(c7UserTask, SKIP_REASON_UNSUPPORTED_CMMN_TASKS);
      }

      if (c7UserTask.getProcessInstanceId() == null && c7UserTask.getCaseInstanceId() == null) {
        throw new EntitySkippedException(c7UserTask, SKIP_REASON_UNSUPPORTED_SA_TASKS);
      }

      String c7FormId = null;
      if (c7UserTask.getProcessDefinitionId() != null) {
        c7FormId = c7Client.getFormId(c7UserTask.getProcessDefinitionId(), c7UserTask.getTaskDefinitionKey());
      }

      var builder = new UserTaskDbModel.Builder();
      if (isMigrated(c7UserTask.getProcessInstanceId(), HISTORY_PROCESS_INSTANCE)) {
        var processInstance = findProcessInstanceByC7Id(c7UserTask.getProcessInstanceId());
        if (processInstance != null) {
          builder.processInstanceKey(processInstance.processInstanceKey())
              .processDefinitionVersion(processInstance.processDefinitionVersion());
          var completionDate = calculateCompletionDateForChild(processInstance.endDate(), c7UserTask.getEndTime());
          builder.completionDate(completionDate);
        }
        String c7RootProcessInstanceId = c7UserTask.getRootProcessInstanceId();
        if (c7RootProcessInstanceId != null && isMigrated(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
          ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
          if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
            builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey());
          }
        }
        if (isMigrated(c7UserTask.getActivityInstanceId(), HISTORY_FLOW_NODE)) {
          var elementInstanceKey = findFlowNodeInstanceKey(c7UserTask.getActivityInstanceId());
          var processDefinitionKey = findProcessDefinitionKey(c7UserTask.getProcessDefinitionId());
          builder.processDefinitionKey(processDefinitionKey).elementInstanceKey(elementInstanceKey);
        }
        if (c7FormId != null && isMigrated(c7FormId, HISTORY_FORM_DEFINITION)) {
          Long formKey = dbClient.findC8KeyByC7IdAndType(c7FormId, HISTORY_FORM_DEFINITION);
          builder.formKey(formKey);
        }
      }

      UserTaskDbModel dbModel = convert(C7Entity.of(c7UserTask), builder);

      if (dbModel.processInstanceKey() == null || dbModel.processDefinitionVersion() == null) {
        throw new EntitySkippedException(c7UserTask, SKIP_REASON_MISSING_PROCESS_INSTANCE);
      }

      if (dbModel.elementInstanceKey() == null) {
        throw new EntitySkippedException(c7UserTask, SKIP_REASON_MISSING_FLOW_NODE);
      }

      if (dbModel.rootProcessInstanceKey() == null) {
        throw new EntitySkippedException(c7UserTask, SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
      }

      if (c7FormId != null && dbModel.formKey() == null) {
        throw new EntitySkippedException(c7UserTask, SKIP_REASON_MISSING_FORM);
      }

      c8Client.insertUserTask(dbModel);
      if (dbModel.tags() != null && !dbModel.tags().isEmpty()) {
        c8Client.insertUserTaskTags(dbModel);
      }

      return dbModel.userTaskKey();
    }

    return null;
  }

}

