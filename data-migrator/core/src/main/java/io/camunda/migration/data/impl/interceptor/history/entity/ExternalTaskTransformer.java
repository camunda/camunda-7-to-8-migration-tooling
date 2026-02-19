/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricExternalTaskLog;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(13)
@Component
public class ExternalTaskTransformer implements EntityInterceptor<HistoricExternalTaskLog, JobDbModel.Builder> {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricExternalTaskLog.class);
  }

  @Override
  public void execute(HistoricExternalTaskLog entity, JobDbModel.Builder builder) {
    boolean isFailureLog = entity.isFailureLog();
    Integer retries = entity.getRetries();
    boolean hasFailedWithRetriesLeft = isFailureLog && retries != null && retries > 0;

    builder
        .type(entity.getTopicName())
        .worker(entity.getWorkerId())
        .state(convertState(entity))
        .kind(JobKind.BPMN_ELEMENT)
        .listenerEventType(ListenerEventType.UNSPECIFIED)
        .retries(retries)
        .hasFailedWithRetriesLeft(hasFailedWithRetriesLeft)
        .errorMessage(entity.getErrorMessage())
        .deadline(null)
        .endTime(entity.isCreationLog() ? null : convertDate(entity.getTimestamp()))
        .creationTime(entity.isCreationLog() ? convertDate(entity.getTimestamp()) : null)
        .lastUpdateTime(convertDate(entity.getTimestamp()))
        .processDefinitionId(prefixDefinitionId(entity.getProcessDefinitionKey()))
        .elementId(entity.getActivityId())
        .tenantId(getTenantId(entity.getTenantId()))
        .partitionId(C7_HISTORY_PARTITION_ID);
    // Note: jobKey, processDefinitionKey, processInstanceKey, rootProcessInstanceKey, elementInstanceKey
    // are set externally in ExternalTaskMigrator
  }

  protected JobState convertState(HistoricExternalTaskLog entity) {
    if (entity.isCreationLog()) {
      return JobState.CREATED;
    } else if (entity.isFailureLog()) {
      return JobState.FAILED;
    } else if (entity.isSuccessLog()) {
      return JobState.COMPLETED;
    } else {
      return JobState.CANCELED;
    }
  }

}
