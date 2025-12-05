/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.date.runtime.variables.interceptor;

import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.date.runtime.RuntimeMigrationAbstractTest;
import io.camunda.migration.date.util.WithSpringProfile;
import io.camunda.process.test.api.CamundaAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "camunda.migrator.interceptors[0].className=io.camunda.migration.data.qa.runtime.variables.interceptor.bean.DisabledCustomInterceptor",
    "camunda.migrator.interceptors[0].enabled=false",
    "camunda.migrator.interceptors[1].className=io.camunda.migration.data.qa.runtime.variables.interceptor.bean.StringOnlyInterceptor",
    "camunda.migrator.interceptors[1].enabled=false",
    "camunda.migrator.interceptors[2].className=io.camunda.migration.data.qa.runtime.variables.interceptor.bean.UniversalInterceptor",
    "camunda.migrator.interceptors[2].enabled=false",
    "camunda.migrator.interceptors[3].className=io.camunda.migration.data.qa.runtime.variables.interceptor.pojo.ComplexInterceptor"
})
@WithSpringProfile("interceptor")
@ActiveProfiles("programmatic")
public class MixedConfigurationTest extends RuntimeMigrationAbstractTest {

  @Test
  public void shouldWorkAlongsideSpringComponentInterceptors(CapturedOutput output) {
    // This test verifies that configured interceptors work alongside Spring @Component interceptors
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // Set up variables that will trigger both types of interceptors
    var processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(processInstance.getId(), "varIntercept", "springValue"); // For ComplexVariableInterceptor (@Component)
    runtimeService.setVariable(processInstance.getId(), "var", "value"); // For ComplexInterceptor (declarative)

    // when running runtime migration
    runtimeMigrator.start();

    // then both interceptors should have executed
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("varIntercept", "Hello") // Transformed by Spring @Component interceptor
        .hasVariable("var", "transformedValue"); // Transformed by declarative interceptor

    // verify both interceptors logged their messages
    assertThat(output.getOut()).contains("Hello from interceptor"); // From ComplexVariableInterceptor
    assertThat(output.getOut()).contains("Hello from declarative interceptor configured via properties"); // From declarative interceptor
  }

}
