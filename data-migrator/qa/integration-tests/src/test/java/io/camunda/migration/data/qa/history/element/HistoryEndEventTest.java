/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.qa.history.element;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.END_EVENT;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class HistoryEndEventTest extends HistoryAbstractElementMigrationTest {

  @Override
  protected Stream<Arguments> elementScenarios_completedElementPostMigration() {
    return Stream.of(Arguments.of("serviceTaskProcessExpr.bpmn", "serviceTaskProcessId", END_EVENT));
  }
}

