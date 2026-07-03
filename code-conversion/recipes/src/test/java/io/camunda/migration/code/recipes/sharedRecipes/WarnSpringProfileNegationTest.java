/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes.sharedRecipes;

import static org.openrewrite.test.SourceSpecs.text;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class WarnSpringProfileNegationTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new WarnSpringProfileNegationRecipe());
  }

  @Test
  void addsCommentBeforeProfileNegationAnnotation() {
    rewriteRun(
        text(
            """
            package org.example;

            import org.springframework.context.annotation.Profile;
            import org.springframework.stereotype.Component;

            @Component
            @Profile("!test")
            public class SampleProcessStarter {
            }
            """,
            """
            package org.example;

            import org.springframework.context.annotation.Profile;
            import org.springframework.stereotype.Component;

            @Component
            // TODO: Manual migration required - @Profile annotation with negation syntax ("!...").
            // OpenRewrite's Java printer corrupts this annotation and silently skips this file during migration.
            // Migrate this file manually. See: https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1548
            @Profile("!test")
            public class SampleProcessStarter {
            }
            """,
            spec -> spec.path("src/main/java/org/example/SampleProcessStarter.java")));
  }

  @Test
  void addsCommentBeforeEachProfileNegationInFile() {
    rewriteRun(
        text(
            """
            package org.example;

            @Profile("!test")
            public class ComponentA {
            }

            @Profile("!prod")
            public class ComponentB {
            }
            """,
            """
            package org.example;

            // TODO: Manual migration required - @Profile annotation with negation syntax ("!...").
            // OpenRewrite's Java printer corrupts this annotation and silently skips this file during migration.
            // Migrate this file manually. See: https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1548
            @Profile("!test")
            public class ComponentA {
            }

            // TODO: Manual migration required - @Profile annotation with negation syntax ("!...").
            // OpenRewrite's Java printer corrupts this annotation and silently skips this file during migration.
            // Migrate this file manually. See: https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1548
            @Profile("!prod")
            public class ComponentB {
            }
            """,
            spec -> spec.path("src/main/java/org/example/MultiProfile.java")));
  }

  @Test
  void isIdempotent() {
    String alreadyAnnotated =
        """
        package org.example;

        @Component
        // TODO: Manual migration required - @Profile annotation with negation syntax ("!...").
        // OpenRewrite's Java printer corrupts this annotation and silently skips this file during migration.
        // Migrate this file manually. See: https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1548
        @Profile("!test")
        public class SampleClass {
        }
        """;
    rewriteRun(
        text(alreadyAnnotated, spec -> spec.path("src/main/java/org/example/SampleClass.java")));
  }

  @Test
  void doesNotModifyFileWithoutProfileNegation() {
    rewriteRun(
        text(
            """
            package org.example;

            import org.springframework.context.annotation.Profile;
            import org.springframework.stereotype.Component;

            @Component
            @Profile("prod")
            public class ProductionBean {
            }
            """,
            spec -> spec.path("src/main/java/org/example/ProductionBean.java")));
  }

  @Test
  void doesNotModifyNonJavaFile() {
    rewriteRun(
        text(
            """
            some.property=@Profile("!test")
            """,
            spec -> spec.path("src/main/resources/application.properties")));
  }

  @Test
  void handlesInlinedAnnotation() {
    rewriteRun(
        text(
            """
            package org.example;

            @Component @Profile("!test") public class Inline {
            }
            """,
            """
            package org.example;

            // TODO: Manual migration required - @Profile annotation with negation syntax ("!...").
            // OpenRewrite's Java printer corrupts this annotation and silently skips this file during migration.
            // Migrate this file manually. See: https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1548
            @Component @Profile("!test") public class Inline {
            }
            """,
            spec -> spec.path("src/main/java/org/example/Inline.java")));
  }
}
