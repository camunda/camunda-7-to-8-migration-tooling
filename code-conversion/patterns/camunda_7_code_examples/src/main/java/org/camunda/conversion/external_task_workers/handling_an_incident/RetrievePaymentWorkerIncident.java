package org.camunda.conversion.external_task_workers.handling_an_incident;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ExternalTaskSubscription("retrievePaymentAdapter")
public class RetrievePaymentWorkerIncident implements ExternalTaskHandler {

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        try {
            // do something...
        } catch(Exception e) {
            Map<String, Object> variablesMap = Map.ofEntries(
                    Map.entry("transactionId", "TX12345")
            );
            externalTaskService.handleFailure(externalTask.getId(), "my error message", "my error details", 0, 0L, variablesMap, null);
        }
    }
}