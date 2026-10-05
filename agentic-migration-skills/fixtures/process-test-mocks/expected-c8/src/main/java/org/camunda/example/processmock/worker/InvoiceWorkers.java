/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0 You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.example.processmock.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import org.camunda.example.processmock.service.InvoiceService;
import org.springframework.stereotype.Component;

@Component
public class InvoiceWorkers {

  private final InvoiceService invoiceService;

  public InvoiceWorkers(InvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  @JobWorker(type = "validate-invoice")
  public void validateInvoice(@Variable(name = "invoiceId") String invoiceId) {
    invoiceService.isValid(invoiceId);
  }

  @JobWorker(type = "notify-invoice")
  public void notifyInvoice() {}

  @JobWorker(type = "notify-start")
  public void notifyStart() {}

  @JobWorker(type = "auto-validate")
  public void autoValidate() {}

  @JobWorker(type = "auto-notify")
  public void autoNotify() {}

  @JobWorker(type = "auto-start-listener")
  public void autoStartListener() {}
}
