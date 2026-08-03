/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards dependency-selection wiring in declarative client recipes. */
class RecipeDependencyConfigTest {

  private static final List<String> PREPARE_RECIPE_DESCRIPTORS =
      List.of(
          "/META-INF/rewrite/clientRecipes.yml",
          "/META-INF/rewrite/delegateRecipes.yml",
          "/META-INF/rewrite/externalWorkerRecipes.yml");

  @Test
  void clientRecipesUseDynamicStarterSelection() {
    String descriptor = resourceText("/META-INF/rewrite/clientRecipes.yml");

    assertThat(descriptor)
        .contains("io.camunda.migration.code.recipes.client.ConfigureCamundaStarterRecipe");
    assertThat(descriptor)
        .contains("io.camunda.migration.code.recipes.client.ConfigureCamundaProcessTestDependencyRecipe");
    assertThat(descriptor)
        .contains("camunda-spring-boot-3-starter")
        .contains("camunda-spring-boot-starter");
    assertThat(descriptor)
        .contains("camunda-process-test-spring-boot-3")
        .contains("camunda-process-test-spring");
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

  private static InputStream resourceStream(String resource) {
    InputStream in = RecipeDependencyConfigTest.class.getResourceAsStream(resource);
    assertThat(in).as("recipe descriptor %s must be on the classpath", resource).isNotNull();
    return in;
  }
}
