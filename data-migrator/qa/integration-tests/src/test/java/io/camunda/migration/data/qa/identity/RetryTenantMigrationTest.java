package io.camunda.migration.data.qa.identity;

import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import io.camunda.client.api.search.request.TenantsSearchRequest;
import io.camunda.client.api.search.response.Tenant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RetryTenantMigrationTest extends IdentityAbstractTest {

  @Autowired
  protected IdentityTestHelper identityTestHelper;

  @Test
  public void shouldMigrateSkippedTenantsOnRetry() {
    // given 3 skipped tenants (missing name)
    var t1 = identityTestHelper.createTenantInC7("tenantId1", "");
    var t2 = identityTestHelper.createTenantInC7("tenantId2", "");
    var t3 = identityTestHelper.createTenantInC7("tenantId3", "");
    identityMigrator.migrate();

    // when issue is fixed (by setting valid names)
    t1.setName("Tenant1");
    t2.setName("Tenant2");
    t3.setName("Tenant3");
    identityService.saveTenant(t1);
    identityService.saveTenant(t2);
    identityService.saveTenant(t3);

    // and migration is retried
    identityMigrator.setMode(RETRY_SKIPPED);
    identityMigrator.migrate();

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
    identityMigrator.migrate();

    // when issue is fixed and migration is retried
    t2.setName("Tenant2");
    identityService.saveTenant(t2);

    // and migration is retried
    identityMigrator.setMode(RETRY_SKIPPED);
    identityMigrator.migrate();

    // then only the previously skipped tenant is additionally migrated
    awaitTenantsCount(3);
    assertThatTenantsContain(List.of(t1, t2, t3), camundaClient.newTenantsSearchRequest().execute().items());
  }

  @Test
  public void shouldNotReattemptSkippedOnRerun() {
    // given one skipped, one migrated tenant, and one non migrated tenant
    var t1 = identityTestHelper.createTenantInC7("tenantId1", "tenantName1");
    var t2 = identityTestHelper.createTenantInC7("tenantId2", "");
    identityMigrator.migrate();
    var t3 = identityTestHelper.createTenantInC7("tenantId3", "tenantName3");

    // when issue is fixed but migration is rerun without retry
    t2.setName("Tenant2");
    identityService.saveTenant(t2);
    identityMigrator.migrate(); // default mode is MIGRATE

    // then we the non migrated tenant is migrated but the previously skipped one is not
    awaitTenantsCount(2);
    assertThatTenantsContain(List.of(t1, t3), camundaClient.newTenantsSearchRequest().execute().items());
    assertThat(camundaClient.newTenantsSearchRequest().filter(f -> f.tenantId(t2.getId())).execute().items()).hasSize(0);
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

