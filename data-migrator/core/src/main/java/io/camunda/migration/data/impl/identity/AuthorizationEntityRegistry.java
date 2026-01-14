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
import org.camunda.bpm.engine.authorization.BatchPermissions;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.ProcessDefinitionPermissions;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.authorization.SystemPermissions;

/**
 * Registry which holds the mapping information between combinations of
 * Camunda 7 Resources/Permissions and Camunda 8 ResourceTypes/PermissionTypes.
 * All mappings not defined here are not supported.
 */
public class AuthorizationEntityRegistry {

  protected static final Map<Resource, AuthorizationMappingEntry> REGISTRY = new HashMap<>();

  static {

    REGISTRY.put(Resources.APPLICATION, new AuthorizationMappingEntry(ResourceType.COMPONENT, true,
        true,
        Map.of(
            Permissions.ALL, getAllSupportedPerms(ResourceType.COMPONENT),
            Permissions.ACCESS, getAllSupportedPerms(ResourceType.COMPONENT)), // Only ACCESS exists
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
        Map.of(Permissions.ALL, Set.of(PermissionType.UPDATE)),
        null
    ));

    REGISTRY.put(Resources.SYSTEM, new AuthorizationMappingEntry(ResourceType.SYSTEM,
        false,
        false,
        Map.of(
            SystemPermissions.ALL, getAllSupportedPerms(ResourceType.SYSTEM),
            SystemPermissions.READ, Set.of(PermissionType.READ, PermissionType.READ_USAGE_METRIC)),
        null
    ));

    REGISTRY.put(Resources.BATCH, new AuthorizationMappingEntry(ResourceType.BATCH,
        false,
        false,
        Map.of(
            BatchPermissions.ALL, getAllSupportedPerms(ResourceType.BATCH),
            BatchPermissions.READ, Set.of(PermissionType.READ),
            BatchPermissions.UPDATE, Set.of(PermissionType.UPDATE),
            BatchPermissions.CREATE, Set.of(PermissionType.CREATE),
            BatchPermissions.CREATE_BATCH_MIGRATE_PROCESS_INSTANCES, Set.of(PermissionType.CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE),
            BatchPermissions.CREATE_BATCH_MODIFY_PROCESS_INSTANCES, Set.of(PermissionType.CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE),
            BatchPermissions.CREATE_BATCH_DELETE_RUNNING_PROCESS_INSTANCES, Set.of(PermissionType.CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE, PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE),
            BatchPermissions.CREATE_BATCH_DELETE_FINISHED_PROCESS_INSTANCES, Set.of(PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE),
            BatchPermissions.CREATE_BATCH_DELETE_DECISION_INSTANCES, Set.of(PermissionType.CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE)
        ),
        null
    ));

    REGISTRY.put(Resources.TENANT, new AuthorizationMappingEntry(ResourceType.TENANT,
        false,
        true,
        Map.of(
            Permissions.ALL, getAllSupportedPerms(ResourceType.TENANT),
            Permissions.READ, Set.of(PermissionType.READ),
            Permissions.UPDATE, Set.of(PermissionType.UPDATE),
            Permissions.CREATE, Set.of(PermissionType.CREATE),
            Permissions.DELETE, Set.of(PermissionType.DELETE)),
        null
    ));

    REGISTRY.put(Resources.TENANT_MEMBERSHIP, new AuthorizationMappingEntry(ResourceType.TENANT,
        false,
        true,
        Map.of(Permissions.ALL, Set.of(PermissionType.UPDATE)),
        null
    ));

    REGISTRY.put(Resources.USER, new AuthorizationMappingEntry(ResourceType.USER,
        false,
        true,
        Map.of(
            Permissions.ALL, getAllSupportedPerms(ResourceType.USER),
            Permissions.READ, Set.of(PermissionType.READ),
            Permissions.UPDATE, Set.of(PermissionType.UPDATE),
            Permissions.CREATE, Set.of(PermissionType.CREATE),
            Permissions.DELETE, Set.of(PermissionType.DELETE)),
        null
    ));

    REGISTRY.put(Resources.DECISION_DEFINITION, new AuthorizationMappingEntry(ResourceType.DECISION_DEFINITION,
        false,
        true,
        Map.of(
            Permissions.ALL, getAllSupportedPerms(ResourceType.DECISION_DEFINITION),
            Permissions.READ, Set.of(PermissionType.READ_DECISION_DEFINITION, PermissionType.READ_DECISION_INSTANCE),
            Permissions.CREATE_INSTANCE, Set.of(PermissionType.CREATE_DECISION_INSTANCE)),
        null
    ));

    REGISTRY.put(Resources.DECISION_REQUIREMENTS_DEFINITION, new AuthorizationMappingEntry(ResourceType.DECISION_REQUIREMENTS_DEFINITION,
        false,
        true,
        Map.of(
            Permissions.ALL, getAllSupportedPerms(ResourceType.DECISION_REQUIREMENTS_DEFINITION),
            Permissions.READ, getAllSupportedPerms(ResourceType.DECISION_REQUIREMENTS_DEFINITION)), // Only READ exists
        null
    ));

    REGISTRY.put(Resources.PROCESS_DEFINITION, new AuthorizationMappingEntry(ResourceType.PROCESS_DEFINITION,
        false,
        true,
        Map.of(
            ProcessDefinitionPermissions.ALL, getAllSupportedPerms(ResourceType.PROCESS_DEFINITION),
            ProcessDefinitionPermissions.READ, Set.of(PermissionType.READ_PROCESS_DEFINITION),
            ProcessDefinitionPermissions.CREATE_INSTANCE, Set.of(PermissionType.CREATE_PROCESS_INSTANCE),
            ProcessDefinitionPermissions.READ_INSTANCE, Set.of(PermissionType.READ_PROCESS_INSTANCE),
            ProcessDefinitionPermissions.UPDATE_INSTANCE, Set.of(PermissionType.UPDATE_PROCESS_INSTANCE),
            ProcessDefinitionPermissions.DELETE_INSTANCE, Set.of(PermissionType.DELETE_PROCESS_INSTANCE),
            ProcessDefinitionPermissions.READ_TASK, Set.of(PermissionType.READ_USER_TASK),
            ProcessDefinitionPermissions.UPDATE_TASK, Set.of(PermissionType.UPDATE_USER_TASK)
        ),
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
