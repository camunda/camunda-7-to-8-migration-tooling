/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.advanced.grpc;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamingJobWorkerExampleTest {

    private final JobClient client = mock(JobClient.class, RETURNS_DEEP_STUBS);
    private final ActivatedJob job = mock(ActivatedJob.class);

    @Test
    void shouldCompleteSuccessfulJob() {
        when(job.getVariablesAsMap()).thenReturn(Map.of("outcome", "complete"));

        StreamingJobWorkerExample.handleJob(client, job);

        verify(client).newCompleteCommand(job);
    }

    @Test
    void shouldFailJobAfterTechnicalFailure() {
        when(job.getVariablesAsMap()).thenReturn(Map.of("outcome", "failure"));
        when(job.getRetries()).thenReturn(3);

        StreamingJobWorkerExample.handleJob(client, job);

        verify(client).newFailCommand(job);
    }

    @Test
    void shouldThrowBpmnErrorForBusinessOutcome() {
        when(job.getVariablesAsMap()).thenReturn(Map.of("outcome", "bpmn-error"));

        StreamingJobWorkerExample.handleJob(client, job);

        verify(client).newThrowErrorCommand(job);
    }

    @Test
    void shouldFailUnsupportedOutcomeWithoutRetry() {
        when(job.getVariablesAsMap()).thenReturn(Map.of("outcome", "unexpected"));

        StreamingJobWorkerExample.handleJob(client, job);

        verify(client).newFailCommand(job);
    }
}
