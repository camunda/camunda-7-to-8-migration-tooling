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
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class BusinessKeyMigrationTest extends RuntimeMigrationAbstractTest {

  static Stream<Arguments> businessKeyScenarios() {
    return Stream.of(
        Arguments.of(null, null, true),
      Arguments.of("", null, false),
        Arguments.of("myBusinessKey", "myBusinessKey", false));
  }

  @ParameterizedTest
  @MethodSource("businessKeyScenarios")
  public void shouldMigrateProcessInstanceWithBusinessKey(String businessKey,
                                                           String expectedBusinessId,
                                                           boolean startWithoutBusinessKey) {
    // given
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    if (startWithoutBusinessKey) {
      runtimeService.startProcessInstanceByKey("simpleProcess");
    } else {
      runtimeService.startProcessInstanceByKey("simpleProcess", businessKey);
    }

    // when
    runtimeMigrator.start();

    // then
    Awaitility.await().ignoreException(ClientException.class).untilAsserted(() -> {
      List<ProcessInstance> c8Instances = camundaClient.newProcessInstanceSearchRequest().execute().items();
      assertThat(c8Instances).hasSize(1);
      assertThat(c8Instances.getFirst().getBusinessId()).isEqualTo(expectedBusinessId);
    });
  }
}
