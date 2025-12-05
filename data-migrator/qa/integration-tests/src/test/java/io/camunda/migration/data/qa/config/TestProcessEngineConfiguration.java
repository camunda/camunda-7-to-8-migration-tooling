/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.config;

import static org.camunda.bpm.engine.ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE;
import static org.camunda.bpm.engine.ProcessEngineConfiguration.HISTORY_FULL;

import io.camunda.migration.data.config.property.MigratorProperties;
import javax.sql.DataSource;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.camunda.spin.plugin.impl.SpinProcessEnginePlugin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@Profile("history-level-full")
public class TestProcessEngineConfiguration {

  @Bean
  @Primary
  public ProcessEngineConfigurationImpl processEngineConfiguration(
      DataSource c7DataSource,
      PlatformTransactionManager c7TransactionManager,
      MigratorProperties migratorProperties) {
    var config = new SpringProcessEngineConfiguration();
    config.setDataSource(c7DataSource);
    config.setTransactionManager(c7TransactionManager);
    config.setHistory(HISTORY_FULL);
    config.setJobExecutorActivate(false);
    config.setMetricsEnabled(false);

    String tablePrefix = migratorProperties.getC7().getDataSource().getTablePrefix();
    if (tablePrefix != null) {
      config.setDatabaseTablePrefix(tablePrefix);
    }
    config.getProcessEnginePlugins().add(new SpinProcessEnginePlugin());

    if (migratorProperties.getC7().getDataSource().getAutoDdl() != null
        && migratorProperties.getC7().getDataSource().getAutoDdl()) {
      config.setDatabaseSchemaUpdate(DB_SCHEMA_UPDATE_TRUE);
    }

    return config;
  }
}
