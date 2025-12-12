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
import java.util.Set;
import org.camunda.bpm.engine.authorization.Permission;

public record AuthorizationMappingEntry(
    ResourceType c8ResourceType,
    boolean needsResourceIdMapping, // whether resource ID mapping is needed for this resource type
    boolean isSpecificResourceIdSupported, // if false, only wildcard resource IDs are supported
    Map<Permission, Set<PermissionType>> permissionMapping,
    Map<String, String> resourceIdMapping) {

  public boolean needsResourceIdMapping() {
    return needsResourceIdMapping;
  }

  public String getMappedResourceId(String c7ResourceId) {
    return resourceIdMapping.getOrDefault(c7ResourceId, null);
  }

  public boolean isPermissionMappingSupported(Permission permission) {
    return permissionMapping.containsKey(permission);
  }

  public Set<PermissionType> getMappedPermissions(Permission permission) {
    return permissionMapping.get(permission);
  }

}