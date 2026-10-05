/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed to Camunda Services GmbH under the Camunda License 1.0. You may not use
 * this file except in compliance with the Camunda License 1.0.
 */
package com.camunda.fixture.order;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class MockitoAnnotationTest {

  @Spy private Collaborator collaborator = new Collaborator();
  @Captor private ArgumentCaptor<String> orderIdCaptor;
  @InjectMocks private CollaboratorService collaboratorService;

  private AutoCloseable mocks;

  @Before
  public void openMocks() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void closeMocks() throws Exception {
    mocks.close();
  }

  @Test
  public void initializesMockitoAnnotationsForPlainUnitTest() {
    when(collaborator.lookup("order-42")).thenReturn("ready");

    assertEquals("ready", collaboratorService.lookup("order-42"));
    verify(collaborator).lookup(orderIdCaptor.capture());
    assertEquals("order-42", orderIdCaptor.getValue());
  }

  static class Collaborator {
    String lookup(String orderId) {
      return "not-stubbed";
    }
  }

  static class CollaboratorService {
    private Collaborator collaborator;

    String lookup(String orderId) {
      return collaborator.lookup(orderId);
    }
  }
}
