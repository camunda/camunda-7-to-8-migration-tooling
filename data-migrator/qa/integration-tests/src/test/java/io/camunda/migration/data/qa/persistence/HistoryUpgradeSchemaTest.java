/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.persistence;

import static io.camunda.migration.data.qa.persistence.UpgradeSchemaTest.applyChangelog;
import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.migration.data.MigratorMode;
import io.camunda.migration.data.impl.persistence.IdKeyDbModel;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.migration.data.qa.util.WhiteBox;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
 * <p>The test is database-independent and leverages {@link UpgradeSchemaTest} for database
 * configuration. It runs on all supported databases including H2, PostgreSQL, Oracle, MySQL,
 * MariaDB, and SQL Server.
 */
public class HistoryUpgradeSchemaTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected IdKeyMapper idKeyMapper;

  protected HikariDataSource durableDataSource;

  @BeforeAll
  public static void setupDatabase() {
    // Ensure UpgradeSchemaTest's database configuration is initialized
    UpgradeSchemaTest.setupDatabase();
  }

  @AfterEach
  @Override
  public void cleanup() {
    historyMigrator.setMode(MigratorMode.MIGRATE);
    UpgradeSchemaTest.closeAndCleanupDataSource(durableDataSource);
  }

  @Test
  @WhiteBox
  public void shouldMigrateProcessInstanceAfterUpgradeFromV010ToV030() throws Exception {
    // given: a database with only the 0.1.0 changelog applied (simulating an existing installation)
    durableDataSource = UpgradeSchemaTest.createDurableDataSource();
    applyChangelog(durableDataSource, "classpath:db/changelog/migrator/db.0.1.0.xml");

    // and: the C8_KEY column is BIGINT (confirming the 0.1.0 schema state)
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // skip process instances
    historyMigrator.migrateProcessInstances();
    List<IdKeyDbModel> skippedInstances = idKeyMapper.findSkippedByType(IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE, 0, 100);
    assertThat(skippedInstances).hasSize(1);

    // when: the master changelog is applied (simulating the 0.3.0 migrator startup with auto-ddl=true)
    // Liquibase detects 0.1.0 changesets are already applied and only runs the 0.3.0 changesets
    applyChangelog(durableDataSource, "classpath:db/changelog/migrator/db.changelog-master.yaml");
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrateProcessInstances();

    // then process instance has been successfully migrated
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    assertThat(processInstances.getFirst().processInstanceKey()).isNotNull();
  }

}
