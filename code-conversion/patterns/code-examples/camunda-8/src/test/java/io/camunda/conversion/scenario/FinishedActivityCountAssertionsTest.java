/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.scenario;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.TestDeployment;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@CamundaSpringProcessTest
@TestDeployment(resources = "scenario-finished-counts.bpmn")
class FinishedActivityCountAssertionsTest {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private CamundaProcessTestContext processTestContext;

    @Test
    void shouldCountCompletedOccurrences() {
        ProcessInstanceEvent processInstance = start();

        processTestContext.completeUserTask("RepeatedTask", Map.of("repeatAgain", false));

        CamundaAssert.assertThat(processInstance)
                .isCompleted()
                .hasCompletedElement("RepeatedTask", 1);
    }

    @Test
    void shouldCountCanceledOccurrences() {
        ProcessInstanceEvent processInstance = start();

        processTestContext.increaseTime(Duration.ofMinutes(2));

        CamundaAssert.assertThat(processInstance)
                .isCompleted()
                .hasTerminatedElement("RepeatedTask", 1);
    }

    @Test
    void shouldCountMixedCompletedAndCanceledOccurrences() {
        ProcessInstanceEvent processInstance = start();

        processTestContext.completeUserTask("RepeatedTask", Map.of("repeatAgain", true));
        CamundaAssert.assertThat(processInstance).hasActiveElements("RepeatedTask");
        processTestContext.increaseTime(Duration.ofMinutes(2));

        CamundaAssert.assertThat(processInstance)
                .isCompleted()
                .hasCompletedElement("RepeatedTask", 1)
                .hasTerminatedElement("RepeatedTask", 1);
    }

    private ProcessInstanceEvent start() {
        return camundaClient.newCreateInstanceCommand()
                .bpmnProcessId("scenario-finished-counts")
                .latestVersion()
                .send()
                .join();
    }
}
