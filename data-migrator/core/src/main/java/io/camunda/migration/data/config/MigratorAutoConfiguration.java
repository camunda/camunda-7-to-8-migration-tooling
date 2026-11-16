/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config;

import static org.camunda.bpm.engine.ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE;
import static org.camunda.bpm.engine.ProcessEngineConfiguration.HISTORY_AUTO;

import io.camunda.migration.data.IdentityMigrator;
import io.camunda.migration.data.impl.DataSourceRegistry;
import io.camunda.migration.data.impl.EntityConversionService;
import io.camunda.migration.data.impl.SchemaShutdownCleaner;
import io.camunda.migration.data.impl.AutoDeployer;
import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.config.mybatis.C8Configuration;
import io.camunda.migration.data.config.mybatis.MigratorConfiguration;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.impl.clients.C8Client;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.VariableService;
import io.camunda.migration.data.impl.RuntimeValidator;
import io.camunda.migration.data.impl.history.migrator.DecisionDefinitionMigrator;
import io.camunda.migration.data.impl.history.migrator.DecisionInstanceMigrator;
import io.camunda.migration.data.impl.history.migrator.DecisionRequirementsMigrator;
import io.camunda.migration.data.impl.history.migrator.FormMigrator;
import io.camunda.migration.data.impl.history.migrator.JobMigrator;
import io.camunda.migration.data.impl.history.migrator.ProcessDefinitionMigrator;
import io.camunda.migration.data.impl.history.migrator.AuditLogMigrator;
import io.camunda.migration.data.impl.identity.AuthorizationManager;
import io.camunda.migration.data.impl.history.migrator.ExternalTaskMigrator;
import io.camunda.migration.data.impl.history.migrator.FlowNodeMigrator;
import io.camunda.migration.data.impl.history.migrator.IncidentMigrator;
import io.camunda.migration.data.impl.history.migrator.ProcessInstanceMigrator;
import io.camunda.migration.data.impl.history.migrator.UserTaskMigrator;
import io.camunda.migration.data.impl.history.migrator.VariableMigrator;

import io.camunda.migration.data.impl.identity.DefinitionLookupService;
import io.camunda.migration.data.impl.history.PartitionSupplier;
import io.camunda.migration.data.impl.identity.IdentitySync;
import io.camunda.migration.data.impl.util.LegacyIdPrefixResolver;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import liquibase.integration.spring.SpringLiquibase;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.spring.ProcessEngineFactoryBean;
import org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.camunda.bpm.engine.spring.SpringProcessEngineServicesConfiguration;
import org.camunda.spin.plugin.impl.SpinProcessEnginePlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Import({
    SpringProcessEngineServicesConfiguration.class,
    JacksonConfiguration.class,
    C8Configuration.class,
    MigratorConfiguration.class,
    InterceptorConfiguration.class,
    AutoDeployer.class,
    C7Client.class,
    C8Client.class,
    DbClient.class,
    VariableService.class,
    EntityConversionService.class,
    RuntimeValidator.class,
    HistoryMigrator.class,
    RuntimeMigrator.class,
    IdentityMigrator.class,
    AuthorizationManager.class,
    DefinitionLookupService.class,
    DecisionDefinitionMigrator.class,
    DecisionInstanceMigrator.class,
    DecisionRequirementsMigrator.class,
    FlowNodeMigrator.class,
    JobMigrator.class,
    ExternalTaskMigrator.class,
    IncidentMigrator.class,
    FormMigrator.class,
    ProcessDefinitionMigrator.class,
    ProcessInstanceMigrator.class,
    UserTaskMigrator.class,
    VariableMigrator.class,
    AuditLogMigrator.class,
    SchemaShutdownCleaner.class,
    PartitionSupplier.class,
    DataSourceRegistry.class,
    LegacyIdPrefixResolver.class,
    IdentitySync.class
})
@Configuration
@EnableConfigurationProperties(MigratorProperties.class)
public class MigratorAutoConfiguration {

