/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.migrator.exception.EntityInterceptorException;
import io.camunda.migrator.interceptor.EntityInterceptor;
import io.camunda.migrator.interceptor.property.EntityConversionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityConversionServiceTest {

  private EntityConversionService service;

  // Test entity classes
  static class HistoricProcessInstance {
    private String processInstanceId;
    private String businessKey;

    public HistoricProcessInstance(String processInstanceId, String businessKey) {
      this.processInstanceId = processInstanceId;
      this.businessKey = businessKey;
    }

    public String getProcessInstanceId() {
      return processInstanceId;
    }

    public String getBusinessKey() {
      return businessKey;
    }
  }

  static class HistoricActivityInstance {
    private String activityId;

    public HistoricActivityInstance(String activityId) {
      this.activityId = activityId;
    }

    public String getActivityId() {
      return activityId;
    }
  }

  // Test C8 DB Model Builder
  static class ProcessInstanceDbModelBuilder {
    private Long processInstanceKey;
    private String bpmnProcessId;

    public ProcessInstanceDbModelBuilder processInstanceKey(Long key) {
      this.processInstanceKey = key;
      return this;
    }

    public ProcessInstanceDbModelBuilder bpmnProcessId(String id) {
      this.bpmnProcessId = id;
      return this;
    }

    public Long getProcessInstanceKey() {
      return processInstanceKey;
    }

    public String getBpmnProcessId() {
      return bpmnProcessId;
    }
  }

  // Test interceptors
  static class ProcessInstanceInterceptor implements EntityInterceptor {
    private boolean executed = false;
    private List<String> executionOrder;

    public void setExecutionOrder(List<String> executionOrder) {
      this.executionOrder = executionOrder;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(EntityConversionContext<?, ?> context) {
      executed = true;
      if (executionOrder != null) {
        executionOrder.add("ProcessInstanceInterceptor");
      }
      HistoricProcessInstance c7Entity = (HistoricProcessInstance) context.getC7Entity();
      ProcessInstanceDbModelBuilder c8Builder = new ProcessInstanceDbModelBuilder();
      c8Builder.processInstanceKey(12345L).bpmnProcessId(c7Entity.getBusinessKey());

      EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> typedContext =
          (EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder>) context;
      typedContext.setC8DbModelBuilder(c8Builder);
    }

    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(HistoricProcessInstance.class);
    }

    public boolean wasExecuted() {
      return executed;
    }
  }

  static class UniversalInterceptor implements EntityInterceptor {
    private boolean executed = false;
    private List<String> executionOrder;

    public void setExecutionOrder(List<String> executionOrder) {
      this.executionOrder = executionOrder;
    }

    @Override
    public void execute(EntityConversionContext<?, ?> context) {
      executed = true;
      if (executionOrder != null) {
        executionOrder.add("UniversalInterceptor");
      }
    }

    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(); // Handle all types
    }

    public boolean wasExecuted() {
      return executed;
    }
  }

  static class ActivityInterceptor implements EntityInterceptor {
    private boolean executed = false;

    @Override
    public void execute(EntityConversionContext<?, ?> context) {
      executed = true;
    }

    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(HistoricActivityInstance.class);
    }

    public boolean wasExecuted() {
      return executed;
    }
  }

  static class FailingInterceptor implements EntityInterceptor {
    @Override
    public void execute(EntityConversionContext<?, ?> context) {
      throw new RuntimeException("Interceptor failed");
    }

    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(HistoricProcessInstance.class);
    }
  }

  static class ExceptionThrowingInterceptor implements EntityInterceptor {
    @Override
    public void execute(EntityConversionContext<?, ?> context) {
      throw new EntityInterceptorException("Custom error message");
    }

    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(HistoricProcessInstance.class);
    }
  }

  @BeforeEach
  void setUp() {
    service = new EntityConversionService();
  }

  @Test
  void shouldConvertEntityWithSpecificInterceptor() {
    // Given
    ProcessInstanceInterceptor interceptor = new ProcessInstanceInterceptor();
    service.configuredEntityInterceptors = List.of(interceptor);
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When
    EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> result =
        service.convert(c7Entity, HistoricProcessInstance.class);

    // Then
    assertThat(interceptor.wasExecuted()).isTrue();
    assertThat(result).isNotNull();
    assertThat(result.getC7Entity()).isEqualTo(c7Entity);
    assertThat(result.getC8DbModelBuilder()).isNotNull();
    assertThat(result.getC8DbModelBuilder().getProcessInstanceKey()).isEqualTo(12345L);
    assertThat(result.getC8DbModelBuilder().getBpmnProcessId()).isEqualTo("business-key");
  }

  @Test
  void shouldNotExecuteInterceptorForNonMatchingType() {
    // Given
    ProcessInstanceInterceptor processInterceptor = new ProcessInstanceInterceptor();
    service.configuredEntityInterceptors = List.of(processInterceptor);
    HistoricActivityInstance c7Entity = new HistoricActivityInstance("activity-123");

    // When
    service.convert(c7Entity, HistoricActivityInstance.class);

    // Then
    assertThat(processInterceptor.wasExecuted()).isFalse();
  }

  @Test
  void shouldExecuteUniversalInterceptorForAllTypes() {
    // Given
    UniversalInterceptor universalInterceptor = new UniversalInterceptor();
    service.configuredEntityInterceptors = List.of(universalInterceptor);

    // When - Test with process instance
    service.convert(
        new HistoricProcessInstance("proc-123", "key"), HistoricProcessInstance.class);

    // Then
    assertThat(universalInterceptor.wasExecuted()).isTrue();

    // Given - Reset
    universalInterceptor = new UniversalInterceptor();
    service.configuredEntityInterceptors = List.of(universalInterceptor);

    // When - Test with activity instance
    service.convert(new HistoricActivityInstance("act-123"), HistoricActivityInstance.class);

    // Then
    assertThat(universalInterceptor.wasExecuted()).isTrue();
  }

  @Test
  void shouldExecuteMultipleInterceptorsInOrder() {
    // Given
    ProcessInstanceInterceptor processInterceptor = new ProcessInstanceInterceptor();
    UniversalInterceptor universalInterceptor = new UniversalInterceptor();
    List<String> executionOrder = new ArrayList<>();
    processInterceptor.setExecutionOrder(executionOrder);
    universalInterceptor.setExecutionOrder(executionOrder);
    service.configuredEntityInterceptors = List.of(processInterceptor, universalInterceptor);
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When
    EntityConversionContext<?, ?> result = service.convert(c7Entity, HistoricProcessInstance.class);

    // Then
    assertThat(processInterceptor.wasExecuted()).isTrue();
    assertThat(universalInterceptor.wasExecuted()).isTrue();
    assertThat(result.getC8DbModelBuilder()).isNotNull();
    assertThat(executionOrder).containsExactly("ProcessInstanceInterceptor", "UniversalInterceptor");
  }

  @Test
  void shouldExecuteOnlyMatchingInterceptors() {
    // Given
    ProcessInstanceInterceptor processInterceptor = new ProcessInstanceInterceptor();
    ActivityInterceptor activityInterceptor = new ActivityInterceptor();
    UniversalInterceptor universalInterceptor = new UniversalInterceptor();
    List<String> executionOrder = new ArrayList<>();
    processInterceptor.setExecutionOrder(executionOrder);
    universalInterceptor.setExecutionOrder(executionOrder);
    service.configuredEntityInterceptors =
        List.of(processInterceptor, activityInterceptor, universalInterceptor);
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When
    service.convert(c7Entity, HistoricProcessInstance.class);

    // Then
    assertThat(processInterceptor.wasExecuted()).isTrue();
    assertThat(activityInterceptor.wasExecuted()).isFalse();
    assertThat(universalInterceptor.wasExecuted()).isTrue();
    assertThat(executionOrder).containsExactly("ProcessInstanceInterceptor", "UniversalInterceptor");
  }

  @Test
  void shouldConvertWithExistingContext() {
    // Given
    ProcessInstanceInterceptor interceptor = new ProcessInstanceInterceptor();
    service.configuredEntityInterceptors = List.of(interceptor);
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");
    EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> context =
        new EntityConversionContext<>(c7Entity, HistoricProcessInstance.class);

    // When
    EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> result =
        service.convertWithContext(context);

    // Then
    assertThat(interceptor.wasExecuted()).isTrue();
    assertThat(result).isSameAs(context);
    assertThat(result.getC8DbModelBuilder()).isNotNull();
  }

  @Test
  void shouldHandleEmptyInterceptorList() {
    // Given
    service.configuredEntityInterceptors = new ArrayList<>();
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When
    EntityConversionContext<?, ?> result = service.convert(c7Entity, HistoricProcessInstance.class);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getC7Entity()).isEqualTo(c7Entity);
    assertThat(result.getC8DbModelBuilder()).isNull();
  }

  @Test
  void shouldHandleNullInterceptorList() {
    // Given
    service.configuredEntityInterceptors = null;
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When
    EntityConversionContext<?, ?> result = service.convert(c7Entity, HistoricProcessInstance.class);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getC7Entity()).isEqualTo(c7Entity);
    assertThat(result.getC8DbModelBuilder()).isNull();
  }

  @Test
  void shouldWrapRuntimeExceptionInEntityInterceptorException() {
    // Given
    service.configuredEntityInterceptors = List.of(new FailingInterceptor());
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When & Then
    assertThatThrownBy(() -> service.convert(c7Entity, HistoricProcessInstance.class))
        .isInstanceOf(EntityInterceptorException.class)
        .hasMessageContaining("FailingInterceptor")
        .hasMessageContaining("HistoricProcessInstance")
        .hasCauseInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldNotWrapEntityInterceptorException() {
    // Given
    service.configuredEntityInterceptors = List.of(new ExceptionThrowingInterceptor());
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When & Then
    assertThatThrownBy(() -> service.convert(c7Entity, HistoricProcessInstance.class))
        .isInstanceOf(EntityInterceptorException.class)
        .hasMessage("Custom error message")
        .hasNoCause();
  }

  @Test
  void shouldStopExecutionWhenInterceptorFails() {
    // Given
    FailingInterceptor failingInterceptor = new FailingInterceptor();
    UniversalInterceptor universalInterceptor = new UniversalInterceptor();
    service.configuredEntityInterceptors = List.of(failingInterceptor, universalInterceptor);
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When & Then
    assertThatThrownBy(() -> service.convert(c7Entity, HistoricProcessInstance.class))
        .isInstanceOf(EntityInterceptorException.class);

    // The universal interceptor should not have been executed
    assertThat(universalInterceptor.wasExecuted()).isFalse();
  }
}

