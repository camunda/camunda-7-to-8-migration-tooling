/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

/** Guards dependency-selection wiring in declarative client recipes. */
class RecipeDependencyConfigTest implements RewriteTest {

  private static final List<String> PREPARE_RECIPE_DESCRIPTORS =
      List.of(
          "/META-INF/rewrite/clientRecipes.yml",
          "/META-INF/rewrite/delegateRecipes.yml",
          "/META-INF/rewrite/externalWorkerRecipes.yml");

  @Test
  void clientRecipeUsesSpringBoot3DependenciesForSpringBoot3Project() {
    rewriteRun(
        spec ->
            spec.recipeFromResources(
                "io.camunda.migration.code.recipes.client.ConfigureCamundaStarterRecipe",
                "io.camunda.migration.code.recipes.client.ConfigureCamundaProcessTestDependencyRecipe")
                .expectedCyclesThatMakeChanges(1),
        pomXml(
            """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>demo</artifactId>
              <version>1.0.0</version>
              <parent>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-parent</artifactId>
                <version>3.5.4</version>
              </parent>
            </project>
            """,
            spec ->
                spec.path("pom.xml")
                    .after(
                        rewrittenPom -> {
                          assertThat(rewrittenPom).contains("<artifactId>camunda-spring-boot-3-starter</artifactId>");
                          assertThat(rewrittenPom).contains("<artifactId>camunda-process-test-spring-boot-3</artifactId>");
                          assertThat(rewrittenPom).doesNotContain("<artifactId>camunda-spring-boot-starter</artifactId>");
                          assertThat(rewrittenPom).doesNotContain("<artifactId>camunda-process-test-spring</artifactId>");
                          return rewrittenPom;
                        })));
  }

  @Test
  void clientRecipeUsesSpringBoot4DependenciesForSpringBoot4Project() {
    assertUsesSpringBoot4Dependencies(
        """
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example</groupId>
          <artifactId>demo</artifactId>
          <version>1.0.0</version>
          <parent>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-parent</artifactId>
            <version>4.0.0</version>
          </parent>
        </project>
        """);
  }

  @Test
  void clientRecipeUsesSpringBoot4DependenciesWhenBoot4BomIsUsed() {
    assertUsesSpringBoot4Dependencies(
        """
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example</groupId>
          <artifactId>demo</artifactId>
          <version>1.0.0</version>
          <dependencyManagement>
            <dependencies>
              <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>4.0.0</version>
                <type>pom</type>
                <scope>import</scope>
              </dependency>
            </dependencies>
          </dependencyManagement>
        </project>
        """);
  }

  @Test
  void clientRecipeUsesSpringBoot4DependenciesWhenBoot4PropertyIsUsed() {
    assertUsesSpringBoot4Dependencies(
        """
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.example</groupId>
          <artifactId>demo</artifactId>
          <version>1.0.0</version>
          <properties>
            <spring-boot.version>4.0.0</spring-boot.version>
          </properties>
        </project>
        """);
  }

  @Test
  void clientRecipesDeclareGradleBoot4SelectorBranches() {
    String descriptor = resourceText("/META-INF/rewrite/clientRecipes.yml");

    assertThat(descriptor)
        .contains("UseSpringBoot4CamundaStarterWhenGradlePluginIsBoot4")
        .contains("UseSpringBoot4CamundaStarterWhenGradleBootBomIsBoot4")
        .contains("UseSpringBoot4CamundaStarterWhenGradleBootPropertyIsBoot4")
        .contains("UseSpringBoot4ProcessTestWhenGradlePluginIsBoot4")
        .contains("UseSpringBoot4ProcessTestWhenGradleBootBomIsBoot4")
        .contains("UseSpringBoot4ProcessTestWhenGradleBootPropertyIsBoot4")
        .contains("filePattern: \"**/build.gradle*\"")
        .contains("filePattern: \"**/gradle.properties\"");
  }

  @Test
  void clientRecipeUsesSpringBoot4DependenciesWhenGradlePluginIsBoot4() {
    rewriteRun(
        spec ->
            spec.recipeFromResources(
                "io.camunda.migration.code.recipes.client.ConfigureCamundaStarterRecipe",
                "io.camunda.migration.code.recipes.client.ConfigureCamundaProcessTestDependencyRecipe"),
        text(
            """
            plugins {
              id("org.springframework.boot") version "4.0.0"
            }

            dependencies {
              implementation("io.camunda:camunda-spring-boot-3-starter:8.10.0-SNAPSHOT")
              testImplementation("io.camunda:camunda-process-test-spring-boot-3:8.10.0-SNAPSHOT")
            }
            """,
            spec ->
                spec.path("build.gradle.kts")
                    .after(
                    rewrittenBuild -> {
                      assertThat(rewrittenBuild)
                          .contains("camunda-spring-boot-starter")
                          .contains("camunda-process-test-spring");
                      assertThat(rewrittenBuild)
                          .doesNotContain("camunda-spring-boot-3-starter")
                          .doesNotContain("camunda-process-test-spring-boot-3");
                      return rewrittenBuild;
                    })));
  }

