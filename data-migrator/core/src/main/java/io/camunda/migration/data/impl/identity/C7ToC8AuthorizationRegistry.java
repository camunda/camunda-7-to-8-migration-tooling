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
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.authorization.Resources;

public class C7ToC8AuthorizationRegistry {

  protected static final Map<Resource, AuthorizationMappingEntry> REGISTRY = new HashMap<>();

  static {

    REGISTRY.put(Resources.APPLICATION, new AuthorizationMappingEntry(ResourceType.COMPONENT, true,
        true,
        Map.of(
            Permissions.ALL, getAllSupportedPerms(ResourceType.COMPONENT),
            Permissions.ACCESS, Set.of(PermissionType.ACCESS)),
        Map.of(
            "*", "*",
            "cockpit", "operate",
            "tasklist", "tasklist",
            "admin", "identity"
        )));

    REGISTRY.put(Resources.AUTHORIZATION, new AuthorizationMappingEntry(ResourceType.AUTHORIZATION,
        false,
        false,
        Map.of(
            Permissions.ALL, getAllSupportedPerms(ResourceType.AUTHORIZATION),
            Permissions.READ, Set.of(PermissionType.READ),
            Permissions.UPDATE, Set.of(PermissionType.UPDATE),
            Permissions.CREATE, Set.of(PermissionType.CREATE),
            Permissions.DELETE, Set.of(PermissionType.DELETE)),
        null
    ));

    REGISTRY.put(Resources.GROUP, new AuthorizationMappingEntry(ResourceType.GROUP,
        false,
        true,
        Map.of(
            Permissions.ALL, getAllSupportedPerms(ResourceType.GROUP),
            Permissions.READ, Set.of(PermissionType.READ),
            Permissions.UPDATE, Set.of(PermissionType.UPDATE),
            Permissions.CREATE, Set.of(PermissionType.CREATE),
            Permissions.DELETE, Set.of(PermissionType.DELETE)),
        null
    ));

    REGISTRY.put(Resources.GROUP_MEMBERSHIP, new AuthorizationMappingEntry(ResourceType.GROUP,
        false,
        true,
        Map.of(
            Permissions.ALL, Set.of(PermissionType.UPDATE),
            Permissions.CREATE, Set.of(PermissionType.UPDATE),
            Permissions.DELETE, Set.of(PermissionType.UPDATE)),
        null
    ));

    REGISTRY.put(Resources.SYSTEM, new AuthorizationMappingEntry(ResourceType.SYSTEM,
        false,
        false,
        Map.of(Permissions.READ, Set.of(PermissionType.READ, PermissionType.READ_USAGE_METRIC)),
        null
    ));

    // TODO
    REGISTRY.put(Resources.BATCH, new AuthorizationMappingEntry(ResourceType.BATCH,
        false,
        false,
        null,
        null
    ));

    // TODO
    REGISTRY.put(Resources.TENANT, new AuthorizationMappingEntry(ResourceType.TENANT,
        false,
        false,
        null,
        null
    ));

    // TODO
    REGISTRY.put(Resources.TENANT_MEMBERSHIP, new AuthorizationMappingEntry(ResourceType.TENANT,
        false,
        false,
        null,
        null
    ));

    // TODO
    REGISTRY.put(Resources.USER, new AuthorizationMappingEntry(ResourceType.USER,
        false,
        false,
        null,
        null
    ));
  }

  public static boolean isSupported(Resource resource) {
    return REGISTRY.containsKey(resource);
  }

  public static AuthorizationMappingEntry getMappingForResourceType(Resource resource) {
    return REGISTRY.get(resource);
  }

  protected static Set<PermissionType> getAllSupportedPerms(ResourceType resourceType) {
    return AuthorizationResourceType
        .valueOf(resourceType.name())
        .getSupportedPermissionTypes()
        .stream()
        .map(permissionType -> PermissionType.valueOf(permissionType.name())) // Needed to convert from io.camunda.zeebe.protocol.record.value.PermissionType to io.camunda.client.api.search.enums.PermissionType
        .collect(Collectors.toSet());
  }
}
