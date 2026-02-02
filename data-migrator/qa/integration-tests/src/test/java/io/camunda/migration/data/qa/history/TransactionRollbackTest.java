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

  /**
   * Reusable exception for simulating mapping insert failures.
   * Using a static instance avoids creating anonymous subclasses in each test.
   */
  protected static final DataAccessException SIMULATED_MAPPING_FAILURE = 
      new DataAccessException("Simulated mapping insert failure") {};

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

    // when/then - test rollback behavior
    testRollbackWithMappingVerification(
        IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION,
        processDefinitionId,
        () -> historyMigrator.migrateProcessDefinitions(),
        () -> {
          List<ProcessDefinitionEntity> definitions = searchHistoricProcessDefinitions("userTaskProcessId");
          assertThat(definitions)
              .as("C8 should contain NO process definitions after rollback")
              .isEmpty();
        },
        () -> {
          List<ProcessDefinitionEntity> definitions = searchHistoricProcessDefinitions("userTaskProcessId");
          assertThat(definitions)
              .as("After retry, C8 should contain the process definition")
              .hasSize(1);
        }
    );
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

    // Get the migrated process instance key for verification
    Long processInstanceKey = searchHistoricProcessInstances("userTaskProcessId").getFirst().processInstanceKey();

    // when/then - test rollback behavior
    String c7Id = historyService.createHistoricActivityInstanceQuery().list().getFirst().getId();
    testRollbackWithMappingVerification(
        IdKeyMapper.TYPE.HISTORY_FLOW_NODE,
        c7Id,
        () -> historyMigrator.migrateFlowNodes(),
        () -> {
          List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodes(processInstanceKey);
          assertThat(flowNodes)
              .as("C8 should contain NO flow nodes after rollback")
              .isEmpty();
        },
        () -> {
          List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodes(processInstanceKey);
          assertThat(flowNodes)
              .as("After retry, C8 should contain flow nodes")
              .hasSize(3);
        }
    );
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

    // Get the migrated process instance key for verification
    Long processInstanceKey = searchHistoricProcessInstances("userTaskProcessId").getFirst().processInstanceKey();

    // when/then - test rollback behavior
    String c7Id = historyService.createHistoricTaskInstanceQuery()
        .processDefinitionKey("userTaskProcessId")
        .singleResult()
        .getId();
    testRollbackWithMappingVerification(
        IdKeyMapper.TYPE.HISTORY_USER_TASK,
        c7Id,
        () -> historyMigrator.migrateUserTasks(),
        () -> {
          List<UserTaskEntity> userTasks = searchHistoricUserTasks(processInstanceKey);
          assertThat(userTasks)
              .as("C8 should contain NO user tasks after rollback")
              .isEmpty();
        },
        () -> {
          List<UserTaskEntity> userTasks = searchHistoricUserTasks(processInstanceKey);
          assertThat(userTasks)
              .as("After retry, C8 should contain user tasks")
              .isNotEmpty();
        }
    );
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

    // when/then - test rollback behavior
    String c7Id = historyService.createHistoricVariableInstanceQuery().singleResult().getId();
    testRollbackWithMappingVerification(
        IdKeyMapper.TYPE.HISTORY_VARIABLE,
        c7Id,
        () -> historyMigrator.migrateVariables(),
        () -> {
          List<VariableEntity> variables = searchHistoricVariables("testVar");
          assertThat(variables)
              .as("C8 should contain NO variables after rollback")
              .isEmpty();
        },
        () -> {
          List<VariableEntity> variables = searchHistoricVariables("testVar");
          assertThat(variables)
              .as("After retry, C8 should contain variables")
              .isNotEmpty();
        }
    );
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

    // when/then - test rollback behavior
    String c7Id = historyService.createHistoricIncidentQuery().singleResult().getId();
    testRollbackWithMappingVerification(
        IdKeyMapper.TYPE.HISTORY_INCIDENT,
        c7Id,
        () -> historyMigrator.migrateIncidents(),
        () -> {
          List<IncidentEntity> incidents = searchHistoricIncidents("incidentProcessId");
          assertThat(incidents)
              .as("C8 should contain NO incidents after rollback")
              .isEmpty();
        },
        () -> {
          List<IncidentEntity> incidents = searchHistoricIncidents("incidentProcessId");
          assertThat(incidents)
              .as("After retry, C8 should contain incidents")
              .isNotEmpty();
        }
    );
  }

  @Test
  public void shouldRollbackDecisionRequirementsInsertWhenMappingFails() {
    // given - deploy a DMN
    deployer.deployCamunda7Decision("dish-decision.dmn");
    String drdId = repositoryService.createDecisionRequirementsDefinitionQuery()
        .singleResult()
        .getId();

    // when/then - test rollback behavior
    testRollbackWithMappingVerification(
        IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT,
        drdId,
        () -> historyMigrator.migrateDecisionRequirementsDefinitions(),
        () -> {
          List<DecisionRequirementsEntity> drds = searchHistoricDecisionRequirementsDefinition("dish-decision");
          assertThat(drds)
              .as("C8 should contain NO decision requirements after rollback")
              .isEmpty();
        },
        () -> {
          List<DecisionRequirementsEntity> drds = searchHistoricDecisionRequirementsDefinition("dish-decision");
          assertThat(drds)
              .as("After retry, C8 should contain decision requirements")
              .hasSize(1);
        }
    );
  }

  @Test
  public void shouldRollbackDecisionDefinitionInsertWhenMappingFails() {
    // given - deploy a DMN
    deployer.deployCamunda7Decision("dish-decision.dmn");
    String decisionDefinitionId = repositoryService.createDecisionDefinitionQuery()
        .decisionDefinitionKey("Dish")
        .singleResult()
        .getId();

    // Migrate prerequisites
    historyMigrator.migrateDecisionRequirementsDefinitions();

    // when/then - test rollback behavior
    testRollbackWithMappingVerification(
        IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION,
        decisionDefinitionId,
        () -> historyMigrator.migrateDecisionDefinitions(),
        () -> {
          List<DecisionDefinitionEntity> definitions = searchHistoricDecisionDefinitions("Dish");
          assertThat(definitions)
              .as("C8 should contain NO decision definitions after rollback")
              .isEmpty();
        },
        () -> {
          List<DecisionDefinitionEntity> definitions = searchHistoricDecisionDefinitions("Dish");
          assertThat(definitions)
              .as("After retry, C8 should contain decision definitions")
              .hasSize(1);
        }
    );
  }

  @Test
  public void shouldRollbackDecisionInstanceInsertWhenMappingFails() {
    // given - deploy and execute a decision
    deployer.deployCamunda7Decision("dish-decision.dmn");

    decisionService.evaluateDecisionByKey("Dish")
        .variables(createVariables()
            .putValue("season", "Winter")
            .putValue("guestCount", 5))
        .evaluate();

    // Migrate prerequisites
    historyMigrator.migrateDecisionRequirementsDefinitions();
    historyMigrator.migrateDecisionDefinitions();

    // when/then - test rollback behavior
    String c7Id = historyService.createHistoricDecisionInstanceQuery()
        .decisionDefinitionKey("Dish")
        .singleResult()
        .getId();

    testRollbackWithMappingVerification(
        IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE,
        c7Id,
        () -> historyMigrator.migrateDecisionInstances(),
        () -> {
          List<DecisionInstanceEntity> instances = searchHistoricDecisionInstances("Dish", "Season", "GuestCount");
          assertThat(instances)
              .as("C8 should contain NO decision instances after rollback")
              .isEmpty();
        },
        () -> {
          List<DecisionInstanceEntity> instances = searchHistoricDecisionInstances("Dish", "Season", "GuestCount");
          assertThat(instances)
              .as("After retry, C8 should contain decision instances")
              .hasSize(3);  // Dish + Season + GuestCount
        }
    );
  }

  // helper ////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Configures the spy to fail on the first insert for a specific entity type.
   *
   * @param entityType the type of entity to fail on
   */
  protected void configureSpyToFailOnFirstInsert(IdKeyMapper.TYPE entityType) {
    AtomicInteger callCount = new AtomicInteger(0);
    doAnswer(invocation -> {
      IdKeyMapper.TYPE type = invocation.getArgument(3);
      if (type == entityType) {
        if (callCount.incrementAndGet() == 1) {
          throw SIMULATED_MAPPING_FAILURE;
        }
      }
      return invocation.callRealMethod();
    }).when(dbClient).insert(
        anyString(),
        anyString(),
        any(Date.class),
        any(IdKeyMapper.TYPE.class),
        any());
  }

  /**
   * Tests rollback with mapping verification for entities that have ID-based mapping.
   *
   * @param entityType the entity type being tested
   * @param c7Id the C7 entity ID to check mapping for
   * @param migration the migration operation to execute
   * @param verifyEmptyC8Data verification that C8 contains no data after rollback
   * @param verifySuccessfulRetry verification that retry succeeds and data exists
   */
  protected void testRollbackWithMappingVerification(
      IdKeyMapper.TYPE entityType,
      String c7Id,
      Runnable migration,
      Runnable verifyEmptyC8Data,
      Runnable verifySuccessfulRetry) {

    // Configure spy to fail on first insert
    configureSpyToFailOnFirstInsert(entityType);

    // when - migration should fail
    assertThatThrownBy(migration::run)
        .isInstanceOf(DataAccessException.class)
        .isSameAs(SIMULATED_MAPPING_FAILURE);

    // then - verify NO C8 data (rollback occurred)
    verifyEmptyC8Data.run();

    // verify NO mapping
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(c7Id, entityType))
        .as("Mapping should NOT exist after rollback")
        .isFalse();

    // verify successful retry
    migration.run();
    verifySuccessfulRetry.run();

    // verify mapping
    assertThat(dbClient.checkHasC8KeyByC7IdAndType(c7Id, entityType))
        .as("Mapping should exist after commit")
        .isTrue();
  }

}

