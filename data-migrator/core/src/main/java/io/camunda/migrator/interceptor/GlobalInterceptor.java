/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.interceptor;

import java.util.Set;

/**
 * Base interceptor interface for handling invocations during migration.
 * <p>
 * This interface serves as the parent interface for all interceptors in the migration system,
 * providing a common contract for intercepting and processing data during migration operations.
 * </p>
 * <p>
 * Implementers should define specific interceptor behavior through specialized sub-interfaces
 * like {@link VariableInterceptor} and {@link EntityInterceptor}.
 * </p>
 *
 * @param <T> the type of invocation object that this interceptor handles
 */
public interface GlobalInterceptor<T> {

  /**
   * Executes the interceptor logic for an invocation.
   *
   * @param invocation the invocation containing data and methods to process or modify it
   */
  void execute(T invocation);

  /**
   * Returns the set of types that this interceptor can handle.
   * <p>
   * Interceptors can specify which types they handle to allow the system to only call
   * relevant interceptors based on the data type being processed.
   * </p>
   * <p>
   * For {@link VariableInterceptor}, use Camunda's variable value types like:
   * - {@code PrimitiveValue.class} for primitive variables
   * - {@code DateValue.class} for date variables
   * - {@code ObjectValue.class} for object variables (JSON, XML, Java serialized)
   * - {@code FileValue.class} for file variables
   * - {@code SpinValue.class} for Spin variables
   * </p>
   * <p>
   * For {@link EntityInterceptor}, use Camunda 7 historic entity classes like:
   * - {@code HistoricProcessInstance.class} for process instances
   * - {@code HistoricActivityInstance.class} for flow nodes/activities
   * - {@code HistoricVariableInstance.class} for variables
   * - {@code HistoricTaskInstance.class} for user tasks
   * - {@code HistoricIncident.class} for incidents
   * - {@code HistoricDecisionInstance.class} for decision instances
   * </p>
   * <p>
   * If the returned set is empty, this interceptor will be called for all types.
   * </p>
   * <p>
   * Default implementation returns an empty set (handle all types) for backward compatibility.
   * </p>
   *
   * @return set of supported types, or empty set to handle all types
   */
  default Set<Class<?>> getTypes() {
    return Set.of(); // Empty set = handle all types for backward compatibility
  }
}

