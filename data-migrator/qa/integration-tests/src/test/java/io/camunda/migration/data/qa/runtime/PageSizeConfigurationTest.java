/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.util.WithSpringProfile;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@WithSpringProfile("logging-test")
@TestPropertySource(properties = {
    "camunda.migrator.page-size=2"
})
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PageSizeConfigurationTest extends RuntimeMigrationAbstractTest {

  public static final String MIGRATOR_JOBS_FOUND = "Migrator jobs found: ";

  @Test
  public void shouldPerformPaginationForProcessInstancesAndMigrationJobs(CapturedOutput output) {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("simpleProcess");
    }
    assertThat(runtimeService.createProcessInstanceQuery().list()).hasSize(5);

    // when running runtime migration
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(5);
    assertThat(output.getOut()).contains("Method: #fetchAndHandleHistoricRootProcessInstances, max count: 5, offset: 0, page size: 2");
    assertThat(output.getOut()).contains("Method: #fetchAndHandleHistoricRootProcessInstances, max count: 5, offset: 2, page size: 2");
    assertThat(output.getOut()).contains("Method: #fetchAndHandleHistoricRootProcessInstances, max count: 5, offset: 4, page size: 2");
    Matcher matcher = Pattern.compile("Method: #fetchAndHandleProcessInstances, max count: 1, offset: 0, page size: 2").matcher(output.getOut());
    assertThat(matcher.results().count()).isEqualTo(5);

    Matcher migratorJobsFound = Pattern.compile(MIGRATOR_JOBS_FOUND + "2").matcher(output.getOut());
    assertThat(migratorJobsFound.results().count()).isEqualTo(2);
    assertThat(output.getOut()).contains(MIGRATOR_JOBS_FOUND + "1");
    assertThat(output.getOut()).contains(MIGRATOR_JOBS_FOUND + "0");
  }

}
