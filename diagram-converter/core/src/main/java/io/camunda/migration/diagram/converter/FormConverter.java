/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter;

import java.util.regex.Pattern;

public class FormConverter {

  static final String FILE_EXTENSION = ".form";
  static final String TARGET_EXECUTION_PLATFORM = "Camunda Cloud";
  static final String TARGET_EXECUTION_PLATFORM_VERSION = "8.9.0";

  private static final Pattern EXECUTION_PLATFORM_PATTERN =
      Pattern.compile("(\"executionPlatform\"\\s*:\\s*)\"[^\"]*\"");
  private static final Pattern EXECUTION_PLATFORM_VERSION_PATTERN =
      Pattern.compile("(\"executionPlatformVersion\"\\s*:\\s*)\"[^\"]*\"");

  private FormConverter() {}

  public static boolean isFormFile(String fileName) {
    return fileName != null && fileName.endsWith(FILE_EXTENSION);
  }

  public static String convert(String formContent) {
    String result =
        EXECUTION_PLATFORM_PATTERN
            .matcher(formContent)
            .replaceAll("$1\"" + TARGET_EXECUTION_PLATFORM + "\"");
    result =
        EXECUTION_PLATFORM_VERSION_PATTERN
            .matcher(result)
            .replaceAll("$1\"" + TARGET_EXECUTION_PLATFORM_VERSION + "\"");
    return result;
  }
}
