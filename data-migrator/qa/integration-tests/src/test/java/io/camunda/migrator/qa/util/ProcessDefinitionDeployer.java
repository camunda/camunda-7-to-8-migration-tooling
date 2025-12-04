/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.util;

import static io.camunda.zeebe.protocol.record.value.TenantOwned.DEFAULT_TENANT_IDENTIFIER;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.search.request.ProcessDefinitionSearchRequest;
import io.camunda.client.api.search.response.ProcessDefinition;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessDefinitionDeployer {

  @Autowired
  protected RepositoryService repositoryService;

  @Autowired
  protected CamundaClient camundaClient;

  public void deployCamunda7Process(String fileName) {
    deployCamunda7Process(fileName, null);
  }

  public void deployCamunda7Process(String fileName, String tenantId) {
    Deployment deployment = repositoryService.createDeployment()
        .tenantId(tenantId)
        .addClasspathResource("io/camunda/migrator/bpmn/c7/" + fileName)
        .deploy();
    if (deployment == null) {
      throw new IllegalStateException("Could not deploy process");
    }
  }

  public void deployCamunda8Process(String fileName) {
    deployCamunda8Process(fileName, null);
  }

  public void deployCamunda8Process(String fileName, String tenantId) {
    if (tenantId == null || tenantId.isEmpty()) {
      tenantId = DEFAULT_TENANT_IDENTIFIER;
    }

    DeploymentEvent deployment = camundaClient.newDeployResourceCommand()
        .addResourceFromClasspath("io/camunda/migrator/bpmn/c8/" + fileName)
        .tenantId(tenantId)
        .execute();

    if (deployment == null) {
      throw new IllegalStateException("Could not deploy process");
    }

    checkC8ProcessDefinitionAvailable("io/camunda/migrator/bpmn/c8/" + fileName, tenantId);
  }

  public void deployCamunda7Decision(String fileName) {
    deployCamunda7Decision(fileName, null);
  }

  public void deployCamunda7Decision(String fileName, String tenantId) {
    Deployment deployment = repositoryService.createDeployment()
        .tenantId(tenantId)
        .addClasspathResource("io/camunda/migrator/dmn/c7/" + fileName)
        .deploy();
    if (deployment == null) {
      throw new IllegalStateException("Could not deploy decision");
    }
  }

  protected void checkC8ProcessDefinitionAvailable(String resourcePath, String tenantId) {

    Awaitility.await().ignoreException(ClientException.class).untilAsserted(() -> {
      ProcessDefinitionSearchRequest endFilter = null;
      if (!StringUtils.isEmpty(tenantId)) {
        endFilter = camundaClient.newProcessDefinitionSearchRequest()
            .filter(filter -> filter.resourceName(resourcePath).tenantId(tenantId));
      } else {
        endFilter = camundaClient.newProcessDefinitionSearchRequest()
            .filter(filter -> filter.resourceName(resourcePath));
      }
      List<ProcessDefinition> items = endFilter.send().join().items();

      // assume
      assertThat(items).hasSize(1);
    });
  }

  public void deployProcessInC7AndC8(String fileName, String tenantId) {
    deployCamunda7Process(fileName, tenantId);
    deployCamunda8Process(fileName, tenantId);
  }

  public void deployProcessInC7AndC8(String fileName) {
    deployCamunda7Process(fileName);
    deployCamunda8Process(fileName);
  }

  public void deployModelInstance(String process,
                                     BpmnModelInstance c7Model,
                                     io.camunda.zeebe.model.bpmn.BpmnModelInstance c8Model) {
    repositoryService.createDeployment().addModelInstance(process + ".bpmn", c7Model).deploy();
    camundaClient.newDeployResourceCommand().addProcessModel(c8Model, process + ".bpmn").execute();
    checkC8ProcessDefinitionAvailable(process + ".bpmn", null);
  }
}
