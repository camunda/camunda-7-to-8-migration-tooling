/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.runtime;

import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.migrator.qa.util.WithSpringProfile;
import io.camunda.process.test.api.CamundaAssert;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@WithSpringProfile("logging-test")
@TestPropertySource(properties = {
    "camunda.migrator.page-size=2"
})
class PageSizeConfigurationTest extends RuntimeMigrationAbstractTest {

  public static final String MIGRATOR_JOBS_FOUND = "Migrator jobs found: ";

  @Test
  public void shouldPerformPaginationForProcessInstances(CapturedOutput output) {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("simpleProcess");
    }
    assertThat(runtimeService.createProcessInstanceQuery().list().size()).isEqualTo(5);

    // when running runtime migration
    runtimeMigrator.start();

    // then
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().execute().items();
    assertThat(processInstances.size()).isEqualTo(5);
    assertThat(output.getOut()).contains("Method: #fetchAndHandleHistoricRootProcessInstances, max count: 5, offset: 0, page size: 2");
    assertThat(output.getOut()).contains("Method: #fetchAndHandleHistoricRootProcessInstances, max count: 5, offset: 2, page size: 2");
    assertThat(output.getOut()).contains("Method: #fetchAndHandleHistoricRootProcessInstances, max count: 5, offset: 4, page size: 2");
    Matcher matcher = Pattern.compile("Method: #fetchAndHandleProcessInstances, max count: 1, offset: 0, page size: 2").matcher(output.getOut());
    assertThat(matcher.results().count()).isEqualTo(5);
  }

  @Test
  public void shouldPerformPaginationForMigrationJobs(CapturedOutput output) {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // given
    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("simpleProcess");
    }

    // when running runtime migration
    runtimeMigrator.start();

    // then
    assertThat(camundaClient.newProcessInstanceSearchRequest().execute().items()).hasSize(5);

    Matcher matcher = Pattern.compile(MIGRATOR_JOBS_FOUND + "2").matcher(output.getOut());
    assertThat(matcher.results().count()).isEqualTo(2);
    assertThat(output.getOut()).contains(MIGRATOR_JOBS_FOUND + "1");
    assertThat(output.getOut()).contains(MIGRATOR_JOBS_FOUND + "0");
  }

  @Test
  public void shouldPaginateMultiLevelProcessModel(CapturedOutput output) {
    // deploy processes
    String rootId = "root";
    String level1Id = "level1";
    String level2Id = "level2";
    deployModels(rootId, level1Id, level2Id);

    // given
    runtimeService.startProcessInstanceByKey("root");

    // when running runtime migration
    runtimeMigrator.start();

    // then
    List<String> processIds = List.of(rootId, level1Id, level2Id);
    processIds.forEach(processId ->
        CamundaAssert.assertThat(byProcessId(processId))
            .isActive());

    Matcher matcher = Pattern.compile(MIGRATOR_JOBS_FOUND + "1").matcher(output.getOut());
    assertThat(matcher.results().count()).isEqualTo(3);
    assertThat(output.getOut()).contains("Method: #fetchAndHandleHistoricRootProcessInstances, max count: 1, offset: 0, page size: 2");
    assertThat(output.getOut()).contains("Method: #fetchAndHandleProcessInstances, max count: 3, offset: 0, page size: 2");
  }

  private void deployModels(String rootId, String level1Id, String level2Id) {
    // C7
    var c7rootModel = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(rootId)
        .startEvent("start_1")
        .callActivity("ca_level_1")
        .camundaIn(level1Id, level2Id)
          .calledElement(level1Id)
        .endEvent("end_1").done();
    var c7level1Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(level1Id)
        .startEvent("start_2")
        .callActivity("ca_level_2")
          .calledElement(level2Id)
        .endEvent("end_2").done();
    var c7level2Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(level2Id)
        .startEvent("start_3")
        .userTask("userTask_1")
        .endEvent("end_3").done();

    // C8
    var c8rootModel = io.camunda.zeebe.model.bpmn.Bpmn.createExecutableProcess(rootId)
        .startEvent("start_1")
        .zeebeEndExecutionListener("migrator")
        .callActivity("ca_level_1", c -> c.zeebeProcessId(level1Id))
        .endEvent("end_1").done();
    var c8level1Model = io.camunda.zeebe.model.bpmn.Bpmn.createExecutableProcess(level1Id)
        .startEvent("start_2")
        .zeebeEndExecutionListener("migrator")
        .callActivity("ca_level_2", c -> c.zeebeProcessId(level2Id))
        .endEvent("end_2").done();
    var c8level2Model = io.camunda.zeebe.model.bpmn.Bpmn.createExecutableProcess(level2Id)
        .startEvent("start_3")
        .zeebeEndExecutionListener("migrator")
        .userTask("userTask_1")
        .endEvent("end_3").done();

    deployer.deployModelInstance(rootId, c7rootModel, c8rootModel);
    deployer.deployModelInstance(level1Id, c7level1Model, c8level1Model);
    deployer.deployModelInstance(level2Id, c7level2Model, c8level2Model);
  }

}
