/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_CANNOT_DETERMINATE_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigratingExternalTask;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_EXTERNAL_TASK;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.sanitizeFlowNodeId;

import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.camunda.bpm.engine.history.HistoricExternalTaskLog;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating historic external task log entries from Camunda 7 to Camunda 8.
 * <p>
 * External task logs in Camunda 7 record lifecycle events (creation, execution, failure, deletion)
 * for each external task. This migrator converts C7 external task log entries to C8
 * {@link JobDbModel} records, tracking each C7 external task by its external task ID so that
 * only one C8 record is created per C7 external task.
 * </p>
 * <p>
 * The C8 job key is stored in the migration tracking table keyed by the C7 external task ID.
 * This allows the {@link IncidentMigrator} to look up the C8 job key when migrating
 * {@code failedExternalTask} incidents.
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
   * Migrates a single historic external task log entry from Camunda 7 to Camunda 8.
   * <p>
   * Uses the C7 external task ID as the tracking key, ensuring that only one C8 job record is
   * created per C7 external task across multiple log entries.
   * </p>
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>External task already tracked in the migration table - silently skipped, returns
   *       {@code null}</li>
   *   <li>Process definition not yet migrated - skipped with
   *       {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *   <li>Process instance not yet migrated - skipped with
   *       {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *   <li>Root process instance not yet migrated (when part of a process hierarchy) - skipped with
   *       {@code SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE}</li>
   * </ul>
   *
   * @param c7ExternalTaskLog the Camunda 7 historic external task log entry to migrate
   * @return the C8 job key, or {@code null} if already migrated
   */
  @Override
  public MigrationResult migrateTransactionally(final HistoricExternalTaskLog c7ExternalTaskLog) {
    final String c7ExternalTaskId = c7ExternalTaskLog.getExternalTaskId();
    if (shouldMigrate(c7ExternalTaskId, HISTORY_EXTERNAL_TASK)) {
      final AtomicBoolean hasMultipleFlowNodes = new AtomicBoolean(false);
      logMigratingExternalTask(c7ExternalTaskId);

      final var jobKey = getNextKey();
      final var builder = new JobDbModel.Builder().jobKey(jobKey);

      final var processDefinitionKey = findProcessDefinitionKey(c7ExternalTaskLog.getProcessDefinitionId());
      builder.processDefinitionKey(processDefinitionKey);

      final String c7ProcessInstanceId = c7ExternalTaskLog.getProcessInstanceId();
      final ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7ProcessInstanceId);
      if (processInstance != null) {
        builder.processInstanceKey(processInstance.processInstanceKey());

        final String c7RootProcessInstanceId = c7ExternalTaskLog.getRootProcessInstanceId();
        if (c7RootProcessInstanceId != null && isMigrated(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
          final ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
          if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
            builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey())
            .partitionId(partitionSupplier.getPartitionIdByRootProcessInstance(c7RootProcessInstanceId));
          }
        }

        Long elementInstanceKey = findFlowNodeInstanceKey(
            c7ExternalTaskLog.getActivityId(), c7ProcessInstanceId, hasMultipleFlowNodes);
        if (elementInstanceKey != null) {
          builder.elementInstanceKey(elementInstanceKey);
        }
      }

      final JobDbModel dbModel = convert(C7Entity.of(c7ExternalTaskLog), builder);

      if (dbModel.processDefinitionKey() == null) {
        throw new EntitySkippedException(c7ExternalTaskLog, SKIP_REASON_MISSING_PROCESS_DEFINITION);
      }

      if (dbModel.processInstanceKey() == null) {
        throw new EntitySkippedException(c7ExternalTaskLog, SKIP_REASON_MISSING_PROCESS_INSTANCE);
      }

      if (dbModel.rootProcessInstanceKey() == null) {
        throw new EntitySkippedException(c7ExternalTaskLog, SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
      }

      if (hasMultipleFlowNodes.get() && dbModel.elementInstanceKey() == null) {
        throw new EntitySkippedException(c7ExternalTaskLog, SKIP_REASON_CANNOT_DETERMINATE_FLOW_NODE);
      }

      c8Client.insertJob(dbModel);

      return MigrationResult.of(jobKey);
    }

    return null;
  }
}
