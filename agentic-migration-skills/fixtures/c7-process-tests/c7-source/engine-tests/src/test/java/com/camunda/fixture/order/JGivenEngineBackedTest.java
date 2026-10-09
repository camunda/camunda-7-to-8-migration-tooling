/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tngtech.jgiven.Stage;
import com.tngtech.jgiven.annotation.ScenarioState;
import com.tngtech.jgiven.junit5.ScenarioTest;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.junit5.ProcessEngineExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessEngineExtension.class)
class JGivenEngineBackedTest
    extends ScenarioTest<
        JGivenEngineBackedTest.EngineStage,
        JGivenEngineBackedTest.EngineStage,
        JGivenEngineBackedTest.EngineStage> {

  @Test
  void startsProcess(ProcessEngine processEngine) {
    given().engineIsAvailable(processEngine);
    when().startsProcess();
    then().aProcessInstanceWasCreated();
  }

  public static class EngineStage extends Stage<EngineStage> {

    @ScenarioState private ProcessEngine processEngine;
    @ScenarioState private ProcessInstance processInstance;

    public EngineStage engineIsAvailable(ProcessEngine engine) {
      processEngine = engine;
      return this;
    }

    public EngineStage startsProcess() {
      processEngine
          .getRepositoryService()
          .createDeployment()
          .addClasspathResource("jgiven-inventory.bpmn")
          .deploy();
      processInstance =
          processEngine.getRuntimeService().startProcessInstanceByKey("jgivenInventory");
      return this;
    }

    public EngineStage aProcessInstanceWasCreated() {
      assertNotNull(processInstance);
      return this;
    }
  }
}
