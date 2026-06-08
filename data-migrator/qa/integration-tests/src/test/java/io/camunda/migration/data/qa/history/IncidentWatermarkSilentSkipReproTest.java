/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_INCIDENT;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.qa.util.WhiteBox;
import io.camunda.search.entities.IncidentEntity;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Reproduces the silent-skip bug described in
 * {@code context/bug-watermark-silent-skip-on-rerun.md}.
 *
 * <p>The History Data Migrator fetches entities incrementally with an <em>exclusive</em>
 * create-time watermark ({@code createTimeAfter(max committed create-time)}). When two entities
 * share a create-time and one commits while the other fails and rolls back, a re-run fetches
 * {@code > watermark} and silently omits the rolled-back entity (it also has no skip record, so
 * {@code --retry-skipped} won't recover it).
 *
 * <p>Scenario: a call activity whose child service task fails. The child incident (no
 * HistoricActivityInstance) is skipped; the propagated parent incident migrates. The clock is
 * frozen so both incidents share a create-time. A spy fails the parent's insert on the first run,
 * so the child's skip record commits (advancing the watermark to the shared create-time) while the
 * parent rolls back. On re-run the parent is excluded by the exclusive watermark.
 *
 * <p>Enabled, this test FAILS at the final assertion, demonstrating the bug. It is
 * {@link Disabled} so it does not break CI; remove the annotation to reproduce.
 */
@WhiteBox
//@Disabled("Reproduces silent-skip bug; see context/bug-watermark-silent-skip-on-rerun.md")
public class IncidentWatermarkSilentSkipReproTest extends HistoryMigrationAbstractTest {

  protected static final DataAccessException SIMULATED_MAPPING_FAILURE =
      new DataAccessException("Simulated mapping insert failure") {};

  @MockitoSpyBean
  protected DbClient dbClient;

  @Test
  public void rerunAfterPartialFailureShouldNotSilentlySkipSameCreateTimeIncident() {
    // given - freeze the clock so the child and propagated parent incidents share a create-time
    ClockUtil.setCurrentTime(new Date());
    deployer.deployCamunda7Process("callActivityWithIncidentSubprocess.bpmn");
    deployer.deployCamunda7Process("incidentProcess.bpmn");
    ProcessInstance parentInstance = runtimeService.startProcessInstanceByKey("callActivityWithIncidentSubprocessId");
    ProcessInstance childInstance = runtimeService.createProcessInstanceQuery()
        .superProcessInstanceId(parentInstance.getId())
        .singleResult();
    triggerIncident(childInstance.getId());

    String parentIncidentC7Id = historyService.createHistoricIncidentQuery()
        .processInstanceId(parentInstance.getId())
        .singleResult()
        .getId();

    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);

    // when - first run: the child is skipped and commits its record (advancing the watermark to the
    // shared create-time); the parent's migrate insert fails and rolls back, aborting the run
    failParentMigrateInsertOnce(parentIncidentC7Id);
    assertThatThrownBy(() -> historyMigrator.migrateByType(HISTORY_INCIDENT))
        .isSameAs(SIMULATED_MAPPING_FAILURE);

    // re-run: no simulated failure now
    historyMigrator.migrateByType(HISTORY_INCIDENT);

    // then - the parent incident is silently lost: not migrated AND not even recorded as skipped
    List<IncidentEntity> incidents = searchHistoricIncidents("callActivityWithIncidentSubprocessId");
    assertThat(dbClient.checkExistsByC7IdAndType(parentIncidentC7Id, HISTORY_INCIDENT))
        .as("parent incident silently has no record after re-run (no migrate, no skip)")
        .isFalse();
    assertThat(incidents)
        .as("re-run should migrate the parent incident, not silently skip it")
        .isNotEmpty();
  }

  /** Fails the parent incident's real migrate insert (non-null c8Key) exactly once. */
  protected void failParentMigrateInsertOnce(String parentIncidentC7Id) {
    AtomicInteger callCount = new AtomicInteger(0);
    doAnswer(invocation -> {
      String c7Id = invocation.getArgument(0);
      Object c8Key = invocation.getArgument(1);
      IdKeyMapper.TYPE type = invocation.getArgument(3);
      if (type == HISTORY_INCIDENT && c8Key != null && parentIncidentC7Id.equals(c7Id)
          && callCount.incrementAndGet() == 1) {
        throw SIMULATED_MAPPING_FAILURE;
      }
      return invocation.callRealMethod();
    }).when(dbClient).insert(anyString(), anyString(), any(Date.class), any(IdKeyMapper.TYPE.class), any(), any());
  }
}
