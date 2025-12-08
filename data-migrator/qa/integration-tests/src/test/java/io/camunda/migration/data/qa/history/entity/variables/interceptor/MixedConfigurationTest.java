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
import io.camunda.migration.data.qa.util.WithSpringProfile;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import io.camunda.search.entities.VariableEntity;
import java.util.List;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "camunda.migrator.interceptors[0].className=io.camunda.migrator.qa.runtime.variables.interceptor.bean.DisabledCustomInterceptor",
    "camunda.migrator.interceptors[0].enabled=false",
    "camunda.migrator.interceptors[1].className=io.camunda.migrator.qa.runtime.variables.interceptor.bean.StringOnlyInterceptor",
    "camunda.migrator.interceptors[1].enabled=false",
    "camunda.migrator.interceptors[2].className=io.camunda.migrator.qa.runtime.variables.interceptor.bean.UniversalInterceptor",
    "camunda.migrator.interceptors[2].enabled=false",
    "camunda.migrator.interceptors[3].className=io.camunda.migrator.qa.runtime.variables.interceptor.pojo.ComplexInterceptor"
})
@WithSpringProfile("interceptor")
@ActiveProfiles("programmatic")
public class MixedConfigurationTest extends AbstractMigratorTest {

  @RegisterExtension
  HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @Test
  public void shouldWorkAlongsideSpringComponentInterceptors(CapturedOutput output) {
    // This test verifies that configured interceptors work alongside Spring @Component interceptors
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    // Set up variables that will trigger both types of interceptors
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "varIntercept", "springValue"); // For ComplexVariableInterceptor (@Component)
    runtimeService.setVariable(processInstance.getId(), "var", "value"); // For ComplexInterceptor (declarative)

    // when running history migration
    historyMigration.getMigrator().migrate();

    // then both interceptors should have executed
    assertVariableExists("varIntercept", "\"Hello\""); // Transformed by Spring @Component interceptor
    assertVariableExists("var", "\"transformedValue\""); // Transformed by declarative interceptor

    // verify both interceptors logged their messages
    assertThat(output.getOut()).contains("Hello from interceptor"); // From ComplexVariableInterceptor
    assertThat(output.getOut()).contains("Hello from declarative interceptor configured via properties"); // From declarative interceptor
  }

  protected void assertVariableExists(String varName, Object expectedValue) {
    List<VariableEntity> variables = historyMigration.searchHistoricVariables(varName);
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.getFirst();
    assertThat(variable.name()).isEqualTo(varName);
    assertThat(variable.value()).isEqualTo(expectedValue);
  }

}
