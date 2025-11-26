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

import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import org.camunda.bpm.engine.repository.DecisionDefinition;

public class DecisionDefinitionConverter {

  public DecisionDefinitionDbModel apply(DecisionDefinition c7DecisionDefinition, Long decisionRequirementsKey) {

    return new DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder().decisionDefinitionKey(getNextKey())
        .name(c7DecisionDefinition.getName())
        .decisionDefinitionId(c7DecisionDefinition.getKey())
        .tenantId(getTenantId(c7DecisionDefinition.getTenantId()))
        .version(c7DecisionDefinition.getVersion())
        .decisionRequirementsId(c7DecisionDefinition.getDecisionRequirementsDefinitionKey())
        .decisionRequirementsKey(decisionRequirementsKey)
        .build();
  }
}
