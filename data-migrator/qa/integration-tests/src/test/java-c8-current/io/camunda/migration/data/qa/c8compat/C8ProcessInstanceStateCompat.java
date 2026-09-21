/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.c8compat;

import io.camunda.client.api.search.enums.ProcessInstanceState;

/** Version-specific process instance state helpers for the current Camunda 8 version. */
public final class C8ProcessInstanceStateCompat {

  private C8ProcessInstanceStateCompat() {}

  public static boolean isActiveOrSuspended(ProcessInstanceState state) {
    return state == ProcessInstanceState.ACTIVE || state == ProcessInstanceState.SUSPENDED;
  }
}
