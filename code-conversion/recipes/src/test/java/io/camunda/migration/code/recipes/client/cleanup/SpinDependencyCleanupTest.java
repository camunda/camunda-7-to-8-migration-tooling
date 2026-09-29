/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.cleanup;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class SpinDependencyCleanupTest implements RewriteTest {

  @Test
  void removesSpinDependenciesWhenNoSpinTypesRemain() {
    rewriteRun(
        spec -> spec.recipeFromResources("io.camunda.migration.code.recipes.AllClientCleanupRecipes"),
        mavenProject(
            "spin-example",
            srcMainJava(
                java(
                    """
                    package org.camunda.community.migration.example;

                    class NoSpinUsage {}
                    """)),
            pomXml(
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>spin-example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.camunda.spin</groupId>
                            <artifactId>camunda-spin-core</artifactId>
                            <version>7.24.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.camunda.spin</groupId>
                            <artifactId>camunda-spin-dataformat-json-jackson</artifactId>
                            <version>7.24.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.camunda.bpm</groupId>
                            <artifactId>camunda-engine-plugin-spin</artifactId>
                            <version>7.24.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.commons</groupId>
                            <artifactId>commons-lang3</artifactId>
                            <version>3.20.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>spin-example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.commons</groupId>
                            <artifactId>commons-lang3</artifactId>
                            <version>3.20.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """)));
  }

  @Test
  void preservesSpinDependenciesWhileSpinTypesRemain() {
    rewriteRun(
        spec -> spec.recipeFromResources("io.camunda.migration.code.recipes.AllClientCleanupRecipes"),
        mavenProject(
            "spin-example",
            srcMainJava(
                java(
                    """
                    package org.camunda.community.migration.example;

                    import static org.camunda.spin.Spin.JSON;

                    import org.camunda.spin.json.SpinJsonNode;

                    class UsesSpin {
                        SpinJsonNode parse(String json) {
                            return JSON(json);
                        }
                    }
                    """)),
            pomXml(
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>spin-example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.camunda.spin</groupId>
                            <artifactId>camunda-spin-core</artifactId>
                            <version>7.24.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.camunda.spin</groupId>
                            <artifactId>camunda-spin-dataformat-json-jackson</artifactId>
                            <version>7.24.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.camunda.bpm</groupId>
                            <artifactId>camunda-engine-plugin-spin</artifactId>
                            <version>7.24.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """)));
  }

  @Test
  void removesDependenciesAfterRemovingUnusedSpinImports() {
    rewriteRun(
        spec ->
            spec.recipeFromResources("io.camunda.migration.code.recipes.AllClientCleanupRecipes")
                .cycles(2)
                .expectedCyclesThatMakeChanges(2),
        mavenProject(
            "spin-example",
            srcMainJava(
                java(
                    """
                    package org.camunda.community.migration.example;

                    import org.camunda.spin.json.SpinJsonNode;

                    class UnusedSpinImport {}
                    """,
                    """
                    package org.camunda.community.migration.example;

                    class UnusedSpinImport {}
                    """)),
            pomXml(
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>spin-example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.camunda.spin</groupId>
                            <artifactId>camunda-spin-core</artifactId>
                            <version>7.24.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>spin-example</artifactId>
                    <version>1.0.0</version>
                </project>
                """)));
  }
}
