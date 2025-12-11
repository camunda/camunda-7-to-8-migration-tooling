/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.converter.webapp;

import org.camunda.community.migration.converter.DiagramConverter;
import org.camunda.community.migration.converter.DiagramConverterFactory;
import org.camunda.community.migration.converter.NotificationService;
import org.camunda.community.migration.converter.NotificationServiceFactory;
import org.camunda.community.migration.converter.excel.ExcelWriter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ConverterApplication {
  public static void main(String[] args) {
    SpringApplication.run(ConverterApplication.class, args);
  }

  @Bean
  public DiagramConverter bpmnConverter(NotificationService notificationService) {
    NotificationServiceFactory.getInstance().setInstance(notificationService);
    return DiagramConverterFactory.getInstance().get();
  }

  @Bean
  public ExcelWriter excelWriter() {
    return new ExcelWriter();
  }
}
