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
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import io.camunda.client.api.search.request.TenantsSearchRequest;
import io.camunda.client.api.search.response.Tenant;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
public class RetryTenantMigrationTest extends IdentityAbstractTest {

  @Autowired
  protected IdentityTestHelper identityTestHelper;

  @Test
  public void shouldMigrateSkippedTenantsOnRetry() {
    // given 3 skipped tenants (missing name)
    var t1 = identityTestHelper.createTenantInC7("tenantId1", "");
    var t2 = identityTestHelper.createTenantInC7("tenantId2", "");
    var t3 = identityTestHelper.createTenantInC7("tenantId3", "");
    identityMigrator.start();

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
    awaitTenantsCount(3);
    assertThatTenantsContain(List.of(t1, t2, t3), camundaClient.newTenantsSearchRequest().execute().items());
  }

  @Test
  public void shouldOnlyMigrateSkippedTenantsOnRetry() {
    // given one skipped and two migrated tenants
    var t1 = identityTestHelper.createTenantInC7("tenantId1", "tenantName1");
    var t2 = identityTestHelper.createTenantInC7("tenantId2", "");
    var t3 = identityTestHelper.createTenantInC7("tenantId3", "tenantName3");
    identityMigrator.start();

    // when issue is fixed
    t2.setName("Tenant2");
    identityService.saveTenant(t2);

    // and migration is retried
    identityMigrator.setMode(RETRY_SKIPPED);
    identityMigrator.start();

    // then only the previously skipped tenant is additionally migrated
    awaitTenantsCount(3);
    assertThatTenantsContain(List.of(t1, t2, t3), camundaClient.newTenantsSearchRequest().execute().items());
  }

  @Test
  public void shouldNotReattemptSkippedOnRerun() {
    // given one skipped, one migrated tenant, and one non migrated tenant
    var t1 = identityTestHelper.createTenantInC7("tenantId1", "tenantName1");
    var t2 = identityTestHelper.createTenantInC7("tenantId2", "");
    identityMigrator.start();
    var t3 = identityTestHelper.createTenantInC7("tenantId3", "tenantName3");

    // when issue is fixed but migration is rerun without retry
    t2.setName("Tenant2");
    identityService.saveTenant(t2);
    identityMigrator.start(); // default mode is MIGRATE

    // then we the non migrated tenant is migrated but the previously skipped one is not
    awaitTenantsCount(2);
    assertThatTenantsContain(List.of(t1, t3), camundaClient.newTenantsSearchRequest().execute().items());
    assertThat(camundaClient.newTenantsSearchRequest().filter(f -> f.tenantId(t2.getId())).execute().items()).hasSize(0);
  }

  @Test
  public void shouldListSkippedTenants(CapturedOutput output) {
    // given skipped tenants (invalid IDs allowed in C7)
    processEngineConfiguration.setTenantResourceWhitelistPattern(".+");
    List<String> tenantIds = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      tenantIds.add(identityTestHelper.createTenantInC7("tenantId-" + i + "-!~^", "tenantName" + i).getId());
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

  private static void assertThatTenantsContain(List<org.camunda.bpm.engine.identity.Tenant> expectedTenants, List<Tenant> tenants) {
    assertThat(tenants)
        .extracting(Tenant::getTenantId, Tenant::getName)
        .containsAll(expectedTenants.stream().map(tenant -> tuple(tenant.getId(), tenant.getName())).toList());
  }

  private void awaitTenantsCount(int expected) {
    TenantsSearchRequest request = camundaClient.newTenantsSearchRequest();
    await().timeout(5, TimeUnit.SECONDS).until(() -> request.execute().items().size() == expected + 1) ; // +1 for default tenant
  }
}
