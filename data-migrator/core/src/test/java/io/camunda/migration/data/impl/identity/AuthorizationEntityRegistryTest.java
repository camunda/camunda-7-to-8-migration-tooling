/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.Set;
import org.camunda.bpm.engine.authorization.BatchPermissions;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.ProcessDefinitionPermissions;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.authorization.SystemPermissions;
import org.junit.jupiter.api.Test;

class AuthorizationEntityRegistryTest {

  @Test
  void shouldContainExactlyExpectedResources() {
    // given
    Set<Resource> expectedResources = Set.of(
        Resources.APPLICATION,
        Resources.AUTHORIZATION,
        Resources.GROUP,
        Resources.GROUP_MEMBERSHIP,
        Resources.SYSTEM,
        Resources.BATCH,
        Resources.TENANT,
        Resources.TENANT_MEMBERSHIP,
        Resources.USER,
        Resources.DECISION_DEFINITION,
        Resources.DECISION_REQUIREMENTS_DEFINITION,
        Resources.PROCESS_DEFINITION,
        Resources.DEPLOYMENT);

    // when
    Set<Resource> actualResources = AuthorizationEntityRegistry.REGISTRY.keySet();

    // then
    assertThat(actualResources).containsExactlyInAnyOrderElementsOf(expectedResources);
  }

  @Test
  void shouldHaveExpectedApplicationMapping() {
    // when
    var mapping = AuthorizationEntityRegistry.findMappingForResourceType(Resources.APPLICATION);

    // then
    assertThat(mapping.isPresent()).isTrue();
    assertThat(mapping.get().c8ResourceType()).isEqualTo(ResourceType.COMPONENT);
    assertThat(mapping.get().supportsExplicitId()).isTrue();
    assertThat(mapping.get().needsIdMapping()).isTrue();

    assertThat(mapping.get().getMappedPermissions(Permissions.ACCESS)).containsExactlyInAnyOrder(PermissionType.ACCESS);
    assertThat(mapping.get().getMappedPermissions(Permissions.ALL)).containsExactlyInAnyOrder(getAllSupportedPerms(ResourceType.COMPONENT));
  }

  @Test
  void shouldHaveExpectedAuthorizationMapping() {
    // when
    var mapping = AuthorizationEntityRegistry.findMappingForResourceType(Resources.AUTHORIZATION);

    // then
    assertThat(mapping.isPresent()).isTrue();
    assertThat(mapping.get().c8ResourceType()).isEqualTo(ResourceType.AUTHORIZATION);
    assertThat(mapping.get().supportsExplicitId()).isFalse();
    assertThat(mapping.get().needsIdMapping()).isFalse();

    assertThat(mapping.get().getMappedPermissions(Permissions.READ)).containsExactlyInAnyOrder(PermissionType.READ);
    assertThat(mapping.get().getMappedPermissions(Permissions.UPDATE)).containsExactlyInAnyOrder(PermissionType.UPDATE);
    assertThat(mapping.get().getMappedPermissions(Permissions.CREATE)).containsExactlyInAnyOrder(PermissionType.CREATE);
    assertThat(mapping.get().getMappedPermissions(Permissions.DELETE)).containsExactlyInAnyOrder(PermissionType.DELETE);
    assertThat(mapping.get().getMappedPermissions(Permissions.ALL)).containsExactlyInAnyOrder(getAllSupportedPerms(ResourceType.AUTHORIZATION));
  }

  @Test
  void shouldHaveExpectedGroupMapping() {
    // when
    var mapping = AuthorizationEntityRegistry.findMappingForResourceType(Resources.GROUP);

    // then
    assertThat(mapping.isPresent()).isTrue();
    assertThat(mapping.get().c8ResourceType()).isEqualTo(ResourceType.GROUP);
    assertThat(mapping.get().supportsExplicitId()).isTrue();
    assertThat(mapping.get().needsIdMapping()).isFalse();

    assertThat(mapping.get().getMappedPermissions(Permissions.READ)).containsExactlyInAnyOrder(PermissionType.READ);
    assertThat(mapping.get().getMappedPermissions(Permissions.UPDATE)).containsExactlyInAnyOrder(PermissionType.UPDATE);
    assertThat(mapping.get().getMappedPermissions(Permissions.CREATE)).containsExactlyInAnyOrder(PermissionType.CREATE);
    assertThat(mapping.get().getMappedPermissions(Permissions.DELETE)).containsExactlyInAnyOrder(PermissionType.DELETE);
    assertThat(mapping.get().getMappedPermissions(Permissions.ALL)).containsExactlyInAnyOrder(getAllSupportedPerms(ResourceType.GROUP));
  }

