/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.code.recipes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.camunda.migration.code.recipes.sharedRecipes.FindSpringBootMajorRecipe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/**
 * Guards Spring Boot major aware dependency selection.
 *
 * <p>Camunda 8.9 split both the Spring Boot starter and the Spring Boot process test module into
 * mutually exclusive Spring Boot 3 and Spring Boot 4 artifacts. Picking the wrong one leaves the
 * migrated application unable to start, so every prepare recipe set has to delegate the choice to
 * the shared selector recipes instead of hardcoding an artifact.
 */
class RecipeDependencyConfigTest implements RewriteTest {

  private static final String SB3_STARTER = "camunda-spring-boot-3-starter";
  private static final String SB4_STARTER = "camunda-spring-boot-starter";
  private static final String SB3_PROCESS_TEST = "camunda-process-test-spring-boot-3";
  private static final String SB4_PROCESS_TEST = "camunda-process-test-spring";
  private static final String GRADLE_TEST_CAMUNDA_VERSION = "8.9.0";

  /** The two artifacts of each pair are mutually exclusive - a project must declare exactly one. */
  private static final Map<String, String> MUTUALLY_EXCLUSIVE_PAIRS =
      Map.of(
          SB3_STARTER, SB4_STARTER,
          SB4_STARTER, SB3_STARTER,
          SB3_PROCESS_TEST, SB4_PROCESS_TEST,
          SB4_PROCESS_TEST, SB3_PROCESS_TEST);

  private static final List<String> RECIPE_DESCRIPTORS =
      List.of(
          "/META-INF/rewrite/clientRecipes.yml",
          "/META-INF/rewrite/delegateRecipes.yml",
          "/META-INF/rewrite/externalWorkerRecipes.yml");

