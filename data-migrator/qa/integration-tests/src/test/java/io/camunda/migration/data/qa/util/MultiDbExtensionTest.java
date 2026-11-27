/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.config.property.MigratorProperties;
import java.util.List;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@WithMultiDb
@WithSpringProfile("history-level-full")
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
    String dbProfile = SpringProfileResolver.getActiveProfiles().stream()
        .filter(DATABASE_PROFILES::contains)
        .findFirst()
        .orElse("h2");
    String c7JdbcUrlProperty = MigratorProperties.PREFIX + ".c7.data-source.jdbc-url";
    String c8JdbcUrlProperty = MigratorProperties.PREFIX + ".c8.data-source.jdbc-url";

    if ("h2".equals(dbProfile)) {
      assertThat(environment.getProperty(c7JdbcUrlProperty)).startsWith("jdbc:h2:");
      assertThat(environment.getProperty(c8JdbcUrlProperty)).startsWith("jdbc:h2:");
      return;
    }

    String c7ContainerKey = "sqlserver-to-postgresql".equals(dbProfile) ? "sqlserver" : dbProfile;
    String c8ContainerKey = "sqlserver-to-postgresql".equals(dbProfile) ? "postgresql" : dbProfile;
    assertThat(environment.getProperty(c7JdbcUrlProperty))
        .isEqualTo(MultiDbExtension.getJdbcUrl(c7ContainerKey));
    assertThat(environment.getProperty(c8JdbcUrlProperty))
        .isEqualTo(MultiDbExtension.getJdbcUrl(c8ContainerKey));
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
}
