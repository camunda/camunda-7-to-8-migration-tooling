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
 * Utility class for determining entity type compatibility with interceptors.
 * <p>
 * This class works with Camunda 7 historic entity classes to determine if an interceptor
 * should process a particular entity based on its type.
 * </p>
 */
public final class EntityTypeDetector {

  private EntityTypeDetector() {
    // Utility class - prevent instantiation
  }

  /**
   * Checks if an interceptor supports a specific entity based on its conversion context.
   *
   * @param interceptor the interceptor to check
   * @param context the entity conversion context
   * @return true if the interceptor supports the entity type
   */
  public static boolean supportsEntity(EntityInterceptor interceptor, EntityConversionContext<?, ?> context) {
    return supportsEntityType(interceptor, context.getEntityType());
  }

  /**
   * Checks if an interceptor supports a specific entity type.
   *
   * @param interceptor the interceptor to check
   * @param entityType the entity type class to check
   * @return true if the interceptor supports the entity type
   */
  public static boolean supportsEntityType(EntityInterceptor interceptor, Class<?> entityType) {
    var supportedTypes = interceptor.getTypes();

    // Empty set means handle all types
    if (supportedTypes.isEmpty()) {
      return true;
    }

    // Check if any supported type matches or is assignable from the actual entity type
    return supportedTypes.stream()
        .anyMatch(supportedType -> supportedType.isAssignableFrom(entityType));
  }

  /**
   * Checks if an interceptor supports a specific entity instance.
   *
   * @param interceptor the interceptor to check
   * @param entity the entity instance to check
   * @return true if the interceptor supports the entity type
   */
  public static boolean supportsEntity(EntityInterceptor interceptor, Object entity) {
    if (entity == null) {
      return false;
    }
    return supportsEntityType(interceptor, entity.getClass());
  }
}

