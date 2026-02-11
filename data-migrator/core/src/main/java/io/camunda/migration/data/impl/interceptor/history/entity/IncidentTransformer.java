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

import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.search.entities.IncidentEntity;
import org.camunda.bpm.engine.history.HistoricIncident;

import java.util.Set;
import org.camunda.bpm.engine.history.IncidentState;
import org.camunda.bpm.engine.impl.history.event.HistoricIncidentEventEntity;
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
        .errorType(null) // TODO: does error type exist in C7?
        .errorMessage(entity.getIncidentMessage())
        .creationDate(convertDate(entity.getCreateTime()))
        .treePath(null)
        .errorMessageHash(null)
        .partitionId(C7_HISTORY_PARTITION_ID)
        .jobKey(null)
        .state(getIncidentState((HistoricIncidentEventEntity) entity))
        .tenantId(getTenantId(entity.getTenantId()));
        // Note: processDefinitionKey, processInstanceKey, jobKey, and flowNodeInstanceKey are set externally
  }

  private static IncidentEntity.IncidentState getIncidentState(HistoricIncidentEventEntity entity) {
    int state = entity.getIncidentState();
    if (state == IncidentState.DEFAULT.getStateCode()) {
      return IncidentEntity.IncidentState.ACTIVE;
    } else if (state == IncidentState.RESOLVED.getStateCode() || state == IncidentState.DELETED.getStateCode()) {
      return IncidentEntity.IncidentState.RESOLVED;
    } else {
      throw new EntityInterceptorException("Could not determine incident state for state code: " + state);
    }
  }

}
