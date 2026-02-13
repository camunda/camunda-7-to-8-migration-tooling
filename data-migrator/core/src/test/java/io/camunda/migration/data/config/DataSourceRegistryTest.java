/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.config.property.C7Properties;
import io.camunda.migration.data.config.property.C8Properties;
import io.camunda.migration.data.config.property.DataSourceProperties;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.DataSourceRegistry;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests for {@link DataSourceRegistry} to verify correct datasource and transaction manager
 * configuration for different scenarios.
 */
class DataSourceRegistryTest {

  private DataSourceRegistry registry;

  @AfterEach
  void tearDown() {
    if (registry != null) {
      registry.close();
    }
  }

  @Nested
  @DisplayName("When only C7 datasource is configured")
  class C7OnlyScenario {

    @Test
    @DisplayName("should use C7 datasource for migrator")
    void shouldUseC7DataSourceForMigrator() {
      // given
      MigratorProperties properties = createC7OnlyProperties();

      // when
      registry = new DataSourceRegistry(properties);

      // then
      assertThat(registry.hasC8DataSource()).isFalse();
      assertThat(registry.getC8DataSource()).isEmpty();
      assertThat(registry.getMigratorDataSource()).isSameAs(registry.getC7DataSource());
    }

    @Test
    @DisplayName("should have separate transaction managers for C7 and migrator")
    void shouldHaveSeparateTransactionManagers() {
      // given
      MigratorProperties properties = createC7OnlyProperties();

      // when
      registry = new DataSourceRegistry(properties);

      // then
      PlatformTransactionManager c7TxManager = registry.getC7TxManager();
      PlatformTransactionManager migratorTxManager = registry.getMigratorTxManager();

      // Separate instances to avoid interference between C7 reads and migrator writes
      assertThat(migratorTxManager).isNotSameAs(c7TxManager);

      // But both use the same underlying C7 datasource
      assertThat(getDataSource(c7TxManager)).isSameAs(registry.getC7DataSource());
      assertThat(getDataSource(migratorTxManager)).isSameAs(registry.getC7DataSource());
    }

    @Test
    @DisplayName("should provide transaction template using C7 datasource")
    void shouldProvideTransactionTemplateUsingC7DataSource() {
      // given
      MigratorProperties properties = createC7OnlyProperties();

      // when
      registry = new DataSourceRegistry(properties);

      // then
      TransactionTemplate txTemplate = registry.getMigratorTxTemplate();
      assertThat(txTemplate).isNotNull();
      assertThat(txTemplate.getTransactionManager()).isSameAs(registry.getMigratorTxManager());
    }
  }

  @Nested
  @DisplayName("When both C7 and C8 datasources are configured")
  class C7AndC8Scenario {

    @Test
    @DisplayName("should use C8 datasource for migrator")
    void shouldUseC8DataSourceForMigrator() {
      // given
      MigratorProperties properties = createC7AndC8Properties();

      // when
      registry = new DataSourceRegistry(properties);

      // then
      assertThat(registry.hasC8DataSource()).isTrue();
      assertThat(registry.getC8DataSource()).isPresent();
      assertThat(registry.getMigratorDataSource()).isSameAs(registry.getC8DataSource().get());
      assertThat(registry.getMigratorDataSource()).isNotSameAs(registry.getC7DataSource());
    }

    @Test
    @DisplayName("should have C7 transaction manager separate from migrator")
    void shouldHaveC7TransactionManagerSeparateFromMigrator() {
      // given
      MigratorProperties properties = createC7AndC8Properties();

      // when
      registry = new DataSourceRegistry(properties);

      // then
      PlatformTransactionManager c7TxManager = registry.getC7TxManager();
      PlatformTransactionManager migratorTxManager = registry.getMigratorTxManager();

      // C7 has its own transaction manager (used for reads only)
      assertThat(c7TxManager).isNotSameAs(migratorTxManager);
      assertThat(getDataSource(c7TxManager)).isSameAs(registry.getC7DataSource());
    }

