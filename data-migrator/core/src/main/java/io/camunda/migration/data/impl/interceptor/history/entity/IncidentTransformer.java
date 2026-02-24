/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.db.rdbms.write.domain.IncidentDbModel.Builder;
import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static io.camunda.search.entities.IncidentEntity.ErrorType.CONDITION_ERROR;
import static io.camunda.search.entities.IncidentEntity.ErrorType.DECISION_EVALUATION_ERROR;
import static io.camunda.search.entities.IncidentEntity.ErrorType.FORM_NOT_FOUND;
import static io.camunda.search.entities.IncidentEntity.ErrorType.JOB_NO_RETRIES;
import static io.camunda.search.entities.IncidentEntity.ErrorType.RESOURCE_NOT_FOUND;
import static io.camunda.search.entities.IncidentEntity.ErrorType.UNHANDLED_ERROR_EVENT;
import static io.camunda.search.entities.IncidentEntity.ErrorType.UNKNOWN;

import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.search.entities.IncidentEntity;
import org.camunda.bpm.engine.history.HistoricIncident;

import java.util.Set;
import org.camunda.bpm.engine.runtime.Incident;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(7)
@Component
public class IncidentTransformer implements EntityInterceptor<HistoricIncident, Builder> {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricIncident.class);
  }

  @Override
  public void execute(HistoricIncident entity, Builder builder) {
    builder.incidentKey(getNextKey())
        .processDefinitionId(prefixDefinitionId(entity.getProcessDefinitionKey()))
        .flowNodeId(entity.getActivityId())
        .errorType(determineErrorType(entity))
        .errorMessage(entity.getIncidentMessage())
        .creationDate(convertDate(entity.getCreateTime()))
        .treePath(null)
        .errorMessageHash(null)
        .partitionId(C7_HISTORY_PARTITION_ID)
        .jobKey(null)
        .state(IncidentEntity.IncidentState.RESOLVED)
        .tenantId(getTenantId(entity.getTenantId()));
        // Note: processDefinitionKey, processInstanceKey, jobKey, and flowNodeInstanceKey are set externally
  }

  protected IncidentEntity.ErrorType determineErrorType(HistoricIncident c7Incident) {
    String type = c7Incident.getIncidentType();
    String msg  = c7Incident.getIncidentMessage();

    if (msg == null) {
      // fall back to job‑retries semantics
      if (Incident.FAILED_JOB_HANDLER_TYPE.equals(type) || Incident.EXTERNAL_TASK_HANDLER_TYPE.equals(type)) {
        return JOB_NO_RETRIES;
      }
      return UNKNOWN;
    }

    // DMN result / mapping issues
    if (msg.contains("ENGINE-22001") || (msg.contains("The decision result mapper") && msg.contains("failed to process"))) {
      return DECISION_EVALUATION_ERROR;
    }

    // Resource not found
    if (msg.contains("ENGINE-09017") || msg.contains("Cannot load class") ||
        msg.contains("ENGINE-09024") || msg.contains("Unable to find resource at path")) {
      return RESOURCE_NOT_FOUND;
    }

    // Conditional start / condition evaluation
    if (msg.contains("ENGINE-13043") || msg.contains("No subscriptions were found during evaluation") ||
        msg.contains("ENGINE-13042") || msg.contains("does not declare conditional start event")) {
      return CONDITION_ERROR;
    }

    // Unhandled error event
    if (msg.contains("ENGINE-02042") || msg.contains("but no error handler was defined")) {
      return UNHANDLED_ERROR_EVENT;
    }

    // Form not found
    if (msg.contains("The form with the resource name") && msg.contains("cannot be found in deployment")) {
      return FORM_NOT_FOUND;
    }

    // Fallback for anything else
    return UNKNOWN;
  }
}
