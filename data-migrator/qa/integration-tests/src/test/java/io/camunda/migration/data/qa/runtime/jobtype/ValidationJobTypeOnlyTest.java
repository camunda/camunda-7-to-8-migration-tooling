/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.qa.runtime.jobtype;

import static io.camunda.migration.data.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR;
import static io.camunda.migration.data.impl.logging.RuntimeValidatorLogs.NO_EXECUTION_LISTENER_OF_TYPE_ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.RuntimeMigrationExtension;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.validation-job-type==if legacyId != null then \"migrator\" else \"noop\""
})
@CamundaSpringProcessTest
public class ValidationJobTypeOnlyTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final RuntimeMigrationExtension runtimeMigration = new RuntimeMigrationExtension();

  protected static final String VALIDATION_JOB_TYPE = "=if legacyId != null then \"migrator\" else \"noop\"";

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Test
  public void shouldUseCustomValidationJobTypeWithDefaultActivationType() {
    // given
    deployer.deployProcessInC7AndC8("noMigratorListener.bpmn");

    String id = runtimeService.startProcessInstanceByKey("noMigratorListener").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigration.getMigrator().start();

    // then
    runtimeMigration.assertThatProcessInstanceCountIsEqualTo(0);

    var events = logs.getEvents();
    assertThat(events.stream()
        .filter(event -> event.getMessage()
            .matches(String.format(".*" + String.format(
                SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("[{}]", "\\[%s\\]").replace("{}", "%s"), id,
                String.format(
                    NO_EXECUTION_LISTENER_OF_TYPE_ERROR.replace(".", "\\.").replace("[", "\\[").replace("]", "\\]"),
                    VALIDATION_JOB_TYPE, "Event_1px2j50", "noMigratorListener", 1, VALIDATION_JOB_TYPE)))))).hasSize(1);
  }

  @Test
  public void shouldUseCustomValidationJobTypeInListenerNotFoundMessage() {
    // given
    deployer.deployProcessInC7AndC8("migratorListenerCustomType.bpmn");

    String id = runtimeService.startProcessInstanceByKey("migratorListenerCustomType").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigration.getMigrator().start();

    // then
    runtimeMigration.assertThatProcessInstanceCountIsEqualTo(0);

    var events = logs.getEvents();
    assertThat(events.stream()
        .filter(event -> event.getMessage()
            .matches(String.format(".*" + String.format(
                SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("[{}]", "\\[%s\\]").replace("{}", "%s"), id,
                String.format(
                    NO_EXECUTION_LISTENER_OF_TYPE_ERROR.replace(".", "\\.").replace("[", "\\[").replace("]", "\\]"),
                    VALIDATION_JOB_TYPE, "Event_1px2j50", "migratorListenerCustomType", 1, VALIDATION_JOB_TYPE)))))).hasSize(1);
  }

  @Test
  public void shouldUseCustomValidationJobTypeInListenerSucceed() {
    // given
    deployer.deployProcessInC7AndC8("migratorListenerFeel.bpmn");

    String id = runtimeService.startProcessInstanceByKey("migratorListenerFeel").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigration.getMigrator().start();

    // then
    runtimeMigration.assertThatProcessInstanceCountIsEqualTo(1);
  }

}
