/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.client.api.search.response.SearchResponsePage;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.Date;
import java.util.function.Supplier;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@CamundaSpringProcessTest
class MigrationOrderedByCreateTimeTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final RuntimeMigrationExtension runtimeMigration = new RuntimeMigrationExtension();

  @Test
  public void shouldMigrateStartedBetweenRuns() {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.startProcessInstanceByKey("simpleProcess");

    runtimeMigration.getMigrator().start();

    Supplier<SearchResponsePage> response = () -> runtimeMigration.getCamundaClient().newProcessInstanceSearchRequest().execute().page();

    // assume
    assertThat(response.get().totalItems()).isEqualTo(2);

    runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.startProcessInstanceByKey("simpleProcess");

    // when
    runtimeMigration.getMigrator().start();

    // then
    assertThat(response.get().totalItems()).isEqualTo(5);
  }

  @Test
  public void shouldMigrateWithSameCreateTime() {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    runtimeService.startProcessInstanceByKey("simpleProcess");

    ClockUtil.setCurrentTime(new Date());
    runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.startProcessInstanceByKey("simpleProcess");

    ClockUtil.offset(1_000 * 4L);
    runtimeService.startProcessInstanceByKey("simpleProcess");

    // when
    runtimeMigration.getMigrator().start();

    Supplier<SearchResponsePage> response = () -> runtimeMigration.getCamundaClient().newProcessInstanceSearchRequest().execute().page();

    // then
    assertThat(response.get().totalItems()).isEqualTo(5);
  }

  @Test
  public void shouldRerunWithDifferentCreateTimeProcessInstancesMigratedAndValidationFailure() {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    ClockUtil.setCurrentTime(new Date());
    runtimeService.startProcessInstanceByKey("simpleProcess");
    ClockUtil.offset(1_000 * 4L);
    runtimeService.startProcessInstanceByKey("simpleProcess");

    runtimeMigration.getMigrator().start();

    Supplier<SearchResponsePage> response = () -> runtimeMigration.getCamundaClient().newProcessInstanceSearchRequest().execute().page();

    // assume
    assertThat(response.get().totalItems()).isEqualTo(2);

    deployer.deployCamunda8Process("simpleProcessWithoutListener.bpmn");

    // when
    runtimeMigration.getMigrator().start();

    // then
    assertThat(response.get().totalItems()).isEqualTo(2);
  }

  @Test
  public void shouldRerunWithDifferentCreateTimeWithProcessInstancesSkippedAndValidationFailure() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    deployer.deployCamunda8Process("simpleProcessWithoutListener.bpmn");

    ClockUtil.setCurrentTime(new Date());
    runtimeService.startProcessInstanceByKey("simpleProcess");
    ClockUtil.offset(1_000 * 4L);
    runtimeService.startProcessInstanceByKey("simpleProcess");

    runtimeMigration.getMigrator().start();

    Supplier<SearchResponsePage> response = () -> runtimeMigration.getCamundaClient().newProcessInstanceSearchRequest().execute().page();

    // assume
    assertThat(response.get().totalItems()).isEqualTo(0);

    deployer.deployCamunda8Process("simpleProcessMissingTask.bpmn");

    // when
    runtimeMigration.getMigrator().start();

    // then
    assertThat(response.get().totalItems()).isEqualTo(0);
  }

  @Test
  public void shouldRerunSameCreateTimeWithProcessInstancesMigratedAndValidationFailure() {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    ClockUtil.setCurrentTime(new Date());
    runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.startProcessInstanceByKey("simpleProcess");

    runtimeMigration.getMigrator().start();

    Supplier<SearchResponsePage> response = () -> runtimeMigration.getCamundaClient().newProcessInstanceSearchRequest().execute().page();

    // assume
    assertThat(response.get().totalItems()).isEqualTo(2);

    deployer.deployCamunda8Process("simpleProcessWithoutListener.bpmn");

    // when
    runtimeMigration.getMigrator().start();

    // then
    assertThat(response.get().totalItems()).isEqualTo(2);
  }

  @Test
  public void shouldRerunWithSameCreateTimeWithProcessInstancesSkippedAndValidationFailure() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    deployer.deployCamunda8Process("simpleProcessWithoutListener.bpmn");

    ClockUtil.setCurrentTime(new Date());
    runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.startProcessInstanceByKey("simpleProcess");

    runtimeMigration.getMigrator().start();

    Supplier<SearchResponsePage> response = () -> runtimeMigration.getCamundaClient().newProcessInstanceSearchRequest().execute().page();

    // assume
    assertThat(response.get().totalItems()).isEqualTo(0);

    deployer.deployCamunda8Process("simpleProcessMissingTask.bpmn");

    // when
    runtimeMigration.getMigrator().start();

    // then
    assertThat(response.get().totalItems()).isEqualTo(0);
  }


}
