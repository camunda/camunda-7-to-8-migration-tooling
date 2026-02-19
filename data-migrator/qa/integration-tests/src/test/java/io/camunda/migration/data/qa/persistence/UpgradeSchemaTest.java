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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

public class UpgradeSchemaTest {

  protected static final String MIGRATION_MAPPING_TABLE = "MIGRATION_MAPPING";
  protected static final String DB_USERNAME = "sa";
  protected static final String DB_PASSWORD = "sa";

  protected HikariDataSource durableDataSource;

  @AfterEach
  public void tearDown() {
    if (durableDataSource != null && !durableDataSource.isClosed()) {
      try (Connection conn = durableDataSource.getConnection()) {
        conn.createStatement().execute("DROP ALL OBJECTS");
      } catch (Exception ignored) {
      }
      durableDataSource.close();
    }
  }

  @Test
  public void shouldUpgradeSchemaFromV010ToV030() throws Exception {
    // given: an H2 database with only the 0.1.0 changelog applied (simulating an existing installation)
    String jdbcUrl = "jdbc:h2:mem:upgrade-v010-to-v030;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    durableDataSource = createDurableDataSource(jdbcUrl);
    applyChangelog(durableDataSource, "classpath:db/changelog/migrator/db.0.1.0.xml");

    // and: the C8_KEY column is BIGINT (confirming the 0.1.0 schema state)
    assertThat(getColumnJdbcType(durableDataSource, MIGRATION_MAPPING_TABLE, "C8_KEY"))
        .as("C8_KEY should be BIGINT in schema version 0.1.0")
        .isEqualTo(Types.BIGINT);

    // and: existing data is inserted using the 0.1.0 BIGINT format for C8_KEY
    insertRowWithBigIntKey(durableDataSource, "test-c7-id-1", 123456789L, "RUNTIME_PROCESS_INSTANCE");
    insertRowWithBigIntKey(durableDataSource, "test-c7-id-2", null, "RUNTIME_PROCESS_INSTANCE");

    // when: the master changelog is applied (simulating the 0.3.0 migrator startup with auto-ddl=true)
    // Liquibase detects 0.1.0 changesets are already applied and only runs the 0.3.0 changesets
    applyChangelog(durableDataSource, "classpath:db/changelog/migrator/db.changelog-master.yaml");

    // then: the C8_KEY column is now VARCHAR(255) after the 0.3.0 upgrade
    assertThat(getColumnJdbcType(durableDataSource, MIGRATION_MAPPING_TABLE, "C8_KEY"))
        .as("C8_KEY should be VARCHAR after schema upgrade to 0.3.0")
        .isEqualTo(Types.VARCHAR);

    // and: pre-existing data with a numeric C8_KEY is preserved as its string representation
    assertThat(rowExists(durableDataSource, "test-c7-id-1", "123456789", "RUNTIME_PROCESS_INSTANCE"))
        .as("Pre-existing data should be preserved after schema upgrade from 0.1.0 to 0.3.0")
        .isTrue();

    // and: pre-existing data with a null C8_KEY is also preserved
    assertThat(rowExistsWithNullKey(durableDataSource, "test-c7-id-2", "RUNTIME_PROCESS_INSTANCE"))
        .as("Pre-existing data with null C8_KEY should be preserved after schema upgrade")
        .isTrue();
  }

  /**
   * Applies the given Liquibase changelog to the data source using the empty table prefix,
   * tracking changes in the standard DATABASECHANGELOG table.
   *
   * @param dataSource the data source to apply the changelog to
   * @param changeLog the classpath location of the Liquibase changelog file
   * @throws Exception if an error occurs while applying the changelog
   */
  protected static void applyChangelog(DataSource dataSource, String changeLog) throws Exception {
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setResourceLoader(new DefaultResourceLoader());
    liquibase.setDataSource(dataSource);
    liquibase.setChangeLog(changeLog);
    liquibase.setChangeLogParameters(Map.of("prefix", ""));
    liquibase.setDatabaseChangeLogTable("DATABASECHANGELOG");
    liquibase.setDatabaseChangeLogLockTable("DATABASECHANGELOGLOCK");
    liquibase.afterPropertiesSet();
  }

