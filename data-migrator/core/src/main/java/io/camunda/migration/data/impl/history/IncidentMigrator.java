/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_JOB_REFERENCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE_KEY;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_SCOPE_KEY;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_INCIDENT;

import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.ProcessInstanceEntity;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating incidents from Camunda 7 to Camunda 8.
 */
@Service
public class IncidentMigrator extends BaseMigrator {

  public void migrateIncidents() {
    HistoryMigratorLogs.migratingHistoricIncidents();
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_INCIDENT, idKeyDbModel -> {
        HistoricIncident historicIncident = c7Client.getHistoricIncident(idKeyDbModel.getC7Id());
        migrateIncident(historicIncident);
      });
    } else {
      c7Client.fetchAndHandleHistoricIncidents(this::migrateIncident,
          dbClient.findLatestCreateTimeByType((HISTORY_INCIDENT)));
    }
  }

  /**
   * Migrates a historic incident from Camunda 7 to Camunda 8.
   *
   * <p>Incidents represent errors or exceptional conditions that occurred during process execution.
   * This method validates that all parent entities (process instance, process definition, and
   * flow node instance) have been migrated before attempting to migrate the incident.
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *   <li>Process instance key missing - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE_KEY}</li>
   *   <li>Process definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *   <li>Flow node instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_SCOPE_KEY}</li>
   *   <li>Job reference missing - skipped with {@code SKIP_REASON_MISSING_JOB_REFERENCE}</li>
   * </ul>
   *
   * @param c7Incident the historic incident from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  public void migrateIncident(HistoricIncident c7Incident) {
    String c7IncidentId = c7Incident.getId();
    if (shouldMigrate(c7IncidentId, HISTORY_INCIDENT)) {
      HistoryMigratorLogs.migratingHistoricIncident(c7IncidentId);
      ProcessInstanceEntity c7ProcessInstance = findProcessInstanceByC7Id(c7Incident.getProcessInstanceId());

      try {
        IncidentDbModel.Builder incidentDbModelBuilder = new IncidentDbModel.Builder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7Incident, HistoricIncident.class,
            incidentDbModelBuilder);

        if (c7ProcessInstance != null) {
          Long processInstanceKey = c7ProcessInstance.processInstanceKey();
          incidentDbModelBuilder.processInstanceKey(processInstanceKey);
          if (processInstanceKey != null) {
            Long flowNodeInstanceKey = findFlowNodeInstanceKey(c7Incident.getActivityId(),
                c7Incident.getProcessInstanceId());
            Long processDefinitionKey = findProcessDefinitionKey(c7Incident.getProcessDefinitionId());
            Long jobDefinitionKey = null; // TODO Job table doesn't exist yet.

            incidentDbModelBuilder.processDefinitionKey(processDefinitionKey)
                .jobKey(jobDefinitionKey)
                .flowNodeInstanceKey(flowNodeInstanceKey);

          }
          IncidentDbModel dbModel = convertIncident(context);
          if (dbModel.processInstanceKey() == null) {
            markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(),
                SKIP_REASON_MISSING_PROCESS_INSTANCE_KEY);
            HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
          } else if (dbModel.processDefinitionKey() == null) {
            markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(),
                SKIP_REASON_MISSING_PROCESS_DEFINITION);
            HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
            // TODO: https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/364
            // check if flowNodeInstanceKey is resolved correctly
            // } else if (dbModel.flowNodeInstanceKey() == null) {
            //   markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(), SKIP_REASON_MISSING_SCOPE_KEY);
            //   HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
          } else if (dbModel.jobKey() == null) {
            markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(), SKIP_REASON_MISSING_JOB_REFERENCE);
            HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
          } else {
            insertIncident(c7Incident, dbModel, c7IncidentId);
          }
        } else {
          IncidentDbModel dbModel = convertIncident(context);
          if (dbModel.processInstanceKey() == null) {
            markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(),
                SKIP_REASON_MISSING_PROCESS_INSTANCE);
            HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
          } else {
            insertIncident(c7Incident, dbModel, c7IncidentId);
          }
        }
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(), e);
      }
    }
  }

  protected void insertIncident(HistoricIncident c7Incident, IncidentDbModel dbModel, String c7IncidentId) {
    c8Client.insertIncident(dbModel);
    markMigrated(c7IncidentId, dbModel.incidentKey(), c7Incident.getCreateTime(), HISTORY_INCIDENT);
    HistoryMigratorLogs.migratingHistoricIncidentCompleted(c7IncidentId);
  }

  protected IncidentDbModel convertIncident(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    IncidentDbModel.Builder builder = (IncidentDbModel.Builder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }
}

