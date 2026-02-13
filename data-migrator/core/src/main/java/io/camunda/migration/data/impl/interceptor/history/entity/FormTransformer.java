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

import io.camunda.db.rdbms.write.domain.FormDbModel;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import java.util.Set;
import org.camunda.bpm.engine.impl.persistence.entity.CamundaFormDefinitionEntity;
import org.camunda.bpm.engine.repository.CamundaFormDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FormTransformer implements EntityInterceptor<CamundaFormDefinition, FormDbModel.FormDbModelBuilder> {

  @Autowired
  protected C7Client c7Client;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(CamundaFormDefinition.class);
  }

  @Override
  public void execute(CamundaFormDefinition entity, FormDbModel.FormDbModelBuilder builder) {
    String deploymentId = entity.getDeploymentId();
    String resourceName = entity.getResourceName();

    String formJson = c7Client.getResourceAsString(deploymentId, resourceName);
    builder.schema(formJson);

    builder
        .formKey(getNextKey())
        .formId(prefixDefinitionId(entity.getKey()))
        .tenantId(getTenantId(entity.getTenantId()))
        .version((long) entity.getVersion())
        .isDeleted(false);
  }

}
