/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.interceptor.history.entity;

import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
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

    builder.key(getNextKey())
        .timestamp(convertDate(userOperationLog.getTimestamp()))
        .userId(userOperationLog.getUserId())
        .operationType(userOperationLog.getOperationType())
        .entityType(userOperationLog.getEntityType())
        .category(userOperationLog.getCategory())
        .property(userOperationLog.getProperty())
        .orgValue(userOperationLog.getOrgValue())
        .newValue(userOperationLog.getNewValue())
        .processDefinitionId(userOperationLog.getProcessDefinitionId())
        .processDefinitionKey(userOperationLog.getProcessDefinitionKey())
        .processInstanceId(userOperationLog.getProcessInstanceId())
        .executionId(userOperationLog.getExecutionId())
        .caseDefinitionId(userOperationLog.getCaseDefinitionId())
        .caseInstanceId(userOperationLog.getCaseInstanceId())
        .caseExecutionId(userOperationLog.getCaseExecutionId())
        .taskId(userOperationLog.getTaskId())
        .externalTaskId(userOperationLog.getExternalTaskId())
        .batchId(userOperationLog.getBatchId())
        .jobId(userOperationLog.getJobId())
        .jobDefinitionId(userOperationLog.getJobDefinitionId())
        .deploymentId(userOperationLog.getDeploymentId())
        .annotation(userOperationLog.getAnnotation())
        .tenantId(userOperationLog.getTenantId())
        .removalTime(convertDate(userOperationLog.getRemovalTime()))
        .rootProcessInstanceId(userOperationLog.getRootProcessInstanceId());
  }
}
