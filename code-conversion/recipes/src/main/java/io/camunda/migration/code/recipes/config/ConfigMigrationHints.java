/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.config;

/**
 * Shared TODO hints emitted while migrating Spring Boot configuration. A hint is used when a Camunda
 * 7 key is being removed whose <em>intent</em> has a Camunda 8 counterpart, but whose value cannot be
 * translated mechanically (different semantics). The hint preserves the signal without guessing a
 * value, so the user can reconsider the setting by hand.
 */
final class ConfigMigrationHints {

  private ConfigMigrationHints() {}

  /** C7 key prefix whose presence triggers the job-execution hint. */
  static final String JOB_EXECUTION_KEY_PREFIX = "camunda.bpm.job-execution";

  /**
   * Emitted (without a leading `#`) when {@code camunda.bpm.job-execution.*} is removed. Camunda 8
   * has no embedded job executor; concurrency is a remote job-worker concern instead.
   */
  static final String JOB_EXECUTION =
      "TODO(migration): Camunda 8 runs job workers remotely (no embedded job executor)."
          + " Tune worker concurrency via camunda.client.worker.defaults.max-jobs-active"
          + " and camunda.client.execution-threads.";
}
