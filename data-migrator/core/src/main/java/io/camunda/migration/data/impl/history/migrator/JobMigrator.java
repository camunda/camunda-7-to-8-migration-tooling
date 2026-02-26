/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_UNSUPPORTED_JOBS;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigratingJob;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_JOB;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static org.camunda.bpm.engine.impl.jobexecutor.MessageJobDeclaration.ASYNC_AFTER;
import static org.camunda.bpm.engine.impl.jobexecutor.MessageJobDeclaration.ASYNC_BEFORE;

import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import java.util.Date;
import java.util.List;
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
   *   <li>Job already tracked in the migration table - silently skipped</li>
   *   <li>Process instance not yet migrated - skipped with
   *       {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   * </ul>
   *
   * @param c7JobLog the Camunda 7 historic job log entry to migrate
   * @return the C8 job key as a string, or {@code null} if already migrated
   */
  @Override
  public Long migrateTransactionally(final HistoricJobLog c7JobLog) {
    final String c7JobId = c7JobLog.getJobId();
    if (shouldMigrate(c7JobId, HISTORY_JOB)) {
      String jobDefinitionConfiguration = c7JobLog.getJobDefinitionConfiguration();
      logMigratingJob(c7JobId);
      if (!ASYNC_BEFORE.equals(jobDefinitionConfiguration) || !ASYNC_AFTER.equals(jobDefinitionConfiguration)) {
        throw new EntitySkippedException(c7JobLog, SKIP_REASON_UNSUPPORTED_JOBS);
      }

      final var jobKey = getNextKey();
      final var builder = new JobDbModel.Builder().jobKey(jobKey);

      final String c7ProcessInstanceId = c7JobLog.getProcessInstanceId();
      final ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7ProcessInstanceId);
      if (processInstance != null) {
        builder.processInstanceKey(processInstance.processInstanceKey());

        final var processDefinitionKey = findProcessDefinitionKey(c7JobLog.getProcessDefinitionId());
        builder.processDefinitionKey(processDefinitionKey);

        final String c7RootProcessInstanceId = c7JobLog.getRootProcessInstanceId();
        if (c7RootProcessInstanceId != null && isMigrated(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
          final ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
          if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
            builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey());
          }
        }

        final Long elementInstanceKey = findFlowNodeInstanceKey(
            c7JobLog.getActivityId(), c7ProcessInstanceId);
        builder.elementInstanceKey(elementInstanceKey);
      }

      final JobDbModel dbModel = convert(C7Entity.of(c7JobLog), builder);

      if (dbModel.processInstanceKey() == null) {
        throw new EntitySkippedException(c7JobLog, SKIP_REASON_MISSING_PROCESS_INSTANCE);
      }

      c8Client.insertJob(dbModel);

      return jobKey;
    }

    return null;
  }

  /**
   * Finds the C8 flow node instance key by C7 activity ID and process instance ID.
   * <p>
   * Since {@link HistoricJobLog} provides only the activity ID (not the activity instance ID),
   * this method searches C8 flow node instances by activity ID within the migrated process
   * instance.
   * </p>
   *
   * @param activityId the C7 activity ID
   * @param processInstanceId the C7 process instance ID
   * @return the C8 flow node instance key, or {@code null} if not found
   */
  protected Long findFlowNodeInstanceKey(String activityId, String processInstanceId) {
    Long processInstanceKey = dbClient.findC8KeyByC7IdAndType(processInstanceId, HISTORY_PROCESS_INSTANCE);
    if (processInstanceKey == null) {
      return null;
    }

    List<FlowNodeInstanceDbModel> flowNodes = c8Client.searchFlowNodeInstances(
        FlowNodeInstanceDbQuery.of(builder -> builder.filter(
            FlowNodeInstanceFilter.of(filter -> filter.flowNodeIds(activityId).processInstanceKeys(processInstanceKey))
        ))
    );

    if (!flowNodes.isEmpty()) {
      return flowNodes.getFirst().flowNodeInstanceKey();
    } else {
      return null;
    }
  }
}
