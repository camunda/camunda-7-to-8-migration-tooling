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
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

/**
 * Removes every YAML mapping entry whose flattened key path equals {@code camunda.bpm} or starts
 * with {@code camunda.bpm.}.
 *
 * <p>The built-in {@code org.openrewrite.yaml.DeleteKey} matches a single structural JSONPath, so it
 * only removes the nested {@code camunda: { bpm: ... }} form. Spring Boot also accepts dotted keys in
 * YAML (for example {@code camunda.bpm.admin-user.id: demo}) as well as mixed forms. This recipe
 * flattens each entry's path across dotted key segments so the whole {@code camunda.bpm.*} namespace
 * is removed regardless of how it is written.
 */
public class RemoveCamundaBpmYaml extends Recipe {

  private static final String PREFIX = "camunda.bpm";

  /** Instantiates a new instance. */
  public RemoveCamundaBpmYaml() {}

  @Override
  public @NonNull String getDisplayName() {
    return "Remove all `camunda.bpm.*` YAML keys";
  }

  @Override
  public @NonNull String getDescription() {
    return "Removes every YAML entry whose flattened key path equals `camunda.bpm` or starts with"
        + " `camunda.bpm.`, covering both the nested (`camunda: { bpm: ... }`) and dotted"
        + " (`camunda.bpm.admin-user.id`) forms. These Camunda 7 process engine configuration keys"
        + " have no equivalent in Camunda 8 and are removed during migration.";
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
                entry -> matchesPrefix(join(parentPath, entry.getKey().getValue())) ? null : entry));
      }
    };
  }

  /** Builds the dotted key path of the mapping currently on the cursor from its enclosing entries. */
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

  private static boolean matchesPrefix(String path) {
    return path.equals(PREFIX) || path.startsWith(PREFIX + ".");
  }
}
