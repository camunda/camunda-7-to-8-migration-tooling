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
    timeout = Duration.ofMillis(migratorProperties.getIdentity().getSync().getTimeout());
    pollInterval = Duration.ofMillis(migratorProperties.getIdentity().getSync().getPollInterval());
    IdentityMigratorLogs.logInitializedIdentitySync(timeout.toMillis(), pollInterval.toMillis());
  }

  /**
   * Waits until a user is visible via Identity, or logs and returns on timeout.
   */
  public void waitUserVisible(String username) {
    waitUntil("User visible: " + username, () -> c8Client.ownerExists(username, null));
  }

  /**
   * Waits until a group is visible via Identity, or logs and returns on timeout.
   */
  public void waitGroupVisible(String groupId) {
    waitUntil("Group visible: " + groupId, () -> c8Client.ownerExists(null, groupId));
  }

  /**
   * Waits until a tenant is visible via Identity, or logs and returns on timeout.
   */
  public void waitTenantVisible(String tenantId) {
    waitUntil("Tenant visible: " + tenantId, () -> c8Client.tenantExists(tenantId));
  }

  protected void waitUntil(String alias, Supplier<Boolean> condition) {
    final Instant deadline = Instant.now().plus(timeout);

    while (true) {
      if (condition.get()) {
        return;
      }
      if (Instant.now().isAfter(deadline)) {
        IdentityMigratorLogs.logIdentitySyncTimeout(alias);
        return;
      }

      try {
        Thread.sleep(pollInterval.toMillis());
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for condition: " + alias, ie);
      }
    }
  }
}