/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.converter;

import static io.camunda.migrator.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migrator.impl.util.ConverterUtil.convertDate;
import static io.camunda.migrator.impl.util.ConverterUtil.getNextKey;
import static io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.migrator.constants.MigratorConstants;
import io.camunda.migrator.exception.EntityInterceptorException;
import io.camunda.migrator.impl.util.ConverterUtil;
import io.camunda.migrator.interceptor.EntityInterceptor;
import io.camunda.migrator.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricProcessInstance;

public class ProcessInstanceConverter implements EntityInterceptor {


  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricProcessInstance.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    HistoricProcessInstance processInstance = (HistoricProcessInstance) context.getC7Entity();
    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder =
        (ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) context.getC8DbModelBuilder();

    if (builder == null) {
      throw new EntityInterceptorException("C8 ProcessInstanceDbModel.Builder is null in context");
    }

    builder.processInstanceKey(getNextKey())
        // Get key from runtime instance/model migration
        .processDefinitionId(processInstance.getProcessDefinitionKey())
        .startDate(convertDate(processInstance.getStartTime()))
        .endDate(convertDate(processInstance.getEndTime()))
        .state(convertState(processInstance.getState()))
        .tenantId(getTenantId(processInstance))
        .version(processInstance.getProcessDefinitionVersion())
        // parent and super process instance are used synonym (process instance that contained the call activity)
        // TODO: Call activity instance id that created the process in C8. No yet migrated from C7.
        // https://github.com/camunda/camunda-bpm-platform/issues/5359
        //        .parentElementInstanceKey(null)
        //        .treePath(null)
        // TODO https://github.com/camunda/camunda-bpm-platform/issues/5400
//        .numIncidents()
        .partitionId(C7_HISTORY_PARTITION_ID)
        .historyCleanupDate(convertDate(processInstance.getRemovalTime()));
  }

  protected ProcessInstanceState convertState(String state) {
    return switch (state) {
      case "ACTIVE", "SUSPENDED" -> ProcessInstanceState.ACTIVE;
      case "COMPLETED" -> ProcessInstanceState.COMPLETED;
      case "EXTERNALLY_TERMINATED", "INTERNALLY_TERMINATED" -> ProcessInstanceState.CANCELED;

      default -> throw new IllegalArgumentException("Unknown state: " + state);
    };
  }

  protected String getTenantId(HistoricProcessInstance processInstance) {
    return processInstance != null
        ? ConverterUtil.getTenantId(processInstance.getTenantId())
        : MigratorConstants.C8_DEFAULT_TENANT;
  }

}
