/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.interceptor;

import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import io.camunda.migration.data.qa.extension.RuntimeMigrationExtension;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.interceptors[0].class-name=io.camunda.migrator.qa.runtime.variables.interceptor.pojo.MixedVariableInterceptor"
})
@CamundaSpringProcessTest
public class MixedEntityTypeFilteringTest extends AbstractMigratorTest {

  @RegisterExtension
  RuntimeMigrationExtension runtimeMigration = new RuntimeMigrationExtension();

  @RegisterExtension
  HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @Test
  public void shouldFilterByProcessVariableEntityTypes() {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("simpleProcess", Map.of("testVar", "value1"));

    // when
    runtimeMigration.getMigrator().start();
    historyMigration.getMigrator().start();

    // then
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("testVar", "MIXED_value1");

    assertThat(historyMigration.searchHistoricVariables("testVar").getFirst().value())
        .isEqualTo("\"MIXED_value1\"");
  }

}

