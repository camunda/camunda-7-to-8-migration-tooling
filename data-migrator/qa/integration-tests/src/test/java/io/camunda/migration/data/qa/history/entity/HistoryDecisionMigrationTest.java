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
import static io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState.EVALUATED;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.BUSINESS_RULE_TASK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
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
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;

public class HistoryDecisionMigrationTest extends HistoryMigrationAbstractTest {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

  @Test
  public void shouldMigrateSingleHistoricDecision() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");

    // when
    historyMigrator.migrate();

    // then
    List<DecisionDefinitionEntity> migratedDecisions = searchHistoricDecisionDefinitions("simpleDecisionId");
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
    historyMigrator.migrate();
    List<DecisionDefinitionEntity> firstDecision = searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id");
    List<DecisionDefinitionEntity> secondDecision = searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id");
    List<DecisionRequirementsEntity> decisionReqs = searchHistoricDecisionRequirementsDefinition("simpleDmnWithReqsId");

    // then
    assertThat(decisionReqs).singleElement().satisfies(decisionRequirements -> {
      assertThat(decisionRequirements.decisionRequirementsId()).isEqualTo("simpleDmnWithReqsId");
      assertThat(decisionRequirements.decisionRequirementsKey()).isNotNull();
      assertThat(decisionRequirements.version()).isEqualTo(1);
      assertThat(decisionRequirements.name()).isEqualTo("simpleDmnWithReqsName");
      assertThat(decisionRequirements.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
      assertThat(decisionRequirements.xml()).isNull();
      assertThat(decisionRequirements.resourceName()).isEqualTo(
          "io/camunda/migration/data/dmn/c7/simpleDmnWithReqs.dmn");
    });
    Long decisionReqsKey = decisionReqs.get(0).decisionRequirementsKey();

    assertThat(firstDecision).singleElement()
        .satisfies(decision -> assertDecisionDefinition(decision, "simpleDmnWithReqs1Id", "simpleDmnWithReqs1Name", 1,
            decisionReqsKey, "simpleDmnWithReqsId"));

    assertThat(secondDecision).singleElement()
        .satisfies(decision -> assertDecisionDefinition(decision, "simpleDmnWithReqs2Id", "simpleDmnWithReqs2Name", 1,
            decisionReqsKey, "simpleDmnWithReqsId"));
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
    historyMigrator.migrate();

    // then
    List<ProcessInstanceEntity> migratedProcessInstances = searchHistoricProcessInstances("businessRuleProcessId");
    assertThat(migratedProcessInstances).singleElement();
    List<DecisionDefinitionEntity> migratedDecisions = searchHistoricDecisionDefinitions("simpleDecisionId");
    assertThat(migratedDecisions).singleElement();
    List<FlowNodeInstanceEntity> migratedFlowNodeInstances = searchHistoricFlowNodesForType(
        migratedProcessInstances.getFirst().processInstanceKey(), BUSINESS_RULE_TASK);
    assertThat(migratedFlowNodeInstances).singleElement();
    List<DecisionInstanceEntity> migratedInstances = searchHistoricDecisionInstances("simpleDecisionId");

    assertThat(migratedInstances).singleElement().satisfies(instance ->
        assertDecisionInstance(
            instance,
            "simpleDecisionId",
            now,
            migratedFlowNodeInstances.getFirst().flowNodeInstanceKey(),
            migratedProcessInstances.getFirst().processInstanceKey(),
            migratedProcessInstances.getFirst().processDefinitionKey(),
            migratedDecisions.getFirst().decisionDefinitionKey(),
            DecisionInstanceEntity.DecisionDefinitionType.DECISION_TABLE,
            "inputA", "\"A\"",
            "outputB", "\"B\""));
  }

