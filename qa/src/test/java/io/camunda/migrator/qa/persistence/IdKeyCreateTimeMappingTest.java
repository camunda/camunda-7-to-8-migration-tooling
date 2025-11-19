/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.HistoryMigrator;
import io.camunda.migrator.qa.history.HistoryMigrationAbstractTest;
import io.github.netmikey.logunit.api.LogCapturer;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;

/**
 * Test to verify that migration correctly processes entities with proper timing.
 * Verifies that entities created before migration are successfully migrated.
 */
public class IdKeyCreateTimeMappingTest extends HistoryMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

  @Test
  public void shouldCorrectlyMapCreateTimeDuringActualMigration() {

    // Given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    String processInstanceId = runtimeService.startProcessInstanceByKey("simpleProcess").getId();
    // Record the time before migration for comparison
    Date beforeMigration = new Date();

    // When: Run the history migration
    historyMigrator.migrate();

    // Then: Verify that migration completed successfully via logs
    logs.assertContains("Migration of historic process instance with C7 ID [" + processInstanceId + "] completed");

    // Verify the process instance was migrated (not skipped)
    assertThat(logs.getEvents().stream()
        .anyMatch(event -> event.getMessage().contains("Migration of historic process instance with C7 ID [" + processInstanceId + "]")
            && event.getMessage().contains("skipped")))
        .isFalse();

    // Verify migration was successful and entity has proper timing
    var processInstances = searchHistoricProcessInstances("simpleProcess");
    assertThat(processInstances).hasSize(1);
    var migratedInstance = processInstances.getFirst();
    assertThat(migratedInstance.processInstanceKey()).isNotNull().isPositive();
    assertThat(migratedInstance.startDate()).isNotNull()
        .isBeforeOrEqualTo(OffsetDateTime.ofInstant(beforeMigration.toInstant(), ZoneId.systemDefault()));
  }
}
