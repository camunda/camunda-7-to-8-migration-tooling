/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.junit5.ProcessEngineExtension;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessEngineExtension.class)
class FluentModelTest {

  @Test
  void buildsAndStartsModel(ProcessEngine processEngine) {
    processEngine
        .getRepositoryService()
        .createDeployment()
        .addModelInstance(
            "fluent-order.bpmn",
            Bpmn.createExecutableProcess("fluentOrder")
                .startEvent()
                .userTask("Task_FluentReview")
                .endEvent()
                .done())
        .deploy();

    ProcessInstance instance =
        processEngine.getRuntimeService().startProcessInstanceByKey("fluentOrder");

    assertNotNull(instance);
  }
}
