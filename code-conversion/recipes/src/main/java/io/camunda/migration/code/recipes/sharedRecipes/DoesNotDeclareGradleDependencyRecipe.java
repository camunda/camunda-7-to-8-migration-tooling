/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.sharedRecipes;

import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.trait.GradleDependency;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/**
 * Matches a Gradle build file that does not declare a specific dependency.
 *
 * <p>The built-in Gradle absence search inspects resolved dependencies. Migration artifacts may be
 * unavailable in a user's configured repositories while the build is being migrated, so selection
 * must also recognize unresolved direct declarations in the build script itself.
 */
public class DoesNotDeclareGradleDependencyRecipe extends Recipe {

  private final String groupId;
  private final String artifactId;

  public DoesNotDeclareGradleDependencyRecipe(String groupId, String artifactId) {
    this.groupId = groupId;
    this.artifactId = artifactId;
  }

  @Override
  public @NonNull String getDisplayName() {
    return "Find a Gradle build without a declared dependency";
  }

  @Override
  public @NonNull String getDescription() {
    return "Matches Gradle build files that do not declare the requested direct dependency.";
  }

  @Override
  public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaVisitor<>() {
      @Override
      public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
        if (tree instanceof J j && j instanceof SourceFile sourceFile) {
          AtomicBoolean found = new AtomicBoolean();
          new GradleDependency.Matcher()
              .groupId(groupId)
              .artifactId(artifactId)
              .asVisitor(
                  (GradleDependency dependency, AtomicBoolean state) -> {
                    state.set(true);
                    return dependency.getTree();
                  })
              .reduce(sourceFile, found);
          if (!found.get()) {
            return SearchResult.found(j);
          }
        }
        return super.visit(tree, ctx);
      }
    };
  }
}
