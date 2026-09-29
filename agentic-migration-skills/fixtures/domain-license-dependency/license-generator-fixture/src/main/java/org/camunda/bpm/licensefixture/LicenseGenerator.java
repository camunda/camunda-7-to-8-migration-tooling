/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.licensefixture;

public final class LicenseGenerator {
  public enum LicenseType {
    TYPE_A,
    TYPE_B
  }

  public String generateUnifiedLicenseKey(final LicenseType type) {
    return "fixture-result-" + type.name();
  }
}
