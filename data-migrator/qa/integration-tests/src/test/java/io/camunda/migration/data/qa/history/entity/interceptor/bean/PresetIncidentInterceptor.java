/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.bean;

import static io.camunda.db.rdbms.write.domain.IncidentDbModel.*;

import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricIncident;

public class PresetIncidentInterceptor implements EntityInterceptor<HistoricIncident, Builder> {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricIncident.class);
  }

  @Override
  public void presetParentProperties(EntityConversionContext<HistoricIncident, Builder> context) {
    context.getC8DbModelBuilder()
        .processDefinitionKey(1L)
        .processInstanceKey(2L)
        .jobKey(3L)
        .flowNodeInstanceKey(4L)
        .rootProcessInstanceKey(2L);
  }

  @Override
  public void execute(EntityConversionContext<HistoricIncident, Builder> context) {
    // This interceptor intentionally does not modify the incident during execution.
  }
}

