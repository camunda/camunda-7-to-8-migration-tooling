/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.migrator.impl.EntityConversionService;
import io.camunda.migrator.interceptor.property.EntityConversionContext;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for custom entity interceptors that use the presetParentProperties method
 * to set processDefinitionKey and parentProcessInstanceKey before the main conversion.
 * <p>
 * This test demonstrates how custom interceptors can be used to override or provide
 * parent properties that are typically set by the HistoryMigrator.
 * </p>
 */
@TestPropertySource(properties = {
    // Disable built-in converters to isolate our custom interceptor behavior
    "camunda.migrator.interceptors[0].className=io.camunda.migrator.converter.ProcessInstanceConverter",
    "camunda.migrator.interceptors[0].enabled=false"
})
@SpringBootTest(classes = {
    EntityConversionService.class,
    EntityConversionPresetParentPropertiesTest.TestConfiguration.class
})
public class EntityConversionPresetParentPropertiesTest {

  @Autowired
  protected EntityConversionService entityConversionService;

  @Autowired
  protected List<EntityInterceptor> configuredEntityInterceptors;

  @Test
  void shouldHaveCustomInterceptorConfigured() {
    assertThat(configuredEntityInterceptors).isNotNull();
    assertThat(configuredEntityInterceptors).hasSize(1);
    assertThat(configuredEntityInterceptors.get(0))
        .isInstanceOf(CustomProcessInstanceInterceptorWithParentProperties.class);
  }

  @Test
  void shouldSetProcessDefinitionKeyViaPresetParentProperties() {
    // given
    HistoricProcessInstance mockProcessInstance = createMockProcessInstance(
        "process-instance-1",
        "process-def-1",
        null
    );

    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder =
        new ProcessInstanceDbModel.ProcessInstanceDbModelBuilder();

    EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModel.ProcessInstanceDbModelBuilder> context =
        new EntityConversionContext<>(mockProcessInstance, HistoricProcessInstance.class, builder, null);

    // when
    entityConversionService.prepareParentProperties(context);

    // then - verify processDefinitionKey was set via presetParentProperties
    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder resultBuilder = context.getC8DbModelBuilder();
    assertThat(resultBuilder).isNotNull();

    // Build the model to verify the key was set
    ProcessInstanceDbModel model = resultBuilder.build();
    assertThat(model.processDefinitionKey()).isEqualTo(12345L);
  }

  @Test
  void shouldSetParentProcessInstanceKeyViaPresetParentProperties() {
    // given
    HistoricProcessInstance mockProcessInstance = createMockProcessInstance(
        "child-process-instance-1",
        "process-def-1",
        "parent-process-instance-1"
    );

    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder =
        new ProcessInstanceDbModel.ProcessInstanceDbModelBuilder();

    EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModel.ProcessInstanceDbModelBuilder> context =
        new EntityConversionContext<>(mockProcessInstance, HistoricProcessInstance.class, builder, null);

    // when
    entityConversionService.prepareParentProperties(context);

    // then - verify both keys were set via presetParentProperties
    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder resultBuilder = context.getC8DbModelBuilder();
    assertThat(resultBuilder).isNotNull();

    // Build the model to verify the keys were set
    ProcessInstanceDbModel model = resultBuilder.build();
    assertThat(model.processDefinitionKey()).isEqualTo(12345L);
    assertThat(model.parentProcessInstanceKey()).isEqualTo(99999L);
  }

  @Test
  void shouldConvertWithPresetParentPropertiesInFullPipeline() {
    // given
    HistoricProcessInstance mockProcessInstance = createMockProcessInstance(
        "process-instance-1",
        "process-def-1",
        "parent-process-instance-1"
    );

    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder =
        new ProcessInstanceDbModel.ProcessInstanceDbModelBuilder();

    EntityConversionContext<HistoricProcessInstance, ProcessInstanceDbModel.ProcessInstanceDbModelBuilder> context =
        new EntityConversionContext<>(mockProcessInstance, HistoricProcessInstance.class, builder, null);

    // when - prepareParentProperties is called first
    entityConversionService.prepareParentProperties(context);

    // then convertWithContext is called (simulating the full migration flow)
    entityConversionService.convertWithContext(context);

    // then - verify all properties were set correctly
    ProcessInstanceDbModel model = context.getC8DbModelBuilder().build();
    assertThat(model.processDefinitionKey()).isEqualTo(12345L);
    assertThat(model.parentProcessInstanceKey()).isEqualTo(99999L);
    // Verify the execute method was also called
    assertThat(model.processInstanceKey()).isEqualTo(88888L);
  }

