/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.exception;

/**
 * Exception thrown when an entity interceptor encounters an error during entity conversion.
 */
public class EntityInterceptorException extends MigratorException {

  public EntityInterceptorException(String message) {
    super(message, null);
  }

  public EntityInterceptorException(String message, Throwable cause) {
    super(message, cause);
  }
}

