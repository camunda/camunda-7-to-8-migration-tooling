/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.identity;

import java.util.List;

public class AuthorizationMappingResult {

  protected final boolean success;
  protected final List<C8Authorization> c8authorizations;
  protected final String reason;

  protected AuthorizationMappingResult(boolean success, List<C8Authorization> c8authorizations, String reason) {
    this.success = success;
    this.c8authorizations = c8authorizations;
    this.reason = reason;
  }

  public static AuthorizationMappingResult success(List<C8Authorization> c8authorization) {
    return new AuthorizationMappingResult(true, c8authorization, null);
  }

  public static AuthorizationMappingResult failure(String reason) {
    return new AuthorizationMappingResult(false, null, reason);
  }

  public boolean isSuccess() {
    return success;
  }

  public List<C8Authorization> getC8Authorizations() {
    return c8authorizations;
  }

  public String getReason() {
    return reason;
  }

  public boolean isSingleAuth() {
    return c8authorizations.size() == 1;
  }
}