/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.message;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class ContextBuilderTest {
  @Test
  void shouldAddEntries() {
    Map<String, String> context = ContextBuilder.builder().entry("key", "value").build();
    assertThat(context).containsEntry("key", "value");
  }

  @Test
  void shouldThrowWhenOverridingEntries() {
    assertThrows(
        IllegalStateException.class,
        () -> ContextBuilder.builder().entry("key", "value").entry("key", "value2").build());
  }

  @Test
  void shouldNotThrowWhenSettingSameEntryManyTimes() {
    Map<String, String> context =
        ContextBuilder.builder().entry("key", "value").entry("key", "value").build();
    assertThat(context).containsEntry("key", "value");
  }

  @Test
  void shouldAddEntriesFromOtherContext() {
    Map<String, String> context =
        ContextBuilder.builder()
            .context(ContextBuilder.builder().entry("key", "value").build())
            .entry("key2", "value2")
            .build();
    assertThat(context).containsEntry("key", "value").containsEntry("key2", "value2");
  }
}
