/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.bpm.engine.variable.Variables.createVariables;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;

import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.migration.data.qa.util.WhiteBox;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Comprehensive transaction rollback tests for ALL entity migrators.
 *
 * This test class verifies that each migrator properly manages transactions by ensuring
 * that when an exception occurs during migration, both C8 insert and migration schema
 * updates are rolled back atomically.
 *
 * Each test follows the pattern:
 * 1. Set up test data in C7
 * 2. Spy on the migrator's markMigrated() method to inject a failure AFTER the C8 insert
 * 3. Verify that both C8 data AND mapping data are rolled back
 * 4. Verify successful retry proves no partial state exists
 *
 * This pattern is the most reliable way to detect missing @Transactional annotations
 * because it tests the scenario where data is written to C8 but the mapping fails,
 * requiring a rollback of the C8 insert.
 */
@WhiteBox
public class TransactionRollbackTest extends HistoryMigrationAbstractTest {

  @MockitoSpyBean
  protected DbClient dbClient;

  @BeforeEach
  public void clearMockInvocations() {
    // Clear invocation history before each test to prevent interference
    clearInvocations(dbClient);
  }

  @Test
  public void shouldRollbackProcessDefinitionInsertWhenMappingFails() {
    // given - deploy a process definition
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    String processDefinitionId = repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey("userTaskProcessId")
        .singleResult()
        .getId();

    // Configure spy to fail ONLY on first process definition insert
    AtomicInteger processDefCallCount = new AtomicInteger(0);
    doAnswer(invocation -> {
      IdKeyMapper.TYPE type = invocation.getArgument(3);
      if (type == IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION) {
        if (processDefCallCount.incrementAndGet() == 1) {
          throw new DataAccessException("Simulated mapping insert failure") {};
        }
      }
      return invocation.callRealMethod();
    }).when(dbClient).insert(
        anyString(),
        anyLong(),
        any(Date.class),
        any(IdKeyMapper.TYPE.class),
        any());

    // when - migration should fail
    assertThatThrownBy(() -> historyMigrator.migrateProcessDefinitions())
        .isInstanceOf(DataAccessException.class);

    // then - verify NO C8 data (rollback occurred)
    List<ProcessDefinitionEntity> definitions = searchHistoricProcessDefinitions("userTaskProcessId");
    assertThat(definitions)
        .as("C8 should contain NO process definitions after rollback")
        .isEmpty();

    // verify NO mapping
    boolean wasMigrated = dbClient.checkHasC8KeyByC7IdAndType(
        processDefinitionId, IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);
    assertThat(wasMigrated)
        .as("Mapping should NOT exist after rollback")
        .isFalse();

    // verify successful retry
    historyMigrator.migrateProcessDefinitions();
    definitions = searchHistoricProcessDefinitions("userTaskProcessId");
    assertThat(definitions)
        .as("After retry, C8 should contain the process definition")
        .hasSize(1);
  }


  @Test
  public void shouldRollbackFlowNodeInsertWhenMappingFails() {
    // given - create process with flow nodes
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // Migrate prerequisites
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateProcessInstances();

    // Configure spy to fail ONLY on first flow node insert
    AtomicInteger flowNodeCallCount = new AtomicInteger(0);
    doAnswer(invocation -> {
      IdKeyMapper.TYPE type = invocation.getArgument(3);
      if (type == IdKeyMapper.TYPE.HISTORY_FLOW_NODE) {
        if (flowNodeCallCount.incrementAndGet() == 1) {
          throw new DataAccessException("Simulated mapping insert failure") {};
        }
      }
      return invocation.callRealMethod();
    }).when(dbClient).insert(
        anyString(),
        anyLong(),
        any(Date.class),
        any(IdKeyMapper.TYPE.class),
        any());

    // when - migration should fail
    assertThatThrownBy(() -> historyMigrator.migrateFlowNodes())
        .isInstanceOf(DataAccessException.class);

    // then - verify NO C8 flow node data (rollback occurred)
    // Get the migrated process instance key to search for flow nodes
    Long processInstanceKey = searchHistoricProcessInstances("userTaskProcessId").getFirst().processInstanceKey();
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodes(processInstanceKey);
    assertThat(flowNodes)
        .as("C8 should contain NO flow nodes after rollback")
        .isEmpty();

    // verify successful retry
    historyMigrator.migrateFlowNodes();
    flowNodes = searchHistoricFlowNodes(processInstanceKey);
    assertThat(flowNodes)
        .as("After retry, C8 should contain flow nodes")
        .isNotEmpty();
  }

