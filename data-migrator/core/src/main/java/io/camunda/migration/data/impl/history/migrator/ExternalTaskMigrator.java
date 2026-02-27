/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigratingExternalTask;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_EXTERNAL_TASK;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.Date;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.camunda.bpm.engine.history.HistoricExternalTaskLog;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating historic external task logs from Camunda 7 to Camunda 8.
 * <p>
 * External task logs in Camunda 7 record each state change (creation, failure, success, deletion)
 * of external tasks. This migrator converts these log entries to Camunda 8 job records, preserving
 * the execution history including retries, error messages, worker assignments, and timing information.
 * </p>
 */
@Service
public class ExternalTaskMigrator extends HistoryEntityMigrator<HistoricExternalTaskLog, JobDbModel> {

  @Override
  public BiConsumer<Consumer<HistoricExternalTaskLog>, Date> fetchForMigrateHandler() {
    return c7Client::fetchAndHandleHistoricExternalTaskLogs;
  }

  @Override
  public Function<String, HistoricExternalTaskLog> fetchForRetryHandler() {
    return c7Client::getHistoricExternalTaskLog;
  }

  @Override
  public IdKeyMapper.TYPE getType() {
    return HISTORY_EXTERNAL_TASK;
  }

  /**
   * Migrates a single historic external task log from Camunda 7 to Camunda 8.
   * <p>
   * Each external task log entry represents a state snapshot of an external task job.
   * This method resolves related entity keys (process instance, process definition, flow node)
   * and creates a corresponding job record in Camunda 8.
   * </p>
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *   <li>Process definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *   <li>Root process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE}</li>
   *   <li>Flow node instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_FLOW_NODE}</li>
   *   <li>Interceptor error during conversion - skipped with the exception message</li>
   * </ul>
   *
   * @param c7ExternalTaskLog the historic external task log from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion (handled internally, entity marked as skipped)
   */
  @Override
  public Long migrateTransactionally(HistoricExternalTaskLog c7ExternalTaskLog) {
    var c7LogId = c7ExternalTaskLog.getId();
    if (shouldMigrate(c7LogId, HISTORY_EXTERNAL_TASK)) {
      logMigratingExternalTask(c7LogId);

      var builder = new JobDbModel.Builder();
      builder.jobKey(getNextKey());

      if (isMigrated(c7ExternalTaskLog.getProcessInstanceId(), HISTORY_PROCESS_INSTANCE)) {
        ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7ExternalTaskLog.getProcessInstanceId());
        if (processInstance != null) {
          builder.processInstanceKey(processInstance.processInstanceKey());
        }
        Long processDefinitionKey = findProcessDefinitionKey(c7ExternalTaskLog.getProcessDefinitionId());
        builder.processDefinitionKey(processDefinitionKey);

        String c7RootProcessInstanceId = c7ExternalTaskLog.getRootProcessInstanceId();
        if (c7RootProcessInstanceId != null && isMigrated(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
          ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
          if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
            builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey());
          }
        }

        if (isMigrated(c7ExternalTaskLog.getActivityInstanceId(), HISTORY_FLOW_NODE)) {
          Long elementInstanceKey = findFlowNodeInstanceKey(c7ExternalTaskLog.getActivityInstanceId());
          builder.elementInstanceKey(elementInstanceKey);
        }
      }

      JobDbModel dbModel = convert(C7Entity.of(c7ExternalTaskLog), builder);

      if (dbModel.processInstanceKey() == null) {
        throw new EntitySkippedException(c7ExternalTaskLog, SKIP_REASON_MISSING_PROCESS_INSTANCE);
      }

      if (dbModel.processDefinitionKey() == null) {
        throw new EntitySkippedException(c7ExternalTaskLog, SKIP_REASON_MISSING_PROCESS_DEFINITION);
      }

      if (dbModel.rootProcessInstanceKey() == null) {
        throw new EntitySkippedException(c7ExternalTaskLog, SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
      }

      if (dbModel.elementInstanceKey() == null) {
        throw new EntitySkippedException(c7ExternalTaskLog, SKIP_REASON_MISSING_FLOW_NODE);
      }

      c8Client.insertJob(dbModel);

      return dbModel.jobKey();
    }

    return null;
  }

}
