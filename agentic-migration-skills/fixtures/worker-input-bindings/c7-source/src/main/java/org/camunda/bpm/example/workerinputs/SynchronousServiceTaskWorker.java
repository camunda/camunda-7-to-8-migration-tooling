/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.workerinputs;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public class SynchronousServiceTaskWorker implements JavaDelegate {

  @Override
  public void execute(DelegateExecution execution) {
    boolean shouldFail = (boolean) execution.getVariable("shouldFail");
    if (shouldFail) {
      throw new IllegalStateException("The source requested a failure");
    }
  }
}
