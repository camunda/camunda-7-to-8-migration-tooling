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
import java.util.Set;
import org.camunda.bpm.engine.authorization.BatchPermissions;
import org.camunda.bpm.engine.authorization.Permissions;
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
        Resources.USER);

    // when
    Set<Resource> actualResources = AuthorizationEntityRegistry.REGISTRY.keySet();

    // then
    assertThat(actualResources).containsExactlyInAnyOrderElementsOf(expectedResources);
  }

  @Test
  void shouldHaveExpectedApplicationMapping() {
    AuthorizationMappingEntry entry = AuthorizationEntityRegistry.getMappingForResourceType(Resources.APPLICATION);

    assertThat(entry.c8ResourceType()).isEqualTo(ResourceType.COMPONENT);
    assertThat(entry.supportsExplicitId()).isTrue();
    assertThat(entry.needsToAdaptId()).isTrue();

    assertThat(entry.getMappedPermissions(Permissions.ACCESS)).containsExactlyInAnyOrder(PermissionType.ACCESS);
    assertThat(entry.getMappedPermissions(Permissions.ALL)).contains(PermissionType.ACCESS);
  }

  @Test
  void shouldHaveExpectedAuthorizationMapping() {
    AuthorizationMappingEntry entry = AuthorizationEntityRegistry.getMappingForResourceType(Resources.AUTHORIZATION);

    assertThat(entry.c8ResourceType()).isEqualTo(ResourceType.AUTHORIZATION);
    assertThat(entry.supportsExplicitId()).isFalse();
    assertThat(entry.needsToAdaptId()).isFalse();

    assertThat(entry.getMappedPermissions(Permissions.READ)).containsExactlyInAnyOrder(PermissionType.READ);
    assertThat(entry.getMappedPermissions(Permissions.UPDATE)).containsExactlyInAnyOrder(PermissionType.UPDATE);
    assertThat(entry.getMappedPermissions(Permissions.CREATE)).containsExactlyInAnyOrder(PermissionType.CREATE);
    assertThat(entry.getMappedPermissions(Permissions.DELETE)).containsExactlyInAnyOrder(PermissionType.DELETE);
    assertThat(entry.getMappedPermissions(Permissions.ALL)).contains(
        PermissionType.READ,
        PermissionType.UPDATE,
        PermissionType.CREATE,
        PermissionType.DELETE);
  }

  @Test
  void shouldHaveExpectedGroupMapping() {
    AuthorizationMappingEntry entry = AuthorizationEntityRegistry.getMappingForResourceType(Resources.GROUP);

    assertThat(entry.c8ResourceType()).isEqualTo(ResourceType.GROUP);
    assertThat(entry.supportsExplicitId()).isTrue();
    assertThat(entry.needsToAdaptId()).isFalse();

    assertThat(entry.getMappedPermissions(Permissions.READ)).containsExactlyInAnyOrder(PermissionType.READ);
    assertThat(entry.getMappedPermissions(Permissions.UPDATE)).containsExactlyInAnyOrder(PermissionType.UPDATE);
    assertThat(entry.getMappedPermissions(Permissions.CREATE)).containsExactlyInAnyOrder(PermissionType.CREATE);
    assertThat(entry.getMappedPermissions(Permissions.DELETE)).containsExactlyInAnyOrder(PermissionType.DELETE);
    assertThat(entry.getMappedPermissions(Permissions.ALL)).contains(
        PermissionType.READ,
        PermissionType.UPDATE,
        PermissionType.CREATE,
        PermissionType.DELETE);
  }

  @Test
  void shouldHaveExpectedGroupMembershipMapping() {
    AuthorizationMappingEntry entry = AuthorizationEntityRegistry.getMappingForResourceType(Resources.GROUP_MEMBERSHIP);

    assertThat(entry.c8ResourceType()).isEqualTo(ResourceType.GROUP);
    assertThat(entry.supportsExplicitId()).isTrue();
    assertThat(entry.needsToAdaptId()).isFalse();

    assertThat(entry.getMappedPermissions(Permissions.ALL)).contains(PermissionType.UPDATE);
  }

  @Test
  void shouldHaveExpectedSystemMapping() {
    AuthorizationMappingEntry entry = AuthorizationEntityRegistry.getMappingForResourceType(Resources.SYSTEM);

    assertThat(entry.c8ResourceType()).isEqualTo(ResourceType.SYSTEM);
    assertThat(entry.supportsExplicitId()).isFalse();
    assertThat(entry.needsToAdaptId()).isFalse();

    assertThat(entry.getMappedPermissions(SystemPermissions.READ)).containsExactlyInAnyOrder(PermissionType.READ, PermissionType.READ_USAGE_METRIC);
    assertThat(entry.getMappedPermissions(SystemPermissions.ALL)).contains(
        PermissionType.READ,
        PermissionType.READ_USAGE_METRIC,
        PermissionType.UPDATE);
  }

  @Test
  void shouldHaveExpectedBatchMapping() {
    AuthorizationMappingEntry entry = AuthorizationEntityRegistry.getMappingForResourceType(Resources.BATCH);

    assertThat(entry.c8ResourceType()).isEqualTo(ResourceType.BATCH);
    assertThat(entry.supportsExplicitId()).isFalse();
    assertThat(entry.needsToAdaptId()).isFalse();

    assertThat(entry.getMappedPermissions(BatchPermissions.READ)).containsExactlyInAnyOrder(PermissionType.READ);
    assertThat(entry.getMappedPermissions(BatchPermissions.UPDATE)).containsExactlyInAnyOrder(PermissionType.UPDATE);
    assertThat(entry.getMappedPermissions(BatchPermissions.CREATE)).containsExactlyInAnyOrder(PermissionType.CREATE);

    assertThat(entry.getMappedPermissions(BatchPermissions.CREATE_BATCH_MIGRATE_PROCESS_INSTANCES)).containsExactlyInAnyOrder(PermissionType.CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE);
    assertThat(entry.getMappedPermissions(BatchPermissions.CREATE_BATCH_MODIFY_PROCESS_INSTANCES)).containsExactlyInAnyOrder(PermissionType.CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE);
    assertThat(entry.getMappedPermissions(BatchPermissions.CREATE_BATCH_DELETE_RUNNING_PROCESS_INSTANCES)).containsExactlyInAnyOrder(PermissionType.CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE, PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE);
    assertThat(entry.getMappedPermissions(BatchPermissions.CREATE_BATCH_DELETE_FINISHED_PROCESS_INSTANCES)).containsExactlyInAnyOrder(PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE);
    assertThat(entry.getMappedPermissions(BatchPermissions.CREATE_BATCH_DELETE_DECISION_INSTANCES)).containsExactlyInAnyOrder(PermissionType.CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE);

    assertThat(entry.getMappedPermissions(BatchPermissions.ALL)).contains(
        PermissionType.READ,
        PermissionType.UPDATE,
        PermissionType.CREATE,
        PermissionType.CREATE_BATCH_OPERATION_RESOLVE_INCIDENT,
        PermissionType.CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE,
        PermissionType.CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE,
        PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE,
        PermissionType.CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE,
        PermissionType.CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE,
        PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION,
        PermissionType.CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION);
  }

  @Test
  void shouldHaveExpectedTenantMapping() {
    AuthorizationMappingEntry entry = AuthorizationEntityRegistry.getMappingForResourceType(Resources.TENANT);

    assertThat(entry.c8ResourceType()).isEqualTo(ResourceType.TENANT);
    assertThat(entry.supportsExplicitId()).isTrue();
    assertThat(entry.needsToAdaptId()).isFalse();

    assertThat(entry.getMappedPermissions(Permissions.READ)).containsExactlyInAnyOrder(PermissionType.READ);
    assertThat(entry.getMappedPermissions(Permissions.UPDATE)).containsExactlyInAnyOrder(PermissionType.UPDATE);
    assertThat(entry.getMappedPermissions(Permissions.CREATE)).containsExactlyInAnyOrder(PermissionType.CREATE);
    assertThat(entry.getMappedPermissions(Permissions.DELETE)).containsExactlyInAnyOrder(PermissionType.DELETE);
    assertThat(entry.getMappedPermissions(Permissions.ALL)).contains(
        PermissionType.READ,
        PermissionType.UPDATE,
        PermissionType.CREATE,
        PermissionType.DELETE);
  }

  @Test
  void shouldHaveExpectedTenantMembershipMapping() {
    AuthorizationMappingEntry entry = AuthorizationEntityRegistry.getMappingForResourceType(Resources.TENANT_MEMBERSHIP);

    assertThat(entry.c8ResourceType()).isEqualTo(ResourceType.TENANT);
    assertThat(entry.supportsExplicitId()).isTrue();
    assertThat(entry.needsToAdaptId()).isFalse();

    assertThat(entry.getMappedPermissions(Permissions.ALL)).contains(PermissionType.UPDATE);
  }

  @Test
  void shouldHaveExpectedUserMapping() {
    AuthorizationMappingEntry entry = AuthorizationEntityRegistry.getMappingForResourceType(Resources.USER);

    assertThat(entry.c8ResourceType()).isEqualTo(ResourceType.USER);
    assertThat(entry.supportsExplicitId()).isTrue();
    assertThat(entry.needsToAdaptId()).isFalse();

    assertThat(entry.getMappedPermissions(Permissions.READ)).containsExactlyInAnyOrder(PermissionType.READ);
    assertThat(entry.getMappedPermissions(Permissions.UPDATE)).containsExactlyInAnyOrder(PermissionType.UPDATE);
    assertThat(entry.getMappedPermissions(Permissions.CREATE)).containsExactlyInAnyOrder(PermissionType.CREATE);
    assertThat(entry.getMappedPermissions(Permissions.DELETE)).containsExactlyInAnyOrder(PermissionType.DELETE);
    assertThat(entry.getMappedPermissions(Permissions.ALL)).contains(
        PermissionType.READ,
        PermissionType.UPDATE,
        PermissionType.CREATE,
        PermissionType.DELETE);
  }
}