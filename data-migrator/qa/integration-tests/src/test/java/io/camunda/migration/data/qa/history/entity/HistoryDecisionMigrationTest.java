/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.NOT_MIGRATING_DECISION_INSTANCE;
import static io.camunda.migration.data.qa.util.LogMessageFormatter.formatMessage;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.BUSINESS_RULE_TASK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.HistoryMigrationExtension;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.github.netmikey.logunit.api.LogCapturer;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;

public class HistoryDecisionMigrationTest extends AbstractMigratorTest {

  @RegisterExtension
  protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

  @Test
  public void shouldMigrateSingleHistoricDecision() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<DecisionDefinitionEntity> migratedDecisions = historyMigration.searchHistoricDecisionDefinitions("simpleDecisionId");
    assertThat(migratedDecisions).singleElement().satisfies(decision -> {
      assertThat(decision.decisionDefinitionId()).isEqualTo("simpleDecisionId");
      assertThat(decision.decisionDefinitionKey()).isNotNull();
      assertThat(decision.version()).isEqualTo(1);
      assertThat(decision.name()).isEqualTo("simpleDecisionName");
      assertThat(decision.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
      assertThat(decision.decisionRequirementsKey()).isNull();
      assertThat(decision.decisionRequirementsId()).isNull();
    });
  }

