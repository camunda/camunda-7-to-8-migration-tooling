/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.conversion.java_delegates.handling_a_failure;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class RetrievePaymentAdapterFailure implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        try {
            // do something...
        } catch (Exception e) {
            execution.setVariable("transactionId", "TX12345");
            throw new ProcessEngineException("my error message", e);
        }
    }
}

