package org.camunda.conversion.external_task_workers.handling_a_failure;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ExternalTaskSubscription("retrievePaymentAdapter")
public class RetrievePaymentWorkerFailure implements ExternalTaskHandler {

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        try {
            // do something...
        } catch(Exception e) {
            Map<String, Object> variableMap = Map.ofEntries(
                    Map.entry("transactionId", "TX12345")
            );
            externalTaskService.handleFailure(externalTask.getId(), "my error message", "my error details", externalTask.getRetries() - 1, 30000L, variableMap, null);
        }
    }
}