/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.bean;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Profile("entity-programmatic")
@Component
@Order(200)
public class ActivityInstanceInterceptor implements EntityInterceptor<HistoricActivityInstance, FlowNodeInstanceDbModelBuilder> {

  protected final AtomicInteger executionCount = new AtomicInteger(0);

  public Set<Class<?>> getTypes() {
    return Set.of(HistoricActivityInstance.class);
  }

  @Override
  public void execute(HistoricActivityInstance c7Entity, FlowNodeInstanceDbModelBuilder c8ModelBuilder) {
    executionCount.incrementAndGet();
    // Add "BEAN_" prefix to tenant ID
    String originalTenantId = c7Entity.getTenantId();
    String modifiedTenantId = originalTenantId != null ? "BEAN_" + originalTenantId : "BEAN_DEFAULT";
    c8ModelBuilder.tenantId(modifiedTenantId);
  }

  public int getExecutionCount() {
    return executionCount.get();

  }

  public void resetCounter() {
    executionCount.set(0);
  }

}