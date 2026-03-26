/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_CANNOT_DETERMINATE_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_JOB_REFERENCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_INCIDENT;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_JOB;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_EXTERNAL_TASK;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static org.camunda.bpm.engine.runtime.Incident.FAILED_JOB_HANDLER_TYPE;

import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel.Builder;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.camunda.bpm.engine.runtime.Incident;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating incidents from Camunda 7 to Camunda 8.
 */
@Service
public class IncidentMigrator extends HistoryEntityMigrator<HistoricIncident, IncidentDbModel> {

  @Override
  public BiConsumer<Consumer<HistoricIncident>, Date> fetchForMigrateHandler() {
    return c7Client::fetchAndHandleHistoricIncidents;
  }

  @Override
  public Function<String, HistoricIncident> fetchForRetryHandler() {
    return c7Client::getHistoricIncident;
  }

  @Override
  public IdKeyMapper.TYPE getType() {
    return HISTORY_INCIDENT;
  }

  /**
   * Migrates a historic incident from Camunda 7 to Camunda 8.
   *
   * <p>Incidents represent errors or exceptional conditions that occurred during process execution.
   * This method validates that all parent entities (process instance, process definition)
   * have been migrated before attempting to migrate the incident.
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *   <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *   <li>Root process instance not yet migrated (when part of a process hierarchy) - skipped with {@code SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE}</li>
   *   <li>Flow node instance not found and activity is not in async-before waiting state - skipped with {@code SKIP_REASON_MISSING_FLOW_NODE}</li>
   *   <li>Failed job incident whose referenced job was skipped (no C8 key) - skipped with {@code SKIP_REASON_MISSING_JOB_REFERENCE}.
   *       Propagated incidents in parent process instances (no job reference) are migrated without a job key.</li>
   * </ul>
   *
   * @param c7Incident the historic incident from Camunda 7 to be migrated
   * @return the C8 incident key if the incident was migrated, or {@code null} if it was already migrated
   * * @throws EntityInterceptorException if an error occurs during entity conversion (handled internally, entity marked as skipped)
   */
  @Override
  public MigrationResult migrateTransactionally(HistoricIncident c7Incident) {
    var c7IncidentId = c7Incident.getId();
    if (shouldMigrate(c7IncidentId, HISTORY_INCIDENT)) {
      AtomicBoolean hasMultipleFlowNodes = new AtomicBoolean(false);
      HistoryMigratorLogs.migratingHistoricIncident(c7IncidentId);
      var c7ProcessInstance = findProcessInstanceByC7Id(c7Incident.getProcessInstanceId());
      Long processInstanceKey;
      Long flowNodeInstanceKey;

      var builder = new Builder();
      var processDefinitionKey = findProcessDefinitionKey(c7Incident.getProcessDefinitionId());
      builder.processDefinitionKey(processDefinitionKey);
      if (c7ProcessInstance != null) {
        processInstanceKey = c7ProcessInstance.processInstanceKey();
        builder.processInstanceKey(processInstanceKey);
        if (processInstanceKey != null) {
          flowNodeInstanceKey = findFlowNodeInstanceKey(c7Incident.getActivityId(), c7Incident.getProcessInstanceId(),
              hasMultipleFlowNodes);
          builder.flowNodeInstanceKey(flowNodeInstanceKey);

          String c7RootProcessInstanceId = c7Incident.getRootProcessInstanceId();
          if (c7RootProcessInstanceId != null && isMigrated(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
            ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
            if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
              builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey())
                  .partitionId(partitionSupplier.getPartitionIdByRootProcessInstance(c7RootProcessInstanceId));
            }
          }
          builder.treePath(generateTreePath(processInstanceKey, flowNodeInstanceKey));
        }
      }

      resolveJobKey(c7Incident, builder);

      IncidentDbModel dbModel = convert(C7Entity.of(c7Incident), builder);

      if (dbModel.processDefinitionKey() == null) {
        throw new EntitySkippedException(c7Incident, SKIP_REASON_MISSING_PROCESS_DEFINITION);
      }

      if (dbModel.processInstanceKey() == null) {
        throw new EntitySkippedException(c7Incident, SKIP_REASON_MISSING_PROCESS_INSTANCE);
      }

      if (dbModel.rootProcessInstanceKey() == null) {
        throw new EntitySkippedException(c7Incident, SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
      }

      if (dbModel.flowNodeInstanceKey() == null) {
        if (hasMultipleFlowNodes.get()) {
          // Multi-instance activities produce multiple flow nodes for the same activityId within a process
          // instance, making it impossible to deterministically resolve the correct flow node for this
          // incident. Skip to avoid wrong associations. See https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1103
          throw new EntitySkippedException(c7Incident, SKIP_REASON_CANNOT_DETERMINATE_FLOW_NODE);
        }
        // Activities on async before waiting state will not have a flow node instance key, but should not be skipped
        if (!c7Client.hasWaitingExecution(c7Incident.getProcessInstanceId(), c7Incident.getActivityId())) {
          throw new EntitySkippedException(c7Incident, SKIP_REASON_MISSING_FLOW_NODE);
        }
      }

      if ((isFailedJobIncident(c7Incident) || isFailedExternalTaskIncident(c7Incident))
          && c7Incident.getConfiguration() != null && dbModel.jobKey() == null) {
        // Only skip the incident when it has a job reference but the job was not successfully migrated.
        // Incidents without a configuration (e.g. propagated incidents in parent process instances)
        // do not carry a direct job reference and should be migrated without a job key.
        throw new EntitySkippedException(c7Incident, SKIP_REASON_MISSING_JOB_REFERENCE);
      }

      c8Client.insertIncident(dbModel);

      return MigrationResult.of(dbModel.incidentKey());
    }

    return null;
  }

