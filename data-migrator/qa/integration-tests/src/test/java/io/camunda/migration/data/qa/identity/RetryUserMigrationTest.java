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
public class RetryUserMigrationTest extends IdentityMigrationAbstractTest {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(IdentityMigrator.class);

  @Test
  public void shouldMigrateSkippedUsersOnRetry() {
    // given 3 skipped users (invalid email)
    var user1 = testHelper.createUserInC7("user1", "name1", "lastName1", "@@@");
    var user2 = testHelper.createUserInC7("user2", "name2", "lastName2", "@@@");
    var user3 = testHelper.createUserInC7("user3", "name3", "lastName", "@@@");

    identityMigrator.start();

    // when issue is fixed
    user1.setEmail("user1@camunda.com");
    user2.setEmail("user2@camunda.com");
    user3.setEmail("user3@camunda.com");
    identityService.saveUser(user1);
    identityService.saveUser(user2);
    identityService.saveUser(user3);

    // and migration is retried
    identityMigrator.setMode(RETRY_SKIPPED);
    identityMigrator.start();

    // then all three users are migrated successfully
    var users = testHelper.awaitUserCountAndGet(3);
    testHelper.assertThatUsersContain(List.of(user1, user2, user3), users);
  }

  @Test
  public void shouldOnlyMigrateSkippedUsersOnRetry() {
    // given one skipped and two migrated users
    var user1 = testHelper.createUserInC7("user1", "name1", "lastName1");
    var user2 = testHelper.createUserInC7("user2", "name2", "lastName2", "@@@"); // invalid email
    var user3 = testHelper.createUserInC7("user3", "name3", "lastName");

    identityMigrator.start();

    // when issue is fixed
    user2.setEmail("user2@camunda.com");
    identityService.saveUser(user2);

    // and migration is retried
    identityMigrator.setMode(RETRY_SKIPPED);
    identityMigrator.start();

    // then only the previously skipped user is additionally migrated
    var users = testHelper.awaitUserCountAndGet(3);
    testHelper.assertThatUsersContain(List.of(user1, user2, user3), users);
  }

  @Test
  public void shouldNotReattemptSkippedOnRerun() {
    // given one skipped, one migrated, and one non migrated user
    var user1 = testHelper.createUserInC7("user1", "name1", "lastName1");
    var user2 = testHelper.createUserInC7("user2", "name2", "lastName2", "@@@"); // invalid email

    identityMigrator.start();
    var user3 = testHelper.createUserInC7("user3", "name3", "lastName");

    // when issue is fixed but migration is rerun without retry
    user2.setEmail("user2@camunda.com");
    identityService.saveUser(user2);

    identityMigrator.start(); // default mode is MIGRATE

    // then the non migrated user is migrated but the previously skipped one is not
    var users = testHelper.awaitUserCountAndGet(2);
    testHelper.assertThatUsersContain(List.of(user1, user3), users);
    assertThat(camundaClient.newUsersSearchRequest().filter(f -> f.username(user2.getId())).execute().items()).hasSize(0);
  }

  @Test
  public void shouldListSkippedUsers(CapturedOutput output) {
    // given skipped users (invalid email)
    List<String> userIds = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      userIds.add(testHelper.createUserInC7("user" + i, "name" + i, "lastName" + i, "@@@").getId());
    }
    identityMigrator.start();

    // when running migration with list skipped mode
    identityMigrator.setMode(LIST_SKIPPED);
    identityMigrator.start();

    // then all skipped users were listed
    String expectedHeader = "Previously skipped \\[" + IdKeyMapper.TYPE.USER.getDisplayName() + "s\\]:";
    String regex = expectedHeader + "\\R((?:.+\\R){9}.+)";
    assertThat(output.getOut()).containsPattern(regex);
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(output.getOut());
    final String capturedIds = matcher.find() ? matcher.group(1) : "";
    userIds.forEach(id -> assertThat(capturedIds).contains(id));
  }

}
