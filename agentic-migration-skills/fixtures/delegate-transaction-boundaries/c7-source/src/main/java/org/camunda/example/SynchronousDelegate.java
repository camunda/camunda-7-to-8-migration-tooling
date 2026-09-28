/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.example;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public final class SynchronousDelegate implements JavaDelegate {

    @Override
    public void execute(final DelegateExecution execution) {
        execution.setVariable("delegateStarted", true);
        throw new IllegalStateException("Expected failure for the transaction-boundary path test.");
    }
}
