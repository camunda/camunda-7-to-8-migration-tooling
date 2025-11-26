/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(properties = {
    "camunda.client.grpc-address=http://helloworld:33333",
    "camunda.client.rest-address=http://helloworld:44444"
})
public class CamundaClientConfigurationTest {

  @Autowired
  protected CamundaClient camundaClient;

  @Test
  public void shouldConfigureCamundaClient() {
    // given

    // when
    CamundaClientConfiguration configuration = camundaClient.getConfiguration();

    // then
    assertThat(configuration.getGrpcAddress()).isEqualTo(URI.create("http://helloworld:33333"));
    assertThat(configuration.getRestAddress()).isEqualTo(URI.create("http://helloworld:44444"));
  }
}