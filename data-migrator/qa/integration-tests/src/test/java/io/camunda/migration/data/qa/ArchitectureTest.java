/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static io.camunda.migration.data.architecturefixture.InvalidArchitectureFixtures.InvalidVisibility;
import static io.camunda.migration.data.architecturefixture.InvalidArchitectureFixtures.MisplacedComponent;
import static io.camunda.migration.data.architecturefixture.InvalidArchitectureFixtures.MisplacedConfiguration;
import static io.camunda.migration.data.architecturefixture.InvalidArchitectureFixtures.PrintStackTraceCall;
import static io.camunda.migration.data.architecturefixture.InvalidArchitectureFixtures.SystemOutAccess;
import static io.camunda.migration.data.impl.logging.architecturefixture.InvalidLoggingFixtures.NonFinalLogs;
import static io.camunda.migration.data.impl.logging.architecturefixture.InvalidLoggingFixtures.NonPublicConstantsLogs;
import static io.camunda.migration.data.impl.logging.architecturefixture.InvalidLoggingFixtures.NonStaticLogs;
import static io.camunda.migration.data.qa.architecturefixture.InvalidTestingFixtures.BadMethodNameTest;
import static io.camunda.migration.data.qa.architecturefixture.InvalidTestingFixtures.BadParameterizedMethodNameTest;
import static io.camunda.migration.data.qa.architecturefixture.InvalidTestingFixtures.EngineImplAccessTest;
import static io.camunda.migration.data.qa.architecturefixture.InvalidTestingFixtures.EngineImplLifecycleAccessTest;
import static io.camunda.migration.data.qa.architecturefixture.InvalidTestingFixtures.ImplementationAccessTest;
import static io.camunda.migration.data.qa.architecturefixture.InvalidTestingFixtures.MissingSuffix;
import static io.camunda.migration.data.qa.architecturefixture.InvalidTestingFixtures.MissingParameterizedSuffix;
import static io.camunda.migration.data.qa.architecturefixture.InvalidTestingFixtures.StandaloneMigrationTest;
import static io.camunda.migration.data.qa.architecturefixture.InvalidTestingFixtures.StandaloneParameterizedMigrationTest;
import static io.camunda.migration.data.qa.architecturefixture.InvalidTestingFixtures.StaticNestedContainerTest.StaticNestedMigrationTest;
import static io.camunda.migration.data.qa.architecturefixture.InvalidTestingFixtures.WhiteBoxEngineImplAccessTest;
import static io.camunda.migration.data.qa.architecturefixture.InvalidTestingFixtures.packagePrivateMissingSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.core.importer.Locations;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Architecture tests to enforce design principles and prevent anti-patterns.
 *
 * <p>These tests ensure that:
 * <ul>
 *   <li>Tests use black-box testing approach (no access to internal impl classes)</li>
 *   <li>Tests verify behavior through observable outputs (logs, API responses)</li>
 *   <li>Tests use real-world skip scenarios instead of manipulating internal state</li>
 * </ul>
 */
class ArchitectureTest {

  protected static final Pattern ARCHITECTURE_FIXTURE_PATH =
      Pattern.compile(".*[/\\\\]architecturefixture[/\\\\].*");

  protected static final ImportOption EXCLUDE_ARCHITECTURE_FIXTURES =
      location -> !location.matches(ARCHITECTURE_FIXTURE_PATH);

  protected static final Path REPOSITORY_ROOT = Path.of(Objects.requireNonNull(
      System.getProperty("repositoryRoot"),
      "repositoryRoot system property must be configured by the test runner"));

  protected static final JavaClasses CLASSES = new ClassFileImporter()
      .withImportOption(EXCLUDE_ARCHITECTURE_FIXTURES)
      .importLocations(productionAndTestLocations());

  protected static final Pattern RULE_HEADING =
      Pattern.compile("^###\\s+((?:VR|PO|LR|TR)-\\d+):.*$");

  protected static final Pattern ENFORCEMENT =
      Pattern.compile("^\\*\\*Enforcement:\\*\\*\\s+`ArchitectureTest\\.(\\w+)\\(\\)`$");

  protected static final Pattern DOCUMENTED_RULE_ROW =
      Pattern.compile("^\\|\\s*((?:VR|PO|LR|TR)-\\d+)\\s*\\|.*\\|\\s*`(\\w+)\\(\\)`\\s*\\|$");

  @Test
  void shouldNotAccessImplClasses() {
    checkShouldNotAccessImplClasses(CLASSES);
  }