  /**
   * Declarative recipes that add, remove or rename a dependency. A prepare recipe set must never
   * name one of these directly - it has to go through the shared selector recipes, otherwise the
   * Spring Boot major is ignored again.
   */
  private static final List<String> DEPENDENCY_MUTATING_RECIPES =
      List.of(
          "org.openrewrite.java.dependencies.AddDependency",
          "org.openrewrite.java.dependencies.RemoveDependency",
          "org.openrewrite.java.dependencies.ChangeDependency",
          "org.openrewrite.maven.AddDependency",
          "org.openrewrite.maven.RemoveDependency",
          "org.openrewrite.maven.ChangeDependencyGroupIdAndArtifactId",
          "org.openrewrite.gradle.AddDependency",
          "org.openrewrite.gradle.RemoveDependency",
          "org.openrewrite.gradle.ChangeDependency");

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources(
        "io.camunda.migration.code.recipes.sharedRecipes.ConfigureCamundaStarterRecipe",
        "io.camunda.migration.code.recipes.sharedRecipes.ConfigureCamundaProcessTestDependencyRecipe");
  }

  /**
   * Every way a project can pin its Spring Boot major, paired with the artifacts the selector has
   * to end up with. Adding a detection branch without extending this list leaves it unguarded.
   */
  static List<Arguments> springBootMajorSignals() {
    return List.of(
        arguments("parent is Boot 3", parent("3.5.4"), SB3_STARTER, SB3_PROCESS_TEST),
        arguments("parent is Boot 4", parent("4.0.0"), SB4_STARTER, SB4_PROCESS_TEST),
        arguments("imported BOM is Boot 3", bom("3.5.4"), SB3_STARTER, SB3_PROCESS_TEST),
        arguments("imported BOM is Boot 4", bom("4.0.0"), SB4_STARTER, SB4_PROCESS_TEST),
        arguments(
            "BOM version comes from a Boot 3 spring-boot.version property",
            property("3.5.4") + bom("${spring-boot.version}"),
            SB3_STARTER,
            SB3_PROCESS_TEST),
        arguments(
            "BOM version comes from a Boot 4 spring-boot.version property",
            property("4.0.0") + bom("${spring-boot.version}"),
            SB4_STARTER,
            SB4_PROCESS_TEST),
        arguments("no detectable Spring Boot major", "", SB3_STARTER, SB3_PROCESS_TEST));
  }

  /**
   * Gradle exposes the Spring Boot major through the resolved platform, the Boot plugin, or the
   * Spring dependency management plugin. These are separate rows so a future detection change
   * cannot silently drop one of the supported Gradle project shapes.
   */
  static List<Arguments> gradleSpringBootMajorSignals() {
    return List.of(
        arguments(
            "resolved Spring Boot 3 platform",
            gradleBuild(
                """
                plugins {
                    id 'java'
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation platform('org.springframework.boot:spring-boot-dependencies:3.5.4')
                }
                """),
            SB3_STARTER,
            SB3_PROCESS_TEST),
        arguments(
            "resolved Spring Boot 4 platform",
            gradleBuild(
                """
                plugins {
                    id 'java'
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation platform('org.springframework.boot:spring-boot-dependencies:4.0.0')
                }
                """),
            SB4_STARTER,
            SB4_PROCESS_TEST),
        arguments(
            "Spring Boot 3 plugin",
            gradleBuild(
                """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '3.5.4'
                }

                repositories {
                    mavenCentral()
                }
                """),
            SB3_STARTER,
            SB3_PROCESS_TEST),
        arguments(
            "Spring Boot 4 plugin",
            gradleBuild(
                """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '4.0.0'
                }

                repositories {
                    mavenCentral()
                }
                """),
            SB4_STARTER,
            SB4_PROCESS_TEST),
        arguments(
            "Spring dependency management imports Boot 4",
            gradleBuild(
                """
                plugins {
                    id 'java'
                    id 'io.spring.dependency-management' version '1.1.7'
                }

                repositories {
                    mavenCentral()
                }

                dependencyManagement {
                    imports {
                        mavenBom 'org.springframework.boot:spring-boot-dependencies:4.0.0'
                    }
                }
                """),
            SB4_STARTER,
            SB4_PROCESS_TEST),
        arguments(
            "Spring dependency management imports Boot 3",
            gradleBuild(
                """
                plugins {
                    id 'java'
                    id 'io.spring.dependency-management' version '1.1.7'
                }

                repositories {
                    mavenCentral()
                }

                dependencyManagement {
                    imports {
                        mavenBom 'org.springframework.boot:spring-boot-dependencies:3.5.4'
                    }
                }
                """),
            SB3_STARTER,
            SB3_PROCESS_TEST),
        arguments(
            "no detectable Spring Boot major",
            gradleBuild(
                """
                plugins {
                    id 'java'
                }

                repositories {
                    mavenCentral()
                }
                """),
            SB3_STARTER,
            SB3_PROCESS_TEST));
  }

  @ParameterizedTest(name = "selects Gradle dependencies when {0}")
  @MethodSource("gradleSpringBootMajorSignals")
  void selectsDependenciesMatchingGradleSpringBootMajor(
      String signalName, String buildScript, String expectedStarter, String expectedProcessTest) {
    rewriteRun(
        spec -> spec.beforeRecipe(withToolingApi()),
        buildGradle(
            buildScript,
            spec ->
                spec.path("build.gradle")
                    .after(
                        after ->
                            assertGradleDependencies(after, expectedStarter, expectedProcessTest))));
  }

  static List<Arguments> gradleDetectedSpringBootMajorSignals() {
    return gradleSpringBootMajorSignals().stream()
        .filter(arguments -> !"no detectable Spring Boot major".equals(arguments.get()[0]))
        .toList();
  }

  @ParameterizedTest(name = "repairs Gradle dependencies when {0}")
  @MethodSource("gradleDetectedSpringBootMajorSignals")
  void repairsMismatchedGradleDependencyChoice(
      String signalName, String buildScript, String expectedStarter, String expectedProcessTest) {
    String wrongStarter = SB3_STARTER.equals(expectedStarter) ? SB4_STARTER : SB3_STARTER;
    String wrongProcessTest =
        SB3_PROCESS_TEST.equals(expectedProcessTest) ? SB4_PROCESS_TEST : SB3_PROCESS_TEST;

    rewriteRun(
        spec -> spec.beforeRecipe(withToolingApi()),
        buildGradle(
            buildScript + gradleDependencies(wrongStarter, wrongProcessTest),
            spec ->
                spec.path("build.gradle")
                    .after(
                        after ->
                            assertGradleDependencies(after, expectedStarter, expectedProcessTest))));
  }

  @Test
  void leavesExplicitGradleChoiceAloneWithoutSpringBootSignal() {
    rewriteRun(
        spec -> spec.beforeRecipe(withToolingApi()),
        buildGradle(
            gradleBuild(
                """
                plugins {
                    id 'java'
                }

                repositories {
                    mavenCentral()
                }
                """
                    + gradleDependencies(SB4_STARTER, SB4_PROCESS_TEST)),
            spec -> spec.path("build.gradle")));
  }

  @Test
  void changesUnresolvedGradleDependencyDeclaration() {
    String before =
        gradleBuild(
            """
            plugins {
                id 'java'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation platform('org.springframework.boot:spring-boot-dependencies:4.0.0')
                implementation "io.camunda:camunda-spring-boot-3-starter:8.10.0-SNAPSHOT"
            }

            dependencies {
                testImplementation "io.camunda:camunda-process-test-spring-boot-3:8.10.0-SNAPSHOT"
            }
            """);
    String after =
        before.replace(SB3_STARTER + ":8.10.0-SNAPSHOT", SB4_STARTER + ":8.10.0-SNAPSHOT");

    rewriteRun(
        spec ->
            spec.recipe(
                    new io.camunda.migration.code.recipes.sharedRecipes
                        .ChangeGradleDependencyRecipe(
                        "io.camunda",
                        SB3_STARTER,
                        "io.camunda",
                        SB4_STARTER,
                        "8.10.0-SNAPSHOT"))
                .beforeRecipe(withToolingApi()),
        buildGradle(before, after));
  }

  @Test
  void detectsSpringBootPluginWithoutGradleModel() {
    String build =
        """
        plugins {
            id 'java'
            id 'org.springframework.boot' version '4.0.0'
        }
        """;

    rewriteRun(
        spec -> spec.recipe(new FindSpringBootMajorRecipe("4")),
        buildGradle(build, "/*~~>*/" + build));
  }

  @Test
  void detectsDependencyManagementBomWithoutGradleModel() {
    String build =
        """
        plugins {
            id 'java'
            id 'io.spring.dependency-management' version '1.1.7'
        }

        dependencyManagement {
            imports {
                mavenBom 'org.springframework.boot:spring-boot-dependencies:3.5.4'
            }
        }
        """;

    rewriteRun(
        spec -> spec.recipe(new FindSpringBootMajorRecipe("3")),
        buildGradle(build, "/*~~>*/" + build));
  }

  @Test
  void configuresPlatformBackedGradleDependenciesWithTheSharedRecipe() {
    String before =
        gradleBuild(
            """
            plugins {
                id 'java'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation platform('org.springframework.boot:spring-boot-dependencies:4.0.0')
            }
            """)
            + gradleDependencies(SB3_STARTER, SB3_PROCESS_TEST);

    rewriteRun(
        spec -> spec.beforeRecipe(withToolingApi()),
        buildGradle(
            before,
            spec ->
                spec.path("build.gradle")
                    .after(
                        after ->
                            assertGradleDependencies(after, SB4_STARTER, SB4_PROCESS_TEST))));
  }

  /**
   * The selector adds the matching artifacts to a project that has neither of them yet. This is the
   * plain Camunda 7 starting point.
   */
  @ParameterizedTest(name = "adds Spring Boot matched dependencies when {0}")
  @MethodSource("springBootMajorSignals")
  void addsDependenciesMatchingSpringBootMajor(
      String signalName, String signal, String expectedStarter, String expectedProcessTest) {
    rewriteRun(
        pomXml(
            pom(signal, ""),
            spec ->
                spec.path("pom.xml")
                    .after(after -> assertOnly(after, expectedStarter, expectedProcessTest))));
  }

  /**
   * Re-running the selector over a project it already migrated must not change anything. This is
   * what proves the recipe converges instead of oscillating between the two artifact pairs, which
   * would make every re-run report a diff.
   */
  @ParameterizedTest(name = "is idempotent when {0}")
  @MethodSource("springBootMajorSignals")
  void isIdempotentOnAlreadyMigratedProject(
      String signalName, String signal, String expectedStarter, String expectedProcessTest) {
    rewriteRun(
        pomXml(
            pom(signal, dependencies(expectedStarter, expectedProcessTest)),
            spec -> spec.path("pom.xml")));
  }

  /**
   * The situation reported in #2017: an earlier recipe version added the Spring Boot 4 artifacts to
   * a Spring Boot 3 project. Re-running has to repair the choice rather than leave the project
   * broken.
   */
  @ParameterizedTest(name = "repairs a mismatched dependency choice when {0}")
  @MethodSource("springBootMajorSignals")
  void repairsMismatchedDependencyChoice(
      String signalName, String signal, String expectedStarter, String expectedProcessTest) {
    String wrongStarter = SB3_STARTER.equals(expectedStarter) ? SB4_STARTER : SB3_STARTER;
    String wrongProcessTest =
        SB3_PROCESS_TEST.equals(expectedProcessTest) ? SB4_PROCESS_TEST : SB3_PROCESS_TEST;
    String pom = pom(signal, dependencies(wrongStarter, wrongProcessTest));

    if (signal.isEmpty()) {
      // Without a Spring Boot signal there is nothing to decide against, so an explicit choice by
      // the user is left alone rather than overwritten with the default.
      rewriteRun(pomXml(pom, spec -> spec.path("pom.xml")));
      return;
    }

    rewriteRun(
        pomXml(
            pom,
            spec ->
                spec.path("pom.xml")
                    .after(after -> assertOnly(after, expectedStarter, expectedProcessTest))));
  }

  /** The process test module keeps test scope when the selector rewrites it. */
  @Test
  void keepsTestScopeWhenSwitchingProcessTestModule() {
    rewriteRun(
        pomXml(
            pom(parent("4.0.0"), dependencies(SB3_STARTER, SB3_PROCESS_TEST)),
            spec ->
                spec.path("pom.xml")
                    .after(
                        after -> {
                          assertThat(after)
                              .as("process test module must stay in test scope")
                              .containsPattern(
                                  Pattern.compile(
                                      "<artifactId>"
                                          + Pattern.quote(SB4_PROCESS_TEST)
                                          + "</artifactId>\\s*<version>[^<]+</version>\\s*"
                                          + "<scope>test</scope>"));
                          return after;
                        })));
  }

  /**
   * Detection reads the <em>resolved</em> Spring Boot version, so a {@code spring-boot.version}
   * property that nothing consumes must not flip the choice. {@code spring-boot-starter-parent}
   * does not read that property, so the parent governs here.
   */
  @Test
  void unusedSpringBootVersionPropertyDoesNotOverrideTheParent() {
    rewriteRun(
        pomXml(
            pom(parent("3.5.4") + property("4.0.0"), ""),
            spec ->
                spec.path("pom.xml")
                    .after(after -> assertOnly(after, SB3_STARTER, SB3_PROCESS_TEST))));
  }

  /**
   * A POM that pins Spring Boot 3 through its parent and Spring Boot 4 through an imported BOM is
   * self-contradictory. The selector resolves it in favour of the parent, which is what Maven
   * itself gives precedence to for the Spring Boot managed versions. Reaching that answer costs one
   * extra rewrite cycle, because the BOM branch and the parent branch both fire and undo each
   * other; this is the only input shape where the selector is not single cycle, so it is pinned
   * here to keep the cost visible if a future detection branch widens it.
   */
  @Test
  void parentPomWinsOverConflictingImportedBom() {
    rewriteRun(
        spec -> spec.cycles(2).expectedCyclesThatMakeChanges(2),
        pomXml(
            pom(parent("3.5.4") + bom("4.0.0"), ""),
            spec ->
                spec.path("pom.xml")
                    .after(after -> assertOnly(after, SB3_STARTER, SB3_PROCESS_TEST))));
  }

  /**
   * Structural guard: every prepare recipe set has to delegate dependency selection to the shared
   * selector recipes. Hardcoding any dependency recipe there is exactly the defect from #2017, so
   * it is blocked for all recipe sets - including ones added later.
   */
  @Test
  void everyPrepareRecipeSetDelegatesDependencySelection() {
    for (String descriptor : RECIPE_DESCRIPTORS) {
      String yaml = resourceText(descriptor);
      for (String recipeName : prepareRecipeNames(yaml)) {
        String section = recipeSection(yaml, recipeName);
        assertThat(section)
            .as(
                "%s in %s must select the Camunda starter by Spring Boot major",
                recipeName, descriptor)
            .contains(
                "- io.camunda.migration.code.recipes.sharedRecipes.ConfigureCamundaStarterRecipe");
        assertThat(section)
            .as("%s in %s must not hardcode a dependency change", recipeName, descriptor)
            .doesNotContain(DEPENDENCY_MUTATING_RECIPES.toArray(String[]::new));
      }
    }
  }

  /**
   * Structural guard: the recipe set that adds the process test module must delegate that choice
   * too, and never name a process test artifact directly.
   */
  @Test
  void processTestDependencyIsSelectedBySpringBootMajor() {
    String yaml = resourceText("/META-INF/rewrite/clientRecipes.yml");
    String section =
        recipeSection(yaml, "io.camunda.migration.code.recipes.AllClientMigrateRecipes");

    assertThat(section)
        .contains(
            "- io.camunda.migration.code.recipes.sharedRecipes"
                + ".ConfigureCamundaProcessTestDependencyRecipe")
        .doesNotContain(SB3_PROCESS_TEST)
        .doesNotContain(SB4_PROCESS_TEST)
        .doesNotContain(DEPENDENCY_MUTATING_RECIPES.toArray(String[]::new));
  }

  /**
   * Structural guard for the Gradle branches, which the Maven fixtures above cannot reach.
   *
   * <p>{@code org.openrewrite.gradle.AddDependency} only skips the artifact it adds, so on its own
   * it happily adds the Spring Boot 3 artifact to a build that already declares the mutually
   * exclusive Spring Boot 4 one. Every Gradle add therefore has to sit behind
   * {@code DoesNotDeclareGradleDependencyRecipe} preconditions for both artifacts of its pair.
   *
   * <p>This is asserted on the descriptor rather than by running the recipe because executing a
   * Gradle branch requires a {@code GradleProject} marker, which in turn needs {@code
   * withToolingApi()} and a ~130 MB Gradle distribution download - see #2051.
   */
  @Test
  void everyGradleAddIsGuardedAgainstTheOppositeArtifact() {
    String yaml = resourceText("/META-INF/rewrite/dependencyRecipes.yml");

    List<String> guarded = new ArrayList<>();
    for (String document : yaml.split("(?m)^---$")) {
      if (!document.contains("- org.openrewrite.gradle.AddDependency:")) {
        continue;
      }
      Matcher addedMatcher =
          Pattern.compile(
                  "- org\\.openrewrite\\.gradle\\.AddDependency:\\s*\\n"
                      + "\\s*groupId: io\\.camunda\\s*\\n"
                      + "\\s*artifactId: (\\S+)\\s*\\n")
              .matcher(document);
      assertThat(addedMatcher.find())
          .as("Gradle add must name an io.camunda artifact:\n%s", document)
          .isTrue();
      String added = addedMatcher.group(1);
      assertThat(MUTUALLY_EXCLUSIVE_PAIRS)
          .as("Gradle add of %s must be part of a known mutually exclusive pair", added)
          .containsKey(added);
      guarded.add(added);

      for (String artifactId : List.of(added, MUTUALLY_EXCLUSIVE_PAIRS.get(added))) {
        assertThat(document)
            .as("Gradle add of %s must be preconditioned on %s being absent", added, artifactId)
            .containsPattern(
                Pattern.compile(
                    "- io\\.camunda\\.migration\\.code\\.recipes\\.sharedRecipes\\."
                        + "DoesNotDeclareGradleDependencyRecipe:\\s*\\n"
                        + "\\s*groupId: io\\.camunda\\s*\\n"
                        + "\\s*artifactId: "
                        + Pattern.quote(artifactId)
                        + "\\s*\\n"));
      }
    }

    assertThat(guarded)
        .as("Gradle builds must still get a starter and a process test module")
        .contains(SB3_STARTER, SB4_STARTER, SB3_PROCESS_TEST, SB4_PROCESS_TEST);
  }

  private static String assertOnly(String pom, String expectedStarter, String expectedProcessTest) {
    String unexpectedStarter = SB3_STARTER.equals(expectedStarter) ? SB4_STARTER : SB3_STARTER;
    String unexpectedProcessTest =
        SB3_PROCESS_TEST.equals(expectedProcessTest) ? SB4_PROCESS_TEST : SB3_PROCESS_TEST;

    assertThat(pom)
        .contains(artifactTag(expectedStarter))
        .contains(artifactTag(expectedProcessTest))
        .doesNotContain(artifactTag(unexpectedStarter))
        .doesNotContain(artifactTag(unexpectedProcessTest));
    return pom;
  }

  private static String artifactTag(String artifactId) {
    return "<artifactId>" + artifactId + "</artifactId>";
  }

  private static String parent(String version) {
    return """
             <parent>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-parent</artifactId>
               <version>%s</version>
             </parent>
           """
        .formatted(version);
  }

  private static String bom(String version) {
    return """
             <dependencyManagement>
               <dependencies>
                 <dependency>
                   <groupId>org.springframework.boot</groupId>
                   <artifactId>spring-boot-dependencies</artifactId>
                   <version>%s</version>
                   <type>pom</type>
                   <scope>import</scope>
                 </dependency>
               </dependencies>
             </dependencyManagement>
           """
        .formatted(version);
  }

  private static String property(String version) {
    return """
             <properties>
               <spring-boot.version>%s</spring-boot.version>
             </properties>
           """
        .formatted(version);
  }

  private static String dependencies(String starter, String processTest) {
    return """
             <dependencies>
               <dependency>
                 <groupId>io.camunda</groupId>
                 <artifactId>%s</artifactId>
                 <version>%s</version>
               </dependency>
               <dependency>
                 <groupId>io.camunda</groupId>
                 <artifactId>%s</artifactId>
                 <version>%s</version>
                 <scope>test</scope>
               </dependency>
             </dependencies>
           """
        .formatted(starter, camundaVersion(), processTest, camundaVersion());
  }

  private static String gradleBuild(String script) {
    return script;
  }

  private static String gradleDependencies(String starter, String processTest) {
    return """

           dependencies {
             implementation "io.camunda:%s:%s"
             testImplementation "io.camunda:%s:%s"
           }
           """
        .formatted(
            starter, GRADLE_TEST_CAMUNDA_VERSION, processTest, GRADLE_TEST_CAMUNDA_VERSION);
  }

  private static String assertGradleDependencies(
      String build, String expectedStarter, String expectedProcessTest) {
    String unexpectedStarter = SB3_STARTER.equals(expectedStarter) ? SB4_STARTER : SB3_STARTER;
    String unexpectedProcessTest =
        SB3_PROCESS_TEST.equals(expectedProcessTest) ? SB4_PROCESS_TEST : SB3_PROCESS_TEST;

    assertThat(build)
        .contains("io.camunda:" + expectedStarter + ":")
        .contains("io.camunda:" + expectedProcessTest + ":")
        .doesNotContain("io.camunda:" + unexpectedStarter + ":")
        .doesNotContain("io.camunda:" + unexpectedProcessTest + ":");
    return build;
  }

  private static String pom(String springBootSignal, String dependencies) {
    return """
           <project>
             <modelVersion>4.0.0</modelVersion>
             <groupId>com.example</groupId>
             <artifactId>demo</artifactId>
             <version>1.0.0</version>
           %s%s</project>
           """
        .formatted(springBootSignal, dependencies);
  }

  /**
   * Reads the Camunda 8 version the recipes were filtered with, so fixtures stay in sync with
   * whatever {@code ${version.camunda-8}} resolved to at build time.
   */
  private static String camundaVersion() {
    Matcher matcher =
        Pattern.compile("artifactId: " + SB3_STARTER + "\\s*\\n\\s*version: (\\S+)")
            .matcher(resourceText("/META-INF/rewrite/dependencyRecipes.yml"));
    assertThat(matcher.find()).as("filtered Camunda 8 version must be readable").isTrue();
    return matcher.group(1);
  }

  /** Names every {@code All...PrepareRecipes} recipe declared in the given descriptor. */
  private static List<String> prepareRecipeNames(String yaml) {
    Matcher matcher =
        Pattern.compile(
                "^name: (io\\.camunda\\.migration\\.code\\.recipes\\.All\\w*PrepareRecipes)$",
                Pattern.MULTILINE)
            .matcher(yaml);
    List<String> names = new ArrayList<>();
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    assertThat(names).as("descriptor must declare at least one prepare recipe").isNotEmpty();
    return names;
  }

  private static String recipeSection(String descriptor, String recipeName) {
    String marker = "name: " + recipeName + '\n';
    int start = descriptor.indexOf(marker);
    assertThat(start).as("recipe %s must exist", recipeName).isNotEqualTo(-1);
    int end = descriptor.indexOf("\n---\n", start);
    return end == -1 ? descriptor.substring(start) : descriptor.substring(start, end);
  }

  private static String resourceText(String resource) {
    try (InputStream in = RecipeDependencyConfigTest.class.getResourceAsStream(resource)) {
      assertThat(in).as("recipe descriptor %s must be on the classpath", resource).isNotNull();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line).append('\n');
        }
        return sb.toString();
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read recipe descriptor " + resource, e);
    }
  }
}
