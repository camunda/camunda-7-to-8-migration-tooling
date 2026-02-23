/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.response.Tenant;
import io.camunda.migration.data.IdentityMigrator;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.List;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.junit.jupiter.api.Test;

import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_CREATE_TENANT_USER_MEMBERSHIP;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.CANNOT_MIGRATE_TENANT_MEMBERSHIP;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

public class TenantMigrationTest extends IdentityMigrationAbstractTest {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(IdentityMigrator.class);

  @Autowired
  protected ProcessEngineConfiguration processEngineConfiguration;

  @Test
  public void shouldMigrateTenants() {
    // given 3 tenants in c7
    var tenant1 = testHelper.createTenantInC7("tenantId1", "tenantName1");
    var tenant2 = testHelper.createTenantInC7("tenantId2", "tenantName2");
    var tenant3 = testHelper.createTenantInC7("tenantId3", "tenantName3");
    var expectedTenants = List.of(tenant1, tenant2, tenant3);

    // when migrating
    identityMigrator.start();

    // then the 3 tenants exist in c8
    List<Tenant> tenants = testHelper.awaitTenantsCountAndGet(3);
    testHelper.assertThatTenantsContain(expectedTenants, tenants);
  }

  @Test
  public void shouldSkipTenants() {
    // given 3 tenants in c7
    processEngineConfiguration.setTenantResourceWhitelistPattern(".+"); // allow to create tenant with any ID
    var tenant1 = testHelper.createTenantInC7("tenantId1", "tenantName1");
    var tenant2 = testHelper.createTenantInC7("tenantId2-!~^", "tenantName2");
    var tenant3 = testHelper.createTenantInC7("tenantId3", "tenantName3");
    var expectedTenants = List.of(tenant1, tenant3);

    // when migrating
    identityMigrator.start();

    // then the 2 tenants exist in c8
    List<Tenant> tenants = testHelper.awaitTenantsCountAndGet(2);
    testHelper.assertThatTenantsContain(expectedTenants, tenants);

    // and 1 tenant was marked as skipped
    testHelper.verifyTenantSkippedViaLogs(tenant2.getId(), logs);
  }

  @Test
  public void shouldMigrateOnlyNonPreviouslyMigratedTenants() {
    // given 3 tenants in c7 but one was already marked as migrated
    var tenant1 = testHelper.createTenantInC7("tenantId1", "tenantName1");
    identityMigrator.start();
    camundaClient.newDeleteTenantCommand(tenant1.getId()).execute(); // To be able to assert that it doesn't get migrated again
    var tenant2 = testHelper.createTenantInC7("tenantId2", "tenantName2");
    var tenant3 = testHelper.createTenantInC7("tenantId3", "tenantName3");
    var expectedTenants = List.of(tenant2, tenant3);

    // when migrating
    identityMigrator.start();

    // then the 2 tenants exist in c8
    List<Tenant> tenants = testHelper.awaitTenantsCountAndGet(2);
    testHelper.assertThatTenantsContain(expectedTenants, tenants);

    // but not tenant1
    assertThat(camundaClient.newTenantsSearchRequest().filter(f -> f.tenantId(tenant1.getId())).execute().items()).hasSize(0);
  }

  @Test
  public void shouldMigrateTenantMemberships() {
    // given
    var tenant1 = testHelper.createTenantInC7("tenantId1", "tenantName1");
    var tenant2 = testHelper.createTenantInC7("tenantId2", "tenantName2");

    testHelper.createUserInC7("userId1", "firstName1", "lastName1");
    testHelper.createUserInC7("userId2", "firstName2", "lastName2");
    testHelper.createUserInC8("userId1", "firstName1", "lastName1");
    testHelper.createUserInC8("userId2", "firstName2", "lastName2");
    testHelper.createGroupInC7("groupId1", "groupName1");
    testHelper.createGroupInC7("groupId2", "groupName2");
    testHelper.createGroupInC8("groupId1", "groupName1");
    testHelper.createGroupInC8("groupId2", "groupName2");

    identityService.createTenantUserMembership(tenant1.getId(), "userId1");
    identityService.createTenantGroupMembership(tenant1.getId(), "groupId1");
    identityService.createTenantUserMembership(tenant2.getId(), "userId2");
    identityService.createTenantGroupMembership(tenant2.getId(), "groupId2");

    // when
    identityMigrator.start();

    // then the 2 tenants exist in c8
    List<Tenant> tenants = testHelper.awaitTenantsCountAndGet(2);
    testHelper.assertThatTenantsContain(List.of(tenant1, tenant2), tenants);

    // and tenant memberships are migrated
    testHelper.assertThatUsersForTenantContainExactly(tenant1.getId(), "userId1");
    testHelper.assertThatGroupsForTenantContainExactly(tenant1.getId(), "groupId1");
    testHelper.assertThatUsersForTenantContainExactly(tenant2.getId(), "userId2");
    testHelper.assertThatGroupsForTenantContainExactly(tenant2.getId(), "groupId2");
  }

  @Test
  public void shouldNotMigrateTenantMembershipsWhenUserDoesNotExist() {
    // given
    var tenant1 = testHelper.createTenantInC7("tenantId1", "tenantName1");

    testHelper.createUserInC7("userId0", "firstName0", "lastName0");
    testHelper.createUserInC7("userId1", "firstName1", "lastName1");
    testHelper.createUserInC7("userId2", "firstName2", "lastName2");

    testHelper.createUserInC8("userId1", "firstName1", "lastName1");
    testHelper.createUserInC8("userId2", "firstName2", "lastName2");

    identityService.createTenantUserMembership(tenant1.getId(), "userId0"); // cannot be migrated because user does not exist
    identityService.createTenantUserMembership(tenant1.getId(), "userId1");
    identityService.createTenantUserMembership(tenant1.getId(), "userId2");

    // when
    identityMigrator.start();

    // then the tenant exists in c8
    List<Tenant> tenants = testHelper.awaitTenantsCountAndGet(1);
    testHelper.assertThatTenantsContain(List.of(tenant1), tenants);

    // and 2 tenant memberships are migrated
    testHelper.assertThatUsersForTenantContainExactly(tenant1.getId(), "userId1", "userId2");

    // and 1 tenant membership could not be migrated
    String reason = format(FAILED_TO_CREATE_TENANT_USER_MEMBERSHIP, tenant1.getId(), "userId0");
    logs.assertContains(formatMessage(CANNOT_MIGRATE_TENANT_MEMBERSHIP, tenant1.getId(), OwnerType.USER.name(), "userId0", reason));
  }

}