  protected static void checkShouldNotAccessImplClasses(JavaClasses classesToCheck) {
    classes()
        .that().resideInAPackage("..qa..")
        .and().areNotAnnotatedWith("io.camunda.migration.data.qa.util.WhiteBox")
        .should(notAccessMigratorImplPackage())
        .allowEmptyShould(true)
        .because("Tests should not access internal implementation details from io.camunda.migration.data.impl package. " +
            "Use log assertions and C8 API queries instead. " +
            "Exceptions: (1) Constants (static final fields) and enums from impl classes are allowed, including their methods (e.g., enum.getDisplayName()), " +
            "(2) Methods annotated with @BeforeEach or @AfterEach can access impl classes for test setup/cleanup, " +
            "(3) Classes or methods annotated with @WhiteBox are allowed to access impl classes for white-box testing.")
        .check(classesToCheck);
  }

  @Test
  void shouldNotAccessCamundaBpmEngineImplClasses() {
    checkShouldNotAccessCamundaBpmEngineImplClasses(CLASSES);
  }

  protected static void checkShouldNotAccessCamundaBpmEngineImplClasses(JavaClasses classesToCheck) {
    classes()
        .that().resideInAPackage("..qa..")
        .and().haveSimpleNameEndingWith("Test")
        .should(notAccessCamundaBpmEngineImplPackage())
        .allowEmptyShould(true)
        .because("Tests should not use internal Camunda BPM engine implementation classes. " +
            "ClockUtil is allowed for time manipulation, ProcessEngineConfigurationImpl is allowed " +
            "from test lifecycle and test methods, and @WhiteBox classes and methods are exempt.")
        .check(classesToCheck);
  }

  @Test
  void shouldRecognizeArchitectureFixturePathsAcrossOperatingSystems() {
    assertThat(ARCHITECTURE_FIXTURE_PATH.matcher(
        "file:/workspace/io/camunda/migration/data/architecturefixture/Invalid.class").matches())
        .isTrue();
    assertThat(ARCHITECTURE_FIXTURE_PATH.matcher(
        "C:\\workspace\\io\\camunda\\migration\\data\\architecturefixture\\Invalid.class").matches())
        .isTrue();
  }

  @Test
  void shouldAllowEngineConfigurationAccessFromAllLifecycleMethods() {
    JavaClasses fixtureClasses = new ClassFileImporter().importClasses(EngineImplLifecycleAccessTest.class);

    checkShouldNotAccessCamundaBpmEngineImplClasses(fixtureClasses);
  }

  @Test
  void shouldAllowEngineImplementationAccessFromWhiteBoxClass() {
    JavaClasses fixtureClasses = new ClassFileImporter().importClasses(WhiteBoxEngineImplAccessTest.class);

    checkShouldNotAccessCamundaBpmEngineImplClasses(fixtureClasses);
  }

  @Test
  void shouldNotHavePrivateMethods() {
    checkShouldNotHavePrivateMethods(CLASSES);
  }

  protected static void checkShouldNotHavePrivateMethods(JavaClasses classesToCheck) {
    classes()
        .that().resideInAPackage("io.camunda.migration.data..")
        .and().resideOutsideOfPackage("..qa..") // Exclude test code
        .should(notHavePrivateMethods())
        .allowEmptyShould(true)
        .because("Methods should use protected or package-protected visibility instead of private " +
            "to allow for testing and extensibility. Use protected for methods that might be " +
            "overridden and package-protected for internal methods that tests might need to access.")
        .check(classesToCheck);
  }

  @Test
  void shouldNotHavePrivateFields() {
    checkShouldNotHavePrivateFields(CLASSES);
  }

  protected static void checkShouldNotHavePrivateFields(JavaClasses classesToCheck) {
    classes()
        .that().resideInAPackage("io.camunda.migration.data..")
        .and().resideOutsideOfPackage("..qa..") // Exclude test code
        .should(notHavePrivateFields())
        .allowEmptyShould(true)
        .because("Fields should use protected or package-protected visibility instead of private " +
            "to allow for testing and extensibility. Use protected for fields that might be " +
            "accessed by subclasses and package-protected for fields that tests might need to access.")
        .check(classesToCheck);
  }

  @Test
  void shouldNotHavePrivateConstructors() {
    checkShouldNotHavePrivateConstructors(CLASSES);
  }

  protected static void checkShouldNotHavePrivateConstructors(JavaClasses classesToCheck) {
    classes()
        .that().resideInAPackage("io.camunda.migration.data..")
        .and().resideOutsideOfPackage("..qa..") // Exclude test code
        .should(notHavePrivateConstructors())
        .allowEmptyShould(true)
        .because("Classes should use protected or package-protected constructors to allow for " +
            "testing and extensibility.")
        .check(classesToCheck);
  }

  @Test
  void shouldOnlyHaveStaticFinalFieldsInLogClasses() {
    checkShouldOnlyHaveStaticFinalFieldsInLogClasses(CLASSES);
  }

