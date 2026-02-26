/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_JOB_REFERENCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_INCIDENT;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_JOB;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static org.camunda.bpm.engine.runtime.Incident.FAILED_JOB_HANDLER_TYPE;

import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel.Builder;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import java.util.Date;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.camunda.bpm.engine.history.HistoricIncident;
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
   *   <li>Process instance key missing - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE_KEY}</li>
   *   <li>Process definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *   <li>Root process instance not yet migrated (when part of a process hierarchy) - skipped with {@code SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE}</li>
   *   <li>Referenced job explicitly skipped - skipped with {@code SKIP_REASON_MISSING_JOB_REFERENCE}</li>
   *   <li>Interceptor error during conversion - skipped with the exception message</li>
   * </ul>
   *
   * @param c7Incident the historic incident from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion (handled internally, entity marked as skipped)
   */
  @Override
  public Long migrateTransactionally(HistoricIncident c7Incident) {
    var c7IncidentId = c7Incident.getId();
    if (shouldMigrate(c7IncidentId, HISTORY_INCIDENT)) {
      HistoryMigratorLogs.migratingHistoricIncident(c7IncidentId);
      var c7ProcessInstance = findProcessInstanceByC7Id(c7Incident.getProcessInstanceId());

      var builder = new Builder();
      var processDefinitionKey = findProcessDefinitionKey(c7Incident.getProcessDefinitionId());
      builder.processDefinitionKey(processDefinitionKey);
      if (c7ProcessInstance != null) {
        var processInstanceKey = c7ProcessInstance.processInstanceKey();
        builder.processInstanceKey(processInstanceKey);
        if (processInstanceKey != null) {
          var flowNodeInstanceKey = findFlowNodeInstanceKey(c7Incident.getActivityId(), c7Incident.getProcessInstanceId());
          builder.flowNodeInstanceKey(flowNodeInstanceKey);

          String c7RootProcessInstanceId = c7Incident.getRootProcessInstanceId();
          if (c7RootProcessInstanceId != null && isMigrated(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
            ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
            if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
              builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey());
            }
          }
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
      if (!c7Client.hasWaitingExecution(c7Incident.getProcessInstanceId(), c7Incident.getActivityId())) { // Activities on async before waiting state will not have a flow node instance key, but should not be skipped
        throw new EntitySkippedException(c7Incident, SKIP_REASON_MISSING_FLOW_NODE);
      }
    }

    if (dbModel.jobKey() == null) {
      throw new EntitySkippedException(c7Incident, SKIP_REASON_MISSING_JOB_REFERENCE);
    }
      c8Client.insertIncident(dbModel);

      return dbModel.incidentKey();
    }

    return null;
  }

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

  /**
   * Resolves and sets the job key on the incident builder for {@code failedJob} incidents.
   * <p>
   * If the incident is a {@code failedJob} incident and the associated C7 job has been tracked
   * in the migration table:
   * <ul>
   *   <li>If the job was migrated (has a C8 key), sets the {@code jobKey} on the builder.</li>
   *   <li>If the job was explicitly skipped (null C8 key), throws an
   *       {@link EntitySkippedException} to skip this incident as well.</li>
   * </ul>
   * If the job is not yet tracked (job migration may not have run or the job type is not
   * tracked), the incident proceeds without a job key.
   * </p>
   *
   * @param c7Incident the Camunda 7 incident
   * @param builder    the incident builder to set the job key on
   * @throws EntitySkippedException if the associated job was explicitly skipped
   */
  protected void resolveJobKey(final HistoricIncident c7Incident, final Builder builder) {
    if (!isFailedJobIncident(c7Incident)) {
      return;
    }
    final String c7JobId = c7Incident.getConfiguration();
    if (c7JobId == null) {
      return;
    }
    if (dbClient.checkExistsByC7IdAndType(c7JobId, HISTORY_JOB)) {
      final Long jobKey = dbClient.findC8KeyByC7IdAndType(c7JobId, HISTORY_JOB);
      builder.jobKey(jobKey);
    }
  }

  /**
   * Returns true if this incident was caused by a job failure.
   *
   * @param c7Incident the Camunda 7 incident
   * @return true for {@code failedJob} incident type
   */
  protected boolean isFailedJobIncident(final HistoricIncident c7Incident) {
    return FAILED_JOB_HANDLER_TYPE.equals(c7Incident.getIncidentType());
  }

}

