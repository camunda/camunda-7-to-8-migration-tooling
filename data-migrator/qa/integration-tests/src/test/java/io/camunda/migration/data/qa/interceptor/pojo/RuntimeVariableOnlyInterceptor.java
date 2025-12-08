/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.interceptor.pojo;

import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.interceptor.VariableContext;
import java.util.Set;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.impl.variable.serializer.ValueFields;

/**
 * Test interceptor that only processes runtime process variables.
 */
public class RuntimeVariableOnlyInterceptor implements VariableInterceptor {

  @Override
  public Set<Class<? extends ValueFields>> getEntityTypes() {
    // Only handle runtime process variables
    return Set.of(VariableInstanceEntity.class);
  }

  @Override
  public void execute(VariableContext context) {
    context.setC8Value("RUNTIME_" + context.getC7Value());
  }
}