  @Test
  void shouldHaveExpectedGroupMembershipMapping() {
    // when
    var mapping = AuthorizationEntityRegistry.findMappingForResourceType(Resources.GROUP_MEMBERSHIP);

    // then
    assertThat(mapping.isPresent()).isTrue();
    assertThat(mapping.get().c8ResourceType()).isEqualTo(ResourceType.GROUP);
    assertThat(mapping.get().supportsExplicitId()).isTrue();
    assertThat(mapping.get().needsIdMapping()).isFalse();

    assertThat(mapping.get().getMappedPermissions(Permissions.ALL)).containsExactlyInAnyOrder(PermissionType.UPDATE);
  }

  @Test
  void shouldHaveExpectedSystemMapping() {
    // when
    var mapping = AuthorizationEntityRegistry.findMappingForResourceType(Resources.SYSTEM);

    // then
    assertThat(mapping.isPresent()).isTrue();
    assertThat(mapping.get().c8ResourceType()).isEqualTo(ResourceType.SYSTEM);
    assertThat(mapping.get().supportsExplicitId()).isFalse();
    assertThat(mapping.get().needsIdMapping()).isFalse();

    assertThat(mapping.get().getMappedPermissions(SystemPermissions.READ)).containsExactlyInAnyOrder(PermissionType.READ, PermissionType.READ_USAGE_METRIC);
    assertThat(mapping.get().getMappedPermissions(SystemPermissions.ALL)).containsExactlyInAnyOrder(getAllSupportedPerms(ResourceType.SYSTEM));
  }

  @Test
  void shouldHaveExpectedBatchMapping() {
    // when
    var mapping = AuthorizationEntityRegistry.findMappingForResourceType(Resources.BATCH);

    // then
    assertThat(mapping.isPresent()).isTrue();
    assertThat(mapping.get().c8ResourceType()).isEqualTo(ResourceType.BATCH);
    assertThat(mapping.get().supportsExplicitId()).isFalse();
    assertThat(mapping.get().needsIdMapping()).isFalse();

    assertThat(mapping.get().getMappedPermissions(BatchPermissions.READ)).containsExactlyInAnyOrder(PermissionType.READ);
    assertThat(mapping.get().getMappedPermissions(BatchPermissions.UPDATE)).containsExactlyInAnyOrder(PermissionType.UPDATE);
    assertThat(mapping.get().getMappedPermissions(BatchPermissions.CREATE)).containsExactlyInAnyOrder(PermissionType.CREATE);

    assertThat(mapping.get().getMappedPermissions(BatchPermissions.CREATE_BATCH_MIGRATE_PROCESS_INSTANCES)).containsExactlyInAnyOrder(PermissionType.CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE);
    assertThat(mapping.get().getMappedPermissions(BatchPermissions.CREATE_BATCH_MODIFY_PROCESS_INSTANCES)).containsExactlyInAnyOrder(PermissionType.CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE);
    assertThat(mapping.get().getMappedPermissions(BatchPermissions.CREATE_BATCH_DELETE_RUNNING_PROCESS_INSTANCES)).containsExactlyInAnyOrder(PermissionType.CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE, PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE);
    assertThat(mapping.get().getMappedPermissions(BatchPermissions.CREATE_BATCH_DELETE_FINISHED_PROCESS_INSTANCES)).containsExactlyInAnyOrder(PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE);
    assertThat(mapping.get().getMappedPermissions(BatchPermissions.CREATE_BATCH_DELETE_DECISION_INSTANCES)).containsExactlyInAnyOrder(PermissionType.CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE);

    assertThat(mapping.get().getMappedPermissions(BatchPermissions.ALL)).containsExactlyInAnyOrder(getAllSupportedPerms(ResourceType.BATCH));
  }

