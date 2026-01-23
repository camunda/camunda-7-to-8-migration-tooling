/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.identity;

import static io.camunda.migration.data.impl.identity.AuthorizationEntityRegistry.WILDCARD;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_GLOBAL_AND_REVOKE_UNSUPPORTED;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_OWNER_NOT_EXISTS;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNEXPECTED_ERROR;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_PERMISSION_TYPE;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_RESOURCE_ID;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_RESOURCE_TYPE;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_SPECIFIC_RESOURCE_ID;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static io.camunda.migration.data.impl.util.ExceptionUtils.callApi;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.camunda.bpm.engine.authorization.Resources.APPLICATION;
import static org.camunda.bpm.engine.authorization.Resources.DECISION_DEFINITION;
import static org.camunda.bpm.engine.authorization.Resources.DEPLOYMENT;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_DEFINITION;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.migration.data.exception.IdentityMigratorException;
import io.camunda.migration.data.exception.MigratorException;
import io.camunda.migration.data.impl.clients.C8Client;
import io.camunda.migration.data.impl.logging.IdentityMigratorLogs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.RepositoryService;
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

  @Autowired
  protected DefinitionLookupService definitionLookupService;

  /**
   * Validates and maps a C7 authorization to a C8 authorization if the validation is successful.
   * If the validation fails, a failure reason is provided in the result.
   * @param authorization The C7 authorization to be mapped.
   * @return The result of the authorization mapping, containing either the mapped C8 authorization or a failure reason.
   */
  public AuthorizationMappingResult mapAuthorization(Authorization authorization) {
    try {
      return attemptToMapAuthorization(authorization);
    } catch (MigratorException e) {
      return AuthorizationMappingResult.failure(format(FAILURE_UNEXPECTED_ERROR, authorization.getId(), e.getMessage()));
    }
  }

  protected AuthorizationMappingResult attemptToMapAuthorization(Authorization authorization) {
    if (authorization.getAuthorizationType() != Authorization.AUTH_TYPE_GRANT) {
      return AuthorizationMappingResult.failure(FAILURE_GLOBAL_AND_REVOKE_UNSUPPORTED);
    }

    Resource c7ResourceType = ResourceTypeUtil.getResourceByType(authorization.getResourceType());
    if (!AuthorizationEntityRegistry.isSupported(c7ResourceType)) {
      return AuthorizationMappingResult.failure(format(FAILURE_UNSUPPORTED_RESOURCE_TYPE, c7ResourceType.resourceName()));
    }

    var mappingForResourceType = AuthorizationEntityRegistry.findMappingForResourceType(c7ResourceType).get();
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

    if (!ownerExists(authorization.getUserId(), authorization.getGroupId())) {
      return AuthorizationMappingResult.failure(FAILURE_OWNER_NOT_EXISTS);
    }

    OwnerType ownerType = isNotBlank(authorization.getUserId()) ? OwnerType.USER : OwnerType.GROUP;
    String ownerId = isNotBlank(authorization.getUserId()) ? authorization.getUserId() : authorization.getGroupId();

    Set<String> c8ResourceIds = mapResourceId(c7ResourceType, mappingForResourceType, authorization.getResourceId());
    if (c8ResourceIds.isEmpty()) {
      return AuthorizationMappingResult.failure(format(FAILURE_UNSUPPORTED_RESOURCE_ID, authorization.getResourceId(), c7ResourceType.resourceName()));
    }

    var c8Auths = new ArrayList<C8Authorization>();
    for (String c8ResourceId : c8ResourceIds) {
      // Create an authorization for each mapped resource ID
      c8Auths.add(new C8Authorization(ownerType, ownerId, c8ResourceType, c8ResourceId, c8Permissions));
    }

    return AuthorizationMappingResult.success(c8Auths);
  }

  protected Set<String> mapResourceId(Resource c7ResourceType, AuthorizationMappingEntry authMapping, String resourceId) {
    if (authMapping.needsIdMapping()) {
      return switch (c7ResourceType) {
        case APPLICATION -> mapApplicationToComponentId(resourceId);
        case DEPLOYMENT -> mapDeploymentIdToResourceKeys(resourceId);
        case PROCESS_DEFINITION, DECISION_DEFINITION -> mapDefinitionKeyToPrefixedKey(resourceId);
        default -> throw new IdentityMigratorException("No dynamic id mapper configured for resource type: " + c7ResourceType.resourceName());
      };
    } else {
      return Set.of(resourceId);
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
        throw new IdentityMigratorException("Cannot verify owner existence: " + userOrGroup, e);
      }
    }
  }

  protected boolean equals(Permission permission1, Permission permission2) {
    return permission1.getName().equals(permission2.getName()) &&
        permission1.getValue() == permission2.getValue();
  }

  /**
   * Maps C7 application IDs to C8 component IDs.
   * @param applicationId
   * @return the equivalent component ID
   */
  protected Set<String> mapApplicationToComponentId(String applicationId) {
    return switch (applicationId) {
      case WILDCARD -> Set.of(WILDCARD);
      case "cockpit" -> Set.of("operate");
      case "tasklist" -> Set.of("tasklist");
      case "admin" -> Set.of("identity");
      default -> Set.of();
    };
  }

  /**
   * Maps C7 deployment IDs to all resource IDs (definition keys) that belong to that deployment
   * @param resourceId
   * @return a set of all resource keys belonging to the deployment
   */
  protected Set<String> mapDeploymentIdToResourceKeys(String resourceId) {
    // Retrieve all keys
    Set<String> allDefinitionsInDeployment = callApi(
        () -> definitionLookupService.getAllDefinitionKeysForDeployment(resourceId),
        "There was an error while querying for definitions in deployment " + resourceId
    );
    IdentityMigratorLogs.foundDefinitionsInDeployment(allDefinitionsInDeployment.size(), resourceId);
    // Then map each key to both the original and the prefixed key
    return allDefinitionsInDeployment.stream()
        .flatMap(key -> mapDefinitionKeyToPrefixedKey(key).stream())
        .collect(Collectors.toSet());
  }

  /**
   * Maps C7 definition keys to the same key and the legacy prefixed key in C8
   * This is because when we migrate history data, to avoid collisions, we prefix all definition keys with C7_LEGACY_PREFIX
   * And we need to grant authorizations to both the original and the prefixed keys
   * @param resourceId
   * @return a set containing both the original and the prefixed key
   */
  protected Set<String> mapDefinitionKeyToPrefixedKey(String resourceId) {
    if (resourceId.equals(WILDCARD)) {
      return Set.of(WILDCARD);
    } else {
      return Set.of(resourceId, prefixDefinitionId(resourceId));
    }
  }
}
