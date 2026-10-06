/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobWorker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class OrderJobHandlers {

  private OrderJobHandlers() {}

  static List<JobWorker> open(CamundaClient client) {
    List<JobWorker> workers = new ArrayList<>();
    workers.add(
        client.newWorker()
            .jobType("order-audit")
            .handler(
                (jobClient, job) ->
                    jobClient
                        .newCompleteCommand(job)
                        .variables(Map.of("auditStarted", true))
                        .send()
                        .join())
            .open());
    workers.add(openStockWorker(client));
    workers.add(
        client.newWorker()
            .jobType("charge-payment")
            .handler(
                (jobClient, job) ->
                    jobClient
                        .newCompleteCommand(job)
                        .variables(
                            Map.of(
                                "paymentCharged", true,
                                "paymentReference", "payment-42"))
                        .send()
                        .join())
            .open());
    workers.add(
        client.newWorker()
            .jobType("notify-customer")
            .handler(
                (jobClient, job) ->
                    jobClient
                        .newCompleteCommand(job)
                        .variables(Map.of("customerNotified", true))
                        .send()
                        .join())
            .open());
    workers.add(
        client.newWorker()
            .jobType("ship-order")
            .handler(
                (jobClient, job) ->
                    jobClient
                        .newCompleteCommand(job)
                        .variables(Map.of("shipped", true))
                        .send()
                        .join())
            .open());
    return workers;
  }

  static List<JobWorker> openWithoutCharge(CamundaClient client) {
    List<JobWorker> workers = new ArrayList<>();
    workers.add(openStockWorker(client));
    workers.add(
        client.newWorker()
            .jobType("notify-customer")
            .handler(
                (jobClient, job) ->
                    jobClient
                        .newCompleteCommand(job)
                        .variables(Map.of("customerNotified", true))
                        .send()
                        .join())
            .open());
    return workers;
  }

  static JobWorker openStockWorker(CamundaClient client) {
    return client.newWorker()
        .jobType("check-stock")
        .handler(
            (jobClient, job) -> {
              if ("missing".equals(job.getVariablesAsMap().get("sku"))) {
                jobClient
                    .newFailCommand(job)
                    .retries(0)
                    .errorMessage("Stock is unavailable")
                    .send()
                    .join();
                return;
              }
              jobClient
                  .newCompleteCommand(job)
                  .variables(Map.of("stockChecked", true))
                  .send()
                  .join();
            })
        .open();
  }
}
