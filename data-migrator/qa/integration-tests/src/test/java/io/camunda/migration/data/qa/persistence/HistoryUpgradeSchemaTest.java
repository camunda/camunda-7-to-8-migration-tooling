/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.persistence;

import static io.camunda.migration.data.qa.persistence.UpgradeSchemaTest.applyChangelog;
import static io.camunda.migration.data.qa.persistence.UpgradeSchemaTest.createDurableDataSource;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryUpgradeSchemaTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected IdKeyMapper idKeyMapper;

  protected HikariDataSource durableDataSource;

  @AfterEach
  public void tearDown() {
    historyMigrator.setMode(MigratorMode.MIGRATE);
  }

  @Test
  @WhiteBox
  public void shouldMigrateFromV010ToV030() throws Exception {
    // given: an H2 database with only the 0.1.0 changelog applied (simulating an existing installation)
    String jdbcUrl = "jdbc:h2:mem:upgrade-v010-to-v030;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    durableDataSource = createDurableDataSource(jdbcUrl);
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

    // then
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("userTaskProcessId");
    assertThat(processInstances).hasSize(1);
    assertThat(processInstances.getFirst().processInstanceKey()).isNotNull();
  }

}
