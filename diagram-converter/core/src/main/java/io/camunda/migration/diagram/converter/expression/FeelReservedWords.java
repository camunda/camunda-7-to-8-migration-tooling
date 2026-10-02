/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.expression;

import java.util.Set;

public final class FeelReservedWords {
  private static final Set<String> RESERVED_WORDS =
      Set.of(
          "and",
          "else",
          "false",
          "function",
          "in",
          "null",
          "or",
          "return",
          "satisfies",
          "then",
          "true");

  private FeelReservedWords() {}

  public static boolean isReservedWord(String value) {
    return RESERVED_WORDS.contains(value);
  }
}
