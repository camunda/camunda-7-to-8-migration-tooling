/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.runtime.jobtype;

import static io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.migration.data.runtime.RuntimeMigrationAbstractTest;
import io.camunda.process.test.api.CamundaAssert;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.validation-job-type==if legacyId != null then \"migrator\" else \"noop\""
})
public class ExternalTrafficWithValidationJobTypeTest extends RuntimeMigrationAbstractTest {

  @Test
  public void shouldHandleExternallyStartedMigratorJobsGracefully() {
    // given
    deployer.deployProcessInC7AndC8("migratorListenerFeel.bpmn");

    String id = runtimeService.startProcessInstanceByKey("migratorListenerFeel").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    camundaClient.newCreateInstanceCommand().bpmnProcessId("migratorListenerFeel").latestVersion().execute();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(2);

    ActivateJobsResponse response = camundaClient.newActivateJobsCommand()
        .jobType("noop")
        .maxJobsToActivate(5)
        .execute();
    assertThat(response.getJobs()).hasSize(1);

    CamundaAssert.assertThat(byElementId("Activity_016bbjm")).isCreated();
  }

}
