/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.pojo;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricActivityInstance;

public class CustomActivityInstanceInterceptor implements EntityInterceptor {
  protected String tenantIdPrefix;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricActivityInstance.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    HistoricActivityInstance activityInstance = (HistoricActivityInstance) context.getC7Entity();
    FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder builder =
        (FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder) context.getC8DbModelBuilder();

    if (builder != null) {
      // Modify the tenant ID by prepending a prefix
      String originalTenantId = activityInstance.getTenantId();
      String modifiedTenantId = originalTenantId != null
          ? tenantIdPrefix + originalTenantId
          : tenantIdPrefix;
      builder.tenantId(modifiedTenantId);
    }
  }

  // Setter for property binding from YAML
  public void setTenantIdPrefix(String tenantIdPrefix) {
    this.tenantIdPrefix = tenantIdPrefix;
  }
}