  protected static void checkShouldOnlyHaveStaticFinalFieldsInLogClasses(JavaClasses classesToCheck) {
    classes()
        .that().haveSimpleNameEndingWith("Logs")
        .and().resideInAPackage("..impl.logging..")
        .should(onlyHaveStaticFinalFields())
        .allowEmptyShould(true)
        .because("Log classes should only contain static final fields")
        .check(classesToCheck);
  }

  @Test
  void shouldFollowNamingConventionForTestMethods() {
    checkShouldFollowNamingConventionForTestMethods(CLASSES);
  }

  protected static void checkShouldFollowNamingConventionForTestMethods(JavaClasses classesToCheck) {
    classes()
        .that().resideInAPackage("..qa..")
        .and().haveSimpleNameEndingWith("Test")
        .should(haveTestMethodsWithCorrectNaming())
        .allowEmptyShould(true)
        .because("Test methods should follow the naming convention: 'should' prefix for behavior tests " +
            "to clearly express what the test verifies (e.g., shouldSkipProcessInstanceWhenDefinitionMissing).")
        .check(classesToCheck);
  }

  @Test
  void shouldResideInImplPackageForComponents() {
    checkShouldResideInImplPackageForComponents(CLASSES);
  }

  protected static void checkShouldResideInImplPackageForComponents(JavaClasses classesToCheck) {
    classes()
        .that().areAnnotatedWith("org.springframework.stereotype.Component")
        .or().areAnnotatedWith("org.springframework.stereotype.Service")
        .and().resideInAPackage("io.camunda.migration.data..")
        .and().resideOutsideOfPackages("..qa..")
        .should(beInImplPackageOrTopLevel())
        .allowEmptyShould(true)
        .because("Components and services should reside in the 'impl' package (for internal implementations) " +
            "or at the top level of io.camunda.migration.data (for public APIs like HistoryMigrator, RuntimeMigrator).")
        .check(classesToCheck);
  }

  @Test
  void shouldResideInConfigPackageForConfigurations() {
    checkShouldResideInConfigPackageForConfigurations(CLASSES);
  }

  protected static void checkShouldResideInConfigPackageForConfigurations(JavaClasses classesToCheck) {
    classes()
        .that().areAnnotatedWith("org.springframework.context.annotation.Configuration")
        .and().resideInAPackage("io.camunda.migration.data..")
        .and().resideOutsideOfPackage("..qa..")
        .should().resideInAPackage("..config..")
        .allowEmptyShould(true)
        .because("@Configuration classes should be centralized in the config package")
        .check(classesToCheck);
  }

  @Test
  void shouldBePublicStaticFinalForLogConstants() {
    checkShouldBePublicStaticFinalForLogConstants(CLASSES);
  }

  protected static void checkShouldBePublicStaticFinalForLogConstants(JavaClasses classesToCheck) {
    classes()
        .that().haveSimpleNameEndingWith("Logs")
        .and().resideInAPackage("..impl.logging..")
        .should(notHaveNonFinalStaticFields())
        .allowEmptyShould(true)
        .because("Log message constants should be public static final to prevent modification " +
            "and ensure consistent logging across the application.")
        .check(classesToCheck);
  }

  @Test
  void shouldUseConstantsNotStringLiteralsInLogAssertions() {
    classes()
        .that().resideInAPackage("..qa..")
        .and().haveSimpleNameEndingWith("Test")
        .should(useLogConstantsInAssertions())
        .allowEmptyShould(true)
        .because("Log assertions should use constants from Logs classes (via formatMessage()) " +
            "instead of string literals to ensure consistency and prevent typos. " +
            "Use formatMessage(HistoryMigratorLogs.CONSTANT, args...) or formatMessage(RuntimeMigratorLogs.CONSTANT, args...).")
        .check(CLASSES);
  }

  @Test
  void shouldHaveProtectedAutowiredFields() {
    fields()
        .that().areAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
        .should().beProtected()
        .allowEmptyShould(true)
        .because("@Autowired fields should be protected to allow subclasses to access them " +
            "while maintaining encapsulation. This is especially important in test hierarchies.")
        .check(CLASSES);
  }

  @Test
  void shouldNotUseSystemOutOrPrintStackTrace() {
    checkShouldNotUseSystemOutOrPrintStackTrace(CLASSES);
  }

  protected static void checkShouldNotUseSystemOutOrPrintStackTrace(JavaClasses classesToCheck) {
    noClasses()
        .that().resideInAPackage("io.camunda.migration.data..")
        .and().resideOutsideOfPackages("..app..", "..qa..")
        .should().accessField(System.class, "out")
        .orShould().callMethod("java.lang.Throwable", "printStackTrace")
        .allowEmptyShould(true)
        .because("Production code should use SLF4J logging instead of System.out.println() or printStackTrace(). " +
            "Use a Logger instance and appropriate log levels (debug, info, warn, error).")
        .check(classesToCheck);
  }

