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
import static io.camunda.migration.data.impl.logging.RuntimeValidatorLogs.TENANT_ID_ERROR;
import static io.camunda.migration.data.util.LogMessageFormatter.formatMessage;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.migration.data.RuntimeMigrator;
import io.camunda.migration.data.qa.runtime.RuntimeMigrationAbstractTest;
import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = { "camunda.process-test.multi-tenancy-enabled=true" })
class MultiTenancyNoConfigTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  // Test constants
  protected static final String DEFAULT_USERNAME = "demo";
  protected static final String TENANT_ID_1 = "tenant-1";
  protected static final String TENANT_ID_2 = "tenant-2";
  protected static final String SIMPLE_PROCESS_BPMN = "simpleProcess.bpmn";
  protected static final String SIMPLE_PROCESS_ID = "simpleProcess";

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
  public void shouldSkipProcessInstanceWithTenantMismatch() {
    // given
    deployer.deployProcessInC7AndC8(SIMPLE_PROCESS_BPMN, TENANT_ID_1);

    String c7ProcessInstanceId = runtimeService.startProcessInstanceByKey(SIMPLE_PROCESS_ID,
        Variables.putValue("myVar", 1234)).getId();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);
    logs.assertContains(
        formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, c7ProcessInstanceId,
            formatMessage(TENANT_ID_ERROR, TENANT_ID_1)));
  }

  @Test
  public void shouldMigrateProcessInstanceWithoutTenant() {
    // given
    deployer.deployProcessInC7AndC8(SIMPLE_PROCESS_BPMN);

    String c7ProcessInstanceId = runtimeService.startProcessInstanceByKey(SIMPLE_PROCESS_ID,
        Variables.putValue("myVar", 1234)).getId();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(1);

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
  public void shouldMigrateProcessInstancesOnlyWithoutTenant() {
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
    String instanceWithTenant1 = runtimeService.startProcessInstanceById(definitionWithTenant1, Variables.putValue("myVar", 1))
        .getId();
    String instanceWithTenant2 = runtimeService.startProcessInstanceById(definitionWithTenant2, Variables.putValue("myVar", 2))
        .getId();
    String c7instance = runtimeService.startProcessInstanceById(definitionWithoutTenant,
        Variables.putValue("myVar", 10)).getId();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(1);
    assertProcessInstanceState(C8_DEFAULT_TENANT, c7instance, 10);

    // Verify the two tenant instances were skipped via logs
    logs.assertContains(formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, instanceWithTenant1,
        formatMessage(TENANT_ID_ERROR, TENANT_ID_1)));
    logs.assertContains(formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, instanceWithTenant2,
        formatMessage(TENANT_ID_ERROR, TENANT_ID_2)));
  }

  @Test
  public void shouldSkipProcessInstanceWhenProcessDefinitionHasNoTenant() {
    // given
    deployer.deployCamunda7Process(SIMPLE_PROCESS_BPMN, TENANT_ID_1);
    deployer.deployCamunda8Process(SIMPLE_PROCESS_BPMN);

    String c7ProcessInstanceId = runtimeService.startProcessInstanceByKey(SIMPLE_PROCESS_ID).getId();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);
    logs.assertContains(
        formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, c7ProcessInstanceId,
            formatMessage(TENANT_ID_ERROR, TENANT_ID_1)));
  }

  @Test
  public void shouldSkipProcessInstanceWhenTenantNotConfigured() {
    // given
    deployer.deployCamunda7Process(SIMPLE_PROCESS_BPMN, TENANT_ID_2); // not configured
    deployer.deployCamunda8Process(SIMPLE_PROCESS_BPMN, TENANT_ID_1);

    String c7ProcessInstanceId = runtimeService.startProcessInstanceByKey(SIMPLE_PROCESS_ID).getId();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);
    logs.assertContains(
        formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, c7ProcessInstanceId,
            formatMessage(TENANT_ID_ERROR, TENANT_ID_2)));
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