  @Test
  public void shouldMigrateHistoricDecisionInstanceWithNonDefaultTenant() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn", "aTenantId");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn", "aTenantId");
    Map<String, Object> variables = Variables.createVariables().putValue("inputA", stringValue("A"));
    runtimeService.startProcessInstanceByKey("businessRuleProcessId", variables);

    // when
    historyMigrator.migrate();

    // then
    List<DecisionInstanceEntity> migratedInstances = searchHistoricDecisionInstances("simpleDecisionId");
    assertThat(migratedInstances).flatExtracting(DecisionInstanceEntity::tenantId)
        .singleElement()
        .isEqualTo("aTenantId");
  }

  @Test
  public void shouldMigrateChildDecisionInstances() {
    // given
    Date now = ClockUtil.now();
    ClockUtil.setCurrentTime(now);
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");
    deployer.deployCamunda7Process("businessRuleForDmnWithReqs.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleForDmnWithReqsId",
        Variables.createVariables().putValue("inputA", stringValue("A")));

    // when
    historyMigrator.migrate();

    // then
    List<DecisionInstanceEntity> instances1 = searchHistoricDecisionInstances("simpleDmnWithReqs1Id");
    List<DecisionInstanceEntity> instances2 = searchHistoricDecisionInstances("simpleDmnWithReqs2Id");
    List<DecisionDefinitionEntity> migratedDecisions1 = searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id");
    assertThat(migratedDecisions1).singleElement();
    List<DecisionDefinitionEntity> migratedDecisions2 = searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id");
    assertThat(migratedDecisions2).singleElement();
    List<ProcessInstanceEntity> migratedProcessInstances = searchHistoricProcessInstances("businessRuleForDmnWithReqsId");
    assertThat(migratedProcessInstances).singleElement();
    List<FlowNodeInstanceEntity> migratedFlowNodeInstances = searchHistoricFlowNodesForType(
        migratedProcessInstances.getFirst().processInstanceKey(), BUSINESS_RULE_TASK);
    assertThat(migratedFlowNodeInstances).singleElement();

    assertThat(instances1).singleElement().satisfies(instance ->
        assertDecisionInstance(
            instance,
            "simpleDmnWithReqs1Id",
            now,
            migratedFlowNodeInstances.getFirst().flowNodeInstanceKey(),
            migratedProcessInstances.getFirst().processInstanceKey(),
            migratedProcessInstances.getFirst().processDefinitionKey(),
            migratedDecisions1.getFirst().decisionDefinitionKey(),
            DecisionInstanceEntity.DecisionDefinitionType.DECISION_TABLE,
            "inputA", "\"A\"",
            "outputB", "\"B\""));

    assertThat(instances2).singleElement().satisfies(instance ->
        assertDecisionInstance(
            instance,
            "simpleDmnWithReqs2Id",
            now,
            migratedFlowNodeInstances.getFirst().flowNodeInstanceKey(),
            migratedProcessInstances.getFirst().processInstanceKey(),
            migratedProcessInstances.getFirst().processDefinitionKey(),
            migratedDecisions2.getFirst().decisionDefinitionKey(),
            DecisionInstanceEntity.DecisionDefinitionType.DECISION_TABLE,
            "inputB", "\"B\"",
            "outputC", "\"C\""));
  }

  @Test
  public void shouldMigrateHistoricDecisionInstanceWithLiteralExpression() {
    // given
    deployer.deployCamunda7Decision("literalExpressionDmn.dmn");
    deployer.deployCamunda7Process("businessRuleLiteralExpressionProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleLiteralExpressionProcessId");

    // when
    historyMigrator.migrate();

    // then
    List<DecisionInstanceEntity> migratedInstances = searchHistoricDecisionInstances("literalExpressionDecisionId");
    assertThat(migratedInstances).singleElement().satisfies(instance -> {
      assertThat(instance.decisionDefinitionType()).isEqualTo(DecisionInstanceEntity.DecisionDefinitionType.LITERAL_EXPRESSION);
    });
  }

  @Test
  public void shouldMigrateHistoricDecisionInstancesWithMixedDecisionTypes() {
    // given
    deployer.deployCamunda7Decision("mixedDecisionTypesDmn.dmn");
    deployer.deployCamunda7Process("businessRuleMixedTypesProcess.bpmn");
    Map<String, Object> variables = Variables.createVariables().putValue("inputA", stringValue("A"));
    runtimeService.startProcessInstanceByKey("businessRuleMixedTypesProcessId", variables);

    // when
    historyMigrator.migrate();

    // then
    List<DecisionInstanceEntity> decisionTableInstances = searchHistoricDecisionInstances("decisionTableInMixedId");
    assertThat(decisionTableInstances).singleElement().satisfies(instance -> {
      assertThat(instance.decisionDefinitionType()).isEqualTo(DecisionInstanceEntity.DecisionDefinitionType.DECISION_TABLE);
    });

    List<DecisionInstanceEntity> literalExpressionInstances = searchHistoricDecisionInstances("literalExpressionInMixedId");
    assertThat(literalExpressionInstances).singleElement().satisfies(instance -> {
      assertThat(instance.decisionDefinitionType()).isEqualTo(DecisionInstanceEntity.DecisionDefinitionType.LITERAL_EXPRESSION);
    });
  }

  @Test
  public void shouldNotMigrateDecisionInstanceWhenNotOriginatingFromProcessDefinition() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    Map<String, Object> variables = Variables.createVariables().putValue("inputA", stringValue("A"));
    decisionService.evaluateDecisionTableByKey("simpleDecisionId", variables);
    String decisionInstanceId = historyService.createHistoricDecisionInstanceQuery().decisionDefinitionKey("simpleDecisionId").singleResult().getId();

    // when
    historyMigrator.migrate();

    // then
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(formatMessage(NOT_MIGRATING_DECISION_INSTANCE, decisionInstanceId));
  }

  private void assertDecisionDefinition(
      DecisionDefinitionEntity decision,
      String decisionId,
      String decisionName,
      int version,
      Long decisionRequirementsKey,
      String decisionRequirementsId) {
    assertThat(decision.decisionDefinitionId()).isEqualTo(decisionId);
    assertThat(decision.decisionDefinitionKey()).isNotNull();
    assertThat(decision.version()).isEqualTo(version);
    assertThat(decision.name()).isEqualTo(decisionName);
    assertThat(decision.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(decision.decisionRequirementsKey()).isEqualTo(decisionRequirementsKey);
    assertThat(decision.decisionRequirementsId()).isEqualTo(decisionRequirementsId);
  }

  private void assertDecisionInstance(
      DecisionInstanceEntity instance,
      String decisionDefinitionId,
      Date evaluationDate,
      Long flowNodeInstanceKey,
      Long processInstanceKey,
      Long processDefinitionKey,
      Long decisionDefinitionKey,
      DecisionInstanceEntity.DecisionDefinitionType decisionDefinitionType,
      String inputName,
      String inputValue,
      String outputName,
      String outputValue) {
    assertThat(instance.decisionInstanceId()).isNotNull();
    assertThat(instance.decisionInstanceKey()).isNotNull();
    assertThat(instance.state()).isEqualTo(EVALUATED);
    assertThat(instance.evaluationDate()).isEqualTo(
        OffsetDateTime.ofInstant(evaluationDate.toInstant(), ZoneId.systemDefault()));
    assertThat(instance.evaluationFailure()).isNull();
    assertThat(instance.evaluationFailureMessage()).isNull();
    assertThat(instance.flowNodeInstanceKey()).isEqualTo(flowNodeInstanceKey);
    assertThat(instance.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(instance.processDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(instance.decisionDefinitionKey()).isEqualTo(decisionDefinitionKey);
    assertThat(instance.decisionDefinitionId()).isEqualTo(decisionDefinitionId);
    assertThat(instance.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(instance.decisionDefinitionType()).isEqualTo(decisionDefinitionType);
    assertThat(instance.result()).isNull();
    assertThat(instance.rootDecisionDefinitionKey()).isNull();

    assertThat(instance.evaluatedInputs()).singleElement().satisfies(input -> {
      assertThat(input.inputId()).isNotNull();
      assertThat(input.inputName()).isEqualTo(inputName);
      assertThat(input.inputValue()).isEqualTo(inputValue);
    });

    assertThat(instance.evaluatedOutputs()).singleElement().satisfies(output -> {
      assertThat(output.outputId()).isNotNull();
      assertThat(output.outputName()).isEqualTo(outputName);
      assertThat(output.outputValue()).isEqualTo(outputValue);
    });
  }
}
