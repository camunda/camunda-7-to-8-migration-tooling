/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.migration.data.config.property.MigratorProperties;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@WithMultiDb
public class MultiDbExtensionTest {

  protected static final Set<String> DATABASE_PROFILES =
      Set.of(
          "postgresql",
          "postgresql-15",
          "oracle",
          "oracle-19",
          "mysql",
          "mariadb",
          "mariadb-10",
          "sqlserver",
          "sqlserver-to-postgresql");

  @Autowired
  protected Environment environment;

  @Test
  public void shouldUseMappedJdbcUrlsForActiveProfile() {
    assertThat(environment.getActiveProfiles()).doesNotContain("history-level-full");

    String dbProfile = SpringProfileResolver.getActiveProfiles().stream()
        .filter(DATABASE_PROFILES::contains)
        .findFirst()
        .orElse("h2");
    String c7JdbcUrlProperty = MigratorProperties.PREFIX + ".c7.data-source.jdbc-url";
    String c8JdbcUrlProperty = MigratorProperties.PREFIX + ".c8.data-source.jdbc-url";
    String c7UsernameProperty = MigratorProperties.PREFIX + ".c7.data-source.username";
    String c8UsernameProperty = MigratorProperties.PREFIX + ".c8.data-source.username";
    String c7PasswordProperty = MigratorProperties.PREFIX + ".c7.data-source.password";
    String c8PasswordProperty = MigratorProperties.PREFIX + ".c8.data-source.password";

    if ("h2".equals(dbProfile)) {
      assertThat(environment.getProperty(c7JdbcUrlProperty)).startsWith("jdbc:h2:");
      assertThat(environment.getProperty(c8JdbcUrlProperty)).startsWith("jdbc:h2:");
      assertThat(environment.getProperty(c7UsernameProperty)).isEqualTo("sa");
      assertThat(environment.getProperty(c8UsernameProperty)).isEqualTo("sa");
      assertThat(environment.getProperty(c7PasswordProperty)).isEqualTo("sa");
      assertThat(environment.getProperty(c8PasswordProperty)).isEqualTo("sa");
      return;
    }

    String c7ContainerKey = "sqlserver-to-postgresql".equals(dbProfile) ? "sqlserver" : dbProfile;
    String c8ContainerKey = "sqlserver-to-postgresql".equals(dbProfile) ? "postgresql" : dbProfile;
    JdbcDatabaseContainer<?> c7Container = MultiDbExtension.getContainer(c7ContainerKey);
    JdbcDatabaseContainer<?> c8Container = MultiDbExtension.getContainer(c8ContainerKey);
    assertThat(environment.getProperty(c7JdbcUrlProperty))
        .isEqualTo(c7Container.getJdbcUrl());
    assertThat(environment.getProperty(c8JdbcUrlProperty))
        .isEqualTo(c8Container.getJdbcUrl());
    assertThat(environment.getProperty(c7UsernameProperty)).isEqualTo(c7Container.getUsername());
    assertThat(environment.getProperty(c8UsernameProperty)).isEqualTo(c8Container.getUsername());
    assertThat(environment.getProperty(c7PasswordProperty)).isEqualTo(c7Container.getPassword());
    assertThat(environment.getProperty(c8PasswordProperty)).isEqualTo(c8Container.getPassword());
  }

