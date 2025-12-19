/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config.property.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Period;
import org.junit.jupiter.api.Test;

class CleanupPropertiesTest {

  @Test
  void shouldHaveDefaultEnabledTrue() {
    // given
    CleanupProperties properties = new CleanupProperties();

    // then
    assertThat(properties.isEnabled()).isTrue();
  }

  @Test
  void shouldHaveDefaultTtl() {
    // given
    CleanupProperties properties = new CleanupProperties();

    // then
    assertThat(properties.getTtl()).isEqualTo(Period.ofDays(180));
  }

  @Test
  void shouldSetEnabled() {
    // given
    CleanupProperties properties = new CleanupProperties();

    // when
    properties.setEnabled(false);

    // then
    assertThat(properties.isEnabled()).isFalse();
  }

  @Test
  void shouldSetTtl() {
    // given
    CleanupProperties properties = new CleanupProperties();
    Period customTtl = Period.ofDays(365);

    // when
    properties.setTtl(customTtl);

    // then
    assertThat(properties.getTtl()).isEqualTo(customTtl);
  }

  @Test
  void shouldAllowNullTtl() {
    // given
    CleanupProperties properties = new CleanupProperties();

    // when
    properties.setTtl(null);

    // then
    assertThat(properties.getTtl()).isNull();
  }
}

