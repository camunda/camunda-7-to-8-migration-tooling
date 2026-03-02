/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import io.camunda.search.entities.ProcessInstanceEntity;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating flow node instances from Camunda 7 to Camunda 8.
 */
@Service
public class FlowNodeMigrator extends BaseMigrator<HistoricActivityInstance, FlowNodeInstanceDbModel> {

  @Override
  public void migrateAll() {
    fetchMigrateOrRetry(
        HISTORY_FLOW_NODE,
        c7Client::getHistoricActivityInstance,
        c7Client::fetchAndHandleHistoricFlowNodes
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
   *   <li>Parent flow node (scope) not yet migrated - skipped with {@code SKIP_REASON_MISSING_PARENT_FLOW_NODE}</li>
   *   <li>Root process instance not yet migrated (when part of a process hierarchy) - skipped with {@code SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE}</li>
   *   <li>Interceptor error during conversion - skipped with the exception message</li>
   * </ul>
   *
   * @param c7FlowNode the historic activity instance from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion (handled internally, entity marked as skipped)
   */
  @Override
  public Long migrateTransactionally(HistoricActivityInstance c7FlowNode) {
    var c7FlowNodeId = c7FlowNode.getId();
    if (shouldMigrate(c7FlowNodeId, HISTORY_FLOW_NODE)) {
      HistoryMigratorLogs.migratingHistoricFlowNode(c7FlowNodeId);

      var flowNodeInstanceKey = getNextKey();
      var builder = new FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder();
      builder.flowNodeInstanceKey(flowNodeInstanceKey);

      String c7ProcessInstanceId = c7FlowNode.getProcessInstanceId();
      var processInstance = findProcessInstanceByC7Id(c7ProcessInstanceId);
      if (processInstance != null) {
        var processInstanceKey = processInstance.processInstanceKey();
        var processDefinitionKey = findProcessDefinitionKey(c7FlowNode.getProcessDefinitionId());

        builder.processInstanceKey(processInstanceKey)
            .treePath(generateTreePath(processInstanceKey, flowNodeInstanceKey))
            .processDefinitionKey(processDefinitionKey)
            .endDate(calculateCompletionDateForChild(processInstance.endDate(), c7FlowNode.getEndTime()));

        String c7RootProcessInstanceId = c7FlowNode.getRootProcessInstanceId();
        if (c7RootProcessInstanceId != null && isMigrated(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
          ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
          if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
            builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey());
          }
        }

        Long flowNodeScopeKey = resolveFlowNodeScopeKey(c7FlowNode, processInstanceKey);
        if (flowNodeScopeKey != null) {
          builder.flowNodeScopeKey(flowNodeScopeKey);
        }
      }

      FlowNodeInstanceDbModel dbModel = convert(C7Entity.of(c7FlowNode), builder);

      if (dbModel.processInstanceKey() == null) {
        throw new EntitySkippedException(c7FlowNode, SKIP_REASON_MISSING_PROCESS_INSTANCE);
      }

      if (dbModel.processDefinitionKey() == null) {
        throw new EntitySkippedException(c7FlowNode, SKIP_REASON_MISSING_PROCESS_DEFINITION);
      }

      if (dbModel.flowNodeScopeKey() == null) {
        throw new EntitySkippedException(c7FlowNode, SKIP_REASON_MISSING_PARENT_FLOW_NODE);
      }

      if (dbModel.rootProcessInstanceKey() == null) {
        throw new EntitySkippedException(c7FlowNode, SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
      }

      c8Client.insertFlowNodeInstance(dbModel);

      return dbModel.flowNodeInstanceKey();
    }

    return null;
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
                                         Long c8ProcessInstanceKey) {
    var c7ParentActivityInstanceId = c7FlowNode.getParentActivityInstanceId();

    if (c7ParentActivityInstanceId == null) {
      return null;
    } else if (c7ParentActivityInstanceId.equals(c7FlowNode.getProcessInstanceId())) {
      return c8ProcessInstanceKey;
    }

    return dbClient.findC8KeyByC7IdAndType(c7ParentActivityInstanceId, HISTORY_FLOW_NODE);
  }
}

