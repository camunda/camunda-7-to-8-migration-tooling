/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.*;
import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.migration.data.impl.VariableService;
import io.camunda.migration.data.exception.EntityInterceptorException;
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
public class DecisionInstanceTransformer implements EntityInterceptor<HistoricDecisionInstance, Builder> {

  @Autowired
  protected VariableService variableService;

  @Autowired
  protected ObjectMapper objectMapper;

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricDecisionInstance.class);
  }

  @Override
  public void execute(HistoricDecisionInstance entity, Builder builder) {
    var evaluatedOutputs = mapOutputs(entity.getId(), entity.getOutputs());

    String resultJsonString;
    var collectResultValue = entity.getCollectResultValue();
    if (collectResultValue != null) {
      resultJsonString = constructResultFromCollectValue(collectResultValue);
    } else {
      resultJsonString = constructResultJsonFromOutputs(evaluatedOutputs);
    }

    builder.partitionId(C7_HISTORY_PARTITION_ID)
        .state(DecisionInstanceEntity.DecisionInstanceState.EVALUATED)
        .evaluationDate(convertDate(entity.getEvaluationTime()))
        .evaluationFailure(null) // not stored in HistoricDecisionInstance
        .evaluationFailureMessage(null) // not stored in HistoricDecisionInstance
        .processDefinitionId(prefixDefinitionId(entity.getProcessDefinitionKey()))
        .decisionDefinitionId(prefixDefinitionId(entity.getDecisionDefinitionKey()))
        .decisionRequirementsId(prefixDefinitionId(entity.getDecisionRequirementsDefinitionKey()))
        .result(resultJsonString)
        .tenantId(getTenantId(entity.getTenantId()))
        .evaluatedInputs(mapInputs(entity.getId(), entity.getInputs()))
        .evaluatedOutputs(evaluatedOutputs);
    // Note: decisionDefinitionKey, processDefinitionKey, decisionRequirementsKey, decisionType
    // processInstanceKey, rootDecisionDefinitionKey, flowNodeInstanceKey, and flowNodeId are set externally
  }

  protected List<EvaluatedInput> mapInputs(String decisionInstanceId,
                                           List<HistoricDecisionInputInstance> c7Inputs) {
    return c7Inputs.stream().map(input -> new EvaluatedInput(decisionInstanceId,
        input.getId(),
        input.getClauseName(),
        variableService.convertValue((ValueFields) input)
    )).toList();
  }

  protected List<EvaluatedOutput> mapOutputs(String decisionInstanceId,
                                             List<HistoricDecisionOutputInstance> c7Outputs) {
    return c7Outputs.stream().map(output -> new EvaluatedOutput(decisionInstanceId,
        output.getId(),
        output.getClauseName(),
        variableService.convertValue((ValueFields) output),
        output.getRuleId(),
        output.getRuleOrder() != null ? output.getRuleOrder() : 1)).toList();
  }

  protected String constructResultFromCollectValue(Double collectResultValue) {
    try {
      if (collectResultValue % 1 == 0) {
        // the result is a whole number, serialize as Long
        return objectMapper.writeValueAsString(collectResultValue.longValue());
      } else {
        return objectMapper.writeValueAsString(collectResultValue);
      }
    } catch (JsonProcessingException e) {
      throw new EntityInterceptorException("Failed to construct result JSON from collect value", e);
    }
  }

  protected String constructResultJsonFromOutputs(List<EvaluatedOutput> outputValues) {
    if (outputValues == null || outputValues.isEmpty()) {
      return null;
    }

    try {
      List<Object> parsedValues = new java.util.ArrayList<>();
      for (EvaluatedOutput output : outputValues) {
        String jsonValue = output.value();
        Object parsedValue = objectMapper.readValue(jsonValue, Object.class);
        parsedValues.add(parsedValue);
      }

      if (parsedValues.size() == 1) {
        return objectMapper.writeValueAsString(parsedValues.get(0));
      }

      return objectMapper.writeValueAsString(parsedValues);
    } catch (JsonProcessingException e) {
      throw new EntityInterceptorException("Failed to construct result JSON from outputs", e);
    }
  }
}
