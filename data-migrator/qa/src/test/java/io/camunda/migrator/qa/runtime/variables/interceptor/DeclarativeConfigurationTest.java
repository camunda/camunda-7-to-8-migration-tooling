/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.runtime.variables.interceptor;

import static io.camunda.migrator.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migrator.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR;
import static io.camunda.migrator.impl.logging.VariableServiceLogs.VARIABLE_INTERCEPTOR_FAILED_MSG;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.interceptor.DateVariableTransformer;
import io.camunda.migrator.impl.interceptor.PrimitiveVariableTransformer;
import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.qa.runtime.RuntimeMigrationAbstractTest;
import io.camunda.migrator.qa.runtime.variables.interceptor.pojo.CustomTestInterceptor;
import io.camunda.migrator.qa.runtime.variables.interceptor.pojo.DisabledTestInterceptor;
import io.camunda.migrator.qa.runtime.variables.interceptor.pojo.UniversalTestInterceptor;
import io.camunda.migrator.qa.runtime.variables.interceptor.pojo.ComplexInterceptor;
import io.camunda.migrator.qa.util.WithSpringProfile;
import io.camunda.process.test.api.CamundaAssert;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@WithSpringProfile("interceptor")
public class DeclarativeConfigurationTest extends RuntimeMigrationAbstractTest {

  @Autowired
  protected MigratorProperties migratorProperties;

  @Autowired
  protected List<VariableInterceptor> configuredVariableInterceptors;

  @Test
  public void shouldLoadVariableInterceptorFromConfiguration() {
    // Verify that the configuration is loaded correctly
    assertThat(migratorProperties.getInterceptors()).isNotNull();
    assertThat(migratorProperties.getInterceptors()).hasSize(7);

    var interceptor = migratorProperties.getInterceptors().getLast();
    assertThat(interceptor.getClassName()).isEqualTo("io.camunda.migrator.qa.runtime.variables.interceptor.pojo.ComplexInterceptor");
    assertThat(interceptor.getProperties()).containsEntry("targetVariable", "var");
  }

  @Test
  public void shouldDisableBuiltInInterceptorsViaConfig() {
    // Verify that built-in interceptors specified as disabled are not in the context
    long primitiveTransformers = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof PrimitiveVariableTransformer)
        .count();

