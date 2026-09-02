/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

public final class TargetPlatformVersionPolicy {
  public static final String LATEST_STABLE = "8.9";

  private TargetPlatformVersionPolicy() {}

  public static void validateDefault(String platformVersion) {
    if (!LATEST_STABLE.equals(platformVersion)) {
      throw new IllegalStateException(
          "The default target platform version must be "
              + LATEST_STABLE
              + " but was "
              + platformVersion);
    }
  }
}
