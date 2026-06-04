/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.response.ProcessInstance;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class BusinessKeyMigrationTest extends RuntimeMigrationAbstractTest {

  @Test
  public void shouldMigrateProcessInstanceWithBusinessKey() {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("simpleProcess", "myBusinessKey");

    // when
    runtimeMigrator.start();

    // then
    Awaitility.await().ignoreException(ClientException.class).untilAsserted(() -> {
      List<ProcessInstance> c8Instances = camundaClient.newProcessInstanceSearchRequest().execute().items();
      assertThat(c8Instances).hasSize(1);
      assertThat(c8Instances.getFirst().getBusinessId()).isEqualTo("myBusinessKey");
    });
  }

  @Test
  public void shouldMigrateProcessInstanceWithoutBusinessKey() {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("simpleProcess");

    // when
    runtimeMigrator.start();

    // then
    Awaitility.await().ignoreException(ClientException.class).untilAsserted(() -> {
      List<ProcessInstance> c8Instances = camundaClient.newProcessInstanceSearchRequest().execute().items();
      assertThat(c8Instances).hasSize(1);
      assertThat(c8Instances.getFirst().getBusinessId()).isNull();
    });
  }
}
