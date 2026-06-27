/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime.variables.interceptor;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR;
import static io.camunda.migration.data.impl.logging.VariableServiceLogs.VARIABLE_INTERCEPTOR_FAILED_MSG;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.interceptor.DateVariableTransformer;
import io.camunda.migration.data.impl.interceptor.PrimitiveVariableTransformer;
import io.camunda.migration.data.interceptor.VariableInterceptor;
import io.camunda.migration.data.qa.runtime.RuntimeMigrationAbstractTest;
import io.camunda.migration.data.qa.runtime.variables.interceptor.pojo.ComplexInterceptor;
import io.camunda.migration.data.qa.runtime.variables.interceptor.pojo.CustomTestInterceptor;
import io.camunda.migration.data.qa.runtime.variables.interceptor.pojo.DisabledTestInterceptor;
import io.camunda.migration.data.qa.runtime.variables.interceptor.pojo.UniversalTestInterceptor;
import io.camunda.migration.data.qa.util.WithSpringProfile;
import io.camunda.process.test.api.CamundaAssert;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@WithSpringProfile("interceptor")
@Timeout(value = 2, unit = TimeUnit.MINUTES)
abstract class AbstractDeclarativeConfigurationTest extends RuntimeMigrationAbstractTest {

  @Autowired
  protected MigratorProperties migratorProperties;

  @Autowired
  protected List<VariableInterceptor> configuredVariableInterceptors;
}

class DeclarativeDisableBuiltInInterceptorsTest extends AbstractDeclarativeConfigurationTest {

  @Test
  public void shouldDisableBuiltInInterceptorsViaConfig() {
    long primitiveTransformers = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof PrimitiveVariableTransformer)
        .count();

    long dateTransformers = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof DateVariableTransformer)
        .count();

    assertThat(primitiveTransformers).isEqualTo(0);
    assertThat(dateTransformers).isEqualTo(0);

    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    Date now = new Date();
    runtimeService.setVariable(processInstance.getId(), "testDate", now);
    runtimeService.setVariable(processInstance.getId(), "testString", "hello");

    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("testString", "CUSTOM_hello")
        .hasVariable("testDate", "DATE_SPECIFIC_" + now);
  }
}

class DeclarativeCustomInterceptorsEnabledTest extends AbstractDeclarativeConfigurationTest {

  @Test
  public void shouldEnableCustomInterceptorsByDefault() {
    long customEnabledInterceptors = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof CustomTestInterceptor)
        .count();

    assertThat(customEnabledInterceptors).isEqualTo(1);

    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "testVar", "value");

    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("testVar", "CUSTOM_value");
  }
}

class DeclarativeCustomInterceptorsDisabledTest extends AbstractDeclarativeConfigurationTest {

  @Test
  public void shouldDisableCustomInterceptorsViaConfig() {
    long disabledInterceptors = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof DisabledTestInterceptor)
        .count();

    assertThat(disabledInterceptors).isEqualTo(0);

    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "testVar", "value");

    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("testVar", "CUSTOM_value");
  }
}

class DeclarativeGetTypesTest extends AbstractDeclarativeConfigurationTest {

  @Test
  public void shouldRespectGetTypes() {
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    runtimeService.setVariable(processInstance.getId(), "stringVar", "test");
    runtimeService.setVariable(processInstance.getId(), "intVar", 42);

    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("stringVar", "CUSTOM_test")
        .hasVariable("intVar", "UNIVERSAL_42");
  }
}

class DeclarativeSpecificTypesTest extends AbstractDeclarativeConfigurationTest {

  @Test
  public void shouldRunInterceptorOnlyForSpecificTypesDefinedInGetTypes() {
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    runtimeService.setVariable(processInstance.getId(), "stringVar", "hello");
    Date now = new Date();
    runtimeService.setVariable(processInstance.getId(), "dateVar", now);
    runtimeService.setVariable(processInstance.getId(), "intVar", 123);

    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("stringVar", "CUSTOM_hello")
        .hasVariable("dateVar", "DATE_SPECIFIC_" + now)
        .hasVariable("intVar", "UNIVERSAL_123");
  }
}

