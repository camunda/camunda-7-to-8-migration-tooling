/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;

import io.camunda.migration.code.recipes.client.DetectJobBasedUserTaskWorkerRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class DetectJobBasedUserTaskWorkerTest implements RewriteTest {

  @Test
  void flagsJobBasedUserTaskWorker() {
    rewriteRun(
        spec -> spec.recipe(new DetectJobBasedUserTaskWorkerRecipe()),
        // language=java
        java(
"""
package org.camunda.community.migration.example;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;

public class UserTaskWorker {

    @JobWorker(type = "io.camunda.zeebe:userTask")
    public void handleUserTask(ActivatedJob job) {
    }
}
""",
"""
package org.camunda.community.migration.example;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;

public class UserTaskWorker {

    // TODO: job-based user tasks are deprecated (removed in Camunda 8.10). This @JobWorker handles the built-in "io.camunda.zeebe:userTask" job type. Migrate to Camunda user tasks: remove this worker and manage the task via the User Task API / Tasklist. See https://docs.camunda.io/docs/apis-tools/migration-manuals/migrate-to-camunda-user-tasks/
    @JobWorker(type = "io.camunda.zeebe:userTask")
    public void handleUserTask(ActivatedJob job) {
    }
}
"""));
  }

  @Test
  void doesNotFlagOtherJobWorkers() {
    rewriteRun(
        spec -> spec.recipe(new DetectJobBasedUserTaskWorkerRecipe()),
        // language=java
        java(
"""
package org.camunda.community.migration.example;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;

public class PaymentWorker {

    @JobWorker(type = "retrievePayment")
    public void retrievePayment(ActivatedJob job) {
    }
}
"""));
  }
}
