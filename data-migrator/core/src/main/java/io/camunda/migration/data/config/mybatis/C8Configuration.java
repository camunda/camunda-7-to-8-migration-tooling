/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config.mybatis;

import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.read.service.AuditLogDbReader;
import io.camunda.db.rdbms.read.service.AuthorizationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationDbReader;
import io.camunda.db.rdbms.read.service.BatchOperationItemDbReader;
import io.camunda.db.rdbms.read.service.CorrelatedMessageSubscriptionDbReader;
import io.camunda.db.rdbms.read.service.DecisionDefinitionDbReader;
import io.camunda.db.rdbms.read.service.DecisionInstanceDbReader;
import io.camunda.db.rdbms.read.service.DecisionRequirementsDbReader;
import io.camunda.db.rdbms.read.service.FlowNodeInstanceDbReader;
import io.camunda.db.rdbms.read.service.FormDbReader;
import io.camunda.db.rdbms.read.service.GroupDbReader;
import io.camunda.db.rdbms.read.service.GroupMemberDbReader;
import io.camunda.db.rdbms.read.service.IncidentDbReader;
import io.camunda.db.rdbms.read.service.JobDbReader;
import io.camunda.db.rdbms.read.service.MappingRuleDbReader;
import io.camunda.db.rdbms.read.service.MessageSubscriptionDbReader;
import io.camunda.db.rdbms.read.service.ProcessDefinitionDbReader;
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
import io.camunda.db.rdbms.read.service.ClusterVariableDbReader;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.sql.AuthorizationMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.FormMapper;
import io.camunda.db.rdbms.sql.GroupMapper;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.sql.MappingRuleMapper;
import io.camunda.db.rdbms.sql.MessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.sql.RoleMapper;
import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.db.rdbms.sql.TenantMapper;
import io.camunda.db.rdbms.sql.UsageMetricMapper;
import io.camunda.db.rdbms.sql.UsageMetricTUMapper;
import io.camunda.db.rdbms.sql.UserMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.sql.ClusterVariableMapper;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics;
import io.camunda.migration.data.config.C8DataSourceConfigured;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.exception.MigratorException;
import io.camunda.migration.data.impl.logging.ConfigurationLogs;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Properties;
import javax.sql.DataSource;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(C8DataSourceConfigured.class)
public class C8Configuration extends AbstractConfiguration {

  @Autowired
  @Qualifier("c8DataSource")
  protected DataSource dataSource;

  @Bean
  @ConditionalOnProperty(prefix = MigratorProperties.PREFIX
      + ".c8.data-source", name = "auto-ddl", havingValue = "true")
  public MultiTenantSpringLiquibase createRdbmsExporterSchema(VendorDatabaseProperties vendorDatabaseProperties) {
    String userCharColumnSize = "";
    try {
      userCharColumnSize = String.valueOf(vendorDatabaseProperties.userCharColumnSize());
    } catch (Exception e) {
      throw new MigratorException(ConfigurationLogs.getC8RdbmsUserCharColumnSizeError(), e);
    }
    return createSchema(dataSource, configProperties.getC8().getDataSource().getTablePrefix(),
        "db/changelog/rdbms-exporter/changelog-master.xml", userCharColumnSize);
  }

  @Bean
  public DbVendorProvider dbVendorProvider() {
    String vendor = configProperties.getC8().getDataSource().getVendor();
    return new DbVendorProvider(vendor);
  }

  @Bean
  public VendorDatabaseProperties vendorDatabaseProperties(DbVendorProvider dbVendorProvider) throws Exception {
    String c8DbVendor = dbVendorProvider.getDatabaseId(dataSource);
    String c8File = "db/vendor-properties/" + c8DbVendor + ".properties";

    return new VendorDatabaseProperties(loadPropertiesFile(c8DbVendor, c8File));
  }

  @Bean
  public SqlSessionFactory c8SqlSessionFactory(VendorDatabaseProperties vendorDatabaseProperties, DbVendorProvider dbVendorProvider) throws Exception {
    String tablePrefix = this.configProperties.getC8().getDataSource().getTablePrefix();
    Properties properties = vendorDatabaseProperties.properties();
    return createSqlSessionFactory(dataSource, dbVendorProvider, properties, tablePrefix);
  }

