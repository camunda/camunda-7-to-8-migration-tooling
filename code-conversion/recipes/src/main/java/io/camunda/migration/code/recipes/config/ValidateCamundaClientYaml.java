/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.config;

import java.util.ArrayDeque;
import java.util.Deque;
import org.jspecify.annotations.NonNull;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

/** Marks invalid or deprecated Camunda client settings in Spring Boot YAML files. */
public class ValidateCamundaClientYaml extends Recipe {

  /** Instantiates a Camunda client YAML validator. */
  public ValidateCamundaClientYaml() {}

  @Override
  public @NonNull String getDisplayName() {
    return "Validate Camunda client YAML settings";
  }

  @Override
  public @NonNull String getDescription() {
    return "Marks unsupported Camunda client modes and authentication property shapes, and marks"
        + " legacy Zeebe property aliases as deprecated.";
  }

  @Override
  public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
    return new YamlIsoVisitor<>() {
      @Override
      public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
        Yaml.Mapping m = super.visitMapping(mapping, ctx);
        String parentPath = ancestorPath(getCursor());
        return m.withEntries(
            ListUtils.map(
                m.getEntries(),
                entry -> {
                  String key = join(parentPath, entry.getKey().getValue());
                  if (!(entry.getValue() instanceof Yaml.Scalar value)) {
                    return CamundaClientConfigurationValidation.shapeFinding(key)
                        .map(message -> SearchResult.found(entry, message))
                        .orElse(entry);
                  }
                  return CamundaClientConfigurationValidation.finding(key, value.getValue())
                      .map(message -> SearchResult.found(entry, message))
                      .orElse(entry);
                }));
      }
    };
  }

  private static String ancestorPath(Cursor mappingCursor) {
    Deque<String> parts = new ArrayDeque<>();
    Cursor cursor = mappingCursor.getParent();
    while (cursor != null) {
      if (cursor.getValue() instanceof Yaml.Mapping.Entry entry) {
        parts.addFirst(entry.getKey().getValue());
      }
      cursor = cursor.getParent();
    }
    return String.join(".", parts);
  }

  private static String join(String parentPath, String key) {
    return parentPath.isEmpty() ? key : parentPath + "." + key;
  }
}
