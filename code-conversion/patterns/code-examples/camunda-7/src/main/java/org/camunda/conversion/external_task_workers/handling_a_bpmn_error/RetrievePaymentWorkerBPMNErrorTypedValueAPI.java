package org.camunda.conversion.external_task_workers.handling_a_bpmn_error;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.springframework.context.annotation.Configuration;

@Configuration
@ExternalTaskSubscription("retrievePaymentAdapter")
public class RetrievePaymentWorkerBPMNErrorTypedValueAPI implements ExternalTaskHandler {

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        StringValue typedTransactionId = Variables.stringValue("TX12345");
        VariableMap variableMap = Variables.createVariables().putValueTyped("transactionId", typedTransactionId);
        externalTaskService.handleBpmnError(externalTask, "my error code", "my error message", variableMap);
    }
}