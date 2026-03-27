/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
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
  public void execute(HistoricExternalTaskLog entity, JobDbModel.Builder builder) {
    var creationTime = convertDate(entity.getTimestamp());

    builder
        .type(entity.getTopicName())
        .state(JobState.COMPLETED)
        .kind(JobKind.BPMN_ELEMENT)
        .creationTime(creationTime)
        .listenerEventType(ListenerEventType.UNSPECIFIED)
        .retries(0)
        .worker(null)
        .processDefinitionId(prefixDefinitionId(entity.getProcessDefinitionKey()))
        .elementId(sanitizeFlowNodeId(entity.getActivityId()))
        .tenantId(getTenantId(entity.getTenantId()));
    // Note: partitionId is set externally by ExternalTaskMigrator to match the parent process instance
    // jobKey, processDefinitionKey, processInstanceKey, rootProcessInstanceKey,
    // and elementInstanceKey are set externally in ExternalTaskMigrator.
  }
}
