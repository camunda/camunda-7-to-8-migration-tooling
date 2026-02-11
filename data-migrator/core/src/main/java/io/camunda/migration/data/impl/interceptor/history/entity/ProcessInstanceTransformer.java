/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.*;
import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;

import io.camunda.migration.data.interceptor.EntityInterceptor;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(2)
@Component
public class ProcessInstanceTransformer implements EntityInterceptor<HistoricProcessInstance, ProcessInstanceDbModelBuilder> {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricProcessInstance.class);
  }

  @Override
  public void execute(HistoricProcessInstance entity, ProcessInstanceDbModelBuilder builder) {
    builder
        // Get key from runtime instance/model migration
        .processDefinitionId(prefixDefinitionId(entity.getProcessDefinitionKey()))
        .startDate(convertDate(entity.getStartTime()))
        .state(convertState(entity.getState()))
        .tenantId(getTenantId(entity.getTenantId()))
        .version(entity.getProcessDefinitionVersion())
        // parent and super process instance are used synonym (process instance that contained the call activity)
        .treePath(null)
        .numIncidents(0)
        .partitionId(C7_HISTORY_PARTITION_ID);
  }

  /**
   * Active/suspended instances are auto-canceled in C8.
   * Externally/internally terminated instances are also represented as CANCELED.
   */
  protected ProcessInstanceState convertState(String state) {
    return switch (state) {
      case "ACTIVE", "SUSPENDED", "EXTERNALLY_TERMINATED", "INTERNALLY_TERMINATED" -> ProcessInstanceState.CANCELED;
      case "COMPLETED" -> ProcessInstanceState.COMPLETED;

      default -> throw new IllegalArgumentException("Unknown state: " + state);
    };
  }

}
