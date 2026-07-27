/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.architecturefixture;

import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

public class InvalidArchitectureFixtures {

  public static class InvalidVisibility {

    private String mutableState;

    private InvalidVisibility() {
    }

    private void mutate() {
      mutableState = "changed";
    }
  }

  @Component
  public static class MisplacedComponent {
  }

  @Configuration
  public static class MisplacedConfiguration {
  }

  public static class SystemOutAccess {

    public void print() {
      System.out.println("invalid");
    }
  }

  public static class PrintStackTraceCall {

    public void print(Throwable throwable) {
      throwable.printStackTrace();
    }
  }
}
