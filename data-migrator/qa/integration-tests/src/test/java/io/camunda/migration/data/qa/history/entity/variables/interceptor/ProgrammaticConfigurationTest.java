/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.variables.interceptor;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.impl.interceptor.PrimitiveVariableTransformer;
import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.runtime.variables.interceptor.bean.DisabledCustomInterceptor;
import io.camunda.migration.data.qa.runtime.variables.interceptor.bean.StringOnlyInterceptor;
import io.camunda.migration.data.qa.runtime.variables.interceptor.bean.UniversalInterceptor;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import io.camunda.search.entities.VariableEntity;
import java.util.List;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    // Disable built-in interceptor for controlled testing
    "camunda.migrator.interceptors[0].className=io.camunda.migrator.impl.interceptor.PrimitiveVariableTransformer",
    "camunda.migrator.interceptors[0].enabled=false",
    // Register interceptor and disable it
    "camunda.migrator.interceptors[1].className=io.camunda.migrator.qa.runtime.variables.interceptor.bean.DisabledCustomInterceptor",
    "camunda.migrator.interceptors[1].enabled=false"
})
@ActiveProfiles("programmatic")
public class ProgrammaticConfigurationTest extends AbstractMigratorTest {

  @RegisterExtension
  HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @Autowired
  protected List<VariableInterceptor> configuredVariableInterceptors;

  @Autowired
  protected UniversalInterceptor universalInterceptor;

  @Autowired
  protected StringOnlyInterceptor stringOnlyInterceptor;

  @BeforeEach
  void setUp() {
    // Reset counters before each test
    universalInterceptor.resetCounter();
    stringOnlyInterceptor.resetCounter();
  }

  @Test
  public void shouldDisableBuiltInInterceptor() {
    // Deploy process
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    // Create process instance with primitive variable
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "primitiveVar", "originalValue");

    // Verify built-in PrimitiveVariableTransformer is disabled
    // If it were enabled, it would copy the variable as-is
    long disabledInterceptors = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof PrimitiveVariableTransformer)
        .count();

    assertThat(disabledInterceptors).isEqualTo(0); // Should be removed from context when disabled

    // Run migration
    historyMigration.getMigrator().migrate();

    // Verify primitive variable was processed by our test interceptors instead
    // Since built-in is disabled, our universal and string only interceptor should have processed it
    assertThat(universalInterceptor.getExecutionCount()).isEqualTo(1);

    assertVariableExists("primitiveVar", "\"STRING_originalValue\"");
  }

  @Test
  public void shouldExecuteInterceptorsOnlyForRespectiveTypes() {
    // Deploy process
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    // Create process instance with different variable types
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "stringVar", "testString");
    runtimeService.setVariable(processInstance.getId(), "intVar", 42);
    runtimeService.setVariable(processInstance.getId(), "boolVar", true);

    // Run migration
    historyMigration.getMigrator().migrate();

    // Verify string-specific interceptor only executed for string variable
    assertThat(stringOnlyInterceptor.getExecutionCount()).isEqualTo(1);
    // Verify universal interceptor executed for all variables
    assertThat(universalInterceptor.getExecutionCount()).isEqualTo(3);

    // Verify the primitive-only interceptor processed the variables
    assertVariableExists("stringVar", "\"STRING_testString\"");
    assertVariableExists("intVar", "\"UNIVERSAL_42\"");
    assertVariableExists("boolVar", "\"UNIVERSAL_true\"");
  }

  @Test
  public void shouldEnableCustomInterceptorByDefault() {
    // Deploy process
    deployer.deployCamunda7Process("simpleProcess.bpmn");

    // Create process instance
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "testVar", "value");

    // Verify custom interceptors are enabled by default
    long enabledCustomInterceptors = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof UniversalInterceptor
                || interceptor instanceof StringOnlyInterceptor)
        .count();

    assertThat(enabledCustomInterceptors).isEqualTo(2); // All our test interceptors should be enabled

    // Run migration
    historyMigration.getMigrator().migrate();

    // Verify our custom interceptors executed
    assertThat(stringOnlyInterceptor.getExecutionCount()).isEqualTo(1);
    assertThat(universalInterceptor.getExecutionCount()).isEqualTo(1);
  }

  @Test
  public void shouldDisableCustomInterceptorViaConfiguration() {
    // The DisablableCustomInterceptor is disabled via @TestPropertySource

    // Verify disabled custom interceptor is not in the context
    long disabledCustomInterceptors = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof DisabledCustomInterceptor)
        .count();

    assertThat(disabledCustomInterceptors).isEqualTo(0); // Should be removed when disabled

    // Deploy process and create variables
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "testVar", "value");

    // Run migration
    historyMigration.getMigrator().migrate();

    // Verify the disabled interceptor did not execute
    // Variables should not have the "DISABLED_" prefix that would be added by DisablableCustomInterceptor
    assertVariableExists("testVar", "\"STRING_value\"");
  }

  @Test
  public void shouldInvokeTestInterceptor() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "varIntercept", "value");
    simpleProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    runtimeService.setVariable(simpleProcessInstance.getId(), "varIntercept", "value");

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<VariableEntity> variables = historyMigration.searchHistoricVariables("varIntercept");
    assertThat(variables).hasSize(2);
    assertThat(variables.getFirst().value()).isEqualTo("\"Hello\"");
    assertThat(variables.get(1).value()).isEqualTo("\"Hello\"");
  }

  @Test
  public void shouldSkipProcessInstanceDueToExceptionFromInterceptor() {
    // given
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    ProcessInstance simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "exFlag", true);
    historyMigration.getMigrator().migrate();

    runtimeService.setVariable(simpleProcessInstance.getId(), "exFlag", false);

    // when
    historyMigration.getMigrator().setMode(RETRY_SKIPPED);
    historyMigration.getMigrator().migrate();

    // then
    assertVariableExists("exFlag", "\"UNIVERSAL_false\"");
  }

  protected void assertVariableExists(String varName, Object expectedValue) {
    List<VariableEntity> variables = historyMigration.searchHistoricVariables(varName);
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.getFirst();
    assertThat(variable.name()).isEqualTo(varName);
    assertThat(variable.value()).isEqualTo(expectedValue);
  }

}
