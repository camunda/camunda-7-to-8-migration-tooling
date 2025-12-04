/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EntityTypeDetectorTest {

  @Test
  void shouldSupportAllTypesWhenInterceptorReturnsEmptySet() {
    // Given
    EntityInterceptor interceptor = new UniversalInterceptor();
    EntityConversionContext<?, ?> context =
        new EntityConversionContext<>(new HistoricProcessInstance(), HistoricProcessInstance.class);

    // When
    boolean supports = EntityTypeDetector.supportsEntityBasedOnContext(interceptor, context);

    // Then
    assertThat(supports).isTrue();
  }

  @Test
  void shouldSupportAllTypesWhenInterceptorReturnsEmptySet_WithDifferentType() {
    // Given
    EntityInterceptor interceptor = new UniversalInterceptor();
    EntityConversionContext<?, ?> context =
        new EntityConversionContext<>(
            new HistoricActivityInstance(), HistoricActivityInstance.class);

    // When
    boolean supports = EntityTypeDetector.supportsEntityBasedOnContext(interceptor, context);

    // Then
    assertThat(supports).isTrue();
  }

  @Test
  void shouldSupportSpecificTypeWhenInterceptorSpecifiesIt() {
    // Given
    EntityInterceptor interceptor = new ProcessInstanceInterceptor();
    EntityConversionContext<?, ?> context =
        new EntityConversionContext<>(new HistoricProcessInstance(), HistoricProcessInstance.class);

    // When
    boolean supports = EntityTypeDetector.supportsEntityBasedOnContext(interceptor, context);

    // Then
    assertThat(supports).isTrue();
  }

  @Test
  void shouldNotSupportTypeWhenInterceptorDoesNotSpecifyIt() {
    // Given
    EntityInterceptor interceptor = new ProcessInstanceInterceptor();
    EntityConversionContext<?, ?> context =
        new EntityConversionContext<>(
            new HistoricActivityInstance(), HistoricActivityInstance.class);

    // When
    boolean supports = EntityTypeDetector.supportsEntityBasedOnContext(interceptor, context);

    // Then
    assertThat(supports).isFalse();
  }

  @Test
  void shouldSupportMultipleTypesWhenInterceptorSpecifiesThem() {
    // Given
    EntityInterceptor interceptor = new MultiTypeInterceptor();

    // When & Then
    EntityConversionContext<?, ?> processContext =
        new EntityConversionContext<>(new HistoricProcessInstance(), HistoricProcessInstance.class);
    assertThat(EntityTypeDetector.supportsEntityBasedOnContext(interceptor, processContext)).isTrue();

    EntityConversionContext<?, ?> activityContext =
        new EntityConversionContext<>(
            new HistoricActivityInstance(), HistoricActivityInstance.class);
    assertThat(EntityTypeDetector.supportsEntityBasedOnContext(interceptor, activityContext)).isTrue();

    EntityConversionContext<?, ?> taskContext =
        new EntityConversionContext<>(new HistoricTaskInstance(), HistoricTaskInstance.class);
    assertThat(EntityTypeDetector.supportsEntityBasedOnContext(interceptor, taskContext)).isTrue();

    EntityConversionContext<?, ?> variableContext =
        new EntityConversionContext<>(
            new HistoricVariableInstance(), HistoricVariableInstance.class);
    assertThat(EntityTypeDetector.supportsEntityBasedOnContext(interceptor, variableContext)).isFalse();
  }

  @Test
  void shouldSupportSubclassWhenInterceptorSpecifiesParentClass() {
    // Given
    EntityInterceptor interceptor = new ProcessInstanceInterceptor();
    EntityConversionContext<?, ?> context =
        new EntityConversionContext<>(
            new SpecializedProcessInstance(), SpecializedProcessInstance.class);

    // When
    boolean supports = EntityTypeDetector.supportsEntityBasedOnContext(interceptor, context);

    // Then
    assertThat(supports)
        .isTrue(); // Should support subclass due to isAssignableFrom() logic
  }

  @Test
  void shouldSupportEntityTypeDirectly() {
    // Given
    EntityInterceptor interceptor = new ProcessInstanceInterceptor();

    // When
    boolean supports =
        EntityTypeDetector.supportsEntityType(interceptor, HistoricProcessInstance.class);

    // Then
    assertThat(supports).isTrue();
  }

  @Test
  void shouldNotSupportEntityTypeDirectly() {
    // Given
    EntityInterceptor interceptor = new ProcessInstanceInterceptor();

    // When
    boolean supports =
        EntityTypeDetector.supportsEntityType(interceptor, HistoricActivityInstance.class);

    // Then
    assertThat(supports).isFalse();
  }

  @Test
  void shouldSupportEntityInstance() {
    // Given
    EntityInterceptor interceptor = new ProcessInstanceInterceptor();
    Object entity = new HistoricProcessInstance();

    // When
    boolean supports = EntityTypeDetector.supportsEntityBasedOnContext(interceptor, entity);

    // Then
    assertThat(supports).isTrue();
  }

  @Test
  void shouldNotSupportEntityInstance() {
    // Given
    EntityInterceptor interceptor = new ProcessInstanceInterceptor();
    Object entity = new HistoricActivityInstance();

    // When
    boolean supports = EntityTypeDetector.supportsEntityBasedOnContext(interceptor, entity);

    // Then
    assertThat(supports).isFalse();
  }

  @Test
  void shouldNotSupportNullEntityInstance() {
    // Given
    EntityInterceptor interceptor = new ProcessInstanceInterceptor();

    // When
    boolean supports = EntityTypeDetector.supportsEntityBasedOnContext(interceptor, (Object) null);

    // Then
    assertThat(supports).isFalse();
  }

  @Test
  void shouldSupportSpecializedEntityInstance() {
    // Given
    EntityInterceptor interceptor = new ProcessInstanceInterceptor();
    Object entity = new SpecializedProcessInstance();

    // When
    boolean supports = EntityTypeDetector.supportsEntityBasedOnContext(interceptor, entity);

    // Then
    assertThat(supports).isTrue();
  }

  @Test
  void shouldSupportAllEntityTypesWithUniversalInterceptor() {
    // Given
    EntityInterceptor interceptor = new UniversalInterceptor();

    // When & Then
    assertThat(EntityTypeDetector.supportsEntityType(interceptor, HistoricProcessInstance.class))
        .isTrue();
    assertThat(EntityTypeDetector.supportsEntityType(interceptor, HistoricActivityInstance.class))
        .isTrue();
    assertThat(EntityTypeDetector.supportsEntityType(interceptor, HistoricVariableInstance.class))
        .isTrue();
    assertThat(EntityTypeDetector.supportsEntityType(interceptor, HistoricTaskInstance.class))
        .isTrue();
    assertThat(EntityTypeDetector.supportsEntityType(interceptor, HistoricIncident.class))
        .isTrue();
  }

  @Test
  void shouldOnlySupportSpecifiedTypeWithSpecificInterceptor() {
    // Given
    EntityInterceptor interceptor = new TestVariableInterceptor();

    // When & Then
    assertThat(EntityTypeDetector.supportsEntityType(interceptor, HistoricVariableInstance.class))
        .isTrue();
    assertThat(EntityTypeDetector.supportsEntityType(interceptor, HistoricProcessInstance.class))
        .isFalse();
    assertThat(EntityTypeDetector.supportsEntityType(interceptor, HistoricActivityInstance.class))
        .isFalse();
    assertThat(EntityTypeDetector.supportsEntityType(interceptor, HistoricTaskInstance.class))
        .isFalse();
    assertThat(EntityTypeDetector.supportsEntityType(interceptor, HistoricIncident.class))
        .isFalse();
  }

  // Test entity classes to simulate Camunda 7 historic entities
  static class HistoricProcessInstance {}

  static class HistoricActivityInstance {}

  static class HistoricVariableInstance {}

  static class HistoricTaskInstance {}

  static class HistoricIncident {}

  static class SpecializedProcessInstance extends HistoricProcessInstance {}

  // Test interceptor implementations
  static abstract class NoOpInterceptor implements EntityInterceptor {
    @Override
    public void execute(EntityConversionContext<?, ?> context) {
      // No-op for testing
    }

    // Force subclasses to declare their types explicitly
    public abstract Set<Class<?>> getTypes();
  }

  static class UniversalInterceptor extends NoOpInterceptor {
    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(); // Empty set = handle all types
    }
  }

  static class ProcessInstanceInterceptor extends NoOpInterceptor {
    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(HistoricProcessInstance.class);
    }
  }

  static class MultiTypeInterceptor extends NoOpInterceptor {
    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(
          HistoricProcessInstance.class,
          HistoricActivityInstance.class,
          HistoricTaskInstance.class);
    }
  }

  static class TestVariableInterceptor extends NoOpInterceptor {
    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(HistoricVariableInstance.class);
    }
  }

}

