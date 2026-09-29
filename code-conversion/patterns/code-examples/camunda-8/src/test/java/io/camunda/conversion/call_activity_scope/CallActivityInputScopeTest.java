/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.call_activity_scope;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@CamundaSpringProcessTest
@DisabledOnOs(OS.WINDOWS)
class CallActivityInputScopeTest {

    @Autowired private CamundaClient camundaClient;

    @Autowired private CallActivityInputScopeProbe probe;

    @BeforeEach
    void resetProbe() {
        probe.reset();
    }

    @Test
    void childReceivesOnlySelectedParentVariables() {
        final ProcessInstanceEvent parent = camundaClient.newCreateInstanceCommand()
                .bpmnProcessId("call-activity-input-scope-parent")
                .latestVersion()
                .variables(Map.of("selectedInput", "selected-value", "parentOnly", "must-not-cross"))
                .send()
                .join();

        CamundaAssert.assertThat(parent).isCompleted();

        assertThat(probe.getReceivedVariables())
                .containsEntry("selectedInput", "selected-value")
                .doesNotContainKey("parentOnly");
    }
}
