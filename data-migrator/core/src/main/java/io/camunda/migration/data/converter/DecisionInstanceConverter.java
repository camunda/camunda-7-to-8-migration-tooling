/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.converter;

import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;

import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.migration.data.impl.VariableService;
import java.util.List;
import org.camunda.bpm.engine.history.HistoricDecisionInputInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricDecisionOutputInstance;
import org.camunda.bpm.engine.impl.variable.serializer.ValueFields;
import org.springframework.beans.factory.annotation.Autowired;

public class DecisionInstanceConverter {

  @Autowired
  protected VariableService variableService;

  public DecisionInstanceDbModel apply(HistoricDecisionInstance decisionInstance,
                                       String decisionInstanceId,
                                       Long decisionDefinitionKey,
                                       Long processDefinitionKey,
                                       Long decisionRequirementsDefinitionKey,
                                       Long processInstanceKey,
                                       Long rootDecisionDefinitionKey,
                                       Long flowNodeInstanceKey,
                                       String flowNodeId) {

    long decisionInstanceKey = Long.parseLong(decisionInstanceId.split("-")[0]);

    return new DecisionInstanceDbModel.Builder()
        .partitionId(C7_HISTORY_PARTITION_ID)
        .decisionInstanceId(decisionInstanceId)
        .decisionInstanceKey(decisionInstanceKey)
        .state(DecisionInstanceEntity.DecisionInstanceState.EVALUATED) // TODO https://github.com/camunda/camunda-bpm-platform/issues/5370
        .evaluationDate(convertDate(decisionInstance.getEvaluationTime()))
        .evaluationFailure(null) // not stored in HistoricDecisionInstance
        .evaluationFailureMessage(null) // not stored in HistoricDecisionInstanc
        .flowNodeInstanceKey(flowNodeInstanceKey)
        .flowNodeId(flowNodeId)
        .processInstanceKey(processInstanceKey)
        .processDefinitionKey(processDefinitionKey)
        .processDefinitionId(decisionInstance.getProcessDefinitionKey())
        .decisionDefinitionKey(decisionDefinitionKey)
        .decisionDefinitionId(decisionInstance.getDecisionDefinitionKey())
        .decisionRequirementsKey(decisionRequirementsDefinitionKey)
        .decisionRequirementsId(decisionInstance.getDecisionRequirementsDefinitionKey())
        .rootDecisionDefinitionKey(rootDecisionDefinitionKey)
        .result(null)
        .decisionType(null) // LITERAL_EXPRESSION, DECISION_TABLE
        .tenantId(getTenantId(decisionInstance.getTenantId()))
        .evaluatedInputs(mapInputs(decisionInstanceId, decisionInstance.getInputs()))
        .evaluatedOutputs(mapOutputs(decisionInstanceId, decisionInstance.getOutputs()))
        .historyCleanupDate(convertDate(decisionInstance.getRemovalTime()))
        .build();
  }

  protected List<DecisionInstanceDbModel.EvaluatedInput> mapInputs(String decisionInstanceId,
                                                                 List<HistoricDecisionInputInstance> c7Inputs) {
    return c7Inputs.stream().map(input -> new DecisionInstanceDbModel.EvaluatedInput(decisionInstanceId,
        input.getId(),
        input.getClauseName(),
        variableService.convertValue((ValueFields) input)
    )).toList();
  }

  protected List<DecisionInstanceDbModel.EvaluatedOutput> mapOutputs(String decisionInstanceId,
                                                                   List<HistoricDecisionOutputInstance> c7Outputs) {
    return c7Outputs.stream().map(output -> new DecisionInstanceDbModel.EvaluatedOutput(decisionInstanceId,
        output.getId(),
        output.getClauseName(),
        variableService.convertValue((ValueFields) output),
        output.getRuleId(),
        output.getRuleOrder())).toList();
  }
}
