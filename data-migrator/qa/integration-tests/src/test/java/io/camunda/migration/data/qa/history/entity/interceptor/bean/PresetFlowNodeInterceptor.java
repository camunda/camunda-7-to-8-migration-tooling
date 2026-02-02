/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.bean;

import static io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.*;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricActivityInstance;

public class PresetFlowNodeInterceptor implements EntityInterceptor<HistoricActivityInstance, FlowNodeInstanceDbModelBuilder> {

  protected boolean skipSettingScopeKey = false;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricActivityInstance.class);
  }

  @Override
  public void presetParentProperties(HistoricActivityInstance c7Entity, FlowNodeInstanceDbModelBuilder builder) {
    builder.treePath("1/2/3/")
        .processInstanceKey(1L)
        .processDefinitionKey(2L)
        .rootProcessInstanceKey(1L);
    if (!skipSettingScopeKey) {
      builder.flowNodeScopeKey(1L);
    }
  }

  @Override
  public void execute(EntityConversionContext<HistoricActivityInstance, FlowNodeInstanceDbModelBuilder> context) {
    // This interceptor intentionally does not modify the flow node during execution.
  }

  public boolean isSkipSettingScopeKey() {
    return skipSettingScopeKey;
  }

  public void setSkipSettingScopeKey(boolean skipSettingScopeKey) {
    this.skipSettingScopeKey = skipSettingScopeKey;
  }
}
