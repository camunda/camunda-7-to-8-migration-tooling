/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_DECISION_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class responsible for migrating decision instances from Camunda 7 to Camunda 8.
 */
@Service
public class DecisionInstanceMigrator extends BaseMigrator {

  public void migrateDecisionInstances() {
    HistoryMigratorLogs.migratingDecisionInstances();
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_DECISION_INSTANCE, idKeyDbModel -> {
        HistoricDecisionInstance historicDecisionInstance = c7Client.getHistoricDecisionInstance(
            idKeyDbModel.getC7Id());
        migrateDecisionInstance(historicDecisionInstance);
      });
    } else {
      c7Client.fetchAndHandleHistoricDecisionInstances(this::migrateDecisionInstance,
          dbClient.findLatestCreateTimeByType(HISTORY_DECISION_INSTANCE));
    }
  }

  /**
   * Migrates a historic decision instance from Camunda 7 to Camunda 8.
   *
   * <p>Decision instances represent individual executions of DMN decisions. They can be triggered:
   * <ul>
   *   <li>By business rule tasks in process instances (with process context)</li>
   *   <li>As standalone decisions (without process context)</li>
   * </ul>
   *
   * <p>This method validates that all parent entities have been migrated before attempting
   * to migrate the decision instance. For standalone decisions, process-related validations
   * are skipped.
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Decision definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_DECISION_DEFINITION}</li>
   *   <li>Root decision instance not yet migrated (for nested decisions) - skipped with {@code SKIP_REASON_MISSING_PARENT_DECISION_INSTANCE}</li>
   *   <li>For process-triggered decisions only:</li>
   *   <ul>
   *     <li>Process definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *     <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *     <li>Flow node instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_FLOW_NODE}</li>
   *   </ul>
   * </ul>
   *
   * @param c7DecisionInstance the historic decision instance from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  public void migrateDecisionInstance(HistoricDecisionInstance c7DecisionInstance) {
    String c7DecisionInstanceId = c7DecisionInstance.getId();
    if (shouldMigrate(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE)) {
      HistoryMigratorLogs.migratingDecisionInstance(c7DecisionInstanceId);
      try {
        DecisionInstanceDbModel.Builder decisionInstanceDbModelBuilder = new DecisionInstanceDbModel.Builder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7DecisionInstance,
            HistoricDecisionInstance.class, decisionInstanceDbModelBuilder);

        // Check if this is a standalone decision (not triggered by a BPMN)
        boolean isStandaloneDecision = c7DecisionInstance.getProcessDefinitionKey() == null;

        String c7RootDecisionInstanceId = c7DecisionInstance.getRootDecisionInstanceId();

        if (isMigrated(c7DecisionInstance.getDecisionDefinitionId(), HISTORY_DECISION_DEFINITION)) {
          DecisionDefinitionEntity decisionDefinition = findDecisionDefinition(
              c7DecisionInstance.getDecisionDefinitionId());
          if (decisionDefinition != null) {
            if (decisionDefinition.decisionDefinitionKey() != null) {
              decisionInstanceDbModelBuilder.decisionDefinitionKey(decisionDefinition.decisionDefinitionKey());
            }
            if (decisionDefinition.decisionRequirementsKey() != null) {
              decisionInstanceDbModelBuilder.decisionRequirementsKey(decisionDefinition.decisionRequirementsKey());
            }
          }

          if (c7RootDecisionInstanceId != null && isMigrated(c7RootDecisionInstanceId, HISTORY_DECISION_INSTANCE)) {
            DecisionInstanceEntity parentDecision = findDecisionInstance(c7RootDecisionInstanceId);
            if (parentDecision != null && parentDecision.decisionDefinitionKey() != null) {
              Long parentDecisionDefinitionKey = parentDecision.decisionDefinitionKey();
              decisionInstanceDbModelBuilder.rootDecisionDefinitionKey(parentDecisionDefinitionKey);
            }
          }

          if (!isStandaloneDecision) {
            if (isMigrated(c7DecisionInstance.getProcessDefinitionId(), HISTORY_PROCESS_DEFINITION)) {
              Long processDefinitionKey = findProcessDefinitionKey(c7DecisionInstance.getProcessDefinitionId());
              if (processDefinitionKey != null) {
                decisionInstanceDbModelBuilder.processDefinitionKey(processDefinitionKey);
              }
            }

            if (isMigrated(c7DecisionInstance.getProcessInstanceId(), HISTORY_PROCESS_INSTANCE)) {
              ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(
                  c7DecisionInstance.getProcessInstanceId());
              if (processInstance != null && processInstance.processInstanceKey() != null) {
                decisionInstanceDbModelBuilder.processInstanceKey(processInstance.processInstanceKey());
              }
            }
            if (isMigrated(c7DecisionInstance.getRootProcessInstanceId(), HISTORY_PROCESS_INSTANCE)) {
              ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(
                  c7DecisionInstance.getRootProcessInstanceId());
              if (processInstance != null && processInstance.processInstanceKey() != null) {
                decisionInstanceDbModelBuilder.rootProcessInstanceKey(processInstance.processInstanceKey())
                    .historyCleanupDate(calculateHistoryCleanupDateForChild(processInstance.endDate(),
                        c7DecisionInstance.getRemovalTime()));
              }
            }
            if (isMigrated(c7DecisionInstance.getActivityInstanceId(), HISTORY_FLOW_NODE)) {
              FlowNodeInstanceDbModel flowNode = findFlowNodeInstance(c7DecisionInstance.getActivityInstanceId());
              if (flowNode != null) {
                if (flowNode.flowNodeInstanceKey() != null) {
                  decisionInstanceDbModelBuilder.flowNodeInstanceKey(flowNode.flowNodeInstanceKey());
                }
                if (flowNode.flowNodeId() != null) {
                  decisionInstanceDbModelBuilder.flowNodeId(flowNode.flowNodeId());
                }
              }
            }
          }
        }
        // Generate decision instance key and finalize model
        Long decisionInstanceKey = getNextKey();
        String decisionInstanceId = String.format("%s-%d", decisionInstanceKey, 1);
        DmnModelInstance dmnModelInstance = c7Client.getDmnModelInstance(c7DecisionInstance.getDecisionDefinitionId());

        decisionInstanceDbModelBuilder.decisionInstanceId(decisionInstanceId)
            .decisionInstanceKey(decisionInstanceKey)
            .decisionType(determineDecisionType(dmnModelInstance, c7DecisionInstance.getDecisionDefinitionKey()));

        DecisionInstanceDbModel dbModel = convertDecisionInstance(context);
        if (dbModel.decisionDefinitionKey() == null || dbModel.decisionRequirementsKey() == null) {
          markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(),
              SKIP_REASON_MISSING_DECISION_DEFINITION);
          HistoryMigratorLogs.skippingDecisionInstanceDueToMissingDecisionDefinition(c7DecisionInstanceId);
          return;
        }
        if (!isStandaloneDecision) {
          // For process-triggered decisions, re-validate that all process-related keys are set
          if (c7RootDecisionInstanceId != null && dbModel.rootDecisionDefinitionKey() == null) {
            markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(),
                SKIP_REASON_MISSING_PARENT_DECISION_INSTANCE);
            HistoryMigratorLogs.skippingDecisionInstanceDueToMissingParent(c7DecisionInstanceId);
            return;
          } else if (dbModel.processDefinitionKey() == null) {
            markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(),
                SKIP_REASON_MISSING_PROCESS_DEFINITION);
            HistoryMigratorLogs.skippingDecisionInstanceDueToMissingProcessDefinition(c7DecisionInstanceId);
            return;
          } else if (dbModel.processInstanceKey() == null) {
            markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(),
                SKIP_REASON_MISSING_PROCESS_INSTANCE);
            HistoryMigratorLogs.skippingDecisionInstanceDueToMissingProcessInstance(c7DecisionInstanceId);
            return;
          } else if (c7DecisionInstance.getRootProcessInstanceId() != null
              && dbModel.rootProcessInstanceKey() == null) {
            markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(),
                SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE);
            HistoryMigratorLogs.skippingDecisionInstanceDueToMissingParent(c7DecisionInstanceId);
            return;
          } else if (dbModel.flowNodeInstanceKey() == null) {
            markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(),
                SKIP_REASON_MISSING_FLOW_NODE);
            HistoryMigratorLogs.skippingDecisionInstanceDueToMissingFlowNodeInstance(c7DecisionInstanceId);
            return;
          }
        }
        insertDecisionInstanceWithChildren(dbModel, dbModel.rootDecisionDefinitionKey(), c7DecisionInstance);
        markMigrated(c7DecisionInstanceId, dbModel.decisionInstanceKey(), c7DecisionInstance.getEvaluationTime(),
            HISTORY_DECISION_INSTANCE);
        HistoryMigratorLogs.migratingDecisionInstanceCompleted(c7DecisionInstanceId);

      } catch (VariableInterceptorException | EntityInterceptorException e) {
        handleInterceptorException(c7DecisionInstanceId, HISTORY_DECISION_INSTANCE,
            c7DecisionInstance.getEvaluationTime(), e);
      }
    }
  }

  /**
   * Performs the actual C8 database inserts for a decision instance and its children in a transaction.
   * This method is separate to ensure all C8 writes happen atomically within a single transaction.
   *
   * @param dbModel the decision instance model to insert
   * @param parentDecisionDefinitionKey the parent decision definition key
   * @param c7DecisionInstance the C7 decision instance
   */
  @Transactional
  protected void insertDecisionInstanceWithChildren(DecisionInstanceDbModel dbModel,
                                                    Long parentDecisionDefinitionKey,
                                                    HistoricDecisionInstance c7DecisionInstance) {
    c8Client.insertDecisionInstance(dbModel);
    migrateChildDecisionInstances(parentDecisionDefinitionKey, c7DecisionInstance, dbModel);
  }

  /**
   * Migrates child decision instances for a given parent decision instance.
   * This method is called within the transaction started by insertDecisionInstanceWithChildren.
   *
   * @param parentDecisionDefinitionKey the parent decision definition key
   * @param c7DecisionInstance the parent C7 decision instance
   * @param c8ParentDecisionInstanceModel the parent C8 decision instance model
   */
  public void migrateChildDecisionInstances(Long parentDecisionDefinitionKey,
                                            HistoricDecisionInstance c7DecisionInstance,
                                            DecisionInstanceDbModel c8ParentDecisionInstanceModel) {
    List<HistoricDecisionInstance> childDecisionInstances =
        c7Client.findChildDecisionInstances(c7DecisionInstance.getId());
    for (int i = 0; i < childDecisionInstances.size(); i++) {
      HistoricDecisionInstance childDecisionInstance = childDecisionInstances.get(i);
      if (shouldMigrate(childDecisionInstance.getId(), TYPE.HISTORY_DECISION_INSTANCE)) {
        try {
          DecisionInstanceDbModel.Builder decisionInstanceDbModelBuilder = new DecisionInstanceDbModel.Builder();
          EntityConversionContext<?, ?> context = createEntityConversionContext(childDecisionInstance,
              HistoricDecisionInstance.class, decisionInstanceDbModelBuilder);
          DecisionDefinitionEntity decisionDefinition = findDecisionDefinition(
              childDecisionInstance.getDecisionDefinitionId());
          String childDecisionInstanceId =
              // +2 because +1 is used for the parent decision instance
              String.format("%s-%d", c8ParentDecisionInstanceModel.decisionInstanceKey(), i + 2);
          DmnModelInstance dmnModelInstance = c7Client.getDmnModelInstance(childDecisionInstance.getDecisionDefinitionId());
          decisionInstanceDbModelBuilder.decisionDefinitionKey(decisionDefinition.decisionDefinitionKey())
              .decisionInstanceId(childDecisionInstanceId)
              .decisionInstanceKey(c8ParentDecisionInstanceModel.decisionInstanceKey())
              .decisionRequirementsKey(decisionDefinition.decisionRequirementsKey())
              .processDefinitionKey(c8ParentDecisionInstanceModel.processDefinitionKey())
              .processInstanceKey(c8ParentDecisionInstanceModel.processInstanceKey())
              .rootDecisionDefinitionKey(parentDecisionDefinitionKey)
              .flowNodeInstanceKey(c8ParentDecisionInstanceModel.flowNodeInstanceKey())
              .flowNodeId(c8ParentDecisionInstanceModel.flowNodeId())
              .decisionType(determineDecisionType(dmnModelInstance, childDecisionInstance.getDecisionDefinitionKey()))
              .historyCleanupDate(c8ParentDecisionInstanceModel.historyCleanupDate());
          DecisionInstanceDbModel childDbModel = convertDecisionInstance(context);
          c8Client.insertDecisionInstance(childDbModel);
        } catch (EntityInterceptorException e) {
          handleInterceptorException(childDecisionInstance.getId(), HISTORY_DECISION_INSTANCE,
              childDecisionInstance.getEvaluationTime(), e);
        }
      }
    }
  }

  protected DecisionInstanceDbModel convertDecisionInstance(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    DecisionInstanceDbModel.Builder builder = (DecisionInstanceDbModel.Builder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

}

