/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.workerinputs;

import java.util.Map;
import java.util.function.Consumer;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public class PersistProjectDelegate implements JavaDelegate {

  private final Consumer<Map<String, Object>> projectPersister;

  public PersistProjectDelegate(Consumer<Map<String, Object>> projectPersister) {
    this.projectPersister = projectPersister;
  }

  @Override
  public void execute(DelegateExecution execution) {
    projectPersister.accept(execution.getVariables());
  }
}
