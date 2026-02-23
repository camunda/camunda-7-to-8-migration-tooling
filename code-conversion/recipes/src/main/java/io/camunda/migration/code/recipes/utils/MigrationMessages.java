/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.utils;

/**
 * Centralized message templates for migration-related comments added to source code during recipe
 * execution.
 *
 * <p>These messages help developers identify code that requires manual review or migration after
 * automated recipes have been applied.
 *
 * <p>Pattern follows {@code EntityConversionServiceLogs} with format templates and helper methods.
 */
public final class MigrationMessages {

  private MigrationMessages() {
    // utility class
  }

  // Message templates using %s for String.format()
  static final String UNRESOLVED_RETURN_TYPE_MSG =
      "TODO: Manual migration required - could not resolve return type for: %s";

  /**
   * Creates a formatted TODO comment for unresolved return type.
   *
   * <p>This typically occurs with lambda parameters, method parameters, or variables initialized
   * from unrecognized sources.
   *
   * @param variableName the variable name that could not be resolved
   * @return formatted TODO comment
   */
  public static String formatUnresolvedReturnType(String variableName) {
    return String.format(UNRESOLVED_RETURN_TYPE_MSG, variableName);
  }

  /**
   * Checks if the given text contains a manual migration message for the specified variable.
   *
   * @param text the text to check (e.g., comment content)
   * @param variableName the variable name to look for
   * @return true if the text contains the manual migration message for this variable
   */
  public static boolean containsUnresolvedTypeMessage(String text, String variableName) {
    return text != null && text.contains(formatUnresolvedReturnType(variableName));
  }
}

