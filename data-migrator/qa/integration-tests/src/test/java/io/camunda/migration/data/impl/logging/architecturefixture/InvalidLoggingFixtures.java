/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.logging.architecturefixture;

public class InvalidLoggingFixtures {

  public static class NonFinalLogs {

    public static String STATIC_MESSAGE = "mutable";
  }

  public static class NonStaticLogs {

    public String instanceMessage = "instance";
  }

  public static class NonPublicConstantsLogs {

    protected static final String MESSAGE = "hidden";
  }
}
