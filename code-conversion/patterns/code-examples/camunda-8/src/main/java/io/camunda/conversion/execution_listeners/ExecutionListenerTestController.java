/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.execution_listeners;

import io.camunda.client.CamundaClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ExecutionListenerTestController {

    private final CamundaClient camundaClient;

    public ExecutionListenerTestController(CamundaClient camundaClient) {
        this.camundaClient = camundaClient;
    }

    /**
     * Start the execution-listener-test process with variable "foo".
     *
     * Usage: curl -X POST "http://localhost:8080/test/execution-listener?foo=bar"
     */
    @PostMapping("/test/execution-listener")
    public String startProcess(@RequestParam(name = "foo", defaultValue = "bar") String foo) {
        var result = camundaClient.newCreateInstanceCommand()
                .bpmnProcessId("execution-listener-test")
                .latestVersion()
                .variables(Map.of("foo", foo))
                .send()
                .join();
        return "Started process instance: " + result.getProcessInstanceKey() + " with foo=" + foo;
    }
}

