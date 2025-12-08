/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.variables.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceInputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceOutputEntity;
import io.camunda.search.entities.VariableEntity;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.TestPropertySource;


@TestPropertySource(properties = {
    "camunda.migrator.interceptors[0].class-name=io.camunda.migrator.qa.runtime.variables.interceptor.pojo.DecisionVariableOnlyInterceptor"
})
@Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/523")
public class DecisionVariableFilteringTest extends AbstractMigratorTest {

  @RegisterExtension
  HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @Test
  public void shouldFilterDecisionInputsAndOutputsSeparately() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");

    Map<String, Object> variables = Variables.createVariables()
        .putValue("inputA", stringValue("A"));

    runtimeService.startProcessInstanceByKey("businessRuleProcessId", variables);

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> processVars = historyMigration.searchHistoricVariables("inputA");
    assertThat(processVars).hasSize(1);
    assertThat(processVars.getFirst().value()).isEqualTo("\"A\"");

    var decisionInstances = historyMigration.searchHistoricDecisionInstances("simpleDecisionId");
    assertThat(decisionInstances).hasSize(1);

    List<DecisionInstanceInputEntity> inputs = decisionInstances.getFirst().evaluatedInputs();
    assertThat(inputs).hasSize(1);
    decisionInstances.getFirst().evaluatedInputs().forEach(input -> {
      assertThat(input.inputValue()).isEqualTo("\"DECISION_A\"");
    });

    List<DecisionInstanceOutputEntity> outputs = decisionInstances.getFirst().evaluatedOutputs();
    assertThat(outputs).hasSize(1);
    outputs.forEach(output -> {
      assertThat(output.outputValue()).isEqualTo("\"DECISION_B\"");
    });
  }

}

