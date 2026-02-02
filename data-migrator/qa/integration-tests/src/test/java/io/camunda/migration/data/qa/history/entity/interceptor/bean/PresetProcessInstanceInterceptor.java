/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.bean;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricProcessInstance;


public class PresetProcessInstanceInterceptor implements EntityInterceptor<HistoricProcessInstance, ProcessInstanceDbModelBuilder> {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricProcessInstance.class);
  }

  @Override
  public void presetParentProperties(EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> context) {
    context.getC8DbModelBuilder()
        .processDefinitionKey(12345L)
        .parentProcessInstanceKey(67890L)
        .rootProcessInstanceKey(55555L);
  }

  @Override
  public void execute(EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> context) {
    context.getC8DbModelBuilder()
        .processInstanceKey(88888L)
        .processDefinitionId(context.getC7Entity().getProcessDefinitionKey());
  }
}