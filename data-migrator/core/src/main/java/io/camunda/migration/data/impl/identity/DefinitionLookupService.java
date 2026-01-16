/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.identity;

import static io.camunda.migration.data.impl.identity.AuthorizationEntityRegistry.WILDCARD;

import io.camunda.migration.data.impl.logging.IdentityMigratorLogs;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.persistence.entity.CamundaFormDefinitionEntity;
import org.camunda.bpm.engine.repository.CaseDefinition;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinition;
import org.camunda.bpm.engine.repository.ResourceDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DefinitionLookupService {

  @Autowired
  protected RepositoryService repositoryService;

  @Autowired
  protected ProcessEngineConfigurationImpl processEngineConfiguration;

  /**
   * Returns all IDs of process, decision, DRD, case and form
   * definitions that belong to the given deployment.
   */
  public Set<String> getAllDefinitionKeysForDeployment(String deploymentId) {
    if (WILDCARD.equals(deploymentId)) {
      return Set.of(WILDCARD);
    }

    Set<String> ids = new HashSet<>();

    // BPMN process definitions
    ids.addAll(getProcessDefinitionKeys(deploymentId));

    // DMN decision definitions
    ids.addAll(getDecisionDefinitionKeys(deploymentId));

    // DMN decision requirements
    ids.addAll(getDecisionReqDefinitionKeys(deploymentId));

    // Camunda Forms
    ids.addAll(getCamundaFormDefinitionIdsForDeployment(deploymentId));

    // CMMN (not supported in Camunda 8, so just log if any are found)
    List<String> caseDefinitions = getCaseDefinitionKeys(deploymentId);
    if (!caseDefinitions.isEmpty()) {
      IdentityMigratorLogs.foundCmmnInDeployment(caseDefinitions.size(), deploymentId);
    }

    return ids;
  }

  protected List<String> getCaseDefinitionKeys(String deploymentId) {
    return repositoryService
        .createCaseDefinitionQuery()
        .deploymentId(deploymentId)
        .list()
        .stream().map(CaseDefinition::getKey)
        .toList();
  }

  protected List<String> getDecisionReqDefinitionKeys(String deploymentId) {
    return repositoryService
        .createDecisionRequirementsDefinitionQuery()
        .deploymentId(deploymentId)
        .list()
        .stream().map(DecisionRequirementsDefinition::getKey)
        .toList();
  }

  protected List<String> getDecisionDefinitionKeys(String deploymentId) {
    return repositoryService
        .createDecisionDefinitionQuery()
        .deploymentId(deploymentId)
        .list()
        .stream().map(DecisionDefinition::getKey)
        .toList();
  }

  protected List<String> getProcessDefinitionKeys(String deploymentId) {
    return repositoryService
        .createProcessDefinitionQuery()
        .deploymentId(deploymentId)
        .list()
        .stream().map(ResourceDefinition::getKey)
        .toList();
  }

  protected List<String> getCamundaFormDefinitionIdsForDeployment(String deploymentId) {
    return processEngineConfiguration
      .getCommandExecutorTxRequired()
      .execute(commandContext ->
          commandContext.getCamundaFormDefinitionManager()
          .findDefinitionsByDeploymentId(deploymentId)
          .stream().map(CamundaFormDefinitionEntity::getKey)
          .toList()
      );
  }
}