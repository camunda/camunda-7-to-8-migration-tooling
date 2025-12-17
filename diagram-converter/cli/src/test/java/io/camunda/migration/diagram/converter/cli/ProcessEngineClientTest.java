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
import java.util.List;
import org.camunda.bpm.engine.ProcessEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(classes = App.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class ProcessEngineClientTest {
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
  void shouldFindProcessDefinition() {
    processEngine
        .getRepositoryService()
        .createDeployment()
        .name("test")
        .addClasspathResource("c7.bpmn")
        .deploy();
    ProcessEngineClient client =
        ProcessEngineClient.withEngine(
            "http://localhost:" + randomServerPort + "/engine-rest", null, null);
    List<ProcessDefinitionDto> allLatestProcessDefinitions =
        client.getAllLatestProcessDefinitions();
    assertThat(allLatestProcessDefinitions).hasSize(1);
    ProcessDefinitionDto processDefinition = allLatestProcessDefinitions.get(0);
    assertThat(processDefinition.getId()).isNotNull();
    ProcessDefinitionDiagramDto bpmnXml = client.getBpmnXml(processDefinition.getId());
    assertThat(bpmnXml).isNotNull();
    assertThat(bpmnXml.getBpmn20Xml()).isNotNull();
  }
}
