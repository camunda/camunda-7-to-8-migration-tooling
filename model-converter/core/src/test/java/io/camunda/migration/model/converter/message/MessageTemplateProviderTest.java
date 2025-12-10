/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.model.converter.message;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class MessageTemplateProviderTest {
  private static final MessageTemplateProvider MESSAGE_TEMPLATE_PROVIDER =
      new MessageTemplateProvider();

  @Test
  void shouldFindExistingMessageTemplate() {
    MessageTemplate messageTemplate =
        MESSAGE_TEMPLATE_PROVIDER.getMessageTemplate("connector-hint");
    assertNotNull(messageTemplate);
  }

  @Test
  void shouldThrowIfNoTemplateCanBeFound() {
    assertThrows(
        IllegalStateException.class,
        () -> MESSAGE_TEMPLATE_PROVIDER.getMessageTemplate("non-existent"));
  }
}
