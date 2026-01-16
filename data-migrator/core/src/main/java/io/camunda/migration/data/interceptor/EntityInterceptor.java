/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.interceptor;

import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;

/**
 * Type-safe interceptor interface for handling entity conversion during migration.
 * <p>
 * Implement this interface to define custom logic that should be executed
 * when a Camunda 7 entity is being converted to a Camunda 8 database model during migration.
 * The generic type parameters eliminate the need for casting.
 * </p>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Type-safe interceptor for process instances:</h3>
 * <pre>
 * &#64;Component
 * &#64;Order(2)
 * public class ProcessInstanceTransformer
 *     implements EntityInterceptor&lt;HistoricProcessInstance, ProcessInstanceDbModel.ProcessInstanceDbModelBuilder&gt; {
 *
 *   &#64;Override
 *   public Set&lt;Class&lt;?&gt;&gt; getTypes() {
 *     return Set.of(HistoricProcessInstance.class);
 *   }
 *
 *   &#64;Override
 *   public void execute(HistoricProcessInstance entity,
 *                       ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder,
 *                       EntityConversionContext&lt;HistoricProcessInstance, ProcessInstanceDbModel.ProcessInstanceDbModelBuilder&gt; context) {
 *     // No casting needed!
 *     builder.processInstanceKey(getNextKey())
 *         .processDefinitionId(prefixDefinitionId(entity.getProcessDefinitionKey()))
 *         .startDate(convertDate(entity.getStartTime()));
 *   }
 * }
 * </pre>
 *
 * <h3>Universal interceptor (handles all entity types):</h3>
 * <pre>
 * &#64;Component
 * public class EntityLogger implements EntityInterceptor&lt;Object, Object&gt; {
 *   &#64;Override
 *   public Set&lt;Class&lt;?&gt;&gt; getTypes() {
 *     return Set.of(); // Empty set = handle all types
 *   }
 *
 *   &#64;Override
 *   public void execute(Object entity, Object builder, EntityConversionContext&lt;Object, Object&gt; context) {
 *     logger.info("Converting entity: {}", context.getEntityType().getSimpleName());
 *   }
 * }
 * </pre>
 *
 * <h3>Disabling interceptors via YAML configuration:</h3>
 * <pre>
 * migrator:
 *   interceptors:
 *     - className: "io.camunda.migrator.data.impl.interceptor.history.entity.ProcessInstanceTransformer"
 *       enabled: false
 * </pre>
 * <p>
 * The {@link #getTypes()} method allows specifying entity types using Camunda 7 historic entity classes like:
 * - {@code ProcessDefinition.class} for process definitions
 * - {@code HistoricProcessInstance.class} for process instances
 * - {@code HistoricActivityInstance.class} for flow nodes/activities
 * - {@code HistoricVariableInstance.class} for variables
 * - {@code HistoricTaskInstance.class} for user tasks
 * - {@code HistoricIncident.class} for incidents
 * - {@code HistoricDecisionInstance.class} for decision instances
 * </p>
 * <p>
 * If the returned set is empty, this interceptor will be called for all entity types.
 * </p>
 *
 * @param <C7> the Camunda 7 entity type
 * @param <C8> the Camunda 8 database model builder type
 */
public interface EntityInterceptor<C7, C8> extends BaseInterceptor<EntityConversionContext<C7, C8>> {

  /**
   * Executes the typed interceptor logic without requiring casts.
   *
   * @param entity  the Camunda 7 entity (already cast to the correct type)
   * @param builder the Camunda 8 database model builder (already cast to the correct type), may be null
   */
  default void execute(C7 entity, C8 builder) {
  }

  /**
   * Bridge method for BaseInterceptor compatibility. Delegates to the typed execute method.
   * <p>
   * This method is called by the framework and automatically delegates to the type-safe
   * {@link #execute(Object, Object)} method.
   * </p>
   *
   * @param context the entity conversion context
   */
  @SuppressWarnings("unchecked")
  default void execute(EntityConversionContext<C7, C8> context) {
    C7 entity = context.getC7Entity();
    C8 builder = context.getC8DbModelBuilder();
    execute(entity, builder);
  }

  /**
   * Presets parent-related properties with type safety.
   * <p>
   * This method is called <b>before</b> {@link #execute(Object, Object)} during the entity conversion lifecycle.
   * It allows interceptors to set parent process or definition keys (such as {@code processDefinitionKey},
   * {@code parentProcessInstanceKey}) or other hierarchical properties that must be available to the main conversion logic.
   * </p>
   *
   * <h2>Lifecycle</h2>
   * <ul>
   *   <li>1. {@code presetParentProperties} is called first for each entity.</li>
   *   <li>2. {@code execute} is called next to perform the main conversion.</li>
   * </ul>
   *
   * @param entity the Camunda 7 entity (already cast to the correct type)
   * @param builder the Camunda 8 database model builder (already cast to the correct type), may be null
   */
  default void presetParentProperties(C7 entity, C8 builder) {
  }

  /**
   * Bridge method for preset parent properties. Delegates to the typed presetParentProperties method.
   *
   * @param context the entity conversion context
   */
  @SuppressWarnings("unchecked")
  default void presetParentProperties(EntityConversionContext<C7, C8> context) {
    C7 entity = context.getC7Entity();
    C8 builder = context.getC8DbModelBuilder();
    presetParentProperties(entity, builder);
  }

  /**
   * Returns the set of types that this interceptor can handle.
   * <p>
   * Interceptors can specify which types they handle to allow the system to only call
   * relevant interceptors based on the data type being processed.
   * </p>
   * <p>
   * Use Camunda 7 historic entity classes like:
   * - {@code ProcessDefinition.class} for process definitions
   * - {@code HistoricProcessInstance.class} for process instances
   * - {@code HistoricActivityInstance.class} for flow nodes/activities
   * - {@code HistoricVariableInstance.class} for variables
   * - {@code HistoricTaskInstance.class} for user tasks
   * - {@code HistoricIncident.class} for incidents
   * - {@code HistoricDecisionInstance.class} for decision instances
   * </p>
   * <p>
   * If the returned set is empty, this interceptor will be called for all entity types.
   * </p>
   *
   * @return set of supported types, or empty set to handle all types
   */
  default Set<Class<?>> getTypes() {
    return Set.of(); // Empty set = handle all types
  }
}

