/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import java.time.Duration;
import org.camunda.bpm.example.springprocess.startup.PostDeploymentOrderInitializer;
import org.camunda.bpm.example.springprocesstest.TestProcessApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = TestProcessApplication.class)
@CamundaSpringProcessTest
class PostDeploymentOrderTest {

  @Autowired private CamundaClient camundaClient;
  private PostDeploymentOrderInitializer postDeploymentOrderInitializer;

  @BeforeEach
  void repeatPostDeploymentListener() {
    postDeploymentOrderInitializer = new PostDeploymentOrderInitializer(camundaClient);
    postDeploymentOrderInitializer.startProcessAfterDeployment();
  }

  @Test
  void completesPostDeploymentOrderFromItsListener() {
    long processInstanceKey = postDeploymentOrderInitializer.lastStartedProcessInstanceKey();
    assertThatProcessInstance(ProcessInstanceSelectors.byKey(processInstanceKey))
        .isCompleted()
        .hasVariable("postDeploymentExecuted", true);
    ProcessInstanceSearchWaiter.awaitCompleted(
        processInstanceKey,
        () ->
            camundaClient
                .newProcessInstanceSearchRequest()
                .filter(filter -> filter.processInstanceKey(processInstanceKey))
                .execute()
                .items(),
        Duration.ofSeconds(20));
  }
}
