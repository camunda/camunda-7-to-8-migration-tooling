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
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigratingCandidateGroups;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigratingCandidateUsers;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigratingCandidates;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.camunda.bpm.engine.history.HistoricIdentityLinkLog;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.task.IdentityLinkType;
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
  public MigrationResult migrateTransactionally(HistoricTaskInstance c7UserTask) {
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
              .processDefinitionVersion(processInstance.processDefinitionVersion())
              .partitionId(partitionSupplier.getPartitionIdByRootProcessInstance(c7UserTask.getRootProcessInstanceId()));
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

      migrateCandidateAssignments(c7UserTaskId, dbModel);

      return MigrationResult.of(dbModel.userTaskKey());
    }

    return null;
  }

  /**
   * Computes the current set of candidateUsers and candidateGroups for a userTask using {@link HistoricIdentityLinkLog}
   * entries and persists them to C8.
   *
   * @param c7UserTaskId the Camunda 7 userTask ID used to query identityLinkLogs
   * @param dbModel      the C8 user task model
   */
  public void migrateCandidateAssignments(String c7UserTaskId, UserTaskDbModel dbModel) {
    logMigratingCandidates(c7UserTaskId);
    List<HistoricIdentityLinkLog> identityLinkLogs = c7Client.getHistoricIdentityLinkLogs(c7UserTaskId);

    List<String> candidateUsers = computeCurrentCandidates(identityLinkLogs, true);
    List<String> candidateGroups = computeCurrentCandidates(identityLinkLogs, false);

    if (!candidateUsers.isEmpty()) {
      logMigratingCandidateUsers(candidateUsers.size(), c7UserTaskId);
      dbModel.candidateUsers(candidateUsers);
      c8Client.insertCandidateUsers(dbModel);
    }

    if (!candidateGroups.isEmpty()) {
      logMigratingCandidateGroups(candidateGroups.size(), c7UserTaskId);
      dbModel.candidateGroups(candidateGroups);
      c8Client.insertCandidateGroups(dbModel);
    }
  }

  /**
   * Computes which candidate users or groups are currently assigned to a userTask based on its identityLinkLogs.
   *
   * <p>Only entries with {@code type = "candidate"} are considered. Within those:
   * <ul>
   *   <li>{@code operationType = "add"} adds the user/group to the active set</li>
   *   <li>{@code operationType = "delete"} removes the user/group from the active set</li>
   * </ul>
   *
   * @param logs    identityLinkLog entries ordered by time ascending
   * @param isUsers {@code true} to compute candidateUsers
   *                {@code false} to compute candidateGroups
   * @return a list of currently active candidate user/group IDs
   */
  public List<String> computeCurrentCandidates(List<HistoricIdentityLinkLog> logs, boolean isUsers) {
    HashSet<String> assignedIdentityIds = new HashSet<>();

    for (HistoricIdentityLinkLog log : logs) {
      if (!IdentityLinkType.CANDIDATE.equals(log.getType())) {
        continue;
      }

      String id = isUsers ? log.getUserId() : log.getGroupId();
      if (id == null) {
        continue;
      }

      if ("add".equals(log.getOperationType())) {
        assignedIdentityIds.add(id);
      } else if ("delete".equals(log.getOperationType())) {
        assignedIdentityIds.remove(id);
      }
    }

    return new ArrayList<>(assignedIdentityIds);
  }

}
