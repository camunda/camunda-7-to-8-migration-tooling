/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.config.mybatis;

import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_DROP_MIGRATION_TABLE;
import static io.camunda.migrator.impl.logging.SchemaShutdownCleanerLogs.logForceDrop;
import static io.camunda.migrator.impl.logging.SchemaShutdownCleanerLogs.logSkippedEntitiesCount;
import static io.camunda.migrator.impl.logging.SchemaShutdownCleanerLogs.logSkippingDrop;
import static io.camunda.migrator.impl.logging.SchemaShutdownCleanerLogs.logSuccessfulMigrationDrop;
import static io.camunda.migrator.impl.util.ExceptionUtils.callApi;

import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.clients.DbClient;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
  @Qualifier("migratorDataSource")
  protected DataSource dataSource;

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
    try (Connection conn = dataSource.getConnection()) {
      Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
      Liquibase liquibase = new Liquibase("db/changelog/migrator/db.0.2.0.xml", new ClassLoaderResourceAccessor(),
          database);
      database.setDatabaseChangeLogTableName(prefix + "DATABASECHANGELOG");
      database.setDatabaseChangeLogLockTableName(prefix + "DATABASECHANGELOGLOCK");
      liquibase.setChangeLogParameter("prefix", prefix);
      liquibase.clearCheckSums();
      liquibase.rollback("tag_before_create_migration_mapping_table", "");
    } catch (Exception e) {
      throw new PersistenceException(e);
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