/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.converter;

import static io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder;
import static io.camunda.migrator.impl.util.ConverterUtil.convertDate;
import static io.camunda.migrator.impl.util.ConverterUtil.getNextKey;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import org.camunda.bpm.engine.ActivityTypes;
import org.camunda.bpm.engine.history.HistoricActivityInstance;

public class FlowNodeConverter {

  public FlowNodeInstanceDbModel apply(HistoricActivityInstance flowNode,
                                       Long processDefinitionKey,
                                       Long processInstanceKey) {
    return new FlowNodeInstanceDbModelBuilder().flowNodeInstanceKey(getNextKey())
        .flowNodeId(flowNode.getActivityId())
        .processInstanceKey(processInstanceKey)
        .processDefinitionKey(processDefinitionKey)
        .processDefinitionId(flowNode.getProcessDefinitionKey())
        .startDate(convertDate(flowNode.getStartTime()))
        .endDate(convertDate(flowNode.getEndTime()))
        .type(convertType(flowNode.getActivityType()))
        .tenantId(flowNode.getTenantId())
        .state(null) // TODO: Doesn't exist in C7 activity instance. Inherited from process instance.
        .treePath(null) // TODO: Doesn't exist in C7 activity instance. Not yet supported by C8 RDBMS
        .incidentKey(null) // TODO Doesn't exist in C7 activity instance.
        .numSubprocessIncidents(null) // TODO: increment/decrement when incident exist in subprocess. C8 RDBMS specific.
        .build();
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
