/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.identity;

import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.authorization.Resources;

public class AuthMapper {

  public static final Map<Pair<Resource, Permission>, Pair<ResourceType, Set<PermissionType>>> AUTH_MAPPING_TABLE = Map.of(
      Pair.of(Resources.TENANT, Permissions.ALL), Pair.of(ResourceType.TENANT, getAllSupportedPermissionsForResourceType(ResourceType.TENANT)),
      Pair.of(Resources.TENANT, Permissions.READ), Pair.of(ResourceType.TENANT, Set.of(PermissionType.READ)),
      Pair.of(Resources.TENANT, Permissions.CREATE), Pair.of(ResourceType.TENANT, Set.of(PermissionType.CREATE)),
      Pair.of(Resources.TENANT, Permissions.UPDATE), Pair.of(ResourceType.TENANT, Set.of(PermissionType.UPDATE)),
      Pair.of(Resources.TENANT, Permissions.DELETE), Pair.of(ResourceType.TENANT, Set.of(PermissionType.DELETE)),
      Pair.of(Resources.TENANT_MEMBERSHIP, Permissions.ALL), Pair.of(ResourceType.TENANT, Set.of(PermissionType.UPDATE)),
      Pair.of(Resources.TENANT_MEMBERSHIP, Permissions.CREATE), Pair.of(ResourceType.TENANT, Set.of(PermissionType.UPDATE)),
      Pair.of(Resources.TENANT_MEMBERSHIP, Permissions.DELETE), Pair.of(ResourceType.TENANT, Set.of(PermissionType.UPDATE))
  );

  public static Pair<ResourceType, Set<PermissionType>> mapAuthorization(Resource resource, Permission permission) {
    return AUTH_MAPPING_TABLE.get(Pair.of(resource, permission));
  }

  private static Set<PermissionType> getAllSupportedPermissionsForResourceType(ResourceType resourceType) {
    return AuthorizationResourceType
        .valueOf(resourceType.name())
        .getSupportedPermissionTypes()
        .stream()
        .map(permissionType -> PermissionType.valueOf(permissionType.name())) // Needed to convert from io.camunda.zeebe.protocol.record.value.PermissionType to io.camunda.client.api.search.enums.PermissionType
        .collect(Collectors.toSet());
  }

}
