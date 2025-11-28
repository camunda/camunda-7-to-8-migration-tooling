/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.runtime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import io.camunda.migrator.impl.AutoDeployer;
import io.camunda.migrator.config.property.MigratorProperties;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class AutoDeploymentTest extends RuntimeMigrationAbstractTest {

  protected static final Path RESOURCES_PATH = FileSystems.getDefault().getPath("src/test/resources/my-resources");
  protected static final Path HIDDEN_FILE = RESOURCES_PATH.resolve(".hiddenFile");

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add(MigratorProperties.PREFIX + ".c8.deployment-dir",
        () -> RESOURCES_PATH.toAbsolutePath().toString());
  }

  @Autowired
  protected AutoDeployer autoDeployer;

  @AfterEach
  public void cleanUp() throws IOException {
    Files.deleteIfExists(HIDDEN_FILE);
  }

  @Test
  public void shouldAutoDeploy() {
    // given: a BPMN process is placed under ./my-resources

    // when: starting up the Migrator, the process is deployed automatically to C8.
    autoDeployer.deploy();

    // then: we can start a new process instance
    assertThatCode(() -> camundaClient.newCreateInstanceCommand()
        .bpmnProcessId("simpleProcess")
        .latestVersion()
        .send()
        .join()).doesNotThrowAnyException();
  }

  @Test
  public void shouldIgnoreHiddenFiles() throws IOException {
    // given: a BPMN process and a hidden file are placed under ./my-resources
    Files.createFile(HIDDEN_FILE);

    // when: starting up the Migrator, the hidden file is ignored and the process is deployed automatically to C8
    autoDeployer.deploy();

    // then: we can start a new process instance
    assertThatCode(() -> camundaClient.newCreateInstanceCommand()
        .bpmnProcessId("simpleProcess")
        .latestVersion()
        .send()
        .join()).doesNotThrowAnyException();
  }
}