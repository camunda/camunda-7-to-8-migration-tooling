/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.qa.runtime;

import static io.camunda.migration.data.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR;
import static io.camunda.migration.data.impl.logging.RuntimeValidatorLogs.FLOW_NODE_NOT_EXISTS_ERROR;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@CamundaSpringProcessTest
public class ProcessElementNotFoundTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final RuntimeMigrationExtension runtimeMigration = new RuntimeMigrationExtension();

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Test
  public void shouldSkipOnMissingElementInC8Deployment() {
    // given an instance that is currently in an element in the C7 model which does not exist in the C8 model
    deployer.deployProcessInC7AndC8("userTaskProcessWithMissingUserTaskInC8.bpmn");
    var c7Instance = runtimeService.startProcessInstanceByKey("userTaskProcessWithMissingUserTaskInC8Id");

    // when
    runtimeMigration.getMigrator().start();

    // then
    logs.assertContains(
        formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, c7Instance.getId(),
            formatMessage(FLOW_NODE_NOT_EXISTS_ERROR, "userTaskId")));
    runtimeMigration.assertThatProcessInstanceCountIsEqualTo(0);
  }

}
