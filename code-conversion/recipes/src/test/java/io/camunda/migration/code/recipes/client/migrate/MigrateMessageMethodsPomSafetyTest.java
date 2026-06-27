/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.client.migrate;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

import io.camunda.migration.code.recipes.client.MigrateMessageMethodsRecipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class MigrateMessageMethodsPomSafetyTest implements RewriteTest {

  /**
   * Regression test: the recipe runs over every source in a project, including {@code pom.xml}. Its
   * visitor previously cast every tree to a Java node, throwing a {@code ClassCastException} on the
   * XML pom document of every migrated project. The pom (and any non-correlateMessage Java) must
   * pass through untouched without error.
   */
  @Test
  void leavesNonJavaAndUnrelatedJavaSourcesUntouched() {
    rewriteRun(
        spec -> spec.recipe(new MigrateMessageMethodsRecipe()),
        pomXml(
            """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>demo</artifactId>
              <version>1.0.0</version>
            </project>
            """),
        // language=java
        java(
            """
            package com.example;

            public class NoCorrelateMessage {
              void run() {
                System.out.println("nothing to migrate here");
              }
            }
            """));
  }
}
