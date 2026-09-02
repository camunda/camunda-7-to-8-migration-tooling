/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SemanticVersionTest {

  @ParameterizedTest
  @EnumSource(SemanticVersion.class)
  void parseRoundTripsEverySupportedVersion(SemanticVersion version) {
    assertThat(SemanticVersion.parse(version.toString())).isEqualTo(version);
  }

  @Test
  void parseRejectsUnknownVersion() {
    assertThatThrownBy(() -> SemanticVersion.parse("9.0"))
        .isInstanceOf(IllegalStateException.class);
  }
}
