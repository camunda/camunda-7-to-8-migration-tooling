/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.converter;

import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.migration.data.constants.MigratorConstants;
import io.camunda.migration.data.impl.VariableService;
import io.camunda.migration.data.impl.util.ConverterUtil;
import org.camunda.bpm.engine.impl.persistence.entity.HistoricVariableInstanceEntity;
import org.springframework.beans.factory.annotation.Autowired;

import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

public class VariableConverter {

  @Autowired
  protected VariableService variableService;

  public VariableDbModel apply(HistoricVariableInstanceEntity historicVariable, Long processInstanceKey, Long scopeKey) {
    return new VariableDbModel.VariableDbModelBuilder()
        .variableKey(getNextKey())
        .name(historicVariable.getName())
        .value(variableService.convertValue(historicVariable))
        .scopeKey(scopeKey)
        .processInstanceKey(processInstanceKey)
        .processDefinitionId(historicVariable.getProcessDefinitionKey())
        .tenantId(ConverterUtil.getTenantId(historicVariable.getTenantId()))
        .partitionId(MigratorConstants.C7_HISTORY_PARTITION_ID)
        .historyCleanupDate(convertDate(historicVariable.getRemovalTime()))
        .build();
  }

}
