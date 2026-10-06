/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess.service;

import io.camunda.client.CamundaClient;
import org.springframework.stereotype.Component;

@Component
public class StartupProcessStarter {

  public void startStartupProcess(CamundaClient camundaClient) {
    camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId("startup-order")
        .latestVersion()
        .send()
        .join();
  }
}
