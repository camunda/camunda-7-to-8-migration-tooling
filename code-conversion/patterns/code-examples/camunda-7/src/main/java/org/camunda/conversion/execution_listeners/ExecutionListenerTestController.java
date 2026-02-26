/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.conversion.execution_listeners;

import java.util.Map;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExecutionListenerTestController {

    private final RuntimeService runtimeService;

    public ExecutionListenerTestController(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    /**
     * Start the execution-listener-test process with variable "foo".
     *
     * Usage: curl -X POST "http://localhost:8080/test/execution-listener?foo=bar"
     */
    @PostMapping("/test/execution-listener")
    public String startProcess(@RequestParam(name = "foo", defaultValue = "bar") String foo) {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                "execution-listener-test",
                Map.of("foo", foo)
        );
        return "Started process instance: " + pi.getId() + " with foo=" + foo;
    }
}

