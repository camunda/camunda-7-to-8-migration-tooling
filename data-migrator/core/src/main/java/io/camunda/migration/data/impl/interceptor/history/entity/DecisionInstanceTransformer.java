/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.*;
import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_EXPORTER_PARTITION_ID;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.VariableService;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.search.entities.DecisionInstanceEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricDecisionInputInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricDecisionOutputInstance;
import org.camunda.bpm.engine.impl.variable.serializer.ValueFields;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Rule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(11)
@Component
public class DecisionInstanceTransformer implements EntityInterceptor<HistoricDecisionInstance, Builder> {

  @Autowired
  protected C7Client c7Client;

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
    var evaluatedOutputs = mapOutputs(entity, entity.getOutputs());

    String resultJsonString;
    var collectResultValue = entity.getCollectResultValue();
    if (collectResultValue != null) {
      resultJsonString = constructResultFromCollectValue(collectResultValue);
    } else {
      resultJsonString = constructResultJsonFromOutputs(evaluatedOutputs);
    }

    builder.partitionId(C7_HISTORY_EXPORTER_PARTITION_ID)
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


  protected List<EvaluatedOutput> mapOutputs(HistoricDecisionInstance decisionInstance,
                                             List<HistoricDecisionOutputInstance> c7Outputs) {
    Map<String, Integer> ruleIndexById = buildRuleIndexMap(decisionInstance);

    return c7Outputs.stream()
        .map(output -> new EvaluatedOutput(
            decisionInstance.getId(),
            output.getId(),
            output.getClauseName(),
            variableService.convertValue((ValueFields) output),
            output.getRuleId(),
            resolveRuleIndex(output, ruleIndexById)))
        .toList();
  }

  /**
   * Builds a map of DMN rule ID to 1-based row index for the given decision definition.
   * Falls back to an empty map when the DMN cannot be resolved or is not a decision table.
   *
   * @param entity the C7 historic decision instance whose definition should be looked up
   * @return a map of rule ID to 1-based row index; empty on any resolution failure
   */
  protected Map<String, Integer> buildRuleIndexMap(HistoricDecisionInstance entity) {
    try {
      DmnModelInstance dmn = c7Client.getDmnModelInstance(entity.getDecisionDefinitionId());
      Decision decision = dmn.getModelElementById(entity.getDecisionDefinitionKey());
      if (decision == null || !(decision.getExpression() instanceof DecisionTable decisionTable)) {
        return Collections.emptyMap();
      }
      Map<String, Integer> ruleIndexMap = new HashMap<>();
      int index = 1;
      for (Rule rule : decisionTable.getRules()) {
        String ruleId = rule.getId();
        if (ruleId != null && !ruleId.isEmpty()) {
          ruleIndexMap.put(ruleId, index);
        }
        index++;
      }
      return Collections.unmodifiableMap(ruleIndexMap);
    } catch (Exception e) {
      HistoryMigratorLogs.logCouldNotBuildDecisionRuleIndex(entity.getDecisionDefinitionId());
      return Collections.emptyMap();
    }
  }

  /**
   * Resolves the 1-based row index for an output from the DMN rule ID map.
   * Falls back to 1 if the rule ID is absent or not found in the map.
   *
   * @param output        the C7 decision output instance whose rule ID is to be looked up
   * @param ruleIndexById map of DMN rule ID to 1-based row index, built from the DMN model
   * @return the resolved 1-based row index, or 1 as a fallback
   */
  protected int resolveRuleIndex(HistoricDecisionOutputInstance output,
                                 Map<String, Integer> ruleIndexById) {
    String ruleId = output.getRuleId();
    if (ruleId != null && !ruleId.isEmpty()) {
      return ruleIndexById.getOrDefault(ruleId, 1);
    }
    return 1;
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
      List<Object> parsedValues = new ArrayList<>();
      for (EvaluatedOutput output : outputValues) {
        String jsonValue = output.value();
        Object parsedValue = objectMapper.readValue(jsonValue, Object.class);
        parsedValues.add(parsedValue);
      }

      if (parsedValues.size() == 1) {
        return objectMapper.writeValueAsString(parsedValues.getFirst());
      }

      return objectMapper.writeValueAsString(parsedValues);
    } catch (JsonProcessingException e) {
      throw new EntityInterceptorException("Failed to construct result JSON from outputs", e);
    }
  }
}
