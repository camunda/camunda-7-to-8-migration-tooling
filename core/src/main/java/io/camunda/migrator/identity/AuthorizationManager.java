/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.identity;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.impl.search.filter.AuthorizationFilterImpl;
import io.camunda.client.impl.search.response.AuthorizationImpl;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ResourceTypeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationManager {

  @Autowired
  private ProcessEngineConfigurationImpl processEngineConfiguration;

  @Autowired
  private AuthorizationService authorizationService;

  @Autowired
  private CamundaClient camundaClient;

  /**
   * Retrieve all permissions from C7 and map them to C7Auth
   * @return
   */
  public List<C7Auth> getC7Permissions() {
    return getAuthorizations()
        .stream()
        .flatMap(auth -> decodePermissions(auth).stream()
            .map(perm -> new C7Auth(ResourceTypeUtil.getResourceByType(
                auth.getResourceType()),
                auth.getResourceId(),
                getOwner(auth),
                perm)))
        .toList();
  }

  public boolean permissionExistsInC8(C8Auth c8Auth) {
    SearchResponse<?> response = null;
    try {
      response = camundaClient.newAuthorizationSearchRequest()
          .filter(new AuthorizationFilterImpl()
              .ownerType(c8Auth.owner().ownerType())
              .ownerId(c8Auth.owner().ownerId())
              .resourceType(c8Auth.resourceType())
              .resourceIds(c8Auth.resourceId()))
          .execute();
    } catch (ProblemException e) {
      if (e.details().getStatus() == 404) {
        return false;
      }
    }

    if (response == null) {
      return false;
    } else {
      return response.items()
          .stream()
          .map(o -> ((AuthorizationImpl) o).getPermissionTypes())
          .anyMatch(permissionTypes -> permissionTypes.containsAll(c8Auth.permissions()));
    }
  }

  private List<Authorization> getAuthorizations() {
    return authorizationService
        .createAuthorizationQuery()
        .authorizationType(Authorization.AUTH_TYPE_GRANT) // C8 only supports GRANT, consider iterating over other types and log a warning so that the user knows they won't be migrated
        .list();
  }

  /**
   * For a given authorization, it retrieves all granted permissions for a given resource type
   * @param authorization
   * @return
   */
  private Set<Permission> decodePermissions(Authorization authorization) {
    int resourceType = authorization.getResourceType();
    Permission[] permissionsForResourceType = processEngineConfiguration.getPermissionProvider().getPermissionsForResource(resourceType);

    Set<Permission> permissions = Arrays.stream(authorization.getPermissions(permissionsForResourceType)).collect(Collectors.toSet());

    // Check for ALL and NONE (using anyMatch and equals because ALL exists for each ResourceType)
    if (permissions.stream().anyMatch(permission -> equals(permission, Permissions.ALL))) { // If has ALL
      return permissions.stream().filter(permission -> equals(permission, Permissions.ALL)).collect(Collectors.toSet()); // Return only ALL
    } else { // Else, remove ALL and NONE from the list and return singular permissions
      return permissions.stream().filter(permission -> !equals(permission, Permissions.ALL) && !equals(permission, Permissions.NONE)).collect(Collectors.toSet());
    }
  }

  private boolean equals(Permission permission1, Permission permission2) {
    return permission1.getName().equals(permission2.getName()) &&
        permission1.getValue() == permission2.getValue();
  }

  private Owner getOwner(Authorization authorization) {
    if (isNotBlank(authorization.getUserId())) {
      return new Owner(OwnerType.USER, authorization.getUserId());
    } else if (isNotBlank(authorization.getGroupId())) {
      return new Owner(OwnerType.GROUP, authorization.getGroupId());
    } else {
      return null; // Should be handled properly
    }
  }

}
