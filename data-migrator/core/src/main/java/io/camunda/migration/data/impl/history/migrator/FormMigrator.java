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
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import java.util.Date;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.camunda.bpm.engine.repository.CamundaFormDefinition;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating forms from Camunda 7 to Camunda 8.
 */
@Service
public class FormMigrator extends HistoryEntityMigrator<CamundaFormDefinition, FormDbModel> {

  @Override
  public BiConsumer<Consumer<CamundaFormDefinition>, Date> fetchForMigrateHandler() {
    return c7Client::fetchAndHandleForms;
  }

  @Override
  public Function<String, CamundaFormDefinition> fetchForRetryHandler() {
    return c7Client::getForm;
  }

  @Override
  public IdKeyMapper.TYPE getType() {
    return HISTORY_FORM_DEFINITION;
  }

  @Override
  public Long migrateTransactionally(CamundaFormDefinition c7Form) {
    String c7Id = c7Form.getId();
    if (shouldMigrate(c7Id, getType())) {
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

