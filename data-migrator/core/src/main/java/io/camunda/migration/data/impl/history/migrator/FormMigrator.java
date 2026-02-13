/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigratingForm;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FORM_DEFINITION;

import io.camunda.db.rdbms.write.domain.FormDbModel;
import io.camunda.db.rdbms.write.domain.FormDbModel.FormDbModelBuilder;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.history.C7Entity;
import java.util.Date;
import org.camunda.bpm.engine.impl.persistence.entity.CamundaFormDefinitionEntity;
import org.camunda.bpm.engine.repository.CamundaFormDefinition;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating forms from Camunda 7 to Camunda 8.
 */
@Service
public class FormMigrator extends BaseMigrator<CamundaFormDefinition, FormDbModel> {

  @Override
  public void migrateAll() {
    fetchAndRetry(HISTORY_FORM_DEFINITION,
        c7Client::getForm,
        c7Client::fetchAndHandleForms
    );
  }

  @Override
  public Long migrateTransactionally(CamundaFormDefinition c7Form) {
    String c7Id = c7Form.getId();
    if (shouldMigrate(c7Id, HISTORY_FORM_DEFINITION)) {
      logMigratingForm(c7Id);

      Date deploymentTime = c7Client.getDefinitionDeploymentTime(c7Form.getDeploymentId());
      var builder = new FormDbModelBuilder();
      FormDbModel dbModel = convert(C7Entity.of(c7Form, deploymentTime), builder);
      c8Client.insertForm(dbModel);
      return dbModel.formKey();
    }

    return null;
  }

}

