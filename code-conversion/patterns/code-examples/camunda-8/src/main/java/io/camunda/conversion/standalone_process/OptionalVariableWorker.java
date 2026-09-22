/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.standalone_process;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OptionalVariableWorker {

    private static final Logger LOG = LoggerFactory.getLogger(OptionalVariableWorker.class);

    @JobWorker(type = "optional-variable-worker", autoComplete = true)
    public Map<String, Object> executeJob(ActivatedJob job) {
        Object x = job.getVariablesAsMap().get("x");
        LOG.info("Optional variable x = {}", x);
        return Map.of("y", "hello world");
    }
}
