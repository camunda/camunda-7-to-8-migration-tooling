/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.advanced.grpc;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public final class StreamingJobWorkerExample {

    private static final String JOB_TYPE = "process-payment";

    private StreamingJobWorkerExample() {
    }

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch shutdownRequested = new CountDownLatch(1);
        CountDownLatch cleanupComplete = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdownRequested.countDown();
            try {
                cleanupComplete.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        try {
            try (CamundaClient client = createClient();
                 JobWorker worker = client.newWorker()
                         .jobType(JOB_TYPE)
                         .handler(StreamingJobWorkerExample::handleJob)
                         .name("payment-stream-worker")
                         .maxJobsActive(32)
                         .timeout(Duration.ofMinutes(2))
                         .streamEnabled(true)
                         .streamTimeout(Duration.ofMinutes(1))
                         .open()) {
                System.out.printf("Streaming worker '%s' started. Press Ctrl+C to stop.%n", JOB_TYPE);
                shutdownRequested.await();
            }
        } finally {
            cleanupComplete.countDown();
        }
    }

    static void handleJob(JobClient client, ActivatedJob job) {
        Object outcomeVariable = job.getVariablesAsMap().get("outcome");
        String outcome = outcomeVariable == null || outcomeVariable.toString().isBlank()
                ? "complete"
                : outcomeVariable.toString();

        switch (outcome) {
            case "complete" -> client.newCompleteCommand(job)
                    .variables(Map.of("paymentStatus", "captured"))
                    .send()
                    .join();
            case "failure" -> client.newFailCommand(job)
                    .retries(Math.max(0, job.getRetries() - 1))
                    .errorMessage("Payment provider is temporarily unavailable")
                    .retryBackoff(Duration.ofSeconds(30))
                    .send()
                    .join();
            case "bpmn-error" -> client.newThrowErrorCommand(job)
                    .errorCode("PAYMENT_REJECTED")
                    .errorMessage("The payment was rejected")
                    .variables(Map.of("paymentStatus", "rejected"))
                    .send()
                    .join();
            default -> client.newFailCommand(job)
                    .retries(0)
                    .errorMessage("Unsupported outcome: " + outcome)
                    .send()
                    .join();
        }
    }

    private static CamundaClient createClient() {
        CamundaClientBuilder builder = CamundaClient.newClientBuilder()
                .grpcAddress(URI.create(requiredEnvironmentVariable("CAMUNDA_GRPC_ADDRESS")))
                .preferRestOverGrpc(false)
                .defaultJobWorkerName("payment-stream-worker");

        String clientId = System.getenv("CAMUNDA_CLIENT_ID");
        if (clientId != null && !clientId.isBlank()) {
            builder.credentialsProvider(new OAuthCredentialsProviderBuilder()
                    .clientId(clientId)
                    .clientSecret(requiredEnvironmentVariable("CAMUNDA_CLIENT_SECRET"))
                    .audience(requiredEnvironmentVariable("CAMUNDA_TOKEN_AUDIENCE"))
                    .authorizationServerUrl(requiredEnvironmentVariable("CAMUNDA_TOKEN_URL"))
                    .build());
        }

        return builder.build();
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable " + name + " is required");
        }
        return value;
    }
}
