/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.plugin.cockpit.resources;

import static io.camunda.migrator.impl.util.ConverterUtil.getNextKey;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.List;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.camunda.bpm.cockpit.Cockpit;
import org.camunda.bpm.cockpit.db.CommandExecutor;
import org.camunda.bpm.cockpit.plugin.test.AbstractCockpitPluginTest;
import org.camunda.bpm.engine.ProcessEngine;
import org.junit.Before;
import org.junit.Test;

public class MigratorResourceTest extends AbstractCockpitPluginTest {

  protected MigratorResource resource;

  @Before
  public void setUp() throws Exception {
    super.before();
    ProcessEngine processEngine = getProcessEngine();
    resource = new MigratorResource(processEngine.getName());
    runLiquibaseMigrations();
  }

  @Test
  public void testMigratedRecords() {
    // given - insert multiple migrated records
    IdKeyDbModel expectedMigrated1 = createExpectedMigratedModel("migratedC7Id1");
    IdKeyDbModel expectedMigrated2 = createExpectedMigratedModel("migratedC7Id2");
    insertTestData(expectedMigrated1);
    insertTestData(expectedMigrated2);
    String processInstanceType = String.valueOf(IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE);

    // when - query migrated records
    Long migratedCount = resource.getMigratedCount(processInstanceType);
    List<IdKeyDbModel> migratedInstances = resource.getMigrated(processInstanceType, 0, 10);

    // then - verify migrated records
    assertThat(migratedCount).isEqualTo(2L);
    assertThat(migratedInstances.size()).isEqualTo(2);
    assertIdKeyDbModelListsEqual(List.of(expectedMigrated1, expectedMigrated2), migratedInstances);
  }

  @Test
  public void testSkippedRecords() {
    // given - insert multiple skipped records
    IdKeyDbModel expectedSkipped1 = createExpectedSkippedModel("skippedC7Id1", "Test skip reason 1");
    IdKeyDbModel expectedSkipped2 = createExpectedSkippedModel("skippedC7Id2", "Test skip reason 2");
    insertTestData(expectedSkipped1);
    insertTestData(expectedSkipped2);
    String processInstanceType = String.valueOf(IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE);

    // when - query skipped records
    Long skippedCount = resource.getSkippedCount(processInstanceType);
    List<IdKeyDbModel> skippedInstances = resource.getSkipped(processInstanceType, 0, 10);

    // then - verify skipped records
    assertThat(skippedCount).isEqualTo(2L);
    assertThat(skippedInstances.size()).isEqualTo(2);
    assertIdKeyDbModelListsEqual(List.of(expectedSkipped1, expectedSkipped2), skippedInstances);
  }

  @Test
  public void testPagination() {
    // given - insert test data for pagination
    IdKeyDbModel migrated1 = createExpectedMigratedModel("migratedForPagination1");
    IdKeyDbModel migrated2 = createExpectedMigratedModel("migratedForPagination2");
    IdKeyDbModel skipped1 = createExpectedSkippedModel("skippedForPagination1", "Skip reason 1");
    IdKeyDbModel skipped2 = createExpectedSkippedModel("skippedForPagination2", "Skip reason 2");

    insertTestData(migrated1);
    insertTestData(migrated2);
    insertTestData(skipped1);
    insertTestData(skipped2);

    String processInstanceType = String.valueOf(IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE);

    // when - test pagination with offset 1, limit 1
    List<IdKeyDbModel> migratedSecondPage = resource.getMigrated(processInstanceType, 1, 1);
    List<IdKeyDbModel> skippedSecondPage = resource.getSkipped(processInstanceType, 1, 1);

    // then - verify pagination results
    assertThat(migratedSecondPage.size()).isEqualTo(1);
    assertThat(skippedSecondPage.size()).isEqualTo(1);

    // when - test pagination beyond available data
    List<IdKeyDbModel> migratedBeyondData = resource.getMigrated(processInstanceType, 10, 1);
    List<IdKeyDbModel> skippedBeyondData = resource.getSkipped(processInstanceType, 10, 1);

    // then - verify empty results for out-of-bounds pagination
    assertThat(migratedBeyondData.size()).isEqualTo(0);
    assertThat(skippedBeyondData.size()).isEqualTo(0);
  }