  /**
   * Generates a tree path for incidents in the format: PI_processInstanceKey/FNI_elementInstanceKey (if the
   * elementInstanceKey exists, otherwise PI_processInstanceKey)
   *
   * @param processInstanceKey the process instance key
   * @param elementInstanceKey the flow node instance key
   * @return the tree path string
   */
  public static String generateTreePath(Long processInstanceKey, Long elementInstanceKey) {
    return elementInstanceKey == null ?
        "PI_" + processInstanceKey :
        "PI_" + processInstanceKey + "/FNI_" + elementInstanceKey;
  }

  /**
   * Resolves and sets the job key on the incident builder for {@code failedJob} and
   * {@code failedExternalTask} incidents.
   * <p>
   * If the incident is a {@code failedJob} incident and has a job reference (i.e.
   * {@code configuration} is non-null), the C8 job key is looked up in the migration tracking
   * table and set on the builder. A {@code null} key means the job was explicitly skipped
   * during migration, which will cause the incident to be skipped downstream via
   * {@code SKIP_REASON_MISSING_JOB_REFERENCE}.
   * </p>
   * <p>
   * For {@code failedExternalTask} incidents, the C8 job key is looked up in the
   * {@code HISTORY_EXTERNAL_TASK} tracking table instead.
   * </p>
   * <p>
   * Incidents with a {@code null} configuration are propagated incidents in parent process
   * instances (e.g. from a call activity hierarchy). These incidents do not carry a direct job
   * reference and are migrated without a job key.
   * </p>
   *
   * @param c7Incident the Camunda 7 incident
   * @param builder    the incident builder to set the job key on
   */
  protected void resolveJobKey(HistoricIncident c7Incident, Builder builder) {
    if (!isFailedJobIncident(c7Incident) && !isFailedExternalTaskIncident(c7Incident)) {
      return;
    }
    String c7JobId = c7Incident.getConfiguration();
    if (c7JobId == null) {
      return;
    }
    if (isFailedExternalTaskIncident(c7Incident)) {
      if (dbClient.checkExistsByC7IdAndType(c7JobId, HISTORY_EXTERNAL_TASK)) {
        Long jobKey = dbClient.findC8KeyByC7IdAndType(c7JobId, HISTORY_EXTERNAL_TASK);
        builder.jobKey(jobKey);
      }
    } else if (dbClient.checkExistsByC7IdAndType(c7JobId, HISTORY_JOB)) {
      Long jobKey = dbClient.findC8KeyByC7IdAndType(c7JobId, HISTORY_JOB);
      builder.jobKey(jobKey);
    }
  }

  /**
   * Returns true if this incident was caused by a job failure.
   *
   * @param c7Incident the Camunda 7 incident
   * @return true for {@code failedJob} incident type
   */
  protected boolean isFailedJobIncident(HistoricIncident c7Incident) {
    return FAILED_JOB_HANDLER_TYPE.equals(c7Incident.getIncidentType());
  }

  /**
   * Returns true if this incident was caused by an external task failure.
   *
   * @param c7Incident the Camunda 7 incident
   * @return true for {@code failedExternalTask} incident type
   */
  protected boolean isFailedExternalTaskIncident(HistoricIncident c7Incident) {
    return Incident.EXTERNAL_TASK_HANDLER_TYPE.equals(c7Incident.getIncidentType());
  }
}

