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

public class CustomProcessInstanceInterceptor implements EntityInterceptor<HistoricProcessInstance, ProcessInstanceDbModelBuilder> {
  protected String tenantIdSuffix;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricProcessInstance.class);
  }

  @Override
  public void execute(EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> context) {
      // Modify the tenant ID by appending a suffix
      String originalTenantId = context.getC7Entity().getTenantId();
      String modifiedTenantId = originalTenantId != null
          ? originalTenantId + tenantIdSuffix
          : tenantIdSuffix;
      context.getC8DbModelBuilder().tenantId(modifiedTenantId);
  }

  // Setter for property binding from YAML
  public void setTenantIdSuffix(String tenantIdSuffix) {
    this.tenantIdSuffix = tenantIdSuffix;
  }
}

