package io.camunda.conversion.job_worker.handling_a_bpmn_error;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.annotation.JobWorker;
import io.camunda.spring.client.exception.CamundaError;

import java.util.Map;

public class RetrievePaymentWorkerBPMNError {

    @JobWorker(type = "retrievePaymentAdapter", fetchVariables = {"amount"})
    public Map<String, Object> handleJobAutoCompleteTrue(JobClient client, ActivatedJob job) {
        throw CamundaError.bpmnError("my error code", "my error message", Map.of("transactionId", "TX12345"));
    }

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJobAutoCompleteFalseBlocking(JobClient client, ActivatedJob job) {
        client.newThrowErrorCommand(job.getKey())
                .errorCode("my error code")
                .errorMessage("my error message")
                .variables(Map.of("transactionId", "TX12345"))
                .send()
                .join();
    }

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJobAutoCompleteFalseReactive(JobClient client, ActivatedJob job) {
        client.newThrowErrorCommand(job.getKey())
                .errorCode("my error code")
                .errorMessage("my error message")
                .variables(Map.of("transactionId", "TX12345"))
                .send()
                .exceptionally(t -> {
                    throw new RuntimeException("Could not throw BPMN error: " + t.getMessage(), t);
                });
    }
}
