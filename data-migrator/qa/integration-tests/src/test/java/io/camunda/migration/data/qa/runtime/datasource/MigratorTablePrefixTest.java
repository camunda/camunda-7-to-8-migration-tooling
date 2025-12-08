/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.qa.runtime.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.RuntimeMigrationExtension;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.table-prefix=MY_PREFIX_",
    "logging.level.io.camunda.migration.data.impl.persistence.IdKeyMapper=DEBUG"
})
@CamundaSpringProcessTest
public class MigratorTablePrefixTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final RuntimeMigrationExtension runtimeMigration = new RuntimeMigrationExtension();

  protected static final String TABLE_PREFIX_INSERT_PATTERN = ".*INSERT INTO MY_PREFIX_MIGRATION_MAPPING.*";

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(IdKeyMapper.class, Level.DEBUG);

  @Test
  public void shouldMigrateWithMigratorTablePrefix() {
    // given
    deployer.deployProcessInC7AndC8("messageStartEventProcess.bpmn");
    runtimeService.correlateMessage("msgRef");

    // when
    runtimeMigration.getMigrator().start();

    // then
    runtimeMigration.assertThatProcessInstanceCountIsEqualTo(0);

    var events = logs.getEvents();
    assertThat(events.stream().filter(event -> event.getMessage()
        .matches(TABLE_PREFIX_INSERT_PATTERN)))
        .hasSize(1);
  }

}