  @Test
  void shouldHaveExpectedTenantMapping() {
    // when
    var mapping = AuthorizationEntityRegistry.findMappingForResourceType(Resources.TENANT);

    // then
    assertThat(mapping.isPresent()).isTrue();
    assertThat(mapping.get().c8ResourceType()).isEqualTo(ResourceType.TENANT);
    assertThat(mapping.get().supportsExplicitId()).isTrue();
    assertThat(mapping.get().needsIdMapping()).isFalse();

    assertThat(mapping.get().getMappedPermissions(Permissions.READ)).containsExactlyInAnyOrder(PermissionType.READ);
    assertThat(mapping.get().getMappedPermissions(Permissions.UPDATE)).containsExactlyInAnyOrder(PermissionType.UPDATE);
    assertThat(mapping.get().getMappedPermissions(Permissions.CREATE)).containsExactlyInAnyOrder(PermissionType.CREATE);
    assertThat(mapping.get().getMappedPermissions(Permissions.DELETE)).containsExactlyInAnyOrder(PermissionType.DELETE);
    assertThat(mapping.get().getMappedPermissions(Permissions.ALL)).containsExactlyInAnyOrder(getAllSupportedPerms(ResourceType.TENANT));
  }

  @Test
  void shouldHaveExpectedTenantMembershipMapping() {
    // when
    var mapping = AuthorizationEntityRegistry.findMappingForResourceType(Resources.TENANT_MEMBERSHIP);

    // then
    assertThat(mapping.isPresent()).isTrue();
    assertThat(mapping.get().c8ResourceType()).isEqualTo(ResourceType.TENANT);
    assertThat(mapping.get().supportsExplicitId()).isTrue();
    assertThat(mapping.get().needsIdMapping()).isFalse();

    assertThat(mapping.get().getMappedPermissions(Permissions.ALL)).containsExactlyInAnyOrder(PermissionType.UPDATE);
  }

  @Test
  void shouldHaveExpectedUserMapping() {
    // when
    var mapping = AuthorizationEntityRegistry.findMappingForResourceType(Resources.USER);

    // then
    assertThat(mapping.isPresent()).isTrue();
    assertThat(mapping.get().c8ResourceType()).isEqualTo(ResourceType.USER);
    assertThat(mapping.get().supportsExplicitId()).isTrue();
    assertThat(mapping.get().needsIdMapping()).isFalse();

    assertThat(mapping.get().getMappedPermissions(Permissions.READ)).containsExactlyInAnyOrder(PermissionType.READ);
    assertThat(mapping.get().getMappedPermissions(Permissions.UPDATE)).containsExactlyInAnyOrder(PermissionType.UPDATE);
    assertThat(mapping.get().getMappedPermissions(Permissions.CREATE)).containsExactlyInAnyOrder(PermissionType.CREATE);
    assertThat(mapping.get().getMappedPermissions(Permissions.DELETE)).containsExactlyInAnyOrder(PermissionType.DELETE);
    assertThat(mapping.get().getMappedPermissions(Permissions.ALL)).containsExactlyInAnyOrder(getAllSupportedPerms(ResourceType.USER));
  }

  @Test
  void shouldHaveExpectedDecisionDefinitionMapping() {
    // when
    var mapping = AuthorizationEntityRegistry.findMappingForResourceType(Resources.DECISION_DEFINITION);

    // then
    assertThat(mapping.isPresent()).isTrue();
    assertThat(mapping.get().c8ResourceType()).isEqualTo(ResourceType.DECISION_DEFINITION);
    assertThat(mapping.get().supportsExplicitId()).isTrue();
    assertThat(mapping.get().needsIdMapping()).isFalse();

    assertThat(mapping.get().getMappedPermissions(Permissions.READ)).containsExactlyInAnyOrder(PermissionType.READ_DECISION_DEFINITION, PermissionType.READ_DECISION_INSTANCE);
    assertThat(mapping.get().getMappedPermissions(Permissions.CREATE_INSTANCE)).containsExactlyInAnyOrder(PermissionType.CREATE_DECISION_INSTANCE);
    assertThat(mapping.get().getMappedPermissions(Permissions.ALL)).containsExactlyInAnyOrder(getAllSupportedPerms(ResourceType.DECISION_DEFINITION));
  }

  @Test
  void shouldHaveExpectedDecisionRequirementsDefinitionMapping() {
    // when
    var mapping = AuthorizationEntityRegistry.findMappingForResourceType(Resources.DECISION_REQUIREMENTS_DEFINITION);

    // then
    assertThat(mapping.isPresent()).isTrue();
    assertThat(mapping.get().c8ResourceType()).isEqualTo(ResourceType.DECISION_REQUIREMENTS_DEFINITION);
    assertThat(mapping.get().supportsExplicitId()).isTrue();
    assertThat(mapping.get().needsIdMapping()).isFalse();

    assertThat(mapping.get().getMappedPermissions(Permissions.ALL)).containsExactlyInAnyOrder(getAllSupportedPerms(ResourceType.DECISION_REQUIREMENTS_DEFINITION));
  }

