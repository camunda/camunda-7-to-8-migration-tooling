/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.runtime.element;

import static io.camunda.migrator.constants.MigratorConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.ACTIVE_JOINING_PARALLEL_GATEWAY_ERROR;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.qa.runtime.RuntimeMigrationAbstractTest;
import java.util.Map;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class GatewayMigrationTest extends RuntimeMigrationAbstractTest {

  @Autowired
  private RuntimeService runtimeService;


  @Autowired
  private MigratorProperties migratorProperties;

  @AfterEach
  void cleanUp() {
    migratorProperties.setSaveSkipReason(Boolean.FALSE);
  }

  @Test
  public void migrateEventBasedActivityInstance() {
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
  public void activeParallelGatewayActivityInstanceIsSkipped() {
    // currently parallel gateways are not supported for migration
    // TODO follow up to support parallel gateways: camunda-bpm-platform/issues/5461
    // given
    migratorProperties.setSaveSkipReason(true);
    deployer.deployProcessInC7AndC8("parallelGateway.bpmn");

    ProcessInstance instance = runtimeService.startProcessInstanceByKey("ParallelGatewayProcess");

    // when
    runtimeMigrator.start();

    // then - the instance is skipped
    IdKeyDbModel idKeyDbModel = dbClient.findSkippedProcessInstances().stream()
        .filter(skipped -> skipped.getC7Id().equals(instance.getProcessInstanceId()))
        .findFirst().get();

    Assertions.assertNull(idKeyDbModel.getC8Key());
    Assertions.assertEquals(String.format(ACTIVE_JOINING_PARALLEL_GATEWAY_ERROR, "mergingGatewayActivity", instance.getId()),
        idKeyDbModel.getSkipReason());
  }
}
