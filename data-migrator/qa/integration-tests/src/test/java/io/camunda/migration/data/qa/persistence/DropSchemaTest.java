/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.qa.MigrationTestApplication;
import io.camunda.migration.date.util.WithMultiDb;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.camunda.bpm.engine.RepositoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@WithMultiDb
public class DropSchemaTest {

  protected static final String MIGRATION_MAPPING_TABLE = "MIGRATION_MAPPING";
  protected SpringApplicationBuilder springApplication;
  protected DataSource durableDataSource = null;
  protected String prefix = "";

  @BeforeEach
  void setup() {
    this.springApplication = new SpringApplicationBuilder(MigrationTestApplication.class);
    springApplication.profiles("drop-schema", "history-level-full");
  }

  @AfterEach
  void tearDown() {
    var context = new SpringApplicationBuilder(MigrationTestApplication.class).run();
    var processEngine = context.getBean(org.camunda.bpm.engine.ProcessEngine.class);
    RepositoryService repositoryService = processEngine.getRepositoryService();
    repositoryService.createDeploymentQuery().list()
        .forEach(d -> repositoryService.deleteDeployment(d.getId(), true));
    context.close();
    if (durableDataSource != null) {
      try {
        clearMigrationMappingTable(durableDataSource, prefix);
      } catch (RuntimeException ignored) {
      }
    }
    prefix = "";
  }

  @Test
  void shouldMigrationSchemaBeKeptOnShutdown() throws Exception {
    // given spring application is running with drop-schema flag disabled
    var context = springApplication.run();
    try {
      durableDataSource = createDurableDataSource(context);
      assertThat(tableExists(durableDataSource, MIGRATION_MAPPING_TABLE))
          .as("Migration mapping table does not exist")
          .isTrue();

      // when application is shut down
      context.close();

      // then migration schema is kept
      assertThat(tableExists(durableDataSource, MIGRATION_MAPPING_TABLE)).isTrue();
    } finally {
      if (context.isActive()) {
        context.close();
      }
    }
  }

  @Test
  void shouldMigrationSchemaWithPrefixBeDroppedOnShutdown() throws Exception {
    // given spring application is running with drop-schema flag enabled
    prefix = "FOO_";
    var context = springApplication.properties(Map.of("camunda.migrator.table-prefix", prefix)).run("--drop-schema");
    try {
      durableDataSource = createDurableDataSource(context);
      assertThat(tableExists(durableDataSource, prefix + MIGRATION_MAPPING_TABLE))
          .as("Migration mapping table does not exist")
          .isTrue();

      // when application is shut down
      context.close();

      // then migration schema is dropped
      assertThat(tableExists(durableDataSource, prefix + MIGRATION_MAPPING_TABLE)).isFalse();
    } finally {
      if (context.isActive()) {
        context.close();
      }
    }
  }

  @Test
  void shouldMigrationSchemaBeKeptOnSkippedEntities() throws Exception {
    // given spring application is running with drop-schema flag enabled
    var context = springApplication.run("--drop-schema");
    try {
      durableDataSource = createDurableDataSource(context);
      assertThat(tableExists(durableDataSource, MIGRATION_MAPPING_TABLE))
          .as("Migration mapping table does not exist")
          .isTrue();

      // Create real-world skip scenario: Deploy and start process, but migrate instances without process definition
      // This causes process instances to be skipped with a real-world scenario
      var processEngine = context.getBean(org.camunda.bpm.engine.ProcessEngine.class);
      RepositoryService repositoryService = processEngine.getRepositoryService();
      var runtimeService = processEngine.getRuntimeService();

      repositoryService.createDeployment()
          .addClasspathResource("io/camunda/migrator/bpmn/c7/simpleProcess.bpmn")
          .deploy();
      runtimeService.startProcessInstanceByKey("simpleProcess");

      // Migrate instances without definitions - causes real-world skip
      var historyMigrator = context.getBean(HistoryMigrator.class);
      historyMigrator.migrateProcessInstances();

      // when application is shut down
      context.close();

      // then migration schema is kept (due to skipped entities)
      assertThat(tableExists(durableDataSource, MIGRATION_MAPPING_TABLE)).isTrue();
    } finally {
      if (context.isActive()) {
        context.close();
      }
    }
  }

