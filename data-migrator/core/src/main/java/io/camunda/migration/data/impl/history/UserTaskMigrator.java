/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_USER_TASK;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.time.OffsetDateTime;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating user tasks from Camunda 7 to Camunda 8.
 */
@Service
public class UserTaskMigrator extends BaseMigrator {
  public void migrateUserTasks() {
    HistoryMigratorLogs.migratingHistoricUserTasks();

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_USER_TASK, idKeyDbModel -> {
        HistoricTaskInstance historicTaskInstance = c7Client.getHistoricTaskInstance(idKeyDbModel.getC7Id());
        migrateUserTask(historicTaskInstance);
      });
    } else {
      c7Client.fetchAndHandleHistoricUserTasks(this::migrateUserTask,
          dbClient.findLatestCreateTimeByType(HISTORY_USER_TASK));
    }
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
   * </ul>
   *
   * @param c7UserTask the historic user task from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  public void migrateUserTask(HistoricTaskInstance c7UserTask) {
    String c7UserTaskId = c7UserTask.getId();
    if (shouldMigrate(c7UserTaskId, HISTORY_USER_TASK)) {
      HistoryMigratorLogs.migratingHistoricUserTask(c7UserTaskId);

      try {
        UserTaskDbModel.Builder userTaskDbModelBuilder = new UserTaskDbModel.Builder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7UserTask, HistoricTaskInstance.class,
            userTaskDbModelBuilder);

        if (isMigrated(c7UserTask.getProcessInstanceId(), HISTORY_PROCESS_INSTANCE)) {
          ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7UserTask.getProcessInstanceId());
          if (processInstance != null) {
            userTaskDbModelBuilder.processInstanceKey(processInstance.processInstanceKey())
                .processDefinitionVersion(processInstance.processDefinitionVersion());
            OffsetDateTime historyCleanupDate = calculateHistoryCleanupDateForChild(processInstance.endDate(), c7UserTask.getRemovalTime());
            OffsetDateTime completionDate = calculateCompletionDateForChild(processInstance.endDate(), c7UserTask.getEndTime());
            userTaskDbModelBuilder
                .historyCleanupDate(historyCleanupDate)
                .completionDate(completionDate);
          }
          if (isMigrated(c7UserTask.getActivityInstanceId(), HISTORY_FLOW_NODE)) {
            Long elementInstanceKey = findFlowNodeInstanceKey(c7UserTask.getActivityInstanceId());
            Long processDefinitionKey = findProcessDefinitionKey(c7UserTask.getProcessDefinitionId());
            userTaskDbModelBuilder.processDefinitionKey(processDefinitionKey).elementInstanceKey(elementInstanceKey);
          }
        }
        UserTaskDbModel dbModel = convertUserTask(context);
        if (dbModel.processInstanceKey() == null || dbModel.processDefinitionVersion() == null) {
          markSkipped(c7UserTaskId, TYPE.HISTORY_USER_TASK, c7UserTask.getStartTime(),
              SKIP_REASON_MISSING_PROCESS_INSTANCE);
          HistoryMigratorLogs.skippingHistoricUserTaskDueToMissingProcessInstance(c7UserTaskId);
        } else if (dbModel.elementInstanceKey() == null) {
          markSkipped(c7UserTaskId, TYPE.HISTORY_USER_TASK, c7UserTask.getStartTime(), SKIP_REASON_MISSING_FLOW_NODE);
          HistoryMigratorLogs.skippingHistoricUserTaskDueToMissingFlowNode(c7UserTaskId);
        } else {
          insertUserTask(c7UserTask, dbModel, c7UserTaskId);
        }
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7UserTaskId, HISTORY_USER_TASK, c7UserTask.getStartTime(), e);
      }
    }
  }

  protected UserTaskDbModel convertUserTask(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    UserTaskDbModel.Builder builder = (UserTaskDbModel.Builder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  protected void insertUserTask(HistoricTaskInstance c7UserTask, UserTaskDbModel dbModel, String c7UserTaskId) {
    c8Client.insertUserTask(dbModel);
    markMigrated(c7UserTaskId, dbModel.userTaskKey(), c7UserTask.getStartTime(), HISTORY_USER_TASK);
    HistoryMigratorLogs.migratingHistoricUserTaskCompleted(c7UserTaskId);
  }
}

