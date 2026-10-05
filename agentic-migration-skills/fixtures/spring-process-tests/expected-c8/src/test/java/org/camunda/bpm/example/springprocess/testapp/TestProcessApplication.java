/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess.testapp;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = {
      "org.camunda.bpm.example.springprocess.api",
      "org.camunda.bpm.example.springprocess.service",
      "org.camunda.bpm.example.springprocess.worker"
    })
@Deployment(
    resources = {
      "classpath:processes/converted-c8-order.bpmn",
      "classpath:processes/converted-c8-startup-order.bpmn"
    })
public class TestProcessApplication {}
