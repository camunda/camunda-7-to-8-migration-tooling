/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TargetPlatformVersionPolicyTest {

  @Test
  void rejectsAnOlderVersionAsTheDefaultButAllowsItAsAnExplicitTarget() {
    assertThatThrownBy(() -> TargetPlatformVersionPolicy.verifyConfiguredDefault("8.8"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("The configured default target platform version must be 8.9, but was 8.8");

    DefaultConverterProperties properties = new DefaultConverterProperties();
    properties.setPlatformVersion("8.8");

    assertThat(ConverterPropertiesFactory.getInstance().merge(properties).getPlatformVersion())
        .isEqualTo("8.8");
  }
}
