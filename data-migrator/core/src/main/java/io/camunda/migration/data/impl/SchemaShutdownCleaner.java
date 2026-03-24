/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl;

import static io.camunda.migration.data.impl.logging.DbClientLogs.FAILED_TO_DROP_MIGRATION_TABLE;
import static io.camunda.migration.data.impl.logging.SchemaShutdownCleanerLogs.logForceDrop;
import static io.camunda.migration.data.impl.logging.SchemaShutdownCleanerLogs.logSkippedEntitiesCount;
import static io.camunda.migration.data.impl.logging.SchemaShutdownCleanerLogs.logSkippingDrop;
import static io.camunda.migration.data.impl.logging.SchemaShutdownCleanerLogs.logSuccessfulMigrationDrop;
import static io.camunda.migration.data.impl.util.ExceptionUtils.callApi;

import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.clients.DbClient;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import javax.sql.DataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Component;

@Component
@Conditional(SchemaShutdownCleaner.DropSchemaCondition.class)
public class SchemaShutdownCleaner {

  @Autowired
  protected DataSourceRegistry dataSourceRegistry;

  @Autowired
  protected MigratorProperties configProperties;

  @Autowired
  protected DbClient dbClient;

  @Autowired
  protected Environment environment;

  @PreDestroy
  public void cleanUp() {
    if (schemaDropEnabled(environment)) {
      Long skipped = dbClient.countSkipped();
      logSkippedEntitiesCount(skipped);
      String tablePrefix = StringUtils.trimToEmpty(configProperties.getTablePrefix());
      if (skipped == 0) {
        logSuccessfulMigrationDrop();
        callApi(() -> rollbackTableCreation(tablePrefix), FAILED_TO_DROP_MIGRATION_TABLE);
      } else if (forceEnabled(environment)) {
        logForceDrop();
        callApi(() -> rollbackTableCreation(tablePrefix), FAILED_TO_DROP_MIGRATION_TABLE);
      } else {
        logSkippingDrop();
      }
    }
  }

  protected void rollbackTableCreation(String prefix) {
    DataSource dataSource = dataSourceRegistry.getMigratorDataSource();
    try (Connection conn = dataSource.getConnection()) {
      String mappingTableName = prefix + "MIGRATION_MAPPING";
      String changelogTableName = prefix + "DATABASECHANGELOG";

      // Drop mapping table only if it exists
      if (tableExists(conn, mappingTableName)) {
        try (var stmt = conn.createStatement()) {
          stmt.execute("DROP TABLE " + mappingTableName);
        }
      }

      // Delete migrator changelog entries only if changelog table exists
      if (tableExists(conn, changelogTableName)) {
        try (var stmt = conn.prepareStatement(
            "DELETE FROM " + changelogTableName + " WHERE FILENAME LIKE 'db/changelog/migrator/%'")) {
          stmt.executeUpdate();
        }
      }

      if (!conn.getAutoCommit()) {
        conn.commit();
      }
    } catch (Exception e) {
      throw new PersistenceException(e);
    }
  }

  /**
   * Checks if a table exists in the database using DatabaseMetaData.
   * Handles case-sensitivity differences across database vendors.
   */
  protected boolean tableExists(Connection conn, String tableName) {
    try {
      var metaData = conn.getMetaData();
      // Try exact case first
      try (var rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
        if (rs.next()) {
          return true;
        }
      }
      // Try uppercase (for databases that store identifiers in uppercase like Oracle, H2)
      try (var rs = metaData.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
        if (rs.next()) {
          return true;
        }
      }
      // Try lowercase (for databases that store identifiers in lowercase like PostgreSQL)
      try (var rs = metaData.getTables(null, null, tableName.toLowerCase(), new String[]{"TABLE"})) {
        return rs.next();
      }
    } catch (Exception e) {
      // If metadata check fails, conservatively assume the table does not exist and skip DROP.
      // This keeps shutdown cleanup best-effort and avoids failing on non-existent tables.
      return false;
    }
  }

  protected static boolean schemaDropEnabled(Environment context) {
    return context.containsProperty("drop-schema");
  }

  protected static boolean forceEnabled(Environment context) {
    return context.containsProperty("force");
  }

  protected static class DropSchemaCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return schemaDropEnabled(context.getEnvironment());
    }

  }
}