/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

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
import io.camunda.zeebe.protocol.record.ValueType;
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

  @Override
  public void execute(EntityConversionContext<?, ?> context) {
    UserOperationLogEntry userOperationLog = (UserOperationLogEntry) context.getC7Entity();
    AuditLogDbModel.Builder builder = (AuditLogDbModel.Builder) context.getC8DbModelBuilder();

    if (builder == null) {
      throw new EntityInterceptorException("C8 AuditLogDbModel.Builder is null in context");
    }
    builder.entityType(convertEntityType(userOperationLog)); // set initial value of entity type
    convertOperationTypeAndUpdateEntityType(userOperationLog, builder);
    convertValueType(userOperationLog, builder);
    String tenantId = getTenantId(userOperationLog.getTenantId());
    builder
        .partitionId(C7_HISTORY_PARTITION_ID)
        .result(AuditLogEntity.AuditLogOperationResult.SUCCESS)
        .actorId(userOperationLog.getUserId())
        .actorType(AuditLogEntity.AuditLogActorType.USER)
        .processDefinitionId(prefixDefinitionId(userOperationLog.getProcessDefinitionKey()))
        .annotation(userOperationLog.getAnnotation())
        .tenantId(tenantId)
        .tenantScope(getAuditLogTenantScope(tenantId))
        .category(convertCategory(userOperationLog.getCategory()));
    // Note: auditLogKey, processInstanceKey, rootProcessInstanceKey, processDefinitionKey, userTaskKey, timestamp, historyCleanupDate
    // are set externally in AuditLogMigrator

    // TODO
    // entityOperationIntent, elementInstanceKey, version

  }

  protected AuditLogEntity.@NonNull AuditLogTenantScope getAuditLogTenantScope(String tenantId) {
    AuditLogEntity.AuditLogTenantScope tenantScope;
    if ( tenantId.equals(C8_DEFAULT_TENANT)) {
      tenantScope = AuditLogEntity.AuditLogTenantScope.GLOBAL;
    } else {
      tenantScope = AuditLogEntity.AuditLogTenantScope.TENANT;
    }
    return tenantScope;
  }

  protected AuditLogEntity.AuditLogOperationCategory convertCategory(String category) {
    return switch (category) {
      case UserOperationLogEntry.CATEGORY_ADMIN -> AuditLogEntity.AuditLogOperationCategory.ADMIN;
      case UserOperationLogEntry.CATEGORY_OPERATOR -> AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES;
      case UserOperationLogEntry.CATEGORY_TASK_WORKER -> AuditLogEntity.AuditLogOperationCategory.USER_TASKS;
      default -> AuditLogEntity.AuditLogOperationCategory.UNKNOWN;
    };
  }
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
      // - EntityTypes.BATCH: Could map to BatchOperation but not fully implemented
      // - EntityTypes.IDENTITY_LINK: Task candidate assignments, no direct C8 audit log equivalent
      // - EntityTypes.ATTACHMENT: Task attachments, not tracked in C8 audit logs
      // - EntityTypes.JOB_DEFINITION: Job configuration, not in C8
      // - EntityTypes.JOB: Async jobs, not in C8
      // - EntityTypes.EXTERNAL_TASK: External task workers, not in C8
      // - EntityTypes.CASE_DEFINITION: CMMN not supported in C8
      // - EntityTypes.CASE_INSTANCE: CMMN not supported in C8
      // - EntityTypes.METRICS: Engine metrics, not in C8 audit logs
      // - EntityTypes.TASK_METRICS: Task metrics, not in C8 audit logs
      // - EntityTypes.OPERATION_LOG: Operation log annotations, not in C8
      // - EntityTypes.FILTER: Task filters, not in C8
      // - EntityTypes.COMMENT: Task comments, not tracked in C8 audit logs
      // - EntityTypes.PROPERTY: Engine properties, not in C8

      default ->
          throw new EntityInterceptorException(
              HistoryMigratorLogs.UNSUPPORTED_AUDIT_LOG_ENTITY_TYPE + userOperationLog.getEntityType());
    };
  }
  protected void convertValueType(UserOperationLogEntry userOperationLog, AuditLogDbModel.Builder builder) {
      switch (userOperationLog.getEntityType()) {
        case EntityTypes.PROCESS_INSTANCE -> {
          switch (userOperationLog.getOperationType()) {
            case UserOperationLogEntry.OPERATION_TYPE_CREATE ->
                builder.entityValueType(ValueType.PROCESS_INSTANCE_CREATION.value());
            case UserOperationLogEntry.OPERATION_TYPE_MODIFY_PROCESS_INSTANCE ->
                builder.entityValueType(ValueType.PROCESS_INSTANCE_MODIFICATION.value());
            case UserOperationLogEntry.OPERATION_TYPE_MIGRATE ->
                builder.entityValueType(ValueType.PROCESS_INSTANCE_MIGRATION.value());
            default -> builder.entityValueType(ValueType.PROCESS_INSTANCE.value());
          }
        }
        case EntityTypes.VARIABLE -> builder.entityValueType(ValueType.VARIABLE.value());
        case EntityTypes.TASK -> builder.entityValueType(ValueType.USER_TASK.value());
        case EntityTypes.DECISION_INSTANCE, EntityTypes.DECISION_DEFINITION -> {
          if (userOperationLog.getOperationType().equals(UserOperationLogEntry.OPERATION_TYPE_EVALUATE)) {
            builder.entityValueType(ValueType.DECISION_EVALUATION.value());
          } else {
            builder.entityValueType(ValueType.DECISION.value());
          }
        }
        case EntityTypes.DECISION_REQUIREMENTS_DEFINITION ->
            builder.entityValueType(ValueType.DECISION_REQUIREMENTS.value());
        case EntityTypes.USER -> builder.entityValueType(ValueType.USER.value());
        case EntityTypes.GROUP -> builder.entityValueType(ValueType.GROUP.value());
        case EntityTypes.TENANT -> builder.entityValueType(ValueType.TENANT.value());
        case EntityTypes.AUTHORIZATION -> builder.entityValueType(ValueType.AUTHORIZATION.value());
        case EntityTypes.PROCESS_DEFINITION -> builder.entityValueType(ValueType.PROCESS.value());
        case EntityTypes.DEPLOYMENT -> builder.entityValueType(ValueType.RESOURCE.value());
        case EntityTypes.INCIDENT -> builder.entityValueType(ValueType.INCIDENT.value());
        default -> builder.entityValueType(ValueType.SBE_UNKNOWN.value());
      }
  }

  protected void convertOperationTypeAndUpdateEntityType(UserOperationLogEntry userOperationLog, AuditLogDbModel.Builder builder) {
    String operationType = userOperationLog.getOperationType();

    switch (operationType) {
      // Task operations
      case UserOperationLogEntry.OPERATION_TYPE_ASSIGN,
           UserOperationLogEntry.OPERATION_TYPE_CLAIM,
           UserOperationLogEntry.OPERATION_TYPE_DELEGATE ->
          builder.operationType(AuditLogEntity.AuditLogOperationType.ASSIGN);
      case UserOperationLogEntry.OPERATION_TYPE_COMPLETE ->
          builder.operationType(AuditLogEntity.AuditLogOperationType.COMPLETE);
      case UserOperationLogEntry.OPERATION_TYPE_SET_PRIORITY,
           UserOperationLogEntry.OPERATION_TYPE_SET_OWNER,
           UserOperationLogEntry.OPERATION_TYPE_UPDATE ->
          builder.operationType(AuditLogEntity.AuditLogOperationType.UPDATE);

      // ProcessInstance operations
      case UserOperationLogEntry.OPERATION_TYPE_CREATE ->
          builder.operationType(AuditLogEntity.AuditLogOperationType.CREATE);
      case UserOperationLogEntry.OPERATION_TYPE_DELETE -> {
        // ProcessInstance Delete maps to CANCEL, but other entity types map to DELETE
        if (EntityTypes.PROCESS_INSTANCE.equals(userOperationLog.getEntityType())) {
          builder.operationType(AuditLogEntity.AuditLogOperationType.CANCEL);
        } else {
          builder.operationType(AuditLogEntity.AuditLogOperationType.DELETE);
        }
      }
      case UserOperationLogEntry.OPERATION_TYPE_MODIFY_PROCESS_INSTANCE ->
          builder.operationType(AuditLogEntity.AuditLogOperationType.MODIFY);
      case UserOperationLogEntry.OPERATION_TYPE_MIGRATE ->
          builder.operationType(AuditLogEntity.AuditLogOperationType.MIGRATE);
      case UserOperationLogEntry.OPERATION_TYPE_DELETE_HISTORY, UserOperationLogEntry.OPERATION_TYPE_REMOVE_VARIABLE ->
          builder.operationType(AuditLogEntity.AuditLogOperationType.DELETE);

      // Variable operations
      case UserOperationLogEntry.OPERATION_TYPE_MODIFY_VARIABLE ->
          builder.operationType(AuditLogEntity.AuditLogOperationType.UPDATE);
    case UserOperationLogEntry.OPERATION_TYPE_SET_VARIABLE,
           UserOperationLogEntry.OPERATION_TYPE_SET_VARIABLES -> builder.operationType(AuditLogEntity.AuditLogOperationType.UPDATE)
               .entityType(AuditLogEntity.AuditLogEntityType.VARIABLE);

      // DecisionDefinition operations
      case UserOperationLogEntry.OPERATION_TYPE_EVALUATE ->
          builder.operationType(AuditLogEntity.AuditLogOperationType.EVALUATE);

      // Incident operations
      case UserOperationLogEntry.OPERATION_TYPE_RESOLVE -> {
        if (EntityTypes.PROCESS_INSTANCE.equals(userOperationLog.getEntityType())) {
          builder.operationType(AuditLogEntity.AuditLogOperationType.RESOLVE)
              .entityType(AuditLogEntity.AuditLogEntityType.INCIDENT);
        } else {
          builder.operationType(AuditLogEntity.AuditLogOperationType.UPDATE);
        }
      }

    default ->
        throw new EntityInterceptorException(HistoryMigratorLogs.UNSUPPORTED_AUDIT_LOG_OPERATION_TYPE + operationType);
    }
  }
}