  @Bean
  public MapperFactoryBean<JobMapper> jobMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, JobMapper.class);
  }

  @Bean
  public MapperFactoryBean<BatchOperationMapper> batchOperationMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, BatchOperationMapper.class);
  }

  @Bean
  public MapperFactoryBean<AuthorizationMapper> authorizationMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, AuthorizationMapper.class);
  }

  @Bean
  public MapperFactoryBean<AuditLogMapper> auditLogMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, AuditLogMapper.class);
  }

  @Bean
  public MapperFactoryBean<DecisionDefinitionMapper> decisionDefinitionMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, DecisionDefinitionMapper.class);
  }

  @Bean
  public MapperFactoryBean<DecisionInstanceMapper> decisionInstanceMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, DecisionInstanceMapper.class);
  }

  @Bean
  public MapperFactoryBean<DecisionRequirementsMapper> decisionRequirementsMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, DecisionRequirementsMapper.class);
  }

  @Bean
  public MapperFactoryBean<FlowNodeInstanceMapper> flowNodeInstanceMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, FlowNodeInstanceMapper.class);
  }

  @Bean
  public MapperFactoryBean<GroupMapper> groupInstanceMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, GroupMapper.class);
  }

  @Bean
  public MapperFactoryBean<IncidentMapper> incidentMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, IncidentMapper.class);
  }

  @Bean
  public MapperFactoryBean<ProcessInstanceMapper> processInstanceMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, ProcessInstanceMapper.class);
  }

  @Bean
  public MapperFactoryBean<ProcessDefinitionMapper> processDeploymentMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, ProcessDefinitionMapper.class);
  }

  @Bean
  public MapperFactoryBean<TenantMapper> tenantMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, TenantMapper.class);
  }

  @Bean
  public MapperFactoryBean<VariableMapper> variableMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, VariableMapper.class);
  }

  @Bean
  public MapperFactoryBean<ClusterVariableMapper> clusterVariableMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, ClusterVariableMapper.class);
  }

  @Bean
  public MapperFactoryBean<RoleMapper> roleMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, RoleMapper.class);
  }

  @Bean
  public MapperFactoryBean<UserMapper> userMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, UserMapper.class);
  }

  @Bean
  public MapperFactoryBean<UserTaskMapper> userTaskMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, UserTaskMapper.class);
  }

  @Bean
  public MapperFactoryBean<FormMapper> formMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, FormMapper.class);
  }

  @Bean
  public MapperFactoryBean<MappingRuleMapper> mappingMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, MappingRuleMapper.class);
  }

  @Bean
  public MapperFactoryBean<ExporterPositionMapper> exporterPosition(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, ExporterPositionMapper.class);
  }

  @Bean
  public MapperFactoryBean<PurgeMapper> purgeMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, PurgeMapper.class);
  }

  @Bean
  public MapperFactoryBean<SequenceFlowMapper> sequenceFlowMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, SequenceFlowMapper.class);
  }

  @Bean
  public MapperFactoryBean<MetricsRecorder> metricsRecorder(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, MetricsRecorder.class);
  }

  @Bean
  public MapperFactoryBean<UsageMetricMapper> usageMetricMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, UsageMetricMapper.class);
  }

  @Bean
  public MapperFactoryBean<UsageMetricTUMapper> usageMetricTUMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, UsageMetricTUMapper.class);
  }

  @Bean
  MapperFactoryBean<MessageSubscriptionMapper> messageSubscriptionMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, MessageSubscriptionMapper.class);
  }

  @Bean
  MapperFactoryBean<CorrelatedMessageSubscriptionMapper> correlatedMessageMapper(@Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory) {
    return createMapperFactoryBean(c8SqlSessionFactory, CorrelatedMessageSubscriptionMapper.class);
  }

  @Bean
  public MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  public RdbmsWriterMetrics rdbmsWriterMetrics(MeterRegistry meterRegistry) {
    return new RdbmsWriterMetrics(meterRegistry);
  }

  @Bean
  public VariableDbReader variableRdbmsReader(VariableMapper variableMapper) {
    return new VariableDbReader(variableMapper);
  }

  @Bean
  public ClusterVariableDbReader clusterVariableRdbmsReader(ClusterVariableMapper clusterVariableMapper) {
    return new ClusterVariableDbReader(clusterVariableMapper);
  }

  @Bean
  public AuthorizationDbReader authorizationReader(AuthorizationMapper authorizationMapper) {
    return new AuthorizationDbReader(authorizationMapper);
  }

  @Bean
  public AuditLogDbReader auditLogReader(AuditLogMapper auditLogMapper) {
    return new AuditLogDbReader(auditLogMapper);
  }

  @Bean
  public DecisionDefinitionDbReader decisionDefinitionReader(
      DecisionDefinitionMapper decisionDefinitionMapper) {
    return new DecisionDefinitionDbReader(decisionDefinitionMapper);
  }

  @Bean
  public DecisionInstanceDbReader decisionInstanceReader(
      DecisionInstanceMapper decisionInstanceMapper) {
    return new DecisionInstanceDbReader(decisionInstanceMapper);
  }

  @Bean
  public DecisionRequirementsDbReader decisionRequirementsReader(
      DecisionRequirementsMapper decisionRequirementsMapper) {
    return new DecisionRequirementsDbReader(decisionRequirementsMapper);
  }

  @Bean
  public FlowNodeInstanceDbReader flowNodeInstanceReader(
      FlowNodeInstanceMapper flowNodeInstanceMapper) {
    return new FlowNodeInstanceDbReader(flowNodeInstanceMapper);
  }

  @Bean
  public GroupDbReader groupReader(GroupMapper groupMapper) {
    return new GroupDbReader(groupMapper);
  }

  @Bean
  public IncidentDbReader incidentReader(IncidentMapper incidentMapper) {
    return new IncidentDbReader(incidentMapper);
  }

  @Bean
  public ProcessDefinitionDbReader processDeploymentRdbmsReader(
      ProcessDefinitionMapper processDefinitionMapper) {
    return new ProcessDefinitionDbReader(processDefinitionMapper);
  }

  @Bean
  public ProcessInstanceDbReader processRdbmsReader(
      ProcessInstanceMapper processInstanceMapper) {
    return new ProcessInstanceDbReader(processInstanceMapper);
  }

  @Bean
  public TenantDbReader tenantReader(TenantMapper tenantMapper) {
    return new TenantDbReader(tenantMapper);
  }

  @Bean
  public UserDbReader userRdbmsReader(UserMapper userTaskMapper) {
    return new UserDbReader(userTaskMapper);
  }

  @Bean
  public RoleDbReader roleRdbmsReader(RoleMapper roleMapper) {
    return new RoleDbReader(roleMapper);
  }

  @Bean
  public UserTaskDbReader userTaskRdbmsReader(UserTaskMapper userTaskMapper) {
    return new UserTaskDbReader(userTaskMapper);
  }

  @Bean
  public FormDbReader formRdbmsReader(FormMapper formMapper) {
    return new FormDbReader(formMapper);
  }

  @Bean
  public MappingRuleDbReader mappingRdbmsReader(MappingRuleMapper mappingMapper) {
    return new MappingRuleDbReader(mappingMapper);
  }

  @Bean
  public BatchOperationDbReader batchOperationReader(
      BatchOperationMapper batchOperationMapper) {
    return new BatchOperationDbReader(batchOperationMapper);
  }

  @Bean
  public BatchOperationItemDbReader batchOperationItemReader(
      BatchOperationMapper batchOperationMapper) {
    return new BatchOperationItemDbReader(batchOperationMapper);
  }

  @Bean
  public JobDbReader jobReader(JobMapper jobMapper) {
    return new JobDbReader(jobMapper);
  }

  @Bean
  public SequenceFlowDbReader sequenceFlowReader(SequenceFlowMapper sequenceFlowMapper) {
    return new SequenceFlowDbReader(sequenceFlowMapper);
  }

  @Bean
  public UsageMetricsDbReader usageMetricReader(UsageMetricMapper usageMetricMapper) {
    return new UsageMetricsDbReader(usageMetricMapper);
  }

  @Bean
  public UsageMetricTUDbReader usageMetricTUReader(UsageMetricTUMapper usageMetricTUMapper) {
    return new UsageMetricTUDbReader(usageMetricTUMapper);
  }

  @Bean
  public MessageSubscriptionDbReader messageSubscriptionDbReader(MessageSubscriptionMapper messageSubscriptionMapper) {
    return new MessageSubscriptionDbReader(messageSubscriptionMapper);
  }

  @Bean
  public CorrelatedMessageSubscriptionDbReader correlatedMessageReader(CorrelatedMessageSubscriptionMapper correlatedMessageMapper) {
    return new CorrelatedMessageSubscriptionDbReader(correlatedMessageMapper);
  }

  @Bean
  public GroupMemberDbReader groupMemberReader(GroupMapper groupMapper) {
    return new GroupMemberDbReader(groupMapper);
  }

  @Bean
  public RoleMemberDbReader roleMemberReader(RoleMapper roleMapper) {
    return new RoleMemberDbReader(roleMapper);
  }

  @Bean
  public TenantMemberDbReader tenantMemberReader(TenantMapper tenantMapper) {
    return new TenantMemberDbReader(tenantMapper);
  }


  @Bean
  public RdbmsWriterFactory rdbmsWriterFactory(
      @Qualifier("c8SqlSessionFactory") SqlSessionFactory c8SqlSessionFactory,
      ExporterPositionMapper exporterPositionMapper,
      VendorDatabaseProperties dbVendorProvider,
      AuditLogMapper auditLogMapper,
      DecisionInstanceMapper decisionInstanceMapper,
      FlowNodeInstanceMapper flowNodeInstanceMapper,
      IncidentMapper incidentMapper,
      ProcessInstanceMapper processInstanceMapper,
      PurgeMapper purgeMapper,
      UserTaskMapper userTaskMapper,
      VariableMapper variableMapper,
      RdbmsWriterMetrics rdbmsWriterMetrics,
      BatchOperationDbReader batchOperationReader,
      JobMapper jobMapper,
      SequenceFlowMapper sequenceFlowMapper,
      UsageMetricMapper usageMetricMapper,
      UsageMetricTUMapper usageMetricTUMapper,
      BatchOperationMapper batchOperationMapper,
      MessageSubscriptionMapper messageSubscriptionMapper,
      CorrelatedMessageSubscriptionMapper correlatedMessageMapper,
      ClusterVariableMapper clusterVariableMapper) {
    return new RdbmsWriterFactory(
        c8SqlSessionFactory,
        exporterPositionMapper,
        dbVendorProvider,
        auditLogMapper,
        decisionInstanceMapper,
        flowNodeInstanceMapper,
        incidentMapper,
        processInstanceMapper,
        purgeMapper,
        userTaskMapper,
        variableMapper,
        rdbmsWriterMetrics,
        batchOperationReader,
        jobMapper,
        sequenceFlowMapper,
        usageMetricMapper,
        usageMetricTUMapper,
        batchOperationMapper,
        messageSubscriptionMapper,
        correlatedMessageMapper,
        clusterVariableMapper);
  }

  @Bean
  public RdbmsService rdbmsService(
      RdbmsWriterFactory rdbmsWriterFactory,
      AuditLogDbReader auditLogReader,
      AuthorizationDbReader authorizationReader,
      DecisionDefinitionDbReader decisionDefinitionReader,
      DecisionInstanceDbReader decisionInstanceReader,
      DecisionRequirementsDbReader decisionRequirementsReader,
      FlowNodeInstanceDbReader flowNodeInstanceReader,
      GroupDbReader groupReader,
      GroupMemberDbReader groupMemberReader,
      IncidentDbReader incidentReader,
      ProcessDefinitionDbReader processDefinitionReader,
      ProcessInstanceDbReader processInstanceReader,
      VariableDbReader variableReader,
      ClusterVariableDbReader clusterVariableReader,
      RoleDbReader roleReader,
      RoleMemberDbReader roleMemberReader,
      TenantDbReader tenantReader,
      TenantMemberDbReader tenantMemberReader,
      UserDbReader userReader,
      UserTaskDbReader userTaskReader,
      FormDbReader formReader,
      MappingRuleDbReader mappingRuleReader,
      BatchOperationDbReader batchOperationReader,
      SequenceFlowDbReader sequenceFlowReader,
      BatchOperationItemDbReader batchOperationItemReader,
      JobDbReader jobReader,
      UsageMetricsDbReader usageMetricsReader,
      UsageMetricTUDbReader usageMetricTUDbReader,
      MessageSubscriptionDbReader messageSubscriptionDbReader,
      CorrelatedMessageSubscriptionDbReader correlatedMessageDbReader) {
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
        usageMetricsReader,
        usageMetricTUDbReader,
        messageSubscriptionDbReader,
        correlatedMessageDbReader);
  }

}
