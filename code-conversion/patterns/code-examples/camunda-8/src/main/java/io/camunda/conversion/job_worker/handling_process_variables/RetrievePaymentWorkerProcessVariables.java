/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.job_worker.handling_process_variables;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.spring.client.annotation.Variable;

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
