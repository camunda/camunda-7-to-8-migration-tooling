/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.camunda.bpm.licensefixture.LicenseGenerator.LicenseType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class LicenseProvisioningServiceTest {
  @ParameterizedTest
  @EnumSource(value = LicenseType.class, names = {"TYPE_A", "TYPE_B"})
  void createGeneratesEachLicenseTypeAndUpdatesMembership(final LicenseType type) {
    final LicenseProvisioningService service = new LicenseProvisioningService();

    final String result = service.createLicense("fixture-customer", type);

    assertEquals("fixture-result-" + type.name(), result);
    assertTrue(service.hasMembership("fixture-customer"));
  }

  @ParameterizedTest
  @EnumSource(value = LicenseType.class, names = {"TYPE_A", "TYPE_B"})
  void updateGeneratesEachLicenseTypeAndUpdatesMembership(final LicenseType type) {
    final LicenseProvisioningService service = new LicenseProvisioningService();

    final String result = service.updateLicense("fixture-customer", type);

    assertEquals("fixture-result-" + type.name(), result);
    assertTrue(service.hasMembership("fixture-customer"));
  }
}
