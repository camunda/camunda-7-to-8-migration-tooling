/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.job_worker.handling_an_incident;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.spring.client.exception.CamundaError;

import java.time.Duration;
import java.util.Map;

public class RetrievePaymentWorkerIncident {

    // not recommended - runtime condition!
    @JobWorker(type = "retrievePaymentAdapter")
    public Map<String, Object> handleJobAutoCompleteTrue(JobClient client, ActivatedJob job) {
        try {
            // do something...
        } catch (Exception e) {
            throw CamundaError.jobError("My error message", Map.of("transactionId", "TX12345"), 0, null, e);
        }
        return Map.of("transactionId", "TX12345");
    }

    @JobWorker(type = "retrievePaymentAdapter", autoComplete = false)
    public void handleJobAutoCompleteFalseBlocking(JobClient client, ActivatedJob job) {
        try {
            // do something...
        } catch(Exception e) {
            client.newFailCommand(job.getKey())
                    .retries(0)
                    .errorMessage("my error message")
                    .variables(Map.of("transactionId", "TX12345"))
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
                    .retries(0)
                    .errorMessage("my error message")
                    .variables(Map.of("transactionId", "TX12345"))
                    .send()
                    .exceptionally(t -> {
                        throw new RuntimeException("Could not raise incident: " + t.getMessage(), t);
                    });
        }
    }
}
