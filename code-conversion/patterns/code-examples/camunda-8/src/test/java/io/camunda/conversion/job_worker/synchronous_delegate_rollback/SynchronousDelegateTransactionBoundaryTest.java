/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.job_worker.synchronous_delegate_rollback;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@CamundaSpringProcessTest
class SynchronousDelegateTransactionBoundaryTest {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private CamundaProcessTestContext processTestContext;

    @Test
    void shouldKeepCompletedUserTaskWhenReplacementWorkerFails() {
        ProcessInstanceEvent processInstance = start(true);

        CamundaAssert.assertThat(processInstance).hasActiveElements("WaitStateBefore");
        processTestContext.completeUserTask("WaitStateBefore");

        CamundaAssert.assertThat(processInstance)
                .hasCompletedElements("WaitStateBefore")
                .hasActiveElements("SynchronousDelegateReplacement")
                .hasActiveIncidents();
    }

    @Test
    void shouldCompleteProcessWhenReplacementWorkerDoesNotFail() {
        ProcessInstanceEvent processInstance = start(false);

        CamundaAssert.assertThat(processInstance).hasActiveElements("WaitStateBefore");
        processTestContext.completeUserTask("WaitStateBefore");

        CamundaAssert.assertThat(processInstance)
                .isCompleted()
                .hasNoActiveIncidents()
                .hasCompletedElements("WaitStateBefore", "SynchronousDelegateReplacement", "EndEvent");
    }

    private ProcessInstanceEvent start(boolean shouldFail) {
        return camundaClient.newCreateInstanceCommand()
                .bpmnProcessId("synchronous-delegate-rollback")
                .latestVersion()
                .variables(Map.of("shouldFail", shouldFail))
                .send()
                .join();
    }
}
