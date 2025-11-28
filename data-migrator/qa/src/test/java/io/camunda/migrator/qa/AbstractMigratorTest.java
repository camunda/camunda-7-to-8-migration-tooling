/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.qa;

import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.qa.util.ProcessDefinitionDeployer;
import io.camunda.migrator.qa.util.WithMultiDb;
import io.camunda.migrator.qa.util.WithSpringProfile;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.DecisionService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@WithMultiDb
@WithSpringProfile("history-level-full")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AbstractMigratorTest {

  @Autowired
  protected ProcessDefinitionDeployer deployer;

  @Autowired
  protected DbClient dbClient;

  // C7 ---------------------------------------

  @Autowired
  protected RepositoryService repositoryService;

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected DecisionService decisionService;

  @Autowired
  protected TaskService taskService;

  @Autowired
  protected ManagementService managementService;

  protected void triggerIncident(final String processInstanceId) {
    Job job = managementService.createJobQuery().processInstanceId(processInstanceId).singleResult();
    for (int i = 0; i < 3; i++) {
      try {
        managementService.executeJob(job.getId());
      } catch (Exception e) {
        // ignore
      }
    }
  }

  protected void executeAllJobsWithRetry() {
    var jobs = managementService.createJobQuery().list();

    // Try executing the job multiple times to ensure incident is created
    for (var job : jobs) {
      for (int i = 0; i < 3; i++) {
        try {
          managementService.executeJob(job.getId());
        } catch (Exception e) {
          // expected - job will fail due to empty delegate expression
        }
      }
    }
  }
}
