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
import org.camunda.bpm.engine.history.HistoricJobLog;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Transformer for converting Camunda 7 HistoricJobLog to Camunda 8 JobDbModel.
 * <p>
 * This transformer handles the conversion of historic job log entries from Camunda 7
 * to the Camunda 8 job format. It maps job state, retries, error details, timing, and
 * contextual information.
 * </p>
 * <p>
 * Note: Fields requiring database lookups (processDefinitionKey, processInstanceKey,
 * rootProcessInstanceKey, elementInstanceKey, jobKey) are set externally by JobMigrator.
 * </p>
 */
@Order(13)
@Component
public class JobTransformer implements EntityInterceptor<HistoricJobLog, JobDbModel.Builder> {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricJobLog.class);
  }

  /**
   * Executes the transformation of a Camunda 7 HistoricJobLog to Camunda 8 JobDbModel.
   *
   * @param historicJobLog the Camunda 7 historic job log entry to transform
   * @param builder        the Camunda 8 job builder to populate with converted data
   */
  @Override
  public void execute(final HistoricJobLog historicJobLog, final JobDbModel.Builder builder) {
    final var creationTime = convertDate(historicJobLog.getTimestamp());

    builder
        .type(historicJobLog.getJobDefinitionType())
        .worker(historicJobLog.getHostname())
        .state(convertState(historicJobLog))
        .kind(JobKind.BPMN_ELEMENT)
        .listenerEventType(ListenerEventType.UNSPECIFIED)
        .retries(historicJobLog.getJobRetries())
        .hasFailedWithRetriesLeft(historicJobLog.isFailureLog() && historicJobLog.getJobRetries() > 0)
        .errorMessage(historicJobLog.getJobExceptionMessage())
        .deadline(convertDate(historicJobLog.getJobDueDate()))
        .endTime(isTerminalState(historicJobLog) ? creationTime : null)
        .processDefinitionId(prefixDefinitionId(historicJobLog.getProcessDefinitionKey()))
        .elementId(historicJobLog.getActivityId())
        .tenantId(getTenantId(historicJobLog.getTenantId()))
        .partitionId(C7_HISTORY_PARTITION_ID)
        .creationTime(creationTime)
        .lastUpdateTime(creationTime);
    // Note: jobKey, processDefinitionKey, processInstanceKey, rootProcessInstanceKey,
    // and elementInstanceKey are set externally in JobMigrator.
  }

  /**
   * Converts the Camunda 7 job log state flags to a Camunda 8 JobState.
   *
   * @param historicJobLog the Camunda 7 historic job log entry
   * @return the corresponding Camunda 8 job state
   */
  protected JobState convertState(final HistoricJobLog historicJobLog) {
    if (historicJobLog.isCreationLog()) {
      return JobState.CREATED;
    } else if (historicJobLog.isSuccessLog()) {
      return JobState.COMPLETED;
    } else if (historicJobLog.isFailureLog()) {
      return historicJobLog.getJobRetries() == 0 ? JobState.FAILED : JobState.ERROR_THROWN;
    } else if (historicJobLog.isDeletionLog()) {
      return JobState.CANCELED;
    }
    return JobState.CREATED;
  }

  /**
   * Returns true if this log entry represents a terminal job state (end time should be set).
   *
   * @param historicJobLog the Camunda 7 historic job log entry
   * @return true if this represents a terminal state
   */
  protected boolean isTerminalState(final HistoricJobLog historicJobLog) {
    return historicJobLog.isSuccessLog() ||
        historicJobLog.isDeletionLog() ||
        (historicJobLog.isFailureLog() && historicJobLog.getJobRetries() == 0);
  }
}
