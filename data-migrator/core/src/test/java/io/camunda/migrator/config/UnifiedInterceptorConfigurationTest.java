/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.interceptor.EntityInterceptor;
import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.property.EntityConversionContext;
import io.camunda.migrator.interceptor.VariableInvocation;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for unified interceptor configuration supporting both VariableInterceptor and EntityInterceptor.
 */
@TestPropertySource(properties = {
    // Disable a built-in variable interceptor
    "camunda.migrator.interceptors[0].className=io.camunda.migrator.impl.interceptor.PrimitiveVariableTransformer",
    "camunda.migrator.interceptors[0].enabled=false",
    // Add a custom variable interceptor
    "camunda.migrator.interceptors[1].className=io.camunda.migrator.config.UnifiedInterceptorConfigurationTest$CustomVariableInterceptor",
    // Add a custom entity interceptor
    "camunda.migrator.interceptors[2].className=io.camunda.migrator.config.UnifiedInterceptorConfigurationTest$CustomEntityInterceptor"
})
@SpringBootTest
public class UnifiedInterceptorConfigurationTest {

  @Autowired(required = false)
  private List<VariableInterceptor> configuredVariableInterceptors;

  @Autowired(required = false)
  private List<EntityInterceptor> configuredEntityInterceptors;

  @Test
  void shouldConfigureBothVariableAndEntityInterceptors() {
    // Variable interceptors should be configured
    assertThat(configuredVariableInterceptors).isNotNull();

    // Entity interceptors should be configured
    assertThat(configuredEntityInterceptors).isNotNull();
  }

  @Test
  void shouldIncludeCustomVariableInterceptor() {
    assertThat(configuredVariableInterceptors).isNotNull();

    boolean hasCustomVariableInterceptor = configuredVariableInterceptors.stream()
        .anyMatch(interceptor -> interceptor instanceof CustomVariableInterceptor);

    assertThat(hasCustomVariableInterceptor).isTrue();
  }

  @Test
  void shouldIncludeCustomEntityInterceptor() {
    assertThat(configuredEntityInterceptors).isNotNull();

    boolean hasCustomEntityInterceptor = configuredEntityInterceptors.stream()
        .anyMatch(interceptor -> interceptor instanceof CustomEntityInterceptor);

    assertThat(hasCustomEntityInterceptor).isTrue();
  }

  @Test
  void shouldDisableBuiltInVariableInterceptor() {
    assertThat(configuredVariableInterceptors).isNotNull();

    boolean hasPrimitiveTransformer = configuredVariableInterceptors.stream()
        .anyMatch(interceptor ->
            interceptor.getClass().getName().equals("io.camunda.migrator.impl.interceptor.PrimitiveVariableTransformer"));

    assertThat(hasPrimitiveTransformer).isFalse();
  }

  // Custom interceptor classes for testing
  public static class CustomVariableInterceptor implements VariableInterceptor {
    @Override
    public void execute(VariableInvocation invocation) {
      // No-op for testing
    }
  }

  public static class CustomEntityInterceptor implements EntityInterceptor {
    @Override
    public void execute(EntityConversionContext<?, ?> context) {
      // No-op for testing
    }
  }

  // Spring-managed interceptors for testing
  @Component
  public static class SpringManagedVariableInterceptor implements VariableInterceptor {
    @Override
    public void execute(VariableInvocation invocation) {
      // No-op for testing
    }

    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(); // Handle all types
    }
  }

  @Component
  public static class SpringManagedEntityInterceptor implements EntityInterceptor {
    @Override
    public void execute(EntityConversionContext<?, ?> context) {
      // No-op for testing
    }

    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(); // Handle all types
    }
  }
}

