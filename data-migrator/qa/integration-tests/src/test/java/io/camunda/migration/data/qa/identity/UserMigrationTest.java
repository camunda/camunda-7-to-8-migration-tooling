/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.IdentityMigrator;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class UserMigrationTest extends IdentityMigrationAbstractTest {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(IdentityMigrator.class);

  @Test
  public void shouldMigrateUsers() {
    // given 3 users in c7
    var user1 = testHelper.createUserInC7("user1", "name1", "lastName1");
    var user2 = testHelper.createUserInC7("user2", "name2", "lastName2");
    var user3 = testHelper.createUserInC7("user3", "name3", "lastName");

    var expectedUsers = List.of(user1, user2, user3);

    // when migrating
    identityMigrator.start();

    // then the 3 users exist in c8
    List<io.camunda.client.api.search.response.User> users = testHelper.awaitUserCountAndGet(3);
    testHelper.assertThatUsersContain(expectedUsers, users);
  }

  @Test
  public void shouldSkipUsers() {
    // given 3 users in c7
    var user1 = testHelper.createUserInC7("user1", "name1", "lastName1");
    var user2 = testHelper.createUserInC7("user2", "name2", "lastName2", "@@@");
    var user3 = testHelper.createUserInC7("user3", "name3", "lastName");
    var expectedUsers = List.of(user1, user3);

    // when migrating
    identityMigrator.start();

    // then the 2 users exist in c8
    var users = testHelper.awaitUserCountAndGet(2);
    testHelper.assertThatUsersContain(expectedUsers, users);

    // and 1 user was marked as skipped
    testHelper.verifyUserSkippedViaLogs(user2.getId(), "The provided email '@@@' is not valid.", logs);
  }

  @Test
  public void shouldMigrateOnlyNonPreviouslyMigratedGroups() {
    // given 3 users in c7 but one was already marked as migrated
    var user1 = testHelper.createUserInC7("user1", "name1", "lastName1");
    identityMigrator.start();
    camundaClient.newDeleteUserCommand(user1.getId()).execute(); // To be able to assert that it doesn't get migrated again
    var user2 = testHelper.createUserInC7("user2", "name2", "lastName2");
    var user3 = testHelper.createUserInC7("user3", "name3", "lastName");
    var expectedUsers = List.of(user2, user3);

    // when migrating
    identityMigrator.start();

    // then the 2 users exist in c8
    var users = testHelper.awaitUserCountAndGet(2);
    testHelper.assertThatUsersContain(expectedUsers, users);

    // but not user1
    assertThat(camundaClient.newUsersSearchRequest().filter(f -> f.username(user1.getId())).execute().items()).hasSize(0);
  }

}
