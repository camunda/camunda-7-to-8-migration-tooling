/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.c8compat;

import io.camunda.db.rdbms.read.service.AuditLogDbReader;
import io.camunda.db.rdbms.read.service.IncidentDbReader;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.migration.data.qa.extension.RdbmsQueryExtension;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import java.util.List;

/**
 * Version-specific test query helpers for the previous Camunda 8 version.
 *
 * <p>Lives under {@code src/test/java-c8-previous} and is added to the test source set by the
 * {@code c8-previous-api} Maven profile (activated when {@code -Dversion.camunda-8} matches the
 * previous version pin). A parallel implementation exists under {@code src/test/java-c8-current}
 * for the current Camunda 8 version.
 *
 * <p>In 8.9 {@code ProcessDefinitionQuery.Builder} has no {@code resultConfig} — BPMN XML is
 * included by default. {@code FlowNodeInstanceFilter.Builder.flowNodeIds} accepts a varargs
 * {@code String[]} directly.
 */
public final class C8QueryCompat {

  private C8QueryCompat() {}

  public static ProcessDefinitionQuery processDefinitionQueryWithBpmnXml(
      final String prefixedProcessDefinitionId) {
    return ProcessDefinitionQuery.of(
        queryBuilder ->
            queryBuilder.filter(
                filterBuilder -> filterBuilder.processDefinitionIds(prefixedProcessDefinitionId)));
  }

  public static FlowNodeInstanceQuery flowNodeInstanceQueryByIds(final String... flowNodeIds) {
    if (flowNodeIds.length == 0) {
      throw new IllegalArgumentException("At least one flowNodeId is required");
    }
    return FlowNodeInstanceQuery.of(
        queryBuilder ->
            queryBuilder.filter(filterBuilder -> filterBuilder.flowNodeIds(flowNodeIds)));
  }

  public static List<IncidentDbModel> searchHistoricIncidents(
      IncidentDbReader incidentReader, @SuppressWarnings("unused") RdbmsQueryExtension rdbmsQuery, String prefixedProcessDefinitionId) {
    return incidentReader
        .search(IncidentQuery.of(b -> b.filter(f -> f.processDefinitionIds(prefixedProcessDefinitionId))))
        .items()
        .stream()
        .map(e -> new IncidentDbModel.Builder()
            .incidentKey(e.incidentKey())
            .processDefinitionKey(e.processDefinitionKey())
            .processDefinitionId(e.processDefinitionId())
            .processInstanceKey(e.processInstanceKey())
            .rootProcessInstanceKey(e.rootProcessInstanceKey())
            .flowNodeInstanceKey(e.flowNodeInstanceKey())
            .flowNodeId(e.flowNodeId())
            .jobKey(e.jobKey())
            .errorType(e.errorType())
            .errorMessage(e.errorMessage())
            .state(e.state())
            .creationDate(e.creationTime())
            .tenantId(e.tenantId())
            .build())
        .toList();
  }

  public static List<AuditLogDbModel> searchAuditLogs(
      @SuppressWarnings("unused") AuditLogMapper auditLogMapper,
      AuditLogDbReader auditLogReader, String prefixedProcessDefinitionId) {
    return auditLogReader
        .search(AuditLogQuery.of(q -> q.filter(f -> f.processDefinitionIds(prefixedProcessDefinitionId))))
        .items().stream()
        .map(e -> new AuditLogDbModel.Builder()
            .auditLogKey(e.auditLogKey())
            .entityKey(e.entityKey())
            .entityType(e.entityType())
            .operationType(e.operationType())
            .batchOperationKey(e.batchOperationKey())
            .batchOperationType(e.batchOperationType())
            .timestamp(e.timestamp())
            .actorId(e.actorId())
            .actorType(e.actorType())
            .agentElementId(e.agentElementId())
            .tenantId(e.tenantId())
            .tenantScope(e.tenantScope())
            .result(e.result())
            .category(e.category())
            .processDefinitionId(e.processDefinitionId())
            .processDefinitionKey(e.processDefinitionKey())
            .processInstanceKey(e.processInstanceKey())
            .rootProcessInstanceKey(e.rootProcessInstanceKey())
            .elementInstanceKey(e.elementInstanceKey())
            .jobKey(e.jobKey())
            .userTaskKey(e.userTaskKey())
            .decisionRequirementsId(e.decisionRequirementsId())
            .decisionRequirementsKey(e.decisionRequirementsKey())
            .decisionDefinitionId(e.decisionDefinitionId())
            .decisionDefinitionKey(e.decisionDefinitionKey())
            .decisionEvaluationKey(e.decisionEvaluationKey())
            .deploymentKey(e.deploymentKey())
            .formKey(e.formKey())
            .resourceKey(e.resourceKey())
            .relatedEntityType(e.relatedEntityType())
            .relatedEntityKey(e.relatedEntityKey())
            .entityDescription(e.entityDescription())
            .build())
        .toList();
  }

  public static List<AuditLogDbModel> searchAuditLogsByCategory(
      @SuppressWarnings("unused") AuditLogMapper auditLogMapper,
      AuditLogDbReader auditLogReader, String category) {
    return auditLogReader
        .search(AuditLogQuery.of(q -> q.filter(f -> f.categories(category))))
        .items().stream()
        .map(e -> new AuditLogDbModel.Builder()
            .auditLogKey(e.auditLogKey())
            .entityKey(e.entityKey())
            .entityType(e.entityType())
            .operationType(e.operationType())
            .batchOperationKey(e.batchOperationKey())
            .batchOperationType(e.batchOperationType())
            .timestamp(e.timestamp())
            .actorId(e.actorId())
            .actorType(e.actorType())
            .agentElementId(e.agentElementId())
            .tenantId(e.tenantId())
            .tenantScope(e.tenantScope())
            .result(e.result())
            .category(e.category())
            .processDefinitionId(e.processDefinitionId())
            .processDefinitionKey(e.processDefinitionKey())
            .processInstanceKey(e.processInstanceKey())
            .rootProcessInstanceKey(e.rootProcessInstanceKey())
            .elementInstanceKey(e.elementInstanceKey())
            .jobKey(e.jobKey())
            .userTaskKey(e.userTaskKey())
            .decisionRequirementsId(e.decisionRequirementsId())
            .decisionRequirementsKey(e.decisionRequirementsKey())
            .decisionDefinitionId(e.decisionDefinitionId())
            .decisionDefinitionKey(e.decisionDefinitionKey())
            .decisionEvaluationKey(e.decisionEvaluationKey())
            .deploymentKey(e.deploymentKey())
            .formKey(e.formKey())
            .resourceKey(e.resourceKey())
            .relatedEntityType(e.relatedEntityType())
            .relatedEntityKey(e.relatedEntityKey())
            .entityDescription(e.entityDescription())
            .build())
        .toList();
  }
}
