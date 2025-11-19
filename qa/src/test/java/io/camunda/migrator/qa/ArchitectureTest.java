/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

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
 *   <li>Tests use natural skip scenarios instead of manipulating internal state</li>
 * </ul>
 */
class ArchitectureTest {

  protected static final JavaClasses CLASSES = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_ARCHIVES)
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
      .importPackages("io.camunda.migrator");

  @Test
  void shoutNotAccessImplClasses() {
    classes()
        .that().resideInAPackage("..qa..")
        .should(notAccessMigratorImplPackage())
        .because("Tests should not access internal implementation details from io.camunda.migrator.impl package. " +
            "Use log assertions and C8 API queries instead. " +
            "Exceptions: (1) Constants (static final fields) and enums from impl classes are allowed, including their methods (e.g., enum.getDisplayName()), " +
            "(2) Methods annotated with @BeforeEach or @AfterEach can access impl classes for test setup/cleanup.")
        .check(CLASSES);
  }

  @Test
  void shouldNotAccessCamundaBpmEngineImplClasses() {
    classes()
        .that().resideInAPackage("..qa..")
        .and().haveSimpleNameEndingWith("Test")
        .should(notAccessCamundaBpmEngineImplPackage())
        .because("Tests should not use internal Camunda BPM engine implementation classes. " +
            "Exception: ClockUtil from org.camunda.bpm.engine.impl.util is allowed for time manipulation in tests.")
        .check(CLASSES);
  }

  @Test
  void shouldNotHavePrivateMethods() {
    classes()
        .that().resideInAPackage("io.camunda.migrator..")
        .and().resideOutsideOfPackage("..qa..") // Exclude test code
        .should(notHaveprotectedMethods())
        .because("Methods should use protected or package-protected visibility instead of protected " +
            "to allow for testing and extensibility. Use protected for methods that might be " +
            "overridden and package-protected for internal methods that tests might need to access.")
        .check(CLASSES);
  }

  @Test
  void shouldNotHavePrivateFields() {
    classes()
        .that().resideInAPackage("io.camunda.migrator..")
        .and().resideOutsideOfPackage("..qa..") // Exclude test code
        .should(notHaveprotectedFields())
        .because("Fields should use protected or package-protected visibility instead of protected " +
            "to allow for testing and extensibility. Use protected for fields that might be " +
            "accessed by subclasses and package-protected for fields that tests might need to access.")
        .check(CLASSES);
  }

  @Test
  void shouldNotHavePrivateConstructors() {
    classes()
        .that().resideInAPackage("io.camunda.migrator..")
        .and().resideOutsideOfPackage("..qa..") // Exclude test code
        .should(notHaveprotectedConstructors())
        .because("Classes should use protected or package-protected constructors to allow for " +
            "testing and extensibility.")
        .check(CLASSES);
  }

  // Custom ArchConditions

  protected static ArchCondition<JavaClass> notAccessMigratorImplPackage() {
    return new ArchCondition<JavaClass>("not access io.camunda.migrator.impl package") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getAccessesFromSelf().forEach(access -> {
          JavaClass targetClass = access.getTargetOwner();
          String targetClassName = targetClass.getName();
          String targetPackage = targetClass.getPackageName();

          // Only check for io.camunda.migrator.impl package (our implementation details)
          if (targetPackage.startsWith("io.camunda.migrator.impl")) {
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

            // Violation: calling method or constructor from io.camunda.migrator.impl package
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

  protected static ArchCondition<JavaClass> notHaveprotectedMethods() {
    return new ArchCondition<JavaClass>("not have protected methods") {
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

  protected static ArchCondition<JavaClass> notHaveprotectedFields() {
    return new ArchCondition<JavaClass>("not have protected fields") {
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

  protected static ArchCondition<JavaClass> notHaveprotectedConstructors() {
    return new ArchCondition<JavaClass>("not have protected constructors") {
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
}


