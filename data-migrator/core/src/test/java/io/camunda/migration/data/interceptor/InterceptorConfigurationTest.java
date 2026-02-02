/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for unified interceptor configuration supporting both VariableInterceptor and EntityInterceptor.
 */
@TestPropertySource(properties = {
    // Disable a built-in variable interceptor
    "camunda.migrator.interceptors[0].className=io.camunda.migration.data.impl.interceptor.PrimitiveVariableTransformer",
    "camunda.migrator.interceptors[0].enabled=false",
    // Add a custom variable interceptor
    "camunda.migrator.interceptors[1].className=io.camunda.migration.data.interceptor.InterceptorConfigurationTest$CustomVariableInterceptor",
    // Add a custom entity interceptor
    "camunda.migrator.interceptors[2].className=io.camunda.migration.data.interceptor.InterceptorConfigurationTest$CustomEntityInterceptor"
})
@SpringBootTest
public class InterceptorConfigurationTest {

  @Autowired(required = false)
  protected List<VariableInterceptor> configuredVariableInterceptors;

  @Autowired(required = false)
  protected List<EntityInterceptor> configuredEntityInterceptors;

  @Test
  public void shouldConfigureBothVariableAndEntityInterceptors() {
    // Variable interceptors should be configured
    assertThat(configuredVariableInterceptors).isNotNull();

    // Entity interceptors should be configured
    assertThat(configuredEntityInterceptors).isNotNull();
  }

  @Test
  public void shouldIncludeCustomVariableInterceptor() {
    assertThat(configuredVariableInterceptors).isNotNull();

    boolean hasCustomVariableInterceptor = configuredVariableInterceptors.stream()
        .anyMatch(interceptor -> interceptor instanceof CustomVariableInterceptor);

    assertThat(hasCustomVariableInterceptor).isTrue();
  }

  @Test
  public void shouldIncludeCustomEntityInterceptor() {
    assertThat(configuredEntityInterceptors).isNotNull();

    boolean hasCustomEntityInterceptor = configuredEntityInterceptors.stream()
        .anyMatch(interceptor -> interceptor instanceof CustomEntityInterceptor);

    assertThat(hasCustomEntityInterceptor).isTrue();
  }

  @Test
  public void shouldDisableBuiltInVariableInterceptor() {
    assertThat(configuredVariableInterceptors).isNotNull();

    boolean hasPrimitiveTransformer = configuredVariableInterceptors.stream()
        .anyMatch(interceptor ->
            interceptor.getClass().getName().equals("io.camunda.migrator.impl.interceptor.PrimitiveVariableTransformer"));

    assertThat(hasPrimitiveTransformer).isFalse();
  }

  // Custom interceptor classes for testing
  public static class CustomVariableInterceptor implements VariableInterceptor {
    @Override
    public void execute(VariableContext context) {
      // No-op for testing
    }
  }

  public static class CustomEntityInterceptor implements EntityInterceptor<Object, Object> {
    @Override
    public void execute(EntityConversionContext<Object, Object> context) {
      // No-op for testing
    }
  }

}

