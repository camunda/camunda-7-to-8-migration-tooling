/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.persistence;

import static io.camunda.migration.data.qa.persistence.UpgradeSchemaTest.applyChangelog;
import static io.camunda.migration.data.qa.persistence.UpgradeSchemaTest.insertRowWithBigIntKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.impl.DataSourceRegistry;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.migration.data.qa.util.WhiteBox;
import io.camunda.migration.data.qa.util.WithMultiDb;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test for history migration after upgrading the migration schema from 0.1.0 to 0.3.0.
 *
 * <p>This test verifies that the history migrator can successfully migrate process instances
 * after the schema upgrade from 0.1.0 (where C8_KEY is BIGINT) to 0.3.0 (where C8_KEY is VARCHAR).
 * It specifically tests the scenario where process instances are initially skipped due to the
 * schema mismatch, the schema is upgraded, and then the skipped instances are successfully
 * retried and migrated.
 *
 * <p>The test uses the existing Spring-managed datasource (where both the migrator and C8 RDBMS
 * exporter schemas reside) to avoid datasource mismatch issues. The migrator schema is reset
 * to the 0.1.0 state to simulate an existing installation, then upgraded via the master changelog.
 *
 * <p>The test is database-independent and leverages {@link UpgradeSchemaTest} for database
 * configuration. It runs on all supported databases including H2, PostgreSQL, Oracle, MySQL,
 * MariaDB, and SQL Server.
 */
@WithMultiDb
public class HistoryUpgradeSchemaTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected DataSourceRegistry dataSourceRegistry;

  @AfterEach
  @Override
  public void cleanup() {
    super.cleanup();
  }

  @Test
  @WhiteBox
  public void shouldMigrateProcessInstanceAfterUpgradeFromV010ToV030() throws Exception {
    // Use the existing migrator datasource (shared with C8) so that both schemas are available
    DataSource ds = dataSourceRegistry.getMigratorDataSource();

    // given: reset the migrator schema to simulate a fresh 0.1.0 installation
    // Drop existing migrator tables and Liquibase tracking tables, then re-apply only 0.1.0
    dropMigratorSchema(ds);
    applyChangelog(ds, "classpath:db/changelog/migrator/db.0.1.0.xml");

    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var processInstanceId = runtimeService.startProcessInstanceByKey("userTaskProcessId").getId();
    completeAllUserTasksWithDefaultUserTaskId();

    // and: insert a skipped process instance row using the 0.1.0 BIGINT format for C8_KEY
    insertRowWithBigIntKey(ds, processInstanceId, null, "HISTORY_PROCESS_INSTANCE");

    assertThat(countSkippedRows(ds, "HISTORY_PROCESS_INSTANCE"))
        .as("Skipped instance should exist in the datasource")
        .isEqualTo(1);

    // when: the master changelog is applied (simulating the 0.3.0 migrator startup with auto-ddl=true)
    // Liquibase detects 0.1.0 changesets are already applied and only runs the 0.3.0 changesets
    applyChangelog(ds, "classpath:db/changelog/migrator/db.changelog-master.yaml");
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrateProcessInstances();

    // then process instance has been successfully migrated
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId", true);
    assertThat(processInstances).hasSize(1);
    assertThat(processInstances.getFirst().processInstanceKey()).isNotNull();
  }

  /**
   * Drops the migrator schema tables and Liquibase tracking tables so the changelog
   * can be re-applied from scratch to simulate an upgrade from 0.1.0 to 0.3.0.
   */
  private static void dropMigratorSchema(DataSource dataSource) throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      dropTableIfExists(stmt, "MIGRATION_MAPPING");
      dropTableIfExists(stmt, "DATABASECHANGELOG");
      dropTableIfExists(stmt, "DATABASECHANGELOGLOCK");
    }
  }

  private static void dropTableIfExists(Statement stmt, String tableName) {
    try {
      stmt.execute("DROP TABLE " + tableName);
    } catch (SQLException e) {
      // Table doesn't exist, which is fine
    }
  }

  /**
   * Counts skipped rows (C8_KEY IS NULL) for the given type directly via JDBC.
   */
  private static int countSkippedRows(DataSource dataSource, String type) throws SQLException {
    String sql = "SELECT COUNT(*) FROM MIGRATION_MAPPING WHERE C8_KEY IS NULL AND TYPE = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, type);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
  }

}
