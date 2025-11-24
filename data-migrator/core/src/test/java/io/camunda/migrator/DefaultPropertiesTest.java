/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.config.property.MigratorProperties;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DefaultPropertiesTest {

  @Autowired
  protected MigratorProperties migratorProperties;

  @Autowired
  ProcessEngineConfigurationImpl processEngineConfiguration;

  @Test
  public void shouldHaveDefaultPageSize() {
    assertThat(migratorProperties.getPageSize()).isEqualTo(MigratorProperties.DEFAULT_PAGE_SIZE);
  }

  @Test
  public void shouldHaveDefaultTenants() {
    assertThat(migratorProperties.getTenantIds()).isEqualTo(null);
  }

  @Test
  public void shouldHaveDisabledJobExecutor() {
    assertThat(processEngineConfiguration.getJobExecutor().isActive()).isEqualTo(false);
  }

  @Test
  public void shouldHaveFalseDefaultSaveSkipReason() {
    assertThat(migratorProperties.getSaveSkipReason()).isFalse();

  }
}