class DeclarativeUniversalTypesTest extends AbstractDeclarativeConfigurationTest {

  @Test
  public void shouldRunInterceptorForAllTypesWhenGetTypesReturnsEmptySet() {
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    runtimeService.setVariable(processInstance.getId(), "stringVar", "test");
    runtimeService.setVariable(processInstance.getId(), "intVar", 999);
    runtimeService.setVariable(processInstance.getId(), "boolVar", true);

    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("stringVar", "CUSTOM_test")
        .hasVariable("intVar", "UNIVERSAL_999")
        .hasVariable("boolVar", "UNIVERSAL_true");
  }
}

class DeclarativeBuiltInInterceptorsDisabledBehaviorTest extends AbstractDeclarativeConfigurationTest {

  @Test
  public void shouldDisableBuiltInInterceptorsProperly() {
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    runtimeService.setVariable(processInstance.getId(), "primitiveVar", "original");
    Date now = new Date();
    runtimeService.setVariable(processInstance.getId(), "dateVar", now);

    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("primitiveVar", "CUSTOM_original")
        .hasVariable("dateVar", "DATE_SPECIFIC_" + now);
  }
}

class DeclarativeCustomInterceptorEnableDisableTest extends AbstractDeclarativeConfigurationTest {

  @Test
  public void shouldEnableCustomInterceptorByDefaultAndAllowDisabling() {
    long enabledCustomInterceptors = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof CustomTestInterceptor
            || interceptor instanceof UniversalTestInterceptor)
        .count();

    assertThat(enabledCustomInterceptors).isEqualTo(2);

    long disabledInterceptors = configuredVariableInterceptors.stream()
        .filter(interceptor -> interceptor instanceof DisabledTestInterceptor)
        .count();

    assertThat(disabledInterceptors).isEqualTo(0);

    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "testVar", "value");

    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("testVar", "CUSTOM_value");
  }
}

class DeclarativeConfiguredInterceptorInvocationTest extends AbstractDeclarativeConfigurationTest {

  @Test
  public void shouldInvokeConfiguredInterceptorDuringMigration(CapturedOutput output) {
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    deployer.deployProcessInC7AndC8("userTaskProcess.bpmn");

    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "var", "originalValue");

    var userTaskProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    runtimeService.setVariable(userTaskProcessInstance.getId(), "var", "anotherValue");

    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("var", "transformedValue");
    CamundaAssert.assertThat(byProcessId("userTaskProcessId"))
        .hasVariable("var", "transformedValue");

    assertThat(output.getOut()).contains("Hello from interceptor configured via properties");
    Matcher matcher = Pattern.compile("Hello from interceptor configured via properties").matcher(output.getOut());
    assertThat(matcher.results().count()).isEqualTo(2);

    assertThat(output.getOut()).contains("Transformed variable var from 'originalValue' to 'transformedValue'");
    assertThat(output.getOut()).contains("Transformed variable var from 'anotherValue' to 'transformedValue'");
  }
}

class DeclarativeInterceptorExceptionRetryTest extends AbstractDeclarativeConfigurationTest {

  @Test
  public void shouldSkipProcessInstanceDueToExceptionFromInterceptor(CapturedOutput output) {
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "exFlag", true);

    runtimeMigrator.start();

    assertThat(output.getOut()).contains(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace(" [{}]: {}", ""));
    assertThat(output.getOut()).contains(String.format(VARIABLE_INTERCEPTOR_FAILED_MSG, ComplexInterceptor.class.getSimpleName(), "exFlag"));

    runtimeService.setVariable(simpleProcessInstance.getId(), "exFlag", false);

    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasActiveElements(byId("userTask1"))
        .hasVariable("exFlag", "UNIVERSAL_false");

    assertThat(output.getOut()).contains("Success from interceptor");
  }
}
