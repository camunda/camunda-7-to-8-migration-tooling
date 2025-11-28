package org.camunda.conversion.java_delegates.handling_a_bpmn_error;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class RetrievePaymentAdapterBPMNError implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable("transactionId", "TX12345");
        throw new BpmnError("My error code", "My error message");
    }
}

