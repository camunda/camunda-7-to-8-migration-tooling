/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel.*;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT;

import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import java.util.Date;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.camunda.bpm.engine.impl.dmn.entity.repository.DecisionRequirementsDefinitionEntity;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinition;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating decision requirements definitions from Camunda 7 to Camunda 8.
 */
@Service
public class DecisionRequirementsMigrator extends HistoryEntityMigrator<DecisionRequirementsDefinition, DecisionRequirementsDbModel> {

  @Override
  public BiConsumer<Consumer<DecisionRequirementsDefinition>, Date> fetchForMigrateHandler() {
    return c7Client::fetchAndHandleDecisionRequirementsDefinitions;
  }

  @Override
  public Function<String, DecisionRequirementsDefinition> fetchForRetryHandler() {
    return c7Client::getDecisionRequirementsDefinition;
  }

  @Override
  public IdKeyMapper.TYPE getType() {
    return HISTORY_DECISION_REQUIREMENT;
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
  public Long migrateTransactionally(DecisionRequirementsDefinition c7DecisionRequirements) {
    var c7Id = c7DecisionRequirements.getId();
    if (shouldMigrate(c7Id, HISTORY_DECISION_REQUIREMENT)) {
      HistoryMigratorLogs.migratingDecisionRequirements(c7Id);

      var creationTime = c7Client.getDefinitionDeploymentTime(c7DecisionRequirements.getDeploymentId());
      DecisionRequirementsDbModel dbModel = convert(C7Entity.of(c7DecisionRequirements, creationTime), new Builder());
      c8Client.insertDecisionRequirements(dbModel);
      return dbModel.decisionRequirementsKey();
    }

    return null;
  }

  protected Long migrateSyntheticDrd(DecisionDefinition c7DecisionDefinition) {
    var newDrd = newDrd(c7DecisionDefinition);
    return migrateTransactionally(newDrd);
  }

  protected DecisionRequirementsDefinition newDrd(DecisionDefinition c7DecisionDefinition) {
    var newDrd = new DecisionRequirementsDefinitionEntity();

    String definitionId = c7DecisionDefinition.getId();
    var decisionRequirementsId = c7Client.getDmnModelInstance(definitionId).getDefinitions().getId();
    newDrd.setKey(decisionRequirementsId);

    newDrd.setName(c7DecisionDefinition.getName());
    newDrd.setResourceName(c7DecisionDefinition.getResourceName());
    newDrd.setDeploymentId(c7DecisionDefinition.getDeploymentId());
    newDrd.setVersion(c7DecisionDefinition.getVersion());
    newDrd.setTenantId(c7DecisionDefinition.getTenantId());
    return newDrd;
  }
}

