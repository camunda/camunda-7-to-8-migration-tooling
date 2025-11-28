/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.runtime.tenant;

import static io.camunda.migrator.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migrator.constants.MigratorConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.migrator.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.NO_C8_TENANT_DEPLOYMENT_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.TENANT_ID_ERROR;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byKey;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.migrator.MigratorMode;
import io.camunda.migrator.RuntimeMigrator;
import io.camunda.migrator.exception.RuntimeMigratorException;
import io.camunda.migrator.qa.runtime.RuntimeMigrationAbstractTest;
import io.github.netmikey.logunit.api.LogCapturer;
import io.grpc.StatusRuntimeException;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = { "camunda.process-test.multi-tenancy-enabled=true",
    "camunda.migrator.tenant-ids=tenant-1,tenant-2" })
class MultiTenancyRetryTest extends RuntimeMigrationAbstractTest {

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
    // create tenant
    client.newCreateTenantCommand().tenantId(TENANT_ID_1).name(TENANT_ID_1).execute();
    // assign the default user to the tenant
    client.newAssignUserToTenantCommand().username(DEFAULT_USERNAME).tenantId(TENANT_ID_1).execute();
  }

  @Test
  public void shouldMigrateProcessInstanceWithTenantWhenAdded() {
    // given
    deployer.deployCamunda7Process(SIMPLE_PROCESS_BPMN, TENANT_ID_2);
    deployer.deployCamunda7Process(SIMPLE_PROCESS_BPMN, TENANT_ID_1);

    var definitionWithTenant1 = repositoryService.createProcessDefinitionQuery()
        .tenantIdIn(TENANT_ID_1)
        .singleResult()
        .getId();
    var definitionWithTenant2 = repositoryService.createProcessDefinitionQuery()
        .tenantIdIn(TENANT_ID_2)
        .singleResult()
        .getId();
    String c7WithT1 = runtimeService.startProcessInstanceById(definitionWithTenant1).getId();
    String c7WithT2 = runtimeService.startProcessInstanceById(definitionWithTenant2).getId();

    try {
      runtimeMigrator.start();
    } catch (RuntimeMigratorException e) {
      // expected to throw an exception
      assertThat(e.getCause().toString()).contains("user is not authorized");
    }

    assertThatProcessInstanceCountIsEqualTo(0);

    // when
    client.newCreateTenantCommand().tenantId(TENANT_ID_2).name(TENANT_ID_2).execute();
    client.newAssignUserToTenantCommand().username(DEFAULT_USERNAME).tenantId(TENANT_ID_2).execute();
    deployer.deployCamunda8Process(SIMPLE_PROCESS_BPMN, TENANT_ID_2);

    runtimeMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(1);
    var c8ProcessInstance = client.newProcessInstanceSearchRequest()
        .filter(f -> f.tenantId(TENANT_ID_2))
        .send()
        .join()
        .items()
        .getFirst();
    assertThat(c8ProcessInstance).isNotNull();
    logs.assertContains(formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, c7WithT1,
        formatMessage(NO_C8_TENANT_DEPLOYMENT_ERROR, SIMPLE_PROCESS_ID, TENANT_ID_1, c7WithT1)));
  }

}