/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING_AUDIT_LOG_MISSING_ROOT_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING_AUDIT_LOG_MISSING_USER_TASK;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_BELONGS_TO_SKIPPED_TASK;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_AUDIT_LOG;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_USER_TASK;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

import io.camunda.db.rdbms.read.domain.UserTaskDbQuery;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.Date;
import org.camunda.bpm.engine.EntityTypes;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for migrating audit log entries from Camunda 7 to Camunda 8.
 * <p>
 * Audit logs in Camunda 7 (UserOperationLogEntry) track user operations and changes made to
 * process instances, tasks, and other entities. This migrator converts these entries to the
 * Camunda 8 audit log format.
 * </p>
 */
@Service
public class AuditLogMigrator extends BaseMigrator<UserOperationLogEntry> {

  /**
   * Migrates all audit log entries from Camunda 7 to Camunda 8.
   * <p>
   * This method handles pagination and processes audit logs in batches, either migrating
   * new entries or retrying skipped ones based on the migration mode.
   * </p>
   */
  @Override
  public void migrate() {
    HistoryMigratorLogs.migratingHistoricAuditLogs();

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_AUDIT_LOG, idKeyDbModel -> {
        UserOperationLogEntry userOperationLogEntry = c7Client.getUserOperationLogEntry(idKeyDbModel.getC7Id());
        self.migrateOne(userOperationLogEntry);
      });
    } else {
      c7Client.fetchAndHandleUserOperationLogEntries(self::migrateOne,
          dbClient.findLatestCreateTimeByType(HISTORY_AUDIT_LOG));
    }
  }

  /**
   * Migrates a single audit log entry from Camunda 7 to Camunda 8.
   * <p>
   * Audit log entries track user operations such as creating, updating, or deleting
   * process instances, tasks, variables, and other entities. The migration preserves
   * the operation details, timestamps, and user information.
   * </p>
   *
   * @param c7AuditLog the user operation log entry from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  @Override
  public void migrateOne(UserOperationLogEntry c7AuditLog) {
    String c7AuditLogId = c7AuditLog.getOperationId();
    if (shouldMigrate(c7AuditLogId, HISTORY_AUDIT_LOG)) {
      HistoryMigratorLogs.migratingHistoricAuditLog(c7AuditLogId);

      try {
        AuditLogDbModel.Builder auditLogDbModelBuilder = configureAuditLogBuilder(c7AuditLog);
        EntityConversionContext<?, ?> context = createEntityConversionContext(
            c7AuditLog, UserOperationLogEntry.class, auditLogDbModelBuilder);

        validateDependenciesAndInsert(c7AuditLog, context, c7AuditLogId);
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7AuditLogId, HISTORY_AUDIT_LOG, c7AuditLog.getTimestamp(), e);
      }
    }
  }

  /**
   * Configures the audit log builder with keys and relationships.
   * <p>
   * This method sets the following properties on the builder:
   * <ul>
   *   <li>auditLogKey - Generated unique key for the audit log entry</li>
   *   <li>processInstanceKey - Resolved from C7 process instance ID</li>
   *   <li>rootProcessInstanceKey - Resolved from C7 root process instance ID</li>
   *   <li>processDefinitionKey - Resolved from C7 process definition ID</li>
   *   <li>userTaskKey - Resolved from C7 task ID</li>
   *   <li>timestamp - Converted from C7 timestamp</li>
   *   <li>historyCleanupDate - Calculated based on TTL configuration</li>
   * </ul>
   * <p>
   * Note: Other properties (actorId, actorType, processDefinitionId, annotation, tenantId,
   * tenantScope, category, entityType, operationType, result, partitionId) are set by the
   * AuditLogTransformer interceptor during entity conversion.
   * </p>
   *
   * @param c7AuditLog the user operation log entry from Camunda 7
   * @return the configured builder
   */
  protected AuditLogDbModel.Builder configureAuditLogBuilder(UserOperationLogEntry c7AuditLog) {
    AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();

    String key = String.format("%s-%s", C7_HISTORY_PARTITION_ID, getNextKey());
    builder.auditLogKey(key);

    resolveProcessInstanceKeys(builder, c7AuditLog);
    resolveProcessDefinitionKey(builder, c7AuditLog);
    resolveUserTaskKey(builder, c7AuditLog);

    setHistoryCleanupDate(c7AuditLog, builder);

    return builder;
  }

  /**
   * Resolves and sets process instance keys on the builder.
   * <p>
   * This method looks up the Camunda 8 keys for process instances based on the
   * Camunda 7 IDs from the audit log entry:
   * <ul>
   *   <li>processInstanceKey - from C7 process instance ID</li>
   *   <li>rootProcessInstanceKey - from C7 root process instance ID</li>
   * </ul>
   * Keys are only set if the corresponding entity has already been migrated.
   * If the entity type is PROCESS_INSTANCE, also sets the entityKey.
   * </p>
   *
   * @param builder the audit log builder
   * @param c7AuditLog the user operation log entry from Camunda 7
   */
  protected void resolveProcessInstanceKeys(
      AuditLogDbModel.Builder builder,
      UserOperationLogEntry c7AuditLog) {

    String c7ProcessInstanceId = c7AuditLog.getProcessInstanceId();
    String c7RootProcessInstanceId = c7AuditLog.getRootProcessInstanceId();
    if (c7ProcessInstanceId != null && isMigrated(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
      var processInstanceId = findProcessInstanceByC7Id(c7ProcessInstanceId).processInstanceKey();
      builder.processInstanceKey(processInstanceId);
      if (EntityTypes.PROCESS_INSTANCE.equals(c7AuditLog.getEntityType())){
        builder.entityKey(String.valueOf(processInstanceId));
      }
      if (c7RootProcessInstanceId != null && isMigrated(c7RootProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
        ProcessInstanceEntity rootProcessInstance = findProcessInstanceByC7Id(c7RootProcessInstanceId);
        builder.rootProcessInstanceKey(rootProcessInstance.processInstanceKey());
      }
    }
  }

  /**
   * Resolves and sets process definition key on the builder.
   * <p>
   * This method looks up the Camunda 8 key for the process definition based on the
   * Camunda 7 process definition ID from the audit log entry.
   * The key is only set if the process definition has already been migrated.
   * If the entity type is PROCESS_DEFINITION, also sets the entityKey.
   * </p>
   *
   * @param builder the audit log builder
   * @param c7AuditLog the user operation log entry from Camunda 7
   */
  protected void resolveProcessDefinitionKey(
      AuditLogDbModel.Builder builder,
      UserOperationLogEntry c7AuditLog) {

    String c7ProcessDefinitionId = c7AuditLog.getProcessDefinitionId();
    if (c7ProcessDefinitionId != null && isMigrated(c7ProcessDefinitionId, HISTORY_PROCESS_DEFINITION)) {
      Long processDefinitionKey = findProcessDefinitionKey(c7ProcessDefinitionId);
      builder.processDefinitionKey(processDefinitionKey);
      if (EntityTypes.PROCESS_DEFINITION.equals(c7AuditLog.getEntityType())){
        builder.entityKey(String.valueOf(processDefinitionKey));
      }
    }
  }

  /**
   * Resolves and sets user task key on the builder.
   * <p>
   * This method looks up the Camunda 8 key for the user task based on the
   * Camunda 7 task ID from the audit log entry.
   * The key is only set if the user task has already been migrated.
   * Also retrieves the element instance key from the user task and sets it on the builder.
   * If the entity type is TASK, also sets the entityKey.
   * </p>
   *
   * @param builder the audit log builder
   * @param c7AuditLog the user operation log entry from Camunda 7
   */
  protected void resolveUserTaskKey(
      AuditLogDbModel.Builder builder,
      UserOperationLogEntry c7AuditLog) {

    String c7TaskId = c7AuditLog.getTaskId();
    if (c7TaskId != null && isMigrated(c7TaskId, HISTORY_USER_TASK)) {
      Long taskKey = dbClient.findC8KeyByC7IdAndType(c7TaskId,
          HISTORY_USER_TASK);
      if (EntityTypes.TASK.equals(c7AuditLog.getEntityType())){
        builder.entityKey(String.valueOf(taskKey));
      }
      UserTaskDbModel userTaskDbModel = searchUserTasksByKey(taskKey);
      builder.userTaskKey(taskKey)
          .elementInstanceKey(userTaskDbModel.elementInstanceKey());
    }
  }

  /**
   * Searches for a user task in Camunda 8 by its task key.
   * <p>
   * This method queries the Camunda 8 database to retrieve the user task details
   * including the element instance key, which is needed for the audit log entry.
   * </p>
   *
   * @param taskKey the Camunda 8 user task key
   * @return the user task database model, or null if not found
   */
  protected UserTaskDbModel searchUserTasksByKey(Long taskKey) {
    return c8Client.searchUserTasks(UserTaskDbQuery.of(b -> b.filter(f -> f.userTaskKeys(taskKey))))
        .stream()
        .findFirst()
        .orElse(null);
  }

  /**
   * Validates dependencies and inserts the audit log or marks it as skipped.
   * <p>
   * This method performs the following validations:
   * <ul>
   *   <li>Checks if the process definition has been migrated when referenced</li>
   *   <li>Checks if the process instance has been migrated when referenced</li>
   *   <li>Checks if the root process instance has been migrated when referenced</li>
   *   <li>Checks if the user task has been migrated when referenced</li>
   * </ul>
   * If any required dependency is missing, the audit log is marked as skipped with
   * an appropriate reason. Otherwise, it is inserted into Camunda 8.
   * </p>
   *
   * @param c7AuditLog the user operation log entry from Camunda 7
   * @param context the entity conversion context
   * @param c7AuditLogId the Camunda 7 audit log ID
   */
  protected void validateDependenciesAndInsert(
      UserOperationLogEntry c7AuditLog,
      EntityConversionContext<?, ?> context,
      String c7AuditLogId) {

    AuditLogDbModel dbModel = convertAuditLog(context);

    if (c7AuditLog.getProcessDefinitionKey() != null && dbModel.processDefinitionKey() == null) {
      markSkipped(c7AuditLogId, HISTORY_AUDIT_LOG, c7AuditLog.getTimestamp(),
          SKIP_REASON_MISSING_PROCESS_DEFINITION);
      HistoryMigratorLogs.skippingAuditLogDueToMissingDefinition(c7AuditLogId);
    } else if (c7AuditLog.getProcessInstanceId() != null && dbModel.processInstanceKey() == null) {
      markSkipped(c7AuditLogId, HISTORY_AUDIT_LOG, c7AuditLog.getTimestamp(),
          SKIP_REASON_MISSING_PROCESS_INSTANCE);
      HistoryMigratorLogs.skippingAuditLogDueToMissingProcess(c7AuditLogId);
    } else if (c7AuditLog.getRootProcessInstanceId() != null && dbModel.rootProcessInstanceKey() == null) {
      markSkipped(c7AuditLogId, HISTORY_AUDIT_LOG, c7AuditLog.getTimestamp(), SKIP_REASON_MISSING_ROOT_PROCESS_INSTANCE);
      HistoryMigratorLogs.skippingHistoricAuditLog(SKIPPING_AUDIT_LOG_MISSING_ROOT_INSTANCE, c7AuditLogId);
    } else if (c7AuditLog.getTaskId() != null && dbModel.userTaskKey() == null) {
      markSkipped(c7AuditLogId, HISTORY_AUDIT_LOG, c7AuditLog.getTimestamp(), SKIP_REASON_BELONGS_TO_SKIPPED_TASK);
      HistoryMigratorLogs.skippingHistoricAuditLog(SKIPPING_AUDIT_LOG_MISSING_USER_TASK, c7AuditLogId);
    }  else {
      insertAuditLog(c7AuditLog, dbModel, c7AuditLogId);
    }
  }

  /**
   * Inserts the migrated audit log into Camunda 8 and marks it as migrated.
   *
   * @param c7AuditLog the original Camunda 7 audit log entry
   * @param dbModel the converted Camunda 8 database model
   * @param c7AuditLogId the Camunda 7 audit log ID
   */
  protected void insertAuditLog(UserOperationLogEntry c7AuditLog, AuditLogDbModel dbModel, String c7AuditLogId) {
    c8Client.insertAuditLog(dbModel);
    markMigrated(c7AuditLogId, dbModel.auditLogKey(), c7AuditLog.getTimestamp(), HISTORY_AUDIT_LOG);
    HistoryMigratorLogs.migratingHistoricAuditLogCompleted(c7AuditLogId);
  }

  /**
   * Converts a Camunda 7 user operation log entry to a Camunda 8 audit log database model.
   *
   * @param context the entity conversion context
   * @return the converted audit log database model
   */
  protected AuditLogDbModel convertAuditLog(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    AuditLogDbModel.Builder builder = (AuditLogDbModel.Builder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  /**
   * Sets the history cleanup date and timestamp on the audit log builder.
   * <p>
   * This method:
   * <ul>
   *   <li>Converts the C7 timestamp to C8 format (OffsetDateTime)</li>
   *   <li>Calculates the history cleanup date based on the configured TTL</li>
   *   <li>Sets both timestamp and historyCleanupDate on the builder</li>
   * </ul>
   * The historyCleanupDate determines when the audit log entry will be eligible
   * for cleanup/deletion in Camunda 8.
   * </p>
   *
   * @param c7AuditLog the user operation log entry from Camunda 7
   * @param auditLogDbModelBuilder the audit log builder to configure
   */
  protected void setHistoryCleanupDate(UserOperationLogEntry c7AuditLog,
                                       AuditLogDbModel.Builder auditLogDbModelBuilder) {
    Date c7EndTime = c7AuditLog.getTimestamp();
    var c8EndTime = calculateEndDate(c7EndTime);
    var c8HistoryCleanupDate = calculateHistoryCleanupDate(c8EndTime, c7AuditLog.getRemovalTime());

    auditLogDbModelBuilder
        .historyCleanupDate(c8HistoryCleanupDate)
        .timestamp(c8EndTime);
  }
}
