/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static io.camunda.search.entities.IncidentEntity.IncidentState.RESOLVED;

import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import org.camunda.bpm.engine.history.HistoricIncident;

import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(7)
@Component
public class IncidentTransformer implements EntityInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricIncident.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    HistoricIncident historicIncident = (HistoricIncident) context.getC7Entity();
    IncidentDbModel.Builder builder = (IncidentDbModel.Builder) context.getC8DbModelBuilder();

    if (builder == null) {
      throw new EntityInterceptorException("C8 IncidentDbModel.Builder is null in context");
    }

    builder.incidentKey(getNextKey())
        .processDefinitionId(prefixDefinitionId(historicIncident.getProcessDefinitionKey()))
        .flowNodeId(historicIncident.getActivityId())
        .errorType(null) // TODO: does error type exist in C7?
        .errorMessage(historicIncident.getIncidentMessage())
        .creationDate(convertDate(historicIncident.getCreateTime()))
        .state(RESOLVED) // Mark incident always as resolved
        .treePath(null) //TODO ?
        .tenantId(getTenantId(historicIncident.getTenantId()));
    // Note: processDefinitionKey, processInstanceKey, jobKey, and flowNodeInstanceKey are set externally
  }

}
