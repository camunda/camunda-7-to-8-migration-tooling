/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

import io.camunda.client.spring.event.CamundaClientCreatedSpringEvent;
import org.springframework.context.ApplicationListener;

public class CamundaClusterHealthCheckListener
    implements ApplicationListener<CamundaClientCreatedSpringEvent> {

  @Override
  public void onApplicationEvent(CamundaClientCreatedSpringEvent event) {
    CamundaClusterHealthCheck.awaitHealthy(
        () ->
            CamundaClusterHealthCheck.isHealthy(
                event.getClient().newTopologyRequest().execute()));
  }
}
