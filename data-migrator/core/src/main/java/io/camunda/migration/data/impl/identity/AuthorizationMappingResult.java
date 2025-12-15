/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.identity;

public class AuthorizationMappingResult {

  protected final boolean success;
  protected final C8Authorization c8authorization;
  protected final String reason;

  protected AuthorizationMappingResult(boolean success, C8Authorization c8authorization, String reason) {
    this.success = success;
    this.c8authorization = c8authorization;
    this.reason = reason;
  }

  public static AuthorizationMappingResult success(C8Authorization c8authorization) {
    return new AuthorizationMappingResult(true, c8authorization, null);
  }

  public static AuthorizationMappingResult failure(String reason) {
    return new AuthorizationMappingResult(false, null, reason);
  }

  public boolean isSuccess() {
    return success;
  }

  public C8Authorization getC8Authorization() {
    return c8authorization;
  }

  public String getReason() {
    return reason;
  }
}