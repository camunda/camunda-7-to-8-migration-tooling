/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import io.camunda.client.api.search.request.TenantsSearchRequest;
import io.camunda.migration.data.IdentityMigrator;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.junit.jupiter.api.Test;

import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.SKIPPED_TENANT;
import static io.camunda.migration.data.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import io.camunda.client.api.search.response.Tenant;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

public class TenantMigrationTest extends IdentityAbstractTest {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(IdentityMigrator.class);

  @Autowired
  protected ProcessEngineConfiguration processEngineConfiguration;

  @Test
  public void shouldMigrateTenants() {
    // given 3 tenants in c7
    var tenant1 = createTenant("tenantId1", "tenantName1");
    var tenant2 = createTenant("tenantId2", "tenantName2");
    var tenant3 = createTenant("tenantId3", "tenantName3");
    var expectedTenants = List.of(tenant1, tenant2, tenant3);

    // when migrating
    identityMigrator.migrate();

    // then c8 has default tenant + the 3 migrated ones
    TenantsSearchRequest request = camundaClient.newTenantsSearchRequest();
    await().timeout(5, TimeUnit.SECONDS).until(() -> request.execute().items().size() == 4);
    var tenants = request.execute().items();
    assertThatTenantsContain(expectedTenants, tenants);
  }

  @Test
  public void shouldSkipTenants() {
    // given 3 tenants in c7
    processEngineConfiguration.setTenantResourceWhitelistPattern(".+"); // allow to create tenant with any ID
    var tenant1 = createTenant("tenantId1", "tenantName1");
    var tenant2 = createTenant("tenantId2-!~^", "tenantName2");
    var tenant3 = createTenant("tenantId3", "tenantName3");
    var expectedTenants = List.of(tenant1, tenant3);

    // when migrating
    identityMigrator.migrate();

    // then c8 has default, tenant1 and tenant3
    TenantsSearchRequest request = camundaClient.newTenantsSearchRequest();
    await().timeout(5, TimeUnit.SECONDS).until(() -> request.execute().items().size() == 3);
    var tenants = request.execute().items();
    assertThatTenantsContain(expectedTenants, tenants);

    // and 1 tenant was marked as skipped
    verifySkippedViaLogs(tenant2.getId());
  }

  @Test
  public void shouldMigrateOnlyNonPreviouslyMigratedTenants() {
    // given 3 tenants in c7 but one was already marked as migrated
    var tenant1 = createTenant("tenantId1", "tenantName1");
    identityMigrator.migrate();
    camundaClient.newDeleteTenantCommand(tenant1.getId()).execute(); // To be able to assert that it doesn't get migrated again
    var tenant2 = createTenant("tenantId2", "tenantName2");
    var tenant3 = createTenant("tenantId3", "tenantName3");
    var expectedTenants = List.of(tenant2, tenant3);

    // when migrating
    identityMigrator.migrate();

    // then c8 has default, tenant2 and tenant3
    TenantsSearchRequest request = camundaClient.newTenantsSearchRequest();
    await().timeout(5, TimeUnit.SECONDS).until(() -> request.execute().items().size() == 3);
    var tenants = request.execute().items();
    assertThatTenantsContain(expectedTenants, tenants);

    // but not tenant1
    assertThat(camundaClient.newTenantsSearchRequest().filter(f -> f.tenantId(tenant1.getId())).execute().items()).hasSize(0);
  }

  /**
   * Compares a list of {@link org.camunda.bpm.engine.identity.Tenant}
   * and a list of {@link io.camunda.client.api.search.response.Tenant}
   * by checking that all elements are contained with matching tenantId and name
   */
  private static void assertThatTenantsContain(List<org.camunda.bpm.engine.identity.Tenant> expectedTenants, List<Tenant> tenants) {
    assertThat(tenants)
        .extracting(Tenant::getTenantId, Tenant::getName)
        .containsAll(expectedTenants.stream().map(tenant -> tuple(tenant.getId(), tenant.getName())).toList());
  }

  private org.camunda.bpm.engine.identity.Tenant createTenant(String id, String name) {
    var tenant = identityService.newTenant(id);
    tenant.setName(name);
    identityService.saveTenant(tenant);
    return tenant;
  }

  protected void verifySkippedViaLogs(String tenantId) {
    logs.assertContains(formatMessage(SKIPPED_TENANT, tenantId));
  }
}
