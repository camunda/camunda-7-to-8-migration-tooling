/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.springprocess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.example.springprocess.startup.PostDeploymentOrderInitializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SpringProcessApplication.class)
public class PostDeploymentOrderTest {

  @Autowired private PostDeploymentOrderInitializer postDeploymentOrderInitializer;
  @Autowired private RuntimeService runtimeService;
  @Autowired private ManagementService managementService;
  @Autowired private HistoryService historyService;

  @Test
  public void completesPostDeploymentOrderFromItsListener() {
    postDeploymentOrderInitializer.startProcessAfterDeployment();
    String processInstanceId = postDeploymentOrderInitializer.lastStartedProcessInstanceId();
    assertNotNull(
        runtimeService
            .createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult());

    Job job =
        managementService.createJobQuery().processInstanceId(processInstanceId).singleResult();
    assertNotNull(job);
    managementService.executeJob(job.getId());

    assertNotNull(
        historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .finished()
            .singleResult());
    assertEquals(
        Boolean.TRUE,
        historyService
            .createHistoricVariableInstanceQuery()
            .processInstanceId(processInstanceId)
            .variableName("postDeploymentExecuted")
            .singleResult()
            .getValue());
  }
}
