/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config;

import static io.camunda.migration.data.config.property.MigratorProperties.DataSource.C7;
import static io.camunda.migration.data.config.property.MigratorProperties.DataSource.C8;
import static org.camunda.bpm.engine.ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE;
import static org.camunda.bpm.engine.ProcessEngineConfiguration.HISTORY_AUTO;

import io.camunda.migration.data.IdentityMigrator;
import io.camunda.migration.data.impl.SchemaShutdownCleaner;
import io.camunda.migration.data.impl.AutoDeployer;
import com.zaxxer.hikari.HikariDataSource;
import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.config.mybatis.C8Configuration;
import io.camunda.migration.data.config.mybatis.MigratorConfiguration;
import io.camunda.migration.data.config.property.DataSourceProperties;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.converter.ConverterConfiguration;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.impl.clients.C8Client;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.VariableService;
import io.camunda.migration.data.impl.RuntimeValidator;
import java.util.Optional;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.spring.ProcessEngineFactoryBean;
import org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.camunda.bpm.engine.spring.SpringProcessEngineServicesConfiguration;
import org.camunda.spin.plugin.impl.SpinProcessEnginePlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Import({
    SpringProcessEngineServicesConfiguration.class,
    JacksonConfiguration.class,
    C8Configuration.class,
    MigratorConfiguration.class,
    ConverterConfiguration.class,
    InterceptorConfiguration.class,
    AutoDeployer.class,
    C7Client.class,
    C8Client.class,
    DbClient.class,
    VariableService.class,
    RuntimeValidator.class,
    HistoryMigrator.class,
    RuntimeMigrator.class,
    IdentityMigrator.class,
    SchemaShutdownCleaner.class
})
@Configuration
@EnableConfigurationProperties(MigratorProperties.class)
public class MigratorAutoConfiguration {

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

  @Configuration
  static class DataSourcesConfiguration {

    protected final MigratorProperties migratorProperties;

    DataSourcesConfiguration(MigratorProperties migratorProperties) {
      this.migratorProperties = migratorProperties;
    }

    @Bean
    @Primary
    public DataSource c7DataSource() {
      DataSourceProperties props = migratorProperties.getC7().getDataSource();
      if (props.getJdbcUrl() == null) {
        return createDefaultDataSource(props);
      }
      return new HikariDataSource(props);
    }

    @Bean
    public PlatformTransactionManager c7TransactionManager(DataSource c7DataSource) {
      return new DataSourceTransactionManager(c7DataSource);
    }

    @Bean
    @Conditional(C8DataSourceConfigured.class)
    public DataSource c8DataSource() {
      DataSourceProperties props = migratorProperties.getC8().getDataSource();
      if (props.getJdbcUrl() == null) {
        return createDefaultDataSource(props);
      }
      return new HikariDataSource(props);
    }

    @Bean
    public DataSource migratorDataSource(@Qualifier("c7DataSource") DataSource c7DataSource,
                                         @Qualifier("c8DataSource") Optional<DataSource> c8DataSource) {
      if (C7.equals(migratorProperties.getDataSource())) {
        return c7DataSource;

      } else if (C8.equals(migratorProperties.getDataSource())) {
        if (c8DataSource.isPresent()) {
          return c8DataSource.get();
        }
      }

      return null;
    }

    protected HikariDataSource createDefaultDataSource(DataSourceProperties props) {
      props.setJdbcUrl("jdbc:h2:mem:migrator");
      return new HikariDataSource(props);
    }
  }

  @Autowired
  protected DataSource c7DataSource;

  @Autowired
  protected PlatformTransactionManager c7TransactionManager;

  @Bean
  @ConditionalOnMissingBean(ProcessEngineConfigurationImpl.class)
  public ProcessEngineConfigurationImpl processEngineConfiguration() {
    var config = new SpringProcessEngineConfiguration();
    config.setDataSource(c7DataSource);
    config.setTransactionManager(c7TransactionManager);
    config.setHistory(HISTORY_AUTO);
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

  @Configuration
  static class PecConfiguration {

    @Autowired
    protected ProcessEngineConfigurationImpl processEngineConfiguration;

    @Bean
    @DependsOn({"migratorDataSource", "c7DataSource"})
    public ProcessEngineFactoryBean processEngineFactoryBean() {
      final ProcessEngineFactoryBean factoryBean = new ProcessEngineFactoryBean();
      factoryBean.setProcessEngineConfiguration(processEngineConfiguration);

      return factoryBean;
    }

  }

}
