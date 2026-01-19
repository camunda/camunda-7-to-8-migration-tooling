/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.bean;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricProcessInstance;


public class PresetProcessInstanceInterceptor implements EntityInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricProcessInstance.class);
  }

  @Override
  public void presetParentProperties(EntityConversionContext<?, ?> context) {
    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder = (ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) context.getC8DbModelBuilder();

    if (builder != null) {
      builder.processDefinitionKey(12345L)
          .parentProcessInstanceKey(67890L)
          .rootProcessInstanceKey(55555L);
    }
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    // This execute method runs after presetParentProperties
    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder = (ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) context.getC8DbModelBuilder();

    if (builder != null) {
      HistoricProcessInstance entityType = (HistoricProcessInstance) context.getC7Entity();
      builder.processInstanceKey(88888L)
          .processDefinitionId(entityType.getProcessDefinitionKey());
    }

  }
}