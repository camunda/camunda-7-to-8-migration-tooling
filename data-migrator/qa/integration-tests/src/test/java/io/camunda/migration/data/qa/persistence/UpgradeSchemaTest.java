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
import io.camunda.migration.data.qa.util.MultiDbExtension;
import io.camunda.migration.data.qa.util.SpringProfileResolver;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Tests for upgrading the migration schema from version 0.1.0 to 0.3.0.
 *
 * <p>This test verifies that existing migration mappings are properly preserved when upgrading
 * from the 0.1.0 schema (where C8_KEY is BIGINT) to the 0.3.0 schema (where C8_KEY is VARCHAR).
 *
 * <p>The test is database-independent and runs on all supported databases including H2, PostgreSQL,
 * Oracle, MySQL, MariaDB, and SQL Server. The database configuration is determined by the active
 * Maven profile.
 */
public class UpgradeSchemaTest {

  protected static final String MIGRATION_MAPPING_TABLE = "MIGRATION_MAPPING";

  protected static String activeProfile;
  protected static DatabaseConfig dbConfig;

  protected HikariDataSource durableDataSource;

  @BeforeAll
  public static void setupDatabase() {
    List<String> profiles = SpringProfileResolver.getActiveProfiles();
    activeProfile = profiles.stream()
        .filter(p -> List.of("postgresql", "postgresql-15", "oracle", "oracle-19", "mysql", "mariadb", "mariadb-10", "sqlserver")
            .contains(p))
        .findFirst()
        .orElse("h2");

    // Configure database connection based on active profile
    // For non-H2 databases, use the ports exposed by MultiDbExtension
    switch (activeProfile) {
      case "postgresql":
        dbConfig = createPostgreSqlConfig(MultiDbExtension.POSTGRESQL_PORT);
        break;
      case "postgresql-15":
        dbConfig = createPostgreSqlConfig(MultiDbExtension.POSTGRESQL_15_PORT);
        break;
      case "oracle":
        dbConfig = createOracleConfig(MultiDbExtension.ORACLE_PORT);
        break;
      case "oracle-19":
        dbConfig = createOracleConfig(MultiDbExtension.ORACLE_19_PORT);
        break;
      case "mysql":
        dbConfig = createMySqlConfig(MultiDbExtension.MYSQL_PORT);
        break;
      case "mariadb":
        dbConfig = createMariaDbConfig(MultiDbExtension.MARIADB_PORT);
        break;
      case "mariadb-10":
        dbConfig = createMariaDbConfig(MultiDbExtension.MARIADB_10_PORT);
        break;
      case "sqlserver":
        dbConfig = createSqlServerConfig(MultiDbExtension.SQLSERVER_PORT);
        break;
      default: // h2
        dbConfig = createH2Config();
    }
  }

  @AfterEach
  public void tearDown() {
    closeAndCleanupDataSource(durableDataSource);
  }

