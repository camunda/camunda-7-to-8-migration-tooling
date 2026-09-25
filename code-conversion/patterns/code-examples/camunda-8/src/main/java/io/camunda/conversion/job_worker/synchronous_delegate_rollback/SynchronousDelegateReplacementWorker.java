/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.job_worker.synchronous_delegate_rollback;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.CamundaError;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SynchronousDelegateReplacementWorker {

    @JobWorker(type = "synchronous-delegate-replacement")
    public void handleJob(ActivatedJob job, @Variable(name = "shouldFail") Boolean shouldFail) {
        if (Boolean.TRUE.equals(shouldFail)) {
            throw CamundaError.jobError(
                    "Expected failure after the preceding user task completed",
                    Map.of(),
                    job.getRetries() - 1,
                    Duration.ZERO);
        }
    }
}
