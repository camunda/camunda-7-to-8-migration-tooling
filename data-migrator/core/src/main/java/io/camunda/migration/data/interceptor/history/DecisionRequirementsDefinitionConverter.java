/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.interceptor.history;

import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;

import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.migration.data.impl.clients.C7Client;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinition;
import org.springframework.beans.factory.annotation.Autowired;

public class DecisionRequirementsDefinitionConverter {

  @Autowired
  protected C7Client c7Client;

  public DecisionRequirementsDbModel apply(DecisionRequirementsDefinition c7DecisionRequirements) {
    String deploymentId = c7DecisionRequirements.getDeploymentId();
    String resourceName = c7DecisionRequirements.getResourceName();

    String dmnXml = c7Client.getResourceAsString(deploymentId, resourceName);

    return new DecisionRequirementsDbModel.Builder()
        .decisionRequirementsKey(getNextKey())
        .decisionRequirementsId(c7DecisionRequirements.getKey())
        .name(c7DecisionRequirements.getName())
        .resourceName(resourceName)
        .version(c7DecisionRequirements.getVersion())
        .xml(dmnXml)
        .tenantId(getTenantId(c7DecisionRequirements.getTenantId()))
        .build();
  }
}
