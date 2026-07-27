/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.architecturefixture;

import io.camunda.migration.data.impl.VariableService;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import java.util.List;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

public class InvalidTestingFixtures {

  public static Class<?> packagePrivateMissingSuffix() {
    return PackagePrivateMissingSuffix.class;
  }

  public static class ImplementationAccessTest {

    public void access(VariableService variableService) {
      variableService.processVariablesToActivityGroups(List.of());
    }
  }

  public static class EngineImplAccessTest {

    public void access(ProcessEngineConfigurationImpl configuration) {
      configuration.getHistoryLevel();
    }
  }

  public static class EngineImplLifecycleAccessTest {

    @BeforeAll
    static void beforeAll(ProcessEngineConfigurationImpl configuration) {
      configuration.getHistoryLevel();
    }

    @AfterAll
    static void afterAll(ProcessEngineConfigurationImpl configuration) {
      configuration.getHistoryLevel();
    }

    @BeforeEach
    void beforeEach(ProcessEngineConfigurationImpl configuration) {
      configuration.getHistoryLevel();
    }

    @AfterEach
    void afterEach(ProcessEngineConfigurationImpl configuration) {
      configuration.getHistoryLevel();
    }
  }

  public static class BadMethodNameTest {

    @Test
    void invalidName() {
    }
  }

  public static class BadParameterizedMethodNameTest {

    @ParameterizedTest
    void invalidParameterizedName(String value) {
    }
  }

  public static class StandaloneMigrationTest {

    @Test
    void shouldRequireMigrationTestBaseClass() {
    }
  }

  public static class StandaloneParameterizedMigrationTest {

    @ParameterizedTest
    void shouldRequireMigrationTestBaseClass(String value) {
    }
  }

  public static class StaticNestedContainerTest extends AbstractMigratorTest {

    @Nested
    public static class StaticNestedMigrationTest {

      @Test
      void shouldNotInheritInfrastructureFromEnclosingClass() {
      }
    }
  }

  public static class MissingSuffix {

    @Test
    void shouldRequireTestClassSuffix() {
    }
  }

  public static class MissingParameterizedSuffix {

    @ParameterizedTest
    void shouldRequireTestClassSuffix(String value) {
    }
  }

  static class PackagePrivateMissingSuffix {

    @Test
    void shouldRequireTestClassSuffix() {
    }
  }
}
