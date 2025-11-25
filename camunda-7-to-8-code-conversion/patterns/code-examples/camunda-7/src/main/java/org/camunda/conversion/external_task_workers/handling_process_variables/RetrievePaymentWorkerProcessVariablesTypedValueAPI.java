package org.camunda.conversion.external_task_workers.handling_process_variables;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.IntegerValue;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.springframework.context.annotation.Configuration;

@Configuration
@ExternalTaskSubscription("retrievePaymentAdapter")
public class RetrievePaymentWorkerProcessVariablesTypedValueAPI implements ExternalTaskHandler {

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        IntegerValue typedAmount = externalTask.getVariableTyped("amount");
        int amount = typedAmount.getValue();
        // do something
        StringValue typedTransactionId = Variables.stringValue("TX12345");
        VariableMap variableMap = Variables.createVariables().putValueTyped("transactionId", typedTransactionId);
        externalTaskService.complete(externalTask.getId(), variableMap, null);
    }
}