  @Test
  public void shouldSetDriverClassesFromMappedContainers() {
    PostgreSQLContainer c7Container = mock(PostgreSQLContainer.class);
    PostgreSQLContainer c8Container = mock(PostgreSQLContainer.class);
    when(c7Container.getJdbcUrl()).thenReturn("jdbc:postgresql://c7/process-engine");
    when(c7Container.getDriverClassName()).thenReturn("c7.Driver");
    when(c7Container.getUsername()).thenReturn("c7-user");
    when(c7Container.getPassword()).thenReturn("c7-password");
    when(c8Container.getJdbcUrl()).thenReturn("jdbc:postgresql://c8/process-engine");
    when(c8Container.getDriverClassName()).thenReturn("c8.Driver");
    when(c8Container.getUsername()).thenReturn("c8-user");
    when(c8Container.getPassword()).thenReturn("c8-password");

    String c7JdbcUrlProperty = MigratorProperties.PREFIX + ".c7.data-source.jdbc-url";
    String c8JdbcUrlProperty = MigratorProperties.PREFIX + ".c8.data-source.jdbc-url";
    String c7DriverProperty = MigratorProperties.PREFIX + ".c7.data-source.driver-class-name";
    String c8DriverProperty = MigratorProperties.PREFIX + ".c8.data-source.driver-class-name";
    String c7UsernameProperty = MigratorProperties.PREFIX + ".c7.data-source.username";
    String c8UsernameProperty = MigratorProperties.PREFIX + ".c8.data-source.username";
    String c7PasswordProperty = MigratorProperties.PREFIX + ".c7.data-source.password";
    String c8PasswordProperty = MigratorProperties.PREFIX + ".c8.data-source.password";
    String originalC7JdbcUrl = System.getProperty(c7JdbcUrlProperty);
    String originalC8JdbcUrl = System.getProperty(c8JdbcUrlProperty);
    String originalC7Driver = System.getProperty(c7DriverProperty);
    String originalC8Driver = System.getProperty(c8DriverProperty);
    String originalC7Username = System.getProperty(c7UsernameProperty);
    String originalC8Username = System.getProperty(c8UsernameProperty);
    String originalC7Password = System.getProperty(c7PasswordProperty);
    String originalC8Password = System.getProperty(c8PasswordProperty);
    Map<String, JdbcDatabaseContainer<?>> originalContainers = MultiDbExtension.containers;

    try {
      MultiDbExtension.containers = Map.of("c7", c7Container, "c8", c8Container);
      MultiDbExtension.setDataSourceConnectionProperties(List.of("c7", "c8"));

      assertThat(System.getProperty(c7JdbcUrlProperty))
          .isEqualTo("jdbc:postgresql://c7/process-engine");
      assertThat(System.getProperty(c8JdbcUrlProperty))
          .isEqualTo("jdbc:postgresql://c8/process-engine");
      assertThat(System.getProperty(c7DriverProperty)).isEqualTo("c7.Driver");
      assertThat(System.getProperty(c8DriverProperty)).isEqualTo("c8.Driver");
      assertThat(System.getProperty(c7UsernameProperty)).isEqualTo("c7-user");
      assertThat(System.getProperty(c8UsernameProperty)).isEqualTo("c8-user");
      assertThat(System.getProperty(c7PasswordProperty)).isEqualTo("c7-password");
      assertThat(System.getProperty(c8PasswordProperty)).isEqualTo("c8-password");
    } finally {
      MultiDbExtension.containers = originalContainers;
      restoreSystemProperty(c7JdbcUrlProperty, originalC7JdbcUrl);
      restoreSystemProperty(c8JdbcUrlProperty, originalC8JdbcUrl);
      restoreSystemProperty(c7DriverProperty, originalC7Driver);
      restoreSystemProperty(c8DriverProperty, originalC8Driver);
      restoreSystemProperty(c7UsernameProperty, originalC7Username);
      restoreSystemProperty(c8UsernameProperty, originalC8Username);
      restoreSystemProperty(c7PasswordProperty, originalC7Password);
      restoreSystemProperty(c8PasswordProperty, originalC8Password);
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "spring.profiles.active", matches = "postgresql")
  public void shouldStartPostgreSqlContainersConcurrentlyOnDifferentPorts() throws Exception {
    PostgreSQLContainer first = MultiDbExtension.createPostgreSQLContainer().withReuse(false);
    PostgreSQLContainer second = MultiDbExtension.createPostgreSQLContainer().withReuse(false);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      List<Callable<Void>> starts = List.of(
          () -> {
            first.start();
            return null;
          },
          () -> {
            second.start();
            return null;
          });
      for (Future<Void> start : executor.invokeAll(starts)) {
        start.get();
      }

      assertThat(first.getMappedPort(5432)).isNotEqualTo(second.getMappedPort(5432));
    } finally {
      executor.shutdownNow();
      if (first.isRunning()) {
        first.stop();
      }
      if (second.isRunning()) {
        second.stop();
      }
    }
  }

  protected static void restoreSystemProperty(String property, String value) {
    if (value == null) {
      System.clearProperty(property);
    } else {
      System.setProperty(property, value);
    }
  }
}
