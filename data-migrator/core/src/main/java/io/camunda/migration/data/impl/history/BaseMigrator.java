/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.MigratorMode.MIGRATE;
import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.config.property.history.CleanupProperties.DEFAULT_TTL;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;

import io.camunda.db.rdbms.read.domain.DecisionDefinitionDbQuery;
import io.camunda.db.rdbms.read.domain.DecisionInstanceDbQuery;
import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessInstanceDbQuery;
import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.exception.MigratorException;
import io.camunda.migration.data.impl.EntityConversionService;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.impl.clients.C8Client;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.LiteralExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for all history entity migrators.
 * Contains common utility methods for migration operations.
 */
public abstract class BaseMigrator<T> {

  @Autowired
  protected DbClient dbClient;

  @Autowired
  protected C7Client c7Client;

  @Autowired
  protected C8Client c8Client;

  @Autowired
  protected MigratorProperties migratorProperties;

  @Autowired
  protected EntityConversionService entityConversionService;

  @Autowired
  protected ProcessEngine processEngine;

  protected MigratorMode mode = MIGRATE;

  @Autowired
  @Lazy
  protected BaseMigrator<T> self;

  protected Long findProcessDefinitionKey(String processDefinitionId) {
    Long key = dbClient.findC8KeyByC7IdAndType(processDefinitionId,
        IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);
    if (key == null) {
      return null;
    }

    List<ProcessDefinitionEntity> processDefinitions = c8Client.searchProcessDefinitions(
        ProcessDefinitionDbQuery.of(b -> b.filter(value -> value.processDefinitionKeys(key))));

    if (!processDefinitions.isEmpty()) {
      return processDefinitions.getFirst().processDefinitionKey();
    } else {
      return null;
    }
  }

  protected ProcessInstanceEntity findProcessInstanceByC7Id(String processInstanceId) {
    if (processInstanceId == null)
      return null;

    Long c8Key = dbClient.findC8KeyByC7IdAndType(processInstanceId, HISTORY_PROCESS_INSTANCE);
    if (c8Key == null) {
      return null;
    }

    return c8Client.findProcessInstance(c8Key);
  }

  protected DecisionInstanceEntity findDecisionInstance(String decisionInstanceId) {
    if (decisionInstanceId == null)
      return null;

    Long key = dbClient.findC8KeyByC7IdAndType(decisionInstanceId,
        IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE);
    if (key == null) {
      return null;
    }

    return c8Client.searchDecisionInstances(
            DecisionInstanceDbQuery.of(b -> b.filter(value -> value.decisionInstanceKeys(key))))
        .stream()
        .findFirst()
        .orElse(null);
  }

  protected DecisionDefinitionEntity findDecisionDefinition(String decisionDefinitionId) {
    Long key = dbClient.findC8KeyByC7IdAndType(decisionDefinitionId,
        IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION);
    if (key == null) {
      return null;
    }

    return c8Client.searchDecisionDefinitions(
            DecisionDefinitionDbQuery.of(b -> b.filter(value -> value.decisionDefinitionKeys(key))))
        .stream()
        .findFirst()
        .orElse(null);
  }

  protected Long findFlowNodeInstanceKey(String activityId, String processInstanceId) {
    Long key = dbClient.findC8KeyByC7IdAndType(processInstanceId, HISTORY_PROCESS_INSTANCE);
    if (key == null) {
      return null;
    }

    List<FlowNodeInstanceDbModel> flowNodes = c8Client.searchFlowNodeInstances(FlowNodeInstanceDbQuery.of(
        b -> b.filter(FlowNodeInstanceFilter.of(f -> f.flowNodeIds(activityId).flowNodeInstanceKeys(key)))));

    if (!flowNodes.isEmpty()) {
      return flowNodes.getFirst().flowNodeInstanceKey();
    } else {
      return null;
    }
  }

  protected Long findFlowNodeInstanceKey(String activityInstanceId) {
    return Optional.ofNullable(findFlowNodeInstance(activityInstanceId))
        .map(FlowNodeInstanceDbModel::flowNodeInstanceKey)
        .orElse(null);
  }

  protected FlowNodeInstanceDbModel findFlowNodeInstance(String activityInstanceId) {
    Long key = dbClient.findC8KeyByC7IdAndType(activityInstanceId, HISTORY_FLOW_NODE);
    if (key == null) {
      return null;
    }

    return c8Client.searchFlowNodeInstances(FlowNodeInstanceDbQuery.of(b -> b.filter(f -> f.flowNodeInstanceKeys(key))))
        .stream()
        .findFirst()
        .orElse(null);
  }

  protected Long findScopeKey(String instanceId) {
    Long key = findFlowNodeInstanceKey(instanceId);
    if (key != null) {
      return key;
    }

    Long processInstanceKey = dbClient.findC8KeyByC7IdAndType(instanceId, HISTORY_PROCESS_INSTANCE);
    if (processInstanceKey == null) {
      return null;
    }

    List<ProcessInstanceEntity> processInstances = c8Client.searchProcessInstances(
        ProcessInstanceDbQuery.of(b -> b.filter(value -> value.processInstanceKeys(processInstanceKey))));
    return processInstances.isEmpty() ? null : processInstanceKey;
  }

  protected boolean isMigrated(String id, TYPE type) {
    return dbClient.checkHasC8KeyByC7IdAndType(id, type);
  }

  protected boolean shouldMigrate(String id, TYPE type) {
    if (mode == RETRY_SKIPPED) {
      return !dbClient.checkHasC8KeyByC7IdAndType(id, type);
    }
    return !dbClient.checkExistsByC7IdAndType(id, type);
  }

  public void markMigrated(String c7Id, Long c8Key, Date createTime, TYPE type) {
    saveRecord(c7Id, c8Key, type, createTime, null);
  }

