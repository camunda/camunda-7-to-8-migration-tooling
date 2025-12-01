/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.interceptor;


/**
 * Interceptor interface for handling variable invocations with type-specific filtering.
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
 *   public void execute(VariableInvocation invocation) {
 *     // This will only be called for ObjectValue variables
 *     ObjectValue objectValue = (ObjectValue) invocation.getC7Variable().getTypedValue();
 *     // Process based on serialization format
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
 *   public void execute(VariableInvocation invocation) {
 *     // This will be called for all variable types
 *     logVariableAccess(invocation.getC7Variable());
 *   }
 * }
 * </pre>
 *
 * <h3>Disabling interceptors via YAML configuration:</h3>
 * <pre>
 * migrator:
 *   interceptors:
 *     - className: "io.camunda.migrator.impl.interceptor.SpinJsonVariableTransformer"
 *       enabled: false
 * </pre>
 * <p>
 * The {@link #getTypes()} method inherited from {@link GlobalInterceptor} allows specifying
 * variable value types like:
 * - {@code PrimitiveValue.class} for primitive variables
 * - {@code DateValue.class} for date variables
 * - {@code ObjectValue.class} for object variables (JSON, XML, Java serialized)
 * - {@code FileValue.class} for file variables
 * - {@code SpinValue.class} for Spin variables
 * </p>
 */
public interface VariableInterceptor extends GlobalInterceptor<VariableInvocation> {

  /**
   * Executes the interceptor logic for a variable invocation.
   * This method will only be called if the variable type matches one of the supported types.
   *
   * @param invocation the variable invocation containing C7 variable data and methods to modify it
   */
  @Override
  void execute(VariableInvocation invocation);
}
