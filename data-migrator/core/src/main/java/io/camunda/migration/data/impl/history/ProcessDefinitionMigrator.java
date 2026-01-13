/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;

import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Date;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating process definitions from Camunda 7 to Camunda 8.
 */
@Service
public class ProcessDefinitionMigrator extends BaseMigrator<ProcessDefinition> {

  public void migrate() {
    HistoryMigratorLogs.migratingProcessDefinitions();
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_PROCESS_DEFINITION, idKeyDbModel -> {
        ProcessDefinition historicProcessDefinition = c7Client.getProcessDefinition(idKeyDbModel.getC7Id());
        self.migrateOne(historicProcessDefinition);
      });
    } else {
      Date createTime = dbClient.findLatestCreateTimeByType(HISTORY_PROCESS_DEFINITION);
      c7Client.fetchAndHandleProcessDefinitions(self::migrateOne, createTime);
    }
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
  public void migrateOne(ProcessDefinition c7ProcessDefinition) {
    String c7Id = c7ProcessDefinition.getId();
    if (shouldMigrate(c7Id, HISTORY_PROCESS_DEFINITION)) {
      HistoryMigratorLogs.migratingProcessDefinition(c7Id);
      Date deploymentTime = c7Client.getDefinitionDeploymentTime(c7ProcessDefinition.getDeploymentId());
      try {
        ProcessDefinitionDbModel dbModel = convertProcessDefinition(c7ProcessDefinition);
        c8Client.insertProcessDefinition(dbModel);
        markMigrated(c7Id, dbModel.processDefinitionKey(), deploymentTime, HISTORY_PROCESS_DEFINITION);
        HistoryMigratorLogs.migratingProcessDefinitionCompleted(c7Id);
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7Id, HISTORY_PROCESS_DEFINITION, deploymentTime, e);
      }
    }
  }

  protected ProcessDefinitionDbModel convertProcessDefinition(ProcessDefinition c7ProcessDefinition) {
    EntityConversionContext<?, ?> context = createEntityConversionContext(c7ProcessDefinition, ProcessDefinition.class,
        new ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder());
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder builder = (ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }
}

