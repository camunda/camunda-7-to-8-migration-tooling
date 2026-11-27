/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class MultiDbExtensionTest {

  @Test
  public void shouldConfigureStartupRetriesForEveryDatabaseContainer() {
    assertThat(MultiDbExtension.containers.values())
        .extracting(container -> container.getStartupAttempts())
        .containsOnly(MultiDbExtension.DATABASE_CONTAINER_STARTUP_ATTEMPTS);
  }
}