  @Test
  public void shouldRollbackUserTaskInsertWhenMappingFails() {
    // given - create process with user task
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    completeAllUserTasksWithDefaultUserTaskId();

    // Migrate prerequisites (UserTask requires ProcessInstance AND FlowNode)
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateProcessInstances();
    historyMigrator.migrateFlowNodes();

    // Configure spy to fail ONLY on first user task markMigrated call
    AtomicInteger userTaskCallCount = new AtomicInteger(0);
    doAnswer(invocation -> {
      IdKeyMapper.TYPE type = invocation.getArgument(3);
      if (type == IdKeyMapper.TYPE.HISTORY_USER_TASK) {
        if (userTaskCallCount.incrementAndGet() == 1) {
          throw new DataAccessException("Simulated mapping insert failure") {};
        }
      }
      return invocation.callRealMethod();
    }).when(dbClient).insert(
        anyString(),
        anyLong(),
        any(Date.class),
        any(IdKeyMapper.TYPE.class),
        any());

    // when - migration should fail
    assertThatThrownBy(() -> historyMigrator.migrateUserTasks())
        .isInstanceOf(DataAccessException.class);

    // then - verify NO C8 user task data (rollback occurred)
    Long processInstanceKey = searchHistoricProcessInstances("userTaskProcessId").getFirst().processInstanceKey();
    List<UserTaskEntity> userTasks = searchHistoricUserTasks(processInstanceKey);
    assertThat(userTasks)
        .as("C8 should contain NO user tasks after rollback")
        .isEmpty();

    // verify successful retry
    historyMigrator.migrateUserTasks();
    userTasks = searchHistoricUserTasks(processInstanceKey);
    assertThat(userTasks)
        .as("After retry, C8 should contain user tasks")
        .isNotEmpty();
  }

  @Test
  public void shouldRollbackVariableInsertWhenMappingFails() {
    // given - create process with variables
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId",
        createVariables()
            .putValue("testVar", "testValue"));
    completeAllUserTasksWithDefaultUserTaskId();

    // Migrate prerequisites
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateProcessInstances();

    // Configure spy to fail ONLY on first variable insert
    AtomicInteger variableCallCount = new AtomicInteger(0);
    doAnswer(invocation -> {
      IdKeyMapper.TYPE type = invocation.getArgument(3);
      if (type == IdKeyMapper.TYPE.HISTORY_VARIABLE) {
        if (variableCallCount.incrementAndGet() == 1) {
          throw new DataAccessException("Simulated mapping insert failure") {};
        }
      }
      return invocation.callRealMethod();
    }).when(dbClient).insert(
        anyString(),
        anyLong(),
        any(Date.class),
        any(IdKeyMapper.TYPE.class),
        any());

    // when - migration should fail
    assertThatThrownBy(() -> historyMigrator.migrateVariables())
        .isInstanceOf(DataAccessException.class);

    // then - verify NO C8 variable data (rollback occurred)
    List<VariableEntity> variables = searchHistoricVariables("testVar");
    assertThat(variables)
        .as("C8 should contain NO variables after rollback")
        .isEmpty();

