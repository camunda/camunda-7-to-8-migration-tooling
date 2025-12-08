/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.variables.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import io.camunda.search.entities.VariableEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.interceptors[0].class-name=io.camunda.migrator.qa.runtime.variables.interceptor.pojo.HistoricVariableOnlyInterceptor"
})
public class EntityTypeFilteringTest extends AbstractMigratorTest {

  @RegisterExtension
  HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @Test
  public void shouldFilterByHistoricVariableEntityType() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("simpleProcess", Map.of("histVar", "histValue"));

    // when
    historyMigration.getMigrator().migrate();

    List<VariableEntity> variables = historyMigration.searchHistoricVariables("histVar");
    assertThat(variables).hasSize(1);
    assertThat(variables.getFirst().value()).isEqualTo("\"HISTORIC_histValue\"");
  }

  @Test
  public void shouldHandleMultipleVariablesWithEntityFiltering() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    Map<String, Object> vars = Map.of(
        "var1", "value1",
        "var2", "value2",
        "var3", "value3"
    );

    runtimeService.startProcessInstanceByKey("simpleProcess", vars);

    // when
    historyMigration.getMigrator().migrate();

    // then - all variables should be processed by the interceptors
    assertVariableExists("var1", "\"HISTORIC_value1\"");
    assertVariableExists("var2", "\"HISTORIC_value2\"");
    assertVariableExists("var3", "\"HISTORIC_value3\"");
  }

  protected void assertVariableExists(String varName, String expectedValue) {
    List<VariableEntity> variables = historyMigration.searchHistoricVariables(varName);
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.getFirst();
    assertThat(variable.name()).isEqualTo(varName);
    assertThat(variable.value()).isEqualTo(expectedValue);
  }
}
