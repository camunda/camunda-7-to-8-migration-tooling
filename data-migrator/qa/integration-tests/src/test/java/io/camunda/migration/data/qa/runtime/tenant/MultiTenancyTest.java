/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime.tenant;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.constants.MigratorConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.migration.data.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR;
import static io.camunda.migration.data.impl.logging.RuntimeValidatorLogs.NO_C8_TENANT_DEPLOYMENT_ERROR;
import static io.camunda.migration.data.impl.logging.RuntimeValidatorLogs.TENANT_ID_ERROR;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.RuntimeMigrationExtension;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Combined test class for testing multi-tenancy functionality in the migrator.
 * Includes tests for both configurations:
 * - Without default tenant configuration
 * - With default tenant configuration (&lt;default&gt;)
 */
public class MultiTenancyTest {

  // Shared test constants
  protected static final String DEFAULT_USERNAME = "demo";
  protected static final String TENANT_ID_1 = "tenant-1";
  protected static final String TENANT_ID_2 = "tenant-2";
  protected static final String TENANT_ID_3 = "tenant-3";
  protected static final String SIMPLE_PROCESS_BPMN = "simpleProcess.bpmn";
  protected static final String SIMPLE_PROCESS_ID = "simpleProcess";

  /**
   * Base class for shared test logic between tenant configuration scenarios
   */
  abstract static class MultiTenancyTestBase extends AbstractMigratorTest {

    @RegisterExtension
    protected final RuntimeMigrationExtension runtimeMigration = new RuntimeMigrationExtension();

    @RegisterExtension
    protected final LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

    @Autowired
    protected CamundaClient client;

    @BeforeEach
    void setupTenants() {
      // create tenants
      client.newCreateTenantCommand().tenantId(TENANT_ID_1).name(TENANT_ID_1).send().join();
      client.newCreateTenantCommand().tenantId(TENANT_ID_2).name(TENANT_ID_2).send().join();
      // assign the default user to the tenants
      client.newAssignUserToTenantCommand().username(DEFAULT_USERNAME).tenantId(TENANT_ID_1).send().join();
      client.newAssignUserToTenantCommand().username(DEFAULT_USERNAME).tenantId(TENANT_ID_2).send().join();
    }

    @Test
    public void shouldMigrateProcessInstanceWithTenant() {
      // given
      deployer.deployProcessInC7AndC8(SIMPLE_PROCESS_BPMN, TENANT_ID_1);

      String c7ProcessInstanceId = runtimeService.startProcessInstanceByKey(SIMPLE_PROCESS_ID,
          Variables.putValue("myVar", 1234)).getId();

      // when
      runtimeMigration.getMigrator().start();

      // then
      runtimeMigration.assertThatProcessInstanceCountIsEqualTo(1);
      var c8ProcessInstanceTenant = client.newProcessInstanceSearchRequest()
          .filter(f -> f.processDefinitionId(SIMPLE_PROCESS_ID))
          .send()
          .join()
          .items()
          .getFirst();
      var c8VariableTenant = client.newVariableSearchRequest()
          .filter(f -> f.name("myVar"))
          .send()
          .join()
          .items()
          .getFirst()
          .getTenantId();
      assertThat(c8ProcessInstanceTenant.getTenantId()).isEqualTo(TENANT_ID_1);
      assertThat(c8VariableTenant).isEqualTo(TENANT_ID_1);
      assertThat(byKey(c8ProcessInstanceTenant.getProcessInstanceKey())).isActive()
          .hasActiveElements("userTask1")
          .hasVariable(LEGACY_ID_VAR_NAME, c7ProcessInstanceId)
          .hasVariable("myVar", 1234);
    }

    @Test
    public void shouldMigrateProcessInstanceWithoutTenant() {
      // given
      deployer.deployProcessInC7AndC8(SIMPLE_PROCESS_BPMN);

      String c7ProcessInstanceId = runtimeService.startProcessInstanceByKey(SIMPLE_PROCESS_ID,
          Variables.putValue("myVar", 1234)).getId();

      // when
      runtimeMigration.getMigrator().start();

      // then
      runtimeMigration.assertThatProcessInstanceCountIsEqualTo(1);

      assertProcessInstanceState(C8_DEFAULT_TENANT, c7ProcessInstanceId, 1234);
      var c8VariableTenant = client.newVariableSearchRequest()
          .filter(f -> f.name("myVar"))
          .send()
          .join()
          .items()
          .getFirst()
          .getTenantId();
      assertThat(c8VariableTenant).isEqualTo(C8_DEFAULT_TENANT);
    }

