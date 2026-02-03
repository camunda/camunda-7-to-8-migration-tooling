/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.db.rdbms.write.domain.VariableDbModel.*;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

import io.camunda.migration.data.constants.MigratorConstants;
import io.camunda.migration.data.impl.VariableService;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.impl.persistence.entity.HistoricVariableInstanceEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(5)
@Component
public class VariableTransformer implements EntityInterceptor<HistoricVariableInstanceEntity, VariableDbModelBuilder> {

  @Autowired
  protected VariableService variableService;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricVariableInstance.class);
  }

  @Override
  public void execute(HistoricVariableInstanceEntity entity, VariableDbModelBuilder builder) {
    builder.variableKey(getNextKey())
        .name(entity.getName())
        .value(variableService.convertValue(entity))
        .processDefinitionId(prefixDefinitionId(entity.getProcessDefinitionKey()))
        .tenantId(getTenantId(entity.getTenantId()))
        .partitionId(MigratorConstants.C7_HISTORY_PARTITION_ID);
    // Note: processInstanceKey and scopeKey are set externally
  }

}
