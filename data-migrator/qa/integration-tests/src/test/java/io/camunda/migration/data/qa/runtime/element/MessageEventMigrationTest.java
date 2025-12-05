/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.date.runtime.element;

import static io.camunda.migration.data.constants.MigratorConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

import java.util.Map;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;

public class MessageEventMigrationTest extends AbstractElementMigrationTest {

  @Test
  public void shouldMigrateMessageCatchEvent() {
    // given
    deployer.deployProcessInC7AndC8("messageCatchEventProcess.bpmn");
    ProcessInstance instance = runtimeService.startProcessInstanceByKey("messageCatchEventProcessId");
    runtimeService.setVariable(instance.getId(), "messageRef", "aMessageRef");

    // when
    runtimeMigrator.start();

    // then
    assertThat(byProcessId("messageCatchEventProcessId")).isActive()
        .hasActiveElements(byId("messageCatchEventId"))
        .hasVariables(Map.of(LEGACY_ID_VAR_NAME, instance.getProcessInstanceId(), "messageRef", "aMessageRef"));
  }
}
