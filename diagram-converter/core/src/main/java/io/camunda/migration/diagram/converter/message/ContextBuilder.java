/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.message;

import java.util.HashMap;
import java.util.Map;

public class ContextBuilder {
  private final Map<String, String> context = new HashMap<>();

  static ContextBuilder builder() {
    return new ContextBuilder();
  }

  public ContextBuilder entry(String key, String value) {
    if (context.containsKey(key) && !context.get(key).equals(value)) {
      throw new IllegalStateException("Must not override entries while building context");
    }
    context.put(key, value == null ? "null" : value);
    return this;
  }

  public ContextBuilder context(Map<String, String> context) {
    context.forEach(this::entry);
    return this;
  }

  public Map<String, String> build() {
    return context;
  }
}
