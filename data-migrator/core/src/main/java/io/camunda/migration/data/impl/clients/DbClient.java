/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.clients;

import static io.camunda.migration.data.impl.logging.DbClientLogs.FAILED_TO_CHECK_EXISTENCE;
import static io.camunda.migration.data.impl.logging.DbClientLogs.FAILED_TO_CHECK_KEY;
import static io.camunda.migration.data.impl.logging.DbClientLogs.FAILED_TO_DELETE;
import static io.camunda.migration.data.impl.logging.DbClientLogs.FAILED_TO_FIND_ALL;
import static io.camunda.migration.data.impl.logging.DbClientLogs.FAILED_TO_FIND_ALL_SKIPPED;
import static io.camunda.migration.data.impl.logging.DbClientLogs.FAILED_TO_FIND_KEY_BY_ID;
import static io.camunda.migration.data.impl.logging.DbClientLogs.FAILED_TO_FIND_LATEST_C7_ID;
import static io.camunda.migration.data.impl.logging.DbClientLogs.FAILED_TO_FIND_LATEST_CREATE_TIME;
import static io.camunda.migration.data.impl.logging.DbClientLogs.FAILED_TO_FIND_SKIPPED_COUNT;
import static io.camunda.migration.data.impl.logging.DbClientLogs.FAILED_TO_INSERT_RECORD;
import static io.camunda.migration.data.impl.logging.DbClientLogs.FAILED_TO_UPDATE_KEY;
import static io.camunda.migration.data.impl.logging.DbClientLogs.FAILED_TO_UPDATE_SKIP_REASON;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.util.ExceptionUtils.callApi;

import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.Pagination;
import io.camunda.migration.data.impl.logging.DbClientLogs;
import io.camunda.migration.data.impl.persistence.IdKeyDbModel;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.impl.util.PrintUtils;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Wrapper class for IdKeyMapper database operations with exception handling.
 * Maintains the same exception wrapping behavior as ExceptionUtils.callApi.
 * Supports batch insert operations to reduce database interactions.
 */
@Component
public class DbClient {

  @Autowired
  protected MigratorProperties properties;

  @Autowired
  protected IdKeyMapper idKeyMapper;

  /**
   * Buffer for collecting INSERT operations to be batched.
   */
  protected final List<IdKeyDbModel> insertBuffer = new java.util.ArrayList<>();

  /**
   * Tracks C8 process instance keys created in the current batch.
   * Used for rollback if batch insert fails.
   */
  protected final List<Long> currentBatchC8Keys = new java.util.ArrayList<>();

  /**
   * Stores C8 keys that failed to persist in the last batch operation.
   * This allows retrieving them after an exception for rollback purposes.
   */
  protected final ThreadLocal<List<Long>> failedBatchKeys = ThreadLocal.withInitial(java.util.ArrayList::new);

  /**
   * In-memory cache for C7 ID to C8 Key mappings.
   * Key format: "TYPE:C7_ID" -> C8_KEY
   * This allows lookups of entities that are in the batch buffer but not yet committed to DB.
   */
  protected final java.util.Map<String, Long> idKeyCache = new java.util.concurrent.ConcurrentHashMap<>();

  /**
   * In-memory cache for existence checks.
   * Key format: "TYPE:C7_ID" -> Boolean (exists and has C8 key)
   * Used for isMigrated() checks on buffered entities.
   */
  protected final java.util.Map<String, Boolean> existenceCache = new java.util.concurrent.ConcurrentHashMap<>();

  /**
   * Checks if an entity exists in the mapping table by type and id.
   */
  public boolean checkExistsByC7IdAndType(String c7Id, TYPE type) {
    return callApi(() -> idKeyMapper.checkExistsByC7IdAndType(type, c7Id), FAILED_TO_CHECK_EXISTENCE + c7Id);
  }

  /**
   * Checks if an entity exists in the mapping table by type and id.
   * This checks both the in-memory cache and the database.
   */
  public boolean checkHasC8KeyByC7IdAndType(String c7Id, TYPE type) {
    // First check cache
    String cacheKey = getCacheKey(type, c7Id);
    Boolean cached = existenceCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    // Fall back to database
    return callApi(() -> idKeyMapper.checkHasC8KeyByC7IdAndType(type, c7Id), FAILED_TO_CHECK_KEY + c7Id);
  }

  /**
   * Generates a cache key from type and C7 ID.
   */
  private String getCacheKey(TYPE type, String c7Id) {
    return type.name() + ":" + c7Id;
  }

  /**
   * Finds the latest create time by type.
   */
  public Date findLatestCreateTimeByType(TYPE type) {
    Date latestCreateTime = callApi(() -> idKeyMapper.findLatestCreateTimeByType(type),
        FAILED_TO_FIND_LATEST_CREATE_TIME + type);
    DbClientLogs.foundLatestCreateTime(latestCreateTime, type);
    return latestCreateTime;
  }

