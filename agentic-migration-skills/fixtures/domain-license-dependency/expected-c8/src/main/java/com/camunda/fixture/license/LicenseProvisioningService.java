/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.license;

import java.util.HashSet;
import java.util.Set;
import org.camunda.bpm.licensefixture.LicenseGenerator.LicenseType;

public final class LicenseProvisioningService {
  private final LicenseService licenseService = new LicenseService();
  private final MembershipDirectory membershipDirectory = new MembershipDirectory();

  public String createLicense(final String customerId, final LicenseType type) {
    return generateAndUpdateMembership(customerId, type);
  }

  public String updateLicense(final String customerId, final LicenseType type) {
    return generateAndUpdateMembership(customerId, type);
  }

  public boolean hasMembership(final String customerId) {
    return membershipDirectory.hasMember(customerId);
  }

  private String generateAndUpdateMembership(final String customerId, final LicenseType type) {
    final String key = licenseService.generateUnifiedLicenseKey(type);
    membershipDirectory.updateMembership(customerId);
    return key;
  }

  private static final class MembershipDirectory {
    private final Set<String> members = new HashSet<>();

    private void updateMembership(final String customerId) {
      members.add(customerId);
    }

    private boolean hasMember(final String customerId) {
      return members.contains(customerId);
    }
  }
}
