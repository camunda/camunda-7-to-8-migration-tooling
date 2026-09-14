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
  static final String QUERY_RESULT_COUNT_MSG =
      "TODO: Manual migration required - use page().totalItems() for the complete query count of: %s. Check page().hasMoreTotalItems() because totalItems() can be a lower bound.";
  static final String TOTAL_ITEMS_CAP_MSG =
      "TODO: Manual migration required - check page().hasMoreTotalItems(); when true, totalItems() is only a lower bound.";
  static final String UNFILTERED_PROCESS_INSTANCE_COUNT_MSG =
      "TODO: Manual migration required - preserve the unfiltered process-instance count, including suspended instances, before migration.";
  static final String DERIVED_QUERY_COUNT_MSG =
      "TODO: Manual migration required - preserve the complete query count before applying stream operations to the paginated result.";

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

  /**
   * Creates a formatted TODO comment for a count derived from a migrated query result variable.
   *
   * @param variableName the variable whose result count needs manual migration
   * @return formatted TODO comment
   */
  public static String formatQueryResultCount(String variableName) {
    return String.format(QUERY_RESULT_COUNT_MSG, variableName);
  }

  /**
   * Creates a formatted TODO comment for the capped total returned by a search page.
   *
   * @return formatted TODO comment
   */
  public static String formatTotalItemsCap() {
    return TOTAL_ITEMS_CAP_MSG;
  }

  /**
   * Creates a formatted TODO comment for an unfiltered process-instance count.
   *
   * @return formatted TODO comment
   */
  public static String formatUnfilteredProcessInstanceCount() {
    return UNFILTERED_PROCESS_INSTANCE_COUNT_MSG;
  }

  /**
   * Creates a formatted TODO comment for a count derived from a paginated query result.
   *
   * @return formatted TODO comment
   */
  public static String formatDerivedQueryCount() {
    return DERIVED_QUERY_COUNT_MSG;
  }
}
