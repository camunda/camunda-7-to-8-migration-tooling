/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.example.payment;

import java.util.Map;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.context.annotation.Configuration;

@Configuration
@ExternalTaskSubscription("charge-payment")
public class PaymentWorker implements ExternalTaskHandler {

  @Override
  public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
    int amount = externalTask.getVariable("amount");
    externalTaskService.complete(
        externalTask.getId(), Map.of("charged", amount > 0), null);
  }
}
