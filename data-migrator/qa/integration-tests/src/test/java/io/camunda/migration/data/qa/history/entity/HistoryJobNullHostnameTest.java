/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C7_NULL_PLACEHOLDER;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_JOB;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Verifies that {@code JobTransformer} substitutes the {@link io.camunda.migration.data.constants.MigratorConstants#C7_NULL_PLACEHOLDER}
 * placeholder when the C7 {@code HistoricJobLog} carries a {@code null} hostname.
 * <p>
 * A null hostname is not reproducible with the default engine setup
 * ({@code SimpleIpBasedProvider} always returns {@code ip + "$" + engineName}). This test installs a
 * {@link org.camunda.bpm.engine.impl.history.event.HostnameProvider} that returns {@code null} via a
 * nested {@link TestConfiguration}, which gives this class its own Spring context (torn down by the
 * {@code @DirtiesContext(AFTER_CLASS)} inherited from {@code AbstractMigratorTest}), so no other
 * history test observes a null hostname.
 */
public class HistoryJobNullHostnameTest extends HistoryMigrationAbstractTest {

  /**
   * Detected automatically by {@code @SpringBootTest} as a nested test configuration. The
   * {@link BeanPostProcessor} sets a null-returning {@code HostnameProvider} on the engine
   * configuration before {@code ProcessEngineFactoryBean} builds the engine and runs
   * {@code initHostName()}, so every {@code HistoricJobLog} produced in this context stores a
   * null hostname.
   */
  @TestConfiguration
  static class NullHostnameConfiguration {

    @Bean
    static BeanPostProcessor nullHostnameProcessor() {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
          if (bean instanceof ProcessEngineConfigurationImpl engineConfiguration) {
            engineConfiguration.setHostnameProvider(configuration -> null);
          }
          return bean;
        }
      };
    }
  }

  @Test
  public void shouldUsePlaceholderWorkerWhenC7HostnameIsNull() {
    // given: the engine is configured with a HostnameProvider that returns null, so the job logs
    // recorded below carry a null hostname
    deployer.deployCamunda7Process("asyncBeforeUserTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("asyncBeforeUserTaskProcessId");

    // execute the async-before job to enter the user task (produces creation + success job logs)
    var jobs = managementService.createJobQuery().list();
    assertThat(jobs).hasSize(1);
    managementService.executeJob(jobs.getFirst().getId());

    // when: jobs, flow nodes and process instances are migrated
    // (flow nodes must precede jobs: an async-before job is skipped when C8 has no
    // flow-node instance for its activity — see JobMigrator's SKIP_REASON_MISSING_FLOW_NODE)
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);
    historyMigrator.migrateByType(HISTORY_JOB);

    // then: the process instance was migrated
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("asyncBeforeUserTaskProcessId");
    assertThat(processInstances).hasSize(1);
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    // and: the migrated C8 job falls back to the placeholder worker
    List<JobEntity> c8Jobs = searchJobs(processInstanceKey);
    assertThat(c8Jobs).as("One C8 job entry per C7 job (deduplication by job ID)").hasSize(1);
    assertThat(c8Jobs.getFirst().worker())
        .as("worker should fall back to the C7_NULL_PLACEHOLDER placeholder when the C7 hostname is null")
        .isEqualTo(C7_NULL_PLACEHOLDER);
  }
}
