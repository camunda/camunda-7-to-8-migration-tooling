/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C7_AUDIT_LOG_ENTITY_VERSION;
import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.AuditLogEntity;
import java.util.Set;
import org.camunda.bpm.engine.EntityTypes;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Transformer for converting Camunda 7 UserOperationLogEntry to Camunda 8 AuditLogDbModel.
 * <p>
 * This transformer handles the conversion of user operation logs (audit logs) from Camunda 7
 * to the Camunda 8 audit log format. It maps operation details, timestamps, user information,
 * and entity references.
 * </p>
 */
@Order(12)
@Component
public class AuditLogTransformer implements EntityInterceptor {

  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(UserOperationLogEntry.class);
  }

  /**
   * Executes the transformation of a Camunda 7 UserOperationLogEntry to Camunda 8 AuditLogDbModel.
   * <p>
   * This method:
   * <ul>
   *   <li>Converts entity type, operation type, and category to Camunda 8 equivalents</li>
   *   <li>Sets actor information (user ID and actor type)</li>
   *   <li>Maps tenant information and scope</li>
   *   <li>Preserves annotations and other metadata</li>
   *   <li>Handles special cases where entity types differ between C7 and C8</li>
   * </ul>
   * Note: Key properties like auditLogKey, processInstanceKey, processDefinitionKey, userTaskKey,
   * timestamp, and historyCleanupDate are set externally by AuditLogMigrator.
   * </p>
   *
   * @param context the entity conversion context containing the C7 entity and C8 builder
   * @throws EntityInterceptorException if the C8 builder is null or conversion fails
   */
  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    UserOperationLogEntry userOperationLog = (UserOperationLogEntry) context.getC7Entity();
    AuditLogDbModel.Builder builder = (AuditLogDbModel.Builder) context.getC8DbModelBuilder();

    if (builder == null) {
      throw new EntityInterceptorException("C8 AuditLogDbModel.Builder is null in context");
    }
    String tenantId = getTenantId(userOperationLog.getTenantId());
    builder
        .entityType(convertEntityType(userOperationLog))
        .operationType(convertOperationType(userOperationLog))
        .partitionId(C7_HISTORY_PARTITION_ID)
        .result(AuditLogEntity.AuditLogOperationResult.SUCCESS)
        .entityVersion(C7_AUDIT_LOG_ENTITY_VERSION)
        .actorId(userOperationLog.getUserId())
        .actorType(AuditLogEntity.AuditLogActorType.USER)
        .processDefinitionId(prefixDefinitionId(userOperationLog.getProcessDefinitionKey()))
        .annotation(userOperationLog.getAnnotation())
        .tenantId(tenantId)
        .tenantScope(getAuditLogTenantScope(tenantId))
        .category(convertCategory(userOperationLog.getCategory()));
    // Note: auditLogKey, processInstanceKey, rootProcessInstanceKey, processDefinitionKey, userTaskKey, entityKey, timestamp, historyCleanupDate
    // are set externally in AuditLogMigrator
    // not setting entityValueType and entityOperationIntent as they internal properties meant for future-proofing purposes

    updateEntityTypesThatDontMatchBetweenC7andC8(userOperationLog, builder);

  }

  /**
   * Determines the audit log tenant scope based on the tenant ID.
   * <p>
   * Returns {@link AuditLogEntity.AuditLogTenantScope#GLOBAL} if the tenant ID is the default tenant,
   * otherwise returns {@link AuditLogEntity.AuditLogTenantScope#TENANT}.
   * </p>
   *
   * @param tenantId the tenant ID to evaluate
   * @return the appropriate tenant scope for the audit log entry
   */
  protected AuditLogEntity.@NonNull AuditLogTenantScope getAuditLogTenantScope(String tenantId) {
    AuditLogEntity.AuditLogTenantScope tenantScope;
    if (tenantId.equals(C8_DEFAULT_TENANT)) {
      tenantScope = AuditLogEntity.AuditLogTenantScope.GLOBAL;
    } else {
      tenantScope = AuditLogEntity.AuditLogTenantScope.TENANT;
    }
    return tenantScope;
  }

  /**
   * Converts a Camunda 7 operation category to a Camunda 8 AuditLogOperationCategory.
   * <p>
   * Mapping:
   * <ul>
   *   <li>CATEGORY_ADMIN → ADMIN</li>
   *   <li>CATEGORY_OPERATOR → DEPLOYED_RESOURCES</li>
   *   <li>CATEGORY_TASK_WORKER → USER_TASKS</li>
   *   <li>Other categories → UNKNOWN</li>
   * </ul>
   * </p>
   *
   * @param category the Camunda 7 operation category
   * @return the corresponding Camunda 8 audit log operation category
   */
  protected AuditLogEntity.AuditLogOperationCategory convertCategory(String category) {
    return switch (category) {
      case UserOperationLogEntry.CATEGORY_ADMIN -> AuditLogEntity.AuditLogOperationCategory.ADMIN;
      case UserOperationLogEntry.CATEGORY_OPERATOR -> AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES;
      case UserOperationLogEntry.CATEGORY_TASK_WORKER -> AuditLogEntity.AuditLogOperationCategory.USER_TASKS;
      default -> AuditLogEntity.AuditLogOperationCategory.UNKNOWN;
    };
  }
  /**
   * Converts a Camunda 7 entity type to a Camunda 8 AuditLogEntityType.
   * <p>
   * This method maps various Camunda 7 entity types to their Camunda 8 equivalents:
   * <ul>
   *   <li>PROCESS_INSTANCE → PROCESS_INSTANCE</li>
   *   <li>VARIABLE → VARIABLE</li>
   *   <li>TASK → USER_TASK</li>
   *   <li>DECISION_INSTANCE, DECISION_DEFINITION, DECISION_REQUIREMENTS_DEFINITION → DECISION</li>
   *   <li>USER → USER</li>
   *   <li>GROUP → GROUP</li>
   *   <li>TENANT → TENANT</li>
   *   <li>AUTHORIZATION → AUTHORIZATION</li>
   *   <li>INCIDENT → INCIDENT</li>
   *   <li>PROCESS_DEFINITION, DEPLOYMENT → RESOURCE</li>
   * </ul>
   * </p>
   * <p>
   * Some Camunda 7 entity types are not currently converted (e.g., BATCH, IDENTITY_LINK,
   * ATTACHMENT, JOB_DEFINITION, JOB, EXTERNAL_TASK, CASE_DEFINITION, CASE_INSTANCE, etc.).
   * Attempting to convert unsupported types will throw an EntityInterceptorException.
   * </p>
   *
   * @param userOperationLog the Camunda 7 user operation log entry
   * @return the corresponding Camunda 8 audit log entity type
   * @throws EntityInterceptorException if the entity type is not supported
   */
  protected AuditLogEntity.AuditLogEntityType convertEntityType(UserOperationLogEntry userOperationLog) {
    return  switch (userOperationLog.getEntityType()) {
      case EntityTypes.PROCESS_INSTANCE -> AuditLogEntity.AuditLogEntityType.PROCESS_INSTANCE;
      case EntityTypes.VARIABLE -> AuditLogEntity.AuditLogEntityType.VARIABLE;
      case EntityTypes.TASK -> AuditLogEntity.AuditLogEntityType.USER_TASK;
      case EntityTypes.DECISION_INSTANCE, EntityTypes.DECISION_DEFINITION, EntityTypes.DECISION_REQUIREMENTS_DEFINITION -> AuditLogEntity.AuditLogEntityType.DECISION;
      case EntityTypes.USER -> AuditLogEntity.AuditLogEntityType.USER;
      case EntityTypes.GROUP -> AuditLogEntity.AuditLogEntityType.GROUP;
      case EntityTypes.TENANT -> AuditLogEntity.AuditLogEntityType.TENANT;
      case EntityTypes.AUTHORIZATION -> AuditLogEntity.AuditLogEntityType.AUTHORIZATION;
      case EntityTypes.INCIDENT -> AuditLogEntity.AuditLogEntityType.INCIDENT;
      case EntityTypes.PROCESS_DEFINITION, EntityTypes.DEPLOYMENT -> AuditLogEntity.AuditLogEntityType.RESOURCE;

      // Camunda 7 entity types that are currently NOT converted:
      // EntityTypes.BATCH, EntityTypes.IDENTITY_LINK, EntityTypes.ATTACHMENT, EntityTypes.JOB_DEFINITION,
      // EntityTypes.JOB, EntityTypes.EXTERNAL_TASK, EntityTypes.CASE_DEFINITION, EntityTypes.CASE_INSTANCE,
      // EntityTypes.METRICS, EntityTypes.TASK_METRICS, EntityTypes.OPERATION_LOG, EntityTypes.FILTER,
      // EntityTypes.COMMENT, EntityTypes.PROPERTY

      default -> throw new EntityInterceptorException(
          HistoryMigratorLogs.UNSUPPORTED_AUDIT_LOG_ENTITY_TYPE + userOperationLog.getEntityType());
    };
  }

  /**
   * Converts a Camunda 7 operation type to a Camunda 8 AuditLogOperationType.
   * <p>
   * This method handles the mapping of various operation types, with special handling
   * for context-specific operations:
   * </p>
   * <h3>Task Operations:</h3>
   * <ul>
   *   <li>ASSIGN, CLAIM, DELEGATE → ASSIGN</li>
   *   <li>COMPLETE → COMPLETE</li>
   *   <li>SET_PRIORITY, SET_OWNER, UPDATE → UPDATE</li>
   * </ul>
   * <h3>Process Instance Operations:</h3>
   * <ul>
   *   <li>CREATE → CREATE</li>
   *   <li>DELETE → CANCEL (for process instances) or DELETE (for other entities)</li>
   *   <li>MODIFY_PROCESS_INSTANCE → MODIFY</li>
   *   <li>MIGRATE → MIGRATE</li>
   *   <li>DELETE_HISTORY, REMOVE_VARIABLE → DELETE</li>
   * </ul>
   * <h3>Variable Operations:</h3>
   * <ul>
   *   <li>MODIFY_VARIABLE, SET_VARIABLE, SET_VARIABLES → UPDATE</li>
   * </ul>
   * <h3>Decision Operations:</h3>
   * <ul>
   *   <li>EVALUATE → EVALUATE</li>
   * </ul>
   * <h3>Incident Operations:</h3>
   * <ul>
   *   <li>RESOLVE → RESOLVE (for process instances) or UPDATE (for other entities)</li>
   * </ul>
   *
   * @param userOperationLog the Camunda 7 user operation log entry
   * @return the corresponding Camunda 8 audit log operation type
   * @throws EntityInterceptorException if the operation type is not supported
   */
  protected AuditLogEntity.AuditLogOperationType convertOperationType(UserOperationLogEntry userOperationLog) {
    String operationType = userOperationLog.getOperationType();

    return switch (operationType) {
      // Task operations
      case UserOperationLogEntry.OPERATION_TYPE_ASSIGN,
           UserOperationLogEntry.OPERATION_TYPE_CLAIM,
           UserOperationLogEntry.OPERATION_TYPE_DELEGATE ->
          AuditLogEntity.AuditLogOperationType.ASSIGN;
      case UserOperationLogEntry.OPERATION_TYPE_COMPLETE ->
          AuditLogEntity.AuditLogOperationType.COMPLETE;
      case UserOperationLogEntry.OPERATION_TYPE_SET_PRIORITY,
           UserOperationLogEntry.OPERATION_TYPE_SET_OWNER,
           UserOperationLogEntry.OPERATION_TYPE_UPDATE ->
          AuditLogEntity.AuditLogOperationType.UPDATE;

      // ProcessInstance operations
      case UserOperationLogEntry.OPERATION_TYPE_CREATE ->
          AuditLogEntity.AuditLogOperationType.CREATE;
      case UserOperationLogEntry.OPERATION_TYPE_DELETE -> {
        // ProcessInstance Delete maps to CANCEL, but other entity types map to DELETE
        if (EntityTypes.PROCESS_INSTANCE.equals(userOperationLog.getEntityType())) {
          yield AuditLogEntity.AuditLogOperationType.CANCEL;
        } else {
          yield AuditLogEntity.AuditLogOperationType.DELETE;
        }
      }
      case UserOperationLogEntry.OPERATION_TYPE_MODIFY_PROCESS_INSTANCE ->
          AuditLogEntity.AuditLogOperationType.MODIFY;
      case UserOperationLogEntry.OPERATION_TYPE_MIGRATE ->
          AuditLogEntity.AuditLogOperationType.MIGRATE;
      case UserOperationLogEntry.OPERATION_TYPE_DELETE_HISTORY, UserOperationLogEntry.OPERATION_TYPE_REMOVE_VARIABLE ->
          AuditLogEntity.AuditLogOperationType.DELETE;

      // Variable operations
      case UserOperationLogEntry.OPERATION_TYPE_MODIFY_VARIABLE, UserOperationLogEntry.OPERATION_TYPE_SET_VARIABLE,
           UserOperationLogEntry.OPERATION_TYPE_SET_VARIABLES ->
          AuditLogEntity.AuditLogOperationType.UPDATE;

      // DecisionDefinition operations
      case UserOperationLogEntry.OPERATION_TYPE_EVALUATE ->
          AuditLogEntity.AuditLogOperationType.EVALUATE;

      // Incident operations
      case UserOperationLogEntry.OPERATION_TYPE_RESOLVE -> {
        if (EntityTypes.PROCESS_INSTANCE.equals(userOperationLog.getEntityType())) {
          yield AuditLogEntity.AuditLogOperationType.RESOLVE;
        } else {
          yield AuditLogEntity.AuditLogOperationType.UPDATE;
        }
      }

      default ->
          throw new EntityInterceptorException(HistoryMigratorLogs.UNSUPPORTED_AUDIT_LOG_OPERATION_TYPE + operationType);
    };
  }

  /**
   * Updates entity types for special cases where C7 and C8 handle them differently.
   * <p>
   * This method corrects the entity type in cases where Camunda 7 and Camunda 8 use
   * different entity types for the same operation:
   * </p>
   * <ul>
   *   <li>RESOLVE operation on PROCESS_INSTANCE → entity type becomes INCIDENT
   *       (because resolving incidents in C7 is logged as a process instance operation,
   *       but in C8 it's an incident operation)</li>
   *   <li>SET_VARIABLE or SET_VARIABLES operations → entity type becomes VARIABLE
   *       (ensures variable operations are correctly typed as variable entities)</li>
   * </ul>
   *
   * @param userOperationLog the Camunda 7 user operation log entry
   * @param builder the audit log builder to update
   */
  protected void updateEntityTypesThatDontMatchBetweenC7andC8(UserOperationLogEntry userOperationLog, AuditLogDbModel.Builder builder) {
    if (UserOperationLogEntry.OPERATION_TYPE_RESOLVE.equals(userOperationLog.getOperationType())
        && EntityTypes.PROCESS_INSTANCE.equals(userOperationLog.getEntityType())) {
      builder.entityType(AuditLogEntity.AuditLogEntityType.INCIDENT);
    } else if (UserOperationLogEntry.OPERATION_TYPE_SET_VARIABLE.equals(userOperationLog.getOperationType())
        || UserOperationLogEntry.OPERATION_TYPE_SET_VARIABLES.equals(userOperationLog.getOperationType())) {
      builder.entityType(AuditLogEntity.AuditLogEntityType.VARIABLE);
    }
  }

}
