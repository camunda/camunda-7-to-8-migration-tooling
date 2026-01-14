/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.element;

import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.RECEIVE_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.SEND_TASK;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.USER_TASK;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class HistoryTaskMigrationTest extends HistoryAbstractElementMigrationTest {

  @Override
  protected Stream<Arguments> elementScenarios_terminatedElementPostMigration() {
    return Stream.of(Arguments.of("sendTaskProcess.bpmn", "sendTaskProcessId", SEND_TASK),
        Arguments.of("receiveTaskProcess.bpmn", "receiveTaskProcessId", RECEIVE_TASK),
        Arguments.of("userTaskProcess.bpmn", "userTaskProcessId", USER_TASK)
//        ,
//        Arguments.of("serviceTaskProcess.bpmn", "serviceTaskProcessId", SERVICE_TASK) // doesn't create a task due to async before
    );
  }


}

