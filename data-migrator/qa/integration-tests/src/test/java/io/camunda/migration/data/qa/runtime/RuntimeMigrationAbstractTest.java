/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Variable;
import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.RuntimeMigrationExtension;
import io.camunda.process.test.api.CamundaSpringProcessTest;

import java.util.Optional;
import org.junit.jupiter.api.extension.RegisterExtension;

@CamundaSpringProcessTest
public abstract class RuntimeMigrationAbstractTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final RuntimeMigrationExtension runtimeMigration = new RuntimeMigrationExtension();

  // Convenience accessors for commonly used beans

  protected RuntimeMigrator getRuntimeMigrator() {
    return runtimeMigration.getMigrator();
  }

  protected DbClient getDbClient() {
    return runtimeMigration.getDbClient();
  }

  protected CamundaClient getCamundaClient() {
    return runtimeMigration.getCamundaClient();
  }

  protected Optional<Variable> getVariableByScope(Long processInstanceKey, Long scopeKey, String variableName) {
    return runtimeMigration.getVariableByScope(processInstanceKey, scopeKey, variableName);
  }

  protected void assertThatProcessInstanceCountIsEqualTo(int expected) {
    runtimeMigration.assertThatProcessInstanceCountIsEqualTo(expected);
  }

}