    @Test
    @DisplayName("should use C8 datasource for migrator transaction manager")
    void shouldUseC8DataSourceForMigratorTransactionManager() {
      // given
      MigratorProperties properties = createC7AndC8Properties();

      // when
      registry = new DataSourceRegistry(properties);

      // then
      PlatformTransactionManager migratorTxManager = registry.getMigratorTxManager();

      // Migrator transaction manager uses C8 datasource for atomic history migration
      assertThat(getDataSource(migratorTxManager)).isSameAs(registry.getC8DataSource().get());
      assertThat(getDataSource(migratorTxManager)).isSameAs(registry.getMigratorDataSource());
    }

    @Test
    @DisplayName("should provide transaction template using C8 datasource for atomic history migration")
    void shouldProvideTransactionTemplateUsingC8DataSource() {
      // given
      MigratorProperties properties = createC7AndC8Properties();

      // when
      registry = new DataSourceRegistry(properties);

      // then
      TransactionTemplate txTemplate = registry.getMigratorTxTemplate();
      assertThat(txTemplate).isNotNull();
      assertThat(txTemplate.getTransactionManager()).isSameAs(registry.getMigratorTxManager());
      assertThat(getDataSource(txTemplate.getTransactionManager())).isSameAs(registry.getC8DataSource().get());
    }
  }

  @Nested
  @DisplayName("Transaction atomicity guarantees")
  class TransactionAtomicityGuarantees {

    @Test
    @DisplayName("C7-only: migrator uses dedicated transaction manager to avoid interference with C7 reads")
    void c7OnlyMigratorUsesDedicatedTransactionManager() {
      // given
      MigratorProperties properties = createC7OnlyProperties();

      // when
      registry = new DataSourceRegistry(properties);

      // then
      // Even though both use C7 datasource, they have separate transaction managers
      // This prevents C7 reads from being affected by migrator transaction state
      assertThat(registry.getC7TxManager())
          .isNotSameAs(registry.getMigratorTxManager())
          .isInstanceOf(DataSourceTransactionManager.class);
      assertThat(registry.getMigratorTxManager())
          .isInstanceOf(DataSourceTransactionManager.class);
    }

    @Test
    @DisplayName("C7+C8: migrator and C8 operations share same transaction for atomic rollback")
    void c7AndC8MigratorSharesTransactionWithC8() {
      // given
      MigratorProperties properties = createC7AndC8Properties();

      // when
      registry = new DataSourceRegistry(properties);

      // then
      // When C8 is configured, migrator transaction manager uses C8 datasource
      // This ensures C8 data writes and migrator mapping writes are in the same transaction
      // and will be rolled back together on error
      DataSource c8DataSource = registry.getC8DataSource().orElseThrow();
      DataSource migratorDataSource = registry.getMigratorDataSource();

      assertThat(migratorDataSource).isSameAs(c8DataSource);
      assertThat(getDataSource(registry.getMigratorTxManager())).isSameAs(c8DataSource);
    }
  }

  // Helper methods

  private MigratorProperties createC7OnlyProperties() {
    MigratorProperties properties = new MigratorProperties();

    C7Properties c7 = new C7Properties();
    DataSourceProperties c7DataSource = new DataSourceProperties();
    c7DataSource.setJdbcUrl("jdbc:h2:mem:c7-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    c7DataSource.setUsername("sa");
    c7DataSource.setPassword("");
    c7.setDataSource(c7DataSource);

    properties.setC7(c7);
    // C8 is not configured
    properties.setC8(null);

    return properties;
  }

  private MigratorProperties createC7AndC8Properties() {
    MigratorProperties properties = new MigratorProperties();

    C7Properties c7 = new C7Properties();
    DataSourceProperties c7DataSource = new DataSourceProperties();
    c7DataSource.setJdbcUrl("jdbc:h2:mem:c7-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    c7DataSource.setUsername("sa");
    c7DataSource.setPassword("");
    c7.setDataSource(c7DataSource);

    C8Properties c8 = new C8Properties();
    DataSourceProperties c8DataSource = new DataSourceProperties();
    c8DataSource.setJdbcUrl("jdbc:h2:mem:c8-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
    c8DataSource.setUsername("sa");
    c8DataSource.setPassword("");
    c8.setDataSource(c8DataSource);

    properties.setC7(c7);
    properties.setC8(c8);

    return properties;
  }

  private DataSource getDataSource(PlatformTransactionManager transactionManager) {
    assertThat(transactionManager).isInstanceOf(DataSourceTransactionManager.class);
    return ((DataSourceTransactionManager) transactionManager).getDataSource();
  }
}