  /**
   * Inserts a row into the 0.1.0-era MIGRATION_MAPPING table using BIGINT for the C8_KEY column.
   *
   * @param dataSource the data source to insert the row into
   * @param c7Id the Camunda 7 ID
   * @param c8Key the Camunda 8 key as a Long (may be null)
   * @param type the migration mapping type
   * @throws SQLException if a database access error occurs
   */
  protected void insertRowWithBigIntKey(DataSource dataSource, String c7Id, Long c8Key, String type)
      throws SQLException {
    String sql = "INSERT INTO MIGRATION_MAPPING (C7_ID, C8_KEY, TYPE, CREATE_TIME) VALUES (?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, c7Id);
      if (c8Key != null) {
        stmt.setLong(2, c8Key);
      } else {
        stmt.setNull(2, Types.BIGINT);
      }
      stmt.setString(3, type);
      stmt.setTimestamp(4, new java.sql.Timestamp(System.currentTimeMillis()));
      stmt.executeUpdate();
    }
  }

  /**
   * Returns the JDBC type code of the given column in the given table.
   * Handles H2's uppercase column/table name convention.
   *
   * @param dataSource the data source to query
   * @param tableName the name of the table
   * @param columnName the name of the column
   * @return the JDBC type code as defined in {@link java.sql.Types}
   * @throws SQLException if a database access error occurs
   * @throws RuntimeException if the column is not found in the table
   */
  protected int getColumnJdbcType(DataSource dataSource, String tableName, String columnName)
      throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      DatabaseMetaData meta = conn.getMetaData();
      try (ResultSet rs = meta.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
        if (rs.next()) {
          return rs.getInt("DATA_TYPE");
        }
        throw new RuntimeException("Column " + columnName + " not found in table " + tableName);
      }
    }
  }

  /**
   * Returns true if a row exists with the given C7 ID, C8 key (as VARCHAR), and type.
   *
   * @param dataSource the data source to query
   * @param c7Id the Camunda 7 ID to search for
   * @param c8Key the Camunda 8 key as a String to search for
   * @param type the migration mapping type to search for
   * @return true if a matching row exists, false otherwise
   * @throws SQLException if a database access error occurs
   */
  protected static boolean rowExists(DataSource dataSource, String c7Id, String c8Key, String type)
      throws SQLException {
    String sql = "SELECT COUNT(*) FROM MIGRATION_MAPPING WHERE C7_ID = ? AND C8_KEY = ? AND TYPE = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, c7Id);
      stmt.setString(2, c8Key);
      stmt.setString(3, type);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() && rs.getInt(1) > 0;
      }
    }
  }

  /**
   * Returns true if a row exists with the given C7 ID, a null C8 key, and the given type.
   *
   * @param dataSource the data source to query
   * @param c7Id the Camunda 7 ID to search for
   * @param type the migration mapping type to search for
   * @return true if a matching row with null C8 key exists, false otherwise
   * @throws SQLException if a database access error occurs
   */
  protected static boolean rowExistsWithNullKey(DataSource dataSource, String c7Id, String type)
      throws SQLException {
    String sql = "SELECT COUNT(*) FROM MIGRATION_MAPPING WHERE C7_ID = ? AND C8_KEY IS NULL AND TYPE = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, c7Id);
      stmt.setString(2, type);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() && rs.getInt(1) > 0;
      }
    }
  }

  /**
   * Creates a durable HikariCP data source with the given JDBC URL.
   *
   * @param jdbcUrl the JDBC URL for the database connection
   * @return a configured HikariDataSource instance
   */
  protected static HikariDataSource createDurableDataSource(String jdbcUrl) {
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(jdbcUrl);
    ds.setUsername(DB_USERNAME);
    ds.setPassword(DB_PASSWORD);
    ds.setAutoCommit(true);
    return ds;
  }
}
