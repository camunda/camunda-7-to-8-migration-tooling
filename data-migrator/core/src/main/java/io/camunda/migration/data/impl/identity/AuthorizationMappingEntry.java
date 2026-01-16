/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.identity;

import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.camunda.bpm.engine.authorization.Permission;

public record AuthorizationMappingEntry (
    ResourceType c8ResourceType,
    boolean supportsExplicitId, // when true, explicit ids (different from '*') are supported for the resource type
    boolean needsIdMapping, // when true, the resource id needs to be mapped before migration
    Map<Permission, Set<PermissionType>> permissionMapping
  ) {

  public AuthorizationMappingEntry {
    Objects.requireNonNull(c8ResourceType, "c8ResourceType");
    permissionMapping = Map.copyOf(Objects.requireNonNull(permissionMapping, "permissionMapping"));
  }

  public boolean isPermissionMappingSupported(Permission permission) {
    return permissionMapping.containsKey(permission);
  }

  public Set<PermissionType> getMappedPermissions(Permission permission) {
    return permissionMapping.get(permission);
  }

  public static Builder builder(ResourceType c8ResourceType) {
    return new Builder(c8ResourceType);
  }

  public static final class Builder {
    private final ResourceType c8ResourceType;
    private boolean supportsExplicitId;
    private boolean needsIdMapping;
    private Map<Permission, Set<PermissionType>> permissionMapping = Map.of();

    public Builder(ResourceType c8ResourceType) {
      this.c8ResourceType = Objects.requireNonNull(c8ResourceType, "c8ResourceType");
    }

    public Builder supportsExplicitId(boolean value) {
      this.supportsExplicitId = value;
      return this;
    }

    public Builder needsIdMapping(boolean value) {
      this.needsIdMapping = value;
      return this;
    }

    public Builder permissionMapping(Map<Permission, Set<PermissionType>> value) {
      this.permissionMapping = Objects.requireNonNull(value, "permissionMapping");
      return this;
    }

    public AuthorizationMappingEntry build() {
      return new AuthorizationMappingEntry(
          c8ResourceType,
          supportsExplicitId,
          needsIdMapping,
          permissionMapping);
    }
  }
}