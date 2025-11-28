/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.qa.runtime.element;

import static io.camunda.migrator.constants.MigratorConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

import io.camunda.migrator.qa.runtime.RuntimeMigrationAbstractTest;
import java.util.stream.Stream;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractElementMigrationTest extends RuntimeMigrationAbstractTest {

  @EnabledIf("hasScenarios_activeElementPostMigration")
  @MethodSource("elementScenarios_activeElementPostMigration")
  @ParameterizedTest
  public void migrateSimpleElementScenarios_expectActiveElement(final String processFile,
                                                                final String processId,
                                                                final String elementId) {
    // given
    deployer.deployProcessInC7AndC8(processFile);
    ProcessInstance instance = runtimeService.startProcessInstanceByKey(processId);

    // when
    runtimeMigrator.start();

    // then
    assertThat(byProcessId(processId)).isActive()
        .hasActiveElements(byId(elementId))
        .hasVariable(LEGACY_ID_VAR_NAME, instance.getProcessInstanceId());
  }

  @EnabledIf("hasScenarios_completedElementPostMigration")
  @MethodSource("elementScenarios_completedElementPostMigration")
  @ParameterizedTest
  public void migrateSimpleElementScenarios_expectCompletedElement(final String processFile,
                                                                   final String processId,
                                                                   final String elementId) {
    // given
    deployer.deployProcessInC7AndC8(processFile);
    ProcessInstance instance = runtimeService.startProcessInstanceByKey(processId);

    // when
    runtimeMigrator.start();

    // then
    assertThat(byProcessId(processId)).hasCompletedElement(elementId, 1)
        .hasVariable(LEGACY_ID_VAR_NAME, instance.getProcessInstanceId());
  }

  /**
   * Test cases for elements with a natural wait state in C7 and C8.
   * Post migration we expect an active process instance in the same element.
   *
   * @return Stream of 3 String arguments: processFile, processId, elementId
   */
  protected Stream<Arguments> elementScenarios_activeElementPostMigration() {
    return Stream.empty();
  }

  /**
   * Test cases for elements with a wait state in C7 but no wait state in C8.
   * Post migration we expect the process instance to have completed the element.
   *
   * @return Stream of 3 String arguments: processFile, processId, elementId
   */
  protected Stream<Arguments> elementScenarios_completedElementPostMigration() {
    return Stream.empty();
  }

  private boolean hasScenarios_activeElementPostMigration(){
    return elementScenarios_activeElementPostMigration().findAny().isPresent();
  }

  private boolean hasScenarios_completedElementPostMigration(){
    return elementScenarios_completedElementPostMigration().findAny().isPresent();
  }
}
