/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.delegate;

import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public class MissingFormDelegate implements JavaDelegate {

  @Override
  public void execute(DelegateExecution execution) {
    FormService formService = execution.getProcessEngineServices().getFormService();
    // This will throw NotFoundException
    formService.getDeployedStartForm(execution.getProcessDefinitionId());
  }
}