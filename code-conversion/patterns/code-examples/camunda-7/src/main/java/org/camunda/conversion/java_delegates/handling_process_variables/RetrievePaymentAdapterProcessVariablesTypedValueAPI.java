/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.conversion.java_delegates.handling_process_variables;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.IntegerValue;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.springframework.stereotype.Component;

@Component
public class RetrievePaymentAdapterProcessVariablesTypedValueAPI implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        IntegerValue typedAmount = execution.getVariableTyped("amount");
        int amount = typedAmount.getValue();
        // do something...
        StringValue typedTransactionId = Variables.stringValue("TX12345");
        execution.setVariable("transactionId", typedTransactionId);
    }
}

