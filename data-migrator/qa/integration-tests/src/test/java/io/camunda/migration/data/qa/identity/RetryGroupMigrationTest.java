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

import io.camunda.client.api.search.response.Group;
import io.camunda.migration.data.IdentityMigrator;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
public class RetryGroupMigrationTest extends IdentityMigrationAbstractTest {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(IdentityMigrator.class);

  @Test
  public void shouldMigrateSkippedGroupsOnRetry() {
    // given 3 skipped groups (missing name)
    var g1 = testHelper.createGroupInC7("groupId1", "");
    var g2 = testHelper.createGroupInC7("groupId2", "");
    var g3 = testHelper.createGroupInC7("groupId3", "");

    identityMigrator.start();

    // when issue is fixed
    g1.setName("groupName1");
    g2.setName("groupName2");
    g3.setName("groupName3");
    identityService.saveGroup(g1);
    identityService.saveGroup(g2);
    identityService.saveGroup(g3);

    // and migration is retried
    identityMigrator.setMode(RETRY_SKIPPED);
    identityMigrator.start();

    // then all three groups are migrated successfully
    List<Group> groups = testHelper.awaitGroupsCountAndGet(3);
    testHelper.assertThatGroupsContain(List.of(g1, g2, g3), groups);
  }

  @Test
  public void shouldOnlyMigrateSkippedGroupsOnRetry() {
    // given one skipped and two migrated groups
    var g1 = testHelper.createGroupInC7("groupId1", "groupName1");
    var g2 = testHelper.createGroupInC7("groupId2", ""); // missing name
    var g3 = testHelper.createGroupInC7("groupId3", "groupName3");

    identityMigrator.start();

    // when issue is fixed
    g2.setName("groupName2");
    identityService.saveGroup(g2);

    // and migration is retried
    identityMigrator.setMode(RETRY_SKIPPED);
    identityMigrator.start();

    // then only the previously skipped group is additionally migrated
    List<Group> groups = testHelper.awaitGroupsCountAndGet(3);
    testHelper.assertThatGroupsContain(List.of(g1, g2, g3), groups);
  }

  @Test
  public void shouldNotReattemptSkippedOnRerun() {
    // given one skipped, one migrated group, and one non migrated group
    var g1 = testHelper.createGroupInC7("groupId1", "groupName1");
    var g2 = testHelper.createGroupInC7("groupId2", ""); // missing name

    identityMigrator.start();
    var g3 = testHelper.createGroupInC7("groupId3", "groupName3");

    // when issue is fixed but migration is rerun without retry
    g2.setName("groupName2");
    identityService.saveGroup(g2);

    identityMigrator.start(); // default mode is MIGRATE

    // then the non migrated group is migrated but the previously skipped one is not
    List<Group> groups = testHelper.awaitGroupsCountAndGet(2);
    testHelper.assertThatGroupsContain(List.of(g1, g3), groups);
    assertThat(camundaClient.newGroupsSearchRequest().filter(f -> f.groupId("groupId2")).execute().items()).hasSize(0);
  }

  @Test
  public void shouldListSkippedGroups(CapturedOutput output) {
    // given skipped groups (empty name)
    List<String> groupIds = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      groupIds.add(testHelper.createGroupInC7("groupId" + i, "").getId());
    }
    identityMigrator.start();

    // when running migration with list skipped mode
    identityMigrator.setMode(LIST_SKIPPED);
    identityMigrator.start();

    // then all skipped groups were listed
    String expectedHeader = "Previously skipped \\[" + IdKeyMapper.TYPE.GROUP.getDisplayName() + "s\\]:";
    String regex = expectedHeader + "\\R((?:.+\\R){9}.+)";
    assertThat(output.getOut()).containsPattern(regex);
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(output.getOut());
    final String capturedIds = matcher.find() ? matcher.group(1) : "";
    groupIds.forEach(id -> assertThat(capturedIds).contains(id));
  }

  @Test
  public void shouldMigrateMembershipsForSkippedGroupsOnRetry() {
    // given 1 skipped group (missing name)
    var g1 = testHelper.createGroupInC7("groupId1", "");

    // and memberships for that group
    testHelper.createUserInC7("userId1", "firstName1", "lastName1");
    testHelper.createUserInC7("userId2", "firstName2", "lastName2");
    identityService.createMembership("userId1", g1.getId());
    identityService.createMembership("userId2", g1.getId());

    identityMigrator.start();

    // verify membership migration was skipped
    testHelper.assertNoMembershipsForGroup(g1.getId());

    // when issue with group is fixed
    g1.setName("groupName1");
    identityService.saveGroup(g1);

    // and migration is retried
    identityMigrator.setMode(RETRY_SKIPPED);
    identityMigrator.start();

    // then the group is migrated successfully
    List<Group> groups = testHelper.awaitGroupsCountAndGet(1);
    testHelper.assertThatGroupsContain(List.of(g1), groups);

    // and also the memberships
    testHelper.assertThatUsersForGroupContainExactly(g1.getId(), "userId1", "userId2");
  }
}
