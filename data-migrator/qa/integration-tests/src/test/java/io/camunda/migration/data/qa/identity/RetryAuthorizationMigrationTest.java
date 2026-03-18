/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import static io.camunda.client.api.search.enums.OwnerType.USER;
import static io.camunda.migration.data.MigratorMode.LIST_SKIPPED;
import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_OWNER_NOT_EXISTS;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.FAILURE_UNSUPPORTED_SPECIFIC_RESOURCE_ID;
import static io.camunda.migration.data.qa.identity.IdentityTestHelper.assertAuthorizationsSatisfy;
import static io.camunda.migration.data.qa.identity.IdentityTestHelper.getAllSupportedPerms;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.assertj.core.api.Assertions.assertThat;
import static java.lang.String.format;

import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.persistence.IdKeyDbModel;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.qa.util.WhiteBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
public class RetryAuthorizationMigrationTest extends IdentityMigrationAbstractTest {

  public static final String USERNAME = "tomsmith";
  public static final String USER_FIRST_NAME = "Tom";
  public static final String USER_LAST_NAME = "Smith";

  @Autowired
  protected MigratorProperties migratorProperties;

  @Autowired
  protected IdKeyMapper idKeyMapper;

  @BeforeEach
  public void setup() {
    testHelper.createUserInC7(USERNAME, USER_FIRST_NAME, USER_LAST_NAME);
  }

  @AfterEach
  @Override
  public void cleanup() {
    super.cleanup();
    migratorProperties.setSaveSkipReason(false);
    identityService.deleteUser(USERNAME);
    authorizationService.createAuthorizationQuery().userIdIn(USERNAME).list().forEach(a -> authorizationService.deleteAuthorization(a.getId()));
    camundaClient.newUsersSearchRequest()
        .filter(filter -> filter.username(USERNAME)).execute().items()
        .forEach(user -> camundaClient.newDeleteUserCommand(user.getUsername()).execute());
  }

