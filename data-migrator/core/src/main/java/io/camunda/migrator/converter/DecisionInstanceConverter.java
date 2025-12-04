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
import static io.camunda.migrator.impl.util.ConverterUtil.getTenantId;

import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.migrator.exception.EntityInterceptorException;
import io.camunda.migrator.interceptor.EntityInterceptor;
import io.camunda.migrator.interceptor.property.EntityConversionContext;
import java.util.List;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricDecisionInputInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricDecisionOutputInstance;

public class DecisionInstanceConverter implements EntityInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricDecisionInstance.class);
  }

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    HistoricDecisionInstance decisionInstance = (HistoricDecisionInstance) context.getC7Entity();
    DecisionInstanceDbModel.Builder builder = (DecisionInstanceDbModel.Builder) context.getC8DbModelBuilder();

    if (builder == null) {
      throw new EntityInterceptorException("C8 DecisionInstanceDbModel.Builder is null in context");
    }

    Long decisionInstanceKey = getNextKey();
    builder.partitionId(C7_HISTORY_PARTITION_ID)
        .decisionInstanceId(String.format("%d-%s", decisionInstanceKey, decisionInstance.getId()))
        .decisionInstanceKey(decisionInstanceKey)
        .state(null) // TODO https://github.com/camunda/camunda-bpm-platform/issues/5370
        .evaluationDate(convertDate(decisionInstance.getEvaluationTime()))
        .evaluationFailure(null) // not stored in HistoricDecisionInstance
        .evaluationFailureMessage(null) // not stored in HistoricDecisionInstance
        .result(String.valueOf(decisionInstance.getCollectResultValue()))
        .processDefinitionId(decisionInstance.getProcessDefinitionKey())
        .decisionDefinitionId(decisionInstance.getDecisionDefinitionKey())
        .decisionRequirementsId(decisionInstance.getDecisionRequirementsDefinitionKey())
        .decisionType(null) // TODO https://github.com/camunda/camunda-bpm-platform/issues/5370
        .tenantId(getTenantId(decisionInstance.getTenantId()))
        .evaluatedInputs(mapInputs(decisionInstance.getId(), decisionInstance.getInputs()))
        .evaluatedOutputs(mapOutputs(decisionInstance.getId(), decisionInstance.getOutputs()))
        .historyCleanupDate(convertDate(decisionInstance.getRemovalTime()));
    // Note: decisionDefinitionKey, processDefinitionKey, decisionRequirementsKey,
    // processInstanceKey, rootDecisionDefinitionKey, flowNodeInstanceKey, and flowNodeId are set externally
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
        output.getRuleOrder())).toList();
  }
}
