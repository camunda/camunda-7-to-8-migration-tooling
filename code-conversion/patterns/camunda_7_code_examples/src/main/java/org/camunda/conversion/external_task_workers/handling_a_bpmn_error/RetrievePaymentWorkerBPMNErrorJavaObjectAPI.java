package org.camunda.conversion.external_task_workers.handling_a_bpmn_error;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ExternalTaskSubscription("retrievePaymentAdapter")
public class RetrievePaymentWorkerBPMNErrorJavaObjectAPI implements ExternalTaskHandler {

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        Map<String, Object> variableMap = Map.ofEntries(Map.entry("transactionId", "TX12345"));
        externalTaskService.handleBpmnError(externalTask, "my error code", "my error message", variableMap);
    }
}