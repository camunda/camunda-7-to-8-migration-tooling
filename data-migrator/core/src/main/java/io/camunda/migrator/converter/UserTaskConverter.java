/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.converter;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.migrator.constants.MigratorConstants;
import io.camunda.migrator.impl.util.ConverterUtil;
import io.camunda.search.entities.ProcessInstanceEntity;
import org.camunda.bpm.engine.history.HistoricTaskInstance;

import static io.camunda.migrator.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migrator.impl.util.ConverterUtil.convertDate;
import static io.camunda.migrator.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migrator.impl.util.ConverterUtil.getTenantId;

public class UserTaskConverter {

  public UserTaskDbModel apply(HistoricTaskInstance historicTask,
                               Long processDefinitionKey,
                               ProcessInstanceEntity processInstance,
                               Long elementInstanceKey) {

    return new UserTaskDbModel.Builder()
        .userTaskKey(getNextKey())
        .elementId(historicTask.getTaskDefinitionKey())
        .processDefinitionId(historicTask.getProcessDefinitionKey())
        .creationDate(convertDate(historicTask.getStartTime()))
        .completionDate(convertDate(historicTask.getEndTime()))
        .assignee(historicTask.getAssignee())
        .state(convertState(historicTask.getTaskState()))
        .processDefinitionKey(processDefinitionKey)
        .processInstanceKey(processInstance.processInstanceKey())
        .tenantId(getTenantId(historicTask.getTenantId()))
        .elementInstanceKey(elementInstanceKey)
        .dueDate(convertDate(historicTask.getDueDate()))
        .followUpDate(convertDate(historicTask.getFollowUpDate()))
        .priority(historicTask.getPriority())
        .processDefinitionVersion(processInstance.processDefinitionVersion())
        .formKey(null) // TODO  https://github.com/camunda/camunda-bpm-platform/issues/5347
        .candidateGroups(null) //TODO ?
        .candidateUsers(null) //TODO ?
        .externalFormReference(null) //TODO ?
        .customHeaders(null) //TODO ?
        .historyCleanupDate(convertDate(historicTask.getRemovalTime()))
        .partitionId(C7_HISTORY_PARTITION_ID)
        .name(historicTask.getName())
        .build();
  }

  // See TaskEntity.TaskState
  protected UserTaskDbModel.UserTaskState convertState(String state) {
    return switch (state) {
      case "Init", "Created" -> UserTaskDbModel.UserTaskState.CREATED;
      case "Completed" -> UserTaskDbModel.UserTaskState.COMPLETED;
      case "Deleted" -> UserTaskDbModel.UserTaskState.CANCELED;
      case "Updated" -> UserTaskDbModel.UserTaskState.CREATED;

      default -> throw new IllegalArgumentException("Unknown state: " + state);
    };
  }

}
