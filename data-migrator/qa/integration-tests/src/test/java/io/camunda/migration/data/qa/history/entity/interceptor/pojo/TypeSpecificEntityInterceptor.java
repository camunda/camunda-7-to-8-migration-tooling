/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.pojo;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

public class TypeSpecificEntityInterceptor implements EntityInterceptor<HistoricProcessInstance, ProcessInstanceDbModelBuilder> {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricProcessInstance.class);
  }

  @Override
  public void execute(HistoricProcessInstance c7Entity, ProcessInstanceDbModelBuilder builder) {
      // Add a specific suffix to tenant ID for type-specific processing
      String originalTenantId = c7Entity.getTenantId();
      String modifiedTenantId = originalTenantId != null
          ? originalTenantId + "-type-specific"
          : "type-specific";
      builder.tenantId(modifiedTenantId);
  }
}

