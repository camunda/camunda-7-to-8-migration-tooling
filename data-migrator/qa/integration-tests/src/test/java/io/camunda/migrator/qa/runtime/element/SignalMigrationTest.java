/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.qa.runtime.element;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class SignalMigrationTest extends AbstractElementMigrationTest {

  @Override
  protected Stream<Arguments> elementScenarios_activeElementPostMigration() {
    return Stream.of(Arguments.of("signalCatchProcess.bpmn", "signalCatchProcessId", "signalCatchEventId"));
  }
}
