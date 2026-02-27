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
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.UNSUPPORTED_AUDIT_LOG_ENTITY_TYPE;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static org.camunda.bpm.engine.EntityTypes.*;
import static org.camunda.bpm.engine.history.UserOperationLogEntry.*;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel.Builder;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.search.entities.AuditLogEntity;
import java.util.Set;
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
public class AuditLogTransformer implements EntityInterceptor<UserOperationLogEntry, AuditLogDbModel.Builder> {

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
   * @param userOperationLog the Camunda 7 user operation log entry to transform
   * @param builder the Camunda 8 audit log builder to populate with converted data
   * @throws EntityInterceptorException if the C8 builder is null or conversion fails
   */
  @Override
  public void execute(UserOperationLogEntry userOperationLog, Builder builder) {
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
        .category(convertCategory(userOperationLog.getCategory()))
        .entityDescription(convertEntityDescription(userOperationLog))
        .agentElementId(null)
        .relatedEntityKey(null)
        .relatedEntityType(null);

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
      case CATEGORY_ADMIN -> AuditLogEntity.AuditLogOperationCategory.ADMIN;
      case CATEGORY_OPERATOR -> AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES;
      case CATEGORY_TASK_WORKER -> AuditLogEntity.AuditLogOperationCategory.USER_TASKS;
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
   *   <li>GROUP_MEMBERSHIP → GROUP (membership operations)</li>
   *   <li>TENAT_MEMBERSHIP → TENANT (membership operations)</li>
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
      case PROCESS_INSTANCE -> AuditLogEntity.AuditLogEntityType.PROCESS_INSTANCE;
      case VARIABLE -> AuditLogEntity.AuditLogEntityType.VARIABLE;
      case TASK -> AuditLogEntity.AuditLogEntityType.USER_TASK;
      case DECISION_INSTANCE, DECISION_DEFINITION, DECISION_REQUIREMENTS_DEFINITION -> AuditLogEntity.AuditLogEntityType.DECISION;
      case USER -> AuditLogEntity.AuditLogEntityType.USER;
      case GROUP, GROUP_MEMBERSHIP -> AuditLogEntity.AuditLogEntityType.GROUP;
      case TENANT, TENANT_MEMBERSHIP -> AuditLogEntity.AuditLogEntityType.TENANT;
      case AUTHORIZATION -> AuditLogEntity.AuditLogEntityType.AUTHORIZATION;
      case INCIDENT -> AuditLogEntity.AuditLogEntityType.INCIDENT;
      case PROCESS_DEFINITION, DEPLOYMENT -> AuditLogEntity.AuditLogEntityType.RESOURCE;

      // Camunda 7 entity types that are currently NOT converted:
      // BATCH, IDENTITY_LINK, ATTACHMENT, JOB_DEFINITION,
      // JOB, EXTERNAL_TASK, CASE_DEFINITION, CASE_INSTANCE,
      // METRICS, TASK_METRICS, OPERATION_LOG, FILTER,
      // COMMENT, PROPERTY

      default -> throw new EntityInterceptorException(UNSUPPORTED_AUDIT_LOG_ENTITY_TYPE + userOperationLog.getEntityType());
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
   *   <li>CREATE → CREATE (or ASSIGN for group/tenant memberships)</li>
   *   <li>DELETE → CANCEL (for process instances) or UNASSIGN (for group/tenant memberships) or DELETE (for other entities)</li>
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
   * <h3>Membership Operations:</h3>
   * <ul>
   *   <li>CREATE on GROUP_MEMBERSHIP or TENANT_MEMBERSHIP → ASSIGN</li>
   *   <li>DELETE on GROUP_MEMBERSHIP or TENANT_MEMBERSHIP → UNASSIGN</li>
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
      case OPERATION_TYPE_ASSIGN,
           OPERATION_TYPE_CLAIM,
           OPERATION_TYPE_DELEGATE ->
          AuditLogEntity.AuditLogOperationType.ASSIGN;
      case OPERATION_TYPE_COMPLETE ->
          AuditLogEntity.AuditLogOperationType.COMPLETE;
      case OPERATION_TYPE_SET_PRIORITY,
           OPERATION_TYPE_SET_OWNER,
           OPERATION_TYPE_UPDATE ->
          AuditLogEntity.AuditLogOperationType.UPDATE;

      // ProcessInstance operations
      case OPERATION_TYPE_CREATE -> {
        if (GROUP_MEMBERSHIP.equals(userOperationLog.getEntityType()) ||
            TENANT_MEMBERSHIP.equals(userOperationLog.getEntityType())) {
          yield AuditLogEntity.AuditLogOperationType.ASSIGN;
        } else {
          yield AuditLogEntity.AuditLogOperationType.CREATE;
        }
      }
      case OPERATION_TYPE_DELETE -> {
        if (PROCESS_INSTANCE.equals(userOperationLog.getEntityType())) {
          yield AuditLogEntity.AuditLogOperationType.CANCEL;
        } else if (GROUP_MEMBERSHIP.equals(userOperationLog.getEntityType()) ||
            TENANT_MEMBERSHIP.equals(userOperationLog.getEntityType())) {
          yield AuditLogEntity.AuditLogOperationType.UNASSIGN;
        } else {
          yield AuditLogEntity.AuditLogOperationType.DELETE;
        }
      }
      case OPERATION_TYPE_MODIFY_PROCESS_INSTANCE ->
          AuditLogEntity.AuditLogOperationType.MODIFY;
      case OPERATION_TYPE_MIGRATE ->
          AuditLogEntity.AuditLogOperationType.MIGRATE;
      case OPERATION_TYPE_DELETE_HISTORY, OPERATION_TYPE_REMOVE_VARIABLE ->
          AuditLogEntity.AuditLogOperationType.DELETE;

      // Variable operations
      case OPERATION_TYPE_MODIFY_VARIABLE, OPERATION_TYPE_SET_VARIABLE,
           OPERATION_TYPE_SET_VARIABLES ->
          AuditLogEntity.AuditLogOperationType.UPDATE;

      // DecisionDefinition operations
      case OPERATION_TYPE_EVALUATE ->
          AuditLogEntity.AuditLogOperationType.EVALUATE;

      // Incident operations
      case OPERATION_TYPE_RESOLVE -> {
        if (PROCESS_INSTANCE.equals(userOperationLog.getEntityType())) {
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
   * Converts the entity description for certain entity types based on the user operation log entry.
   * @param userOperationLog the Camunda 7 user operation log entry to extract the entity description from
   * @return the converted entity description, or null if not applicable
   */
  protected String convertEntityDescription(UserOperationLogEntry userOperationLog) {
    return switch (userOperationLog.getEntityType()) {
      case VARIABLE -> {
        if (OPERATION_TYPE_DELETE_HISTORY.equals(userOperationLog.getOperationType())) {
          yield userOperationLog.getNewValue();
        } else {
          yield null; // for rest of the operations, the variable name is not stored
        }
      }
      case USER, GROUP, TENANT -> userOperationLog.getNewValue();
      default -> null;
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
  protected void updateEntityTypesThatDontMatchBetweenC7andC8(UserOperationLogEntry userOperationLog, Builder builder) {
    if (OPERATION_TYPE_RESOLVE.equals(userOperationLog.getOperationType())
        && PROCESS_INSTANCE.equals(userOperationLog.getEntityType())) {
      builder.entityType(AuditLogEntity.AuditLogEntityType.INCIDENT);
    } else if (OPERATION_TYPE_SET_VARIABLE.equals(userOperationLog.getOperationType())
        || OPERATION_TYPE_SET_VARIABLES.equals(userOperationLog.getOperationType())) {
      builder.entityType(AuditLogEntity.AuditLogEntityType.VARIABLE);
    }
  }

}
