/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.converter;

import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;

import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(10)
@Component
public class DecisionDefinitionConverter implements EntityInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(DecisionDefinition.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    DecisionDefinition c7DecisionDefinition = (DecisionDefinition) context.getC7Entity();
    DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder builder =
        (DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder) context.getC8DbModelBuilder();

    if (builder == null) {
      throw new EntityInterceptorException("C8 DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder is null in context");
    }

    builder.decisionDefinitionKey(getNextKey())
        .name(c7DecisionDefinition.getName())
        .decisionDefinitionId(c7DecisionDefinition.getKey())
        .tenantId(getTenantId(c7DecisionDefinition.getTenantId()))
        .version(c7DecisionDefinition.getVersion())
        .decisionRequirementsId(c7DecisionDefinition.getDecisionRequirementsDefinitionKey());
    // Note: decisionRequirementsKey is set externally
  }
}
