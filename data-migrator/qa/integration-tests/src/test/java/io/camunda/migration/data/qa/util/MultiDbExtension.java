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
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.oracle.OracleContainer;

public class MultiDbExtension implements BeforeAllCallback {

  public static final int POSTGRESQL_PORT = 55432;
  public static final int ORACLE_PORT = 15432;
  public static final int SQLSERVER_PORT = 14331;

  /**
   * Cross-vendor profiles that require more than one database container: the profile name is
   * mapped to the container keys it needs, in the order they are started.
   */
  protected static final Map<String, List<String>> CROSS_VENDOR_PROFILES =
      Map.of("sqlserver-to-postgresql", List.of("sqlserver", "postgresql"));
  
  protected static final Logger LOGGER = LoggerFactory.getLogger(MultiDbExtension.class);
  protected static Map<String, JdbcDatabaseContainer<?>> containers = createContainers();
  
  static { // Start db container before initializing Spring context
    startContainer();
  }

  protected static void startContainer() {
    List<String> activeProfiles = SpringProfileResolver.getActiveProfiles();
    String dbProfile = activeProfiles.stream()
        .filter(profile -> containers.containsKey(profile) || CROSS_VENDOR_PROFILES.containsKey(profile))
        .findFirst()
        .orElse("default");
    LOGGER.info("Running tests with DB profile [{}]", dbProfile);
    List<String> containerKeys = CROSS_VENDOR_PROFILES.get(dbProfile);
    if (containerKeys == null) {
      // A single-vendor profile, or "default" for which no container exists.
      containerKeys = List.of(dbProfile);
    } else {
      for (String containerKey : containerKeys) {
        if (!containers.containsKey(containerKey)) {
          throw new IllegalStateException("Cross-vendor profile [" + dbProfile
              + "] requires unknown container [" + containerKey + "], available containers are "
              + containers.keySet());
        }
      }
    }
    for (String containerKey : containerKeys) {
      JdbcDatabaseContainer<?> container = containers.get(containerKey);
      if (container != null) {
        LOGGER.info("Starting container [{}]", containerKey);
        container.start();
      }
    }
  }

  protected static PostgreSQLContainer createPostgreSQLContainer() {
    PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
        .withDatabaseName("process-engine")
        .withUsername("camunda")
        .withPassword("camunda")
        .withReuse(true)
        .withCommand("postgres", "-c", "max_connections=200")
        .withExposedPorts(5432);
    postgres.setPortBindings(List.of(POSTGRESQL_PORT + ":5432"));
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

  protected static MSSQLServerContainer createSQLServerContainer() {
    MSSQLServerContainer sqlserver =
        new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
            .withPassword("Camunda123!")
            .withExposedPorts(1433)
            .acceptLicense();
    sqlserver.setPortBindings(List.of(SQLSERVER_PORT + ":1433"));
    return sqlserver;
  }

  protected static Map<String, JdbcDatabaseContainer<?>> createContainers() {
    Map<String, JdbcDatabaseContainer<?>> containers = new HashMap<>();
    containers.putIfAbsent("postgresql", createPostgreSQLContainer());
    containers.putIfAbsent("oracle", createOracleContainer());
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
