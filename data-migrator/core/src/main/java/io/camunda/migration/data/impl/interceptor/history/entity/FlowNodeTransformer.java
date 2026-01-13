/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.*;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;

import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import java.util.Set;
import org.camunda.bpm.engine.ActivityTypes;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(3)
@Component
public class FlowNodeTransformer implements EntityInterceptor<HistoricActivityInstance, FlowNodeInstanceDbModelBuilder> {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(HistoricActivityInstance.class);
  }

  @Override
  public void execute(HistoricActivityInstance entity, FlowNodeInstanceDbModelBuilder builder) {
    builder
        .flowNodeId(entity.getActivityId())
        .processDefinitionId(prefixDefinitionId(entity.getProcessDefinitionKey()))
        .startDate(convertDate(entity.getStartTime()))
        .type(convertType(entity.getActivityType()))
        .tenantId(entity.getTenantId())
        .state(determineState(entity))
        .incidentKey(null) // TODO Doesn't exist in C7 activity instance.
        .numSubprocessIncidents(null); // TODO: increment/decrement when incident exist in subprocess. C8 RDBMS specific.

    // treePath, processInstanceKey, processDefinitionKey are set in io.camunda.migration.data.HistoryMigrator.migrateFlowNode
  }

  protected FlowNodeInstanceEntity.FlowNodeState determineState(HistoricActivityInstance entity) {
    if (entity.getEndTime() == null) {
      return FlowNodeInstanceEntity.FlowNodeState.TERMINATED; // Active nodes are auto-cancelled in C8
    }
    return entity.isCanceled() ?
        FlowNodeInstanceEntity.FlowNodeState.TERMINATED :
        FlowNodeInstanceEntity.FlowNodeState.COMPLETED;
  }

  protected FlowNodeType convertType(String activityType) {
    return switch (activityType) {
      case ActivityTypes.START_EVENT, ActivityTypes.START_EVENT_TIMER, ActivityTypes.START_EVENT_MESSAGE -> FlowNodeType.START_EVENT;
      case ActivityTypes.END_EVENT_NONE -> FlowNodeType.END_EVENT;
      case ActivityTypes.TASK_SERVICE -> FlowNodeType.SERVICE_TASK;
      case ActivityTypes.TASK_USER_TASK -> FlowNodeType.USER_TASK;
      case ActivityTypes.GATEWAY_EXCLUSIVE -> FlowNodeType.EXCLUSIVE_GATEWAY;
      case ActivityTypes.INTERMEDIATE_EVENT_TIMER, ActivityTypes.INTERMEDIATE_EVENT_SIGNAL ->
          FlowNodeType.INTERMEDIATE_CATCH_EVENT;
      case ActivityTypes.GATEWAY_PARALLEL -> FlowNodeType.PARALLEL_GATEWAY;
      case ActivityTypes.TASK_BUSINESS_RULE -> FlowNodeType.BUSINESS_RULE_TASK;
      case ActivityTypes.CALL_ACTIVITY -> FlowNodeType.CALL_ACTIVITY;
      case ActivityTypes.TASK_SCRIPT -> FlowNodeType.SCRIPT_TASK;
      case ActivityTypes.MULTI_INSTANCE_BODY -> FlowNodeType.MULTI_INSTANCE_BODY;
      case ActivityTypes.START_EVENT_ERROR -> FlowNodeType.START_EVENT;
      case ActivityTypes.END_EVENT_CANCEL -> FlowNodeType.END_EVENT;
      case ActivityTypes.END_EVENT_ERROR -> FlowNodeType.END_EVENT;
      case ActivityTypes.SUB_PROCESS -> FlowNodeType.SUB_PROCESS;
      case ActivityTypes.INTERMEDIATE_EVENT_COMPENSATION_THROW -> FlowNodeType.INTERMEDIATE_THROW_EVENT;
      case ActivityTypes.TASK_MANUAL_TASK -> FlowNodeType.MANUAL_TASK;
      case ActivityTypes.TASK_RECEIVE_TASK -> FlowNodeType.RECEIVE_TASK;
      case ActivityTypes.TRANSACTION -> FlowNodeType.SUB_PROCESS; // TODO how to handle this?
      case ActivityTypes.TASK -> FlowNodeType.TASK;
      default -> throw new IllegalArgumentException("Unknown type: " + activityType);
    };
  }

}
