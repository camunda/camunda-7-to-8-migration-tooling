/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_INCIDENT;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;

import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel.Builder;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.search.entities.ProcessInstanceEntity;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating incidents from Camunda 7 to Camunda 8.
 */
@Service
public class IncidentMigrator extends BaseMigrator<HistoricIncident, IncidentDbModel> {

  @Override
  public void migrateAll() {
    fetchAndRetry(
        HISTORY_INCIDENT,
        c7Client::getHistoricIncident,
        c7Client::fetchAndHandleHistoricIncidents
    );
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
   *   <li>Interceptor error during conversion - skipped with the exception message</li>
   * </ul>
   *
   * <p><strong>Note:</strong> Flow node instance and job reference validations are currently disabled
   * pending resolution of known issues. See code comments for details.
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
      if (c7ProcessInstance != null) {
        var processInstanceKey = c7ProcessInstance.processInstanceKey();
        builder.processInstanceKey(processInstanceKey);
        if (processInstanceKey != null) {
          var flowNodeInstanceKey = findFlowNodeInstanceKey(c7Incident.getActivityId(),
              c7Incident.getProcessInstanceId());
          var processDefinitionKey = findProcessDefinitionKey(c7Incident.getProcessDefinitionId());
          Long jobDefinitionKey = null; // TODO jobs are not migrated yet

          builder.processDefinitionKey(processDefinitionKey)
              .jobKey(jobDefinitionKey)
              .flowNodeInstanceKey(flowNodeInstanceKey);

          String c7RootProcessInstanceId = c7Incident.getRootProcessInstanceId();
          if (c7RootProcessInstanceId != null && isMigrated(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
            ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
            if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
              builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey());
            }
          }
        }
      }

      IncidentDbModel dbModel = convert(C7Entity.of(c7Incident), builder);

      if (dbModel.processInstanceKey() == null) {
        throw new EntitySkippedException(c7Incident, SKIP_REASON_MISSING_PROCESS_INSTANCE);
        // TODO: https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/364
        // check if flowNodeInstanceKey is resolved correctly
        // } else if (dbModel.flowNodeInstanceKey() == null) {
        //   markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(), SKIP_REASON_MISSING_SCOPE_KEY);
        //   HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
        // TODO: always null at the moment because jobs are not migrated yet
        //  } else if (dbModel.jobKey() == null) {
        //    markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(), SKIP_REASON_MISSING_JOB_REFERENCE);
        //    HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
      }

      if (dbModel.processDefinitionKey() == null) {
        throw new EntitySkippedException(c7Incident, SKIP_REASON_MISSING_PROCESS_DEFINITION);
      }

      if (dbModel.rootProcessInstanceKey() == null) {
        throw new EntitySkippedException(c7Incident, SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
      }

      c8Client.insertIncident(dbModel);

      return dbModel.incidentKey();
    }

    return null;
  }

}

