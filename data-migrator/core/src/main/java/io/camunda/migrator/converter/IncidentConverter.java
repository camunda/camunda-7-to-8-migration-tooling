/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.converter;

import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.search.entities.IncidentEntity;
import org.camunda.bpm.engine.history.HistoricIncident;

import static io.camunda.migrator.impl.util.ConverterUtil.convertDate;
import static io.camunda.migrator.impl.util.ConverterUtil.getNextKey;

public class IncidentConverter {

  public IncidentDbModel apply(HistoricIncident historicIncident,
                               Long processDefinitionKey,
                               Long processInstanceKey,
                               Long jobDefinitionKey,
                               Long flowNodeInstanceKey) {
    return new IncidentDbModel.Builder()
        .incidentKey(getNextKey())
        .processDefinitionKey(processDefinitionKey)
        .processDefinitionId(historicIncident.getProcessDefinitionKey())
        .processInstanceKey(processInstanceKey)
        .flowNodeInstanceKey(flowNodeInstanceKey) //TODO: is this linking correct?
        .flowNodeId(historicIncident.getActivityId())
        .jobKey(jobDefinitionKey)
        .errorType(null) // TODO: does error type exist in C7?
        .errorMessage(historicIncident.getIncidentMessage())
        .creationDate(convertDate(historicIncident.getCreateTime()))
        .state(convertState(0)) //TODO: make HistoricIncidentEventEntity#getIncidentState() accessible
        .treePath(null) //TODO ?
        .tenantId(historicIncident.getTenantId())
        .build();
  }

  private IncidentEntity.IncidentState convertState(Integer state) {
    return switch (state) {
      case 0 -> IncidentEntity.IncidentState.ACTIVE; // open
      case 1, 2 -> IncidentEntity.IncidentState.RESOLVED; // resolved/deleted

      default -> throw new IllegalArgumentException("Unknown state: " + state);
    };
  }

}
