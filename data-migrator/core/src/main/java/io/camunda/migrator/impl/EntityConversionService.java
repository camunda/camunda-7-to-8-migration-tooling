/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl;

import io.camunda.migrator.exception.EntityInterceptorException;
import io.camunda.migrator.impl.logging.EntityConversionServiceLogs;
import io.camunda.migrator.interceptor.EntityInterceptor;
import io.camunda.migrator.interceptor.EntityTypeDetector;
import io.camunda.migrator.interceptor.property.EntityConversionContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for managing entity conversion using interceptors.
 * <p>
 * This service orchestrates the conversion of Camunda 7 historic entities to Camunda 8 database models
 * by executing configured entity interceptors. It handles type filtering, error handling, and logging
 * throughout the conversion pipeline.
 * </p>
 * <p>
 * The service is extensible and allows for custom entity interceptors to be registered via Spring
 * configuration or YAML files.
 * </p>
 */
@Service
public class EntityConversionService {

  @Autowired(required = false)
  protected List<EntityInterceptor> configuredEntityInterceptors;

  /**
   * Converts a C7 entity to C8 database model by executing all applicable interceptors.
   * <p>
   * This method creates a new conversion context and delegates to {@link #convertWithContext(EntityConversionContext)}.
   * </p>
   *
   * @param c7Entity   the C7 historic entity
   * @param entityType the entity type class
   * @param <C7>       the C7 entity type
   * @param <C8>       the C8 database model type
   * @return the conversion context with C8 database model set
   */
  public <C7, C8> EntityConversionContext<C7, C8> convert(C7 c7Entity, Class<?> entityType) {
    EntityConversionContext<C7, C8> context = new EntityConversionContext<>(c7Entity, entityType);
    return convertWithContext(context);
  }

  /**
   * Converts a C7 entity to C8 database model by executing all applicable interceptors
   * using an existing conversion context.
   * <p>
   * This method executes all configured interceptors that support the entity type specified
   * in the context. Interceptors are executed in order, and each interceptor can modify
   * the C8 database model builder in the context.
   * </p>
   *
   * @param context the conversion context containing C7 entity and entity type
   * @param <C7>    the C7 entity type
   * @param <C8>    the C8 database model type
   * @return the same conversion context with C8 database model set by interceptors
   * @throws EntityInterceptorException if any interceptor fails during execution
   */
  public <C7, C8> EntityConversionContext<C7, C8> convertWithContext(
      EntityConversionContext<C7, C8> context) {
    if (hasInterceptors()) {
      for (EntityInterceptor interceptor : configuredEntityInterceptors) {
        // Only execute interceptors that support this entity type
        if (EntityTypeDetector.supportsEntityBasedOnContext(interceptor, context)) {
          executeInterceptor(interceptor, context);
        }
      }
    }
    return context;
  }

  /**
   * Executes a single interceptor on the conversion context.
   * <p>
   * This method handles logging and exception wrapping for interceptor execution.
   * If an interceptor throws an exception, it will be caught and wrapped in an
   * {@link EntityInterceptorException} with appropriate error messages.
   * </p>
   *
   * @param interceptor the interceptor to execute
   * @param context     the conversion context
   * @throws EntityInterceptorException if the interceptor execution fails
   */
  protected void executeInterceptor(EntityInterceptor interceptor, EntityConversionContext<?, ?> context) {
    String interceptorName = interceptor.getClass().getSimpleName();
    String entityType = context.getEntityType().getSimpleName();
    try {
      EntityConversionServiceLogs.logExecutingInterceptor(interceptorName, entityType);
      interceptor.execute(context);
    } catch (Exception ex) {
      EntityConversionServiceLogs.logInterceptorError(interceptorName, entityType);

      if (ex instanceof EntityInterceptorException) {
        throw ex;
      } else {
        throw new EntityInterceptorException(
            EntityConversionServiceLogs.formatInterceptorError(interceptorName, entityType), ex);
      }
    }
  }

  /**
   * Prepares parent properties in the conversion context by invoking
   * {@link EntityInterceptor#presetParentProperties(EntityConversionContext)}
   * on all applicable interceptors.
   *
   * @param context the conversion context
   */
  public void prepareParentProperties(EntityConversionContext<?, ?> context) {
    try {
      if (hasInterceptors()) {
        for (EntityInterceptor interceptor : configuredEntityInterceptors) {
          if (EntityTypeDetector.supportsEntityBasedOnContext(interceptor, context)) {
            interceptor.presetParentProperties(context);
          }
        }
      }
    } catch (Exception ex) {
      String interceptorName = ex.getClass().getSimpleName();
      String entityType = context.getEntityType().getSimpleName();
      EntityConversionServiceLogs.logInterceptorError(interceptorName, entityType);

      if (ex instanceof EntityInterceptorException) {
        throw ex;
      } else {
        throw new EntityInterceptorException(
            EntityConversionServiceLogs.formatInterceptorError(interceptorName, entityType), ex);
      }
    }
  }

  /**
   * Checks if there are any configured entity interceptors.
   *
   * @return true if interceptors are configured, false otherwise
   */
  private boolean hasInterceptors() {
    return configuredEntityInterceptors != null && !configuredEntityInterceptors.isEmpty();
  }
}

