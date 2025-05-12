package org.camunda.conversion.java_delegates.handling_an_incident;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class RetrievePaymentAdapterIncident implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("transactionId", "TX12345");
        execution.createIncident("someType", "someConfiguration", "someMessage");
    }
}

