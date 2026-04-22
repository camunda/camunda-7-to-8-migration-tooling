/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.backend.previous;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.AuditLogDbReader;
import io.camunda.db.rdbms.read.service.AuthorizationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationItemDbReader;
import io.camunda.db.rdbms.read.service.ClusterVariableDbReader;
import io.camunda.db.rdbms.read.service.CorrelatedMessageSubscriptionDbReader;
import io.camunda.db.rdbms.read.service.DecisionDefinitionDbReader;
import io.camunda.db.rdbms.read.service.DecisionInstanceDbReader;
import io.camunda.db.rdbms.read.service.DecisionRequirementsDbReader;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceDbReader;
import io.camunda.db.rdbms.read.service.FormDbReader;
import io.camunda.db.rdbms.read.service.GlobalListenerDbReader;
import io.camunda.db.rdbms.read.service.GroupDbReader;
import io.camunda.db.rdbms.read.service.GroupMemberDbReader;
import io.camunda.db.rdbms.read.service.HistoryDeletionDbReader;
import io.camunda.db.rdbms.read.service.IncidentDbReader;
import io.camunda.db.rdbms.read.service.IncidentProcessInstanceStatisticsByDefinitionDbReader;
import io.camunda.db.rdbms.read.service.IncidentProcessInstanceStatisticsByErrorDbReader;
import io.camunda.db.rdbms.read.service.JobDbReader;
import io.camunda.db.rdbms.read.service.JobMetricsBatchDbReader;
import io.camunda.db.rdbms.read.service.MappingRuleDbReader;
import io.camunda.db.rdbms.read.service.MessageSubscriptionDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionInstanceStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionInstanceVersionStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionMessageSubscriptionStatisticsDbReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.read.service.RoleDbReader;
import io.camunda.db.rdbms.read.service.RoleMemberDbReader;
import io.camunda.db.rdbms.read.service.SequenceFlowDbReader;
import io.camunda.db.rdbms.read.service.TenantDbReader;
import io.camunda.db.rdbms.read.service.TenantMemberDbReader;
import io.camunda.db.rdbms.read.service.UsageMetricTUDbReader;
import io.camunda.db.rdbms.read.service.UsageMetricsDbReader;
import io.camunda.db.rdbms.read.service.UserDbReader;
import io.camunda.db.rdbms.read.service.UserTaskDbReader;
import io.camunda.db.rdbms.read.service.VariableDbReader;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import io.camunda.migration.data.backend.C8Backend;
import io.camunda.migration.data.config.C8DataSourceConfigured;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the version-specific {@code RdbmsService} @Bean for the "previous" C8 minor
 * (8.9). The 8.9 constructor has no {@code DeployedResourceDbReader} parameter; the
 * sibling {@code c8-backend-current} module is the one that wires that reader plus
 * the 8.10 {@code RdbmsService} shape.
 */
@Configuration
@Conditional(C8DataSourceConfigured.class)
public class C8BackendPreviousConfiguration implements C8Backend {

  @Override
  public String supportedVersion() {
    return "8.9";
  }

  @Bean
  public C8Backend c8Backend() {
    return this;
  }

  @Bean
  public RdbmsService rdbmsService(
      final RdbmsWriterFactory rdbmsWriterFactory,
      final AuditLogDbReader auditLogReader,
      final AuthorizationDbReader authorizationReader,
      final DecisionDefinitionDbReader decisionDefinitionReader,
      final DecisionInstanceDbReader decisionInstanceReader,
      final DecisionRequirementsDbReader decisionRequirementsReader,
      final FlowNodeInstanceDbReader flowNodeInstanceReader,
      final GroupDbReader groupReader,
      final GroupMemberDbReader groupMemberReader,
      final IncidentDbReader incidentReader,
      final ProcessDefinitionDbReader processDefinitionReader,
      final ProcessInstanceDbReader processInstanceReader,
      final VariableDbReader variableReader,
      final ClusterVariableDbReader clusterVariableReader,
      final RoleDbReader roleReader,
      final RoleMemberDbReader roleMemberReader,
      final TenantDbReader tenantReader,
      final TenantMemberDbReader tenantMemberReader,
      final UserDbReader userReader,
      final UserTaskDbReader userTaskReader,
      final FormDbReader formReader,
      final MappingRuleDbReader mappingRuleReader,
      final BatchOperationDbReader batchOperationReader,
      final SequenceFlowDbReader sequenceFlowReader,
      final BatchOperationItemDbReader batchOperationItemReader,
      final JobDbReader jobReader,
      final JobMetricsBatchDbReader jobMetricsBatchDbReader,
      final UsageMetricsDbReader usageMetricsReader,
      final UsageMetricTUDbReader usageMetricTUDbReader,
      final MessageSubscriptionDbReader messageSubscriptionDbReader,
      final ProcessDefinitionMessageSubscriptionStatisticsDbReader
          processDefinitionMessageSubscriptionStatisticsDbReader,
      final CorrelatedMessageSubscriptionDbReader correlatedMessageDbReader,
      final ProcessDefinitionInstanceStatisticsDbReader
          processDefinitionInstanceStatisticsDbReader,
      final ProcessDefinitionInstanceVersionStatisticsDbReader
          processDefinitionInstanceVersionStatisticsDbReader,
      final HistoryDeletionDbReader historyDeletionReader,
      final IncidentProcessInstanceStatisticsByErrorDbReader
          incidentProcessInstanceStatisticsByErrorDbReader,
      final IncidentProcessInstanceStatisticsByDefinitionDbReader
          incidentProcessInstanceStatisticsByDefinitionDbReader,
      final GlobalListenerDbReader globalListenerReader) {
    return new RdbmsService(
        rdbmsWriterFactory,
        auditLogReader,
        authorizationReader,
        decisionDefinitionReader,
        decisionInstanceReader,
        decisionRequirementsReader,
        flowNodeInstanceReader,
        groupReader,
        groupMemberReader,
        incidentReader,
        processDefinitionReader,
        processInstanceReader,
        variableReader,
        clusterVariableReader,
        roleReader,
        roleMemberReader,
        tenantReader,
        tenantMemberReader,
        userReader,
        userTaskReader,
        formReader,
        mappingRuleReader,
        batchOperationReader,
        sequenceFlowReader,
        batchOperationItemReader,
        jobReader,
        jobMetricsBatchDbReader,
        usageMetricsReader,
        usageMetricTUDbReader,
        messageSubscriptionDbReader,
        processDefinitionMessageSubscriptionStatisticsDbReader,
        correlatedMessageDbReader,
        processDefinitionInstanceStatisticsDbReader,
        processDefinitionInstanceVersionStatisticsDbReader,
        historyDeletionReader,
        incidentProcessInstanceStatisticsByErrorDbReader,
        incidentProcessInstanceStatisticsByDefinitionDbReader,
        globalListenerReader);
  }
}
