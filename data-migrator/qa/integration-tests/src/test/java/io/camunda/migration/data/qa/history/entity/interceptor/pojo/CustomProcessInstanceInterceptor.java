/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.pojo;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricProcessInstance;

public class CustomProcessInstanceInterceptor implements EntityInterceptor {
  protected String tenantIdSuffix;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricProcessInstance.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    HistoricProcessInstance processInstance = (HistoricProcessInstance) context.getC7Entity();
    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder =
        (ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) context.getC8DbModelBuilder();

    if (builder != null) {
      // Modify the tenant ID by appending a suffix
      String originalTenantId = processInstance.getTenantId();
      String modifiedTenantId = originalTenantId != null
          ? originalTenantId + tenantIdSuffix
          : tenantIdSuffix;
      builder.tenantId(modifiedTenantId);
    }
  }

  // Setter for property binding from YAML
  public void setTenantIdSuffix(String tenantIdSuffix) {
    this.tenantIdSuffix = tenantIdSuffix;
  }
}

