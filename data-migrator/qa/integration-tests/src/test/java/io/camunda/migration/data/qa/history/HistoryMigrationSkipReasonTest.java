/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.MIGRATING_USER_TASK;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FORM;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FORM_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_USER_TASK;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.persistence.IdKeyDbModel;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.qa.util.WhiteBox;
import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryMigrationSkipReasonTest extends HistoryMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

  @Autowired
  protected HistoryService historyService;

  @Autowired
  protected MigratorProperties migratorProperties;

  @Autowired
  protected IdKeyMapper idKeyMapper;

  @AfterEach
  void cleanUp() {
    migratorProperties.setSaveSkipReason(false);
  }

  @Test
  public void shouldUpdateSkipReasonWhenEntityIsStillSkippedOnRetry() {
    // given
    migratorProperties.setSaveSkipReason(true);
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId",
        Variables.createVariables().putValue("inputA", stringValue("A")));
    String decisionInstanceId = historyService.createHistoricDecisionInstanceQuery().singleResult().getId();

    // when - first migration: decision instance skips with "missing process definition"
    historyMigrator.migrateByType(HISTORY_DECISION_DEFINITION);
    historyMigrator.migrateByType(HISTORY_DECISION_INSTANCE);

    // then
    verifySkippedViaLogs(HISTORY_DECISION_INSTANCE.getDisplayName(), decisionInstanceId, SKIP_REASON_MISSING_PROCESS_DEFINITION);

    // given - process definition migrated, but NOT process instance
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);

    // when - retry
    historyMigrator.retry();

    // then - skip reason updated to "missing process instance"
    verifySkippedViaLogs(HISTORY_DECISION_INSTANCE.getDisplayName(), decisionInstanceId, SKIP_REASON_MISSING_PROCESS_INSTANCE);
  }

  @Test
  @WhiteBox
  public void shouldClearSkipReasonWhenEntityIsMigratedOnRetry() {
    // given
    migratorProperties.setSaveSkipReason(true);
    deployer.deployCamunda7Process("processWithForm.bpmn");
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();
    runtimeService.startProcessInstanceByKey("processWithFormId");
    var task = taskService.createTaskQuery().singleResult();

    // when - first migration: user task skips with "missing form"
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);
    historyMigrator.migrateByType(HISTORY_USER_TASK);

    // then
    verifySkippedViaLogs(HISTORY_USER_TASK.getDisplayName(), task.getId(), SKIP_REASON_MISSING_FORM);

    // given - form migrated
    historyMigrator.migrateByType(HISTORY_FORM_DEFINITION);

    // when - retry
    historyMigrator.retry();

    // then - user task is now migrated (skip reason cleared)
    var processInstances = searchHistoricProcessInstances("processWithFormId");
    assertThat(processInstances).hasSize(1);
    assertThat(searchHistoricUserTasks(processInstances.getFirst().processInstanceKey())).hasSize(1);
    logs.assertContains(formatMessage(MIGRATING_USER_TASK, task.getId()));
    assertPersistedSkipReasonClearedForMigrated(HISTORY_USER_TASK);
  }

  protected void verifySkippedViaLogs(String entityType, String c7Id, String expectedSkipReason) {
    logs.assertContains(formatMessage(SKIPPING, entityType, c7Id, expectedSkipReason));
  }

  protected void assertPersistedSkipReasonClearedForMigrated(IdKeyMapper.TYPE type) {
    var migrated = idKeyMapper.findMigratedByType(type, 0, 10);
    assertThat(migrated).hasSize(1);
    IdKeyDbModel mapping = migrated.getFirst();
    assertThat(mapping.getSkipReason()).isNull();
  }
}
