/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.config.property.history;

import java.time.Duration;
import java.time.Period;

/**
 * Configuration properties for auto-cancel cleanup behavior.
 */
public class CleanupProperties {

  public static Period DEFAULT_TTL = Period.ofDays(180); // 6 months default

  /**
   * Whether to populate cleanup dates for auto-canceled entities.
   * When false, history cleanup dates will be set to null for auto-canceled instances.
   * Default is true.
   */
  protected boolean enabled = true;

  /**
   * Time-to-live duration for auto-canceled active history records.
   * Default is 6 months (P180D).
   * This value is only used when cleanup is enabled.
   */
  protected Period ttl = DEFAULT_TTL;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Period getTtl() {
    return ttl;
  }

  public void setTtl(Period ttl) {
    this.ttl = ttl;
  }
}
