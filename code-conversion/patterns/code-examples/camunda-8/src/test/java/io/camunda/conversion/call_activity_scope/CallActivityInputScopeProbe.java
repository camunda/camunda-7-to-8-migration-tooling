/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.call_activity_scope;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class CallActivityInputScopeProbe {

    private final AtomicReference<Map<String, Object>> receivedVariables =
            new AtomicReference<>(Map.of());

    @JobWorker(type = "call-activity-input-scope-probe", fetchAllVariables = true)
    public void capture(final ActivatedJob job) {
        receivedVariables.set(Map.copyOf(job.getVariablesAsMap()));
    }

    public Map<String, Object> getReceivedVariables() {
        return receivedVariables.get();
    }

    public void reset() {
        receivedVariables.set(Map.of());
    }
}
