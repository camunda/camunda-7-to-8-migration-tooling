/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.jgiven.Stage;
import com.tngtech.jgiven.annotation.ScenarioState;
import com.tngtech.jgiven.junit5.ScenarioTest;
import org.junit.jupiter.api.Test;

class JGivenNoEngineTest
    extends ScenarioTest<
        JGivenNoEngineTest.NoEngineStage,
        JGivenNoEngineTest.NoEngineStage,
        JGivenNoEngineTest.NoEngineStage> {

  @Test
  void runsPlainUnitTest() {
    given().aPlainTest();
    when().evaluatesAValue();
    then().theValueIsTrue();
  }

  public static class NoEngineStage extends Stage<NoEngineStage> {

    @ScenarioState private boolean value;

    public NoEngineStage aPlainTest() {
      return this;
    }

    public NoEngineStage evaluatesAValue() {
      value = true;
      return this;
    }

    public NoEngineStage theValueIsTrue() {
      assertTrue(value);
      return this;
    }
  }
}
