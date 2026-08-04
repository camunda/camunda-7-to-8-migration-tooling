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
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.gradle.trait.GradleDependency;
import org.openrewrite.gradle.trait.GradlePlugin;
import org.openrewrite.gradle.trait.SpringDependencyManagementPluginEntry;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.marker.SearchResult;

/**
 * Matches Gradle build files that resolve a specific Spring Boot major version.
 *
 * <p>The resolved Spring Boot platform is authoritative. When it is not available, the recipe
 * falls back to the version declared by the Spring Boot plugin and then to a
 * {@code dependencyManagement} BOM entry.
 */
public class FindSpringBootMajorRecipe extends Recipe {

  private static final String SPRING_BOOT_GROUP = "org.springframework.boot";
  private static final String SPRING_BOOT_PLATFORM = "spring-boot-dependencies";
  private static final String SPRING_BOOT_PLUGIN = "org.springframework.boot";
  private static final String SPRING_BOOT_GRADLE_PLUGIN = "spring-boot-gradle-plugin";
  private static final String SPRING_BOOT_PLUGIN_MARKER = "org.springframework.boot.gradle.plugin";
  private static final String NO_DETECTED_MAJOR = "none";

  private final String major;

  public FindSpringBootMajorRecipe(String major) {
    this.major = major;
  }

  @Override
  public @NonNull String getDisplayName() {
    return "Find a Gradle project using a Spring Boot major version";
  }

  @Override
  public @NonNull String getDescription() {
    return "Matches Gradle build files whose resolved Spring Boot platform, Spring Boot plugin, or "
        + "Spring dependency management BOM uses the requested major version.";
  }

  @Override
  public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaVisitor<>() {
      @Override
      public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
        if (tree instanceof J j && j instanceof SourceFile sourceFile) {
          GradleProject gradleProject =
              sourceFile.getMarkers().findFirst(GradleProject.class).orElse(null);
          if (matchesMajor(sourceFile, gradleProject)) {
            return SearchResult.found(j);
          }
        }
        return super.visit(tree, ctx);
      }
    };
  }

  private boolean matchesMajor(SourceFile sourceFile, @Nullable GradleProject gradleProject) {
    Optional<String> detectedMajor = detectMajor(sourceFile, gradleProject);
    if (NO_DETECTED_MAJOR.equals(major)) {
      return detectedMajor.isEmpty();
    }
    return detectedMajor.filter(major::equals).isPresent();
  }

  private Optional<String> detectMajor(
      SourceFile sourceFile, @Nullable GradleProject gradleProject) {
    if (gradleProject != null) {
      Optional<String> resolvedPlatformMajor = resolvedPlatformMajor(gradleProject);
      if (resolvedPlatformMajor.isPresent()) {
        return resolvedPlatformMajor;
      }
    }

    Optional<String> declaredPlatformMajor = declaredPlatformMajor(sourceFile);
    if (declaredPlatformMajor.isPresent()) {
      return declaredPlatformMajor;
    }

    Optional<String> pluginMajor = pluginMajor(sourceFile);
    if (pluginMajor.isPresent()) {
      return pluginMajor;
    }

    if (gradleProject != null) {
      Optional<String> resolvedPluginMajor = resolvedPluginMajor(gradleProject);
      if (resolvedPluginMajor.isPresent()) {
        return resolvedPluginMajor;
      }
    }

    return dependencyManagementMajor(sourceFile);
  }

  private Optional<String> declaredPlatformMajor(SourceFile sourceFile) {
    AtomicReference<Set<String>> majors = new AtomicReference<>(new HashSet<>());
    new GradleDependency.Matcher()
        .groupId(SPRING_BOOT_GROUP)
        .artifactId(SPRING_BOOT_PLATFORM)
        .asVisitor(
            (GradleDependency dependency, AtomicReference<Set<String>> foundMajors) -> {
              addMajor(foundMajors.get(), dependency.getDeclaredVersion());
              return dependency.getTree();
            })
        .reduce(sourceFile, majors);
    return singleMajor(majors.get());
  }

  private Optional<String> resolvedPlatformMajor(GradleProject gradleProject) {
    Set<String> majors = new HashSet<>();
    addResolvedMajors(gradleProject.getConfigurations(), majors, false);
    return singleMajor(majors);
  }

  private Optional<String> resolvedPluginMajor(GradleProject gradleProject) {
    Set<String> majors = new HashSet<>();
    addResolvedMajors(gradleProject.getBuildscript().getConfigurations(), majors, true);
    return singleMajor(majors);
  }

  private void addResolvedMajors(
      Iterable<GradleDependencyConfiguration> configurations,
      Set<String> majors,
      boolean pluginArtifacts) {
    for (GradleDependencyConfiguration configuration : configurations) {
      if (!configuration.isCanBeResolved()) {
        continue;
      }
      for (ResolvedDependency dependency : configuration.getResolved()) {
        if (isSpringBootDependency(dependency, pluginArtifacts)) {
          addMajor(majors, dependency.getVersion());
        }
      }
    }
  }

  private Optional<String> pluginMajor(SourceFile sourceFile) {
    AtomicReference<Set<String>> majors = new AtomicReference<>(new HashSet<>());
    new GradlePlugin.Matcher()
        .pluginIdPattern(SPRING_BOOT_PLUGIN)
        .asVisitor(
            (GradlePlugin plugin, AtomicReference<Set<String>> foundMajors) -> {
              addMajor(foundMajors.get(), plugin.getVersion());
              return plugin.getTree();
            })
        .reduce(sourceFile, majors);
    return singleMajor(majors.get());
  }

  private Optional<String> dependencyManagementMajor(SourceFile sourceFile) {
    AtomicReference<Set<String>> majors = new AtomicReference<>(new HashSet<>());
    new SpringDependencyManagementPluginEntry.Matcher()
        .groupId(SPRING_BOOT_GROUP)
        .artifactId(SPRING_BOOT_PLATFORM)
        .asVisitor(
            (SpringDependencyManagementPluginEntry entry,
                AtomicReference<Set<String>> foundMajors) -> {
              addMajor(foundMajors.get(), entry.getVersion());
              return entry.getTree();
            })
        .reduce(sourceFile, majors);
    return singleMajor(majors.get());
  }

  private static boolean isSpringBootDependency(
      ResolvedDependency dependency, boolean pluginArtifacts) {
    if (!SPRING_BOOT_GROUP.equals(dependency.getGroupId())) {
      return false;
    }
    if (pluginArtifacts) {
      return SPRING_BOOT_GRADLE_PLUGIN.equals(dependency.getArtifactId())
          || SPRING_BOOT_PLUGIN_MARKER.equals(dependency.getArtifactId());
    }
    return SPRING_BOOT_PLATFORM.equals(dependency.getArtifactId());
  }

  private static void addMajor(Set<String> majors, @Nullable String version) {
    if (version == null || version.isBlank()) {
      return;
    }
    int separator = version.indexOf('.');
    majors.add(separator < 0 ? version : version.substring(0, separator));
  }

  private static Optional<String> singleMajor(Set<String> majors) {
    return majors.size() == 1 ? Optional.of(majors.iterator().next()) : Optional.empty();
  }
}
