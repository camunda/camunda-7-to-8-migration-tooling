package org.camunda.conversion.external_task_workers.handling_process_variables;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ExternalTaskSubscription("retrievePaymentAdapter")
public class RetrievePaymentWorkerProcessVariablesJavaObjectAPI implements ExternalTaskHandler {

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        int amount = (int) externalTask.getVariable("amount");
        // do something
        Map<String, Object> variablesMap = Map.ofEntries(
                Map.entry("transactionId", "TX12345")
        );
        externalTaskService.complete(externalTask.getId(), variablesMap, null);
    }
}