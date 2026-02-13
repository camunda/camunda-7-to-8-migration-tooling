/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.qa;

import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.qa.util.ProcessDefinitionDeployer;
import io.camunda.migration.data.qa.util.SpringProfileResolver;
import io.camunda.migration.data.qa.util.WithMultiDb;
import io.camunda.migration.data.qa.util.WithSpringProfile;
import java.util.Calendar;
import java.util.Date;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.DecisionService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.runtime.Job;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
  protected C7Client c7Client;

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

  /**
   * MySQL doesn't support millisecond precision by default, so we need to truncate
   * milliseconds to 0 when running tests against MySQL to avoid timing comparison issues.
   */
  @BeforeEach
  public void setupClockForMySql() {
    if (isMySqlActive()) {
      ClockUtil.setCurrentTime(truncateMilliseconds(new Date()));
    }
  }

  @AfterEach
  public void resetClockUtil() {
    ClockUtil.reset();
  }

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

  /**
   * Checks if MySQL is the active database profile.
   */
  protected static boolean isMySqlActive() {
    return SpringProfileResolver.getActiveProfiles().contains("mysql");
  }

  /**
   * Truncates milliseconds from a Date, setting them to 0.
   * This is needed for MySQL which doesn't support millisecond precision by default.
   */
  protected static Date truncateMilliseconds(Date date) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTime();
  }
}
