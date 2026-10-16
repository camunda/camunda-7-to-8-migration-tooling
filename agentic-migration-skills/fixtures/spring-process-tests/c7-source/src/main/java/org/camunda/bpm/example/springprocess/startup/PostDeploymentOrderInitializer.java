/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess.startup;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.spring.boot.starter.event.PostDeployEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PostDeploymentOrderInitializer {

  private final RuntimeService runtimeService;
  private String lastStartedProcessInstanceId;

  public PostDeploymentOrderInitializer(RuntimeService runtimeService) {
    this.runtimeService = runtimeService;
  }

  @EventListener(PostDeployEvent.class)
  public void startProcessAfterDeployment() {
    lastStartedProcessInstanceId =
        runtimeService.startProcessInstanceByKey("post-deployment-order").getId();
  }

  public String lastStartedProcessInstanceId() {
    return lastStartedProcessInstanceId;
  }
}
