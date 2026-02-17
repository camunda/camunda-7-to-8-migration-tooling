/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.IncidentEntity;
import java.util.List;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import io.camunda.migration.data.qa.util.WithSpringProfile;
import io.camunda.migration.data.config.MigratorAutoConfiguration;
import io.camunda.migration.data.qa.config.TestProcessEngineConfiguration;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

@Import({
  io.camunda.migration.data.qa.history.HistoryCustomConfiguration.class,
  io.camunda.migration.data.qa.config.TestProcessEngineConfiguration.class,
  io.camunda.migration.data.config.MigratorAutoConfiguration.class
})
@WithSpringProfile("history-level-full")
public class HistoryIncidentTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  // TODO add complete test coverage with https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/364

  @Test
  public void shouldMigrateIncidentTenant() {
    // given
    deployer.deployCamunda7Process("incidentProcess.bpmn");
    deployer.deployCamunda7Process("incidentProcess2.bpmn", "tenant1");
    ProcessInstance c7ProcessDefaultTenant = runtimeService.startProcessInstanceByKey("incidentProcessId");
    ProcessInstance c7ProcessTenant1 = runtimeService.startProcessInstanceByKey("incidentProcessId2");
    triggerIncident(c7ProcessDefaultTenant.getId());
    triggerIncident(c7ProcessTenant1.getId());

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidentsDefaultTenant = historyMigration.searchHistoricIncidents("incidentProcessId");
    List<IncidentEntity> incidentsTenant1 = historyMigration.searchHistoricIncidents("incidentProcessId2");
    assertThat(incidentsDefaultTenant).singleElement().extracting(IncidentEntity::tenantId).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(incidentsTenant1).singleElement().extracting(IncidentEntity::tenantId).isEqualTo("tenant1");
  }
}
