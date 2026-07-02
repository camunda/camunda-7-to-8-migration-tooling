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

/**
 * Guards the dependency configuration of the prepare recipes.
 *
 * <p>Since Camunda 8.9 the Spring Boot starter is split: {@code camunda-spring-boot-starter}
 * requires Spring Boot 4.0.x while {@code camunda-spring-boot-3-starter} targets Spring Boot 3.5.x.
 * They are mutually exclusive, so a recipe must add exactly one of them - adding both would put two
 * Spring Boot generations on a migrated project's classpath. This test fails fast if that
 * regression is reintroduced.
 */
class RecipeDependencyConfigTest {

  private static final List<String> PREPARE_RECIPE_DESCRIPTORS =
      List.of(
          "/META-INF/rewrite/clientRecipes.yml",
          "/META-INF/rewrite/delegateRecipes.yml",
          "/META-INF/rewrite/externalWorkerRecipes.yml");

  @Test
  void addsExactlyOneSpringBootStarterAndItIsTheSpringBoot3One() {
    for (String descriptor : PREPARE_RECIPE_DESCRIPTORS) {
      List<String> artifactIds = artifactIdsOf(descriptor);

      assertThat(artifactIds)
          .as("Spring Boot 3 starter must be added by %s", descriptor)
          .contains("camunda-spring-boot-3-starter");

      assertThat(artifactIds)
          .as(
              "%s must not add a second, conflicting Spring Boot starter alongside the Spring Boot 3 one",
              descriptor)
          .doesNotContain("camunda-spring-boot-starter", "camunda-spring-boot-4-starter");

      assertThat(artifactIds.stream().filter(id -> id.endsWith("-starter")).toList())
          .as("exactly one Camunda Spring Boot starter expected in %s", descriptor)
          .containsExactly("camunda-spring-boot-3-starter");
    }
  }

  @Test
  void clientRecipeUsesTheSpringBoot3ProcessTestModule() {
    List<String> artifactIds = artifactIdsOf("/META-INF/rewrite/clientRecipes.yml");

    assertThat(artifactIds).contains("camunda-process-test-spring-boot-3");
    assertThat(artifactIds)
        .as("process-test module must match the Spring Boot 3 starter, not the SB4 default")
        .doesNotContain("camunda-process-test-spring", "camunda-process-test-spring-boot-4");
  }

  /** Collects every {@code artifactId:} value declared in the given recipe descriptor resource. */
  private static List<String> artifactIdsOf(String resource) {
    List<String> artifactIds = new ArrayList<>();
    try (InputStream in = RecipeDependencyConfigTest.class.getResourceAsStream(resource)) {
      assertThat(in).as("recipe descriptor %s must be on the classpath", resource).isNotNull();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (trimmed.startsWith("artifactId:")) {
            artifactIds.add(trimmed.substring("artifactId:".length()).trim());
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read recipe descriptor " + resource, e);
    }
    return artifactIds;
  }
}
