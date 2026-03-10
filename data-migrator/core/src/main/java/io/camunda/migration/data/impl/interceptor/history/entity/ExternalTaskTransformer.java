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

/**
 * Transformer for converting Camunda 7 HistoricExternalTaskLog to Camunda 8 JobDbModel.
 * <p>
 * This transformer handles the conversion of historic external task log entries from Camunda 7
 * to the Camunda 8 job format. The topic name maps to the job type, and the worker ID maps to
 * the worker field.
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
   * @param externalTaskLog the Camunda 7 historic external task log entry to transform
   * @param builder         the Camunda 8 job builder to populate with converted data
   */
  @Override
  public void execute(final HistoricExternalTaskLog externalTaskLog, final JobDbModel.Builder builder) {
    builder
        .type(externalTaskLog.getTopicName())
        .worker(externalTaskLog.getWorkerId())
        .state(JobState.COMPLETED)
        .kind(JobKind.BPMN_ELEMENT)
        .listenerEventType(ListenerEventType.UNSPECIFIED)
        .retries(0)
        .processDefinitionId(prefixDefinitionId(externalTaskLog.getProcessDefinitionKey()))
        .elementId(externalTaskLog.getActivityId())
        .tenantId(getTenantId(externalTaskLog.getTenantId()))
        .partitionId(C7_HISTORY_PARTITION_ID)
        .creationTime(convertDate(externalTaskLog.getTimestamp()));
    // Note: jobKey, processDefinitionKey, processInstanceKey, rootProcessInstanceKey,
    // and elementInstanceKey are set externally in ExternalTaskMigrator.
  }
}
