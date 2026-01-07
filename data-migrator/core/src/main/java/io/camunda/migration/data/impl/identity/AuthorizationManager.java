/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.identity;

import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_GLOBAL_AND_REVOKE_UNSUPPORTED;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_PERMISSION_TYPE;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_RESOURCE_ID;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_RESOURCE_TYPE;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_SPECIFIC_RESOURCE_ID;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_OWNER_NOT_EXISTS;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.migration.data.exception.IdentityMigratorException;
import io.camunda.migration.data.exception.MigratorException;
import io.camunda.migration.data.impl.clients.C8Client;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ResourceTypeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationManager {

  @Autowired
  protected ProcessEngineConfigurationImpl processEngineConfiguration;

  @Autowired
  protected C8Client c8Client;

  /**
   * Validates and maps a C7 authorization to a C8 authorization if the validation is successful.
   * If the validation fails, a failure reason is provided in the result.
   * @param authorization The C7 authorization to be mapped.
   * @return The result of the authorization mapping, containing either the mapped C8 authorization or a failure reason.
   */
  public AuthorizationMappingResult mapAuthorization(Authorization authorization) {
    if (authorization.getAuthorizationType() != Authorization.AUTH_TYPE_GRANT) {
      return AuthorizationMappingResult.failure(FAILURE_GLOBAL_AND_REVOKE_UNSUPPORTED);
    }

    if (!ownerExists(authorization.getUserId(), authorization.getGroupId())) {
      return AuthorizationMappingResult.failure(FAILURE_OWNER_NOT_EXISTS);
    }

    Resource c7ResourceType = ResourceTypeUtil.getResourceByType(authorization.getResourceType());
    if (!AuthorizationEntityRegistry.isSupported(c7ResourceType)) {
      return AuthorizationMappingResult.failure(format(FAILURE_UNSUPPORTED_RESOURCE_TYPE, c7ResourceType.resourceName()));
    }

    var mappingForResourceType = AuthorizationEntityRegistry.getMappingForResourceType(c7ResourceType);
    ResourceType c8ResourceType = mappingForResourceType.c8ResourceType();
    Set<Permission> c7Permissions = decodePermissions(authorization);

    Set<PermissionType> c8Permissions = new HashSet<>();
    for (Permission permission : c7Permissions) {
      if (mappingForResourceType.isPermissionMappingSupported(permission)) {
        Set<PermissionType> c8MappedPermissions = mappingForResourceType.getMappedPermissions(permission);
        c8Permissions.addAll(c8MappedPermissions);
      } else {
        return AuthorizationMappingResult.failure(format(FAILURE_UNSUPPORTED_PERMISSION_TYPE, permission.getName(), c7ResourceType.resourceName()));
      }
    }
    if (!isValidResourceId(mappingForResourceType, authorization.getResourceId())) {
      return AuthorizationMappingResult.failure(format(FAILURE_UNSUPPORTED_SPECIFIC_RESOURCE_ID, authorization.getResourceId(), c7ResourceType.resourceName()));
    }

    String c8ResourceId = mapResourceId(mappingForResourceType, authorization.getResourceId());
    if (c8ResourceId == null) {
      return AuthorizationMappingResult.failure(format(FAILURE_UNSUPPORTED_RESOURCE_ID, authorization.getResourceId(), c7ResourceType.resourceName()));
    }

    OwnerType ownerType = isNotBlank(authorization.getUserId()) ? OwnerType.USER : OwnerType.GROUP;
    String ownerId = isNotBlank(authorization.getUserId()) ? authorization.getUserId() : authorization.getGroupId();

    return AuthorizationMappingResult.success(new C8Authorization(ownerType, ownerId, c8ResourceType, c8ResourceId, c8Permissions));
  }

  protected String mapResourceId(AuthorizationMappingEntry authMapping, String resourceId) {
    if (authMapping.needsToAdaptId()) {
      return authMapping.getMappedResourceId(resourceId);
    } else {
      return resourceId;
    }
  }

  protected boolean isValidResourceId(AuthorizationMappingEntry authMapping, String resourceId) {
    if (resourceId.equals("*")) {
      return true; // Available for all resource types
    } else
      return authMapping.supportsExplicitId(); // Specific resource IDs are supported
  }

  protected Set<Permission> decodePermissions(Authorization authorization) {
    int resourceType = authorization.getResourceType();
    Permission[] permissionsForResourceType = processEngineConfiguration.getPermissionProvider().getPermissionsForResource(resourceType);
    Set<Permission> permissions = Arrays.stream(authorization.getPermissions(permissionsForResourceType)).collect(Collectors.toSet());

    if (permissions.stream().anyMatch(permission -> equals(permission, Permissions.ALL))) { // If it has ALL
      return permissions.stream().filter(permission -> equals(permission, Permissions.ALL)).collect(Collectors.toSet()); // Return only ALL
    } else { // Else, remove NONE from the list and return singular permissions
      return permissions.stream().filter(permission -> !equals(permission, Permissions.NONE)).collect(Collectors.toSet());
    }
  }

  protected boolean ownerExists(String userId, String groupId) {
    Object userOrGroup = null;
    try {
      if (isNotBlank(userId)) {
        userOrGroup = c8Client.getUser(userId);
      } else if (isNotBlank(groupId)) {
        userOrGroup = c8Client.getGroup(groupId);
      }
      return userOrGroup != null;
    } catch (MigratorException e) {
      if (e.getCause() instanceof ProblemException pe && pe.details().getStatus() == 404) { // Not found
        return false;
      } else {
        throw new IdentityMigratorException("Cannot verify owner existence", e);
      }
    }
  }

  protected boolean equals(Permission permission1, Permission permission2) {
    return permission1.getName().equals(permission2.getName()) &&
        permission1.getValue() == permission2.getValue();
  }
}