  @Test
  public void shouldUpgradeSchemaFromV010ToV030() throws Exception {
    // given: a database with only the 0.1.0 changelog applied (simulating an existing installation)
    durableDataSource = createDurableDataSource();
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
   * Handles different database naming conventions for metadata queries.
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

      // Try different case variations based on database type
      String[] tableNameVariations = getNameVariations(tableName);
      String[] columnNameVariations = getNameVariations(columnName);

      for (String tableVariation : tableNameVariations) {
        for (String columnVariation : columnNameVariations) {
          try (ResultSet rs = meta.getColumns(null, null, tableVariation, columnVariation)) {
            if (rs.next()) {
              return rs.getInt("DATA_TYPE");
            }
          }
        }
      }

      throw new RuntimeException("Column " + columnName + " not found in table " + tableName);
    }
  }

  /**
   * Returns name variations (original, uppercase, lowercase) to handle different database metadata conventions.
   *
   * <p>Different databases store metadata differently - some use uppercase (e.g., Oracle, H2),
   * some use lowercase (e.g., PostgreSQL), and some preserve the original case.
   *
   * @param name the original name
   * @return an array containing the original name, uppercase version, and lowercase version
   */
  protected String[] getNameVariations(String name) {
    return new String[]{name, name.toUpperCase(), name.toLowerCase()};
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
   * Creates a durable HikariCP data source using the configured database settings.
   *
   * <p>The database configuration must be initialized by calling {@link #setupDatabase()} before
   * calling this method.
   *
   * @return a configured HikariDataSource instance
   * @throws NullPointerException if setupDatabase() has not been called
   */
  protected static HikariDataSource createDurableDataSource() {
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(dbConfig.jdbcUrl);
    ds.setUsername(dbConfig.username);
    ds.setPassword(dbConfig.password);
    ds.setDriverClassName(dbConfig.driverClassName);
    ds.setAutoCommit(true);
    return ds;
  }

  /**
   * Simple data class to hold database configuration.
   *
   * <p>This class stores the JDBC connection parameters needed to create a datasource
   * for the configured database type.
   */
  protected static class DatabaseConfig {
    final String jdbcUrl;
    final String username;
    final String password;
    final String driverClassName;

    DatabaseConfig(String jdbcUrl, String username, String password, String driverClassName) {
      this.jdbcUrl = jdbcUrl;
      this.username = username;
      this.password = password;
      this.driverClassName = driverClassName;
    }
  }


  /**
   * Creates a PostgreSQL database configuration.
   *
   * @param port the port number for the PostgreSQL instance
   * @return the database configuration
   */
  protected static DatabaseConfig createPostgreSqlConfig(int port) {
    return new DatabaseConfig(
        "jdbc:postgresql://localhost:" + port + "/process-engine",
        "camunda",
        "camunda",
        "org.postgresql.Driver"
    );
  }

  /**
   * Creates an Oracle database configuration.
   *
   * @param port the port number for the Oracle instance
   * @return the database configuration
   */
  protected static DatabaseConfig createOracleConfig(int port) {
    return new DatabaseConfig(
        "jdbc:oracle:thin:@localhost:" + port + ":ORCLDB",
        "camunda",
        "camunda",
        "oracle.jdbc.OracleDriver"
    );
  }

  /**
   * Creates a MySQL database configuration.
   *
   * @param port the port number for the MySQL instance
   * @return the database configuration
   */
  protected static DatabaseConfig createMySqlConfig(int port) {
    return new DatabaseConfig(
        "jdbc:mysql://localhost:" + port + "/process-engine",
        "camunda",
        "camunda",
        "com.mysql.cj.jdbc.Driver"
    );
  }

  /**
   * Creates a MariaDB database configuration.
   *
   * @param port the port number for the MariaDB instance
   * @return the database configuration
   */
  protected static DatabaseConfig createMariaDbConfig(int port) {
    return new DatabaseConfig(
        "jdbc:mariadb://localhost:" + port + "/process-engine",
        "camunda",
        "camunda",
        "org.mariadb.jdbc.Driver"
    );
  }

  /**
   * Creates a SQL Server database configuration.
   *
   * @param port the port number for the SQL Server instance
   * @return the database configuration
   */
  protected static DatabaseConfig createSqlServerConfig(int port) {
    return new DatabaseConfig(
        "jdbc:sqlserver://localhost:" + port,
        "sa",
        "Camunda123!",
        "com.microsoft.sqlserver.jdbc.SQLServerDriver"
    );
  }

  /**
   * Creates an H2 database configuration with a unique in-memory database.
   *
   * @return the database configuration
   */
  protected static DatabaseConfig createH2Config() {
    return new DatabaseConfig(
        "jdbc:h2:mem:upgrade-test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "sa",
        "sa",
        "org.h2.Driver"
    );
  }

  /**
   * Cleans up the database in a database-agnostic way.
   * Uses database-specific SQL syntax for dropping tables.
   *
   * @param dataSource the data source to clean up
   * @throws SQLException if a database access error occurs
   */
  protected static void cleanupDatabase(DataSource dataSource) throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      switch (activeProfile) {
      case "h2":
        stmt.execute("DROP ALL OBJECTS");
        break;
      case "postgresql":
      case "postgresql-15":
        dropMigrationTables(stmt, "DROP TABLE IF EXISTS %s CASCADE");
        break;
      case "oracle":
      case "oracle-19":
        // Oracle requires dropping tables individually
        dropTableIfExists(stmt, "MIGRATION_MAPPING");
        dropTableIfExists(stmt, "DATABASECHANGELOG");
        dropTableIfExists(stmt, "DATABASECHANGELOGLOCK");
        break;
      case "mysql":
      case "mariadb":
      case "mariadb-10":
        dropMigrationTables(stmt, "DROP TABLE IF EXISTS %s");
        break;
      case "sqlserver":
        dropMigrationTables(stmt, "IF OBJECT_ID('%s', 'U') IS NOT NULL DROP TABLE %s");
        break;
      default:
        throw new IllegalStateException("Unsupported database profile: " + activeProfile);
      }
    }
  }

  /**
   * Drops the migration-related tables using the specified SQL template.
   *
   * @param stmt the SQL statement to use
   * @param sqlTemplate the SQL template with placeholders for table names
   * @throws SQLException if a database access error occurs
   */
  protected static void dropMigrationTables(Statement stmt, String sqlTemplate) throws SQLException {
    String[] tables = {"MIGRATION_MAPPING", "DATABASECHANGELOG", "DATABASECHANGELOGLOCK"};
    for (String table : tables) {
      String sql = sqlTemplate.contains("%s") && sqlTemplate.indexOf("%s") != sqlTemplate.lastIndexOf("%s")
          ? String.format(sqlTemplate, table, table)  // For SQL Server: two placeholders
          : String.format(sqlTemplate, table);        // For others: one placeholder
      stmt.execute(sql);
    }
  }

  /**
   * Closes and cleans up the given datasource.
   * Silently handles cleanup errors without failing the test.
   *
   * @param dataSource the datasource to close and clean up
   */
  protected static void closeAndCleanupDataSource(HikariDataSource dataSource) {
    if (dataSource != null && !dataSource.isClosed()) {
      try {
        cleanupDatabase(dataSource);
      } catch (Exception e) {
        // Log but don't fail on cleanup errors
        System.err.println("Error during database cleanup: " + e.getMessage());
      } finally {
        dataSource.close();
      }
    }
  }

  /**
   * Helper method to drop a table if it exists in Oracle.
   * Silently ignores errors if the table doesn't exist.
   *
   * @param stmt the SQL statement to use
   * @param tableName the name of the table to drop
   */
  protected static void dropTableIfExists(Statement stmt, String tableName) {
    try {
      stmt.execute("DROP TABLE " + tableName + " CASCADE CONSTRAINTS");
    } catch (SQLException e) {
      // Table doesn't exist, which is fine
    }
  }

}
