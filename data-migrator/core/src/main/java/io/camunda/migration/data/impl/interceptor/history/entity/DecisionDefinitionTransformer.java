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

import io.camunda.migration.data.impl.util.LegacyIdPrefixResolver;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import java.util.Set;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(10)
@Component
public class DecisionDefinitionTransformer implements EntityInterceptor<DecisionDefinition, DecisionDefinitionDbModelBuilder> {

  @Autowired
  protected LegacyIdPrefixResolver legacyIdPrefix;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(DecisionDefinition.class);
  }

  @Override
  public void execute(DecisionDefinition entity, DecisionDefinitionDbModelBuilder builder) {
    builder.decisionDefinitionKey(getNextKey())
        .name(entity.getName())
        .decisionDefinitionId(legacyIdPrefix.applyTo(entity.getKey()))
        .tenantId(getTenantId(entity.getTenantId()))
        .version(entity.getVersion());
    // Only set decisionRequirementsId from the C7 source when the parent DRD exists. For
    // standalone DMNs the migrator wires the synthetic DRD id onto the builder beforehand;
    // skipping the set here preserves that value and keeps the field non-null on the C8 row.
    String c7DrdKey = entity.getDecisionRequirementsDefinitionKey();
    if (c7DrdKey != null) {
      builder.decisionRequirementsId(legacyIdPrefix.applyTo(c7DrdKey));
    }
    // Note: decisionRequirementsKey is set externally
  }
}
