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
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_LATEST_CREATE_TIME;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_SKIPPED_COUNT;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_INSERT_RECORD;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_UPDATE_KEY;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_UPDATE_SKIP_REASON;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migrator.impl.util.ExceptionUtils.callApi;

import io.camunda.db.rdbms.read.domain.DecisionDefinitionDbQuery;
import io.camunda.db.rdbms.read.domain.DecisionInstanceDbQuery;
import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessInstanceDbQuery;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.Pagination;
import io.camunda.migrator.impl.logging.DbClientLogs;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.migrator.impl.util.PrintUtils;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
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

  // MyBatis mappers for history migration
  // These are optional because they're only available when C8 data source is configured
  @Autowired(required = false)
  protected ProcessInstanceMapper processInstanceMapper;

  @Autowired(required = false)
  protected DecisionInstanceMapper decisionInstanceMapper;

  @Autowired(required = false)
  protected UserTaskMapper userTaskMapper;

  @Autowired(required = false)
  protected VariableMapper variableMapper;

  @Autowired(required = false)
  protected IncidentMapper incidentMapper;

  @Autowired(required = false)
  protected ProcessDefinitionMapper processDefinitionMapper;

  @Autowired(required = false)
  protected DecisionDefinitionMapper decisionDefinitionMapper;

  @Autowired(required = false)
  protected FlowNodeInstanceMapper flowNodeInstanceMapper;

  @Autowired(required = false)
  protected DecisionRequirementsMapper decisionRequirementsMapper;

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
    
    // Add to batch buffer
    synchronized (insertBuffer) {
      insertBuffer.add(model);
      
      // Track C8 keys for potential rollback
      if (c8Key != null && TYPE.RUNTIME_PROCESS_INSTANCE.equals(type)) {
        currentBatchC8Keys.add(c8Key);
      }
      
      // Flush if batch size is reached
      if (insertBuffer.size() >= properties.getBatchSize()) {
        flushBatch();
      }
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
        // Success - clear any previous failed keys
        failedBatchKeys.get().clear();
      } catch (RuntimeException e) {
        // Failed - store keys for rollback and log error
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
      }
    }
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

  // ========== MyBatis Mapper Wrapper Methods for History Migration ==========

  /**
   * Inserts a ProcessDefinition into the database.
   */
  public void insertProcessDefinition(ProcessDefinitionDbModel dbModel) {
    callApi(() -> processDefinitionMapper.insert(dbModel), "Failed to insert process definition");
  }

  /**
   * Inserts a ProcessInstance into the database.
   */
  public void insertProcessInstance(ProcessInstanceDbModel dbModel) {
    callApi(() -> processInstanceMapper.insert(dbModel), "Failed to insert process instance");
  }

  /**
   * Finds a ProcessInstance by key.
   */
  public ProcessInstanceEntity findProcessInstance(Long key) {
    return callApi(() -> processInstanceMapper.findOne(key), "Failed to find process instance by key: " + key);
  }

  /**
   * Searches for ProcessInstances matching the query.
   */
  public List<ProcessInstanceEntity> searchProcessInstances(ProcessInstanceDbQuery query) {
    return callApi(() -> processInstanceMapper.search(query), "Failed to search process instances");
  }

  /**
   * Inserts a DecisionRequirementsDefinition into the database.
   */
  public void insertDecisionRequirements(DecisionRequirementsDbModel dbModel) {
    callApi(() -> decisionRequirementsMapper.insert(dbModel), "Failed to insert decision requirements");
  }

  /**
   * Inserts a DecisionDefinition into the database.
   */
  public void insertDecisionDefinition(DecisionDefinitionDbModel dbModel) {
    callApi(() -> decisionDefinitionMapper.insert(dbModel), "Failed to insert decision definition");
  }

  /**
   * Searches for DecisionDefinitions matching the query.
   */
  public List<DecisionDefinitionEntity> searchDecisionDefinitions(DecisionDefinitionDbQuery query) {
    return callApi(() -> decisionDefinitionMapper.search(query), "Failed to search decision definitions");
  }

  /**
   * Inserts a DecisionInstance into the database.
   */
  public void insertDecisionInstance(DecisionInstanceDbModel dbModel) {
    callApi(() -> decisionInstanceMapper.insert(dbModel), "Failed to insert decision instance");
  }

  /**
   * Searches for DecisionInstances matching the query.
   */
  public List<DecisionInstanceEntity> searchDecisionInstances(DecisionInstanceDbQuery query) {
    return callApi(() -> decisionInstanceMapper.search(query), "Failed to search decision instances");
  }

  /**
   * Inserts an Incident into the database.
   */
  public void insertIncident(IncidentDbModel dbModel) {
    callApi(() -> incidentMapper.insert(dbModel), "Failed to insert incident");
  }

  /**
   * Inserts a Variable into the database.
   */
  public void insertVariable(VariableDbModel dbModel) {
    callApi(() -> variableMapper.insert(dbModel), "Failed to insert variable");
  }

  /**
   * Inserts a UserTask into the database.
   */
  public void insertUserTask(UserTaskDbModel dbModel) {
    callApi(() -> userTaskMapper.insert(dbModel), "Failed to insert user task");
  }

  /**
   * Inserts a FlowNodeInstance into the database.
   */
  public void insertFlowNodeInstance(FlowNodeInstanceDbModel dbModel) {
    callApi(() -> flowNodeInstanceMapper.insert(dbModel), "Failed to insert flow node instance");
  }

  /**
   * Searches for FlowNodeInstances matching the query.
   */
  public List<FlowNodeInstanceDbModel> searchFlowNodeInstances(FlowNodeInstanceDbQuery query) {
    return callApi(() -> flowNodeInstanceMapper.search(query), "Failed to search flow node instances");
  }

  /**
   * Searches for ProcessDefinitions matching the query.
   */
  public List<ProcessDefinitionEntity> searchProcessDefinitions(ProcessDefinitionDbQuery query) {
    return callApi(() -> processDefinitionMapper.search(query), "Failed to search process definitions");
  }
}