    long dateTransformers = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof DateVariableTransformer)
        .count();

    assertThat(primitiveTransformers).isEqualTo(0); // Should be removed when disabled
    assertThat(dateTransformers).isEqualTo(0); // Should be removed when disabled

    // Deploy process and test
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    Date now = new Date();
    runtimeService.setVariable(processInstance.getId(), "testDate", now);
    runtimeService.setVariable(processInstance.getId(), "testString", "hello");

    // Run migration
    runtimeMigrator.start();

    // Since built-in transformers are disabled, our custom interceptor should process variables
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("testString", "CUSTOM_hello")
        .hasVariable("testDate", "DATE_SPECIFIC_" + now);
  }

  @Test
  public void shouldEnableCustomInterceptorsByDefault() {
    long customEnabledInterceptors = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof CustomTestInterceptor)
        .count();

    assertThat(customEnabledInterceptors).isEqualTo(1); // Should be present and enabled

    // Deploy process and test
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "testVar", "value");

    // Run migration
    runtimeMigrator.start();

    // Verify custom interceptor processed the variable
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("testVar", "CUSTOM_value");
  }

  @Test
  public void shouldDisableCustomInterceptorsViaConfig() {
    // Verify disabled custom interceptor is not in context
    long disabledInterceptors = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof DisabledTestInterceptor)
        .count();

    assertThat(disabledInterceptors).isEqualTo(0); // Should be removed when disabled

    // Deploy process and test
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "testVar", "value");

    // Run migration
    runtimeMigrator.start();

    // Verify disabled interceptor did not process the variable
    // Variable should be processed by enabled custom interceptor instead
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("testVar", "CUSTOM_value"); // Not "DISABLED_value"
  }

  @Test
  public void shouldRespectGetTypes() {
    // Deploy process with different variable types
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Set different types of variables
    runtimeService.setVariable(processInstance.getId(), "stringVar", "test");
    runtimeService.setVariable(processInstance.getId(), "intVar", 42);

    // Run migration
    runtimeMigrator.start();

    // CustomTestInterceptor only handles StringValue types (as defined in getTypes)
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("stringVar", "CUSTOM_test")
        .hasVariable("intVar", "UNIVERSAL_42");
  }

  @Test
  public void shouldRunInterceptorOnlyForSpecificTypesDefinedInGetTypes() {
    // Deploy process with mixed variable types
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Set different types of variables
    runtimeService.setVariable(processInstance.getId(), "stringVar", "hello");
    Date now = new Date();
    runtimeService.setVariable(processInstance.getId(), "dateVar", now);
    runtimeService.setVariable(processInstance.getId(), "intVar", 123);

    // Run migration
    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("stringVar", "CUSTOM_hello") // Processed by CustomTestInterceptor
        .hasVariable("dateVar", "DATE_SPECIFIC_" + now) // Processed by TypeSpecificInterceptor
        .hasVariable("intVar", "UNIVERSAL_123"); // Processed by UniversalTestInterceptor
  }

  @Test
  public void shouldRunInterceptorForAllTypesWhenGetTypesReturnsEmptySet() {
    // Verify that UniversalTestInterceptor (with empty getTypes) handles multiple variable types
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Set variables of different types
    runtimeService.setVariable(processInstance.getId(), "stringVar", "test");
    runtimeService.setVariable(processInstance.getId(), "intVar", 999);
    runtimeService.setVariable(processInstance.getId(), "boolVar", true);

    // Run migration
    runtimeMigrator.start();

    // UniversalTestInterceptor should process all types since getTypes() returns empty set
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("stringVar", "CUSTOM_test")
        .hasVariable("intVar", "UNIVERSAL_999")
        .hasVariable("boolVar", "UNIVERSAL_true");
  }

  @Test
  public void shouldDisableBuiltInInterceptorsProperly() {
    // Verify built-in interceptors are disabled and don't process variables
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // Set variables that would normally be processed by built-in transformers
    runtimeService.setVariable(processInstance.getId(), "primitiveVar", "original");
    Date now = new Date();
    runtimeService.setVariable(processInstance.getId(), "dateVar", now);

    // Run migration
    runtimeMigrator.start();

    // Since built-in transformers are disabled, variables should be processed by custom interceptors instead
    // UniversalTestInterceptor should handle these since built-in ones are disabled
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("primitiveVar", "CUSTOM_original") // Not transformed by built-in
        .hasVariable("dateVar", "DATE_SPECIFIC_" + now); // Not transformed by built-in
  }

  @Test
  public void shouldEnableCustomInterceptorByDefaultAndAllowDisabling() {
    // Test that custom interceptors are enabled by default
    long enabledCustomInterceptors = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof CustomTestInterceptor
            || interceptor instanceof UniversalTestInterceptor)
        .count();

    assertThat(enabledCustomInterceptors).isEqualTo(2);

    // Test that disabled interceptor is not present
    long disabledInterceptors = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof DisabledTestInterceptor)
        .count();

    assertThat(disabledInterceptors).isEqualTo(0);

    // Deploy and test behavior
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "testVar", "value");

    // Run migration
    runtimeMigrator.start();

    // Should be processed by enabled interceptors, not disabled one
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("testVar", "CUSTOM_value");
  }

  @Test
  public void shouldRegisterConfiguredInterceptorInInterceptorsList() {
    // Verify that the configured interceptor is included in the configured interceptors list
    assertThat(configuredVariableInterceptors).isNotNull();

    boolean hasInterceptor = configuredVariableInterceptors.stream()
        .anyMatch(interceptor -> interceptor instanceof ComplexInterceptor);

    assertThat(hasInterceptor).isTrue();

    // Find the interceptor and verify its configuration
    ComplexInterceptor complexInterceptor = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof ComplexInterceptor)
        .map(ComplexInterceptor.class::cast)
        .findFirst()
        .orElse(null);

    assertThat(complexInterceptor).isNotNull();
    assertThat(complexInterceptor.getLogMessage()).isEqualTo("Hello from interceptor configured via properties");
    assertThat(complexInterceptor.isEnableTransformation()).isTrue();
    assertThat(complexInterceptor.getTargetVariable()).isEqualTo("var");
  }

  @Test
  public void shouldInvokeConfiguredInterceptorDuringMigration(CapturedOutput output) {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    deployer.deployProcessInC7AndC8("userTaskProcess.bpmn");

    // given processes state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "var", "originalValue");

    var userTaskProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    runtimeService.setVariable(userTaskProcessInstance.getId(), "var", "anotherValue");

    // when running runtime migration
    runtimeMigrator.start();

    // then verify that variables were transformed by the configured interceptor
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("var", "transformedValue");
    CamundaAssert.assertThat(byProcessId("userTaskProcessId"))
        .hasVariable("var", "transformedValue");

    // verify interceptor log messages appear
    assertThat(output.getOut()).contains("Hello from interceptor configured via properties");
    Matcher matcher = Pattern.compile("Hello from interceptor configured via properties").matcher(output.getOut());
    assertThat(matcher.results().count()).isEqualTo(2);

    // verify transformation log messages
    assertThat(output.getOut()).contains("Transformed variable var from 'originalValue' to 'transformedValue'");
    assertThat(output.getOut()).contains("Transformed variable var from 'anotherValue' to 'transformedValue'");
  }

  @Test
  public void shouldSkipProcessInstanceDueToExceptionFromInterceptor(CapturedOutput output) {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // given processes state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "exFlag", true);

    // run migration first time
    runtimeMigrator.start();

    assertThat(output.getOut()).contains(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace(" [{}]: {}", ""));
    assertThat(output.getOut()).contains(String.format(VARIABLE_INTERCEPTOR_FAILED_MSG, ComplexInterceptor.class.getSimpleName(), "exFlag"));

    // fix the variable to allow successful migration
    runtimeService.setVariable(simpleProcessInstance.getId(), "exFlag", false);

    // when run runtime migration again with RETRY_SKIPPED mode
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasActiveElements(byId("userTask1"))
        .hasVariable("exFlag", "UNIVERSAL_false");

    assertThat(output.getOut()).contains("Success from interceptor");
  }

}
