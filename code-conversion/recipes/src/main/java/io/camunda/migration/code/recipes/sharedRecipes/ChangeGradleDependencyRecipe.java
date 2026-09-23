/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.sharedRecipes;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

/**
 * Changes Gradle dependency coordinates without requiring the target artifact to resolve first.
 *
 * <p>Gradle declarations can be migrated even when a Camunda snapshot is not present in the
 * repositories configured by a test or by a partially migrated project.
 */
public class ChangeGradleDependencyRecipe extends Recipe {

  private final String oldGroupId;
  private final String oldArtifactId;
  private final String newGroupId;
  private final String newArtifactId;
  private final String newVersion;

  public ChangeGradleDependencyRecipe(
      String oldGroupId,
      String oldArtifactId,
      String newGroupId,
      String newArtifactId,
      @Nullable String newVersion) {
    this.oldGroupId = oldGroupId;
    this.oldArtifactId = oldArtifactId;
    this.newGroupId = newGroupId;
    this.newArtifactId = newArtifactId;
    this.newVersion = newVersion;
  }

  @Override
  public @NonNull String getDisplayName() {
    return "Change a Gradle dependency declaration";
  }

  @Override
  public @NonNull String getDescription() {
    return "Changes a Gradle dependency declaration by its group, artifact, and optional version.";
  }

  @Override
  public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaVisitor<>() {
      private final GradleDependency.Matcher matcher =
          new GradleDependency.Matcher().groupId(oldGroupId).artifactId(oldArtifactId);
      private final GradleDependency.Matcher targetMatcher =
          new GradleDependency.Matcher().groupId(newGroupId).artifactId(newArtifactId);
      private final Set<String> seenTargets = new HashSet<>();

      @Override
      public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
        if (tree instanceof SourceFile) {
          seenTargets.clear();
        }
        return super.visit(tree, ctx);
      }

      @Override
      public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J visited = super.visitMethodInvocation(method, ctx);
        Optional<GradleDependency> dependency = matcher.get(getCursor());
        if (dependency.isEmpty()) {
          Optional<GradleDependency> target = targetMatcher.get(getCursor());
          if (target.isPresent()
              && !seenTargets.add(dependencyKey(target.get()))) {
            return new J.Empty(Tree.randomId(), Space.EMPTY, Markers.EMPTY);
          }
          return visited;
        }

        GradleDependency updated =
            dependency
                .get()
                .withDeclaredGroupId(newGroupId)
                .withDeclaredArtifactId(newArtifactId)
                .withDeclaredVersion(newVersion);
        if (!seenTargets.add(dependencyKey(updated))) {
          return new J.Empty(Tree.randomId(), Space.EMPTY, Markers.EMPTY);
        }
        return updated.getTree();
      }

      private String dependencyKey(GradleDependency dependency) {
        return dependency.getConfigurationName()
            + ":"
            + dependency.getDeclaredGroupId()
            + ":"
            + dependency.getDeclaredArtifactId();
      }
    };
  }
}