  @Test
  void shouldHaveExpectedProcessDefinitionMapping() {
    // when
    var mapping = AuthorizationEntityRegistry.findMappingForResourceType(Resources.PROCESS_DEFINITION);

    // then
    assertThat(mapping.isPresent()).isTrue();
    assertThat(mapping.get().c8ResourceType()).isEqualTo(ResourceType.PROCESS_DEFINITION);
    assertThat(mapping.get().supportsExplicitId()).isTrue();
    assertThat(mapping.get().needsIdMapping()).isFalse();

    assertThat(mapping.get().getMappedPermissions(ProcessDefinitionPermissions.READ)).containsExactlyInAnyOrder(PermissionType.READ_PROCESS_DEFINITION);
    assertThat(mapping.get().getMappedPermissions(ProcessDefinitionPermissions.CREATE_INSTANCE)).containsExactlyInAnyOrder(PermissionType.CREATE_PROCESS_INSTANCE);
    assertThat(mapping.get().getMappedPermissions(ProcessDefinitionPermissions.READ_INSTANCE)).containsExactlyInAnyOrder(PermissionType.READ_PROCESS_INSTANCE);
    assertThat(mapping.get().getMappedPermissions(ProcessDefinitionPermissions.UPDATE_INSTANCE)).containsExactlyInAnyOrder(PermissionType.UPDATE_PROCESS_INSTANCE);
    assertThat(mapping.get().getMappedPermissions(ProcessDefinitionPermissions.DELETE_INSTANCE)).containsExactlyInAnyOrder(PermissionType.DELETE_PROCESS_INSTANCE);
    assertThat(mapping.get().getMappedPermissions(ProcessDefinitionPermissions.READ_TASK)).containsExactlyInAnyOrder(PermissionType.READ_USER_TASK);
    assertThat(mapping.get().getMappedPermissions(ProcessDefinitionPermissions.UPDATE_TASK)).containsExactlyInAnyOrder(PermissionType.UPDATE_USER_TASK);

    assertThat(mapping.get().getMappedPermissions(ProcessDefinitionPermissions.ALL)).containsExactlyInAnyOrder(getAllSupportedPerms(ResourceType.PROCESS_DEFINITION));
  }

  @Test
  void shouldHaveExpectedDeploymentMapping() {
    // when
    var mapping = AuthorizationEntityRegistry.findMappingForResourceType(Resources.DEPLOYMENT);

    // then
    assertThat(mapping.isPresent()).isTrue();
    assertThat(mapping.get().c8ResourceType()).isEqualTo(ResourceType.RESOURCE);
    assertThat(mapping.get().supportsExplicitId()).isTrue();
    assertThat(mapping.get().needsIdMapping()).isTrue();

    assertThat(mapping.get().getMappedPermissions(Permissions.READ)).containsExactlyInAnyOrder(PermissionType.READ);
    assertThat(mapping.get().getMappedPermissions(Permissions.CREATE)).containsExactlyInAnyOrder(PermissionType.CREATE);
    assertThat(mapping.get().getMappedPermissions(Permissions.DELETE)).containsExactlyInAnyOrder(
        PermissionType.DELETE_RESOURCE,
        PermissionType.DELETE_PROCESS,
        PermissionType.DELETE_DRD,
        PermissionType.DELETE_FORM);

    assertThat(mapping.get().getMappedPermissions(Permissions.ALL)).containsExactlyInAnyOrder(getAllSupportedPerms(ResourceType.RESOURCE));
  }

  @Test
  void shouldReturnEmptyOptionalForUnsupportedResource() {
    assertThat(AuthorizationEntityRegistry.findMappingForResourceType(Resources.FILTER)).isEmpty();
  }

  protected static PermissionType[] getAllSupportedPerms(ResourceType resourceType) {
    return AuthorizationResourceType
        .valueOf(resourceType.name())
        .getSupportedPermissionTypes()
        .stream()
        .map(permissionType -> PermissionType.valueOf(permissionType.name()))
        .toArray(PermissionType[]::new);
  }
}
