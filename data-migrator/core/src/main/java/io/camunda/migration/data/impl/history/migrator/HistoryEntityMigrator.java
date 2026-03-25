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
import static io.camunda.migration.data.constants.MigratorConstants.C7_MULTI_INSTANCE_BODY_SUFFIX;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigrating;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigrationCompleted;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logRetrying;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logSkippingDebug;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;

import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.impl.DataSourceRegistry;
import io.camunda.migration.data.impl.EntityConversionService;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.impl.clients.C8Client;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.C8EntityNotFoundException;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.history.PartitionSupplier;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.repository.ResourceDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base class for all history entity migrators.
 * Contains common utility methods for migration operations.
 */
public abstract class HistoryEntityMigrator<C7, C8> {

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

  @Autowired
  protected DataSourceRegistry dataSourceRegistry;

  @Autowired
  protected PartitionSupplier partitionSupplier;

  protected MigratorMode mode = MIGRATE;

  protected void markMigrated(C7Entity<?> c7Entity, MigrationResult result) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateC8KeyByC7IdAndType(c7Entity.getId(), result.c8Key(), c7Entity.getType(), result.partitionId());
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(c7Entity.getId(), result.c8Key(), c7Entity.getCreationTime(), c7Entity.getType(), result.partitionId());
    }
  }

  @SuppressWarnings("unchecked")
  protected void markSkipped(EntitySkippedException e) {
    C7Entity<C7> c7Entity = (C7Entity<C7>) e.getC7Entity();
    saveRecord(c7Entity.getId(), c7Entity.getType(), c7Entity.getCreationTime(), e.getMessage());
    logSkippingDebug(e);
  }

  protected void saveRecord(String c7Id, TYPE type, Date createTime, String skipReason) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateSkipReason(c7Id, type, skipReason);
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(c7Id, (String) null, createTime, type, skipReason);
    }
  }

  public void setMode(MigratorMode mode) {
    this.mode = mode;
  }

  public abstract BiConsumer<Consumer<C7>, Date> fetchForMigrateHandler();

  public abstract Function<String, C7> fetchForRetryHandler();

  /**
   * Returns the entity type that this migrator handles.
   * <p>
   * This method is used to identify which entity type is being migrated
   * and is used for tracking migration status in the database.
   * </p>
   *
   * @return the entity type from IdKeyMapper.TYPE enum
   */
  public abstract TYPE getType();

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
   * @return the migration result containing the C8 key and optional metadata, or null if not migrated
   */
  public abstract MigrationResult migrateTransactionally(C7 entity);

  public void migrate() {
    setMode(MIGRATE);
    logMigrating(getType());

    TransactionTemplate txTemplate = dataSourceRegistry.getMigratorTxTemplate();
    fetchForMigrateHandler().accept(entity -> txTemplate.execute(status -> migrateEntity(entity)),
        dbClient.findLatestCreateTimeByType(getType()));
  }

  public List<EntitySkippedException> retry() {
    setMode(RETRY_SKIPPED);
    logRetrying(getType());

    TransactionTemplate txTemplate = dataSourceRegistry.getMigratorTxTemplate();
    return dbClient.fetchAndHandleSkippedForType(getType(), idKeyDbModel -> {
      C7 c7Entity = fetchForRetryHandler().apply(idKeyDbModel.getC7Id());
      return txTemplate.execute(status -> migrateEntity(c7Entity));
    });
  }

  @SuppressWarnings("unchecked")
  protected EntitySkippedException migrateEntity(C7 entity) {
    C7Entity<C7> c7Entity = (C7Entity<C7>) getC7Entity(entity);
    try {
      MigrationResult result = migrateTransactionally(c7Entity.unwrap());
      if (result != null) {
        markMigrated(c7Entity, result);
        logMigrationCompleted(c7Entity);
      }
      return null;
    } catch (EntitySkippedException e) {
      markSkipped(e);
      return e;
    } catch (C8EntityNotFoundException e) {
      EntitySkippedException skippedException = new EntitySkippedException(c7Entity, e.getMessage());
      markSkipped(skippedException);
      return skippedException;
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

  protected C8 convert(C7Entity<C7> c7Entity, ObjectBuilder<C8> builder) {
    EntityConversionContext<C7, ObjectBuilder<C8>> context = new EntityConversionContext<>(c7Entity.unwrap(), builder, processEngine);

    try {
      entityConversionService.prepareParentProperties(context);
      entityConversionService.convertWithContext(context);
    } catch (VariableInterceptorException | EntityInterceptorException e) {
      throw new EntitySkippedException(c7Entity, e.getMessage());
    }

    return builder.build();
  }

  protected Long findProcessDefinitionKey(String processDefinitionId) {
    Long key = dbClient.findC8KeyByC7IdAndType(processDefinitionId, TYPE.HISTORY_PROCESS_DEFINITION);
    if (key == null) {
      return null;
    }

    return c8Client.findProcessDefinitionOrThrow(key).processDefinitionKey();
  }

  protected ProcessInstanceEntity findProcessInstanceByC7Id(String processInstanceId) {
    if (processInstanceId == null) {
      return null;
    }

    Long c8Key = dbClient.findC8KeyByC7IdAndType(processInstanceId, HISTORY_PROCESS_INSTANCE);
    if (c8Key == null) {
      return null;
    }

    return c8Client.findProcessInstanceOrThrow(c8Key);
  }

  protected Long findFlowNodeInstanceKey(String activityInstanceId) {
    return dbClient.findC8KeyByC7IdAndType(activityInstanceId, HISTORY_FLOW_NODE);
  }

  /**
   * Finds the C8 flow node instance key by C7 activity ID and process instance ID.
   *
   * <p>The method first checks whether the {@code activityId} contains the
   * {@code #multiInstanceBody} suffix. If so, {@code hasMultipleFlowNodes} is set to {@code true}
   * and {@code null} is returned immediately — a multi-instance body cannot be mapped to a single
   * flow node instance.
   *
   * <p>Otherwise, the method searches C8 for flow nodes matching the {@code activityId}
   * within the migrated process instance. Since the multi-instance body suffix is already handled
   * by the early return above, the {@code activityId} at this point is guaranteed to be a plain
   * BPMN element ID. When at least one C8 flow node exists, the method queries C7 for all historic
   * activity instances with the same {@code activityId} and {@code processInstanceId}. This is
   * necessary because only some of the flow nodes may have been migrated at this point. If more
   * than one C7 activity instance exists, {@code hasMultipleFlowNodes} is set to {@code true}
   * and {@code null} is returned. When exactly one C8 flow node matches and the activity is not
   * multi-instance, its key is returned directly.
   *
   * @param activityId           the C7 activity ID (BPMN element ID), may include the
   *                             {@code #multiInstanceBody} suffix
   * @param processInstanceId    the C7 process instance ID
   * @param hasMultipleFlowNodes mutable flag set to {@code true} when the activity is detected as
   *                             with an ambiguous mapping
   * @return the C8 flow node instance key, or {@code null} if not found or ambiguous
   */
  protected Long findFlowNodeInstanceKey(String activityId, String processInstanceId, AtomicBoolean hasMultipleFlowNodes) {
    Long processInstanceKey = dbClient.findC8KeyByC7IdAndType(processInstanceId, HISTORY_PROCESS_INSTANCE);
    if (processInstanceKey == null) {
      return null;
    }

    if (activityId.endsWith(C7_MULTI_INSTANCE_BODY_SUFFIX)) {
      // C8 flow node can't be determinated
      hasMultipleFlowNodes.set(true);
      return null;
    }

    List<FlowNodeInstanceDbModel> flowNodes = c8Client.searchFlowNodeInstances(FlowNodeInstanceDbQuery.of(
        builder -> builder.filter(FlowNodeInstanceFilter.of(
            filter -> filter.flowNodeIds(activityId).processInstanceKeys(processInstanceKey)))));

    if (!flowNodes.isEmpty()) {
      // only some of the flow nodes might have been migrated at this point so first check how many entities are in C7
      var historicActivityInstances = c7Client.findHistoricActivityInstances(activityId, processInstanceId);
      if (historicActivityInstances != null && historicActivityInstances.size() > 1) {
        // C8 flow node can't be determinated
        hasMultipleFlowNodes.set(true);
        return null;
      }
      if (flowNodes.size() == 1) {
        return flowNodes.getFirst().flowNodeInstanceKey();
      }
    }
    return null;
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
}

