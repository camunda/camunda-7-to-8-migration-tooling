/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.IncidentEntity;
import java.util.List;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class HistoryIncidentTest extends HistoryMigrationAbstractTest {


  // TODO test with completed and non completed user task (and resolved and unresolved inc)

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
    List<IncidentEntity> incidentsDefaultTenant = searchHistoricIncidents("incidentProcessId");
    List<IncidentEntity> incidentsTenant1 = searchHistoricIncidents("incidentProcessId2");
    assertThat(incidentsDefaultTenant).singleElement().extracting(IncidentEntity::tenantId).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(incidentsTenant1).singleElement().extracting(IncidentEntity::tenantId).isEqualTo("tenant1");
  }

  @Test
  public void shouldMigrateIncidentBasicFields() {
    // given
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    ProcessInstance c7ProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

    Task task = taskService.createTaskQuery().taskDefinitionKey("userTaskId").singleResult();
    String executionId = task.getExecutionId();
    runtimeService.createIncident("foo", executionId, "bar");

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery()
        .processInstanceId(c7ProcessInstance.getId())
        .singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("userTaskProcessId");
    assertThat(incidents).hasSize(1);

    IncidentEntity incident = incidents.getFirst();

    // specific values
    assertThat(incident.rootProcessInstanceKey()).isEqualTo(incident.processInstanceKey());
    assertThat(incident.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(incident.processDefinitionId()).isEqualTo(prefixDefinitionId(c7Incident.getProcessDefinitionKey()));
    assertThat(incident.state()).isEqualTo(IncidentEntity.IncidentState.RESOLVED);
    assertThat(incident.errorMessage()).isEqualTo(c7Incident.getIncidentMessage());
    assertThat(incident.flowNodeId()).isEqualTo(c7Incident.getActivityId());

    // non-null values
    assertThat(incident.incidentKey()).isNotNull();
    assertThat(incident.processInstanceKey()).isNotNull();
    assertThat(incident.rootProcessInstanceKey()).isNotNull();
    assertThat(incident.creationTime()).isNotNull();
    assertThat(incident.flowNodeInstanceKey()).isNotNull();

    // null values
    assertThat(incident.jobKey()).isNull();
  }

  @Test
  @Disabled // TODO
  public void shouldMigrateIncidentForNestedProcessInstance() {
    // given nested processes with incident in child instance
    deployer.deployCamunda7Process("incidentCallerProcess.bpmn");
    deployer.deployCamunda7Process("incidentProcess.bpmn");

    ProcessInstance instance = runtimeService.startProcessInstanceByKey("incidentProcessId");
    triggerIncident(instance.getId());

    HistoricIncident c7Incident = historyService.createHistoricIncidentQuery().singleResult();
    assertThat(c7Incident).isNotNull();

    // when
    historyMigrator.migrate();

    // then
    List<IncidentEntity> incidents = searchHistoricIncidents("incidentProcessId");
    assertThat(incidents).isNotEmpty();

    IncidentEntity incident = incidents.getFirst();

    // specific values
    assertThat(incident.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(incident.processDefinitionId()).isEqualTo(prefixDefinitionId(c7Incident.getProcessDefinitionKey()));
    assertThat(incident.flowNodeId()).isEqualTo(c7Incident.getActivityId());
    assertThat(incident.state()).isEqualTo(IncidentEntity.IncidentState.ACTIVE);
    assertThat(incident.errorMessage()).isEqualTo(c7Incident.getIncidentMessage());

    // non-null values
    assertThat(incident.incidentKey()).isNotNull();
    assertThat(incident.processInstanceKey()).isNotNull();
    assertThat(incident.rootProcessInstanceKey()).isNotNull();
    assertThat(incident.creationTime()).isNotNull();

    // null values
    assertThat(incident.jobKey()).isNull(); // TODO shouldn't be null?
    assertThat(incident.flowNodeInstanceKey()).isNull(); // TODO shouldn't be null
  }
}
