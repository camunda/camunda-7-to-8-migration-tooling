/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess.startup;

import io.camunda.client.CamundaClient;
import io.camunda.client.event.CamundaPostDeploymentEvent;
import org.camunda.bpm.example.springprocess.service.StartupProcessStarter;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupProcessInitializer {

  private final CamundaClient camundaClient;
  private final StartupProcessStarter startupProcessStarter;

  public StartupProcessInitializer(
      CamundaClient camundaClient, StartupProcessStarter startupProcessStarter) {
    this.camundaClient = camundaClient;
    this.startupProcessStarter = startupProcessStarter;
  }

  @EventListener(CamundaPostDeploymentEvent.class)
  public void startProcessAfterDeployment(CamundaPostDeploymentEvent event) {
    startupProcessStarter.startStartupProcess(camundaClient);
  }
}
