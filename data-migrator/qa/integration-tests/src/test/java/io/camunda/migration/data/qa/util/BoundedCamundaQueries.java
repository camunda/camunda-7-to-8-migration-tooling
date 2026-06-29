/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.util;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bounded Camunda 8 client search queries shared by the runtime migration test infrastructure
 * ({@code RuntimeMigrationAbstractTest} and {@code RuntimeMigrationExtension}).
 *
 * <p>Each search is capped with {@code join(timeout)} so a Camunda client future cannot park
 * indefinitely while the Camunda Process Test cluster is briefly unavailable (for example during a
 * purge / re-bootstrap window). Cleanup-oriented callers additionally tolerate transient timeouts
 * by treating them as "nothing to clean up", so a flaky cluster does not fail an otherwise green
 * test in its {@code @AfterEach}.
 */
public final class BoundedCamundaQueries {

  private static final long REQUEST_TIMEOUT_SECONDS = 10;

  private BoundedCamundaQueries() {
  }

  public static List<ProcessInstance> searchProcessInstances(CamundaClient camundaClient) {
    return camundaClient.newProcessInstanceSearchRequest()
        .send()
        .join(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .items();
  }

  public static List<ProcessInstance> searchProcessInstancesForCleanup(CamundaClient camundaClient) {
    try {
      return searchProcessInstances(camundaClient);
    } catch (ClientException e) {
      if (isCausedByTimeout(e)) {
        return List.of();
      }
      throw e;
    }
  }

  public static List<Variable> searchVariables(CamundaClient camundaClient) {
    return camundaClient.newVariableSearchRequest()
        .send()
        .join(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .items();
  }

  public static boolean isCausedByTimeout(Throwable exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
