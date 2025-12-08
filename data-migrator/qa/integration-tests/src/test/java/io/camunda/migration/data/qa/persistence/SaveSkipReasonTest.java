/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.persistence;

import static io.camunda.migration.data.impl.logging.RuntimeValidatorLogs.MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.persistence.IdKeyDbModel;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.RuntimeMigrationExtension;
import io.camunda.migration.data.qa.util.WhiteBox;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

@CamundaSpringProcessTest
public class SaveSkipReasonTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final RuntimeMigrationExtension runtimeMigration = new RuntimeMigrationExtension();

  @Autowired
  protected IdKeyMapper idKeyMapper;

  @Autowired
  protected MigratorProperties migratorProperties;

  @AfterEach
  void cleanUp() {
    migratorProperties.setSaveSkipReason(false);
  }

  @Test
  @WhiteBox
  public void shouldSaveSkipReasonWhenSaveSkipReasonIsEnabled() {
    // given
    migratorProperties.setSaveSkipReason(true);
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    assertThat(taskCount).isEqualTo(4);

    // when
    runtimeMigration.getMigrator().start();

    // then
    List<IdKeyDbModel> skippedInstances = idKeyMapper.findSkippedByType(TYPE.RUNTIME_PROCESS_INSTANCE, 0, 100);
    assertThat(skippedInstances).hasSize(1);

    IdKeyDbModel savedInstance = skippedInstances.getFirst();
    assertThat(savedInstance.getC8Key()).isNull();
    assertThat(savedInstance.getSkipReason()).isEqualTo(String.format(MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR, "multiUserTask"));
  }

  @Test
  @WhiteBox
  public void shouldSaveNullSkipReasonIfSaveSkipReasonIsFalse() {
    // given
    migratorProperties.setSaveSkipReason(false);
    deployer.deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    assertThat(taskCount).isEqualTo(4);

    // when
    runtimeMigration.getMigrator().start();

    // then
    List<IdKeyDbModel> skippedInstances = idKeyMapper.findSkippedByType(TYPE.RUNTIME_PROCESS_INSTANCE, 0, 100);
    assertThat(skippedInstances).hasSize(1);

    IdKeyDbModel savedInstance = skippedInstances.getFirst();
    assertThat(savedInstance.getSkipReason()).isNull();
    assertThat(savedInstance.getC8Key()).isNull();
  }
}
