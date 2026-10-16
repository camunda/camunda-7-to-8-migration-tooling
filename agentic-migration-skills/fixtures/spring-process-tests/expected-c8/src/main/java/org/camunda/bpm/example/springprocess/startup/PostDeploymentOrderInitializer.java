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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PostDeploymentOrderInitializer {

  private final CamundaClient camundaClient;
  private long lastStartedProcessInstanceKey;

  public PostDeploymentOrderInitializer(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  @EventListener(CamundaPostDeploymentEvent.class)
  public void startProcessAfterDeployment() {
    lastStartedProcessInstanceKey =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("post-deployment-order")
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();
  }

  public long lastStartedProcessInstanceKey() {
    return lastStartedProcessInstanceKey;
  }
}
