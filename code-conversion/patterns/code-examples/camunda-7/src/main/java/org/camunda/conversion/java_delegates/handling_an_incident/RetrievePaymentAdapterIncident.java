/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.conversion.java_delegates.handling_an_incident;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.runtime.Incident;
import org.springframework.stereotype.Component;

@Component
public class RetrievePaymentAdapterIncident implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("transactionId", "TX12345");
        Incident incident = execution.createIncident("someType", "someConfiguration", "someMessage");
    }
}

