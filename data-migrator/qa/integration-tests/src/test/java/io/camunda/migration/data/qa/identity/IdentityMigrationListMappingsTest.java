/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import static io.camunda.migration.data.MigratorMode.LIST_MIGRATED;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;

import io.camunda.migration.data.IdentityMigrator;
import io.camunda.migration.data.qa.util.EntitiesLogParserUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(locations = "classpath:application-warn.properties")
@ExtendWith(OutputCaptureExtension.class)
public class IdentityMigrationListMappingsTest extends IdentityMigrationAbstractTest {

  public static final String USERNAME = "tomsmith";
  public static final String USER_FIRST_NAME = "Tom";
  public static final String USER_LAST_NAME = "Smith";

  @BeforeEach
  public void setup() {
    testHelper.createUserInC7(USERNAME, USER_FIRST_NAME, USER_LAST_NAME);
    testHelper.createUserInC8(USERNAME, USER_FIRST_NAME, USER_LAST_NAME);
  }

  @AfterEach
  @Override
  public void cleanup() {
    super.cleanup();
    identityService.deleteUser(USERNAME);
    camundaClient.newUsersSearchRequest()
        .filter(filter -> filter.username(USERNAME)).execute().items()
        .forEach(user -> camundaClient.newDeleteUserCommand(user.getUsername()).execute());
  }

  @Test
  public void shouldListMigratedTenants(CapturedOutput output) {
    // given migrated tenants
    testHelper.createTenantInC7("tenantId1", "tenantName1");
    testHelper.createTenantInC7("tenantId2", "tenantName2");
    identityMigrator.start();

    // when
    identityMigrator.setMode(LIST_MIGRATED);
    identityMigrator.start();

    // then
    Map<String, List<String>> migratedEntitiesByType =
        EntitiesLogParserUtils.parseMigratedEntitiesOutput(output.getOut());

    assertThat(migratedEntitiesByType).containsKey(TYPE.TENANT.getDisplayName());
    List<String> tenantMappings = migratedEntitiesByType.get(TYPE.TENANT.getDisplayName());
    assertThat(tenantMappings).hasSize(2);
    // Tenants use DEFAULT_TENANT_KEY (1) since tenants don't have real C8 keys
    assertThat(tenantMappings).contains(
        "tenantId1 " + IdentityMigrator.DEFAULT_TENANT_KEY,
        "tenantId2 " + IdentityMigrator.DEFAULT_TENANT_KEY);
  }

  @Test
  public void shouldListMigratedAuthorizations(CapturedOutput output) {
    // given migrated authorizations
    Authorization auth1 = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null,
        Resources.APPLICATION, "cockpit", Set.of(Permissions.ALL));
    identityMigrator.start();

    List<io.camunda.client.api.search.response.Authorization> auths = testHelper.awaitAuthorizationsCountAndGet(1, USERNAME);
    String c8key = auths.stream()
        .filter(a -> "operate".equals(a.getResourceId()))
        .findFirst()
        .orElseThrow()
        .getAuthorizationKey();

    // when
    identityMigrator.setMode(LIST_MIGRATED);
    identityMigrator.start();

    // then
    Map<String, List<String>> migratedEntitiesByType =
        EntitiesLogParserUtils.parseMigratedEntitiesOutput(output.getOut());

    assertThat(migratedEntitiesByType).containsKey(TYPE.AUTHORIZATION.getDisplayName());
    List<String> authMappings = migratedEntitiesByType.get(TYPE.AUTHORIZATION.getDisplayName());
    assertThat(authMappings).hasSize(1);
    assertThat(authMappings).anyMatch(line -> line.startsWith(auth1.getId() + " " + c8key));
  }

  @Test
  public void shouldShowNoMappingsWhenNoEntitiesMigrated(CapturedOutput output) {
    // given: no migration has been run
    // when
    identityMigrator.setMode(LIST_MIGRATED);
    identityMigrator.start();

    // then
    String outputStr = output.getOut();
    assertThat(outputStr).contains(
        "No entities of type [" + TYPE.TENANT.getDisplayName() + "] were migrated");
    assertThat(outputStr).contains(
        "No entities of type [" + TYPE.AUTHORIZATION.getDisplayName() + "] were migrated");
  }
}
