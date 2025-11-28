/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.persistence;

import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureTrue;

import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE;
import io.camunda.migrator.qa.runtime.RuntimeMigrationAbstractTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SaveSkipReasonTest extends RuntimeMigrationAbstractTest {

  @Autowired
  private IdKeyMapper idKeyMapper;

  @Autowired
  private MigratorProperties migratorProperties;

  @AfterEach
  void cleanUp() {
    migratorProperties.setSaveSkipReason(false);
  }

  @Test
  public void shouldSaveSkipReasonWhenSaveSkipReasonIsEnabled() {
    // given
    migratorProperties.setSaveSkipReason(true);

    // given process state in c7
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    ensureTrue("Unexpected process state: one task and three parallel tasks should be created", taskCount == 4);

    // when
    runtimeMigrator.start();

    // then
    List<IdKeyDbModel> skippedInstances = idKeyMapper.findSkippedByType(TYPE.RUNTIME_PROCESS_INSTANCE, 0, 100);
    assertThat(skippedInstances).hasSize(1);

    IdKeyDbModel savedInstance = skippedInstances.getFirst();
    assertThat(savedInstance.getC8Key()).isNull();
    assertThat(savedInstance.getSkipReason()).isEqualTo(String.format(MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR, "multiUserTask"));
  }

  @Test
  public void shouldSaveNullSkipReasonIfSaveSkipReasonIsFalse() {
    // given
    migratorProperties.setSaveSkipReason(false);

    // given process state in c7
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    ensureTrue("Unexpected process state: one task and three parallel tasks should be created", taskCount == 4);

    // when
    runtimeMigrator.start();

    // then
    List<IdKeyDbModel> skippedInstances = idKeyMapper.findSkippedByType(TYPE.RUNTIME_PROCESS_INSTANCE, 0, 100);
    assertThat(skippedInstances).hasSize(1);

    IdKeyDbModel savedInstance = skippedInstances.getFirst();
    assertThat(savedInstance.getSkipReason()).isNull();
    assertThat(savedInstance.getC8Key()).isNull();
  }
}
