/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.sharedRecipes;

import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.trait.GradleDependency;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

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
    return new JavaIsoVisitor<>() {
      private final GradleDependency.Matcher matcher =
          new GradleDependency.Matcher().groupId(oldGroupId).artifactId(oldArtifactId);

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
        Optional<GradleDependency> dependency = matcher.get(getCursor());
        if (dependency.isEmpty()) {
          return visited;
        }

        GradleDependency updated =
            dependency
                .get()
                .withDeclaredGroupId(newGroupId)
                .withDeclaredArtifactId(newArtifactId)
                .withDeclaredVersion(newVersion);
        return updated.getTree();
      }
    };
  }
}
