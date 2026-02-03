/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel.*;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

import io.camunda.migration.data.interceptor.EntityInterceptor;
import java.util.Set;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(10)
@Component
public class DecisionDefinitionTransformer implements EntityInterceptor<DecisionDefinition, DecisionDefinitionDbModelBuilder> {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(DecisionDefinition.class);
  }

  @Override
  public void execute(DecisionDefinition entity, DecisionDefinitionDbModelBuilder builder) {
    builder.decisionDefinitionKey(getNextKey())
        .name(entity.getName())
        .decisionDefinitionId(prefixDefinitionId(entity.getKey()))
        .decisionRequirementsId(prefixDefinitionId(entity.getDecisionRequirementsDefinitionKey()))
        .tenantId(getTenantId(entity.getTenantId()))
        .version(entity.getVersion());
    // Note: decisionRequirementsKey is set externally
  }
}
