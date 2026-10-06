/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess.startup;

import org.camunda.bpm.engine.RuntimeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupProcessRunner implements CommandLineRunner {

  private final RuntimeService runtimeService;

  public StartupProcessRunner(RuntimeService runtimeService) {
    this.runtimeService = runtimeService;
  }

  @Override
  public void run(String... args) {
    runtimeService.startProcessInstanceByKey("startup-order");
  }
}
