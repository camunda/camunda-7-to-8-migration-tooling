/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import static io.camunda.migration.data.MigratorMode.LIST_SKIPPED;
import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.response.Tenant;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.persistence.IdKeyDbModel;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.qa.util.WhiteBox;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
public class RetryTenantMigrationTest extends IdentityMigrationAbstractTest {

  @Autowired
  protected MigratorProperties migratorProperties;

  @Autowired
  protected IdKeyMapper idKeyMapper;

  @AfterEach
  void cleanUpSaveSkipReason() {
    migratorProperties.setSaveSkipReason(false);
  }

  @Test
  @WhiteBox
  public void shouldMigrateSkippedTenantsOnRetry() {
    migratorProperties.setSaveSkipReason(true);
    // given 3 skipped tenants (missing name)
    var t1 = testHelper.createTenantInC7("tenantId1", "");
    var t2 = testHelper.createTenantInC7("tenantId2", "");
    var t3 = testHelper.createTenantInC7("tenantId3", "");

    // when
    identityMigrator.start();

    // then
    assertThat(idKeyMapper.findSkippedByTypeWithOffset(IdKeyMapper.TYPE.TENANT, 0, 10))
        .hasSize(3)
        .extracting(IdKeyDbModel::getSkipReason)
        .containsOnly("No name provided.");

    // when issue is fixed
    t1.setName("Tenant1");
    t2.setName("Tenant2");
    t3.setName("Tenant3");
    identityService.saveTenant(t1);
    identityService.saveTenant(t2);
    identityService.saveTenant(t3);

    // and migration is retried
    identityMigrator.setMode(RETRY_SKIPPED);
    identityMigrator.start();

    // then all three tenants are migrated successfully
    List<Tenant> tenants = testHelper.awaitTenantsCountAndGet(3);
    testHelper.assertThatTenantsContain(List.of(t1, t2, t3), tenants);
    assertThat(idKeyMapper.findMigratedByType(IdKeyMapper.TYPE.TENANT, 0, 10))
        .hasSize(3)
        .extracting(IdKeyDbModel::getSkipReason)
        .containsOnlyNulls();
  }

  @Test
  @WhiteBox
  public void shouldOnlyMigrateSkippedTenantsOnRetry() {
    migratorProperties.setSaveSkipReason(true);
    // given one skipped and two migrated tenants
    var t1 = testHelper.createTenantInC7("tenantId1", "tenantName1");
    var t2 = testHelper.createTenantInC7("tenantId2", "");
    var t3 = testHelper.createTenantInC7("tenantId3", "tenantName3");

    // when
    identityMigrator.start();

    // then
    assertThat(idKeyMapper.findSkippedByTypeWithOffset(IdKeyMapper.TYPE.TENANT, 0, 10))
        .singleElement()
        .extracting(IdKeyDbModel::getSkipReason)
        .isEqualTo("No name provided.");

    // when issue is fixed
    t2.setName("Tenant2");
    identityService.saveTenant(t2);

    // and migration is retried
    identityMigrator.setMode(RETRY_SKIPPED);
    identityMigrator.start();

    // then only the previously skipped tenant is additionally migrated
    List<Tenant> tenants = testHelper.awaitTenantsCountAndGet(3);
    testHelper.assertThatTenantsContain(List.of(t1, t2, t3), tenants);
  }

  @Test
  @WhiteBox
  public void shouldNotReattemptSkippedOnRerun() {
    migratorProperties.setSaveSkipReason(true);
    // given one skipped, one migrated tenant, and one non migrated tenant
    var t1 = testHelper.createTenantInC7("tenantId1", "tenantName1");
    var t2 = testHelper.createTenantInC7("tenantId2", "");

    // when
    identityMigrator.start();

    // then
    assertThat(idKeyMapper.findSkippedByTypeWithOffset(IdKeyMapper.TYPE.TENANT, 0, 10))
        .singleElement()
        .extracting(IdKeyDbModel::getSkipReason)
        .isEqualTo("No name provided.");

    var t3 = testHelper.createTenantInC7("tenantId3", "tenantName3");

    // when issue is fixed but migration is rerun without retry
    t2.setName("Tenant2");
    identityService.saveTenant(t2);
    identityMigrator.start(); // default mode is MIGRATE

    // then we the non migrated tenant is migrated but the previously skipped one is not
    List<Tenant> tenants = testHelper.awaitTenantsCountAndGet(2);
    testHelper.assertThatTenantsContain(List.of(t1, t3), tenants);
    assertThat(camundaClient.newTenantsSearchRequest().filter(f -> f.tenantId(t2.getId())).execute().items()).hasSize(0);
  }

  @Test
  public void shouldListSkippedTenants(CapturedOutput output) {
    // given skipped tenants (invalid IDs allowed in C7)
    processEngineConfiguration.setTenantResourceWhitelistPattern(".+");
    List<String> tenantIds = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      tenantIds.add(testHelper.createTenantInC7("tenantId-" + i + "-!~^", "tenantName" + i).getId());
    }
    identityMigrator.start();

    // when running migration with list skipped mode
    identityMigrator.setMode(LIST_SKIPPED);
    identityMigrator.start();

    // then all skipped tenants were listed
    String expectedHeader = "Previously skipped \\[" + IdKeyMapper.TYPE.TENANT.getDisplayName() + "s\\]:";
    String regex = expectedHeader + "\\R((?:.+\\R){9}.+)";
    assertThat(output.getOut()).containsPattern(regex);
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(output.getOut());
    final String capturedIds = matcher.find() ? matcher.group(1) : "";
    tenantIds.forEach(id -> assertThat(capturedIds).contains(id));
  }

  @Test
  @WhiteBox
  public void shouldMigrateMembershipsForSkippedTenantsOnRetry() {
    migratorProperties.setSaveSkipReason(true);
    // given 1 skipped tenant (missing name)
    var t1 = testHelper.createTenantInC7("tenantId1", "");

    // and memberships for that tenant
    testHelper.createUserInC7("userId1", "firstName1", "lastName1");
    testHelper.createUserInC7("userId2", "firstName2", "lastName2");
    testHelper.createUserInC8("userId1", "firstName1", "lastName1");
    testHelper.createUserInC8("userId2", "firstName2", "lastName2");
    identityService.createTenantUserMembership(t1.getId(), "userId1");
    identityService.createTenantUserMembership(t1.getId(), "userId2");

    // when
    identityMigrator.start();

    // then
    assertThat(idKeyMapper.findSkippedByTypeWithOffset(IdKeyMapper.TYPE.TENANT, 0, 10))
        .singleElement()
        .extracting(IdKeyDbModel::getSkipReason)
        .isEqualTo("No name provided.");

    // verify membership migration was skipped
    testHelper.assertNoMembershipsForTenant(t1.getId());

    // when issue with tenant is fixed
    t1.setName("Tenant1");
    identityService.saveTenant(t1);

    // and migration is retried
    identityMigrator.setMode(RETRY_SKIPPED);
    identityMigrator.start();

    // then the tenant is migrated successfully
    List<Tenant> tenants = testHelper.awaitTenantsCountAndGet(1);
    testHelper.assertThatTenantsContain(List.of(t1), tenants);

    // and also the memberships
    testHelper.assertThatUsersForTenantContainExactly(t1.getId(), "userId1", "userId2");
  }
}
