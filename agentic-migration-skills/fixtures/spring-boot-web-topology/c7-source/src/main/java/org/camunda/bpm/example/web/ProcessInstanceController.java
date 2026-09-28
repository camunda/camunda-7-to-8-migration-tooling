/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.web;

import java.util.Map;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProcessInstanceController {

  private final RuntimeService runtimeService;

  public ProcessInstanceController(RuntimeService runtimeService) {
    this.runtimeService = runtimeService;
  }

  @PostMapping("/api/process-instances")
  public StartedProcess start(@RequestBody Map<String, Object> variables) {
    ProcessInstance instance = runtimeService.startProcessInstanceByKey("web-example", variables);
    return new StartedProcess(instance.getProcessInstanceId());
  }

  public record StartedProcess(String processInstanceId) {}
}
