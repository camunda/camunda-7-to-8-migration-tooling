/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.clients;

import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_CHECK_EXISTENCE;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_CHECK_KEY;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_DELETE;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_ALL;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_ALL_SKIPPED;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_KEY_BY_ID;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_LATEST_C7_ID;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_LATEST_CREATE_TIME;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_SKIPPED_COUNT;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_INSERT_RECORD;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_UPDATE_KEY;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_UPDATE_SKIP_REASON;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migrator.impl.util.ExceptionUtils.callApi;

import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.Pagination;
import io.camunda.migrator.impl.logging.DbClientLogs;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.migrator.impl.util.PrintUtils;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Wrapper class for IdKeyMapper database operations with exception handling.
 * Maintains the same exception wrapping behavior as ExceptionUtils.callApi.
 */
@Component
public class DbClient {

  @Autowired
  protected MigratorProperties properties;

  @Autowired
  protected IdKeyMapper idKeyMapper;

  /**
   * Checks if an entity exists in the mapping table by type and id.
   */
  public boolean checkExistsByC7IdAndType(String c7Id, TYPE type) {
    return callApi(() -> idKeyMapper.checkExistsByC7IdAndType(type, c7Id), FAILED_TO_CHECK_EXISTENCE + c7Id);
  }

  /**
   * Checks if an entity exists in the mapping table by type and id.
   */
  public boolean checkHasC8KeyByC7IdAndType(String c7Id, TYPE type) {
    return callApi(() -> idKeyMapper.checkHasC8KeyByC7IdAndType(type, c7Id), FAILED_TO_CHECK_KEY + c7Id);
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
   * Finds the latest migrated ID time by type.
   */
  public String findLatestIdByType(TYPE type) {
    String c7Id = callApi(() -> idKeyMapper.findLatestIdByType(type), FAILED_TO_FIND_LATEST_C7_ID + type);
    DbClientLogs.foundLatestIdForType(c7Id, type);
    return c7Id;
  }

  /**
   * Finds the key by C7 ID and type.
   */
  public Long findC8KeyByC7IdAndType(String c7Id, TYPE type) {
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
   */
  public void updateC8KeyByC7IdAndType(String c7Id, Long c8Key, TYPE type) {
    DbClientLogs.updatingC8KeyForC7Id(c7Id, c8Key);
    var model = createIdKeyDbModel(c7Id, null, c8Key, type);
    callApi(() -> idKeyMapper.updateC8KeyByC7IdAndType(model), FAILED_TO_UPDATE_KEY + c8Key);
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
    callApi(() -> idKeyMapper.insert(model), FAILED_TO_INSERT_RECORD + c7Id);
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
