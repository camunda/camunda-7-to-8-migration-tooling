/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating flow node instances from Camunda 7 to Camunda 8.
 */
@Service
public class FlowNodeMigrator extends BaseMigrator<HistoricActivityInstance, FlowNodeInstanceDbModel> {

  public void migrateFlowNodes() {
    HistoryMigratorLogs.migratingHistoricFlowNodes();
    executeMigration(
        HISTORY_FLOW_NODE,
        c7Client::getHistoricActivityInstance,
        c7Client::fetchAndHandleHistoricFlowNodes,
        this::migrateFlowNode
    );
  }

  /**
   * Migrates a historic flow node instance from Camunda 7 to Camunda 8.
   *
   * <p>Flow nodes represent individual steps in a process execution (activities, gateways, events, etc.).
   * This method validates that the parent process instance has been migrated before attempting to migrate
   * the flow node instance.
   *
   * <p>The migration process:
   * <ol>
   *   <li>Checks if the flow node should be migrated based on the current mode</li>
   *   <li>Validates the parent process instance exists in C8</li>
   *   <li>Retrieves the process definition key</li>
   *   <li>Converts the C7 flow node to C8 format</li>
   *   <li>Either inserts the flow node or marks it as skipped if dependencies are missing</li>
   * </ol>
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   * </ul>
   *
   * @param c7FlowNode the historic activity instance from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  public void migrateFlowNode(HistoricActivityInstance c7FlowNode) {
    String c7ProcessInstanceId = c7FlowNode.getProcessInstanceId();

    var c7FlowNodeId = c7FlowNode.getId();
    if (shouldMigrate(c7FlowNodeId, HISTORY_FLOW_NODE)) {
      HistoryMigratorLogs.migratingHistoricFlowNode(c7FlowNodeId);
      var processInstance = findProcessInstanceByC7Id(c7ProcessInstanceId);

      var flowNodeInstanceKey = getNextKey();
      var builder = new FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder();
      builder.flowNodeInstanceKey(flowNodeInstanceKey);

      if (processInstance != null) {
        var processInstanceKey = processInstance.processInstanceKey();
        var processDefinitionKey = findProcessDefinitionKey(c7FlowNode.getProcessDefinitionId());

        builder.processInstanceKey(processInstanceKey)
            .treePath(generateTreePath(processInstanceKey, flowNodeInstanceKey))
            .processDefinitionKey(processDefinitionKey)
            .historyCleanupDate(calculateHistoryCleanupDateForChild(processInstance.endDate(), c7FlowNode.getRemovalTime()))
            .endDate(calculateCompletionDateForChild(processInstance.endDate(), c7FlowNode.getEndTime()));

        var flowNodeScopeKey = resolveFlowNodeScopeKey(c7FlowNode, c7ProcessInstanceId, processInstanceKey);
        if (flowNodeScopeKey != null) {
          builder.flowNodeScopeKey(flowNodeScopeKey);
        }
      }

      FlowNodeInstanceDbModel dbModel;
      try {
        dbModel = convert(c7FlowNode, builder);
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7FlowNodeId, HISTORY_FLOW_NODE, c7FlowNode.getStartTime(), e);
        return;
      }

      if (dbModel.processInstanceKey() == null || dbModel.processDefinitionKey() == null) {
        markSkipped(c7FlowNodeId, HISTORY_FLOW_NODE, c7FlowNode.getStartTime(), SKIP_REASON_MISSING_PROCESS_INSTANCE);
        HistoryMigratorLogs.skippingHistoricFlowNode(c7FlowNodeId);
      } else if (dbModel.flowNodeScopeKey() == null) {
        markSkipped(c7FlowNodeId, HISTORY_FLOW_NODE, c7FlowNode.getStartTime(), SKIP_REASON_MISSING_PARENT_FLOW_NODE);
        HistoryMigratorLogs.skippingHistoricFlowNode(c7FlowNodeId);
      } else {
        c8Client.insertFlowNodeInstance(dbModel);
        markMigrated(c7FlowNodeId, dbModel.flowNodeInstanceKey(), c7FlowNode.getStartTime(), HISTORY_FLOW_NODE);
        HistoryMigratorLogs.migratingHistoricFlowNodeCompleted(c7FlowNodeId);
      }
    }
  }

  /**
   * Generates a tree path for flow nodes in the format: processInstanceKey/elementInstanceKey
   *
   * @param processInstanceKey the process instance key
   * @param elementInstanceKey the element instance key (flow node)
   * @return the tree path string
   */
  public static String generateTreePath(Long processInstanceKey, Long elementInstanceKey) {
    return processInstanceKey + "/" + elementInstanceKey;
  }

  protected Long resolveFlowNodeScopeKey(HistoricActivityInstance c7FlowNode,
                                         String c7ProcessInstanceId,
                                         Long c8ProcessInstanceKey) {
    var c7ParentActivityInstanceId = c7FlowNode.getParentActivityInstanceId();

    if (c7ParentActivityInstanceId == null) {
      return null;
    } else if (c7ParentActivityInstanceId.equals(c7ProcessInstanceId)) {
      return c8ProcessInstanceKey;
    }

    return dbClient.findC8KeyByC7IdAndType(c7ParentActivityInstanceId, HISTORY_FLOW_NODE);
  }
}