  protected void markSkipped(String c7Id, TYPE type, Date createTime, String skipReason) {
    saveRecord(c7Id, null, type, createTime, skipReason);
  }

  protected void saveRecord(String c7Id, Long c8Key, TYPE type, Date createTime, String skipReason) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateC8KeyByC7IdAndType(c7Id, c8Key, type);
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(c7Id, c8Key, createTime, type, skipReason);
    }
  }

  protected <T> EntityConversionContext<?, ?> createEntityConversionContext(T c7Entity,
                                                                            Class<T> c7EntityClass,
                                                                            Object dbModelBuilder) {
    EntityConversionContext<?, ?> context = new EntityConversionContext<>(c7Entity, c7EntityClass, dbModelBuilder,
        processEngine);
    entityConversionService.prepareParentProperties(context);
    return context;
  }

  protected void handleInterceptorException(String c7Id, TYPE type, Date time, MigratorException e) {
    HistoryMigratorLogs.skippingEntityDueToInterceptorError(type, c7Id, e.getMessage());
    HistoryMigratorLogs.stacktrace(e);
    markSkipped(c7Id, type, time, e.getMessage());
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

  public void setMode(MigratorMode mode) {
    this.mode = mode;
  }


  protected DecisionRequirementsDbModel convertDecisionRequirements(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    DecisionRequirementsDbModel.Builder builder =
        (DecisionRequirementsDbModel.Builder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  /**
   * Calculates the history cleanup date for an entity based on its state and endDate.
   * Only calculates a new cleanup date when C7 removalTime is null.
   * For entities with existing removalTime, uses that value directly.
   *
   * @param c7State the C7 state of the entity
   * @param c7EndDate the C7 end date (null if still active)
   * @param c7RemovalTime the C7 removal time
   * @return the calculated history cleanup date
   */
  protected OffsetDateTime calculateHistoryCleanupDate(String c7State, Date c7EndDate, Date c7RemovalTime) {
    // If C7 already has a removalTime, use it directly
    if (c7RemovalTime != null) {
      return convertDate(c7RemovalTime);
    }

    // Only calculate cleanup date when removalTime is null
    // For active entities in C7, calculate cleanup date based on now + TTL
    if (c7EndDate == null) {
      Period ttl = getAutoCancelTtl();
      // If TTL is null or zero, auto-cancel is disabled - return null for cleanup date
      if (ttl == null || ttl.isZero()) {
        return null;
      }
      OffsetDateTime now = convertDate(ClockUtil.now());
      return now.plus(ttl);
    }

    // No removalTime and not active - return null
    return null;
  }

  /**
   * Calculates the history cleanup date for child entities (flow nodes, variables, etc.)
   * based on the parent ProcessInstance's endDate.
   * Only calculates when the parent ProcessInstance has no removalTime.
   *
   * @param processInstanceEndDate the endDate from the parent ProcessInstanceEntity
   * @param c7RemovalTime the C7 removal time of the child entity
   * @return the calculated history cleanup date, or null if auto-cancel TTL is disabled
   */
  protected OffsetDateTime calculateHistoryCleanupDateForChild(OffsetDateTime processInstanceEndDate, Date c7RemovalTime) {
    // If C7 already has a removalTime, use it directly
    if (c7RemovalTime != null) {
      return convertDate(c7RemovalTime);
    }

    Period ttl = getAutoCancelTtl();
    // If TTL is null or zero, auto-cancel is disabled - return null for cleanup date
    if (ttl == null || ttl.isZero()) {
      return null;
    }
    return processInstanceEndDate.plus(ttl);
  }

  /**
   * Calculates the completion/end date for child entities.
   * For entities that don't have an end date, inherit the parent process instance's end date.
   *
   * @param processInstanceEndDate the parent process instance's end date
   * @param c7EndDate the C7 entity's end date
   * @return the completion date to use (processInstanceEndDate if null, original if set)
   */
  protected OffsetDateTime calculateCompletionDateForChild(OffsetDateTime processInstanceEndDate, Date c7EndDate) {
    if (c7EndDate == null) {
      // Inherit the parent process instance's end date
      return processInstanceEndDate;
    }
    return convertDate(c7EndDate);
  }

  /**
   * Determines if the entity should have endDate set to now when converting to C8.
   *
   * @param c7State the C7 state
   * @param c7EndDate the C7 end date
   * @return the endDate to use (now if active, original if completed)
   */
  protected OffsetDateTime calculateEndDate(String c7State, Date c7EndDate) {
    if (c7EndDate == null) {
      return convertDate(ClockUtil.now());
    }
    return convertDate(c7EndDate);
  }

  /**
   * Gets the configured auto-cancel TTL period.
   *
   * @return the configured TTL, or null if cleanup is disabled
   */
  protected Period getAutoCancelTtl() {
    // If history configuration doesn't exist, use default
    if (migratorProperties.getHistory() == null) {
      return DEFAULT_TTL;
    }

    // If auto-cancel configuration doesn't exist, use default
    if (migratorProperties.getHistory().getAutoCancel() == null) {
      return DEFAULT_TTL;
    }

    // If cleanup configuration doesn't exist, use default
    if (migratorProperties.getHistory().getAutoCancel().getCleanup() == null) {
      return DEFAULT_TTL;
    }

    // Check if cleanup is explicitly disabled
    if (!migratorProperties.getHistory().getAutoCancel().getCleanup().isEnabled()) {
      return null;
    }

    // Return configured TTL (will have default of 180 days if not set)
    return migratorProperties.getHistory().getAutoCancel().getCleanup().getTtl();
  }

  abstract void migrate();

  @Transactional("c8TransactionManager")
  abstract void migrateOne(T entity);

}

