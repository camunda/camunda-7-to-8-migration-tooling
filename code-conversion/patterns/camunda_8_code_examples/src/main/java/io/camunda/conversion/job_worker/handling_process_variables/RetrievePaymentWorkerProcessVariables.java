package io.camunda.conversion.job_worker.handling_process_variables;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.annotation.JobWorker;
import io.camunda.spring.client.annotation.Variable;
import io.camunda.spring.client.exception.CamundaError;

import java.util.Map;

public class RetrievePaymentWorkerProcessVariables {

    @JobWorker(type = "retrievePaymentAdapter", fetchVariables={"amount"})
    public Map<String, Object> handleJobAutoCompleteTrue(JobClient client, ActivatedJob job) {
        int amount = (int) job.getVariablesAsMap().get("amount");
        // do something...
        return Map.of("transactionId", "TX12345");
    }

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJobAutoCompleteFalseBlocking(JobClient client, ActivatedJob job, @Variable(name = "amount") int amount) {
        // do something...
        client.newCompleteCommand(job.getKey())
                .variables(Map.of("transactionId", "TX12345"))
                .send()
                .join();
    }

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJobAutoCompleteFalseReactive(JobClient client, ActivatedJob job, @Variable(name = "amount") int amount) {
        // do something...
        client.newCompleteCommand(job.getKey())
                .variables(Map.of("transactionId", "TX12345"))
                .send()
                .thenApply(jobResponse -> jobResponse)
                .exceptionally(t -> {
                    throw new RuntimeException("Could not complete job: " + t.getMessage(), t);
                });
    }
}
