/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.config;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

/**
 * Removes every entry from a {@code .properties} file whose key equals {@code camunda.bpm} or starts
 * with {@code camunda.bpm.}.
 *
 * <p>The built-in {@code org.openrewrite.properties.DeleteProperty} only deletes a single, exactly
 * matched key. The Camunda 7 engine exposes a large and evolving set of {@code camunda.bpm.*}
 * configuration keys, so this recipe deletes the whole namespace by prefix instead of enumerating
 * every known key. That way keys we do not know about today (or that a project added) are still
 * removed during migration.
 */
public class RemoveCamundaBpmProperties extends Recipe {

  private static final String PREFIX = "camunda.bpm";

  /** Instantiates a new instance. */
  public RemoveCamundaBpmProperties() {}

  @Override
  public @NonNull String getDisplayName() {
    return "Remove all `camunda.bpm.*` properties";
  }

  @Override
  public @NonNull String getDescription() {
    return "Removes every property whose key equals `camunda.bpm` or starts with `camunda.bpm.`"
        + " from `.properties` files. These Camunda 7 process engine configuration keys have no"
        + " equivalent in Camunda 8 and are removed during migration.";
  }

  @Override
  public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
    return new PropertiesIsoVisitor<>() {
      @Override
      public Properties.File visitFile(Properties.File file, ExecutionContext ctx) {
        Properties.File f = super.visitFile(file, ctx);
        // Replace the first removed job-execution key with a single TODO comment (in place of the
        // whole namespace), keeping the migration signal; drop every other camunda.bpm.* entry.
        boolean[] jobExecutionHintEmitted = {false};
        return f.withContent(
            ListUtils.map(
                f.getContent(),
                content -> {
                  if (content instanceof Properties.Entry entry && matchesPrefix(entry.getKey())) {
                    if (!jobExecutionHintEmitted[0]
                        && entry.getKey().startsWith(ConfigMigrationHints.JOB_EXECUTION_KEY_PREFIX)) {
                      jobExecutionHintEmitted[0] = true;
                      return new Properties.Comment(
                          Tree.randomId(),
                          entry.getPrefix(),
                          Markers.EMPTY,
                          Properties.Comment.Delimiter.HASH_TAG,
                          " " + ConfigMigrationHints.JOB_EXECUTION);
                    }
                    return null;
                  }
                  return content;
                }));
      }
    };
  }

  private static boolean matchesPrefix(String key) {
    return key.equals(PREFIX) || key.startsWith(PREFIX + ".");
  }
}
