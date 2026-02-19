/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.bean;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel.Builder;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.qa.util.WhiteBox;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(101)
@Component
@Profile("entity-programmatic")
@WhiteBox
public class UserTaskInterceptor implements EntityInterceptor<HistoricTaskInstance, Builder> {
  protected final AtomicInteger executionCount = new AtomicInteger(0);

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricTaskInstance.class);
  }

  @Override
  public void execute(HistoricTaskInstance entity, Builder builder) {
    executionCount.incrementAndGet();

    Set<String> tags = Set.of("custom-tag", "tag1");
    builder
        .tags(tags);
  }

  public int getExecutionCount() {
    return executionCount.get();
  }

  public void resetCounter() {
    executionCount.set(0);
  }
}