  @Test
  public void testEmptyState() {
    // given - no test data inserted
    String processInstanceType = String.valueOf(IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE);

    // when - query empty state
    Long migratedCount = resource.getMigratedCount(processInstanceType);
    List<IdKeyDbModel> migratedInstances = resource.getMigrated(processInstanceType, 0, 10);
    Long skippedCount = resource.getSkippedCount(processInstanceType);
    List<IdKeyDbModel> skippedInstances = resource.getSkipped(processInstanceType, 0, 10);

    // then - verify empty state
    assertThat(migratedCount).isEqualTo(0L);
    assertThat(skippedCount).isEqualTo(0L);
    assertThat(migratedInstances.size()).isEqualTo(0);
    assertThat(skippedInstances.size()).isEqualTo(0);
  }

  protected void assertIdKeyDbModelListsEqual(List<IdKeyDbModel> expected, List<IdKeyDbModel> actual) {
    assertThat(actual).isNotNull();
    assertThat(expected).isNotNull();
    assertThat(actual.size()).isEqualTo(expected.size());

    if (expected.isEmpty()) {
      return;
    }

    // Sort both lists by ID for comparison
    List<IdKeyDbModel> sortedExpected = expected.stream()
        .sorted(Comparator.comparing(IdKeyDbModel::getC7Id))
        .toList();
    List<IdKeyDbModel> sortedActual = actual.stream()
        .sorted(Comparator.comparing(IdKeyDbModel::getC7Id))
        .toList();

    // Compare each element
    for (int i = 0; i < sortedExpected.size(); i++) {
      IdKeyDbModel expectedModel = sortedExpected.get(i);
      IdKeyDbModel actualModel = sortedActual.get(i);

      assertThat(actualModel.getC7Id()).isEqualTo(expectedModel.getC7Id());
      assertThat(actualModel.getC8Key()).isEqualTo(expectedModel.getC8Key());
      assertThat(actualModel.getType()).isEqualTo(expectedModel.getType());
      assertThat(actualModel.getSkipReason()).isEqualTo(expectedModel.getSkipReason());
    }
  }

  protected IdKeyDbModel createExpectedMigratedModel(String c7Id) {
    IdKeyDbModel model = new IdKeyDbModel();
    model.setC7Id(c7Id);
    model.setC8Key(getNextKey());
    model.setType(IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE);
    return model;
  }

  protected IdKeyDbModel createExpectedSkippedModel(String c7Id, String skipReason) {
    IdKeyDbModel model = createExpectedMigratedModel(c7Id);
    model.setSkipReason(skipReason);
    model.setC8Key(null);
    return model;
  }

  protected static void insertTestData(IdKeyDbModel idKeyDbModel) {
    try (Connection conn = DriverManager.getConnection(
        "jdbc:h2:mem:default-process-engine;DB_CLOSE_DELAY=-1", "sa", "")) {
      String insertSql = "INSERT INTO MIGRATION_MAPPING (C7_ID, C8_KEY, CREATE_TIME, TYPE, SKIP_REASON) VALUES (?, ?, ?, ?, ?)";
      try (var stmt = conn.prepareStatement(insertSql)) {
        stmt.setString(1, idKeyDbModel.getC7Id());
        stmt.setTimestamp(3, null);
        stmt.setString(4, String.valueOf(idKeyDbModel.getType()));
        stmt.setString(5, idKeyDbModel.getSkipReason());
        if (idKeyDbModel.getC8Key() == null) {
          stmt.setNull(2, java.sql.Types.BIGINT);
        } else {
          stmt.setLong(2, idKeyDbModel.getC8Key());
        }
        stmt.executeUpdate();
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to insert test data", e);
    }
  }

  protected void runLiquibaseMigrations() throws Exception {
    try (Connection conn = DriverManager.getConnection(
        "jdbc:h2:mem:default-process-engine;DB_CLOSE_DELAY=-1", "sa", "")) {

      Database database = DatabaseFactory.getInstance()
          .findCorrectDatabaseImplementation(new JdbcConnection(conn));
      try (Liquibase liquibase = new Liquibase(
          "db/changelog/migrator/db.changelog-master.yaml",
          new ClassLoaderResourceAccessor(),
          database)) {

        liquibase.getChangeLogParameters().set("prefix", "");
        liquibase.update((String) null);
      }
    }
  }

  protected CommandExecutor getCommandExecutor() {
    return Cockpit.getCommandExecutor("default");
  }
}