  /**
   * Creates a stub HistoricProcessInstance for testing.
   */
  protected HistoricProcessInstance createMockProcessInstance(
      String processInstanceId,
      String processDefinitionId,
      String superProcessInstanceId) {
    return new StubHistoricProcessInstance(
        processInstanceId,
        processDefinitionId,
        superProcessInstanceId
    );
  }

  /**
   * Stub implementation of HistoricProcessInstance for testing.
   */
  static class StubHistoricProcessInstance implements HistoricProcessInstance {
    protected final String id;
    protected final String processDefinitionId;
    protected final String superProcessInstanceId;

    StubHistoricProcessInstance(String id, String processDefinitionId, String superProcessInstanceId) {
      this.id = id;
      this.processDefinitionId = processDefinitionId;
      this.superProcessInstanceId = superProcessInstanceId;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getProcessDefinitionId() {
      return processDefinitionId;
    }

    @Override
    public String getSuperProcessInstanceId() {
      return superProcessInstanceId;
    }

    @Override
    public String getProcessDefinitionKey() {
      return "testProcessKey";
    }

    // Minimal implementation of other required methods
    @Override public String getBusinessKey() { return null; }
    @Override public Date getStartTime() { return null; }
    @Override public Date getEndTime() { return null; }
    @Override public Long getDurationInMillis() { return null; }
    @Override public String getStartUserId() { return null; }
    @Override public String getStartActivityId() { return null; }
    @Override public String getEndActivityId() { return null; }
    @Override public String getDeleteReason() { return null; }
    @Override public String getSuperCaseInstanceId() { return null; }
    @Override public String getCaseInstanceId() { return null; }
    @Override public String getTenantId() { return null; }
    @Override public String getProcessDefinitionName() { return null; }
    @Override public Integer getProcessDefinitionVersion() { return null; }
    @Override public String getState() { return null; }
    @Override public String getRootProcessInstanceId() { return null; }
    @Override public Date getRemovalTime() { return null; }
    @Override public String getRestartedProcessInstanceId() { return null; }
  }

  /**
   * Test configuration that provides a custom interceptor.
   */
  @Configuration
  static class TestConfiguration {

    @Bean
    public EntityInterceptor customProcessInstanceInterceptor() {
      return new CustomProcessInstanceInterceptorWithParentProperties();
    }
  }

  /**
   * Custom interceptor that demonstrates setting parent properties
   * via the presetParentProperties method.
   */
  public static class CustomProcessInstanceInterceptorWithParentProperties implements EntityInterceptor {

    @Override
    public Set<Class<?>> getTypes() {
      return Set.of(HistoricProcessInstance.class);
    }

    @Override
    public void presetParentProperties(EntityConversionContext<?, ?> context) {
      HistoricProcessInstance processInstance = (HistoricProcessInstance) context.getC7Entity();
      ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder =
          (ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) context.getC8DbModelBuilder();

      if (builder != null) {
        // Set processDefinitionKey via custom logic (simulating lookup)
        builder.processDefinitionKey(12345L);

        // If there's a parent, set the parentProcessInstanceKey
        if (processInstance.getSuperProcessInstanceId() != null) {
          builder.parentProcessInstanceKey(99999L);
        }
      }
    }

    @Override
    public void execute(EntityConversionContext<?, ?> context) {
      // This execute method runs after presetParentProperties
      ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder =
          (ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) context.getC8DbModelBuilder();

      if (builder != null) {
        // Set the process instance key (simulating conversion logic)
        builder.processInstanceKey(88888L);
      }
    }
  }
}

