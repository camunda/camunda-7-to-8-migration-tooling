/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.web;

import io.camunda.client.CamundaClient;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProcessInstanceController {

  private final CamundaClient camundaClient;

  public ProcessInstanceController(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  @PostMapping("/api/process-instances")
  public CompletionStage<StartedProcess> start(@RequestBody Map<String, Object> variables) {
    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId("web-example")
        .latestVersion()
        .variables(variables)
        .send()
        .thenApply(instance -> new StartedProcess(instance.getProcessInstanceKey()));
  }

  public record StartedProcess(long processInstanceKey) {}
}