  /**
   * Finds the most recently migrated ID for the given type.
   */
  public String findLatestIdByType(TYPE type) {
    String c7Id = callApi(() -> idKeyMapper.findLatestIdByType(type), FAILED_TO_FIND_LATEST_C7_ID + type);
    DbClientLogs.foundLatestIdForType(c7Id, type);
    return c7Id;
  }

  /**
   * Finds the key by C7 ID and type.
   * This checks the in-memory cache first before querying the database.
   */
  public Long findC8KeyByC7IdAndType(String c7Id, TYPE type) {
    // First check cache for buffered entities
    String cacheKey = getCacheKey(type, c7Id);
    Long cachedKey = idKeyCache.get(cacheKey);
    if (cachedKey != null) {
      return cachedKey;
    }

    // Fall back to database
    return callApi(() -> idKeyMapper.findC8KeyByC7IdAndType(c7Id, type), FAILED_TO_FIND_KEY_BY_ID + c7Id);
  }

  /**
   * Finds all C7 IDs.
   */
  public List<String> findAllC7Ids() {
    return callApi(() -> idKeyMapper.findAllC7Ids(), FAILED_TO_FIND_ALL);
  }

  /**
   * Updates a record by setting the key for an existing ID and type.
   * Also updates the in-memory cache.
   */
  public void updateC8KeyByC7IdAndType(String c7Id, Long c8Key, TYPE type) {
    DbClientLogs.updatingC8KeyForC7Id(c7Id, c8Key);
    var model = createIdKeyDbModel(c7Id, null, c8Key, type);
    callApi(() -> idKeyMapper.updateC8KeyByC7IdAndType(model), FAILED_TO_UPDATE_KEY + c8Key);

    // Update cache after successful database update
    String cacheKey = getCacheKey(type, c7Id);
    if (c8Key != null) {
      idKeyCache.put(cacheKey, c8Key);
      existenceCache.put(cacheKey, true);
    } else {
      idKeyCache.remove(cacheKey);
      existenceCache.put(cacheKey, false);
    }
  }

  public void updateSkipReason(String c7Id, TYPE type, String skipReason) {
    if (!properties.getSaveSkipReason()) {
      return;
    }

    DbClientLogs.updatingSkipReason(c7Id, skipReason);
    var model = createIdKeyDbModel(c7Id, null, null, type, skipReason);
    callApi(() -> idKeyMapper.updateSkipReason(model), FAILED_TO_UPDATE_SKIP_REASON + c7Id);
  }

  /**
   * Inserts a new process instance record into the mapping table.
   */
  public void insert(String c7Id, Long c8Key, Date createTime, TYPE type) {
    insert(c7Id, c8Key, createTime, type, null);
  }

  /**
   * Inserts a new record into the mapping table.
   */
  public void insert(String c7Id, Long c8Key, TYPE type) {
    insert(c7Id, c8Key, null, type, null);
  }

  /**
   * Inserts a new process instance record into the mapping table.
   */
  public void insert(String c7Id, Long c8Key, Date createTime, TYPE type, String skipReason) {
    String finalSkipReason = properties.getSaveSkipReason() ? skipReason : null;
    DbClientLogs.insertingRecord(c7Id, createTime, null, finalSkipReason);
    var model = createIdKeyDbModel(c7Id, createTime, c8Key, type, finalSkipReason);
    
    // Add to batch buffer
    boolean shouldFlush = false;
    synchronized (insertBuffer) {
      insertBuffer.add(model);
      
      // Update in-memory caches
      String cacheKey = getCacheKey(type, c7Id);
      if (c8Key != null) {
        idKeyCache.put(cacheKey, c8Key);
        existenceCache.put(cacheKey, true);
      } else {
        // Entity is being skipped (no C8 key)
        existenceCache.put(cacheKey, false);
      }

      // Track C8 keys for potential rollback
      if (c8Key != null && TYPE.RUNTIME_PROCESS_INSTANCE.equals(type)) {
        currentBatchC8Keys.add(c8Key);
      }
      
      // Check if batch size is reached
      if (insertBuffer.size() >= properties.getBatchSize()) {
        shouldFlush = true;
      }
    }
    
    // Flush outside the synchronized block to avoid nested locking
    if (shouldFlush) {
      flushBatch();
    }
  }

  /**
   * Flushes all pending INSERT operations in the batch buffer.
   * Should be called at the end of a migration process or when batch size is reached.
   * If the batch insert fails, stores the C8 keys in ThreadLocal for rollback handling.
   * 
   * @throws RuntimeException if the batch insert fails
   */
  public void flushBatch() {
    synchronized (insertBuffer) {
      if (insertBuffer.isEmpty()) {
        return;
      }
      
      DbClientLogs.flushingBatch(insertBuffer.size());
      List<IdKeyDbModel> toFlush = new java.util.ArrayList<>(insertBuffer);
      List<Long> keysToRollback = new java.util.ArrayList<>(currentBatchC8Keys);
      
      // Clear buffers before attempting flush
      insertBuffer.clear();
      currentBatchC8Keys.clear();
      
      try {
        callApi(() -> idKeyMapper.insertBatch(toFlush), FAILED_TO_INSERT_RECORD + "batch");
        // Success - cache remains populated with committed data
        failedBatchKeys.get().clear();
      } catch (RuntimeException e) {
        // Failed - remove cache entries for failed records since they weren't persisted
        for (IdKeyDbModel model : toFlush) {
          String cacheKey = getCacheKey(model.getType(), model.getC7Id());
          idKeyCache.remove(cacheKey);
          existenceCache.remove(cacheKey);
        }

        // Store keys for rollback and log error
        failedBatchKeys.get().clear();
        failedBatchKeys.get().addAll(keysToRollback);
        DbClientLogs.batchInsertFailed(keysToRollback.size());
        // Rethrow the exception
        throw e;
      }
    }
  }