  @Test
  void shouldExtendAppropriateAbstractTestClass() {
    checkShouldExtendAppropriateAbstractTestClass(CLASSES);
  }

  protected static void checkShouldExtendAppropriateAbstractTestClass(JavaClasses classesToCheck) {
    classes()
        .that().resideInAPackage("..qa..")
        .and().haveSimpleNameEndingWith("Test")
        .and().resideOutsideOfPackages("..persistence..", "..distribution..")
        .and().doNotHaveSimpleName("ArchitectureTest")
        .should(extendAppropriateAbstractTestClass())
        .allowEmptyShould(true)
        .because("Migration behavior tests should extend a shared abstract test class")
        .check(classesToCheck);
  }

  @Test
  void shouldHaveTestSuffixForTestClasses() {
    checkShouldHaveTestSuffixForTestClasses(CLASSES);
  }

  protected static void checkShouldHaveTestSuffixForTestClasses(JavaClasses classesToCheck) {
    classes()
        .that().resideInAPackage("..qa..")
        .and().areNotInterfaces()
        .and().areNotEnums()
        .and().areNotAnnotations()
        .and().doNotHaveModifier(JavaModifier.ABSTRACT)
        .should(haveTestSuffixIfContainingTestMethods())
        .allowEmptyShould(true)
        .because("Test classes should end with 'Test' suffix to follow JUnit conventions and be " +
            "recognized by test runners and build tools.")
        .check(classesToCheck);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("documentedRuleFixtures")
  @DisplayName("documented rules reject invalid fixtures")
  void shouldRejectInvalidFixtures(
      String ruleId, Consumer<JavaClasses> rule, Class<?> invalidFixture) {
    JavaClasses fixtureClasses = new ClassFileImporter().importClasses(invalidFixture);

    assertThatThrownBy(() -> rule.accept(fixtureClasses))
        .as("Rule %s should reject %s", ruleId, invalidFixture.getSimpleName())
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void shouldBindEveryDocumentedArchitectureRule() throws NoSuchMethodException {
    List<DocumentedRule> documentedRules = Stream.of(
            REPOSITORY_ROOT.resolve("docs/ARCHITECTURE_RULES.md"),
            REPOSITORY_ROOT.resolve("docs/TESTING_GUIDELINES.md"))
        .flatMap(path -> documentedRules(path).stream())
        .toList();

    assertThat(documentedRules).isNotEmpty();
    for (DocumentedRule documentedRule : documentedRules) {
      Method enforcementMethod = ArchitectureTest.class.getDeclaredMethod(documentedRule.enforcementMethod());
      assertThat(enforcementMethod.isAnnotationPresent(Test.class))
          .as("%s (%s) must be an executable @Test", documentedRule.id(), enforcementMethod.getName())
          .isTrue();
    }
  }

  protected static Stream<Arguments> documentedRuleFixtures() {
    return Stream.of(
        Arguments.of("TR-1", (Consumer<JavaClasses>) ArchitectureTest::checkShouldNotAccessImplClasses,
            ImplementationAccessTest.class),
        Arguments.of("TR-2",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldNotAccessCamundaBpmEngineImplClasses,
            EngineImplAccessTest.class),
        Arguments.of("TR-3",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldFollowNamingConventionForTestMethods,
            BadMethodNameTest.class),
        Arguments.of("TR-3 parameterized",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldFollowNamingConventionForTestMethods,
            BadParameterizedMethodNameTest.class),
        Arguments.of("TR-4",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldExtendAppropriateAbstractTestClass,
            StandaloneMigrationTest.class),
        Arguments.of("TR-4 parameterized",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldExtendAppropriateAbstractTestClass,
            StandaloneParameterizedMigrationTest.class),
        Arguments.of("TR-4 static nested",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldExtendAppropriateAbstractTestClass,
            StaticNestedMigrationTest.class),
        Arguments.of("TR-5",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldHaveTestSuffixForTestClasses,
            MissingSuffix.class),
        Arguments.of("TR-5 parameterized",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldHaveTestSuffixForTestClasses,
            MissingParameterizedSuffix.class),
        Arguments.of("TR-5 package-private",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldHaveTestSuffixForTestClasses,
            packagePrivateMissingSuffix()),
        Arguments.of("VR-1", (Consumer<JavaClasses>) ArchitectureTest::checkShouldNotHavePrivateMethods,
            InvalidVisibility.class),
        Arguments.of("VR-2", (Consumer<JavaClasses>) ArchitectureTest::checkShouldNotHavePrivateFields,
            InvalidVisibility.class),
        Arguments.of("VR-3", (Consumer<JavaClasses>) ArchitectureTest::checkShouldNotHavePrivateConstructors,
            InvalidVisibility.class),
        Arguments.of("PO-1",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldResideInImplPackageForComponents,
            MisplacedComponent.class),
        Arguments.of("PO-2",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldResideInConfigPackageForConfigurations,
            MisplacedConfiguration.class),
        Arguments.of("LR-1 non-final",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldOnlyHaveStaticFinalFieldsInLogClasses,
            NonFinalLogs.class),
        Arguments.of("LR-1 non-static",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldOnlyHaveStaticFinalFieldsInLogClasses,
            NonStaticLogs.class),
        Arguments.of("LR-2",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldBePublicStaticFinalForLogConstants,
            NonPublicConstantsLogs.class),
        Arguments.of("LR-3 System.out",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldNotUseSystemOutOrPrintStackTrace,
            SystemOutAccess.class),
        Arguments.of("LR-3 printStackTrace",
            (Consumer<JavaClasses>) ArchitectureTest::checkShouldNotUseSystemOutOrPrintStackTrace,
            PrintStackTraceCall.class));
  }

  protected static Set<Location> productionAndTestLocations() {
    Set<Location> locations = new HashSet<>(Locations.ofPackage("io.camunda.migration.data"));
    Path distroClasses = REPOSITORY_ROOT.resolve("data-migrator/distro/target/classes");
    assertThat(distroClasses)
        .as("Build data-migrator/distro before running architecture tests")
        .isDirectory();
    locations.add(Location.of(distroClasses));
    return locations;
  }

  protected static List<DocumentedRule> documentedRules(Path path) {
    List<String> lines = readLines(path);
    List<DocumentedRule> sectionRules = IntStream.range(0, lines.size())
        .mapToObj(index -> documentedSectionRule(lines, index))
        .flatMap(java.util.Optional::stream)
        .toList();
    List<DocumentedRule> tableRules = lines.stream()
        .map(DOCUMENTED_RULE_ROW::matcher)
        .filter(java.util.regex.Matcher::matches)
        .map(matcher -> new DocumentedRule(matcher.group(1), matcher.group(2)))
        .toList();

    assertThat(tableRules)
        .as("Summary table must match documented rule sections in %s", path)
        .containsExactlyInAnyOrderElementsOf(sectionRules);
    return sectionRules;
  }

  protected static Optional<DocumentedRule> documentedSectionRule(
      List<String> lines, int headingIndex) {
    var heading = RULE_HEADING.matcher(lines.get(headingIndex));
    if (!heading.matches()) {
      return Optional.empty();
    }

    Optional<DocumentedRule> documentedRule = IntStream.range(headingIndex + 1, lines.size())
        .takeWhile(index -> !RULE_HEADING.matcher(lines.get(index)).matches())
        .mapToObj(index -> ENFORCEMENT.matcher(lines.get(index)))
        .filter(java.util.regex.Matcher::matches)
        .findFirst()
        .map(matcher -> new DocumentedRule(heading.group(1), matcher.group(1)));
    assertThat(documentedRule)
        .as("Rule %s must declare an ArchitectureTest enforcement method", heading.group(1))
        .isPresent();
    return documentedRule;
  }

  protected static List<String> readLines(Path path) {
    try {
      return Files.readAllLines(path);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read architecture documentation " + path, e);
    }
  }

  // Custom ArchConditions

  protected static ArchCondition<JavaClass> notAccessMigratorImplPackage() {
    return new ArchCondition<JavaClass>("not access io.camunda.migration.data.impl package") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        if (javaClass.isAnnotatedWith("io.camunda.migration.data.qa.util.WhiteBox")) {
          return;
        }
        javaClass.getAccessesFromSelf().forEach(access -> {
          JavaClass targetClass = access.getTargetOwner();
          String targetClassName = targetClass.getName();
          String targetPackage = targetClass.getPackageName();

          // Only check for io.camunda.migration.data.impl package (our implementation details)
          if (targetPackage.startsWith("io.camunda.migration.data.impl")) {
            // Allow field accesses (constants, enums, static finals, log messages, etc.)
            if (access instanceof com.tngtech.archunit.core.domain.JavaFieldAccess) {
              return; // Field access is allowed
            }

            // Allow method calls on enums (e.g., ENUM_CONSTANT.getDisplayName())
            if (access instanceof com.tngtech.archunit.core.domain.JavaMethodCall) {
              if (targetClass.isEnum()) {
                return; // Method calls on enum types are allowed
              }
            }

            // Allow accesses from methods annotated with @WhiteBox
            if (access.getOrigin() instanceof com.tngtech.archunit.core.domain.JavaMethod originMethod) {
              boolean isWhiteBoxMethod = originMethod.getAnnotations().stream()
                  .anyMatch(annotation ->
                      annotation.getRawType().getName().equals("io.camunda.migration.data.qa.util.WhiteBox"));

              if (isWhiteBoxMethod) {
                return; // Access from @WhiteBox method is allowed
              }
            }

            // Allow accesses from @BeforeEach or @AfterEach methods (test setup/cleanup)
            if (access.getOrigin() instanceof com.tngtech.archunit.core.domain.JavaMethod originMethod) {

              boolean isSetupOrCleanup = originMethod.getAnnotations().stream()
                  .anyMatch(annotation ->
                      annotation.getRawType().getName().equals("org.junit.jupiter.api.BeforeEach") ||
                      annotation.getRawType().getName().equals("org.junit.jupiter.api.AfterEach"));

              if (isSetupOrCleanup) {
                return; // Access from @BeforeEach/@AfterEach is allowed
              }
            }

            // Allow accesses from JUnit Extension classes (test infrastructure)
            // Check both the class being analyzed and the declaring class of the origin method
            if (javaClass.getName().endsWith("Extension")) {
              return; // Access from Extension classes is allowed for test infrastructure
            }
            if (access.getOrigin() instanceof com.tngtech.archunit.core.domain.JavaMethod originMethod) {
              if (originMethod.getOwner().getName().endsWith("Extension")) {
                return; // Access from methods in Extension classes is allowed
              }
            }

            // Violation: calling method or constructor from io.camunda.migration.data.impl package
            String message = String.format(
                "%s accesses %s in (%s.java:%s)",
                access.getDescription(),
                targetClassName,
                javaClass.getSimpleName(),
                access.getLineNumber());
            events.add(SimpleConditionEvent.violated(access, message));
          }
        });
      }
    };
  }

