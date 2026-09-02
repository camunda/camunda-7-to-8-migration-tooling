/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

final class TargetPlatformVersionPolicy {
  // Update this value together with the converter property and frontend version catalog, then
  // backport that same change to every maintenance branch.
  static final String LATEST_STABLE_VERSION = "8.9";

  private TargetPlatformVersionPolicy() {}

  static void verifyConfiguredDefault(String platformVersion) {
    if (!LATEST_STABLE_VERSION.equals(platformVersion)) {
      throw new IllegalStateException(
          "The configured default target platform version must be "
              + LATEST_STABLE_VERSION
              + ", but was "
              + platformVersion);
    }
  }
}
