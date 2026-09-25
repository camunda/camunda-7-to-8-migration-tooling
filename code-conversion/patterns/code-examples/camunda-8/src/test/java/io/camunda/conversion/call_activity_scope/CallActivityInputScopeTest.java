/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.call_activity_scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@CamundaSpringProcessTest
class CallActivityInputScopeTest {

    @Autowired private CamundaClient camundaClient;

    @Autowired private CallActivityInputScopeProbe probe;

    @BeforeEach
    void resetProbe() {
        probe.reset();
    }

    @Test
    void childReceivesOnlySelectedParentVariablesAndInheritsBusinessId() {
        final String businessId = "call-scope-" + UUID.randomUUID();
        final ProcessInstanceEvent parent = camundaClient.newCreateInstanceCommand()
                .bpmnProcessId("call-activity-input-scope-parent")
                .latestVersion()
                .businessId(businessId)
                .variables(Map.of("selectedInput", "selected-value", "parentOnly", "must-not-cross"))
                .send()
                .join();

        CamundaAssert.assertThat(parent).isCompleted();

        assertThat(probe.getReceivedVariables())
                .containsEntry("selectedInput", "selected-value")
                .doesNotContainKey("parentOnly");

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    final var instances = camundaClient.newProcessInstanceSearchRequest()
                            .filter(filter -> filter.businessId(businessId))
                            .send()
                            .join()
                            .items();

                    assertThat(instances).hasSize(2);
                });
    }
}
