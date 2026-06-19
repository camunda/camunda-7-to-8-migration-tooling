/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.pojo;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricActivityInstance;

/**
 * Test interceptor that clears a single, configurable required field on the flow node builder after
 * the built-in {@code FlowNodeTransformer} has populated it. Used to verify that the migrator skips
 * flow nodes whose required fields are nulled by a custom interceptor.
 */
public class RequiredFieldNullingInterceptor
    implements EntityInterceptor<HistoricActivityInstance, FlowNodeInstanceDbModelBuilder> {

  protected String fieldToNull;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricActivityInstance.class);
  }

  @Override
  public void execute(HistoricActivityInstance c7Entity, FlowNodeInstanceDbModelBuilder builder) {
    if (fieldToNull == null) {
      return;
    }
    switch (fieldToNull) {
      case "flowNodeInstanceKey" -> builder.flowNodeInstanceKey(null);
      case "flowNodeId" -> builder.flowNodeId(null);
      case "type" -> builder.type(null);
      case "state" -> builder.state(null);
      case "processDefinitionId" -> builder.processDefinitionId(null);
      case "tenantId" -> builder.tenantId(null);
      default -> throw new IllegalArgumentException("Unknown field: " + fieldToNull);
    }
  }

  public void setFieldToNull(String fieldToNull) {
    this.fieldToNull = fieldToNull;
  }
}
