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
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.List;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricDecisionInputInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricDecisionOutputInstance;
import org.camunda.bpm.engine.impl.variable.serializer.ValueFields;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(11)
@Component
public class DecisionInstanceConverter implements EntityInterceptor {

  @Autowired
  protected VariableService variableService;

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


    builder.partitionId(C7_HISTORY_PARTITION_ID)
        .state(DecisionInstanceEntity.DecisionInstanceState.EVALUATED)
        .evaluationDate(convertDate(decisionInstance.getEvaluationTime()))
        .evaluationFailure(null) // not stored in HistoricDecisionInstance
        .evaluationFailureMessage(null) // not stored in HistoricDecisionInstance
        .processDefinitionId(decisionInstance.getProcessDefinitionKey())
        .decisionDefinitionId(decisionInstance.getDecisionDefinitionKey())
        .decisionRequirementsId(decisionInstance.getDecisionRequirementsDefinitionKey())
        .decisionType(null) // TODO https://github.com/camunda/camunda-bpm-platform/issues/5370
        .result(null)
        .decisionType(null) // LITERAL_EXPRESSION, DECISION_TABLE
        .tenantId(getTenantId(decisionInstance.getTenantId()))
        .historyCleanupDate(convertDate(decisionInstance.getRemovalTime()))
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
