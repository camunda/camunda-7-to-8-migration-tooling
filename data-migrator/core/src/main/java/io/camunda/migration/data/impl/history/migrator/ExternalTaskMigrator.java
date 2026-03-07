/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history.migrator;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.migratingHistoricExternalTask;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_EXTERNAL_TASK;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;

import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.migration.data.impl.history.C7Entity;
import io.camunda.migration.data.impl.history.EntitySkippedException;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.Date;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.camunda.bpm.engine.history.HistoricExternalTaskLog;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating external task log history from Camunda 7 to Camunda 8.
 *
 * <p>Each unique external task in Camunda 7 maps to a single {@link JobDbModel} in Camunda 8.
 * The migration tracks external tasks by their {@code externalTaskId}. Log entries are processed
 * in descending timestamp order so the most recent (final) state of each external task is used.
 */
@Service
public class ExternalTaskMigrator extends HistoryEntityMigrator<HistoricExternalTaskLog, JobDbModel> {

  @Override
  public BiConsumer<Consumer<HistoricExternalTaskLog>, Date> fetchForMigrateHandler() {
    return c7Client::fetchAndHandleHistoricExternalTaskLogs;
  }

  @Override
  public Function<String, HistoricExternalTaskLog> fetchForRetryHandler() {
    return c7Client::getHistoricExternalTaskLog;
  }

  @Override
  public IdKeyMapper.TYPE getType() {
    return HISTORY_EXTERNAL_TASK;
  }

  /**
   * Migrates a single external task log entry. Each external task is migrated only once,
   * tracked by its {@code externalTaskId}. Subsequent log entries for the same external task
   * are silently skipped.
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>External task already migrated - skipped silently (returns null)</li>
   *   <li>Process definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *   <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *   <li>Root process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE}</li>
   * </ul>
   *
   * @param entity the external task log entry from Camunda 7
   * @return the generated C8 job key, or null if the external task was already migrated
   */
  @Override
  public Long migrateTransactionally(HistoricExternalTaskLog entity) {
    String externalTaskId = entity.getExternalTaskId();
    if (!shouldMigrate(externalTaskId, HISTORY_EXTERNAL_TASK)) {
      return null;
    }

    migratingHistoricExternalTask(externalTaskId);

    var builder = new JobDbModel.Builder();

    Long processDefinitionKey = findProcessDefinitionKey(entity.getProcessDefinitionId());
    builder.processDefinitionKey(processDefinitionKey);

    ProcessInstanceEntity c7ProcessInstance = findProcessInstanceByC7Id(entity.getProcessInstanceId());
    Long processInstanceKey = null;
    if (c7ProcessInstance != null) {
      processInstanceKey = c7ProcessInstance.processInstanceKey();
      builder.processInstanceKey(processInstanceKey);

      if (processInstanceKey != null) {
        String rootProcessInstanceId = entity.getRootProcessInstanceId();
        if (rootProcessInstanceId != null && isMigrated(rootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
          ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(rootProcessInstanceId);
          if (rootProcessInstance != null && rootProcessInstance.processInstanceKey() != null) {
            builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey());
          }
        }

        Long elementInstanceKey = dbClient.findC8KeyByC7IdAndType(entity.getActivityInstanceId(), HISTORY_FLOW_NODE);
        builder.elementInstanceKey(elementInstanceKey);
      }
    }

    JobDbModel dbModel = convert(C7Entity.of(entity), builder);

    if (dbModel.processDefinitionKey() == null) {
      throw new EntitySkippedException(entity, SKIP_REASON_MISSING_PROCESS_DEFINITION);
    }

    if (dbModel.processInstanceKey() == null) {
      throw new EntitySkippedException(entity, SKIP_REASON_MISSING_PROCESS_INSTANCE);
    }

    if (dbModel.rootProcessInstanceKey() == null) {
      throw new EntitySkippedException(entity, SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
    }

    c8Client.insertJob(dbModel);

    return dbModel.jobKey();
  }

  @Override
  protected C7Entity<?> getC7Entity(HistoricExternalTaskLog entity) {
    return C7Entity.of(entity);
  }
}
