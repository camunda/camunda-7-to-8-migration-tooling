/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Guards the comment-generation logic in {@link BuilderSpecFactory#createRemovedComment}.
 *
 * <p>The method has one special case: {@code businessKey} in the createProcessInstance builder
 * should emit a businessId hint, while {@code processInstanceBusinessKey} in the
 * correlateMessage builder must stay neutral (businessId is not a replacement for message
 * correlation keys).
 */
class BuilderSpecFactoryTest {

  @Test
  void businessKeyGetsBusinessIdHint() {
    String comment = BuilderSpecFactory.createRemovedComment("businessKey");

    assertThat(comment)
        .as("businessKey on the create-instance path should point to businessId")
        .contains("businessId")
        .contains("TODO");
  }

  @Test
  void processInstanceBusinessKeyKeepsNeutralComment() {
    String comment = BuilderSpecFactory.createRemovedComment("processInstanceBusinessKey");

    assertThat(comment)
        .as("processInstanceBusinessKey is on the correlate-message path; businessId does not apply")
        .contains("processInstanceBusinessKey")
        .contains("was removed")
        .doesNotContain("businessId");
  }

  @Test
  void otherRemovedMethodsGetGenericComment() {
    String comment = BuilderSpecFactory.createRemovedComment("someOtherMethod");

    assertThat(comment).isEqualTo(" someOtherMethod was removed");
  }
}