  @Test
  void shouldMigrationSchemaBeDroppedOnSkippedEntitiesWithForceFlag() throws Exception {
    // given spring application is running with drop-schema and force flags enabled
    prefix = "BAR_";
    var context = springApplication.properties(Map.of("camunda.migrator.table-prefix", prefix)).run("--drop-schema", "--force");
    try {
      durableDataSource = createDurableDataSource(context);
      assertThat(tableExists(durableDataSource, prefix + MIGRATION_MAPPING_TABLE))
          .as("Migration mapping table does not exist")
          .isTrue();

      // Create real-world skip scenario: Deploy and start process, but migrate instances without process definition
      var processEngine = context.getBean(org.camunda.bpm.engine.ProcessEngine.class);
      var repositoryService = processEngine.getRepositoryService();
      var runtimeService = processEngine.getRuntimeService();

      repositoryService.createDeployment()
          .addClasspathResource("io/camunda/migrator/bpmn/c7/simpleProcess.bpmn")
          .deploy();
      runtimeService.startProcessInstanceByKey("simpleProcess");

      // Migrate instances without definitions - causes real-world skip
      var historyMigrator = context.getBean(HistoryMigrator.class);
      historyMigrator.migrateProcessInstances();

      // when application is shut down
      context.close();

      // then migration schema is dropped (force flag overrides)
      assertThat(tableExists(durableDataSource, prefix + MIGRATION_MAPPING_TABLE)).isFalse();
    } finally {
      if (context.isActive()) {
        context.close();
      }
    }
  }

  @Test
  void shouldMigrationSchemaBeDroppedOnShutdownWithoutPrefix() throws Exception {
    // given spring application is running with drop-schema flag enabled
    var context = springApplication.run("--drop-schema");
    try {
      durableDataSource = createDurableDataSource(context);
      assertThat(tableExists(durableDataSource, MIGRATION_MAPPING_TABLE))
          .as("Migration mapping table does not exist")
          .isTrue();

      // when application is shut down
      context.close();

      // then migration schema is dropped
      assertThat(tableExists(durableDataSource, MIGRATION_MAPPING_TABLE)).isFalse();
    } finally {
      if (context.isActive()) {
        context.close();
      }
    }
  }

  @Test
  void shouldMigrationSchemaBeKeptOnForceFlagOnly() throws Exception {
    // given spring application is running with only force flag enabled
    var context = springApplication.run("--force");
    try {
      durableDataSource = createDurableDataSource(context);
      assertThat(tableExists(durableDataSource, MIGRATION_MAPPING_TABLE))
          .as("Migration mapping table does not exist")
          .isTrue();

      // when application is shut down
      context.close();

      // then migration schema is kept
      assertThat(tableExists(durableDataSource, MIGRATION_MAPPING_TABLE)).isTrue();
    } finally {
      if (context.isActive()) {
        context.close();
      }
    }
  }

  /**
   * Create a new DataSource with the same configuration as the migratorDataSource bean to check if the
   * table still exists after the application context is closed.
   */
  protected static DataSource createDurableDataSource(ConfigurableApplicationContext context) {
    HikariDataSource durableDataSource = new HikariDataSource();
    durableDataSource.setJdbcUrl(context.getEnvironment().getProperty("camunda.migrator.c7.data-source.jdbc-url"));
    durableDataSource.setUsername(context.getEnvironment().getProperty("camunda.migrator.c7.data-source.username"));
    durableDataSource.setPassword(context.getEnvironment().getProperty("camunda.migrator.c7.data-source.password"));
    return durableDataSource;
  }

  public static void clearMigrationMappingTable(DataSource dataSource, String prefix) {
    String sql = "DELETE FROM " + prefix + MIGRATION_MAPPING_TABLE;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to clear table: " + prefix + MIGRATION_MAPPING_TABLE, e);
    }
  }

  protected static boolean tableExists(DataSource dataSource, String tableName) throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      DatabaseMetaData meta = conn.getMetaData();
      String schema = conn.getSchema();
      String dbVendor = meta.getDatabaseProductName().toLowerCase();

      String lookupName = tableName;
      if (dbVendor.contains("postgres")) {
        lookupName = tableName.toLowerCase();
      } else if (dbVendor.contains("oracle")) {
        lookupName = tableName.toUpperCase();
      } else if (dbVendor.contains("h2")) {
        lookupName = tableName.toUpperCase();
      }

      try (ResultSet rs = meta.getTables(conn.getCatalog(), schema, lookupName, new String[]{"TABLE"})) {
        return rs.next();
      }
    }
  }
}
