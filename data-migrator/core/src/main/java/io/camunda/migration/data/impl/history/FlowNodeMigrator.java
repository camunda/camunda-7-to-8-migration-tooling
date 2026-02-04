/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING_FLOW_NODE_MISSING_PARENT;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING_FLOW_NODE_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING_FLOW_NODE_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.Date;
import java.util.List;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating flow node instances from Camunda 7 to Camunda 8.
 */
@Service
public class FlowNodeMigrator extends BaseMigrator<HistoricActivityInstance> {

  @Override
  public void migrate() {
    HistoryMigratorLogs.migratingHistoricFlowNodes();

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_FLOW_NODE, idKeyDbModel -> {
        HistoricActivityInstance historicActivityInstance = c7Client.getHistoricActivityInstance(
            idKeyDbModel.getC7Id());
        self.migrateOne(historicActivityInstance);
      });
    } else {
      Date createTime = dbClient.findLatestCreateTimeByType(HISTORY_FLOW_NODE);
      c7Client.fetchAndHandleHistoricFlowNodes(self::migrateOne, createTime);
    }
  }

  /**
   * Migrates a historic flow node instance from Camunda 7 to Camunda 8.
   *
   * <p>Flow nodes represent individual steps in a process execution (activities, gateways, events, etc.).
   * This method validates that the parent process instance has been migrated before attempting to migrate
   * the flow node instance.
   *
   * <p>The migration process follows these steps:
   * <ol>
   *   <li>Checks if the flow node should be migrated based on the current mode</li>
   *   <li>Configures the flow node builder with keys, scope relationships, and dates</li>
   *   <li>Creates entity conversion context for interceptor processing</li>
   *   <li>Converts the C7 flow node to C8 format</li>
   *   <li>Validates dependencies and either inserts or marks as skipped</li>
   * </ol>
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *   <li>Process definition not found - skipped with {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *   <li>Parent flow node (scope) not yet migrated - skipped with {@code SKIP_REASON_MISSING_PARENT_FLOW_NODE}</li>
   *   <li>Root process instance not yet migrated (when part of a process hierarchy) - skipped with {@code SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE}</li>
   *   <li>Interceptor error during conversion - skipped with the exception message</li>
   * </ul>
   *
   * @param c7FlowNode the historic activity instance from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion (handled internally, entity marked as skipped)
   */
  @Override
  public void migrateOne(HistoricActivityInstance c7FlowNode) {
    String c7FlowNodeId = c7FlowNode.getId();
    if (shouldMigrate(c7FlowNodeId, HISTORY_FLOW_NODE)) {
      HistoryMigratorLogs.migratingHistoricFlowNode(c7FlowNodeId);

      FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder flowNodeDbModelBuilder = configureFlowNodeBuilder(
          c7FlowNode);
      try {
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7FlowNode,
            HistoricActivityInstance.class, flowNodeDbModelBuilder);

        validateDependenciesAndInsert(c7FlowNode, context, c7FlowNodeId);
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7FlowNodeId, HISTORY_FLOW_NODE, c7FlowNode.getStartTime(), e);
      }
    }
  }

  /**
   * Configures the flow node builder with keys, scope relationships, and dates.
   *
   * @param c7FlowNode the historic activity instance from Camunda 7
   * @return the configured builder
   */
  protected FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder configureFlowNodeBuilder(
      HistoricActivityInstance c7FlowNode) {
    String c7ProcessInstanceId = c7FlowNode.getProcessInstanceId();
    ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7ProcessInstanceId);

    FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder builder = new FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder();

    Long flowNodeInstanceKey = getNextKey();
    Long processDefinitionKey = findProcessDefinitionKey(c7FlowNode.getProcessDefinitionId());
    builder.flowNodeInstanceKey(flowNodeInstanceKey)
        .processDefinitionKey(processDefinitionKey);

    Long processInstanceKey;
    if (processInstance != null) {
      processInstanceKey = processInstance.processInstanceKey();

      builder.processInstanceKey(processInstanceKey)
          .treePath(generateTreePath(processInstanceKey, flowNodeInstanceKey));

      setFlowNodeDates(builder, c7FlowNode, processInstance);
      resolveFlowNodeScopeKey(builder, c7FlowNode, processInstanceKey);
    }

    String c7RootProcessInstanceId = c7FlowNode.getRootProcessInstanceId();

    if (c7RootProcessInstanceId != null) {
      ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
      if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
        builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey());
      }
    }

    return builder;
  }

  /**
   * Calculates and sets end date on the builder.
   *
   * @param builder the flow node builder
   * @param c7FlowNode the historic activity instance from Camunda 7
   * @param processInstance the parent process instance entity
   */
  protected void setFlowNodeDates(
      FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder builder,
      HistoricActivityInstance c7FlowNode,
      ProcessInstanceEntity processInstance) {

    builder
        .endDate(calculateCompletionDateForChild(processInstance.endDate(), c7FlowNode.getEndTime()));
  }

  /**
   * Resolves and sets the flow node scope key on the builder.
   *
   * @param builder the flow node builder
   * @param c7FlowNode the historic activity instance from Camunda 7
   * @param c8ProcessInstanceKey the C8 process instance key
   */
  protected void resolveFlowNodeScopeKey(
      FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder builder,
      HistoricActivityInstance c7FlowNode,
      Long c8ProcessInstanceKey) {

    String c7ProcessInstanceId = c7FlowNode.getProcessInstanceId();
    String c7ParentActivityInstanceId = c7FlowNode.getParentActivityInstanceId();

    if (c7ParentActivityInstanceId == null) {
      return;
    }

    Long flowNodeScopeKey;
    if (c7ParentActivityInstanceId.equals(c7ProcessInstanceId)) {
      flowNodeScopeKey = c8ProcessInstanceKey;
    } else {
      flowNodeScopeKey = dbClient.findC8KeyByC7IdAndType(c7ParentActivityInstanceId, HISTORY_FLOW_NODE);
    }

    if (flowNodeScopeKey != null) {
      builder.flowNodeScopeKey(flowNodeScopeKey);
    }
  }

  /**
   * Validates dependencies and inserts the flow node or marks it as skipped.
   *
   * @param c7FlowNode the historic activity instance from Camunda 7
   * @param context the entity conversion context
   * @param c7FlowNodeId the C7 flow node ID
   */
  protected void validateDependenciesAndInsert(
      HistoricActivityInstance c7FlowNode,
      EntityConversionContext<?, ?> context,
      String c7FlowNodeId) {

    FlowNodeInstanceDbModel dbModel = convertFlowNode(context);
    if (dbModel.processDefinitionKey() == null) {
      markSkipped(c7FlowNodeId, HISTORY_FLOW_NODE, c7FlowNode.getStartTime(), SKIP_REASON_MISSING_PROCESS_DEFINITION);
      HistoryMigratorLogs.skippingHistoricFlowNode(SKIPPING_FLOW_NODE_MISSING_PROCESS_DEFINITION, c7FlowNodeId);
    } else if (dbModel.processInstanceKey() == null) {
      markSkipped(c7FlowNodeId, HISTORY_FLOW_NODE, c7FlowNode.getStartTime(), SKIP_REASON_MISSING_PROCESS_INSTANCE);
      HistoryMigratorLogs.skippingHistoricFlowNode(SKIPPING_FLOW_NODE_MISSING_PROCESS_INSTANCE, c7FlowNodeId);
    } else if (dbModel.flowNodeScopeKey() == null) {
      markSkipped(c7FlowNodeId, HISTORY_FLOW_NODE, c7FlowNode.getStartTime(), SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE);
      HistoryMigratorLogs.skippingHistoricFlowNode(SKIPPING_FLOW_NODE_MISSING_PARENT, c7FlowNodeId);
    } else if (c7FlowNode.getRootProcessInstanceId() != null && dbModel.rootProcessInstanceKey() == null) {
      markSkipped(c7FlowNodeId, HISTORY_FLOW_NODE, c7FlowNode.getStartTime(), SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
      HistoryMigratorLogs.skippingHistoricFlowNodeMissingRootProcess(c7FlowNodeId);
    } else {
      insertFlowNodeInstance(c7FlowNode, dbModel, c7FlowNodeId);
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

  protected void insertFlowNodeInstance(HistoricActivityInstance c7FlowNode,
                                        FlowNodeInstanceDbModel dbModel,
                                        String c7FlowNodeId) {
    c8Client.insertFlowNodeInstance(dbModel);
    markMigrated(c7FlowNodeId, dbModel.flowNodeInstanceKey(), c7FlowNode.getStartTime(), HISTORY_FLOW_NODE);
    HistoryMigratorLogs.migratingHistoricFlowNodeCompleted(c7FlowNodeId);
  }

  protected FlowNodeInstanceDbModel convertFlowNode(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder builder = (FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }
}

