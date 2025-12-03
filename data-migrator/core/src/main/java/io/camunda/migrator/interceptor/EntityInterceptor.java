/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.interceptor;

import io.camunda.migrator.interceptor.property.EntityConversionContext;

/**
 * Interceptor interface for handling entity conversion with type-specific filtering.
 * <p>
 * Implement this interface to define custom logic that should be executed
 * when a Camunda 7 entity is being converted to a Camunda 8 database model during migration.
 * Interceptors can specify which entity types they handle using Camunda 7's historic entity classes,
 * allowing the system to only call relevant interceptors.
 * </p>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Type-specific interceptor for process instances:</h3>
 * <pre>
 * &#64;Component
 * public class ProcessInstanceEnricher implements EntityInterceptor {
 *   &#64;Override
 *   public Set&lt;Class&lt;?&gt;&gt; getTypes() {
 *     return Set.of(HistoricProcessInstance.class);
 *   }
 *
 *   &#64;Override
 *   public void execute(EntityConversionContext&lt;?, ?&gt; context) {
 *     HistoricProcessInstance c7Instance = (HistoricProcessInstance) context.getC7Entity();
 *     ProcessInstanceDbModel.Builder c8Builder =
 *       (ProcessInstanceDbModel.Builder) context.getC8DbModelBuilder();
 *     // Custom conversion logic
 *   }
 * }
 * </pre>
 *
 * <h3>Universal interceptor (handles all entity types):</h3>
 * <pre>
 * &#64;Component
 * public class EntityLogger implements EntityInterceptor {
 *   &#64;Override
 *   public Set&lt;Class&lt;?&gt;&gt; getTypes() {
 *     return Set.of(); // Empty set = handle all types
 *   }
 *
 *   &#64;Override
 *   public void execute(EntityConversionContext&lt;?, ?&gt; context) {
 *     // This will be called for all entity types
 *     logger.info("Converting entity: {}", context.getEntityType().getSimpleName());
 *   }
 * }
 * </pre>
 *
 * <h3>Disabling interceptors via YAML configuration:</h3>
 * <pre>
 * migrator:
 *   interceptors:
 *     - className: "io.camunda.migrator.impl.converter.ProcessInstanceConverter"
 *       enabled: false
 * </pre>
 * <p>
 * The {@link #getTypes()} method inherited from {@link GlobalInterceptor} allows specifying
 * entity types using Camunda 7 historic entity classes like:
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
 */
public interface EntityInterceptor extends GlobalInterceptor<EntityConversionContext<?, ?>> {

  /**
   * Executes the interceptor logic for an entity conversion.
   * This method will only be called if the entity type matches one of the supported types.
   *
   * @param context the entity conversion context containing C7 entity, C8 builder, and entity type
   */
  @Override
  void execute(EntityConversionContext<?, ?> context);

  default void presetParentProperties(EntityConversionContext<?, ?> context) {
  }
}

