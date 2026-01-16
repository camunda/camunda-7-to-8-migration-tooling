/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT;

import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Date;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating decision requirements definitions from Camunda 7 to Camunda 8.
 */
@Service
public class DecisionRequirementsMigrator extends BaseMigrator<DecisionRequirementsDefinition> {

  @Override
  public void migrate() {
    HistoryMigratorLogs.migratingDecisionRequirements();

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_DECISION_REQUIREMENT, idKeyDbModel -> {
        DecisionRequirementsDefinition c7DecisionRequirement = c7Client.getDecisionRequirementsDefinition(idKeyDbModel.getC7Id());
        self.migrateOne(c7DecisionRequirement);
      });
    } else {
      c7Client.fetchAndHandleDecisionRequirementsDefinitions(self::migrateOne);
    }
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
  @Override
  public void migrateOne(DecisionRequirementsDefinition c7DecisionRequirements) {
    String c7Id = c7DecisionRequirements.getId();
    if (shouldMigrate(c7Id, HISTORY_DECISION_REQUIREMENT)) {
      HistoryMigratorLogs.migratingDecisionRequirements(c7Id);

      DecisionRequirementsDbModel dbModel;
      Date deploymentTime = c7Client.getDefinitionDeploymentTime(c7DecisionRequirements.getDeploymentId());
      try {
        DecisionRequirementsDbModel.Builder decisionRequirementsDbModelBuilder = new DecisionRequirementsDbModel.Builder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7DecisionRequirements,
            DecisionRequirementsDefinition.class, decisionRequirementsDbModelBuilder);

        dbModel = convertDecisionRequirements(context);
        c8Client.insertDecisionRequirements(dbModel);
        markMigrated(c7Id, dbModel.decisionRequirementsKey(), deploymentTime, HISTORY_DECISION_REQUIREMENT);
        HistoryMigratorLogs.migratingDecisionRequirementsCompleted(c7Id);
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7Id, HISTORY_DECISION_REQUIREMENT, deploymentTime, e);
      }
    }
  }

}

