/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.impl.clients.C7Client;
import java.util.ArrayList;
import java.util.List;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "camunda.migrator.page-size=1")
class C7ClientOrderingTest {

  @Autowired
  protected C7Client c7Client;

  @Autowired
  protected RepositoryService repositoryService;

  protected String deploymentId;

  @AfterEach
  public void cleanup() {
    if (deploymentId != null) {
      repositoryService.deleteDeployment(deploymentId, true);
    }
  }

  @Test
  public void shouldPreserveCamunda7QueryOrderingOnPagination() {
    deploymentId = repositoryService.createDeployment()
        .addModelInstance(
            "first.bpmn",
            Bpmn.createExecutableProcess("firstOrderingProcess").startEvent().endEvent().done())
        .addModelInstance(
            "second.bpmn",
            Bpmn.createExecutableProcess("secondOrderingProcess").startEvent().endEvent().done())
        .deploy()
        .getId();
    List<ProcessDefinition> processDefinitions = new ArrayList<>();

    c7Client.fetchAndHandleProcessDefinitions(processDefinitions::add, null);

    assertThat(processDefinitions)
        .extracting(ProcessDefinition::getKey)
        .containsExactly("firstOrderingProcess", "secondOrderingProcess");
  }
}
