/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.converter;

import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.impl.logging.ProcessDefinitionConverterLogs;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

public class ProcessDefinitionConverter {

  @Autowired
  protected C7Client c7Client;

  public ProcessDefinitionDbModel apply(ProcessDefinition c7ProcessDefinition) {

    String deploymentId = c7ProcessDefinition.getDeploymentId();
    String resourceName = c7ProcessDefinition.getResourceName();

    String bpmnXml = c7Client.getResourceAsString(deploymentId, resourceName);

    return new ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder().processDefinitionKey(getNextKey())
        .processDefinitionId(c7ProcessDefinition.getKey())
        .resourceName(resourceName)
        .name(c7ProcessDefinition.getName())
        .tenantId(c7ProcessDefinition.getTenantId())
        .versionTag(c7ProcessDefinition.getVersionTag())
        .version(c7ProcessDefinition.getVersion())
        .bpmnXml(bpmnXml)
        .formId(null) // TODO https://github.com/camunda/camunda-bpm-platform/issues/5347
        .build();
  }

}
