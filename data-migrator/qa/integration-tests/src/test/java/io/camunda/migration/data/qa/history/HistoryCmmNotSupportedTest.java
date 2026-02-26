/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIPPING;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_UNSUPPORTED_CMMN_TASKS;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_UNSUPPORTED_CMMN_VARIABLES;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.impl.history.migrator.UserTaskMigrator;
import io.camunda.migration.data.impl.history.migrator.VariableMigrator;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.qa.util.WithMultiDb;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.CaseService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.runtime.CaseInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({ OutputCaptureExtension.class })
@WithMultiDb
public class HistoryCmmNotSupportedTest extends HistoryMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create()
      .captureForType(HistoryMigrator.class, Level.DEBUG)
      .captureForType(UserTaskMigrator.class, Level.DEBUG)
      .captureForType(VariableMigrator.class, Level.DEBUG);

  @Autowired
  protected CaseService caseService;

  @Autowired
  protected HistoryService historyService;

  @Test
  public void shouldSkipCmmUserTasksDuringMigration() {
    // given - deploy CMMN case definition in C7
    deployer.createDeployment("io/camunda/migration/data/other/simpleCaseDefinition.cmmn");

    // and start a case instance
    CaseInstance caseInstance = caseService.createCaseInstanceByKey("simpleCmmn");
    assertThat(caseInstance).isNotNull();
    String caseInstanceId = caseInstance.getId();

    // verify case instance exists
    assertThat(caseService.createCaseInstanceQuery().caseInstanceId(caseInstanceId).singleResult())
        .isNotNull();

    // verify a user task exists in the case instance
    List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
        .caseInstanceId(caseInstanceId)
        .list();
    assertThat(historicTasks).isNotEmpty();

    // when
    historyMigrator.migrate();

    // then - verify no user tasks were migrated to C8
    // User tasks from CMMN should be skipped, so C8 should have 0 user tasks
    assertThat(searchHistoricUserTasks()).isEmpty();

    // and verify the skip reason is logged
    historicTasks.forEach(t -> {
      String id = t.getId();
      logs.assertContains(formatMessage(SKIPPING, IdKeyMapper.TYPE.HISTORY_USER_TASK.getDisplayName(), id,
          SKIP_REASON_UNSUPPORTED_CMMN_TASKS));
    });
  }

  @Test
  public void shouldSkipMultipleCmmUserTasksDuringMigration() {
    // given - deploy CMMN case definition in C7
    deployer.createDeployment("io/camunda/migration/data/other/simpleCaseDefinition.cmmn");

    // and start multiple case instances
    int caseInstanceCount = 3;
    for (int i = 0; i < caseInstanceCount; i++) {
      CaseInstance caseInstance = caseService.createCaseInstanceByKey("simpleCmmn");
      assertThat(caseInstance).isNotNull();
    }

    // verify all case instances exist
    long c7CaseInstanceCount = caseService.createCaseInstanceQuery().count();
    assertThat(c7CaseInstanceCount).isEqualTo(caseInstanceCount);

    // verify user tasks exist in the case instances
    List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
        .list()
        .stream()
        .filter(t -> t.getCaseInstanceId() != null)
        .toList();

    assertThat(historicTasks.size()).isEqualTo(3);

    // when
    historyMigrator.migrate();

    // then - verify no CMMN user tasks were migrated to C8
    assertThat(searchHistoricUserTasks()).isEmpty();

    // and verify the skip reason is logged for each skipped user task
    historicTasks.forEach(t -> {
      String id = t.getId();
      logs.assertContains(formatMessage(SKIPPING, IdKeyMapper.TYPE.HISTORY_USER_TASK.getDisplayName(), id,
          SKIP_REASON_UNSUPPORTED_CMMN_TASKS));
    });
  }

  @Test
  public void shouldSkipCmmVariablesDuringMigration() {
    // given - deploy CMMN case definition in C7
    deployer.createDeployment("io/camunda/migration/data/other/simpleCaseDefinition.cmmn");

    // and start a case instance with variables
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "test value");
    variables.put("numberVar", 42);
    variables.put("boolVar", true);

    CaseInstance caseInstance = caseService.createCaseInstanceByKey("simpleCmmn", variables);
    assertThat(caseInstance).isNotNull();
    String caseInstanceId = caseInstance.getId();

    // verify case instance exists
    assertThat(caseService.createCaseInstanceQuery().caseInstanceId(caseInstanceId).singleResult())
        .isNotNull();

    // verify variables exist in the case instance
    Map<String, Object> caseVariables = caseService.getVariables(caseInstanceId);
    assertThat(caseVariables).isNotEmpty().hasSize(3);
    assertThat(caseVariables.get("stringVar")).isEqualTo("test value");
    assertThat(caseVariables.get("numberVar")).isEqualTo(42);
    assertThat(caseVariables.get("boolVar")).isEqualTo(true);

    // when
    historyMigrator.migrate();

    // then - verify no user tasks were migrated to C8
    assertThat(searchHistoricVariables()).isEmpty();

    // and verify the CMMN variable skip reason is logged
    historyService.createHistoricVariableInstanceQuery().list().forEach(t -> {
      String id = t.getId();
      logs.assertContains(formatMessage(SKIPPING, IdKeyMapper.TYPE.HISTORY_VARIABLE.getDisplayName(), id,
          SKIP_REASON_UNSUPPORTED_CMMN_VARIABLES));
    });
  }
}

