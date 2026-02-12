/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.MigratorMode.MIGRATE;
import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.config.property.history.CleanupProperties.DEFAULT_TTL;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigrating;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logSkipping;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigrationCompleted;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.skippingEntityDueToInterceptorError;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;

import io.camunda.db.rdbms.read.domain.DecisionDefinitionDbQuery;
import io.camunda.db.rdbms.read.domain.DecisionInstanceDbQuery;
import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessInstanceDbQuery;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.impl.EntityConversionService;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.impl.clients.C8Client;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.repository.ResourceDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for all history entity migrators.
 * Contains common utility methods for migration operations.
 */
public abstract class BaseMigrator<C7, C8> {

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
  protected BaseMigrator<C7, C8> self;

  protected Long findProcessDefinitionKey(String processDefinitionId) {
    Long key = dbClient.findC8KeyByC7IdAndType(processDefinitionId, IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);
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
    Long key = dbClient.findC8KeyByC7IdAndType(decisionDefinitionId, IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION);
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
    Long processInstanceKey = dbClient.findC8KeyByC7IdAndType(processInstanceId, HISTORY_PROCESS_INSTANCE);
    if (processInstanceKey == null) {
      return null;
    }

    List<FlowNodeInstanceDbModel> flowNodes = c8Client.searchFlowNodeInstances(
        FlowNodeInstanceDbQuery.of(builder -> builder.filter(
            FlowNodeInstanceFilter.of(filter -> filter.flowNodeIds(activityId).processInstanceKeys(processInstanceKey))
        ))
    );

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

  protected void markMigrated(C7Entity<?> c7Entity, String c8Key) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateC8KeyByC7IdAndType(c7Entity.getId(), c8Key, c7Entity.getType());
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(c7Entity.getId(), c8Key, c7Entity.getCreationTime(), c7Entity.getType(), null);
    }
  }

  protected void markMigrated(C7Entity<?> c7Entity, Long c8Key) {
    saveRecord(c7Entity.getId(), c8Key, c7Entity.getType(), c7Entity.getCreationTime(), null);
  }

  @SuppressWarnings("unchecked")
  protected void markSkipped(EntitySkippedException e) {
    C7Entity<C7> c7Entity = (C7Entity<C7>) e.getC7Entity();
    saveRecord(c7Entity.getId(), null, c7Entity.getType(), c7Entity.getCreationTime(), e.getMessage());
    logSkipping(e);
  }

  protected void saveRecord(String c7Id, Long c8Key, TYPE type, Date createTime, String skipReason) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateC8KeyByC7IdAndType(c7Id, c8Key, type);
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(c7Id, c8Key, createTime, type, skipReason);
    }
  }

  public void setMode(MigratorMode mode) {
    this.mode = mode;
  }

  /**
   * Centralized method to execute migration logic with standard retry/fetch behavior.
   * This method handles the common pattern of:
   * <ul>
   *   <li>Fetching skipped entities in RETRY_SKIPPED mode</li>
   *   <li>Fetching entities from C7 in normal mode (starting from last migrated timestamp)</li>
   *   <li>Delegating to the specific migration handler</li>
   * </ul>
   *
   * @param type the entity type being migrated
   * @param c7Fetcher fetches a specific entity from C7 by its ID (for retry mode)
   * @param c7BatchHandler fetches and processes entities from C7 in batches (for normal mode)
   */
  protected void fetchAndRetry(TYPE type, Function<String, C7> c7Fetcher, BiConsumer<Consumer<C7>, Date> c7BatchHandler) {
    logMigrating(type);

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(type, idKeyDbModel -> {
        C7 c7Entity = c7Fetcher.apply(idKeyDbModel.getC7Id());
        self.migrateEntity(c7Entity);
      });
    } else {
      c7BatchHandler.accept(self::migrateEntity, dbClient.findLatestCreateTimeByType(type));
    }
  }

  @SuppressWarnings("unchecked")
  @Transactional("c8TransactionManager")
  protected void migrateEntity(C7 entity) {
    C7Entity<C7> c7Entity = (C7Entity<C7>) getC7Entity(entity);
    String c8Key = tryMigrate(c7Entity);
    if (c8Key != null) {
      markMigrated(c7Entity, c8Key);
      logMigrationCompleted(c7Entity);
    }
  }

  protected C7Entity<?> getC7Entity(C7 entity) {
    if (entity instanceof ResourceDefinition) {
      Date deploymentTime = c7Client.getDefinitionDeploymentTime(((ResourceDefinition) entity).getDeploymentId());
      return C7Entity.of(entity, deploymentTime);

    } else {
      return C7Entity.of(entity);

    }
  }

  protected String tryMigrate(C7Entity<C7> c7Entity) {
    Object c8Key = null;
    try {
      c8Key = migrateTransactionally(c7Entity.unwrap());
    } catch (EntitySkippedException e) {
      markSkipped(e);
    }

    return c8Key == null ? null : c8Key.toString();
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
  protected OffsetDateTime calculateHistoryCleanupDateForChild(Date processInstanceEndDate, Date c7RemovalTime) {
    // If C7 already has a removalTime, use it directly
    if (c7RemovalTime != null) {
      return convertDate(c7RemovalTime);
    }

    Period ttl = getAutoCancelTtl();
    // If TTL is null or zero, auto-cancel is disabled - return null for cleanup date
    if (ttl == null || ttl.isZero()) {
      return null;
    }
    return convertDate(processInstanceEndDate).plus(ttl);
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
  /**
   * Calculates the history cleanup date for an entity.
   * Only calculates a new cleanup date when C7 removalTime is null.
   * For entities with existing removalTime, uses that value directly.
   *
   * @param endTime the C7 or auto-canceled end time
   * @param c7RemovalTime the C7 removal time
   * @return the calculated history cleanup date
   */
  protected OffsetDateTime calculateHistoryCleanupDate(OffsetDateTime endTime, Date c7RemovalTime) {
    if (c7RemovalTime != null) {
      return convertDate(c7RemovalTime);
    }

    Period ttl = getAutoCancelTtl();
    if (ttl == null || ttl.isZero()) {
      return null;
    }
    return endTime.plus(ttl);
  }

  /**
   * Determines if the entity should have endDate set to now when converting to C8.
   *
   * @param c7EndDate the C7 end date
   * @return the endDate to use (now if active, original if completed)
   */
  protected OffsetDateTime calculateEndDate(Date c7EndDate) {
    if (c7EndDate == null) {
      return convertDate(ClockUtil.now());
    }
    return convertDate(c7EndDate);
  }

  /**
   * Migrates all entities of type T from Camunda 7 to Camunda 8.
   * <p>
   * This method is responsible for:
   * <ul>
   *   <li>Fetching all entities from Camunda 7</li>
   *   <li>Converting each entity to Camunda 8 format</li>
   *   <li>Persisting the converted entities to Camunda 8</li>
   *   <li>Tracking migration status in the migration database</li>
   * </ul>
   * </p>
   * <p>
   * The migration mode (MIGRATE or RETRY_SKIPPED) affects which entities are processed.
   * </p>
   */
  abstract void migrateAll();

  /**
   * Migrates a single entity from Camunda 7 to Camunda 8.
   * <p>
   * This method is transactional and ensures that all database operations
   * for a single entity migration are committed or rolled back together.
   * </p>
   * <p>
   * Implementations should:
   * <ul>
   *   <li>Convert the C7 entity to C8 format using interceptors</li>
   *   <li>Persist the converted entity to the C8 database</li>
   *   <li>Mark the entity as migrated or skipped in the migration tracking database</li>
   *   <li>Handle any migration errors appropriately</li>
   * </ul>
   * </p>
   *
   * @param entity the Camunda 7 entity to migrate
   */
  abstract Object migrateTransactionally(C7 entity);

  protected C8 convert(C7Entity<C7> c7Entity, ObjectBuilder<C8> builder) {
    EntityConversionContext<C7, ObjectBuilder<C8>> context = new EntityConversionContext<>(c7Entity.unwrap(), builder, processEngine);

    try {
      entityConversionService.prepareParentProperties(context);
      entityConversionService.convertWithContext(context);
    } catch (VariableInterceptorException | EntityInterceptorException e) {
      EntitySkippedException entitySkippedException = new EntitySkippedException(c7Entity, e.getMessage());
      skippingEntityDueToInterceptorError(entitySkippedException);
      throw entitySkippedException;
    }

    return builder.build();
  }
}

