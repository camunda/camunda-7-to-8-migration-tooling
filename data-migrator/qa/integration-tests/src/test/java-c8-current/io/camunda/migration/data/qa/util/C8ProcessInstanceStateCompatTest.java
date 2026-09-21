/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.migration.data.qa.c8compat.C8ProcessInstanceStateCompat;
import org.junit.jupiter.api.Test;

class C8ProcessInstanceStateCompatTest {

  @Test
  void shouldRecognizeCancellableProcessInstanceStates() {
    assertThat(C8ProcessInstanceStateCompat.isActiveOrSuspended(ProcessInstanceState.ACTIVE))
        .isTrue();
    assertThat(C8ProcessInstanceStateCompat.isActiveOrSuspended(ProcessInstanceState.SUSPENDED))
        .isTrue();
    assertThat(C8ProcessInstanceStateCompat.isActiveOrSuspended(ProcessInstanceState.COMPLETED))
        .isFalse();
  }
}
