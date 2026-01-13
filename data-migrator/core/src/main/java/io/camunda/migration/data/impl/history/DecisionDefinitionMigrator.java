/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_DECISION_REQUIREMENTS;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Date;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinition;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating decision definitions from Camunda 7 to Camunda 8.
 */
@Service
public class DecisionDefinitionMigrator extends BaseMigrator {

  public void migrateDecisionDefinitions() {
    HistoryMigratorLogs.migratingDecisionDefinitions();

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_DECISION_DEFINITION, idKeyDbModel -> {
        DecisionDefinition c7DecisionDefinition = c7Client.getDecisionDefinition(idKeyDbModel.getC7Id());
        migrateDecisionDefinition(c7DecisionDefinition);
      });
    } else {
      c7Client.fetchAndHandleDecisionDefinitions(this::migrateDecisionDefinition,
          dbClient.findLatestCreateTimeByType(HISTORY_DECISION_DEFINITION));
    }
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
  public void migrateDecisionDefinition(DecisionDefinition c7DecisionDefinition) {
    String c7Id = c7DecisionDefinition.getId();
    if (shouldMigrate(c7Id, HISTORY_DECISION_DEFINITION)) {
      HistoryMigratorLogs.migratingDecisionDefinition(c7Id);
      Long decisionRequirementsKey;

      Date deploymentTime = c7Client.getDefinitionDeploymentTime(c7DecisionDefinition.getDeploymentId());

      DecisionDefinitionDbModel dbModel;
      try {
        DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder decisionDefinitionDbModelBuilder = new DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7DecisionDefinition,
            DecisionDefinition.class, decisionDefinitionDbModelBuilder);

        if (c7DecisionDefinition.getDecisionRequirementsDefinitionId() != null) {
          decisionRequirementsKey = dbClient.findC8KeyByC7IdAndType(
              c7DecisionDefinition.getDecisionRequirementsDefinitionId(), HISTORY_DECISION_REQUIREMENT);

          if (decisionRequirementsKey != null) {
            decisionDefinitionDbModelBuilder.decisionRequirementsKey(decisionRequirementsKey);
            
            // Populate decisionRequirementsName and decisionRequirementsVersion from the DRD
            DecisionRequirementsDefinition c7Drd = c7Client.getDecisionRequirementsDefinition(
                c7DecisionDefinition.getDecisionRequirementsDefinitionId());
            decisionDefinitionDbModelBuilder
                .decisionRequirementsName(c7Drd.getName())
                .decisionRequirementsVersion(c7Drd.getVersion());
          }
        } else {
          // For single c7 decisions (no DRD), generate a C8 DecisionRequirementsDefinition to store the DMN XML
          decisionRequirementsKey = createAndMigrateNewDrdForC7DmnWithoutDrd(c7DecisionDefinition, deploymentTime);
          decisionDefinitionDbModelBuilder.decisionRequirementsKey(decisionRequirementsKey);
          // For standalone decisions, use the decision's own name and version as the DRD values
          decisionDefinitionDbModelBuilder
              .decisionRequirementsName(c7DecisionDefinition.getName())
              .decisionRequirementsVersion(c7DecisionDefinition.getVersion());
        }
        dbModel = convertDecisionDefinition(context);
        if (dbModel.decisionRequirementsKey() != null) {
          insertDecisionDefinition(dbModel, c7Id, deploymentTime);
        } else {
          markSkipped(c7Id, HISTORY_DECISION_DEFINITION, deploymentTime, SKIP_REASON_MISSING_DECISION_REQUIREMENTS);
          HistoryMigratorLogs.skippingDecisionDefinition(c7Id);
        }
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7Id, HISTORY_DECISION_DEFINITION, deploymentTime, e);
      }
    }
  }

  protected DecisionDefinitionDbModel convertDecisionDefinition(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder builder = (DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  protected void insertDecisionDefinition(DecisionDefinitionDbModel dbModel, String c7Id, Date deploymentTime) {
    c8Client.insertDecisionDefinition(dbModel);
    markMigrated(c7Id, dbModel.decisionDefinitionKey(), deploymentTime, HISTORY_DECISION_DEFINITION);
    HistoryMigratorLogs.migratingDecisionDefinitionCompleted(c7Id);
  }

  protected Long createAndMigrateNewDrdForC7DmnWithoutDrd(DecisionDefinition c7DecisionDefinition,
                                                          Date deploymentTime) {
    Long decisionRequirementsKey = dbClient.findC8KeyByC7IdAndType(c7DecisionDefinition.getId(),
        HISTORY_DECISION_REQUIREMENT);

    if (decisionRequirementsKey == null) {
      String c7DecisionDefinitionId = c7DecisionDefinition.getId();
      HistoryMigratorLogs.creatingDecisionRequirement(c7DecisionDefinitionId);
      String deploymentId = c7DecisionDefinition.getDeploymentId();
      String resourceName = c7DecisionDefinition.getResourceName();
      String dmnXml = c7Client.getResourceAsString(deploymentId, resourceName);

      DecisionRequirementsDbModel.Builder decisionRequirementsDbModelBuilder = generateBuilderForDrdForC7DefinitionWithoutDrd(
          c7DecisionDefinition, resourceName, dmnXml);
      EntityConversionContext<?, ?> context = createEntityConversionContext(null, DecisionRequirementsDefinition.class,
          decisionRequirementsDbModelBuilder);

      DecisionRequirementsDbModel drdModel = convertDecisionRequirements(context);

      decisionRequirementsKey = drdModel.decisionRequirementsKey();
      c8Client.insertDecisionRequirements(drdModel);
      markMigrated(c7DecisionDefinition.getId(), decisionRequirementsKey, deploymentTime, HISTORY_DECISION_REQUIREMENT);
      HistoryMigratorLogs.creatingDecisionRequirementCompleted(c7DecisionDefinitionId);
    }
    return decisionRequirementsKey;
  }

  protected DecisionRequirementsDbModel.Builder generateBuilderForDrdForC7DefinitionWithoutDrd(DecisionDefinition c7DecisionDefinition,
                                                                                               String resourceName,
                                                                                               String xml) {
    String decisionRequirementsId = c7Client.getDmnModelInstance(c7DecisionDefinition.getId()).getDefinitions().getId();
    return new DecisionRequirementsDbModel.Builder().decisionRequirementsKey(getNextKey())
        .decisionRequirementsId(prefixDefinitionId(decisionRequirementsId))
        .name(c7DecisionDefinition.getName())
        .resourceName(resourceName)
        .version(c7DecisionDefinition.getVersion())
        .xml(xml)
        .tenantId(getTenantId(c7DecisionDefinition.getTenantId()));
  }

}

