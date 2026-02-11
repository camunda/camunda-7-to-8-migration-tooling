/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_DECISION_REQUIREMENTS;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT;

import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating decision definitions from Camunda 7 to Camunda 8.
 */
@Service
public class DecisionDefinitionMigrator extends BaseMigrator<DecisionDefinition, DecisionDefinitionDbModel> {

  @Autowired
  protected DecisionRequirementsMigrator decisionRequirementsMigrator;

  @Override
  public void migrateAll() {
    fetchAndRetry(
        HISTORY_DECISION_DEFINITION,
        c7Client::getDecisionDefinition,
        c7Client::fetchAndHandleDecisionDefinitions
    );
  }

  /**
   * Migrates a decision definition from Camunda 7 to Camunda 8.
   *
   * <p>Decision definitions describe individual DMN decisions within a decision requirements definition.
   * This method validates that the parent decision requirements definition has been migrated before
   * attempting to migrate the decision definition.
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Decision requirements definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_DECISION_REQUIREMENTS}</li>
   * </ul>
   *
   * @param c7DecisionDefinition the decision definition from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  @Override
  public Long migrateTransactionally(DecisionDefinition c7DecisionDefinition) {
    var c7Id = c7DecisionDefinition.getId();
    if (shouldMigrate(c7Id, HISTORY_DECISION_DEFINITION)) {
      HistoryMigratorLogs.migratingDecisionDefinition(c7Id);

      var creationTime = c7Client.getDefinitionDeploymentTime(c7DecisionDefinition.getDeploymentId());

      var builder = new DecisionDefinitionDbModelBuilder();

      String drdId = c7DecisionDefinition.getDecisionRequirementsDefinitionId();
      if (drdId != null) {
        Long decisionRequirementsKey = dbClient.findC8KeyByC7IdAndType(drdId, HISTORY_DECISION_REQUIREMENT);
        if (decisionRequirementsKey != null) {
          builder.decisionRequirementsKey(decisionRequirementsKey);
        }
      } else {
        // For single c7 decisions (no DRD), generate a C8 DecisionRequirementsDefinition to store the DMN XML
        Long decisionRequirementsKey = decisionRequirementsMigrator.migrateSyntheticDrd(c7DecisionDefinition);
        builder.decisionRequirementsKey(decisionRequirementsKey);
      }

      DecisionDefinitionDbModel dbModel = convert(C7Entity.of(c7DecisionDefinition, creationTime), builder);

      if (dbModel.decisionRequirementsKey() == null) {
        throw new EntitySkippedException(c7DecisionDefinition, creationTime, SKIP_REASON_MISSING_DECISION_REQUIREMENTS);
      }

      c8Client.insertDecisionDefinition(dbModel);

      return dbModel.decisionDefinitionKey();
    }

    return null;
  }

}