  /**
   * Gets the C8 process instance keys from the last failed batch operation.
   * This should be called after catching an exception from flushBatch().
   * 
   * @return List of C8 process instance keys that need to be rolled back
   */
  public List<Long> getFailedBatchKeys() {
    return new java.util.ArrayList<>(failedBatchKeys.get());
  }

  /**
   * Clears the failed batch keys from ThreadLocal.
   */
  public void clearFailedBatchKeys() {
    failedBatchKeys.get().clear();
  }

  /**
   * Clears all buffers and caches without flushing.
   * This is useful for testing or resetting state between operations.
   */
  public void clearBuffers() {
    synchronized (insertBuffer) {
      insertBuffer.clear();
      currentBatchC8Keys.clear();
      idKeyCache.clear();
      existenceCache.clear();
    }
    clearFailedBatchKeys();
  }

  /**
   * Returns the current size of the batch buffer.
   */
  public int getBatchBufferSize() {
    synchronized (insertBuffer) {
      return insertBuffer.size();
    }
  }

  /**
   * Lists skipped entities by type with pagination and prints them.
   */
  public void listSkippedEntitiesByType(TYPE type) {
    new Pagination<String>().pageSize(properties.getPageSize())
        .maxCount(() -> idKeyMapper.countSkippedByType(type))
        .page(offset -> idKeyMapper.findSkippedByType(type, offset, properties.getPageSize())
            .stream()
            .map(IdKeyDbModel::getC7Id)
            .collect(Collectors.toList()))
        .callback(PrintUtils::print);
  }

  /**
   * Processes skipped entities with pagination.
   */
  public void fetchAndHandleSkippedForType(TYPE type, Consumer<IdKeyDbModel> callback) {
    new Pagination<IdKeyDbModel>().pageSize(properties.getPageSize())
        .maxCount(() -> idKeyMapper.countSkippedByType(type))
        // Hardcode offset to 0 since each callback updates the database and leads to fresh results.
        .page(offset -> idKeyMapper.findSkippedByType(type, 0, properties.getPageSize()))
        .callback(callback);
  }

  /**
   * Finds the count of skipped entities for all types
   */
  public Long countSkipped() {
    return callApi(() -> idKeyMapper.countSkipped(), FAILED_TO_FIND_SKIPPED_COUNT);
  }

  /**
   * Finds the count of skipped entities for the given type
   */
  public Long countSkippedByType(TYPE type) {
    return callApi(() -> idKeyMapper.countSkippedByType(type), FAILED_TO_FIND_SKIPPED_COUNT);
  }

  /**
   * Finds the Ids of all skipped process instances.
   */
  public List<IdKeyDbModel> findSkippedProcessInstances() {
    return callApi(() -> idKeyMapper.findSkippedByType(TYPE.RUNTIME_PROCESS_INSTANCE, 0, properties.getPageSize()),
        FAILED_TO_FIND_ALL_SKIPPED);
  }

  /**
   * Deletes all mappings from the database.
   */
  public void deleteAllMappings() {
    findAllC7Ids().forEach(this::deleteByC7Id);
  }

  /**
   * Deletes a mapping by C7 ID.
   */
  protected void deleteByC7Id(String c7Id) {
    callApi(() -> idKeyMapper.deleteByC7Id(c7Id), FAILED_TO_DELETE + c7Id);
  }

  /**
   * Creates a new IdKeyDbModel instance with the provided parameters including skip reason.
   */
  protected IdKeyDbModel createIdKeyDbModel(String c7Id, Date createTime, Long c8Key, TYPE type, String skipReason) {
    var keyIdDbModel = new IdKeyDbModel();
    keyIdDbModel.setC7Id(c7Id);
    keyIdDbModel.setCreateTime(createTime);
    keyIdDbModel.setC8Key(c8Key);
    keyIdDbModel.setType(type);
    keyIdDbModel.setSkipReason(skipReason);
    return keyIdDbModel;
  }

  /**
   * Creates a new IdKeyDbModel instance with the provided parameters.
   */
  protected IdKeyDbModel createIdKeyDbModel(String c7Id, Date createTime, Long c8Key, TYPE type) {
    return createIdKeyDbModel(c7Id, createTime, c8Key, type, null);
  }
}
