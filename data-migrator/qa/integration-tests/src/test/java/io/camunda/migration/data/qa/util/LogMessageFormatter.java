/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

/**
 * Utility class for formatting SLF4J-style log messages in tests.
 */
public class LogMessageFormatter {

  /**
   * Helper method to format SLF4J-style log messages by replacing {} placeholders with actual values.
   */
  public static String formatMessage(String template, Object... args) {
    String result = template;
    for (Object arg : args) {
      result = result.replaceFirst("\\{}", String.valueOf(arg));
      result = result.replaceFirst("%s", String.valueOf(arg));
    }
    return result;
  }
}
