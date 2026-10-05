/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.community.mockito.DelegateExpressions.autoMock;

import org.camunda.bpm.engine.test.assertions.ProcessEngineTests;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.junit5.ProcessEngineExtension;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.camunda.community.process_test_coverage.junit5.platform7.ProcessEngineCoverageExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({ProcessEngineExtension.class, ProcessEngineCoverageExtension.class})
class OrderAutoMockTest {

  @Test
  @Deployment(resources = "order.bpmn")
  void autoMocksDelegatesAndTracksCoverage() {
    Mocks.register("orderAuditListener", new OrderAuditListener());
    autoMock("order.bpmn");

    ProcessInstance instance =
        org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.runtimeService()
            .startProcessInstanceByKey("order");

    ProcessEngineTests.assertThat(instance).isWaitingAt("Task_Approve");
    assertThat(instance.getProcessDefinitionId()).contains("order");
  }

  @AfterEach
  void resetMocks() {
    Mocks.reset();
  }
}
