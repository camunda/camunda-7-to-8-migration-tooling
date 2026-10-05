/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.subscription;

import io.camunda.client.CamundaClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SubscriptionApplication {

  @Bean
  @ConditionalOnProperty(
      name = "fixture.process-model-deployment",
      havingValue = "true",
      matchIfMissing = true)
  ApplicationRunner deployProcessModels(CamundaClient client) {
    return args ->
        client.newDeployResourceCommand()
            .addResourceFromClasspath("converted-c8-subscription.bpmn")
            .addResourceFromClasspath("subscription-task.form")
            .addResourceFromClasspath("converted-c8-housekeeping.bpmn")
            .addResourceFromClasspath("housekeeping-task.form")
            .send()
            .join();
  }

  public static void main(String[] args) {
    SpringApplication.run(SubscriptionApplication.class, args);
  }
}
