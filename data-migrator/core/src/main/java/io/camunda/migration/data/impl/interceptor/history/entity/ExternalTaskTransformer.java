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
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static io.camunda.search.entities.JobEntity.JobKind.BPMN_ELEMENT;
import static io.camunda.search.entities.JobEntity.JobState.CANCELED;
import static io.camunda.search.entities.JobEntity.JobState.COMPLETED;
import static io.camunda.search.entities.JobEntity.JobState.CREATED;
import static io.camunda.search.entities.JobEntity.JobState.FAILED;
import static io.camunda.search.entities.JobEntity.ListenerEventType.UNSPECIFIED;

import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.search.entities.JobEntity.JobState;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricExternalTaskLog;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Transforms a Camunda 7 {@link HistoricExternalTaskLog} into a Camunda 8 {@link JobDbModel.Builder}.
 *
 * <p>Each external task in Camunda 7 maps to a single job record in Camunda 8. The most recent
 * log entry for each external task is used to determine the final state.
 *
 * <p>Limitations:
 * <ul>
 *   <li>The {@code deadline} field is not available in the C7 external task log history.</li>
 *   <li>Root process instance key and element instance key are resolved externally in the migrator.</li>
 * </ul>
 */
@Order(13)
@Component
public class ExternalTaskTransformer implements EntityInterceptor<HistoricExternalTaskLog, JobDbModel.Builder> {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricExternalTaskLog.class);
  }

  @Override
  public void execute(HistoricExternalTaskLog entity, JobDbModel.Builder builder) {
    JobState state = determineState(entity);
    boolean hasFailedWithRetriesLeft = entity.isFailureLog()
        && entity.getRetries() != null
        && entity.getRetries() > 0;

    builder
        .jobKey(getNextKey())
        .type(entity.getTopicName())
        .worker(entity.getWorkerId())
        .state(state)
        .kind(BPMN_ELEMENT)
        .listenerEventType(UNSPECIFIED)
        .retries(entity.getRetries())
        .hasFailedWithRetriesLeft(hasFailedWithRetriesLeft)
        .errorMessage(entity.getErrorMessage())
        .elementId(entity.getActivityId())
        .tenantId(getTenantId(entity.getTenantId()))
        .partitionId(C7_HISTORY_PARTITION_ID)
        .processDefinitionId(prefixDefinitionId(entity.getProcessDefinitionKey()))
        .creationTime(convertDate(entity.getTimestamp()))
        .lastUpdateTime(convertDate(entity.getTimestamp()))
        .endTime(state == CREATED ? null : convertDate(entity.getTimestamp()));
    // processDefinitionKey, processInstanceKey, rootProcessInstanceKey,
    // and elementInstanceKey are resolved externally in ExternalTaskMigrator
  }

  protected JobState determineState(HistoricExternalTaskLog entity) {
    if (entity.isSuccessLog()) {
      return COMPLETED;
    }
    if (entity.isDeletionLog()) {
      return CANCELED;
    }
    if (entity.isFailureLog()) {
      return FAILED;
    }
    return CREATED;
  }
}
