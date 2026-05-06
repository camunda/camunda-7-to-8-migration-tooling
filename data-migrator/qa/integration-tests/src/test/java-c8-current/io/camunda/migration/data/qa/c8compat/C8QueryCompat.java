/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.c8compat;

import io.camunda.db.rdbms.read.domain.AuditLogAuthorizationFilter;
import io.camunda.db.rdbms.read.domain.AuditLogDbQuery;
import io.camunda.db.rdbms.read.service.AuditLogDbReader;
import io.camunda.db.rdbms.read.service.IncidentDbReader;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.migration.data.qa.extension.RdbmsQueryExtension;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import java.util.Arrays;
import java.util.List;

/**
 * Version-specific test query helpers for the current Camunda 8 version.
 *
 * <p>Lives under {@code src/test/java-c8-current} and is added to the test source set by the
 * {@code c8-current-api} Maven profile. A parallel implementation exists under
 * {@code src/test/java-c8-previous} for the previous Camunda 8 version.
 */
public final class C8QueryCompat {

  private C8QueryCompat() {}

  public static ProcessDefinitionQuery processDefinitionQueryWithBpmnXml(
      final String prefixedProcessDefinitionId) {
    return ProcessDefinitionQuery.of(
        queryBuilder ->
            queryBuilder
                .filter(filterBuilder -> filterBuilder.processDefinitionIds(prefixedProcessDefinitionId))
                .resultConfig(b -> b.includeXml(true)));
  }

  public static FlowNodeInstanceQuery flowNodeInstanceQueryByIds(final String... flowNodeIds) {
    if (flowNodeIds.length == 0) {
      throw new IllegalArgumentException("At least one flowNodeId is required");
    }
    final String first = flowNodeIds[0];
    final String[] rest = Arrays.copyOfRange(flowNodeIds, 1, flowNodeIds.length);
    return FlowNodeInstanceQuery.of(
        queryBuilder ->
            queryBuilder.filter(filterBuilder -> filterBuilder.flowNodeIds(first, rest)));
  }

  public static List<IncidentDbModel> searchHistoricIncidents(
      @SuppressWarnings("unused") IncidentDbReader incidentReader, RdbmsQueryExtension rdbmsQuery, String prefixedProcessDefinitionId) {
    String sql =
        "SELECT INCIDENT_KEY, PROCESS_DEFINITION_KEY, PROCESS_DEFINITION_ID," +
        " PROCESS_INSTANCE_KEY, ROOT_PROCESS_INSTANCE_KEY, FLOW_NODE_INSTANCE_KEY," +
        " FLOW_NODE_ID, JOB_KEY, ERROR_TYPE, ERROR_MESSAGE, ERROR_MESSAGE_HASH," +
        " STATE, CREATION_DATE, TREE_PATH, TENANT_ID, PARTITION_ID" +
        " FROM INCIDENT WHERE PROCESS_DEFINITION_ID = ?";
    return rdbmsQuery.query(sql, (rs, rowNum) -> {
      String errorTypeStr = rs.getString("ERROR_TYPE");
      String stateStr = rs.getString("STATE");
      java.sql.Timestamp ts = rs.getTimestamp("CREATION_DATE");
      return new IncidentDbModel.Builder()
          .incidentKey(rs.getObject("INCIDENT_KEY", Long.class))
          .processDefinitionKey(rs.getObject("PROCESS_DEFINITION_KEY", Long.class))
          .processDefinitionId(rs.getString("PROCESS_DEFINITION_ID"))
          .processInstanceKey(rs.getObject("PROCESS_INSTANCE_KEY", Long.class))
          .rootProcessInstanceKey(rs.getObject("ROOT_PROCESS_INSTANCE_KEY", Long.class))
          .flowNodeInstanceKey(rs.getObject("FLOW_NODE_INSTANCE_KEY", Long.class))
          .flowNodeId(rs.getString("FLOW_NODE_ID"))
          .jobKey(rs.getObject("JOB_KEY", Long.class))
          .errorType(errorTypeStr != null ? IncidentEntity.ErrorType.valueOf(errorTypeStr) : null)
          .errorMessage(rs.getString("ERROR_MESSAGE"))
          .errorMessageHash(rs.getObject("ERROR_MESSAGE_HASH", Integer.class))
          .state(stateStr != null ? IncidentEntity.IncidentState.valueOf(stateStr) : null)
          .creationDate(ts != null ? ts.toInstant().atOffset(java.time.ZoneOffset.UTC) : null)
          .treePath(rs.getString("TREE_PATH"))
          .tenantId(rs.getString("TENANT_ID"))
          .partitionId(rs.getInt("PARTITION_ID"))
          .build();
    }, prefixedProcessDefinitionId);
  }

  public static List<AuditLogDbModel> searchAuditLogs(
      AuditLogMapper auditLogMapper, @SuppressWarnings("unused") AuditLogDbReader auditLogReader,
      String prefixedProcessDefinitionId) {
    return auditLogMapper.search(AuditLogDbQuery.of(b -> b
        .authorizationFilter(AuditLogAuthorizationFilter.allowAll())
        .filter(f -> f.processDefinitionIds(prefixedProcessDefinitionId))));
  }

  public static List<AuditLogDbModel> searchAuditLogsByCategory(
      AuditLogMapper auditLogMapper, @SuppressWarnings("unused") AuditLogDbReader auditLogReader,
      String category) {
    return auditLogMapper.search(AuditLogDbQuery.of(b -> b
        .authorizationFilter(AuditLogAuthorizationFilter.allowAll())
        .filter(f -> f.categories(category))));
  }
}
