/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.history;

import static io.camunda.migration.data.constants.MigratorConstants.DEFAULT_LEGACY_ID_PREFIX;
import static io.camunda.migration.data.impl.util.LegacyIdPrefixResolver.MAX_PREFIX_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.config.property.history.HistoryProperties;
import io.camunda.migration.data.impl.util.LegacyIdPrefixResolver;
import org.junit.jupiter.api.Test;

class LegacyIdPrefixResolverTest {

  @Test
  void shouldUseDefaultPrefixWhenHistoryNotConfigured() {
    // given a configuration without a history block
    LegacyIdPrefixResolver resolver = resolver(new MigratorProperties());

    // then
    assertThat(resolver.getPrefix()).isEqualTo(DEFAULT_LEGACY_ID_PREFIX);
  }

  @Test
  void shouldUseDefaultPrefixWhenNotConfigured() {
    // given a history block without an explicit prefix
    LegacyIdPrefixResolver resolver = resolver(withPrefix(null));

    // then
    assertThat(resolver.getPrefix()).isEqualTo(DEFAULT_LEGACY_ID_PREFIX);
  }

  @Test
  void shouldUseConfiguredPrefix() {
    // given
    LegacyIdPrefixResolver resolver = resolver(withPrefix("acme-legacy-"));

    // then
    assertThat(resolver.getPrefix()).isEqualTo("acme-legacy-");
  }

  @Test
  void shouldApplyPrefixToDefinitionId() {
    // given
    LegacyIdPrefixResolver resolver = resolver(withPrefix("acme-"));

    // then
    assertThat(resolver.applyTo("invoice")).isEqualTo("acme-invoice");
  }

  @Test
  void shouldApplyDefaultPrefixToDefinitionId() {
    // given
    LegacyIdPrefixResolver resolver = resolver(new MigratorProperties());

    // then
    assertThat(resolver.applyTo("invoice")).isEqualTo("c7-legacy-invoice");
  }

  @Test
  void shouldReturnNullWhenApplyingToNullDefinitionId() {
    // given
    LegacyIdPrefixResolver resolver = resolver(withPrefix("acme-"));

    // then
    assertThat(resolver.applyTo(null)).isNull();
  }

  @Test
  void shouldAcceptPrefixWithAllowedCharacters() {
    // given / then: leading underscore, dots, digits, hyphens and underscores are allowed
    assertThat(resolver(withPrefix("_c7.legacy-2_")).getPrefix()).isEqualTo("_c7.legacy-2_");
    assertThat(resolver(withPrefix("Legacy")).getPrefix()).isEqualTo("Legacy");
  }

  @Test
  void shouldAcceptPrefixAtMaximumLength() {
    // given a prefix of exactly the maximum allowed length
    String maxPrefix = "a".repeat(MAX_PREFIX_LENGTH);

    // then
    assertThat(resolver(withPrefix(maxPrefix)).getPrefix()).isEqualTo(maxPrefix);
  }

  @Test
  void shouldRejectEmptyPrefix() {
    assertThatThrownBy(() -> resolver(withPrefix("")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("legacy-id-prefix")
        .hasMessageContaining("must not be blank");
  }

  @Test
  void shouldRejectBlankPrefix() {
    assertThatThrownBy(() -> resolver(withPrefix("   ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void shouldRejectPrefixExceedingMaximumLength() {
    // given a prefix one character longer than the maximum
    String tooLong = "a".repeat(MAX_PREFIX_LENGTH + 1);

    assertThatThrownBy(() -> resolver(withPrefix(tooLong)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not exceed");
  }

  @Test
  void shouldRejectPrefixStartingWithDigit() {
    assertThatThrownBy(() -> resolver(withPrefix("7legacy-")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must start with a letter or underscore");
  }

  @Test
  void shouldRejectPrefixStartingWithHyphen() {
    assertThatThrownBy(() -> resolver(withPrefix("-legacy-")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must start with a letter or underscore");
  }

  @Test
  void shouldRejectPrefixWithWhitespace() {
    assertThatThrownBy(() -> resolver(withPrefix("c7 legacy-")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectPrefixWithDisallowedSymbols() {
    assertThatThrownBy(() -> resolver(withPrefix("legacy!")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  protected static LegacyIdPrefixResolver resolver(MigratorProperties properties) {
    return new LegacyIdPrefixResolver(properties);
  }

  protected static MigratorProperties withPrefix(String prefix) {
    MigratorProperties properties = new MigratorProperties();
    HistoryProperties history = new HistoryProperties();
    history.setLegacyIdPrefix(prefix);
    properties.setHistory(history);
    return properties;
  }
}
