/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime.variables.interceptor;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.impl.interceptor.PrimitiveVariableTransformer;
import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.qa.runtime.RuntimeMigrationAbstractTest;
import io.camunda.migration.data.qa.runtime.variables.interceptor.bean.DisabledCustomInterceptor;
import io.camunda.migration.data.qa.runtime.variables.interceptor.bean.StringOnlyInterceptor;
import io.camunda.migration.data.qa.runtime.variables.interceptor.bean.UniversalInterceptor;
import io.camunda.process.test.api.CamundaAssert;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    // Disable built-in interceptor for controlled testing
    "camunda.migrator.interceptors[0].className=io.camunda.migration.data.impl.interceptor.PrimitiveVariableTransformer",
    "camunda.migrator.interceptors[0].enabled=false",
    // Register interceptor and disable it
    "camunda.migrator.interceptors[1].className=io.camunda.migration.data.qa.runtime.variables.interceptor.bean.DisabledCustomInterceptor",
    "camunda.migrator.interceptors[1].enabled=false"
})
@ActiveProfiles("programmatic")
public class ProgrammaticConfigurationTest extends RuntimeMigrationAbstractTest {

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
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // Create process instance with primitive variable
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "primitiveVar", "originalValue");

    // Verify built-in PrimitiveVariableTransformer is disabled
    // If it were enabled, it would copy the variable as-is
    long disabledInterceptors = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof PrimitiveVariableTransformer)
        .count();

    assertThat(disabledInterceptors).isEqualTo(0); // Should be removed from context when disabled

    // Run migration
    runtimeMigrator.start();

    // Verify primitive variable was processed by our test interceptors instead
    // Since built-in is disabled, our universal and string only interceptor should have processed it
    assertThat(universalInterceptor.getExecutionCount()).isEqualTo(1);

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("primitiveVar", "STRING_originalValue");
  }

  @Test
  public void shouldExecuteInterceptorsOnlyForRespectiveTypes() {
    // Deploy process
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // Create process instance with different variable types
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "stringVar", "testString");
    runtimeService.setVariable(processInstance.getId(), "intVar", 42);
    runtimeService.setVariable(processInstance.getId(), "boolVar", true);

    // Run migration
    runtimeMigrator.start();

    // Verify string-specific interceptor only executed for string variable
    assertThat(stringOnlyInterceptor.getExecutionCount()).isEqualTo(1);
    // Verify universal interceptor executed for all variables
    assertThat(universalInterceptor.getExecutionCount()).isEqualTo(3);

    // Verify the primitive-only interceptor processed the variables
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("stringVar", "STRING_testString")
        .hasVariable("intVar", "UNIVERSAL_42")
        .hasVariable("boolVar", "UNIVERSAL_true");
  }

  @Test
  public void shouldEnableCustomInterceptorByDefault() {
    // Deploy process
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // Create process instance
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "testVar", "value");

    // Verify custom interceptors are enabled by default
    long enabledCustomInterceptors = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof UniversalInterceptor
                || interceptor instanceof StringOnlyInterceptor)
        .count();

    assertThat(enabledCustomInterceptors).isEqualTo(2); // All our test interceptors should be enabled

    // Run migration
    runtimeMigrator.start();

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
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "testVar", "value");

    // Run migration
    runtimeMigrator.start();

    // Verify the disabled interceptor did not execute
    // Variables should not have the "DISABLED_" prefix that would be added by DisablableCustomInterceptor
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("testVar", "STRING_value");
  }

  @Test
  public void shouldInvokeTestInterceptor() {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    deployer.deployProcessInC7AndC8("userTaskProcess.bpmn");
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "varIntercept", "value");
    simpleProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    runtimeService.setVariable(simpleProcessInstance.getId(), "varIntercept", "value");

    // when
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("varIntercept", "Hello");
    CamundaAssert.assertThat(byProcessId("userTaskProcessId"))
        .hasVariable("varIntercept", "Hello");
  }

  @Test
  public void shouldSkipProcessInstanceDueToExceptionFromInterceptor() {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "exFlag", true);
    runtimeMigrator.start();

    runtimeService.setVariable(simpleProcessInstance.getId(), "exFlag", false);

    // when
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasActiveElements(byId("userTask1"))
        .hasVariable("exFlag", "UNIVERSAL_false");
  }

}
