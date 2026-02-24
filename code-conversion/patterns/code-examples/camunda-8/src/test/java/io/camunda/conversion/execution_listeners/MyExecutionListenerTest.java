/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.execution_listeners;

import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@CamundaSpringProcessTest
class MyExecutionListenerTest {

    @Autowired
    private CamundaClient camundaClient;

    @Test
    void shouldExecuteMigratedExecutionListener() {
        // given: deploy and start process with variable "foo"
        camundaClient.newCreateInstanceCommand()
                .bpmnProcessId("execution-listener-test")
                .latestVersion()
                .variables(Map.of("foo", "bar"))
                .send()
                .join();

        // then: the process completes (the @JobWorker handles the service task)
        CamundaAssert.assertThat(byProcessId("execution-listener-test")).isCompleted();
    }
}

