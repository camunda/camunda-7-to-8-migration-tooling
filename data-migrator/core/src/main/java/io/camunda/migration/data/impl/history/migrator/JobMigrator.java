/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_CANNOT_DETERMINE_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_UNSUPPORTED_JOBS;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigratingJob;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_JOB;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static org.camunda.bpm.engine.impl.jobexecutor.MessageJobDeclaration.ASYNC_AFTER;
import static org.camunda.bpm.engine.impl.jobexecutor.MessageJobDeclaration.ASYNC_BEFORE;

import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.C8EntityNotFoundException;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.camunda.bpm.engine.history.HistoricJobLog;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating historic job log entries from Camunda 7 to Camunda 8.
 * <p>
 * Job logs in Camunda 7 record lifecycle events (creation, execution, failure, deletion) for each
 * job. This migrator converts C7 job log entries to C8 {@link JobDbModel} records, tracking each
 * C7 job by its job ID so that only one C8 record is created per C7 job.
 * </p>
 * <p>
 * The C8 job key is stored in the migration tracking table keyed by the C7 job ID. This allows
 * the {@link IncidentMigrator} to look up the C8 job key when migrating {@code failedJob}
 * incidents.
 * </p>
 */
@Service
public class JobMigrator extends HistoryEntityMigrator<HistoricJobLog, JobDbModel> {

  @Override
  public BiConsumer<Consumer<HistoricJobLog>, Date> fetchForMigrateHandler() {
    return c7Client::fetchAndHandleHistoricJobLogs;
  }

  @Override
  public Function<String, HistoricJobLog> fetchForRetryHandler() {
    return c7Client::getHistoricJobLog;
  }

  @Override
  public IdKeyMapper.TYPE getType() {
    return HISTORY_JOB;
  }

  /**
   * Migrates a single historic job log entry from Camunda 7 to Camunda 8.
   * <p>
   * Uses the C7 job ID as the tracking key, ensuring that only one C8 job record is created
   * per C7 job across multiple log entries.
   * </p>
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Job already tracked in the migration table - silently skipped, returns {@code null}</li>
   *   <li>Unsupported job type (not async-before or async-after) - skipped with
   *       {@code SKIP_REASON_UNSUPPORTED_JOBS}</li>
   *   <li>Process definition not yet migrated - skipped with
   *       {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *   <li>Process instance not yet migrated - skipped with
   *       {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *   <li>Root process instance not yet migrated (when part of a process hierarchy) - skipped with
   *       {@code SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE}</li>
   * </ul>
   *
   * @param c7JobLog the Camunda 7 historic job log entry to migrate
   * @return the C8 job key, or {@code null} if already migrated
   */
  @Override
  public MigrationResult migrateTransactionally(HistoricJobLog c7JobLog) {
    String c7JobId = c7JobLog.getJobId();
    if (shouldMigrate(c7JobId, HISTORY_JOB)) {
      AtomicBoolean hasMultipleFlowNodes = new AtomicBoolean(false);
      String jobDefinitionConfiguration = c7JobLog.getJobDefinitionConfiguration();
      logMigratingJob(c7JobId);
      boolean isAsyncBefore = ASYNC_BEFORE.equals(jobDefinitionConfiguration);
      boolean isAsyncAfter = ASYNC_AFTER.equals(jobDefinitionConfiguration);
      if (!isAsyncBefore && !isAsyncAfter) {
        throw new EntitySkippedException(c7JobLog, SKIP_REASON_UNSUPPORTED_JOBS);
      }

      var jobKey = getNextKey();
      var builder = new JobDbModel.Builder().jobKey(jobKey);

      var processDefinitionKey = findProcessDefinitionKey(c7JobLog.getProcessDefinitionId());
      builder.processDefinitionKey(processDefinitionKey);
      String c7ProcessInstanceId = c7JobLog.getProcessInstanceId();
      ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7ProcessInstanceId);
      if (processInstance != null) {
        builder.processInstanceKey(processInstance.processInstanceKey());

        String c7RootProcessInstanceId = c7JobLog.getRootProcessInstanceId();
        if (c7RootProcessInstanceId != null && isMigrated(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
          ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
          if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
            builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey())
                .partitionId(partitionSupplier.getPartitionIdByRootProcessInstance(c7RootProcessInstanceId));
          }
        }

        Long elementInstanceKey = findFlowNodeInstanceKey(c7JobLog.getActivityId(), c7ProcessInstanceId,
            hasMultipleFlowNodes);
        if (elementInstanceKey != null) {
          builder.elementInstanceKey(elementInstanceKey);
        }
      }

      JobDbModel dbModel = convert(C7Entity.of(c7JobLog), builder);

      if (dbModel.processDefinitionKey() == null) {
        throw new EntitySkippedException(c7JobLog, SKIP_REASON_MISSING_PROCESS_DEFINITION);
      }

      if (dbModel.processInstanceKey() == null) {
        throw new EntitySkippedException(c7JobLog, SKIP_REASON_MISSING_PROCESS_INSTANCE);
      }

      if (dbModel.rootProcessInstanceKey() == null) {
        throw new EntitySkippedException(c7JobLog, SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
      }

      if (hasMultipleFlowNodes.get() && dbModel.elementInstanceKey() == null) {
        throw new EntitySkippedException(c7JobLog, SKIP_REASON_CANNOT_DETERMINE_FLOW_NODE);
      }

      // For async-after jobs, element instance key is required
      if (isAsyncAfter && dbModel.elementInstanceKey() == null) {
        throw new C8EntityNotFoundException(HISTORY_FLOW_NODE, dbModel.processInstanceKey(), c7JobLog.getActivityId());
      }

      c8Client.insertJob(dbModel);

      return MigrationResult.of(jobKey);
    }

    return null;
  }

}
