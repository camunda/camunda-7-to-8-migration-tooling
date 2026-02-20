/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

import io.camunda.migration.data.config.property.MigratorProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

public class MultiDbExtension implements BeforeAllCallback {

  public static final int POSTGRESQL_PORT = 55432;
  public static final int POSTGRESQL_15_PORT = 55433;
  public static final int ORACLE_PORT = 15432;
  public static final int ORACLE_19_PORT = 15433;
  public static final int MYSQL_PORT = 33061;
  public static final int MARIADB_PORT = 33062;
  public static final int MARIADB_10_PORT = 33063;
  public static final int SQLSERVER_PORT = 14331;
  
  protected static final Logger LOGGER = LoggerFactory.getLogger(MultiDbExtension.class);
  protected static Map<String, JdbcDatabaseContainer<?>> containers = createContainers();
  
  static { // Start db container before initializing Spring context
    startContainer();
  }

  protected static void startContainer() {
    List<String> activeProfiles = SpringProfileResolver.getActiveProfiles();
    String dbProfile = activeProfiles.stream().filter(containers.keySet()::contains).findFirst().orElse("default");
    LOGGER.info("Running tests with DB profile [{}]", dbProfile);
    JdbcDatabaseContainer<?> container = containers.get(dbProfile);
    if (container != null) {
      LOGGER.info("Starting container [{}]", dbProfile);
      container.start();
    }
  }

  protected static PostgreSQLContainer<?> createPostgreSQLContainer() {
    PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
        .withDatabaseName("process-engine")
        .withUsername("camunda")
        .withPassword("camunda")
        .withReuse(true)
        .withCommand("postgres", "-c", "max_connections=200")
        .withExposedPorts(5432);
    postgres.setPortBindings(List.of(POSTGRESQL_PORT + ":5432"));
    return postgres;
  }

  protected static PostgreSQLContainer<?> createPostgreSQL15Container() {
    PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("process-engine")
        .withUsername("camunda")
        .withPassword("camunda")
        .withReuse(true)
        .withCommand("postgres", "-c", "max_connections=200")
        .withExposedPorts(5432);
    postgres.setPortBindings(List.of(POSTGRESQL_15_PORT + ":5432"));
    return postgres;
  }

  protected static OracleContainer createOracleContainer() {
    OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23")
        .withDatabaseName("ORCLDB")
        .withUsername("camunda")
        .withPassword("camunda")
        .withReuse(true)
        .withExposedPorts(1521);
    oracle.setPortBindings(List.of(ORACLE_PORT + ":1521"));
    return oracle;
  }

  protected static OracleContainer createOracle19Container() {
    DockerImageName oracleImage = DockerImageName.parse("doctorkirk/oracle-19c:latest")
        .asCompatibleSubstituteFor("gvenzl/oracle-free");
    OracleContainer oracle = new OracleContainer(oracleImage)
        .withDatabaseName("ORCLDB")
        .withUsername("camunda")
        .withPassword("camunda")
        .withReuse(true)
        .withExposedPorts(1521);
    oracle.setPortBindings(List.of(ORACLE_19_PORT + ":1521"));
    return oracle;
  }

  protected static MySQLContainer<?> createMySQLContainer() {
    MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("process-engine")
        .withUsername("camunda")
        .withPassword("camunda")
        .withConfigurationOverride("mysql-conf")
        .withExposedPorts(3306);
    mysql.setPortBindings(List.of(MYSQL_PORT + ":3306"));
    return mysql;
  }

  protected static MariaDBContainer<?> createMariaDBContainer() {
    MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("process-engine")
        .withUsername("camunda")
        .withPassword("camunda")
        .withConfigurationOverride("mariadb-conf")
        .withExposedPorts(3306);
    mariadb.setPortBindings(List.of(MARIADB_PORT + ":3306"));
    return mariadb;
  }

  protected static MariaDBContainer<?> createMariaDB10Container() {
    MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:10.6")
        .withDatabaseName("process-engine")
        .withUsername("camunda")
        .withPassword("camunda")
        .withConfigurationOverride("mariadb-conf")
        .withExposedPorts(3306);
    mariadb.setPortBindings(List.of(MARIADB_10_PORT + ":3306"));
    return mariadb;
  }

  protected static MSSQLServerContainer<?> createSQLServerContainer() {
    MSSQLServerContainer<?> sqlserver = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
        .withPassword("Camunda123!")
        .withExposedPorts(1433)
        .acceptLicense();
    sqlserver.setPortBindings(List.of(SQLSERVER_PORT + ":1433"));
    return sqlserver;
  }

  protected static Map<String, JdbcDatabaseContainer<?>> createContainers() {
    Map<String, JdbcDatabaseContainer<?>> containers = new HashMap<>();
    containers.putIfAbsent("postgresql", createPostgreSQLContainer());
    containers.putIfAbsent("postgresql-15", createPostgreSQL15Container());
    containers.putIfAbsent("oracle", createOracleContainer());
    containers.putIfAbsent("oracle-19", createOracle19Container());
    containers.putIfAbsent("mysql", createMySQLContainer());
    containers.putIfAbsent("mariadb", createMariaDBContainer());
    containers.putIfAbsent("mariadb-10", createMariaDB10Container());
    containers.putIfAbsent("sqlserver", createSQLServerContainer());
    return containers;
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    Environment env = SpringExtension.getApplicationContext(context).getEnvironment();
    LOGGER.info("C7 jdbc-url set to [{}]", env.getProperty(MigratorProperties.PREFIX + ".c7.data-source.jdbc-url"));
    LOGGER.info("C8 jdbc-url set to [{}]", env.getProperty(MigratorProperties.PREFIX + ".c8.data-source.jdbc-url"));
  }
}
