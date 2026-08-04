/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.cli;

import static org.assertj.core.api.Assertions.*;

import io.camunda.migration.diagram.converter.cli.mock.App;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(classes = App.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class ConvertEngineCommandTest {

  @LocalServerPort int randomServerPort;
  @Autowired ProcessEngine processEngine;

  @AfterEach
  void cleanUp() {
    processEngine
        .getRepositoryService()
        .createDeploymentQuery()
        .list()
        .forEach(
            deployment ->
                processEngine
                    .getRepositoryService()
                    .deleteDeployment(deployment.getId(), true, true, true));
  }

  @Test
  void shouldConvertSingleFileWithMultipleProcesses(@TempDir File tempDir) throws Exception {
    processEngine
        .getRepositoryService()
        .createDeployment()
        .name("test")
        .addClasspathResource("multiple-processes.bpmn")
        .deploy();
    ConvertEngineCommand command = new ConvertEngineCommand();
    command.targetDirectory = tempDir;
    command.url = "http://localhost:" + randomServerPort + "/engine-rest";
    command.call();
    assertThat(tempDir.listFiles()).hasSize(1);
  }

  @Test
  void shouldConvertSingleFileWithMultipleProcessesAndExportExcel(@TempDir File tempDir)
      throws Exception {
    processEngine
        .getRepositoryService()
        .createDeployment()
        .name("test")
        .addClasspathResource("multiple-processes.bpmn")
        .deploy();
    ConvertEngineCommand command = new ConvertEngineCommand();
    command.targetDirectory = tempDir;
    command.xlsx = true;
    command.url = "http://localhost:" + randomServerPort + "/engine-rest";
    command.call();
    assertThat(tempDir.listFiles()).hasSize(2);
    assertThat(
            Arrays.stream(tempDir.listFiles())
                .filter(file -> file.getName().endsWith(".xlsx"))
                .count())
        .isEqualTo(1);
  }

  @Test
  void shouldConvertManyFilesWithSameName(@TempDir File tempDir) throws Exception {
    BpmnModelInstance test1 =
        Bpmn.createProcess("test1")
            .executable()
            .camundaHistoryTimeToLive(180)
            .startEvent("x")
            .endEvent("y")
            .done();
    BpmnModelInstance test2 =
        Bpmn.createProcess("test2")
            .executable()
            .camundaHistoryTimeToLive(180)
            .startEvent("a")
            .endEvent("b")
            .done();
    processEngine
        .getRepositoryService()
        .createDeployment()
        .name("test")
        .addModelInstance("test.bpmn", test2)
        .addClasspathResource("first.dmn")
        .deploy();
    processEngine
        .getRepositoryService()
        .createDeployment()
        .name("test")
        .addModelInstance("test.bpmn", test1)
        .deploy();
    ConvertEngineCommand command = new ConvertEngineCommand();
    command.targetDirectory = tempDir;
    command.url = "http://localhost:" + randomServerPort + "/engine-rest";
    command.call();
    assertThat(tempDir.listFiles()).hasSize(3);
  }

  @Test
  void shouldCreateTargetDirectoryForPathBasedBpmnAndDmnResources(@TempDir File tempDir)
      throws Exception {
    BpmnModelInstance bpmn =
        Bpmn.createProcess("pathBasedProcess")
            .executable()
            .camundaHistoryTimeToLive(180)
            .startEvent("start")
            .endEvent("end")
            .done();
    try (InputStream dmn =
        Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("first.dmn"))) {
      processEngine
          .getRepositoryService()
          .createDeployment()
          .name("path-based-resources")
          .addModelInstance(
              "Users/test-user/projects/application/target/classes/process.bpmn", bpmn)
          .addInputStream("Users/test-user/projects/application/target/classes/decision.dmn", dmn)
          .deploy();
    }

    File targetDirectory = new File(tempDir, "converted");
    ConvertEngineCommand command = new ConvertEngineCommand();
    command.targetDirectory = targetDirectory;
    command.url = "http://localhost:" + randomServerPort + "/engine-rest";

    assertThat(command.call()).isZero();
    assertThat(targetDirectory.listFiles())
        .extracting(File::getName)
        .containsExactlyInAnyOrder("converted-c8-process.bpmn", "converted-c8-decision.dmn");
  }

  @Test
  void shouldKeepPathBasedResourcesWithSameFilename(@TempDir File tempDir) {
    BpmnModelInstance first =
        Bpmn.createProcess("firstPathBasedProcess")
            .executable()
            .startEvent("start")
            .endEvent("end")
            .done();
    BpmnModelInstance second =
        Bpmn.createProcess("secondPathBasedProcess")
            .executable()
            .startEvent("start")
            .endEvent("end")
            .done();
    processEngine
        .getRepositoryService()
        .createDeployment()
        .name("first-path")
        .addModelInstance("first/application/process.bpmn", first)
        .deploy();
    processEngine
        .getRepositoryService()
        .createDeployment()
        .name("second-path")
        .addModelInstance("second/application/process.bpmn", second)
        .deploy();

    File targetDirectory = new File(tempDir, "converted");
    ConvertEngineCommand command = new ConvertEngineCommand();
    command.targetDirectory = targetDirectory;
    command.url = "http://localhost:" + randomServerPort + "/engine-rest";

    assertThat(command.call()).isZero();
    assertThat(targetDirectory.listFiles())
        .extracting(File::getName)
        .containsExactlyInAnyOrder("converted-c8-process.bpmn", "converted-c8-process (1).bpmn");
  }
}