  @Test
  public void shouldMigrateHistoricDecisionWithRequirements() {
    // given
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");

    // when
    historyMigration.getMigrator().migrate();
    List<DecisionDefinitionEntity> firstDecision = historyMigration.searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id");
    List<DecisionDefinitionEntity> secondDecision = historyMigration.searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id");
    List<DecisionRequirementsEntity> decisionReqs = historyMigration.searchHistoricDecisionRequirementsDefinition("simpleDmnWithReqsId");

    // then
    assertThat(decisionReqs).singleElement().satisfies(decisionRequirements -> {
      assertThat(decisionRequirements.decisionRequirementsId()).isEqualTo("simpleDmnWithReqsId");
      assertThat(decisionRequirements.decisionRequirementsKey()).isNotNull();
      assertThat(decisionRequirements.version()).isEqualTo(1);
      assertThat(decisionRequirements.name()).isEqualTo("simpleDmnWithReqsName");
      assertThat(decisionRequirements.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
      assertThat(decisionRequirements.xml()).isNull();
      assertThat(decisionRequirements.resourceName()).isEqualTo("io/camunda/migration/data/dmn/c7/simpleDmnWithReqs.dmn");
    });
    Long decisionReqsKey = decisionReqs.get(0).decisionRequirementsKey();

    assertThat(firstDecision).singleElement().satisfies(decision -> {
      assertThat(decision.decisionDefinitionId()).isEqualTo("simpleDmnWithReqs1Id");
      assertThat(decision.decisionDefinitionKey()).isNotNull();
      assertThat(decision.version()).isEqualTo(1);
      assertThat(decision.name()).isEqualTo("simpleDmnWithReqs1Name");
      assertThat(decision.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
      assertThat(decision.decisionRequirementsKey()).isEqualTo(decisionReqsKey);
      assertThat(decision.decisionRequirementsId()).isEqualTo("simpleDmnWithReqsId");
    });

    assertThat(secondDecision).singleElement().satisfies(decision -> {
      assertThat(decision.decisionDefinitionId()).isEqualTo("simpleDmnWithReqs2Id");
      assertThat(decision.decisionDefinitionKey()).isNotNull();
      assertThat(decision.version()).isEqualTo(1);
      assertThat(decision.name()).isEqualTo("simpleDmnWithReqs2Name");
      assertThat(decision.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
      assertThat(decision.decisionRequirementsKey()).isEqualTo(decisionReqsKey);
      assertThat(decision.decisionRequirementsId()).isEqualTo("simpleDmnWithReqsId");
    });
  }

  @Test
  public void shouldMigrateHistoricDecisionInstance() {
    // given
    Date now = ClockUtil.now();
    ClockUtil.setCurrentTime(now);
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    Map<String, Object> variables = Variables.createVariables().putValue("inputA", stringValue("A"));
    runtimeService.startProcessInstanceByKey("businessRuleProcessId", variables);

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<ProcessInstanceEntity> migratedProcessInstances = historyMigration.searchHistoricProcessInstances("businessRuleProcessId");
    assertThat(migratedProcessInstances).singleElement();
    List<DecisionDefinitionEntity> migratedDecisions = historyMigration.searchHistoricDecisionDefinitions("simpleDecisionId");
    assertThat(migratedDecisions).singleElement();
    List<FlowNodeInstanceEntity> migratedFlowNodeInstances = historyMigration.searchHistoricFlowNodesForType(
        migratedProcessInstances.getFirst().processInstanceKey(), BUSINESS_RULE_TASK);
    assertThat(migratedFlowNodeInstances).singleElement();
    List<DecisionInstanceEntity> migratedInstances = historyMigration.searchHistoricDecisionInstances("simpleDecisionId");
    HistoricDecisionInstance c7Instance = historyService.createHistoricDecisionInstanceQuery()
        .decisionDefinitionKey("simpleDecisionId")
        .singleResult();

    assertThat(migratedInstances).singleElement().satisfies(instance -> {
      assertThat(instance.decisionInstanceId()).isEqualTo(
          instance.decisionInstanceKey() + "-" + c7Instance.getId());
      assertThat(instance.decisionInstanceKey()).isNotNull();
      assertThat(instance.state()).isNull();
      assertThat(instance.evaluationDate()).isEqualTo(
          OffsetDateTime.ofInstant(now.toInstant(), ZoneId.systemDefault()));
      assertThat(instance.evaluationFailure()).isNull();
      assertThat(instance.evaluationFailureMessage()).isNull();
      assertThat(instance.flowNodeInstanceKey()).isEqualTo(migratedFlowNodeInstances.getFirst().flowNodeInstanceKey());
      assertThat(instance.processInstanceKey()).isEqualTo(migratedProcessInstances.getFirst().processInstanceKey());
      assertThat(instance.processDefinitionKey()).isEqualTo(migratedProcessInstances.getFirst().processDefinitionKey());
      assertThat(instance.decisionDefinitionKey()).isEqualTo(migratedDecisions.getFirst().decisionDefinitionKey());
      assertThat(instance.decisionDefinitionId()).isEqualTo("simpleDecisionId");
      assertThat(instance.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
      assertThat(instance.decisionDefinitionType()).isNull();

      // TODO find out how to get a result http://github.com/camunda/camunda-bpm-platform/issues/5365
      //      assertThat(instance.result()).isEqualTo("B");

      // TODO https://github.com/camunda/camunda-bpm-platform/issues/5364
      //      assertThat(instance.evaluatedInputs()).singleElement().satisfies(input -> {
      //        assertThat(input.inputId()).isNotNull();
      //        assertThat(input.inputName()).isEqualTo("inputA");
      //        assertThat(input.inputValue()).isEqualTo("A");
      //      });
      //      assertThat(instance.evaluatedOutputs()).singleElement().satisfies(output -> {
      //        assertThat(output.outputId()).isNotNull();
      //        assertThat(output.outputName()).isEqualTo("outputB");
      //        assertThat(output.outputValue()).isEqualTo("B");
      //      });
    });
  }

  @Test
  public void shouldMigrateHistoricDecisionInstanceWithNonDefaultTenant() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn", "aTenantId");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn", "aTenantId");
    Map<String, Object> variables = Variables.createVariables().putValue("inputA", stringValue("A"));
    runtimeService.startProcessInstanceByKey("businessRuleProcessId", variables);

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<DecisionInstanceEntity> migratedInstances = historyMigration.searchHistoricDecisionInstances("simpleDecisionId");
    assertThat(migratedInstances).flatExtracting(DecisionInstanceEntity::tenantId)
        .singleElement()
        .isEqualTo("aTenantId");
  }

  @Test
  public void shouldMigrateChildDecisionInstanceWhenParentInstanceTriggeredFromBusinessTask() {
    // given
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");
    deployer.deployCamunda7Process("businessRuleForDmnWithReqs.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleForDmnWithReqsId",
        Variables.createVariables().putValue("inputA", stringValue("A")));

    // when
    historyMigration.getMigrator().migrate();

    // then
    List<DecisionInstanceEntity> instances1 = historyMigration.searchHistoricDecisionInstances("simpleDmnWithReqs1Id");
    List<DecisionInstanceEntity> instances2 = historyMigration.searchHistoricDecisionInstances("simpleDmnWithReqs2Id");
    assertThat(instances1).singleElement();
    assertThat(instances2).singleElement();
  }

  @Test
  public void shouldNotMigrateDecisionInstanceWhenNotOriginatingFromProcessDefinition() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    Map<String, Object> variables = Variables.createVariables().putValue("inputA", stringValue("A"));
    decisionService.evaluateDecisionTableByKey("simpleDecisionId", variables);
    String decisionInstanceId = historyService.createHistoricDecisionInstanceQuery().decisionDefinitionKey("simpleDecisionId").singleResult().getId();

    // when
    historyMigration.getMigrator().migrate();

    // then
    assertThat(historyMigration.searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(NOT_MIGRATING_DECISION_INSTANCE, decisionInstanceId));
  }
}
