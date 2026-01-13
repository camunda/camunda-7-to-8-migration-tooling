/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.bean;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricActivityInstance;

public class PresetFlowNodeInterceptor implements EntityInterceptor {

  protected boolean configureScopeKey = true;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricActivityInstance.class);
  }

  @Override
  public void presetParentProperties(EntityConversionContext<?, ?> context) {
    FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder builder =
        (FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder) context.getC8DbModelBuilder();

    if (builder != null) {
      builder.treePath("1/2/3/")
          .processInstanceKey(1L)
          .processDefinitionKey(2L)
          .rootProcessInstanceKey(1L);
      if (configureScopeKey) {
        builder.flowNodeScopeKey(1L);
      }
    }
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    // This interceptor intentionally does not modify the flow node during execution.
  }

  public boolean isConfigureScopeKey() {
    return configureScopeKey;
  }

  public void setConfigureScopeKey(boolean configureScopeKey) {
    this.configureScopeKey = configureScopeKey;
  }
}
