/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config;

import io.camunda.migration.data.config.property.MigratorProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class C8DataSourceConfigured implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    MigratorProperties config = Binder.get(context.getEnvironment())
        .bind(MigratorProperties.PREFIX, MigratorProperties.class)
        .orElse(null);
    return config != null && config.getC8() != null && config.getC8().getDataSource() != null;
  }

}