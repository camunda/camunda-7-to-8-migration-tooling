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
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.migration.data.qa.util.WhiteBox;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(100)
@Component
@Profile("entity-programmatic")
@WhiteBox
public class ProcessInstanceInterceptor implements EntityInterceptor {
  protected final AtomicInteger executionCount = new AtomicInteger(0);

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricProcessInstance.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    executionCount.incrementAndGet();

    HistoricProcessInstance processInstance = (HistoricProcessInstance) context.getC7Entity();
    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder =
        (ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) context.getC8DbModelBuilder();
    builder.processInstanceKey(getNextKey()).processDefinitionId(processInstance.getProcessDefinitionKey());

    if (builder != null) {
      // Add "BEAN_" prefix to tenant ID
      String originalTenantId = processInstance.getTenantId();
      String modifiedTenantId = originalTenantId != null
          ? "BEAN_" + originalTenantId
          : "BEAN_DEFAULT";
      builder.tenantId(modifiedTenantId);
    }
  }

  public int getExecutionCount() {
    return executionCount.get();
  }

  public void resetCounter() {
    executionCount.set(0);
  }
}

