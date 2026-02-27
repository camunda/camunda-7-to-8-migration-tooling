/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.invoice;

import java.util.logging.Logger;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application that starts an embedded Camunda 7 engine with the invoice process.
 *
 * <p>Use this to inspect the process solution before migration and to populate demo data
 * that can then be migrated using the data-migrator.</p>
 *
 * <p>After startup, open <a href="http://localhost:8080">http://localhost:8080</a> to access
 * Cockpit and Tasklist (login: demo/demo).</p>
 */
@SpringBootApplication
public class InvoiceC7Application implements CommandLineRunner {

  private static final Logger LOGGER = Logger.getLogger(InvoiceC7Application.class.getName());

  private final ProcessEngine processEngine;

  public InvoiceC7Application(ProcessEngine processEngine) {
    this.processEngine = processEngine;
  }

  public static void main(String... args) {
    SpringApplication.run(InvoiceC7Application.class, args);
  }

  @Override
  public void run(String... args) {
    RepositoryService repositoryService = processEngine.getRepositoryService();

    // Deploy v1 first so both versions are available
    if (repositoryService.createProcessDefinitionQuery().processDefinitionKey("invoice").count() == 0) {
      LOGGER.info("Deploying invoice process v1...");
      repositoryService.createDeployment()
          .addClasspathResource("invoice.v1.bpmn")
          .addClasspathResource("invoiceBusinessDecisions.dmn")
          .addClasspathResource("reviewInvoice.bpmn")
          .deploy();
    }

    // Deploy v2
    if (repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey("invoice").count() < 2) {
      LOGGER.info("Deploying invoice process v2...");
      repositoryService.createDeployment()
          .addClasspathResource("invoice.v2.bpmn")
          .addClasspathResource("invoiceBusinessDecisions.dmn")
          .addClasspathResource("reviewInvoice.bpmn")
          .deploy();
    }

    // Create demo users, groups, filters and start sample process instances
    InvoiceApplicationHelper.startFirstProcess(processEngine);

    LOGGER.info("\n\n  Invoice Camunda 7 application started."
        + "\n  Open http://localhost:8080 to access Cockpit and Tasklist."
        + "\n  Login: demo / demo\n");
  }
}
