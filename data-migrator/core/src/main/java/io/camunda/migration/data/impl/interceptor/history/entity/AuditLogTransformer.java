/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C7_HISTORY_PARTITION_ID;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import java.util.Set;
import org.camunda.bpm.engine.ActivityTypes;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
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
    builder
        .partitionId(C7_HISTORY_PARTITION_ID)
        .timestamp(convertDate(userOperationLog.getTimestamp()))
        .actorId(userOperationLog.getUserId())
        .processDefinitionId(prefixDefinitionId(userOperationLog.getProcessDefinitionKey()))
        .annotation(userOperationLog.getAnnotation())
        .tenantId(userOperationLog.getTenantId())
        .category(convertCategory(userOperationLog.getCategory()))
        .historyCleanupDate(convertDate(userOperationLog.getRemovalTime()));

    // Note: auditLogKey, processInstanceKey, and processDefinitionKey are set externally in the migrator
  }

  protected AuditLogEntity.AuditLogOperationCategory convertCategory(String category) {
    return switch (category) {
      case UserOperationLogEntry.CATEGORY_ADMIN -> AuditLogEntity.AuditLogOperationCategory.ADMIN;
      case UserOperationLogEntry.CATEGORY_OPERATOR -> AuditLogEntity.AuditLogOperationCategory.DEPLOYED_RESOURCES;
      case UserOperationLogEntry.CATEGORY_TASK_WORKER -> AuditLogEntity.AuditLogOperationCategory.USER_TASKS;
      default -> AuditLogEntity.AuditLogOperationCategory.UNKNOWN;
    };
  }
}
