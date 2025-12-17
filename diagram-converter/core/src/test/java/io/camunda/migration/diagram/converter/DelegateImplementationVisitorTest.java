/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import static io.camunda.migration.diagram.converter.visitor.AbstractDelegateImplementationVisitor.*;
import static org.assertj.core.api.Assertions.*;

import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

public class DelegateImplementationVisitorTest {
  @Test
  void shouldReturnDelegateNameWithDollar() {
    String delegateExpression = "${someReallyCoolDelegate}";
    Matcher matcher = DELEGATE_NAME_EXTRACT.matcher(delegateExpression);
    String delegateName = matcher.find() ? matcher.group(1) : null;
    assertThat(delegateName).isNotNull().isEqualTo("someReallyCoolDelegate");
  }

  @Test
  void shouldReturnDelegateNameWithHashtag() {
    String delegateExpression = "#{someReallyCoolDelegate}";
    Matcher matcher = DELEGATE_NAME_EXTRACT.matcher(delegateExpression);
    String delegateName = matcher.find() ? matcher.group(1) : null;
    assertThat(delegateName).isNotNull().isEqualTo("someReallyCoolDelegate");
  }
}
