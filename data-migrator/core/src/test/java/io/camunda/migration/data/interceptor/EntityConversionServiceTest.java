/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.impl.EntityConversionService;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class EntityConversionServiceTest {

  protected EntityConversionService service;

  @BeforeEach
  public void setUp() {
    service = new EntityConversionService();
  }

  @Test
  public void shouldConvertEntityWithSpecificInterceptor() {
    // Given
    ProcessInstanceInterceptor interceptor = new ProcessInstanceInterceptor();
    ReflectionTestUtils.setField(service, "configuredEntityInterceptors", List.of(interceptor));
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When
    EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> result =
        service.convert(c7Entity);

    // Then
    assertThat(interceptor.wasExecuted()).isTrue();
    assertThat(result).isNotNull();
    assertThat(result.getC7Entity()).isEqualTo(c7Entity);
    assertThat(result.getC8DbModelBuilder()).isNotNull();
    assertThat(result.getC8DbModelBuilder().getProcessInstanceKey()).isEqualTo(12345L);
    assertThat(result.getC8DbModelBuilder().getBpmnProcessId()).isEqualTo("business-key");
  }

  @Test
  public void shouldNotExecuteInterceptorForNonMatchingType() {
    // Given
    ProcessInstanceInterceptor processInterceptor = new ProcessInstanceInterceptor();
    ReflectionTestUtils.setField(service, "configuredEntityInterceptors", List.of(processInterceptor));
    HistoricActivityInstance c7Entity = new HistoricActivityInstance("activity-123");

    // When
    service.convert(c7Entity);

    // Then
    assertThat(processInterceptor.wasExecuted()).isFalse();
  }

  @Test
  public void shouldExecuteUniversalInterceptorForAllTypes() {
    // Given
    UniversalInterceptor universalInterceptor = new UniversalInterceptor();
    ReflectionTestUtils.setField(service, "configuredEntityInterceptors", List.of(universalInterceptor));

    // When - Test with process instance
    service.convert(
        new HistoricProcessInstance("proc-123", "key"));

    // Then
    assertThat(universalInterceptor.wasExecuted()).isTrue();

    // Given - Reset
    universalInterceptor = new UniversalInterceptor();
    ReflectionTestUtils.setField(service, "configuredEntityInterceptors", List.of(universalInterceptor));

    // When - Test with activity instance
    service.convert(new HistoricActivityInstance("act-123"));

    // Then
    assertThat(universalInterceptor.wasExecuted()).isTrue();
  }

  @Test
  public void shouldExecuteMultipleInterceptorsInOrder() {
    // Given
    ProcessInstanceInterceptor processInterceptor = new ProcessInstanceInterceptor();
    UniversalInterceptor universalInterceptor = new UniversalInterceptor();
    List<String> executionOrder = new ArrayList<>();
    processInterceptor.setExecutionOrder(executionOrder);
    universalInterceptor.setExecutionOrder(executionOrder);
    ReflectionTestUtils.setField(service, "configuredEntityInterceptors", List.of(processInterceptor, universalInterceptor));
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When
    EntityConversionContext<?, ?> result = service.convert(c7Entity);

    // Then
    assertThat(processInterceptor.wasExecuted()).isTrue();
    assertThat(universalInterceptor.wasExecuted()).isTrue();
    assertThat(result.getC8DbModelBuilder()).isNotNull();
    assertThat(executionOrder).containsExactly("ProcessInstanceInterceptor", "UniversalInterceptor");
  }

  @Test
  public void shouldExecuteOnlyMatchingInterceptors() {
    // Given
    ProcessInstanceInterceptor processInterceptor = new ProcessInstanceInterceptor();
    ActivityInterceptor activityInterceptor = new ActivityInterceptor();
    UniversalInterceptor universalInterceptor = new UniversalInterceptor();
    List<String> executionOrder = new ArrayList<>();
    processInterceptor.setExecutionOrder(executionOrder);
    universalInterceptor.setExecutionOrder(executionOrder);
    ReflectionTestUtils.setField(
        service, "configuredEntityInterceptors",
        List.of(processInterceptor, activityInterceptor, universalInterceptor));
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When
    service.convert(c7Entity);

    // Then
    assertThat(processInterceptor.wasExecuted()).isTrue();
    assertThat(activityInterceptor.wasExecuted()).isFalse();
    assertThat(universalInterceptor.wasExecuted()).isTrue();
    assertThat(executionOrder).containsExactly("ProcessInstanceInterceptor", "UniversalInterceptor");
  }

  @Test
  public void shouldConvertWithExistingContext() {
    // Given
    ProcessInstanceInterceptor interceptor = new ProcessInstanceInterceptor();
    ReflectionTestUtils.setField(service, "configuredEntityInterceptors", List.of(interceptor));
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");
    EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> context =
        new EntityConversionContext<>(c7Entity);

    // When
    EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> result =
        service.convertWithContext(context);

    // Then
    assertThat(interceptor.wasExecuted()).isTrue();
    assertThat(result).isSameAs(context);
    assertThat(result.getC8DbModelBuilder()).isNotNull();
  }

  @Test
  public void shouldHandleEmptyInterceptorList() {
    // Given
    ReflectionTestUtils.setField(service, "configuredEntityInterceptors", new ArrayList<>());
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When
    EntityConversionContext<?, ?> result = service.convert(c7Entity);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getC7Entity()).isEqualTo(c7Entity);
    assertThat(result.getC8DbModelBuilder()).isNull();
  }

  @Test
  public void shouldHandleNullInterceptorList() {
    // Given
    ReflectionTestUtils.setField(service, "configuredEntityInterceptors", null);
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When
    EntityConversionContext<?, ?> result = service.convert(c7Entity);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getC7Entity()).isEqualTo(c7Entity);
    assertThat(result.getC8DbModelBuilder()).isNull();
  }

  @Test
  public void shouldWrapRuntimeExceptionInEntityInterceptorException() {
    // Given
    ReflectionTestUtils.setField(service, "configuredEntityInterceptors", List.of(new FailingInterceptor()));
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When & Then
    assertThatThrownBy(() -> service.convert(c7Entity))
        .isInstanceOf(EntityInterceptorException.class)
        .hasMessageContaining("FailingInterceptor")
        .hasMessageContaining("HistoricProcessInstance")
        .hasCauseInstanceOf(RuntimeException.class);
  }

  @Test
  public void shouldNotWrapEntityInterceptorException() {
    // Given
    ReflectionTestUtils.setField(service, "configuredEntityInterceptors", List.of(new ExceptionThrowingInterceptor()));
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When & Then
    assertThatThrownBy(() -> service.convert(c7Entity))
        .isInstanceOf(EntityInterceptorException.class)
        .hasMessage("Custom error message")
        .hasNoCause();
  }

  @Test
  public void shouldStopExecutionWhenInterceptorFails() {
    // Given
    FailingInterceptor failingInterceptor = new FailingInterceptor();
    UniversalInterceptor universalInterceptor = new UniversalInterceptor();
    ReflectionTestUtils.setField(service, "configuredEntityInterceptors", List.of(failingInterceptor, universalInterceptor));
    HistoricProcessInstance c7Entity = new HistoricProcessInstance("proc-123", "business-key");

    // When & Then
    assertThatThrownBy(() -> service.convert(c7Entity))
        .isInstanceOf(EntityInterceptorException.class);

    // The universal interceptor should not have been executed
    assertThat(universalInterceptor.wasExecuted()).isFalse();
  }

  // Test entity classes
  public static class HistoricProcessInstance {
    protected String processInstanceId;
    protected String businessKey;

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

  public static class HistoricActivityInstance {
    protected String activityId;

    public HistoricActivityInstance(String activityId) {
      this.activityId = activityId;
    }

    public String getActivityId() {
      return activityId;
    }
  }

  // Test C8 DB Model Builder
  public static class ProcessInstanceDbModelBuilder {
    protected Long processInstanceKey;
    protected String bpmnProcessId;

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
  public static class ProcessInstanceInterceptor implements EntityInterceptor<HistoricProcessInstance, ProcessInstanceDbModelBuilder> {
    protected boolean executed = false;
    protected List<String> executionOrder;

    public void setExecutionOrder(List<String> executionOrder) {
      this.executionOrder = executionOrder;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> context) {
      executed = true;
      if (executionOrder != null) {
        executionOrder.add("ProcessInstanceInterceptor");
      }
      ProcessInstanceDbModelBuilder c8Builder = new ProcessInstanceDbModelBuilder();
      c8Builder.processInstanceKey(12345L).bpmnProcessId(context.getC7Entity().getBusinessKey());
      context.setC8DbModelBuilder(c8Builder);
    }

    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(HistoricProcessInstance.class);
    }

    public boolean wasExecuted() {
      return executed;
    }
  }

  public static class UniversalInterceptor implements EntityInterceptor<HistoricProcessInstance, ProcessInstanceDbModelBuilder>  {
    protected boolean executed = false;
    protected List<String> executionOrder;

    public void setExecutionOrder(List<String> executionOrder) {
      this.executionOrder = executionOrder;
    }

    @Override
    public void execute(EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> context) {
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

  public static class ActivityInterceptor implements EntityInterceptor<HistoricProcessInstance, ProcessInstanceDbModelBuilder>  {
    protected boolean executed = false;

    @Override
    public void execute(EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> context) {
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

  public static class FailingInterceptor implements EntityInterceptor<HistoricProcessInstance, ProcessInstanceDbModelBuilder>  {
    @Override
    public void execute(EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> context) {
      throw new RuntimeException("Interceptor failed");
    }

    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(HistoricProcessInstance.class);
    }
  }

  public static class ExceptionThrowingInterceptor implements EntityInterceptor<HistoricProcessInstance, ProcessInstanceDbModelBuilder>  {
    @Override
    public void execute(EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModelBuilder> context) {
      throw new EntityInterceptorException("Custom error message");
    }

    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(HistoricProcessInstance.class);
    }
  }
}

