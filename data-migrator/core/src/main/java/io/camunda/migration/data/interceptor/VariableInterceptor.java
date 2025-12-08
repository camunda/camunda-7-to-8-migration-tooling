/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.interceptor;

import java.util.Set;
import org.camunda.bpm.engine.impl.variable.serializer.ValueFields;

/**
 * Interceptor interface for handling variable contexts with type-specific filtering.
 * <p>
 * Implement this interface to define custom logic that should be executed
 * when a variable is accessed or modified during migration. Interceptors can specify
 * which variable types they handle using Camunda's existing type system,
 * allowing the system to only call relevant interceptors.
 * </p>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Type-specific interceptor using Camunda types:</h3>
 * <pre>
 * &#64;Component
 * public class JsonVariableInterceptor implements VariableInterceptor {
 *   &#64;Override
 *   public Set&lt;Class&lt;?&gt;&gt; getTypes() {
 *     return Set.of(ObjectValue.class); // Handle ObjectValue types
 *   }
 *
 *   &#64;Override
 *   public void execute(VariableContext context) {
 *     // This will only be called for ObjectValue variables
 *     ObjectValue objectValue = (ObjectValue) context.getC7TypedValue();
 *     // Process based on serialization format
 *   }
 * }
 * </pre>
 *
 * <h3>Entity type-specific interceptor (process variables only):</h3>
 * <pre>
 * &#64;Component
 * public class ProcessVariableInterceptor implements VariableInterceptor {
 *   &#64;Override
 *   public Set&lt;Class&lt;? extends ValueFields&gt;&gt; getEntityTypes() {
 *     // Only handle historic process variables, not decision inputs/outputs
 *     return Set.of(HistoricVariableInstanceEntity.class, VariableInstanceEntity.class);
 *   }
 *
 *   &#64;Override
 *   public void execute(VariableContext context) {
 *     // This will only be called for process variables
 *     processVariable(context);
 *   }
 * }
 * </pre>
 *
 * <h3>Decision-specific interceptor:</h3>
 * <pre>
 * &#64;Component
 * public class DecisionVariableInterceptor implements VariableInterceptor {
 *   &#64;Override
 *   public Set&lt;Class&lt;? extends ValueFields&gt;&gt; getEntityTypes() {
 *     // Only handle decision inputs and outputs
 *     return Set.of(HistoricDecisionInputInstanceEntity.class,
 *                   HistoricDecisionOutputInstanceEntity.class);
 *   }
 *
 *   &#64;Override
 *   public void execute(VariableContext context) {
 *     // This will only be called for decision variables
 *     processDecisionVariable(context);
 *   }
 * }
 * </pre>
 *
 * <h3>Universal interceptor (handles all types):</h3>
 * <pre>
 * &#64;Component
 * public class UniversalInterceptor implements VariableInterceptor {
 *   &#64;Override
 *   public Set&lt;Class&lt;?&gt;&gt; getTypes() {
 *     return Set.of(); // Empty set = handle all types
 *   }
 *
 *   &#64;Override
 *   public void execute(VariableContext context) {
 *     // This will be called for all variable types
 *     logVariableAccess(context.getName());
 *   }
 * }
 * </pre>
 *
 * <h3>Disabling interceptors via YAML configuration:</h3>
 * <pre>
 * migrator:
 *   interceptors:
 *     - className: "io.camunda.migration.data.impl.interceptor.SpinJsonVariableTransformer"
 *       enabled: false
 * </pre>
 */
public interface VariableInterceptor {

  /**
   * Executes the interceptor logic for a variable context.
   * This method will only be called if the variable type matches one of the supported types.
   *
   * @param context the variable context containing C7 variable data and methods to modify it
   */
  void execute(VariableContext context);

  /**
   * Returns the set of variable value types that this interceptor can handle.
   * <p>
   * Use Camunda's existing type interfaces like:
   * - {@code PrimitiveValue.class} for primitive variables
   * - {@code DateValue.class} for date variables
   * - {@code ObjectValue.class} for object variables (JSON, XML, Java serialized)
   * - {@code FileValue.class} for file variables
   * - {@code SpinValue.class} for Spin variables
   * </p>
   * <p>
   * If the returned set is empty, this interceptor will be called for all variable types.
   * </p>
   * <p>
   * Default implementation returns an empty set (handle all types).
   * </p>
   *
   * @return set of supported variable value types, or empty set to handle all types
   */
  default Set<Class<?>> getTypes() {
    return Set.of(); // Empty set = handle all types for backward compatibility
  }

  /**
   * Returns the set of entity types that this interceptor can handle.
   * <p>
   * Use Camunda's entity classes like:
   * - {@code HistoricVariableInstanceEntity.class} for historic process variables
   * - {@code VariableInstanceEntity.class} for runtime process variables
   * - {@code HistoricDecisionInputInstanceEntity.class} for decision input variables
   * - {@code HistoricDecisionOutputInstanceEntity.class} for decision output variables
   * </p>
   * <p>
   * If the returned set is empty, this interceptor will be called for all entity types.
   * </p>
   * <p>
   * This allows interceptors to filter by the source entity type,providing fine-grained
   * control over when an interceptor is executed.
   * </p>
   * <p>
   * Default implementation returns an empty set (handle all entity types).
   * </p>
   *
   * @return set of supported entity types, or empty set to handle all entity types
   */
  default Set<Class<? extends ValueFields>> getEntityTypes() {
    return Set.of();
  }

}
