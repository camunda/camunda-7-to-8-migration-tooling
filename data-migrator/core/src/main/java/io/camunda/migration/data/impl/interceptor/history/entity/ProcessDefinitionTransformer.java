/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(1)
@Component
public class ProcessDefinitionTransformer implements EntityInterceptor {

  @Autowired
  protected C7Client c7Client;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(ProcessDefinition.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    ProcessDefinition c7ProcessDefinition = (ProcessDefinition) context.getC7Entity();
    ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder builder =
        (ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder) context.getC8DbModelBuilder();

    if (builder == null) {
      throw new EntityInterceptorException("C8 ProcessDefinitionDbModel.Builder is null in context");
    }

    String deploymentId = c7ProcessDefinition.getDeploymentId();
    String resourceName = c7ProcessDefinition.getResourceName();

    String bpmnXml = c7Client.getResourceAsString(deploymentId, resourceName);


    builder.processDefinitionKey(getNextKey())
        .processDefinitionId(prefixDefinitionId(c7ProcessDefinition.getKey()))
        .resourceName(resourceName)
        .name(c7ProcessDefinition.getName())
        .tenantId(getTenantId(c7ProcessDefinition.getTenantId()))
        .versionTag(c7ProcessDefinition.getVersionTag())
        .version(c7ProcessDefinition.getVersion())
        .bpmnXml(bpmnXml)
        .formId(null); // TODO https://github.com/camunda/camunda-bpm-platform/issues/5347
  }

}
