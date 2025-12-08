/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.qa.runtime.jobtype;

import static io.camunda.migration.data.impl.logging.RuntimeMigratorLogs.EXTERNALLY_STARTED_PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.RuntimeMigrationExtension;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@CamundaSpringProcessTest
public class ExternalTrafficTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final RuntimeMigrationExtension runtimeMigration = new RuntimeMigrationExtension();

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Test
  public void shouldHandleExternallyStartedMigratorJobsGracefully() {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    String id = runtimeService.startProcessInstanceByKey("simpleProcess").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    runtimeMigration.getCamundaClient().newCreateInstanceCommand().bpmnProcessId("simpleProcess").latestVersion().execute();

    // when
    runtimeMigration.getMigrator().start();

    // then
    runtimeMigration.assertThatProcessInstanceCountIsEqualTo(2);

    var events = logs.getEvents();
    assertThat(events.stream()
        .filter(event -> event.getMessage()
            .matches(".*" + EXTERNALLY_STARTED_PROCESS_INSTANCE
                .replace(".", "\\.")
                .replace("[{}]", "\\[(\\d+)\\]"))))
        .hasSize(1);
  }

}
