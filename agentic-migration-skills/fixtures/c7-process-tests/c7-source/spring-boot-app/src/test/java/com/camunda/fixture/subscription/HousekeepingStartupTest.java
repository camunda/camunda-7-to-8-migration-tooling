/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.subscription;

import static org.junit.Assert.assertEquals;

import org.camunda.bpm.engine.RuntimeService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:housekeeping-test;DB_CLOSE_DELAY=-1")
public class HousekeepingStartupTest {

  @Autowired private RuntimeService runtimeService;

  @Test
  public void startsHousekeepingOnDeployment() {
    assertEquals(
        1,
        runtimeService
            .createProcessInstanceQuery()
            .processDefinitionKey("housekeeping")
            .count());
  }
}
