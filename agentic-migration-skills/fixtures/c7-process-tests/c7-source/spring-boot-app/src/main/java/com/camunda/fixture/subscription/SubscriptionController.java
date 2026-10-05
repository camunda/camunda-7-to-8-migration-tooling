/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.subscription;

import java.util.Map;
import org.camunda.bpm.engine.RuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

  private final RuntimeService runtimeService;

  public SubscriptionController(RuntimeService runtimeService) {
    this.runtimeService = runtimeService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void startSubscription(@RequestBody Map<String, Object> variables) {
    runtimeService.startProcessInstanceByKey("subscription", variables);
  }
}
