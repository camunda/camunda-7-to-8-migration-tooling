/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.converter;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.migrator.exception.EntityInterceptorException;
import io.camunda.migrator.interceptor.EntityInterceptor;
import io.camunda.migrator.interceptor.property.EntityConversionContext;
import org.camunda.bpm.engine.history.HistoricTaskInstance;

import java.util.Set;

import static io.camunda.migrator.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migrator.impl.util.ConverterUtil.convertDate;
import static io.camunda.migrator.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migrator.impl.util.ConverterUtil.getTenantId;

public class UserTaskConverter implements EntityInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricTaskInstance.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    HistoricTaskInstance historicTask = (HistoricTaskInstance) context.getC7Entity();
    UserTaskDbModel.Builder builder = (UserTaskDbModel.Builder) context.getC8DbModelBuilder();

    if (builder == null) {
      throw new EntityInterceptorException("C8 UserTaskDbModel.Builder is null in context");
    }

    builder.userTaskKey(getNextKey())
        .elementId(historicTask.getTaskDefinitionKey())
        .processDefinitionId(historicTask.getProcessDefinitionKey())
        .creationDate(convertDate(historicTask.getStartTime()))
        .completionDate(convertDate(historicTask.getEndTime()))
        .assignee(historicTask.getAssignee())
        .state(convertState(historicTask.getTaskState()))
        .tenantId(getTenantId(historicTask.getTenantId()))
        .dueDate(convertDate(historicTask.getDueDate()))
        .followUpDate(convertDate(historicTask.getFollowUpDate()))
        .priority(historicTask.getPriority())
        .formKey(null) // TODO  https://github.com/camunda/camunda-bpm-platform/issues/5347
        .candidateGroups(null) //TODO ?
        .candidateUsers(null) //TODO ?
        .externalFormReference(null) //TODO ?
        .customHeaders(null) //TODO ?
        .historyCleanupDate(convertDate(historicTask.getRemovalTime()))
        .partitionId(C7_HISTORY_PARTITION_ID)
        .name(historicTask.getName());
    // Note: processDefinitionKey, processInstanceKey, elementInstanceKey, and processDefinitionVersion are set externally
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
