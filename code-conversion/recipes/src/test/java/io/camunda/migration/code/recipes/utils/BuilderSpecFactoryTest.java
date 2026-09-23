/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the comment-generation logic in {@link BuilderSpecFactory#createRemovedComments}.
 *
 * <p>The method has one special case: {@code businessKey} in the createProcessInstance builder
 * should emit a businessId hint plus a reminder to migrate any BPMN call-activity propagation,
 * while {@code processInstanceBusinessKey} in the correlateMessage builder must stay neutral
 * (businessId is not a replacement for message correlation keys).
 */
class BuilderSpecFactoryTest {

  @Test
  void businessKeyGetsBusinessIdHintAndCallActivityReminder() {
    List<String> comments = BuilderSpecFactory.createRemovedComments("businessKey");

    assertThat(comments).hasSize(2);
    assertThat(comments.get(0))
        .as("businessKey on the create-instance path should point to businessId")
        .contains("businessId")
        .contains("TODO");
    assertThat(comments.get(1))
        .as("businessKey should also flag call-activity propagation for the diagram converter")
        .contains("call activity");
  }

  @Test
  void processInstanceBusinessKeyKeepsNeutralComment() {
    List<String> comments = BuilderSpecFactory.createRemovedComments("processInstanceBusinessKey");

    assertThat(comments).hasSize(1);
    assertThat(comments.get(0))
        .as("processInstanceBusinessKey is on the correlate-message path; businessId does not apply")
        .contains("processInstanceBusinessKey")
        .contains("was removed")
        .doesNotContain("businessId");
  }

  @Test
  void otherRemovedMethodsGetGenericComment() {
    List<String> comments = BuilderSpecFactory.createRemovedComments("someOtherMethod");

    assertThat(comments).containsExactly(" someOtherMethod was removed");
  }
}
