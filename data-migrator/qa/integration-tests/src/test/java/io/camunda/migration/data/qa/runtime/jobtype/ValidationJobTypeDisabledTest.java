/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.date.runtime.jobtype;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.date.runtime.RuntimeMigrationAbstractTest;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.validation-job-type=DISABLED"
})
public class ValidationJobTypeDisabledTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Test
  public void shouldSucceedIfNoMigratorListenerExists() {
    // given
    deployer.deployProcessInC7AndC8("noMigratorListener.bpmn");

    String id = runtimeService.startProcessInstanceByKey("noMigratorListener").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(1);
  }

  @Test
  public void shouldSucceedIfCustomMigratorListenerTypeExists() {
    // given
    deployer.deployProcessInC7AndC8("migratorListenerCustomType.bpmn");

    String id = runtimeService.startProcessInstanceByKey("migratorListenerCustomType").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(1);
  }

  @Test
  public void shouldSucceedIfListenerUsesFeel() {
    // given
    deployer.deployProcessInC7AndC8("migratorListenerFeel.bpmn");

    String id = runtimeService.startProcessInstanceByKey("migratorListenerFeel").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(1);
  }

}
