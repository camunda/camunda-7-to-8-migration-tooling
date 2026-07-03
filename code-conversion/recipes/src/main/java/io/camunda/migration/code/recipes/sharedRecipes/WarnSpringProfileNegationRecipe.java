/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.sharedRecipes;

import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

/**
 * Detects Spring {@code @Profile} annotations using negation syntax (e.g. {@code @Profile("!test")})
 * and adds a TODO comment before each occurrence.
 *
 * <p>OpenRewrite's Java printer cannot round-trip {@code @Profile("!...")} correctly — the string
 * literal is corrupted during printing, causing the print-idempotency safety check to fail. As a
 * result, OpenRewrite silently excludes the entire file from all recipe rewrites. This recipe
 * operates at the plain-text level (bypassing the Java parser) so it can still process affected
 * files and leave an actionable TODO for manual migration.
 *
 * @see <a href="https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1548">Issue #1548</a>
 */
public class WarnSpringProfileNegationRecipe extends Recipe {

  static final String MARKER =
      "Manual migration required - @Profile annotation with negation syntax";

  private static final String ISSUE_LINK =
      "https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1548";

  private static final Pattern PROFILE_NEGATION_PATTERN =
      Pattern.compile("@Profile[ \\t]*\\([ \\t]*\"!");

  @Override
  public @NonNull String getDisplayName() {
    return "Warn about Spring @Profile annotation with negation syntax";
  }

  @Override
  public @NonNull String getDescription() {
    return "Adds a TODO comment before Spring @Profile annotations that use negation syntax "
        + "(e.g., @Profile(\"!test\")). OpenRewrite's Java printer corrupts these annotations, "
        + "causing the file to be silently skipped during migration. "
        + "This recipe detects the pattern and prompts for manual migration.";
  }

  @Override
  public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
    return new PlainTextVisitor<>() {

      @Override
      public PlainText visitText(PlainText text, ExecutionContext ctx) {
        if (!text.getSourcePath().toString().endsWith(".java")) {
          return text;
        }

        String content = text.getText();

        if (!PROFILE_NEGATION_PATTERN.matcher(content).find()) {
          return text;
        }

        String[] lines = content.split("\n", -1);
        StringBuilder result = new StringBuilder();
        boolean changed = false;

        for (int i = 0; i < lines.length; i++) {
          String line = lines[i];
          if (PROFILE_NEGATION_PATTERN.matcher(line).find() && !alreadyAnnotated(lines, i)) {
            String indent = leadingWhitespace(line);
            result
                .append(indent)
                .append("// TODO: ")
                .append(MARKER)
                .append(" (\"!...\").\n")
                .append(indent)
                .append(
                    "// OpenRewrite's Java printer corrupts this annotation and silently skips this file during migration.\n")
                .append(indent)
                .append("// Migrate this file manually. See: ")
                .append(ISSUE_LINK)
                .append("\n");
            changed = true;
          }
          result.append(line);
          if (i < lines.length - 1) {
            result.append("\n");
          }
        }

        return changed ? text.withText(result.toString()) : text;
      }

      private boolean alreadyAnnotated(String[] lines, int annotationIndex) {
        int todoLineCount = 3;
        for (int j = Math.max(0, annotationIndex - todoLineCount); j < annotationIndex; j++) {
          if (lines[j].contains(MARKER)) {
            return true;
          }
        }
        return false;
      }

      private String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
          i++;
        }
        return line.substring(0, i);
      }
    };
  }
}
