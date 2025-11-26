package io.camunda.migrator.qa.identity;

import io.camunda.client.api.search.request.TenantsSearchRequest;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import io.camunda.client.api.search.response.Tenant;
import org.springframework.beans.factory.annotation.Autowired;

public class TenantMigrationTest extends IdentityAbstractTest {

  @Autowired
  protected ProcessEngineConfigurationImpl processEngineConfiguration;

  @Autowired
  private IdKeyMapper idKeyMapper;

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
    await().timeout(3, TimeUnit.SECONDS).until(() -> request.execute().items().size() == 4);
    var tenants = request.execute().items();
    assertThat(tenants)
        .extracting(Tenant::getTenantId, Tenant::getName)
        .containsAll(expectedTenants.stream().map(tenant -> tuple(tenant.getId(), tenant.getName())).toList());
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

    // then c8 has default tenant + the 2 migrated ones
    TenantsSearchRequest request = camundaClient.newTenantsSearchRequest();
    await().timeout(3, TimeUnit.SECONDS).until(() -> request.execute().items().size() == 3);
    var tenants = request.execute().items();
    assertTenantsMatch(expectedTenants, tenants);

    // and 1 tenant was marked as skipped
    assertThat(idKeyMapper.countSkippedByType(IdKeyMapper.TYPE.TENANT)).isEqualTo(1);
    IdKeyDbModel skipped = idKeyMapper.findSkippedByType(IdKeyMapper.TYPE.TENANT, 0, Integer.MAX_VALUE).getFirst();
    assertThat(skipped.getC7Id()).isEqualTo(tenant2.getId());
  }

  private static void assertTenantsMatch(List<org.camunda.bpm.engine.identity.Tenant> expectedTenants, List<Tenant> tenants) {
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
}
