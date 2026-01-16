/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.bean;

import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.migration.data.qa.util.WhiteBox;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(50) // Run early to test ProcessEngine access
@Component
@Profile("entity-programmatic")
@WhiteBox
public class ProcessEngineAwareInterceptor implements EntityInterceptor<HistoricProcessInstance, ProcessInstanceDbModelBuilder> {
  protected final AtomicInteger executionCount = new AtomicInteger(0);
  private String lastDeploymentId;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricProcessInstance.class);
  }

  @Override
  public void execute(EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> context) {
    executionCount.incrementAndGet();

    HistoricProcessInstance processInstance = context.getC7Entity();

    ProcessInstanceDbModelBuilder builder = context.getC8DbModelBuilder()
        .processInstanceKey(getNextKey())
        .processDefinitionId(processInstance.getProcessDefinitionKey());;

    // Use ProcessEngine to retrieve deployment information from C7
    ProcessEngine processEngine = context.getProcessEngine();

    // Get process definition to retrieve deployment ID from C7
    String deploymentId = processEngine.getRepositoryService()
        .createProcessDefinitionQuery()
        .processDefinitionKey(processInstance.getProcessDefinitionKey())
        .singleResult()
        .getDeploymentId();

    if (deploymentId != null) {
      lastDeploymentId = deploymentId;

      // Modify tenant ID by appending deployment ID with "C7_DEPLOY_" prefix
      String originalTenantId = processInstance.getTenantId();
      String modifiedTenantId = (originalTenantId != null ? originalTenantId : "<default>") + "_C7_DEPLOY_" + deploymentId;
      builder.tenantId(modifiedTenantId);
    }
  }

  public int getExecutionCount() {
    return executionCount.get();
  }

  public void resetCounter() {
    executionCount.set(0);
    lastDeploymentId = null;
  }

  public String getLastDeploymentId() {
    return lastDeploymentId;
  }
}

