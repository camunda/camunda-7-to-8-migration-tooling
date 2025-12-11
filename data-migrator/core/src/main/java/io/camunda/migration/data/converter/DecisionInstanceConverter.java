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
import java.util.List;
import org.camunda.bpm.engine.history.HistoricDecisionInputInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricDecisionOutputInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Expression;
import org.camunda.bpm.model.dmn.instance.LiteralExpression;

public class DecisionInstanceConverter {

  public DecisionInstanceDbModel apply(HistoricDecisionInstance decisionInstance,
                                       String decisionInstanceId,
                                       Long decisionDefinitionKey,
                                       Long processDefinitionKey,
                                       Long decisionRequirementsDefinitionKey,
                                       Long processInstanceKey,
                                       Long rootDecisionDefinitionKey,
                                       Long flowNodeInstanceKey,
                                       String flowNodeId,
                                       DmnModelInstance dmnModelInstance) {

    long decisionInstanceKey = Long.parseLong(decisionInstanceId.split("-")[0]);
    DecisionInstanceEntity.DecisionDefinitionType decisionType = determineDecisionType(dmnModelInstance, decisionInstance.getDecisionDefinitionKey());

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
        .decisionType(decisionType)
        .tenantId(getTenantId(decisionInstance.getTenantId()))
        .evaluatedInputs(mapInputs(decisionInstanceId, decisionInstance.getInputs()))
        .evaluatedOutputs(mapOutputs(decisionInstanceId, decisionInstance.getOutputs()))
        .historyCleanupDate(convertDate(decisionInstance.getRemovalTime()))
        .build();
  }

  protected DecisionInstanceEntity.DecisionDefinitionType determineDecisionType(DmnModelInstance dmnModelInstance,
                                                                                String decisionDefinitionId) {
    Decision decision = dmnModelInstance.getModelElementById(decisionDefinitionId);
    if (decision == null) {
      return null;
    }

    if (decision.getExpression() instanceof LiteralExpression) {
      return DecisionInstanceEntity.DecisionDefinitionType.LITERAL_EXPRESSION;
    } else {
      return DecisionInstanceEntity.DecisionDefinitionType.DECISION_TABLE;
    }
  }

  protected List<DecisionInstanceDbModel.EvaluatedInput> mapInputs(String decisionInstanceId,
                                                                 List<HistoricDecisionInputInstance> c7Inputs) {
    return c7Inputs.stream().map(input -> new DecisionInstanceDbModel.EvaluatedInput(decisionInstanceId,
        input.getId(),
        input.getClauseName(),
        String.valueOf(input.getValue())
    )).toList();
  }

  protected List<DecisionInstanceDbModel.EvaluatedOutput> mapOutputs(String decisionInstanceId,
                                                                   List<HistoricDecisionOutputInstance> c7Outputs) {
    return c7Outputs.stream().map(output -> new DecisionInstanceDbModel.EvaluatedOutput(decisionInstanceId,
        output.getId(),
        output.getClauseName(),
        String.valueOf(output.getValue()),
        output.getRuleId(),
        output.getRuleOrder() != null ? output.getRuleOrder() : 1)).toList();
  }
}