    @Test
    public void shouldMigrateProcessInstancesWithAndWithoutTenant() {
      // given
      deployer.deployProcessInC7AndC8(SIMPLE_PROCESS_BPMN);
      deployer.deployProcessInC7AndC8(SIMPLE_PROCESS_BPMN, TENANT_ID_1);
      deployer.deployProcessInC7AndC8(SIMPLE_PROCESS_BPMN, TENANT_ID_2);

      var definitionWithTenant1 = repositoryService.createProcessDefinitionQuery()
          .tenantIdIn(TENANT_ID_1)
          .singleResult()
          .getId();
      var definitionWithTenant2 = repositoryService.createProcessDefinitionQuery()
          .tenantIdIn(TENANT_ID_2)
          .singleResult()
          .getId();
      var definitionWithoutTenant = repositoryService.createProcessDefinitionQuery()
          .withoutTenantId()
          .singleResult()
          .getId();
      String c7WithT1 = runtimeService.startProcessInstanceById(definitionWithTenant1, Variables.putValue("myVar", 1))
          .getId();
      String c7WithT2 = runtimeService.startProcessInstanceById(definitionWithTenant2, Variables.putValue("myVar", 2))
          .getId();
      String c7instance = runtimeService.startProcessInstanceById(definitionWithoutTenant,
          Variables.putValue("myVar", 10)).getId();

      // when
      runtimeMigration.getMigrator().start();

      // then
      runtimeMigration.assertThatProcessInstanceCountIsEqualTo(3);
      assertProcessInstanceState(TENANT_ID_1, c7WithT1, 1);
      assertProcessInstanceState(TENANT_ID_2, c7WithT2, 2);
      assertProcessInstanceState(C8_DEFAULT_TENANT, c7instance, 10);
    }

    @Test
    public void shouldSkipProcessInstanceWhenProcessDefinitionHasNoTenant() {
      // given
      deployer.deployCamunda7Process(SIMPLE_PROCESS_BPMN, TENANT_ID_1);
      deployer.deployCamunda8Process(SIMPLE_PROCESS_BPMN);

      String c7ProcessInstanceId = runtimeService.startProcessInstanceByKey(SIMPLE_PROCESS_ID).getId();

      // when
      runtimeMigration.getMigrator().start();

      // then
      runtimeMigration.assertThatProcessInstanceCountIsEqualTo(0);
      logs.assertContains(
          String.format(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("{}", "%s"), c7ProcessInstanceId,
              String.format(NO_C8_TENANT_DEPLOYMENT_ERROR, SIMPLE_PROCESS_ID, TENANT_ID_1, c7ProcessInstanceId)));
    }

    @Test
    public void shouldSkipProcessInstanceWhenProcessDefinitionHasDifferentTenant() {
      // given
      deployer.deployCamunda7Process(SIMPLE_PROCESS_BPMN, TENANT_ID_1);
      deployer.deployCamunda8Process(SIMPLE_PROCESS_BPMN, TENANT_ID_2);

      String c7ProcessInstanceId = runtimeService.startProcessInstanceByKey(SIMPLE_PROCESS_ID).getId();

      // when
      runtimeMigration.getMigrator().start();

      // then
      runtimeMigration.assertThatProcessInstanceCountIsEqualTo(0);
      logs.assertContains(
          String.format(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("{}", "%s"), c7ProcessInstanceId,
              String.format(NO_C8_TENANT_DEPLOYMENT_ERROR, SIMPLE_PROCESS_ID, TENANT_ID_1, c7ProcessInstanceId)));
    }

    @Test
    public void shouldSkipProcessInstanceWhenTenantNotConfigured() {
      // given
      deployer.deployCamunda7Process(SIMPLE_PROCESS_BPMN, TENANT_ID_3); // not configured
      deployer.deployCamunda8Process(SIMPLE_PROCESS_BPMN, TENANT_ID_1);

      String c7ProcessInstanceId = runtimeService.startProcessInstanceByKey(SIMPLE_PROCESS_ID).getId();

      // when
      runtimeMigration.getMigrator().start();

      // then
      runtimeMigration.assertThatProcessInstanceCountIsEqualTo(0);
      logs.assertContains(
          String.format(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR.replace("{}", "%s"), c7ProcessInstanceId,
              String.format(TENANT_ID_ERROR, TENANT_ID_3)));
    }

    protected void assertProcessInstanceState(String tenantId, String c7instance, int variableValue) {
      var c8ProcessInstance = client.newProcessInstanceSearchRequest()
          .filter(f -> f.tenantId(tenantId))
          .send()
          .join()
          .items()
          .getFirst();
      assertThat(c8ProcessInstance).isNotNull();
      assertThat(byKey(c8ProcessInstance.getProcessInstanceKey())).isActive()
          .hasActiveElements("userTask1")
          .hasVariable(LEGACY_ID_VAR_NAME, c7instance)
          .hasVariable("myVar", variableValue);
    }
  }

  /**
   * Tests for multi-tenancy configuration without default tenant support.
   * Configuration: "camunda.migrator.tenant-ids=tenant-1,tenant-2"
   */
  @Nested
  @CamundaSpringProcessTest
  @TestPropertySource(properties = { "camunda.process-test.multi-tenancy-enabled=true",
      "camunda.migrator.tenant-ids=tenant-1,tenant-2" })
  class WithoutDefaultTenantConfiguration extends MultiTenancyTestBase {
    // All test methods are inherited from MultiTenancyTestBase
  }

  /**
   * Tests for multi-tenancy configuration with default tenant support.
   * Configuration: "camunda.migrator.tenant-ids=tenant-1,tenant-2,&lt;default&gt;"
   */
  @Nested
  @CamundaSpringProcessTest
  @TestPropertySource(properties = { "camunda.process-test.multi-tenancy-enabled=true",
      "camunda.migrator.tenant-ids=tenant-1,tenant-2,<default>" })
  class WithDefaultTenantConfiguration extends MultiTenancyTestBase {
    // All test methods are inherited from MultiTenancyTestBase
  }
}