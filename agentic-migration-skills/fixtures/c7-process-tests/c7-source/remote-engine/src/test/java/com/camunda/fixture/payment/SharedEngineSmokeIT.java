/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestTemplate;

class SharedEngineSmokeIT {

  @Test
  @EnabledIfEnvironmentVariable(named = "SHARED_ENGINE_URL", matches = ".+")
  void readsConfiguredSharedEngine() {
    String engineRest = System.getenv("SHARED_ENGINE_URL");
    List<?> engines = new RestTemplate().getForObject(engineRest + "/engine", List.class);

    assertThat(engines).isNotEmpty();
  }
}
