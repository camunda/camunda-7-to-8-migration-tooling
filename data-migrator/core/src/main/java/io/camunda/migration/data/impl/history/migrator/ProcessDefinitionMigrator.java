/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigratingProcessDefinition;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.history.C7Entity;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating process definitions from Camunda 7 to Camunda 8.
 */
@Service
public class ProcessDefinitionMigrator extends BaseMigrator<ProcessDefinition, ProcessDefinitionDbModel> {

  @Override
  public void migrateAll() {
    fetchMigrateOrRetry(
        HISTORY_PROCESS_DEFINITION,
        c7Client::getProcessDefinition,
        c7Client::fetchAndHandleProcessDefinitions
    );
  }

  /**
   * Migrates a process definition from Camunda 7 to Camunda 8.
   *
   * <p>Process definitions describe the structure and behavior of business processes.
   * This method converts the C7 process definition to C8 format and inserts it into the C8 database.
   *
   * @param c7ProcessDefinition the process definition from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  @Override
  public Long migrateTransactionally(ProcessDefinition c7ProcessDefinition) {
    var c7Id = c7ProcessDefinition.getId();
    if (shouldMigrate(c7Id, HISTORY_PROCESS_DEFINITION)) {
      logMigratingProcessDefinition(c7Id);

      var builder = new ProcessDefinitionDbModelBuilder();

      String startFormId = c7Client.getStartFormId(c7ProcessDefinition);
      builder.formId(prefixDefinitionId(startFormId));

      var creationTime = c7Client.getDefinitionDeploymentTime(c7ProcessDefinition.getDeploymentId());
      var dbModel = convert(C7Entity.of(c7ProcessDefinition, creationTime), builder);
      c8Client.insertProcessDefinition(dbModel);
      return dbModel.processDefinitionKey();
    }

    return null;
  }

}

