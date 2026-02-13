/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.Builder;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_DECISION_INSTANCE;
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
import static io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;

import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import java.util.Date;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.LiteralExpression;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating decision instances from Camunda 7 to Camunda 8.
 */
@Service
public class DecisionInstanceMigrator extends BaseMigrator<HistoricDecisionInstance, DecisionInstanceDbModel> {

  @Override
  public void migrateAll() {
    fetchAndRetry(
        HISTORY_DECISION_INSTANCE,
        c7Client::getHistoricDecisionInstance,
        c7Client::fetchAndHandleHistoricDecisionInstances
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
  @Override
  public Long migrateTransactionally(HistoricDecisionInstance c7DecisionInstance) {
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
          if (c7RootDecisionInstanceId == null) {
            builder.rootDecisionDefinitionKey(decisionDefinition.decisionDefinitionKey());
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
              builder.processInstanceKey(processInstance.processInstanceKey());
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
      builder.decisionInstanceKey(decisionInstanceKey);

      var decisionInstanceId = String.format("%s-%d", decisionInstanceKey, 1);
      builder.decisionInstanceId(decisionInstanceId);

      builder.decisionType(determineDecisionType(c7DecisionInstance));

      Date evaluationTime = c7DecisionInstance.getEvaluationTime();
      Date removalTime = c7DecisionInstance.getRemovalTime();
      builder.historyCleanupDate(calculateHistoryCleanupDateForChild(evaluationTime, removalTime));

      DecisionInstanceDbModel dbModel = convert(C7Entity.of(c7DecisionInstance), builder);

      migrateChildDecisionInstances(c7DecisionInstance, dbModel);

      if (dbModel.decisionDefinitionKey() == null || dbModel.decisionRequirementsKey() == null) {
        throw new EntitySkippedException(c7DecisionInstance, SKIP_REASON_MISSING_DECISION_DEFINITION);
      }

      if (c7RootDecisionInstanceId != null && dbModel.rootDecisionDefinitionKey() == null) {
        throw new EntitySkippedException(c7DecisionInstance, SKIP_REASON_MISSING_ROOT_DECISION_INSTANCE);
      }

      if (!isStandaloneDecision) {
        if (dbModel.processDefinitionKey() == null) {
          throw new EntitySkippedException(c7DecisionInstance, SKIP_REASON_MISSING_PROCESS_DEFINITION);
        }

        if (dbModel.processInstanceKey() == null) {
          throw new EntitySkippedException(c7DecisionInstance, SKIP_REASON_MISSING_PROCESS_INSTANCE);
        }

        if (dbModel.rootProcessInstanceKey() == null) {
          throw new EntitySkippedException(c7DecisionInstance, SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
        }

        if (dbModel.flowNodeInstanceKey() == null) {
          throw new EntitySkippedException(c7DecisionInstance, SKIP_REASON_MISSING_FLOW_NODE);
        }
      }

      c8Client.insertDecisionInstance(dbModel);

      return dbModel.decisionInstanceKey();
    }

    return null;
  }

  public void migrateChildDecisionInstances(HistoricDecisionInstance c7DecisionInstance, DecisionInstanceDbModel parentDbModel) {
    var childDecisionInstances = c7Client.findChildDecisionInstances(c7DecisionInstance.getId());
    for (int i = 0; i < childDecisionInstances.size(); i++) {
      var childDecisionInstance = childDecisionInstances.get(i);
      if (shouldMigrate(childDecisionInstance.getId(), TYPE.HISTORY_DECISION_INSTANCE)) {
        var builder = new Builder();

        // +2 because +1 is used for the parent decision instance
        var childDecisionInstanceId = String.format("%s-%d", parentDbModel.decisionInstanceKey(), i + 2);
        builder.decisionInstanceId(childDecisionInstanceId);

        var childDecisionDefinition = findDecisionDefinition(childDecisionInstance.getDecisionDefinitionId());
        builder.decisionDefinitionKey(childDecisionDefinition.decisionDefinitionKey())
            .decisionInstanceKey(parentDbModel.decisionInstanceKey())
            .decisionRequirementsKey(parentDbModel.decisionRequirementsKey())
            .processDefinitionKey(parentDbModel.processDefinitionKey())
            .processInstanceKey(parentDbModel.processInstanceKey())
            .rootDecisionDefinitionKey(parentDbModel.rootDecisionDefinitionKey())
            .flowNodeInstanceKey(parentDbModel.flowNodeInstanceKey())
            .flowNodeId(parentDbModel.flowNodeId())
            .decisionType(determineDecisionType(childDecisionInstance))
            .historyCleanupDate(parentDbModel.historyCleanupDate());

        DecisionInstanceDbModel dbModel = convert(C7Entity.of(childDecisionInstance), builder);
        c8Client.insertDecisionInstance(dbModel);
      }
    }
  }

  protected DecisionDefinitionType determineDecisionType(HistoricDecisionInstance c7DecisionInstance) {
    var dmnModelInstance = c7Client.getDmnModelInstance(c7DecisionInstance.getDecisionDefinitionId());
    Decision decision = dmnModelInstance.getModelElementById(c7DecisionInstance.getDecisionDefinitionKey());

    if (decision == null) {
      return null;
    }

    if (decision.getExpression() instanceof LiteralExpression) {
      return DecisionDefinitionType.LITERAL_EXPRESSION;
    } else {
      return DecisionDefinitionType.DECISION_TABLE;
    }
  }

}

