/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel.*;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import java.util.Set;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(1)
@Component
public class ProcessDefinitionTransformer implements EntityInterceptor<ProcessDefinition, ProcessDefinitionDbModelBuilder> {

  @Autowired
  protected C7Client c7Client;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(ProcessDefinition.class);
  }

  @Override
  public void execute(ProcessDefinition entity, ProcessDefinitionDbModelBuilder builder) {
    var deploymentId = entity.getDeploymentId();
    var resourceName = entity.getResourceName();
    var bpmnXml = c7Client.getResourceAsString(deploymentId, resourceName);

    builder.processDefinitionKey(getNextKey())
        .processDefinitionId(prefixDefinitionId(entity.getKey()))
        .resourceName(resourceName)
        .name(entity.getName())
        .tenantId(entity.getTenantId())
        .versionTag(entity.getVersionTag())
        .version(entity.getVersion())
        .bpmnXml(bpmnXml)
        .formId(null); // TODO https://github.com/camunda/camunda-bpm-platform/issues/5347
  }

}
