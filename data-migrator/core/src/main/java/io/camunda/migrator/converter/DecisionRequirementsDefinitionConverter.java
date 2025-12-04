/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.converter;

import static io.camunda.migrator.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migrator.impl.util.ConverterUtil.getTenantId;

import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.migrator.exception.EntityInterceptorException;
import io.camunda.migrator.interceptor.EntityInterceptor;
import io.camunda.migrator.interceptor.property.EntityConversionContext;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinition;

import java.util.Set;

public class DecisionRequirementsDefinitionConverter implements EntityInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(DecisionRequirementsDefinition.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    DecisionRequirementsDefinition c7DecisionRequirements = (DecisionRequirementsDefinition) context.getC7Entity();
    DecisionRequirementsDbModel.Builder builder =
        (DecisionRequirementsDbModel.Builder) context.getC8DbModelBuilder();

    if (builder == null) {
      throw new EntityInterceptorException("C8 DecisionRequirementsDbModel.Builder is null in context");
    }

    builder.decisionRequirementsKey(getNextKey())
        .decisionRequirementsId(c7DecisionRequirements.getKey())
        .name(c7DecisionRequirements.getName())
        .resourceName(c7DecisionRequirements.getResourceName())
        .version(c7DecisionRequirements.getVersion())
        .xml(null) // TODO not stored in C7 DecisionRequirementsDefinition
        .tenantId(getTenantId(c7DecisionRequirements.getTenantId()));
  }
}
