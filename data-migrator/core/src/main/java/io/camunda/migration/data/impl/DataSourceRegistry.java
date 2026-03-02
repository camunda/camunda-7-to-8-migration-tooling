/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.migration.data.config.property.DataSourceProperties;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.clients.C8Client;
import jakarta.annotation.PreDestroy;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for managing multiple datasources used by the migrator.
 * <p>
 * This encapsulates datasource creation and selection logic, avoiding
 * the complexity of multiple @Qualifier-annotated beans and conditional logic.
 * <p>
 * Usage: Inject this registry and call the appropriate getter method:
 * <pre>
 * {@code
 * @Autowired
 * protected DataSourceRegistry dataSourceRegistry;
 *
 * // Get datasources
 * DataSource c7 = dataSourceRegistry.getC7DataSource();
 * DataSource migrator = dataSourceRegistry.getMigratorDataSource();
 *
 * // Check if C8 is configured
 * if (dataSourceRegistry.hasC8DataSource()) {
 *     DataSource c8 = dataSourceRegistry.getC8DataSource().get();
 * }
 * }
 * </pre>
 */
@Component
public class DataSourceRegistry {

  protected static final Logger LOGGER = LoggerFactory.getLogger(C8Client.class);

  protected final HikariDataSource c7DataSource;
  protected final HikariDataSource c8DataSource;
  protected final DataSource migratorDataSource;
  protected final PlatformTransactionManager c7TransactionManager;
  protected final PlatformTransactionManager migratorTransactionManager;
  protected final TransactionTemplate migratorTransactionTemplate;
  protected Boolean isOracle19;

  public DataSourceRegistry(MigratorProperties properties) {
    this.c7DataSource = createC7DataSource(properties);
    this.c8DataSource = createC8DataSource(properties);
    this.migratorDataSource = selectMigratorDataSource();
    
    // C7 always gets its own transaction manager (used for reads only in production)
    this.c7TransactionManager = new DataSourceTransactionManager(c7DataSource);
    
    // Migrator transaction manager uses C8 datasource when configured, otherwise C7.
    // When C8 is configured, this transaction manager is also used for C8 operations
    // to ensure atomic transactions during history migration.
    this.migratorTransactionManager = new DataSourceTransactionManager(migratorDataSource);

    // Transaction template for programmatic transaction management
    this.migratorTransactionTemplate = new TransactionTemplate(migratorTransactionManager);
  }

  /**
   * Returns the Camunda 7 datasource for reading C7 engine data.
   */
  public DataSource getC7DataSource() {
    return c7DataSource;
  }

  /**
   * Returns the Camunda 7 transaction manager.
   */
  public PlatformTransactionManager getC7TxManager() {
    return c7TransactionManager;
  }

  /**
   * Returns the optional Camunda 8 datasource for history migration.
   */
  public Optional<DataSource> getC8DataSource() {
    return Optional.ofNullable(c8DataSource);
  }


  /**
   * Returns the datasource where the migrator schema is stored.
   * Prefers C8 datasource when configured for single-transaction atomicity.
   */
  public DataSource getMigratorDataSource() {
    return migratorDataSource;
  }

  /**
   * Returns the transaction manager for the migrator schema.
   * <p>
   * When C8 datasource is configured, this uses the C8 datasource to ensure
   * atomic transactions during history migration - C8 data writes and migrator
   * mapping writes will be committed or rolled back together.
   * <p>
   * When only C7 is configured, this uses the C7 datasource but is a separate
   * transaction manager instance to avoid interference with C7 reads.
   */
  public PlatformTransactionManager getMigratorTxManager() {
    return migratorTransactionManager;
  }

  /**
   * Returns a transaction template for programmatic transaction management.
   * <p>
   * Use this instead of {@code @Transactional} annotations for explicit
   * transaction control without relying on bean name references.
   * <p>
   * Example usage:
   * <pre>
   * {@code
   * dataSourceRegistry.getMigratorTxTemplate().executeWithoutResult(status -> {
   *     // transactional code here
   * });
   * }
   * </pre>
   */
  public TransactionTemplate getMigratorTxTemplate() {
    return migratorTransactionTemplate;
  }

  /**
   * Returns true if a C8 datasource is configured.
   */
  public boolean hasC8DataSource() {
    return c8DataSource != null;
  }

  /**
   * Closes all datasources on application shutdown.
   */
  @PreDestroy
  public void close() {
    if (c7DataSource != null && !c7DataSource.isClosed()) {
      c7DataSource.close();
    }
    if (c8DataSource != null && !c8DataSource.isClosed()) {
      c8DataSource.close();
    }
  }

  protected HikariDataSource createC7DataSource(MigratorProperties properties) {
    DataSourceProperties props = properties.getC7().getDataSource();
    props.setAutoCommit(false);
    if (props.getJdbcUrl() == null) {
      props.setJdbcUrl("jdbc:h2:mem:migrator");
    }
    return new HikariDataSource(props);
  }

  protected HikariDataSource createC8DataSource(MigratorProperties properties) {
    if (properties.getC8() == null || properties.getC8().getDataSource() == null) {
      return null;
    }
    DataSourceProperties props = properties.getC8().getDataSource();
    props.setAutoCommit(false);
    if (props.getJdbcUrl() == null) {
      props.setJdbcUrl("jdbc:h2:mem:migrator");
    }
    return new HikariDataSource(props);
  }

  protected DataSource selectMigratorDataSource() {
    // Prefer C8 datasource when configured for single-transaction atomicity
    return c8DataSource != null ? c8DataSource : c7DataSource;
  }

  /**
   * Checks if the current database is Oracle 19 or below.
   * Oracle 19 requires special handling for insertion.
   * Caches the result after first check.
   */
  public boolean isOracle19() {
    if (isOracle19 != null) {
      return isOracle19;
    }

    try {
      DataSource dataSource = getC8DataSource().get();
      try (var conn = dataSource.getConnection()) {
        var meta = conn.getMetaData();
        String productName = meta.getDatabaseProductName();

        if (productName != null && productName.toLowerCase().contains("oracle")) {
          int majorVersion = meta.getDatabaseMajorVersion();
          isOracle19 = majorVersion <= 19;
          LOGGER.debug("Detected Oracle database version: {}, treating as Oracle 19: {}", majorVersion, isOracle19);
          return isOracle19;
        }
      }
    } catch (SQLException e) {
      LOGGER.warn("Failed to detect database version, assuming not Oracle 19", e);
    }

    isOracle19 = false;
    return false;
  }

}

