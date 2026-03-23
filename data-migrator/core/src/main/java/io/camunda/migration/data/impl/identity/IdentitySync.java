/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.identity;

import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.clients.C8Client;
import io.camunda.migration.data.impl.logging.IdentityMigratorLogs;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IdentitySync {

  @Autowired
  protected C8Client c8Client;

  @Autowired
  protected MigratorProperties migratorProperties;

  protected Duration timeout;
  protected Duration pollInterval;

  @PostConstruct
  public void init() {
    timeout = Duration.ofMillis(migratorProperties.getIdentitySync().getTimeout());
    pollInterval = Duration.ofMillis(migratorProperties.getIdentitySync().getPollInterval());
    IdentityMigratorLogs.logInitializedSyncUtil(timeout.toMillis(), pollInterval.toMillis());
  }

  /**
   * Waits until a user is visible via Identity, or throws IllegalStateException on timeout.
   */
  public void waitUserVisible(String username) {
    waitUntil("User visible: ", username, () -> isUserVisible(username));
  }

  private boolean isUserVisible(String username) {
    return c8Client.ownerExists(username, null);
  }

  private void waitUntil(String alias, String username, Supplier<Boolean> condition) {
    final Instant deadline = Instant.now().plus(timeout);

    while (true) {
      if (condition.get()) {
        return;
      }
      if (Instant.now().isAfter(deadline)) {
        IdentityMigratorLogs.logUserExistenceCheckTimeout(username);
        return;
      }

      try {
        Thread.sleep(pollInterval.toMillis());
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for condition: " + alias + username, ie);
      }
    }
  }
}