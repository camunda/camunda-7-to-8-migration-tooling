/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.history;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_AUDIT_LOG;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.ProcessInstanceEntity;
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
   *
   * @param c7AuditLog the user operation log entry from Camunda 7
   * @return the configured builder
   */
  protected AuditLogDbModel.Builder configureAuditLogBuilder(UserOperationLogEntry c7AuditLog) {
    AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();

    String auditLogKey = c7AuditLog.getId();
    builder.auditLogKey(auditLogKey);

    resolveProcessInstanceAndDefinitionKeys(builder, c7AuditLog);

    return builder;
  }

  /**
   * Resolves and sets process instance and process definition keys on the builder.
   *
   * @param builder the audit log builder
   * @param c7AuditLog the user operation log entry from Camunda 7
   */
  protected void resolveProcessInstanceAndDefinitionKeys(
      AuditLogDbModel.Builder builder,
      UserOperationLogEntry c7AuditLog) {

    String c7ProcessInstanceId = c7AuditLog.getProcessInstanceId();
    if (c7ProcessInstanceId != null) {
      ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7ProcessInstanceId);
      if (processInstance != null) {
        builder.processInstanceKey(processInstance.processInstanceKey());
      }
    }

    String c7ProcessDefinitionId = c7AuditLog.getProcessDefinitionId();
    if (c7ProcessDefinitionId != null) {
      Long processDefinitionKey = findProcessDefinitionKey(c7ProcessDefinitionId);
      if (processDefinitionKey != null) {
        builder.processDefinitionKey(processDefinitionKey);
      }
    }
  }

  /**
   * Validates dependencies and inserts the audit log or marks it as skipped.
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
    } else {
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
    // Use hash code of the audit log key as the Long value for tracking
    Long trackingKey = (long) dbModel.auditLogKey().hashCode(); // TODO
    markMigrated(c7AuditLogId, trackingKey, c7AuditLog.getTimestamp(), HISTORY_AUDIT_LOG);
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
}
