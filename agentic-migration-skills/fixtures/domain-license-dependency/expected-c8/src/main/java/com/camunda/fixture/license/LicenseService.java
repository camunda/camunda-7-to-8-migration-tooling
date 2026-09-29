/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.license;

import org.camunda.bpm.licensefixture.LicenseGenerator;
import org.camunda.bpm.licensefixture.LicenseGenerator.LicenseType;

public final class LicenseService {
  private final LicenseGenerator licenseGenerator = new LicenseGenerator();

  public String generateUnifiedLicenseKey(final LicenseType type) {
    return licenseGenerator.generateUnifiedLicenseKey(type);
  }
}
