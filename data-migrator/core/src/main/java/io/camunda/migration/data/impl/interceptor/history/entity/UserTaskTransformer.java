/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import io.camunda.migration.data.interceptor.EntityInterceptor;
import org.camunda.bpm.engine.history.HistoricTaskInstance;

import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static io.camunda.db.rdbms.write.domain.UserTaskDbModel.*;
import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

@Order(4)
@Component
public class UserTaskTransformer implements EntityInterceptor<HistoricTaskInstance, Builder> {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricTaskInstance.class);
  }

  @Override
  public void execute(HistoricTaskInstance entity, Builder builder) {
    builder.userTaskKey(getNextKey())
        .elementId(entity.getTaskDefinitionKey())
        .processDefinitionId(prefixDefinitionId(entity.getProcessDefinitionKey()))
        .creationDate(convertDate(entity.getStartTime()))
        .assignee(entity.getAssignee())
        .state(convertState(entity.getTaskState()))
        .tenantId(getTenantId(entity.getTenantId()))
        .dueDate(convertDate(entity.getDueDate()))
        .followUpDate(convertDate(entity.getFollowUpDate()))
        .priority(entity.getPriority())
        .formKey(null) // TODO  https://github.com/camunda/camunda-bpm-platform/issues/5347
        .candidateGroups(null) //TODO ?
        .candidateUsers(null) //TODO ?
        .externalFormReference(null) //TODO ?
        .customHeaders(null) //TODO ?
        .partitionId(C7_HISTORY_PARTITION_ID)
        .name(entity.getName());
    // Note: processDefinitionKey, processInstanceKey, elementInstanceKey, and processDefinitionVersion are set externally
  }

  // See TaskEntity.TaskState
  protected UserTaskState convertState(String state) {
    return switch (state) {
      case "Init", "Created", "Updated", "Deleted"  -> UserTaskState.CANCELED;
      case "Completed" -> UserTaskState.COMPLETED;

      default -> throw new IllegalArgumentException("Unknown state: " + state);
    };
  }

}
