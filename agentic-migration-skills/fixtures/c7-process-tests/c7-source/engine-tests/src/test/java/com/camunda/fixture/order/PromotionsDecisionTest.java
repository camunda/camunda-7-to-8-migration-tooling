/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.camunda.bpm.dmn.engine.DmnDecisionTableResult;
import org.camunda.bpm.engine.DecisionService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.test.junit5.ProcessEngineExtension;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessEngineExtension.class)
class PromotionsDecisionTest {

  @BeforeEach
  void deployPromotions(ProcessEngine processEngine) {
    processEngine
        .getRepositoryService()
        .createDeployment()
        .addClasspathResource("promotions.dmn")
        .deploy();
  }

  @Test
  void evaluatesRequiredDecision(ProcessEngine processEngine) {
    DecisionService decisionService = processEngine.getDecisionService();

    DmnDecisionTableResult result =
        decisionService
            .evaluateDecisionTableByKey("promotions")
            .variables(Variables.putValue("amount", 120))
            .evaluate();

    assertEquals(2, result.getResultList().size());
  }

  @Test
  void collectsPromotionRules(ProcessEngine processEngine) {
    DmnDecisionTableResult result =
        processEngine
            .getDecisionService()
            .evaluateDecisionTableByKey("promotions")
            .variables(Map.of("amount", 120))
            .evaluate();

    assertEquals(2, result.getResultList().size());
  }

  @Test
  void raisesUniqueHitPolicyViolation(ProcessEngine processEngine) {
    assertThrows(
        ProcessEngineException.class,
        () ->
            processEngine
                .getDecisionService()
                .evaluateDecisionTableByKey("promotionUniqueViolation")
                .variables(Map.of("tier", "gold"))
                .evaluate());
  }
}
