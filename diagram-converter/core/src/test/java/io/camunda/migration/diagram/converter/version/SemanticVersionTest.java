/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.version;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SemanticVersionTest {

  @Test
  void shouldParseSupportedTargetVersions() {
    assertThat(SemanticVersion.parse("8.8")).isEqualTo(SemanticVersion._8_8);
    assertThat(SemanticVersion.parse("8.9")).isEqualTo(SemanticVersion._8_9);
    assertThat(SemanticVersion.parse("8.10")).isEqualTo(SemanticVersion._8_10);
    assertThat(SemanticVersion.parse("8.11")).isEqualTo(SemanticVersion._8_11);
  }
}
