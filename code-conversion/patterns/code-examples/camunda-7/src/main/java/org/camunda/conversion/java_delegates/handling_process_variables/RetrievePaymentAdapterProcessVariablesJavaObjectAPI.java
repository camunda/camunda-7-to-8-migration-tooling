package org.camunda.conversion.java_delegates.handling_process_variables;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class RetrievePaymentAdapterProcessVariablesJavaObjectAPI implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        int amount = (int) execution.getVariable("amount");
        // do something...
        execution.setVariable("transactionId", "TX12345");
    }
}

