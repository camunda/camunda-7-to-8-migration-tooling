/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.TestDeployment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
@TestDeployment(resources = "converted-c8-LegacyOrderTest.testStockMissing.bpmn")
class LegacyOrderTest {

  private CamundaClient client;
  private List<JobWorker> workers;

  @BeforeEach
  void openFailingStockWorker() {
    workers = List.of(
        client.newWorker()
            .jobType("order-audit")
            .handler((jobClient, job) -> jobClient.newCompleteCommand(job).send().join())
            .open(),
        OrderJobHandlers.openStockWorker(client, true));
  }

  @AfterEach
  void closeWorkers() {
    workers.forEach(JobWorker::close);
  }

  @Test
  void testStockMissing() {
    ProcessInstanceEvent instance =
        client.newCreateInstanceCommand()
            .bpmnProcessId("legacyOrder")
            .latestVersion()
            .variables(Map.of("sku", "missing"))
            .send()
            .join();

    assertThat(instance).hasActiveIncidents();
  }

  @Test
  void completesWhenStockIsAvailable() {
    ProcessInstanceEvent instance =
        client.newCreateInstanceCommand()
            .bpmnProcessId("legacyOrder")
            .latestVersion()
            .variables(Map.of("sku", "available"))
            .send()
            .join();

    assertThat(instance)
        .isCompleted()
        .hasCompletedElements("Task_LegacyCheckStock")
        .hasCompletedElements("End_LegacyOrder");
  }
}
