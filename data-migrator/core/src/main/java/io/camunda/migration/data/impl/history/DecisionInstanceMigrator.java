/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.*;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_DECISION_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.DecisionInstanceEntity;
import java.time.OffsetDateTime;
import java.util.Date;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.LiteralExpression;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating decision instances from Camunda 7 to Camunda 8.
 */
@Service
public class DecisionInstanceMigrator extends BaseMigrator<HistoricDecisionInstance, DecisionInstanceDbModel> {

  public void migrateDecisionInstances() {
    HistoryMigratorLogs.migratingDecisionInstances();
    executeMigration(
        HISTORY_DECISION_INSTANCE,
        c7Client::getHistoricDecisionInstance,
        c7Client::fetchAndHandleHistoricDecisionInstances,
        this::migrateDecisionInstance
    );
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
    var c7DecisionInstanceId = c7DecisionInstance.getId();
    if (shouldMigrate(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE)) {
      HistoryMigratorLogs.migratingDecisionInstance(c7DecisionInstanceId);

      var builder = new Builder();

      // Check if this is a standalone decision (not triggered by a BPMN)
      var isStandaloneDecision = c7DecisionInstance.getProcessDefinitionKey() == null;

      var c7RootDecisionInstanceId = c7DecisionInstance.getRootDecisionInstanceId();

      if (isMigrated(c7DecisionInstance.getDecisionDefinitionId(), HISTORY_DECISION_DEFINITION)) {
        var decisionDefinition = findDecisionDefinition(c7DecisionInstance.getDecisionDefinitionId());
        if (decisionDefinition != null) {
          if (decisionDefinition.decisionDefinitionKey() != null) {
            builder.decisionDefinitionKey(decisionDefinition.decisionDefinitionKey());
          }
          if (decisionDefinition.decisionRequirementsKey() != null) {
            builder.decisionRequirementsKey(decisionDefinition.decisionRequirementsKey());
          }
        }

        if (c7RootDecisionInstanceId != null && isMigrated(c7RootDecisionInstanceId, HISTORY_DECISION_INSTANCE)) {
          var rootDecisionInstance = findDecisionInstance(c7RootDecisionInstanceId);
          if (rootDecisionInstance != null && rootDecisionInstance.decisionDefinitionKey() != null) {
            var rootDecisionDefinitionKey = rootDecisionInstance.decisionDefinitionKey();
            builder.rootDecisionDefinitionKey(rootDecisionDefinitionKey);
          }
        }

        if (!isStandaloneDecision) {
          if (isMigrated(c7DecisionInstance.getProcessDefinitionId(), HISTORY_PROCESS_DEFINITION)) {
            var processDefinitionKey = findProcessDefinitionKey(c7DecisionInstance.getProcessDefinitionId());
            if (processDefinitionKey != null) {
              builder.processDefinitionKey(processDefinitionKey);
            }
          }

          if (isMigrated(c7DecisionInstance.getProcessInstanceId(), HISTORY_PROCESS_INSTANCE)) {
            var processInstance = findProcessInstanceByC7Id(c7DecisionInstance.getProcessInstanceId());
            if (processInstance != null && processInstance.processInstanceKey() != null) {
              OffsetDateTime endDate = processInstance.endDate();
              Date removalTime = c7DecisionInstance.getRemovalTime();
              builder
                  .processInstanceKey(processInstance.processInstanceKey());
                  //.historyCleanupDate(calculateHistoryCleanupDateForChild(endDate, removalTime));
            }
          }
          if (isMigrated(c7DecisionInstance.getRootProcessInstanceId(), HISTORY_PROCESS_INSTANCE)) {
            var rootProcessInstance = findProcessInstanceByC7Id(c7DecisionInstance.getRootProcessInstanceId());
            if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
              builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey());
            }
          }
          if (isMigrated(c7DecisionInstance.getActivityInstanceId(), HISTORY_FLOW_NODE)) {
            var flowNode = findFlowNodeInstance(c7DecisionInstance.getActivityInstanceId());
            if (flowNode != null) {
              if (flowNode.flowNodeInstanceKey() != null) {
                builder.flowNodeInstanceKey(flowNode.flowNodeInstanceKey());
              }
              if (flowNode.flowNodeId() != null) {
                builder.flowNodeId(flowNode.flowNodeId());
              }
            }
          }
        }
      }
      // Generate decision instance key and finalize model
      var decisionInstanceKey = getNextKey();
      var decisionInstanceId = String.format("%s-%d", decisionInstanceKey, 1);
      var dmnModelInstance = c7Client.getDmnModelInstance(c7DecisionInstance.getDecisionDefinitionId());

