/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.example;

import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example entity interceptor that can be packaged as a JAR
 * and configured via config file without Spring Boot annotations.
 *
 * This demonstrates:
 * - How to create a standalone interceptor that handles ALL entity types
 * - How to handle configurable properties
 * - How to perform custom entity transformations
 * - How to log entity conversions for audit purposes
 */
public class MyCustomEntityInterceptor implements EntityInterceptor<Object, Object> {

  protected static final Logger LOGGER = LoggerFactory.getLogger(MyCustomEntityInterceptor.class);

  // Configurable properties that can be set via application.yml
  protected boolean auditEnabled = true;
  protected String tenantPrefix = "";

  /**
   * Return an empty set to handle all entity types.
   * This interceptor will be called for process instances, user tasks,
   * variables, incidents, and all other entity types.
   */
  @Override
  public Set<Class<?>> getTypes() {
    return Set.of(); // Empty set = handle all types
  }

  @Override
  public void execute(EntityConversionContext<Object, Object> context) {
    if (auditEnabled) {
      LOGGER.info(
          "Converting entity type: {} with ID: {}",
          context.getC7Entity().getClass().getSimpleName(),
          getEntityId(context.getC7Entity()));
    }

    // Example: Add a prefix to tenant IDs if configured
    if (!tenantPrefix.isEmpty()) {
      modifyTenantId(context);
    }

    if (auditEnabled) {
      LOGGER.info(
          "Completed conversion for entity type: {}",
          context.getC7Entity().getClass().getSimpleName());
    }
  }

  /**
   * Attempts to extract an ID from the Camunda 7 entity for logging purposes.
   * This is a simplified example and may need to be adapted for specific entity types.
   */
  protected String getEntityId(Object entity) {
    try {
      // Most historic entities have an getId() method
      return entity.getClass().getMethod("getId").invoke(entity).toString();
    } catch (Exception e) {
      return "unknown";
    }
  }

  /**
   * Example method to modify tenant ID in the builder.
   * This demonstrates how to manipulate the C8 entity builder.
   */
  protected void modifyTenantId(EntityConversionContext<?, ?> context) {
    try {
      // Check if C7 entity has getTenantId method
      Object tenantIdObj = context.getC7Entity().getClass()
          .getMethod("getTenantId")
          .invoke(context.getC7Entity());

      if (tenantIdObj != null) {
        String originalTenantId = tenantIdObj.toString();
        String modifiedTenantId = tenantPrefix + originalTenantId;

        // Check if builder has tenantId method
        context.getC8DbModelBuilder().getClass()
            .getMethod("tenantId", String.class)
            .invoke(context.getC8DbModelBuilder(), modifiedTenantId);

        if (auditEnabled) {
          LOGGER.debug(
              "Modified tenant ID from '{}' to '{}'",
              originalTenantId,
              modifiedTenantId);
        }
      }
    } catch (Exception e) {
      // Not all entities have tenant IDs, so we silently ignore errors
      LOGGER.trace("Entity type {} does not support tenant ID modification",
          context.getC7Entity().getClass().getSimpleName());
    }
  }

  // Setter methods for config properties
  public void setAuditEnabled(boolean auditEnabled) {
    this.auditEnabled = auditEnabled;
  }

  public void setTenantPrefix(String tenantPrefix) {
    this.tenantPrefix = tenantPrefix;
  }

  // Getter methods
  public boolean isAuditEnabled() {
    return auditEnabled;
  }

  public String getTenantPrefix() {
    return tenantPrefix;
  }
}

