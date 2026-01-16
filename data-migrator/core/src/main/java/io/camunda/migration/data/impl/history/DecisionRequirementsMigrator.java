/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel.*;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT;

import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import java.util.Date;
import org.camunda.bpm.engine.impl.dmn.entity.repository.DecisionRequirementsDefinitionEntity;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinition;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating decision requirements definitions from Camunda 7 to Camunda 8.
 */
@Service
public class DecisionRequirementsMigrator extends BaseMigrator<DecisionRequirementsDefinition, DecisionRequirementsDbModel> {

  public void migrateDecisionRequirementsDefinitions() {
    HistoryMigratorLogs.migratingDecisionRequirements();
    executeMigration(
        HISTORY_DECISION_REQUIREMENT,
        c7Client::getDecisionRequirementsDefinition,
        c7Client::fetchAndHandleDecisionRequirementsDefinitions,
        this::migrateDecisionRequirementsDefinition
    );
  }

  /**
   * Migrates a decision requirements definition from Camunda 7 to Camunda 8.
   *
   * <p>Decision requirements definitions (DRD) define the structure of DMN decision models
   * and their dependencies. This method converts the C7 decision requirements definition
   * to C8 format and inserts it into the C8 database.
   *
   * @param c7DecisionRequirements the decision requirements definition from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  public void migrateDecisionRequirementsDefinition(DecisionRequirementsDefinition c7DecisionRequirements) {
    var c7Id = c7DecisionRequirements.getId();
    if (shouldMigrate(c7Id, HISTORY_DECISION_REQUIREMENT)) {
      HistoryMigratorLogs.migratingDecisionRequirements(c7Id);

      var deploymentTime = c7Client.getDefinitionDeploymentTime(c7DecisionRequirements.getDeploymentId());

      DecisionRequirementsDbModel dbModel;
      try {
        dbModel = convert(c7DecisionRequirements, new Builder());
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7Id, HISTORY_DECISION_REQUIREMENT, deploymentTime, e);
        return;
      }

      c8Client.insertDecisionRequirements(dbModel);
      markMigrated(c7Id, dbModel.decisionRequirementsKey(), deploymentTime, HISTORY_DECISION_REQUIREMENT);
      HistoryMigratorLogs.migratingDecisionRequirementsCompleted(c7Id);
    }
  }

  protected Long migrateSyntheticDrd(DecisionDefinition c7DecisionDefinition, Date deploymentTime) {
    String c7DecisionDefinitionId = c7DecisionDefinition.getId();

    Long decisionRequirementsKey = dbClient.findC8KeyByC7IdAndType(c7DecisionDefinitionId, HISTORY_DECISION_REQUIREMENT);
    if (decisionRequirementsKey != null) {
      return decisionRequirementsKey;
    }

    HistoryMigratorLogs.creatingDecisionRequirement(c7DecisionDefinitionId);

    var newDrd = newDrd(c7DecisionDefinition);
    var drdModel = convert(newDrd, new Builder());

    c8Client.insertDecisionRequirements(drdModel);

    decisionRequirementsKey = drdModel.decisionRequirementsKey();
    markMigrated(c7DecisionDefinition.getId(), decisionRequirementsKey, deploymentTime, HISTORY_DECISION_REQUIREMENT);
    HistoryMigratorLogs.creatingDecisionRequirementCompleted(c7DecisionDefinitionId);
    return decisionRequirementsKey;
  }

  protected DecisionRequirementsDefinition newDrd(DecisionDefinition c7DecisionDefinition) {
    var newDrd = new DecisionRequirementsDefinitionEntity();
    var decisionRequirementsId = c7Client.getDmnModelInstance(c7DecisionDefinition.getId()).getDefinitions().getId();
    newDrd.setKey(decisionRequirementsId);
    newDrd.setName(c7DecisionDefinition.getName());
    newDrd.setResourceName(c7DecisionDefinition.getResourceName());
    newDrd.setDeploymentId(c7DecisionDefinition.getDeploymentId());
    newDrd.setVersion(c7DecisionDefinition.getVersion());
    newDrd.setTenantId(c7DecisionDefinition.getTenantId());
    return newDrd;
  }
}