  @Test
  void clientRecipeUsesSpringBoot4DependenciesWhenGradleBootBomIsBoot4() {
    rewriteRun(
        spec ->
            spec.recipeFromResources(
                "io.camunda.migration.code.recipes.client.ConfigureCamundaStarterRecipe",
                "io.camunda.migration.code.recipes.client.ConfigureCamundaProcessTestDependencyRecipe"),
        text(
            """
            dependencies {
              implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.0"))
              implementation("io.camunda:camunda-spring-boot-3-starter:8.10.0-SNAPSHOT")
              testImplementation("io.camunda:camunda-process-test-spring-boot-3:8.10.0-SNAPSHOT")
            }
            """,
            spec ->
                spec.path("build.gradle.kts")
                    .after(
                    rewrittenBuild -> {
                      assertThat(rewrittenBuild)
                          .contains("camunda-spring-boot-starter")
                          .contains("camunda-process-test-spring");
                      assertThat(rewrittenBuild)
                          .doesNotContain("camunda-spring-boot-3-starter")
                          .doesNotContain("camunda-process-test-spring-boot-3");
                      return rewrittenBuild;
                    })));
  }

  @Test
  void clientTopLevelRecipesStayWiredToSelectorRecipes() {
    String descriptor = resourceText("/META-INF/rewrite/clientRecipes.yml");

    assertThat(recipeSection(descriptor, "io.camunda.migration.code.recipes.AllClientPrepareRecipes"))
        .contains("- io.camunda.migration.code.recipes.client.ConfigureCamundaStarterRecipe")
        .doesNotContain("org.openrewrite.java.dependencies.AddDependency")
        .doesNotContain("org.openrewrite.maven.AddDependency")
        .doesNotContain("org.openrewrite.gradle.AddDependency");

    assertThat(recipeSection(descriptor, "io.camunda.migration.code.recipes.AllClientMigrateRecipes"))
        .contains("- io.camunda.migration.code.recipes.client.ConfigureCamundaProcessTestDependencyRecipe")
        .doesNotContain("artifactId: camunda-process-test-spring-boot-3")
        .doesNotContain("artifactId: camunda-process-test-spring")
        .doesNotContain("org.openrewrite.java.dependencies.AddDependency")
        .doesNotContain("org.openrewrite.maven.AddDependency")
        .doesNotContain("org.openrewrite.gradle.AddDependency");
  }

  @Test
  void nonClientPrepareRecipesStayOnSpringBoot3Starter() {
    for (String descriptor : PREPARE_RECIPE_DESCRIPTORS.stream().skip(1).toList()) {
      List<String> artifactIds = artifactIdsOf(descriptor);
      assertThat(artifactIds).contains("camunda-spring-boot-3-starter");
      assertThat(artifactIds).doesNotContain("camunda-spring-boot-starter");
    }
  }

  /** Collects every {@code artifactId:} value declared in the given recipe descriptor resource. */
  private static List<String> artifactIdsOf(String resource) {
    List<String> artifactIds = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(resourceStream(resource), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (trimmed.startsWith("artifactId:")) {
          artifactIds.add(trimmed.substring("artifactId:".length()).trim());
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read recipe descriptor " + resource, e);
    }
    return artifactIds;
  }

  private static InputStream resourceStream(String resource) {
    InputStream in = RecipeDependencyConfigTest.class.getResourceAsStream(resource);
    assertThat(in).as("recipe descriptor %s must be on the classpath", resource).isNotNull();
    return in;
  }

  private void assertUsesSpringBoot4Dependencies(String inputPom) {
    rewriteRun(
        spec ->
            spec.recipeFromResources(
                "io.camunda.migration.code.recipes.client.ConfigureCamundaStarterRecipe",
                "io.camunda.migration.code.recipes.client.ConfigureCamundaProcessTestDependencyRecipe")
                .expectedCyclesThatMakeChanges(2),
        pomXml(
            inputPom,
            spec ->
                spec.path("pom.xml")
                    .after(
                        rewrittenPom -> {
                          assertThat(rewrittenPom).contains("<artifactId>camunda-spring-boot-starter</artifactId>");
                          assertThat(rewrittenPom).contains("<artifactId>camunda-process-test-spring</artifactId>");
                          assertThat(rewrittenPom).doesNotContain("<artifactId>camunda-spring-boot-3-starter</artifactId>");
                          assertThat(rewrittenPom).doesNotContain("<artifactId>camunda-process-test-spring-boot-3</artifactId>");
                          return rewrittenPom;
                        })));
  }

  private static String resourceText(String resource) {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(resourceStream(resource), StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append('\n');
      }
      return sb.toString();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read recipe descriptor " + resource, e);
    }
  }

  private static String recipeSection(String descriptor, String recipeName) {
    String marker = "name: " + recipeName + '\n';
    int start = descriptor.indexOf(marker);
    assertThat(start).as("recipe %s must exist", recipeName).isNotEqualTo(-1);
    int end = descriptor.indexOf("\n---\n", start);
    if (end == -1) {
      end = descriptor.length();
    }
    return descriptor.substring(start, end);
  }
}
