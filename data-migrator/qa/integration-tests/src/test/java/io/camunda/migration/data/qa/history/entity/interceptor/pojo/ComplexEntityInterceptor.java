/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor.pojo;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComplexEntityInterceptor implements EntityInterceptor {
  private static final Logger LOG = LoggerFactory.getLogger(ComplexEntityInterceptor.class);

  protected String logMessage;
  protected boolean enableTransformation;
  protected String targetTenantId;

  @Override
  public Set<Class<?>> getTypes() {
    // Handle both process instances and activity instances
    return Set.of(HistoricProcessInstance.class, HistoricActivityInstance.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    if (logMessage != null && !logMessage.isEmpty()) {
      LOG.info(logMessage);
    }

    if (!enableTransformation) {
      return;
    }

    Object entity = context.getC7Entity();
    Object builder = context.getC8DbModelBuilder();

    if (entity instanceof HistoricProcessInstance && builder instanceof ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) {
      ProcessInstanceDbModel.ProcessInstanceDbModelBuilder processBuilder =
          (ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) builder;
      if (targetTenantId != null) {
        processBuilder.tenantId(targetTenantId);
      }
    } else if (entity instanceof HistoricActivityInstance && builder instanceof FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder) {
      FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder flowNodeBuilder =
          (FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder) builder;
      if (targetTenantId != null) {
        flowNodeBuilder.tenantId(targetTenantId);
      }
    }
  }

  // Setters for property binding from YAML
  public void setLogMessage(String logMessage) {
    this.logMessage = logMessage;
  }

  public void setEnableTransformation(boolean enableTransformation) {
    this.enableTransformation = enableTransformation;
  }

  public void setTargetTenantId(String targetTenantId) {
    this.targetTenantId = targetTenantId;
  }
}