  @Test
  @WhiteBox
  public void shouldMigrateSkippedAuthorizationsOnRetry() {
    migratorProperties.setSaveSkipReason(true);
    // given three skipped authorizations (owner does not exist in C8)
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.APPLICATION, "cockpit", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.APPLICATION, "admin", Set.of(Permissions.ALL));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.APPLICATION, "tasklist", Set.of(Permissions.ALL));

    // when
    identityMigrator.start();

    // then
    assertThat(idKeyMapper.findSkippedByTypeWithOffset(IdKeyMapper.TYPE.AUTHORIZATION, 0, 10))
        .hasSize(3)
        .extracting(IdKeyDbModel::getSkipReason)
        .containsOnly(FAILURE_OWNER_NOT_EXISTS);

    // when issue is fixed
    testHelper.createUserInC8(USERNAME, USER_FIRST_NAME, USER_LAST_NAME);

    // and migration is retried
    identityMigrator.setMode(RETRY_SKIPPED);
    identityMigrator.start();

    // then all three authorizations are migrated successfully
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(3, USERNAME);
    assertAuthorizationsSatisfy(authorizations, ResourceType.COMPONENT, "operate", USER, USERNAME, getAllSupportedPerms(ResourceType.COMPONENT));
    assertAuthorizationsSatisfy(authorizations, ResourceType.COMPONENT, "admin", USER, USERNAME, getAllSupportedPerms(ResourceType.COMPONENT));
    assertAuthorizationsSatisfy(authorizations, ResourceType.COMPONENT, "tasklist", USER, USERNAME, getAllSupportedPerms(ResourceType.COMPONENT));
  }

  @Test
  @WhiteBox
  public void shouldOnlyMigrateSkippedAuthorizationsOnRetry() {
    migratorProperties.setSaveSkipReason(true);
    // given one skipped and two migrated authorizations
    testHelper.createUserInC8(USERNAME, USER_FIRST_NAME, USER_LAST_NAME);
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.AUTHORIZATION, "*", Set.of(Permissions.READ));
    var skippedAuth = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.BATCH, "batchId", Set.of(Permissions.READ));
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.PROCESS_DEFINITION, "*", Set.of(Permissions.READ));

    // when
    identityMigrator.start();

    // then
    assertThat(idKeyMapper.findSkippedByTypeWithOffset(IdKeyMapper.TYPE.AUTHORIZATION, 0, 10))
        .singleElement()
        .extracting(IdKeyDbModel::getSkipReason)
        .isEqualTo(format(FAILURE_UNSUPPORTED_SPECIFIC_RESOURCE_ID, "batchId", Resources.BATCH.resourceName()));

    // when issue is fixed
    skippedAuth.setResourceId("*");
    authorizationService.saveAuthorization(skippedAuth);

    // and migration is retried
    identityMigrator.setMode(RETRY_SKIPPED);
    identityMigrator.start();

    // then we have three migrated authorizations
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(3, USERNAME);
    assertAuthorizationsSatisfy(authorizations, ResourceType.AUTHORIZATION, "*", USER, USERNAME, Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.BATCH, "*", USER, USERNAME, Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.PROCESS_DEFINITION, "*", USER, USERNAME, Set.of(PermissionType.READ_PROCESS_DEFINITION));
  }

  @Test
  @WhiteBox
  public void shouldNotReattemptSkippedOnRerun() {
    migratorProperties.setSaveSkipReason(true);
    // given one skipped, one migrated authorization, and one non migrated authorization
    testHelper.createUserInC8(USERNAME, USER_FIRST_NAME, USER_LAST_NAME);
    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.AUTHORIZATION, "*", Set.of(Permissions.READ));
    var skippedAuth = testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.BATCH, "batchId", Set.of(Permissions.READ));

    // when
    identityMigrator.start();

    // then
    assertThat(idKeyMapper.findSkippedByTypeWithOffset(IdKeyMapper.TYPE.AUTHORIZATION, 0, 10))
        .singleElement()
        .extracting(IdKeyDbModel::getSkipReason)
        .isEqualTo(format(FAILURE_UNSUPPORTED_SPECIFIC_RESOURCE_ID, "batchId", Resources.BATCH.resourceName()));

    testHelper.createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.PROCESS_DEFINITION, "*", Set.of(Permissions.READ));

    // when issue is fixed but migration is rerun without retry mode
    skippedAuth.setResourceId("*");
    authorizationService.saveAuthorization(skippedAuth);
    identityMigrator.start(); // default mode is MIGRATE

    // then we have only two migrated authorizations (skipped remained skipped)
    var authorizations = testHelper.awaitAuthorizationsCountAndGet(2, USERNAME);
    assertAuthorizationsSatisfy(authorizations, ResourceType.AUTHORIZATION, "*", USER, USERNAME, Set.of(PermissionType.READ));
    assertAuthorizationsSatisfy(authorizations, ResourceType.PROCESS_DEFINITION, "*", USER, USERNAME, Set.of(PermissionType.READ_PROCESS_DEFINITION));
  }

  @Test
  public void shouldListSkippedAuthorizations(CapturedOutput output) {
    // given skipped authorizations (owner does not exist in C8)
    List<String> authIds = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      authIds.add(testHelper
          .createAuthorizationInC7(AUTH_TYPE_GRANT, USERNAME, null, Resources.APPLICATION, "cockpit-" + i, Set.of(Permissions.ALL))
          .getId());
    }
    identityMigrator.start();

    // when running migration with list skipped mode
    identityMigrator.setMode(LIST_SKIPPED);
    identityMigrator.start();

    // then all skipped authorizations were listed
    String expectedHeader = "Previously skipped \\[" + IdKeyMapper.TYPE.AUTHORIZATION.getDisplayName() + "s\\]:";
    String regex = expectedHeader + "\\R((?:.+\\R){9}.+)";
    assertThat(output.getOut()).containsPattern(regex);
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(output.getOut());
    final String capturedIds = matcher.find() ? matcher.group(1) : "";
    authIds.forEach(id -> assertThat(capturedIds).contains(id));
  }

}
