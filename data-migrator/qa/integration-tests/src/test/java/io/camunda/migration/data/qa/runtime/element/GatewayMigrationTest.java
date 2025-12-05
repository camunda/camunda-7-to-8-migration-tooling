/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime.element;

import static io.camunda.migration.data.constants.MigratorConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.migration.data.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR;
import static io.camunda.migration.data.impl.logging.RuntimeValidatorLogs.ACTIVE_JOINING_PARALLEL_GATEWAY_ERROR;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.qa.runtime.RuntimeMigrationAbstractTest;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.Map;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class GatewayMigrationTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Autowired
  protected RuntimeService runtimeService;


  @Autowired
  protected MigratorProperties migratorProperties;

  @AfterEach
  void cleanUp() {
    migratorProperties.setSaveSkipReason(Boolean.FALSE);
  }

  @Test
  public void shouldMigrateEventBasedActivityInstance() {
    // given
    deployer.deployProcessInC7AndC8("eventGateway.bpmn");

    // For C8 correlation variables are required
    Map<String, Object> variables = Variables.createVariables()
        .putValue("catchEvent1CorrelationVariable", "12345")
        .putValue("catchEvent2CorrelationVariable", 99.9);

    ProcessInstance instance = runtimeService.startProcessInstanceByKey("eventGatewayProcessId", variables);

    // when
    runtimeMigrator.start();

    // then
    assertThat(byProcessId("eventGatewayProcessId")).isActive()
        .hasActiveElements(byId("eventGatewayElementId"))
        .hasVariables(Map.of(
            LEGACY_ID_VAR_NAME, instance.getProcessInstanceId(),
            "catchEvent1CorrelationVariable", "12345",
            "catchEvent2CorrelationVariable", 99.9
        ));
    
  }
  
  @Test
  @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/321")
  public void shouldSkipActiveParallelGatewayActivityInstance() {
    // given
    migratorProperties.setSaveSkipReason(true);
    deployer.deployProcessInC7AndC8("parallelGateway.bpmn");

    ProcessInstance instance = runtimeService.startProcessInstanceByKey("ParallelGatewayProcess");

    // when
    runtimeMigrator.start();

    // then
    logs.assertDoesNotContain(formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, instance.getId(),
        formatMessage(ACTIVE_JOINING_PARALLEL_GATEWAY_ERROR, "mergingGatewayActivity", instance.getId())));
  }
}
