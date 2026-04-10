/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_CREATE_GROUP_MEMBERSHIP;
import static io.camunda.migration.data.impl.logging.ConfigurationLogs.INVALID_IDENTITY_PROPERTIES_ERROR;
import static io.camunda.migration.data.impl.logging.IdentityMigratorLogs.SKIPPED_GROUP;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.api.search.response.Group;
import io.camunda.migration.data.IdentityMigrator;
import io.camunda.migration.data.config.MigratorAutoConfiguration;
import io.github.netmikey.logunit.api.LogCapturer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.SpringBootTest;

public class GroupMigrationTest extends IdentityMigrationAbstractTest {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(IdentityMigrator.class);

  @Autowired
  protected ProcessEngineConfiguration processEngineConfiguration;

  @Test
  public void shouldMigrateGroups() {
    // given 3 groups in c7
    var group1 = testHelper.createGroupInC7("groupId1", "groupName1");
    var group2 = testHelper.createGroupInC7("groupId2", "groupName2");
    var group3 = testHelper.createGroupInC7("groupId3", "groupName3");
    var expectedGroups = List.of(group1, group2, group3);

    // when migrating
    identityMigrator.start();

    // then the 3 groups exist in c8
    List<Group> groups = testHelper.awaitGroupsCountAndGet(3);
    testHelper.assertThatGroupsContain(expectedGroups, groups);
  }

  @Test
  public void shouldSkipGroups() {
    // given 3 groups in c7
    processEngineConfiguration.setGeneralResourceWhitelistPattern(".+"); // allow to create group with any ID
    var group1 = testHelper.createGroupInC7("groupId1", "groupName1");
    var group2 = testHelper.createGroupInC7("groupId2-!~^", "groupName2");
    var group3 = testHelper.createGroupInC7("groupId3", "groupName3");
    var expectedGroups = List.of(group1, group3);

    // when migrating
    identityMigrator.start();

    // then the 2 groups exist in c8
    List<Group> groups = testHelper.awaitGroupsCountAndGet(2);
    testHelper.assertThatGroupsContain(expectedGroups, groups);

    // and 1 group was marked as skipped
    testHelper.verifyGroupSkippedViaLogs(group2.getId(), group2.getName(), "The provided groupId contains illegal characters.", logs);
  }

  @Test
  public void shouldMigrateOnlyNonPreviouslyMigratedGroups() {
    // given 3 groups in c7 but one was already marked as migrated
    var group1 = testHelper.createGroupInC7("groupId1", "groupName1");
    identityMigrator.start();
    camundaClient.newDeleteGroupCommand(group1.getId()).execute(); // To be able to assert that it doesn't get migrated again
    var group2 = testHelper.createGroupInC7("groupId2", "groupName2");
    var group3 = testHelper.createGroupInC7("groupId3", "groupName3");
    var expectedGroups = List.of(group2, group3);

    // when migrating
    identityMigrator.start();

    // then the 2 groups exist in c8
    List<Group> groups = testHelper.awaitGroupsCountAndGet(2);
    testHelper.assertThatGroupsContain(expectedGroups, groups);

    // but not group1
    assertThat(camundaClient.newGroupsSearchRequest().filter(f -> f.groupId(group1.getId())).execute().items()).hasSize(0);
  }

  @Test
  public void shouldMigrateGroupMemberships() {
    // given
    var group1 = testHelper.createGroupInC7("groupId1", "groupName1");
    var group2 = testHelper.createGroupInC7("groupId2", "groupName2");

    testHelper.createUserInC7("userId1", "firstName1", "lastName1");
    testHelper.createUserInC7("userId2", "firstName2", "lastName2");

    identityService.createMembership("userId1", group1.getId());
    identityService.createMembership("userId2", group2.getId());

    // when
    identityMigrator.start();

    // then the 2 groups exist in c8
    List<Group> groups = testHelper.awaitGroupsCountAndGet(2);
    testHelper.assertThatGroupsContain(List.of(group1, group2), groups);

    // and group memberships are migrated
    testHelper.assertThatUsersForGroupContainExactly(group1.getId(), "userId1");
    testHelper.assertThatUsersForGroupContainExactly(group2.getId(), "userId2");
  }

