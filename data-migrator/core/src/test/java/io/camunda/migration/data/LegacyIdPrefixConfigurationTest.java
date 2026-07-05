/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.util.LegacyIdPrefixResolver;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

class LegacyIdPrefixConfigurationTest {

  @Nested
  @SpringBootTest
  class DefaultPrefixTest {

    @Autowired
    protected LegacyIdPrefixResolver legacyIdPrefix;

    @Test
    public void shouldUseDefaultPrefixWhenNotConfigured() {
      assertThat(legacyIdPrefix.getPrefix()).isEqualTo("c7-legacy-");
      assertThat(legacyIdPrefix.applyTo("invoice")).isEqualTo("c7-legacy-invoice");
    }
  }

  @Nested
  @SpringBootTest
  @TestPropertySource(properties = {
      "camunda.migrator.history.legacy-id-prefix=acme-legacy-"
  })
  class CustomPrefixTest {

    @Autowired
    protected MigratorProperties migratorProperties;

    @Autowired
    protected LegacyIdPrefixResolver legacyIdPrefix;

    @Test
    public void shouldBindConfiguredPrefix() {
      assertThat(migratorProperties.getHistory().getLegacyIdPrefix()).isEqualTo("acme-legacy-");
    }

    @Test
    public void shouldApplyConfiguredPrefix() {
      assertThat(legacyIdPrefix.getPrefix()).isEqualTo("acme-legacy-");
      assertThat(legacyIdPrefix.applyTo("invoice")).isEqualTo("acme-legacy-invoice");
    }
  }
}
