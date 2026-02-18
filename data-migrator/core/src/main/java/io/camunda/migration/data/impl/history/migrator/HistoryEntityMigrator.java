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
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logMigrationCompleted;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logRetrying;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.logSkippingDebug;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;

import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.impl.DataSourceRegistry;
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
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Date;
import java.util.List;
import java.util.Set;
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

  protected MigratorMode mode = MIGRATE;

  protected void markMigrated(C7Entity<?> c7Entity, String c8Key) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateC8KeyByC7IdAndType(c7Entity.getId(), c8Key, c7Entity.getType());
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(c7Entity.getId(), c8Key, c7Entity.getCreationTime(), c7Entity.getType(), null);
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
      dbClient.updateC8KeyByC7IdAndType(c7Id, (String) null, type);
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
  public abstract IdKeyMapper.TYPE getType();

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
  public abstract Object migrateTransactionally(C7 entity);

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
      Object c8Key = migrateTransactionally(c7Entity.unwrap());
      if (c8Key != null) {
        markMigrated(c7Entity, c8Key.toString());
        logMigrationCompleted(c7Entity);
      }
      return null;
    } catch (EntitySkippedException e) {
      markSkipped(e);
      return e;
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
    if (processInstanceId == null) {
      return null;
    }

    Long c8Key = dbClient.findC8KeyByC7IdAndType(processInstanceId, HISTORY_PROCESS_INSTANCE);
    if (c8Key == null) {
      return null;
    }

    return c8Client.findProcessInstance(c8Key);
  }

  protected Long findFlowNodeInstanceKey(String activityInstanceId) {
    return dbClient.findC8KeyByC7IdAndType(activityInstanceId, HISTORY_FLOW_NODE);
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