    // verify successful retry
    historyMigrator.migrateVariables();
    variables = searchHistoricVariables("testVar");
    assertThat(variables)
        .as("After retry, C8 should contain variables")
        .isNotEmpty();
  }

  @Test
  public void shouldRollbackIncidentInsertWhenMappingFails() {
    // given - create process with incident
    deployer.deployCamunda7Process("incidentProcess.bpmn");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("incidentProcessId");

    // Trigger incident
    triggerIncident(processInstance.getId());

    // Migrate prerequisites
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateProcessInstances();

    // Configure spy to fail ONLY on first incident insert
    AtomicInteger incidentCallCount = new AtomicInteger(0);
    doAnswer(invocation -> {
      IdKeyMapper.TYPE type = invocation.getArgument(3);
      if (type == IdKeyMapper.TYPE.HISTORY_INCIDENT) {
        if (incidentCallCount.incrementAndGet() == 1) {
          throw new DataAccessException("Simulated mapping insert failure") {};
        }
      }
      return invocation.callRealMethod();
    }).when(dbClient).insert(
        anyString(),
        anyLong(),
        any(Date.class),
        any(IdKeyMapper.TYPE.class),
        any());

    // when - migration should fail
    assertThatThrownBy(() -> historyMigrator.migrateIncidents())
        .isInstanceOf(DataAccessException.class);

    // then - verify NO C8 incident data (rollback occurred)
    List<IncidentEntity> incidents = searchHistoricIncidents("incidentProcessId");
    assertThat(incidents)
        .as("C8 should contain NO incidents after rollback")
        .isEmpty();

    // verify successful retry
    historyMigrator.migrateIncidents();
    incidents = searchHistoricIncidents("incidentProcessId");
    assertThat(incidents)
        .as("After retry, C8 should contain incidents")
        .isNotEmpty();
  }

  @Test
  public void shouldRollbackDecisionRequirementsInsertWhenMappingFails() {
    // given - deploy a DMN
    deployer.deployCamunda7Decision("dish-decision.dmn");
    String drdId = repositoryService.createDecisionRequirementsDefinitionQuery()
        .singleResult()
        .getKey();

    // Configure spy to fail ONLY on first decision requirements insert
    AtomicInteger drdCallCount = new AtomicInteger(0);
    doAnswer(invocation -> {
      IdKeyMapper.TYPE type = invocation.getArgument(3);
      if (type == IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT) {
        if (drdCallCount.incrementAndGet() == 1) {
          throw new DataAccessException("Simulated mapping insert failure") {};
        }
      }
      return invocation.callRealMethod();
    }).when(dbClient).insert(
        anyString(),
        anyLong(),
        any(Date.class),
        any(IdKeyMapper.TYPE.class),
        any());

    // when - migration should fail
    assertThatThrownBy(() -> historyMigrator.migrateDecisionRequirementsDefinitions())
        .isInstanceOf(DataAccessException.class);

    // then - verify NO C8 data (rollback occurred)
    List<DecisionRequirementsEntity> drds = searchHistoricDecisionRequirementsDefinition(drdId);
    assertThat(drds)
        .as("C8 should contain NO decision requirements after rollback")
        .isEmpty();

    // verify NO mapping
    boolean wasMigrated = dbClient.checkHasC8KeyByC7IdAndType(
        drdId, IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT);
    assertThat(wasMigrated)
        .as("Mapping should NOT exist after rollback")
        .isFalse();

    // verify successful retry
    historyMigrator.migrateDecisionRequirementsDefinitions();
    drds = searchHistoricDecisionRequirementsDefinition(drdId);
    assertThat(drds)
        .as("After retry, C8 should contain decision requirements")
        .hasSize(1);
  }

  @Test
  public void shouldRollbackDecisionDefinitionInsertWhenMappingFails() {
    // given - deploy a DMN
    deployer.deployCamunda7Decision("dish-decision.dmn");
    String decisionDefinitionId = repositoryService.createDecisionDefinitionQuery()
        .decisionDefinitionKey("Dish")
        .singleResult()
        .getKey();

    // Migrate prerequisites
    historyMigrator.migrateDecisionRequirementsDefinitions();

    // Configure spy to fail ONLY on first decision definition insert
    AtomicInteger decisionDefCallCount = new AtomicInteger(0);
    doAnswer(invocation -> {
      IdKeyMapper.TYPE type = invocation.getArgument(3);
      if (type == IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION) {
        if (decisionDefCallCount.incrementAndGet() == 1) {
          throw new DataAccessException("Simulated mapping insert failure") {};
        }
      }
      return invocation.callRealMethod();
    }).when(dbClient).insert(
        anyString(),
        anyLong(),
        any(Date.class),
        any(IdKeyMapper.TYPE.class),
        any());

    // when - migration should fail
    assertThatThrownBy(() -> historyMigrator.migrateDecisionDefinitions())
        .isInstanceOf(DataAccessException.class);

    // then - verify NO C8 data (rollback occurred)
    List<DecisionDefinitionEntity> definitions = searchHistoricDecisionDefinitions(decisionDefinitionId);
    assertThat(definitions)
        .as("C8 should contain NO decision definitions after rollback")
        .isEmpty();

    // verify NO mapping
    boolean wasMigrated = dbClient.checkHasC8KeyByC7IdAndType(
        decisionDefinitionId, IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION);
    assertThat(wasMigrated)
        .as("Mapping should NOT exist after rollback")
        .isFalse();

    // verify successful retry
    historyMigrator.migrateDecisionDefinitions();
    definitions = searchHistoricDecisionDefinitions(decisionDefinitionId);
    assertThat(definitions)
        .as("After retry, C8 should contain decision definitions")
        .hasSize(1);
  }

  @Test
  public void shouldRollbackDecisionInstanceInsertWhenMappingFails() {
    // given - deploy and execute a decision
    deployer.deployCamunda7Decision("dish-decision.dmn");
    String decisionDefinitionId = repositoryService.createDecisionDefinitionQuery()
        .decisionDefinitionKey("Dish")
        .singleResult()
        .getKey();

    decisionService.evaluateDecisionByKey("Dish")
        .variables(createVariables()
            .putValue("season", "Winter")
            .putValue("guestCount", 5))
        .evaluate();

    // Migrate prerequisites
    historyMigrator.migrateDecisionRequirementsDefinitions();
    historyMigrator.migrateDecisionDefinitions();

    // Configure spy to fail ONLY on first decision instance insert
    AtomicInteger decisionInstCallCount = new AtomicInteger(0);
    doAnswer(invocation -> {
      IdKeyMapper.TYPE type = invocation.getArgument(3);
      if (type == IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE) {
        if (decisionInstCallCount.incrementAndGet() == 1) {
          throw new DataAccessException("Simulated mapping insert failure") {};
        }
      }
      return invocation.callRealMethod();
    }).when(dbClient).insert(
        anyString(),
        anyLong(),
        any(Date.class),
        any(IdKeyMapper.TYPE.class),
        any());

    // when - migration should fail
    assertThatThrownBy(() -> historyMigrator.migrateDecisionInstances())
        .isInstanceOf(DataAccessException.class);

    // then - verify NO C8 data (rollback occurred)
    List<DecisionInstanceEntity> instances = searchHistoricDecisionInstances("Dish", "Season", "GuestCount");
    assertThat(instances)
        .as("C8 should contain NO decision instances after rollback")
        .isEmpty();

    // verify successful retry
    historyMigrator.migrateDecisionInstances();
    instances = searchHistoricDecisionInstances("Dish", "Season", "GuestCount");
    assertThat(instances)
        .as("After retry, C8 should contain decision instances")
        .hasSize(3);  // Dish + Season + GuestCount
  }

}

