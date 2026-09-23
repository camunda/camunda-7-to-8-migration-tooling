/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.standalone_process;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@CamundaSpringProcessTest
class StandaloneProcessTest {

    @Autowired
    private CamundaClient camundaClient;

    @Test
    void shouldCompleteStandaloneProcessWithoutOptionalVariable() {
        ProcessInstanceEvent processInstance = start(Map.of());

        CamundaAssert.assertThat(processInstance)
                .isCompleted()
                .hasNoActiveIncidents()
                .hasVariable("y", "hello world");
    }

    @Test
    void shouldCompleteStandaloneProcessWithOptionalVariable() {
        ProcessInstanceEvent processInstance = start(Map.of("x", 7));

        CamundaAssert.assertThat(processInstance)
                .isCompleted()
                .hasNoActiveIncidents()
                .hasVariable("y", "hello world");
    }

    private ProcessInstanceEvent start(Map<String, Object> variables) {
        return camundaClient.newCreateInstanceCommand()
                .bpmnProcessId("sub-process")
                .latestVersion()
                .variables(variables)
                .send()
                .join();
    }
}
