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
 * <p>Detection uses the following precedence: resolved Spring Boot platform, declared platform,
 * declared Spring Boot plugin, resolved plugin artifacts, and finally a {@code dependencyManagement}
 * BOM entry. An ambiguous signal stops fallback and is not treated as a missing major.
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
    return "Matches Gradle build files whose resolved or declared Spring Boot platform, declared or "
        + "resolved Spring Boot plugin, or Spring dependency management BOM uses the requested major "
        + "version; `none` matches only when no Spring Boot signal can be detected.";
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
    MajorDetection detectedMajor = detectMajor(sourceFile, gradleProject);
    if (NO_DETECTED_MAJOR.equals(major)) {
      return !detectedMajor.hasSignal();
    }
    return detectedMajor.major().filter(major::equals).isPresent();
  }

  private MajorDetection detectMajor(
      SourceFile sourceFile, @Nullable GradleProject gradleProject) {
    if (gradleProject != null) {
      MajorDetection resolvedPlatformMajor = resolvedPlatformMajor(gradleProject);
      if (resolvedPlatformMajor.hasSignal()) {
        return resolvedPlatformMajor;
      }
    }

    MajorDetection declaredPlatformMajor = declaredPlatformMajor(sourceFile);
    if (declaredPlatformMajor.hasSignal()) {
      return declaredPlatformMajor;
    }

    MajorDetection pluginMajor = pluginMajor(sourceFile);
    if (pluginMajor.hasSignal()) {
      return pluginMajor;
    }

    if (gradleProject != null) {
      MajorDetection resolvedPluginMajor = resolvedPluginMajor(gradleProject);
      if (resolvedPluginMajor.hasSignal()) {
        return resolvedPluginMajor;
      }
    }

    return dependencyManagementMajor(sourceFile);
  }

  private MajorDetection declaredPlatformMajor(SourceFile sourceFile) {
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
    return majorDetection(majors.get());
  }

  private MajorDetection resolvedPlatformMajor(GradleProject gradleProject) {
    Set<String> majors = new HashSet<>();
    addResolvedMajors(gradleProject.getConfigurations(), majors, false);
    return majorDetection(majors);
  }

  private MajorDetection resolvedPluginMajor(GradleProject gradleProject) {
    Set<String> majors = new HashSet<>();
    addResolvedMajors(gradleProject.getBuildscript().getConfigurations(), majors, true);
    return majorDetection(majors);
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

  private MajorDetection pluginMajor(SourceFile sourceFile) {
    AtomicReference<Set<String>> majors = new AtomicReference<>(new HashSet<>());
    new GradlePlugin.Matcher()
        .pluginIdPattern(SPRING_BOOT_PLUGIN)
        .asVisitor(
            (GradlePlugin plugin, AtomicReference<Set<String>> foundMajors) -> {
              addMajor(foundMajors.get(), plugin.getVersion());
              return plugin.getTree();
            })
        .reduce(sourceFile, majors);
    return majorDetection(majors.get());
  }

  private MajorDetection dependencyManagementMajor(SourceFile sourceFile) {
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
    return majorDetection(majors.get());
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

  private static MajorDetection majorDetection(Set<String> majors) {
    return switch (majors.size()) {
      case 0 -> new MajorDetection(Optional.empty(), false);
      case 1 -> new MajorDetection(Optional.of(majors.iterator().next()), false);
      default -> new MajorDetection(Optional.empty(), true);
    };
  }

  private record MajorDetection(Optional<String> major, boolean ambiguous) {

    private boolean hasSignal() {
      return ambiguous || major.isPresent();
    }
  }
}
