/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess.api;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final CamundaClient camundaClient;

  public OrderController(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  @PostMapping
  public ResponseEntity<StartedOrder> createOrder(@RequestBody OrderRequest request) {
    ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("order")
            .latestVersion()
            .variables(Map.of("amount", request.amount()))
            .send()
            .join();
    return ResponseEntity.accepted().body(new StartedOrder(processInstance.getProcessInstanceKey()));
  }

  public record OrderRequest(int amount) {}

  public record StartedOrder(long processInstanceKey) {}
}
