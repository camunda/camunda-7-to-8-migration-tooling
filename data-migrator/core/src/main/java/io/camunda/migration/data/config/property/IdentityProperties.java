/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config.property;

public class IdentityProperties {

  protected Boolean skipUsers = false;
  protected Boolean skipGroups = false;
  protected IdentitySyncProperties sync = new IdentitySyncProperties();

  public Boolean getSkipGroups() {
    return skipGroups;
  }

  public void setSkipGroups(Boolean skipGroups) {
    this.skipGroups = skipGroups;
  }

  public Boolean getSkipUsers() {
    return skipUsers;
  }

  public void setSkipUsers(Boolean skipUsers) {
    this.skipUsers = skipUsers;
  }

  public IdentitySyncProperties getSync() {
    return sync;
  }

  public void setSync(IdentitySyncProperties sync) {
    this.sync = sync;
  }

}
