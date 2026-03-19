/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static io.camunda.migration.data.impl.util.ConverterUtil.sanitizeFlowNodeId;

import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricExternalTaskLog;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Transformer for converting Camunda 7 HistoricExternalTaskLog to Camunda 8 JobDbModel.
 * <p>
 * This transformer handles the conversion of historic external task log entries from Camunda 7
 * to the Camunda 8 job format. External tasks are mapped as jobs with:
 * <ul>
 *   <li>{@code topicName} mapped to {@code type}</li>
 *   <li>{@code workerId} mapped to {@code worker}</li>
 *   <li>state derived from the log entry type (creation, success, failure, deletion)</li>
 * </ul>
 * </p>
 * <p>
 * Note: Fields requiring database lookups (processDefinitionKey, processInstanceKey,
 * rootProcessInstanceKey, elementInstanceKey, jobKey) are set externally by ExternalTaskMigrator.
 * </p>
 */
@Order(14)
@Component
public class ExternalTaskTransformer implements EntityInterceptor<HistoricExternalTaskLog, JobDbModel.Builder> {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricExternalTaskLog.class);
  }

  /**
   * Executes the transformation of a Camunda 7 HistoricExternalTaskLog to Camunda 8 JobDbModel.
   *
   * @param entity  the Camunda 7 historic external task log entry to transform
   * @param builder the Camunda 8 job builder to populate with converted data
   */
  @Override
  public void execute(final HistoricExternalTaskLog entity, final JobDbModel.Builder builder) {
    builder
        .type(entity.getTopicName())
        .worker(entity.getWorkerId())
        .state(deriveState(entity))
        .kind(JobKind.BPMN_ELEMENT)
        .listenerEventType(ListenerEventType.UNSPECIFIED)
        .retries(0)
        .processDefinitionId(prefixDefinitionId(entity.getProcessDefinitionKey()))
        .elementId(sanitizeFlowNodeId(entity.getActivityId()))
        .errorMessage(entity.getErrorMessage())
        .tenantId(getTenantId(entity.getTenantId()))
        .partitionId(C7_HISTORY_PARTITION_ID);
    // Note: jobKey, processDefinitionKey, processInstanceKey, rootProcessInstanceKey,
    // and elementInstanceKey are set externally in ExternalTaskMigrator.
  }

  /**
   * Derives the C8 job state from the type of the external task log entry.
   * <p>
   * For historic records, the first log entry encountered per external task (by timestamp)
   * determines the recorded state. The mapping is:
   * <ul>
   *   <li>Success logs → COMPLETED</li>
   *   <li>Deletion logs → COMPLETED (task deleted/cancelled)</li>
   *   <li>Failure logs → FAILED</li>
   *   <li>Creation logs → COMPLETED (historic records represent completed lifecycle events)</li>
   * </ul>
   * </p>
   *
   * @param entity the historic external task log entry
   * @return the corresponding C8 job state
   */
  protected JobState deriveState(final HistoricExternalTaskLog entity) {
    if (entity.isFailureLog()) {
      return JobState.FAILED;
    }
    // For success logs, deletion logs, and creation logs, the task is considered completed.
    return JobState.COMPLETED;
  }
}