  protected static ArchCondition<JavaClass> notAccessCamundaBpmEngineImplPackage() {
    return new ArchCondition<JavaClass>(
        "not access org.camunda.bpm.engine.impl package except documented test exemptions") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        if (javaClass.isAnnotatedWith("io.camunda.migration.data.qa.util.WhiteBox")) {
          return;
        }
        javaClass.getAccessesFromSelf().forEach(access -> {
          JavaClass targetClass = access.getTargetOwner();
          String targetClassName = targetClass.getName();
          String targetPackage = targetClass.getPackageName();

          // Only check for org.camunda.bpm.engine.impl package
          if (targetPackage.startsWith("org.camunda.bpm.engine.impl")) {
            // Allow ClockUtil - it's a legitimate test utility
            if (targetClassName.equals("org.camunda.bpm.engine.impl.util.ClockUtil")) {
              return; // ClockUtil is allowed
            }

            if (access.getOrigin() instanceof com.tngtech.archunit.core.domain.JavaMethod originMethod &&
                originMethod.isAnnotatedWith("io.camunda.migration.data.qa.util.WhiteBox")) {
              return;
            }

            // Allow ProcessEngineConfigurationImpl from test lifecycle and test methods
            if (access.getOrigin() instanceof com.tngtech.archunit.core.domain.JavaMethod originMethod) {
              boolean isTestInfrastructureMethod = isJUnitTestMethod(originMethod) ||
                  originMethod.getAnnotations().stream()
                  .anyMatch(annotation ->
                      annotation.getRawType().getName().equals("org.junit.jupiter.api.BeforeAll") ||
                      annotation.getRawType().getName().equals("org.junit.jupiter.api.AfterAll") ||
                      annotation.getRawType().getName().equals("org.junit.jupiter.api.BeforeEach") ||
                      annotation.getRawType().getName().equals("org.junit.jupiter.api.AfterEach"));

              if (isTestInfrastructureMethod &&
                  targetClassName.equals("org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl")) {
                return; // ProcessEngineConfigurationImpl access from test methods is allowed
              }
            }

            // Violation: accessing org.camunda.bpm.engine.impl class other than ClockUtil
            String message = String.format(
                "%s accesses %s in (%s.java:%s)",
                access.getDescription(),
                targetClassName,
                javaClass.getSimpleName(),
                access.getLineNumber());
            events.add(SimpleConditionEvent.violated(access, message));
          }
        });
      }
    };
  }

  protected static ArchCondition<JavaClass> notHavePrivateMethods() {
    return new ArchCondition<JavaClass>("not have private methods") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        // Skip enums - their synthetic methods are legitimately private
        if (javaClass.isEnum()) {
          return;
        }

        javaClass.getMethods().stream()
            .filter(method -> method.getModifiers().contains(JavaModifier.PRIVATE))
            .filter(method -> !method.getName().equals("lambda$")) // Exclude lambdas
            .forEach(method -> {
              String message = String.format(
                  "Method <%s.%s()> is private, should be protected or package-protected",
                  javaClass.getName(), method.getName());
              events.add(SimpleConditionEvent.violated(method, message));
            });
      }
    };
  }

  protected static ArchCondition<JavaClass> notHavePrivateFields() {
    return new ArchCondition<JavaClass>("not have private fields") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        // Skip enums - their synthetic fields are legitimately private
        if (javaClass.isEnum()) {
          return;
        }

        javaClass.getFields().stream()
            .filter(field -> field.getModifiers().contains(JavaModifier.PRIVATE))
            .forEach(field -> {
              String message = String.format(
                  "Field <%s.%s> is private, should be protected or package-protected",
                  javaClass.getName(), field.getName());
              events.add(SimpleConditionEvent.violated(field, message));
            });
      }
    };
  }

  protected static ArchCondition<JavaClass> notHavePrivateConstructors() {
    return new ArchCondition<JavaClass>("not have private constructors") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        // Skip enums - their constructors are legitimately private
        if (javaClass.isEnum()) {
          return;
        }

        javaClass.getConstructors().stream()
            .filter(constructor -> constructor.getModifiers().contains(JavaModifier.PRIVATE))
            .forEach(constructor -> {
              String message = String.format(
                  "Constructor <%s()> is private, should be protected or package-protected",
                  javaClass.getName());
              events.add(SimpleConditionEvent.violated(constructor, message));
            });
      }
    };
  }

  protected static ArchCondition<JavaClass> haveLogCapturerField() {
    return new ArchCondition<JavaClass>("have LogCapturer field for log assertions") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        boolean hasLogCapturer = javaClass.getAllFields().stream()
            .anyMatch(field -> field.getRawType().getName().equals("io.github.netmikey.logunit.api.LogCapturer"));

        if (!hasLogCapturer) {
          String message = String.format(
              "Test class <%s> should have a LogCapturer field to verify behavior through logs",
              javaClass.getName());
          events.add(SimpleConditionEvent.violated(javaClass, message));
        }
      }
    };
  }

  protected static ArchCondition<JavaClass> onlyHaveStaticFinalFields() {
    return new ArchCondition<JavaClass>("only have static final fields") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getFields().stream()
            .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC) ||
                           !field.getModifiers().contains(JavaModifier.FINAL))
            .forEach(field -> {
              String message = String.format(
                  "Field <%s.%s> should be static final in Logs class",
                  javaClass.getName(), field.getName());
              events.add(SimpleConditionEvent.violated(field, message));
            });
      }
    };
  }

  protected static ArchCondition<JavaClass> haveTestMethodsWithCorrectNaming() {
    return new ArchCondition<JavaClass>("have test methods following 'should' naming convention") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getMethods().stream()
            .filter(ArchitectureTest::isJUnitTestMethod)
            .filter(method -> !method.getName().startsWith("should"))
            .forEach(method -> {
              String message = String.format(
                  "Test method <%s.%s()> should start with 'should' to clearly express the expected behavior",
                  javaClass.getName(), method.getName());
              events.add(SimpleConditionEvent.violated(method, message));
            });
      }
    };
  }

  protected static ArchCondition<JavaClass> extendAppropriateAbstractTestClass() {
    return new ArchCondition<JavaClass>("extend an appropriate abstract test class") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        boolean hasTestMethods = javaClass.getMethods().stream()
            .anyMatch(ArchitectureTest::isJUnitTestMethod);
        if (javaClass.getModifiers().contains(JavaModifier.ABSTRACT) || !hasTestMethods) {
          return;
        }

        boolean extendsAbstractTest = extendsAppropriateTestBase(javaClass);

        if (!extendsAbstractTest) {
          String message = String.format(
              "Test class <%s> should extend an appropriate abstract test class for proper setup",
              javaClass.getName());
          events.add(SimpleConditionEvent.violated(javaClass, message));
        }
      }
    };
  }

  protected static boolean extendsAppropriateTestBase(JavaClass javaClass) {
    boolean extendsBaseClass = javaClass.getAllRawSuperclasses().stream()
        .anyMatch(superClass ->
            superClass.getName().equals("io.camunda.migration.data.qa.AbstractMigratorTest") ||
            superClass.getName().equals("io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest") ||
            superClass.getName().equals("io.camunda.migration.data.qa.runtime.RuntimeMigrationAbstractTest") ||
            superClass.getSimpleName().endsWith("AbstractTest"));
    boolean isNonStaticNestedTest = javaClass.isAnnotatedWith("org.junit.jupiter.api.Nested") &&
        !javaClass.getModifiers().contains(JavaModifier.STATIC);
    return extendsBaseClass || isNonStaticNestedTest &&
        javaClass.getEnclosingClass().map(ArchitectureTest::extendsAppropriateTestBase).orElse(false);
  }

  protected static boolean isJUnitTestMethod(com.tngtech.archunit.core.domain.JavaMethod method) {
    return method.isAnnotatedWith("org.junit.jupiter.api.Test") ||
        method.isAnnotatedWith("org.junit.jupiter.params.ParameterizedTest");
  }

  protected record DocumentedRule(String id, String enforcementMethod) {
  }

  protected static ArchCondition<JavaClass> beInImplPackageOrTopLevel() {
    return new ArchCondition<JavaClass>("be in impl package or top-level package") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        String packageName = javaClass.getPackageName();

        // Allow top-level io.camunda.migration.data (HistoryMigrator, RuntimeMigrator)
        if (packageName.equals("io.camunda.migration.data")) {
          return;
        }

        // Require impl package (either directly or in subpackages) for all other components
        // This matches: io.camunda.migration.data.impl, io.camunda.migration.data.impl.clients, etc.
        if (!packageName.startsWith("io.camunda.migration.data.impl")) {
          String message = String.format(
              "Component <%s> should be in 'impl' package or top-level io.camunda.migration.data",
              javaClass.getName());
          events.add(SimpleConditionEvent.violated(javaClass, message));
        }
      }
    };
  }

  protected static ArchCondition<JavaClass> notHaveNonFinalStaticFields() {
    return new ArchCondition<JavaClass>("have public static final String constants") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getFields().stream()
            .filter(field -> field.getRawType().isEquivalentTo(String.class))
            .filter(field -> !field.getModifiers().contains(JavaModifier.PUBLIC) ||
                             !field.getModifiers().contains(JavaModifier.STATIC) ||
                             !field.getModifiers().contains(JavaModifier.FINAL))
            .forEach(field -> {
              String message = String.format(
                  "Log constant <%s.%s> should be public static final",
                  javaClass.getName(), field.getName());
              events.add(SimpleConditionEvent.violated(field, message));
            });
      }
    };
  }

  protected static ArchCondition<JavaClass> useLogConstantsInAssertions() {
    return new ArchCondition<JavaClass>("use log constants in assertions instead of string literals") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getCodeUnits().forEach(codeUnit ->
          codeUnit.getCallsFromSelf().stream()
              .filter(call -> call.getTargetOwner().getName().equals("io.github.netmikey.logunit.api.LogCapturer"))
              .filter(call -> call.getName().equals("assertContains") || call.getName().equals("assertDoesNotContain"))
              .forEach(call -> {
                // Check if the calling method uses formatMessage()
                // This is a heuristic: if we see logs.assertContains("literal string"), it's likely wrong
                // We want to see: logs.assertContains(formatMessage(SomeLogs.CONSTANT, ...))

                int lineNumber = call.getLineNumber();
                boolean likelyUsesStringLiteral = checkIfMethodUsesStringLiteral(codeUnit);

                if (likelyUsesStringLiteral) {
                  String message = String.format(
                      "Log assertion at %s.%s():%d should use formatMessage() with a constant from " +
                      "HistoryMigratorLogs or RuntimeMigratorLogs instead of string literals. " +
                      "Example: logs.assertContains(formatMessage(HistoryMigratorLogs.SKIPPING_INSTANCE_MISSING_DEFINITION, args...))",
                      javaClass.getName(), codeUnit.getName(), lineNumber);
                  events.add(SimpleConditionEvent.violated(call, message));
                }
              })
        );
      }

      private boolean checkIfMethodUsesStringLiteral(com.tngtech.archunit.core.domain.JavaCodeUnit codeUnit) {
        // This is a heuristic check. We look at the method to see if it's using
        // formatMessage() or directly passing a string.
        // If the method calls formatMessage(), it's likely using constants properly

        // Check if there's a field access to a Logs class in this method
        boolean accessesLogsConstant = codeUnit.getFieldAccesses().stream()
            .anyMatch(fieldAccess -> {
              JavaClass targetClass = fieldAccess.getTargetOwner();
              return targetClass.getSimpleName().endsWith("Logs");
            });

        // Also check if formatMessage is being called (indirect evidence of using constants)
        boolean callsFormatMessage = codeUnit.getCallsFromSelf().stream()
            .anyMatch(methodCall -> methodCall.getName().equals("formatMessage"));

        // If neither condition is true for this method/code unit, it's likely using string literals
        // Note: This is conservative - it may have false positives, but that's better than false negatives
        return !accessesLogsConstant && !callsFormatMessage;
      }
    };
  }

  protected static ArchCondition<JavaClass> haveTestSuffixIfContainingTestMethods() {
    return new ArchCondition<JavaClass>("have 'Test' suffix if containing test methods") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        boolean hasTestMethods = javaClass.getMethods().stream()
            .anyMatch(ArchitectureTest::isJUnitTestMethod);

        if (hasTestMethods && !javaClass.getSimpleName().endsWith("Test")) {
          String message = String.format(
              "Class <%s> contains @Test methods but does not end with 'Test' suffix",
              javaClass.getName());
          events.add(SimpleConditionEvent.violated(javaClass, message));
        }
      }
    };
  }
}
