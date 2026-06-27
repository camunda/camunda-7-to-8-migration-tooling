/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime.variables.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.runtime.variables.interceptor.pojo.ComplexInterceptor;
import org.junit.jupiter.api.Test;

public class DeclarativeConfigurationTest extends AbstractDeclarativeConfigurationTest {

  @Test
  public void shouldLoadVariableInterceptorFromConfiguration() {
    // Verify that the configuration is loaded correctly
    assertThat(migratorProperties.getInterceptors()).isNotNull();
    assertThat(migratorProperties.getInterceptors()).hasSize(7);

    var interceptor = migratorProperties.getInterceptors().getLast();
    assertThat(interceptor.getClassName()).isEqualTo("io.camunda.migration.data.qa.runtime.variables.interceptor.pojo.ComplexInterceptor");
    assertThat(interceptor.getProperties()).containsEntry("targetVariable", "var");
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

}