  @Test
  public void shouldNotMigrateGroupMembershipsWhenUserDoesNotExist() {
    // given
    var group1 = testHelper.createGroupInC7("groupId1", "groupName1");

    testHelper.createUserInC7("userId0", "firstName0", "lastName0", "@@@"); // invalid email so it cannot get migrated
    testHelper.createUserInC7("userId1", "firstName1", "lastName1");
    testHelper.createUserInC7("userId2", "firstName2", "lastName2");

    identityService.createMembership("userId0", group1.getId()); // cannot be migrated because user does not exist
    identityService.createMembership("userId1", group1.getId());
    identityService.createMembership("userId2", group1.getId());

    // when
    identityMigrator.start();

    // then the group exists in c8
    List<Group> groups = testHelper.awaitGroupsCountAndGet(1);
    testHelper.assertThatGroupsContain(List.of(group1), groups);

    // and 2 group memberships are migrated
    await().timeout(3, TimeUnit.SECONDS).untilAsserted(() -> testHelper.assertThatUsersForGroupContainExactly(group1.getId(), "userId1", "userId2"));

    // and 1 group membership could not be migrated
    logs.assertContains(formatMessage(FAILED_TO_CREATE_GROUP_MEMBERSHIP, group1.getId(), "userId0"));
  }

  @Nested
  @SpringBootTest(properties = {
      "camunda.migrator.identity.skip-users=true",
      "camunda.migrator.identity.skip-groups=true",
  })
  public class SkipGroupsEnabledTest {

    @Test
    public void shouldNotMigrateGroupsWhenSkipGroupsIsEnabled() {
      // given groups exist in c7 but skip-groups is enabled
      testHelper.createGroupInC7("groupId1", "name1");
      testHelper.createGroupInC7("groupId2", "name2");
      testHelper.createGroupInC7("groupId3", "name3");

      // when migrating
      identityMigrator.start();

      // then no groups were migrated
      await().pollDelay(Duration.ofSeconds(2)).timeout(Duration.ofSeconds(5)).untilAsserted(() -> {
        var currentGroups = camundaClient.newGroupsSearchRequest().execute().items();
        assertThat(currentGroups).hasSize(0);
      });
    }
  }

  @Nested
  @SpringBootTest(properties = {
      "camunda.migrator.identity.skip-users=true",
      "camunda.migrator.identity.skip-groups=false",
  })
  public class SkipUsersEnabledTest {

    @Test
    public void shouldMigrateGroupsButNotUsers() {
      // given groups and memberships exist in c7 but skip-users is enabled
      var group1 = testHelper.createGroupInC7("groupId1", "groupName1");
      var user1 = testHelper.createUserInC7("userId1", "firstName1", "lastName1");
      identityService.createMembership("userId1", group1.getId());

      // when migrating
      identityMigrator.start();

      // then the groups were migrated
      List<Group> groups = testHelper.awaitGroupsCountAndGet(1);
      testHelper.assertThatGroupsContain(List.of(group1), groups);

      // but the membership could not be migrated because the user wasn't migrated
      logs.assertContains(formatMessage(FAILED_TO_CREATE_GROUP_MEMBERSHIP, group1.getId(), user1.getId()));
    }
  }

  @Nested
  class InvalidSkipPropertiesTest {

    @Test
    void shouldFailAtStartupWhenBadPropertyCombination() {

      assertThatThrownBy(() ->
          new SpringApplicationBuilder(MigratorAutoConfiguration.class)
              .properties(
                  "camunda.migrator.identity.skip-users=false",
                  "camunda.migrator.identity.skip-groups=true"
              )
              .run()
      )
          .hasMessageContaining("Error creating bean")
          .hasRootCauseInstanceOf(BindValidationException.class)
          .rootCause()
          .hasMessageContaining(INVALID_IDENTITY_PROPERTIES_ERROR);
    }
  }
}