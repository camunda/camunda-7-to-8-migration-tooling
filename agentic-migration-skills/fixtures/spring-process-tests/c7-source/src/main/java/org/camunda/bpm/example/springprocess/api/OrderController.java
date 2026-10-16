/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess.api;

import java.util.Map;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstanceWithVariables;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final RuntimeService runtimeService;

  public OrderController(RuntimeService runtimeService) {
    this.runtimeService = runtimeService;
  }

  @PostMapping
  public ResponseEntity<Void> createOrder(@RequestBody OrderRequest request) {
    runtimeService.startProcessInstanceByKey("order", Map.of("amount", request.amount()));
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/total")
  public ResponseEntity<OrderTotalResponse> calculateTotal(@RequestBody OrderRequest request) {
    ProcessInstanceWithVariables instance =
        runtimeService
            .createProcessInstanceByKey("order-total")
            .setVariables(Map.of("amount", request.amount()))
            .executeWithVariablesInReturn();
    return ResponseEntity.ok(
        new OrderTotalResponse(instance.getVariables().getValue("total", Integer.class)));
  }

  public record OrderRequest(int amount) {}

  public record OrderTotalResponse(int total) {}
}
