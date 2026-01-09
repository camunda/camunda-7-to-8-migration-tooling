/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.bean;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricTaskInstance;

public class PresetUserTaskInterceptor implements EntityInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricTaskInstance.class);
  }

  @Override
  public void presetParentProperties(EntityConversionContext<?, ?> context) {
    UserTaskDbModel.Builder builder = (UserTaskDbModel.Builder) context.getC8DbModelBuilder();

    if (builder != null) {
      builder.processDefinitionKey(1L)
          .processInstanceKey(2L)
          .elementInstanceKey(3L)
          .processDefinitionVersion(1);
    }
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    // This interceptor intentionally does not modify the user task during execution.
  }
}

