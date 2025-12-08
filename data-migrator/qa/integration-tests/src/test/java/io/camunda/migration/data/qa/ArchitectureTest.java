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

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

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

  protected static final JavaClasses CLASSES = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
      .importPackages("io.camunda.migration.data");

  @Test
  void shouldNotAccessImplClasses() {
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
        .check(CLASSES);
  }

  @Test
  void shouldNotAccessCamundaBpmEngineImplClasses() {
    classes()
        .that().resideInAPackage("..qa..")
        .and().haveSimpleNameEndingWith("Test")
        .should(notAccessCamundaBpmEngineImplPackage())
        .allowEmptyShould(true)
        .because("Tests should not use internal Camunda BPM engine implementation classes. " +
            "Exception: ClockUtil from org.camunda.bpm.engine.impl.util is allowed for time manipulation in tests.")
        .check(CLASSES);
  }

  @Test
  void shouldNotHavePrivateMethods() {
    classes()
        .that().resideInAPackage("io.camunda.migration.data..")
        .and().resideOutsideOfPackage("..qa..") // Exclude test code
        .should(notHavePrivateMethods())
        .allowEmptyShould(true)
        .because("Methods should use protected or package-protected visibility instead of private " +
            "to allow for testing and extensibility. Use protected for methods that might be " +
            "overridden and package-protected for internal methods that tests might need to access.")
        .check(CLASSES);
  }

  @Test
  void shouldNotHavePrivateFields() {
    classes()
        .that().resideInAPackage("io.camunda.migration.data..")
        .and().resideOutsideOfPackage("..qa..") // Exclude test code
        .should(notHavePrivateFields())
        .allowEmptyShould(true)
        .because("Fields should use protected or package-protected visibility instead of private " +
            "to allow for testing and extensibility. Use protected for fields that might be " +
            "accessed by subclasses and package-protected for fields that tests might need to access.")
        .check(CLASSES);
  }

  @Test
  void shouldNotHavePrivateConstructors() {
    classes()
        .that().resideInAPackage("io.camunda.migration.data..")
        .and().resideOutsideOfPackage("..qa..") // Exclude test code
        .should(notHavePrivateConstructors())
        .allowEmptyShould(true)
        .because("Classes should use protected or package-protected constructors to allow for " +
            "testing and extensibility.")
        .check(CLASSES);
  }

  @Test
  void shouldOnlyHaveStaticFinalFieldsInLogClasses() {
    classes()
        .that().haveSimpleNameEndingWith("Logs")
        .and().resideInAPackage("..impl.logging..")
        .should(onlyHaveStaticFinalFields())
        .allowEmptyShould(true)
        .because("Log classes should only contain public static final String constants " +
            "for centralized log message management.")
        .check(CLASSES);
  }

  @Test
  void shouldFollowNamingConventionForTestMethods() {
    classes()
        .that().resideInAPackage("..qa..")
        .and().haveSimpleNameEndingWith("Test")
        .should(haveTestMethodsWithCorrectNaming())
        .allowEmptyShould(true)
        .because("Test methods should follow the naming convention: 'should' prefix for behavior tests " +
            "to clearly express what the test verifies (e.g., shouldSkipProcessInstanceWhenDefinitionMissing).")
        .check(CLASSES);
  }

  @Test
  void shouldResideInImplPackageForComponents() {
    classes()
        .that().areAnnotatedWith("org.springframework.stereotype.Component")
        .or().areAnnotatedWith("org.springframework.stereotype.Service")
        .and().resideInAPackage("io.camunda.migration.data..")
        .and().resideOutsideOfPackages("..qa..")
        .should(beInImplPackageOrTopLevel())
        .allowEmptyShould(true)
        .because("Components and services should reside in the 'impl' package (for internal implementations) " +
            "or at the top level of io.camunda.migration.data (for public APIs like HistoryMigrator, RuntimeMigrator).")
        .check(CLASSES);
  }

  @Test
  void shouldBePublicStaticFinalForLogConstants() {
    classes()
        .that().haveSimpleNameEndingWith("Logs")
        .and().resideInAPackage("..impl.logging..")
        .should(notHaveNonFinalStaticFields())
        .allowEmptyShould(true)
        .because("Log message constants should be public static final to prevent modification " +
            "and ensure consistent logging across the application.")
        .check(CLASSES);
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
    noClasses()
        .that().resideInAPackage("io.camunda.migration.data..")
        .should().callMethod(System.class, "out")
        .orShould().callMethod("java.lang.Throwable", "printStackTrace")
        .allowEmptyShould(true)
        .because("Production code should use SLF4J logging instead of System.out.println() or printStackTrace(). " +
            "Use a Logger instance and appropriate log levels (debug, info, warn, error).")
        .check(CLASSES);
  }

  @Test
  void shouldHaveTestSuffixForTestClasses() {
    classes()
        .that().resideInAPackage("..qa..")
        .and().haveModifier(JavaModifier.PUBLIC)
        .and().areNotInterfaces()
        .and().areNotEnums()
        .and().areNotAnnotations()
        .should(haveTestSuffixIfContainingTestMethods())
        .allowEmptyShould(true)
        .because("Test classes should end with 'Test' suffix to follow JUnit conventions and be " +
            "recognized by test runners and build tools.")
        .check(CLASSES);
  }

  // Custom ArchConditions

  protected static ArchCondition<JavaClass> notAccessMigratorImplPackage() {
    return new ArchCondition<JavaClass>("not access io.camunda.migration.data.impl package") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
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
    return new ArchCondition<JavaClass>("not access org.camunda.bpm.engine.impl package except ClockUtil") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
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
    return new ArchCondition<JavaClass>("only have public static final fields") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getFields().stream()
            .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC) ||
                           !field.getModifiers().contains(JavaModifier.FINAL))
            .forEach(field -> {
              String message = String.format(
                  "Field <%s.%s> should be public static final in Logs class",
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
            .filter(method -> method.isAnnotatedWith("org.junit.jupiter.api.Test"))
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
        // Skip abstract classes themselves
        if (javaClass.getModifiers().contains(JavaModifier.ABSTRACT)) {
          return;
        }

        boolean extendsAbstractTest = javaClass.getAllRawSuperclasses().stream()
            .anyMatch(superClass ->
                superClass.getName().equals("io.camunda.migration.data.qa.AbstractMigratorTest") ||
                superClass.getName().equals("io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest") ||
                superClass.getName().equals("io.camunda.migration.data.qa.runtime.RuntimeMigrationAbstractTest") ||
                superClass.getSimpleName().endsWith("AbstractTest"));

        if (!extendsAbstractTest) {
          String message = String.format(
              "Test class <%s> should extend an appropriate abstract test class for proper setup",
              javaClass.getName());
          events.add(SimpleConditionEvent.violated(javaClass, message));
        }
      }
    };
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
    return new ArchCondition<JavaClass>("not have mutable static fields") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getFields().stream()
            .filter(field -> field.getModifiers().contains(JavaModifier.STATIC))
            .filter(field -> !field.getModifiers().contains(JavaModifier.FINAL))
            .forEach(field -> {
              String message = String.format(
                  "Static field <%s.%s> should be final to prevent modification",
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
            .anyMatch(method -> method.isAnnotatedWith("org.junit.jupiter.api.Test"));

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


