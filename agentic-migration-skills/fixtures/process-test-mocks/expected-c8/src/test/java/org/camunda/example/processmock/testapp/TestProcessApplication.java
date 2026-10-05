/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.example.processmock.testapp;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = "org.camunda.example.processmock.worker")
@Deployment(
    resources = {
      "classpath:processes/converted-c8-invoice.bpmn",
      "classpath:processes/converted-c8-auto-mock-invoice.bpmn",
      "classpath:processes/converted-c8-task-listener.bpmn",
      "classpath:processes/converted-c8-task-listener-twice.bpmn",
      "classpath:processes/converted-c8-task-listener-never.bpmn",
      "classpath:processes/converted-c8-decision-output.bpmn",
      "classpath:processes/converted-c8-invoice-risk.dmn"
    })
public class TestProcessApplication {}
