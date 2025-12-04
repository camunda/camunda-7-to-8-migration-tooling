/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.job_worker.handling_a_failure;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.annotation.JobWorker;
import io.camunda.spring.client.exception.CamundaError;

import java.time.Duration;
import java.util.Map;

public class RetrievePaymentWorkerFailure {

    // not recommended - runtime condition!
    @JobWorker(type = "retrievePaymentAdapter")
    public Map<String, Object> handleJobAutoCompleteTrue(JobClient client, ActivatedJob job) {
        try {
            // do something...
        } catch(Exception e) {
            throw CamundaError.jobError("My error message", Map.of("transactionId", "TX12345"), job.getRetries() - 1, Duration.ofSeconds(30));
        }
        return Map.of("transactionId", "TX12345");
    }

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJobAutoCompleteFalseBlocking(JobClient client, ActivatedJob job) {
        try {
            // do something...
        } catch(Exception e) {
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage("my error message")
                    .variables(Map.of("transactionId", "TX12345"))
                    .retryBackoff(Duration.ofSeconds(30))
                    .send()
                    .join();
        }
    }

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJobAutoCompleteFalseReactive(JobClient client, ActivatedJob job) {
        try {
            // do something...
        } catch(Exception e) {
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage("my error message")
                    .variables(Map.of("transactionId", "TX12345"))
                    .retryBackoff(Duration.ofSeconds(30))
                    .send()
                    .exceptionally(t -> {
                        throw new RuntimeException("Could not fail job: " + t.getMessage(), t);
                    });
        }
    }
}