  protected static final String CAMUNDA_COMMONS_RESOURCE =
      "org/camunda/bpm/engine/impl/mapping/entity/Commons.xml";
  protected static final String MIGRATOR_COMMONS_RESOURCE =
      "io/camunda/migration/data/impl/mybatis/Commons.xml";

  protected final MigratorProperties migratorProperties;

  public MigratorAutoConfiguration(MigratorProperties migratorProperties) {
    this.migratorProperties = migratorProperties;
  }

  @Bean
  public SpringLiquibase liquibase() {
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setShouldRun(false);
    return liquibase;
  }

  @Bean
  @ConditionalOnMissingBean(ProcessEngineConfigurationImpl.class)
  public ProcessEngineConfigurationImpl processEngineConfiguration(DataSourceRegistry registry) {
    var config = new SpringProcessEngineConfiguration();

    config.setDataSource(registry.getC7DataSource());
    config.setTransactionManager(registry.getC7TxManager());

    config.setHistory(HISTORY_AUTO);
    config.setHistoryLevel(HistoryLevel.HISTORY_LEVEL_FULL);

    config.setJobExecutorActivate(false);
    config.setMetricsEnabled(false);

    String tablePrefix = migratorProperties.getC7().getDataSource().getTablePrefix();
    if (tablePrefix != null) {
      config.setDatabaseTablePrefix(tablePrefix);
    }
    config.getProcessEnginePlugins().add(new SpinProcessEnginePlugin());

    if (migratorProperties.getC7().getDataSource().getAutoDdl() != null && migratorProperties.getC7().getDataSource().getAutoDdl()) {
      config.setDatabaseSchemaUpdate(DB_SCHEMA_UPDATE_TRUE);
    }

    return config;
  }

  protected static void configureMyBatisClassLoader(ProcessEngineConfigurationImpl config) {
    ClassLoader parentClassLoader = config.getClassLoader();
    if (parentClassLoader == null) {
      parentClassLoader = Thread.currentThread().getContextClassLoader();
    }
    if (parentClassLoader == null) {
      parentClassLoader = MigratorAutoConfiguration.class.getClassLoader();
    }
    config.setClassLoader(createMyBatisClassLoader(parentClassLoader));
  }

  protected static ClassLoader createMyBatisClassLoader(ClassLoader parentClassLoader) {
    URL patchedCommonsResource =
        MigratorAutoConfiguration.class.getClassLoader().getResource(MIGRATOR_COMMONS_RESOURCE);
    if (patchedCommonsResource == null) {
      throw new IllegalStateException("Missing patched Camunda Commons mapper: " + MIGRATOR_COMMONS_RESOURCE);
    }

    return new ClassLoader(parentClassLoader) {
      @Override
      public URL getResource(String name) {
        if (CAMUNDA_COMMONS_RESOURCE.equals(name)) {
          return patchedCommonsResource;
        }
        return super.getResource(name);
      }

      @Override
      public Enumeration<URL> getResources(String name) throws IOException {
        if (CAMUNDA_COMMONS_RESOURCE.equals(name)) {
          return Collections.enumeration(List.of(patchedCommonsResource));
        }
        return super.getResources(name);
      }
    };
  }

  @Configuration
  static class PecConfiguration {

    @Autowired
    protected ProcessEngineConfigurationImpl processEngineConfiguration;

    @Bean
    public ProcessEngineFactoryBean processEngineFactoryBean() {
      configureMyBatisClassLoader(processEngineConfiguration);

      final ProcessEngineFactoryBean factoryBean = new ProcessEngineFactoryBean() {
        @Override
        public ProcessEngine getObject() throws Exception {
          ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
          ClassLoader processEngineClassLoader = processEngineConfiguration.getClassLoader();
          try {
            if (processEngineClassLoader != null) {
              Thread.currentThread().setContextClassLoader(processEngineClassLoader);
            }
            return super.getObject();
          } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
          }
        }
      };
      factoryBean.setProcessEngineConfiguration(processEngineConfiguration);

      return factoryBean;
    }

  }

}