      builder.decisionInstanceId(decisionInstanceId)
          .decisionInstanceKey(decisionInstanceKey)
          .decisionType(determineDecisionType(dmnModelInstance, c7DecisionInstance.getDecisionDefinitionKey()));

      Date removalTime = c7DecisionInstance.getRemovalTime();
      builder.historyCleanupDate(calculateHistoryCleanupDateForChild(convertDate(c7DecisionInstance.getEvaluationTime()), removalTime));

      DecisionInstanceDbModel dbModel;
      try {
        dbModel = convert(c7DecisionInstance, builder);
      } catch (VariableInterceptorException | EntityInterceptorException e) {
        handleInterceptorException(c7DecisionInstanceId, HISTORY_DECISION_INSTANCE,
            c7DecisionInstance.getEvaluationTime(), e);
        return;
      }

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
              SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
          HistoryMigratorLogs.skippingDecisionInstanceDueToMissingRoot(c7DecisionInstanceId);
          return;
        } else if (dbModel.flowNodeInstanceKey() == null) {
          markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(),
              SKIP_REASON_MISSING_FLOW_NODE);
          HistoryMigratorLogs.skippingDecisionInstanceDueToMissingFlowNodeInstance(c7DecisionInstanceId);
          return;
        }
      }

      c8Client.insertDecisionInstance(dbModel);
      migrateChildDecisionInstances(dbModel.rootDecisionDefinitionKey(), c7DecisionInstance, dbModel);
      markMigrated(c7DecisionInstanceId, dbModel.decisionInstanceKey(), c7DecisionInstance.getEvaluationTime(),
          HISTORY_DECISION_INSTANCE);
      HistoryMigratorLogs.migratingDecisionInstanceCompleted(c7DecisionInstanceId);
    }
  }

  public void migrateChildDecisionInstances(Long rootDecisionDefinitionKey,
                                            HistoricDecisionInstance c7DecisionInstance,
                                            DecisionInstanceDbModel c8ParentDecisionInstanceModel) {
    var childDecisionInstances = c7Client.findChildDecisionInstances(c7DecisionInstance.getId());
    for (int i = 0; i < childDecisionInstances.size(); i++) {
      var childDecisionInstance = childDecisionInstances.get(i);
      if (shouldMigrate(childDecisionInstance.getId(), TYPE.HISTORY_DECISION_INSTANCE)) {
        var decisionDefinition = findDecisionDefinition(childDecisionInstance.getDecisionDefinitionId());
        var childDecisionInstanceId =
            // +2 because +1 is used for the parent decision instance
            String.format("%s-%d", c8ParentDecisionInstanceModel.decisionInstanceKey(), i + 2);

        var dmnModelInstance = c7Client.getDmnModelInstance(childDecisionInstance.getDecisionDefinitionId());

        var builder = new Builder();
        builder.decisionDefinitionKey(decisionDefinition.decisionDefinitionKey())
            .decisionInstanceId(childDecisionInstanceId)
            .decisionInstanceKey(c8ParentDecisionInstanceModel.decisionInstanceKey())
            .decisionRequirementsKey(decisionDefinition.decisionRequirementsKey())
            .processDefinitionKey(c8ParentDecisionInstanceModel.processDefinitionKey())
            .processInstanceKey(c8ParentDecisionInstanceModel.processInstanceKey())
            .rootDecisionDefinitionKey(rootDecisionDefinitionKey)
            .flowNodeInstanceKey(c8ParentDecisionInstanceModel.flowNodeInstanceKey())
            .flowNodeId(c8ParentDecisionInstanceModel.flowNodeId())
            .decisionType(determineDecisionType(dmnModelInstance, childDecisionInstance.getDecisionDefinitionKey()))
            .historyCleanupDate(c8ParentDecisionInstanceModel.historyCleanupDate());

        DecisionInstanceDbModel childDbModel;
        try {
          childDbModel = convert(childDecisionInstance, builder);
        } catch (VariableInterceptorException | EntityInterceptorException e) {
          handleInterceptorException(childDecisionInstance.getId(), HISTORY_DECISION_INSTANCE,
              childDecisionInstance.getEvaluationTime(), e);
          return;
        }

        c8Client.insertDecisionInstance(childDbModel);
      }
    }
  }

  protected DecisionInstanceEntity.DecisionDefinitionType determineDecisionType(DmnModelInstance dmnModelInstance,
                                                                                String decisionDefinitionId) {
    Decision decision = dmnModelInstance.getModelElementById(decisionDefinitionId);
    if (decision == null) {
      return null;
    }

    if (decision.getExpression() instanceof LiteralExpression) {
      return DecisionInstanceEntity.DecisionDefinitionType.LITERAL_EXPRESSION;
    } else {
      return DecisionInstanceEntity.DecisionDefinitionType.DECISION_TABLE;
    }
  }

}

