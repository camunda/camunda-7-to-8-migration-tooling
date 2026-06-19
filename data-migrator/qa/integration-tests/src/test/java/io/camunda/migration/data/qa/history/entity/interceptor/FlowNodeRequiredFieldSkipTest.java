/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity.interceptor;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_REQUIRED_FIELD_NULL;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.interceptor.EntityInterceptor;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.migration.data.qa.history.entity.interceptor.pojo.RequiredFieldNullingInterceptor;
import io.camunda.migration.data.qa.util.WithSpringProfile;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;

@WithSpringProfile("required-field-null")
public class FlowNodeRequiredFieldSkipTest extends HistoryMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

  @Autowired
  protected List<EntityInterceptor> configuredEntityInterceptors;

  @AfterEach
  void resetNullingInterceptor() {
    nullingInterceptor().setFieldToNull(null);
  }

  @ParameterizedTest
  @ValueSource(strings = {"flowNodeInstanceKey", "flowNodeId", "type", "state", "processDefinitionId", "tenantId"})
  public void shouldSkipFlowNodeWhenRequiredFieldIsNull(String field) {
    // given: an interceptor that nulls the required field after the built-in transformer set it
    nullingInterceptor().setFieldToNull(field);
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    String processInstanceId = runtimeService.startProcessInstanceByKey("simpleProcess").getId();
    String startEventC7Id = historyService.createHistoricActivityInstanceQuery()
        .processInstanceId(processInstanceId).activityId("startEvent").singleResult().getId();

    // when
    historyMigrator.migrate();

    // then: the process instance migrates but its flow nodes are skipped
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("simpleProcess");
    assertThat(processInstances).hasSize(1);
    assertThat(searchHistoricFlowNodes(processInstances.getFirst().processInstanceKey())).isEmpty();
    logs.assertContains(formatMessage(SKIPPING, HISTORY_FLOW_NODE.getDisplayName(), startEventC7Id,
        String.format(SKIP_REASON_REQUIRED_FIELD_NULL, field)));
  }

  private RequiredFieldNullingInterceptor nullingInterceptor() {
    return configuredEntityInterceptors.stream()
        .filter(RequiredFieldNullingInterceptor.class::isInstance)
        .map(RequiredFieldNullingInterceptor.class::cast)
        .findFirst()
        .orElseThrow();
  }
}
