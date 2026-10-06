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
import java.util.function.Supplier;

final class OrderJobHandlers {

  private OrderJobHandlers() {}

  static List<JobWorker> open(CamundaClient client) {
    return open(client, () -> defaultNotificationWorker(client));
  }

  static List<JobWorker> open(CamundaClient client, NotificationService notificationService) {
    return open(client, () -> openNotificationWorker(client, notificationService));
  }

  private static List<JobWorker> open(
      CamundaClient client, Supplier<JobWorker> notificationWorkerFactory) {
    List<JobWorker> workers = new ArrayList<>();
    workers.add(openAuditWorker(client));
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
    workers.add(notificationWorkerFactory.get());
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

  private static JobWorker defaultNotificationWorker(CamundaClient client) {
    return client.newWorker()
        .jobType("notify-customer")
        .handler(
            (jobClient, job) ->
                jobClient
                    .newCompleteCommand(job)
                    .variables(Map.of("customerNotified", true))
                    .send()
                    .join())
        .open();
  }

  static JobWorker openAuditWorker(CamundaClient client) {
    return client.newWorker()
        .jobType("order-audit")
        .handler(
            (jobClient, job) ->
                jobClient
                    .newCompleteCommand(job)
                    .variables(Map.of("auditStarted", true))
                    .send()
                    .join())
        .open();
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

  static JobWorker openNotificationWorker(
      CamundaClient client, NotificationService notificationService) {
    return client.newWorker()
        .jobType("notify-customer")
        .handler(
            (jobClient, job) -> {
              notificationService.notifyPaymentFailed(job.getVariablesAsMap());
              jobClient.newCompleteCommand(job).send().join();
            })
        .open();
  }

  interface NotificationService {

    void notifyPaymentFailed(Map<String, Object> variables);
  }
}
