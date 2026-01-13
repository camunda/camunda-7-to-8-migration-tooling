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
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.BUSINESS_RULE_TASK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.qa.extension.CleanupExtension;
import io.camunda.migration.data.qa.extension.RdbmsQueryExtension;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.github.netmikey.logunit.api.LogCapturer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;

public class HistoryDecisionMigrationTest extends HistoryMigrationAbstractTest {
  protected static final String BUSINESS_RULE_PROCESS_ID_PATTERN = "BusinessRuleProcess_%s";

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

  @RegisterExtension
  RdbmsQueryExtension rdbmsQuery = new RdbmsQueryExtension();

  @RegisterExtension
  CleanupExtension cleanup = new CleanupExtension(rdbmsQuery);

  @Test
  public void shouldMigrateSingleHistoricDecision() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");

    // when
    historyMigrator.migrate();

    // then
    List<DecisionDefinitionEntity> migratedDecisions = searchHistoricDecisionDefinitions("simpleDecisionId");
    assertThat(migratedDecisions).singleElement().satisfies(decision -> {
      assertThat(decision.decisionDefinitionId()).isEqualTo(prefixDefinitionId("simpleDecisionId"));
      assertThat(decision.decisionDefinitionKey()).isNotNull();
      assertThat(decision.version()).isEqualTo(1);
      assertThat(decision.name()).isEqualTo("simpleDecisionName");
      assertThat(decision.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
      assertThat(decision.decisionRequirementsKey()).isNotNull();
      assertThat(decision.decisionRequirementsId()).isNull();
      // For standalone decision (no DRD in C7), name and version should be from the decision itself
      assertThat(decision.decisionRequirementsName()).isEqualTo("simpleDecisionName");
      assertThat(decision.decisionRequirementsVersion()).isEqualTo(1);
    });

    List<DecisionRequirementsEntity> decisionReqs =
        searchHistoricDecisionRequirementsDefinition("simpleDmnId");
    assertThat(decisionReqs).singleElement().satisfies(decisionRequirements -> {
      assertThat(decisionRequirements.decisionRequirementsId())
          .isEqualTo("c7-legacy-simpleDmnId");
      assertThat(decisionRequirements.decisionRequirementsKey())
          .isNotNull()
          .isEqualTo(migratedDecisions.getFirst().decisionRequirementsKey());
      assertThat(decisionRequirements.version()).isEqualTo(1);
      assertThat(decisionRequirements.name()).isEqualTo("simpleDecisionName");
      assertThat(decisionRequirements.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
      assertThat(decisionRequirements.xml()).isNotNull();
      assertThat(decisionRequirements.resourceName()).isEqualTo("io/camunda/migration/data/dmn/c7/simpleDmn.dmn");
    });
  }

  @Test
  public void shouldGenerateDecisionRequirementsForDifferentVersionsOfSingleHistoricDecision() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    historyMigrator.migrate();
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    historyMigrator.migrate();

    // then
    List<DecisionDefinition> c7Definitions = repositoryService.createDecisionDefinitionQuery()
        .decisionDefinitionKey("simpleDecisionId")
        .orderByDecisionDefinitionVersion()
        .asc()
        .list();
    assertThat(c7Definitions).hasSize(2);

    List<DecisionDefinitionEntity> migratedDecisions = searchHistoricDecisionDefinitions("simpleDecisionId");
    List<DecisionRequirementsEntity> decisionReqs = searchHistoricDecisionRequirementsDefinition("simpleDmnId");

    assertThat(decisionReqs).hasSize(2);
    assertThat(migratedDecisions).hasSize(2);
    assertThat(migratedDecisions).allSatisfy(decision -> assertThat(decision.decisionRequirementsId()).isNull());
    assertThat(migratedDecisions).extracting(DecisionDefinitionEntity::decisionRequirementsKey)
        .containsExactlyInAnyOrderElementsOf(
            decisionReqs.stream().map(DecisionRequirementsEntity::decisionRequirementsKey).toList());
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
      assertThat(decisionRequirements.decisionRequirementsId()).isEqualTo(prefixDefinitionId("simpleDmnWithReqsId"));
      assertThat(decisionRequirements.decisionRequirementsKey()).isNotNull();
      assertThat(decisionRequirements.version()).isEqualTo(1);
      assertThat(decisionRequirements.name()).isEqualTo("simpleDmnWithReqsName");
      assertThat(decisionRequirements.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
      assertThat(decisionRequirements.xml()).isNotNull();
      assertThat(decisionRequirements.resourceName()).isEqualTo(
          "io/camunda/migration/data/dmn/c7/simpleDmnWithReqs.dmn");
    });
    Long decisionReqsKey = decisionReqs.get(0).decisionRequirementsKey();

    assertThat(firstDecision).singleElement()
        .satisfies(decision -> assertDecisionDefinition(decision, "simpleDmnWithReqs1Id", "simpleDmnWithReqs1Name", 1,
            decisionReqsKey, "simpleDmnWithReqsId", "simpleDmnWithReqsName", 1));

    assertThat(secondDecision).singleElement()
        .satisfies(decision -> assertDecisionDefinition(decision, "simpleDmnWithReqs2Id", "simpleDmnWithReqs2Name", 1,
            decisionReqsKey, "simpleDmnWithReqsId", "simpleDmnWithReqsName", 1));
  }

  @Test
  public void shouldMigrateHistoricDecisionWithAdjustedXml() {
    // given
    deployer.deployCamunda7Decision("dish-decision.dmn");

    // when
    historyMigrator.migrate();

    // then
    List<DecisionRequirementsEntity> decisionReqs = searchHistoricDecisionRequirementsDefinition("dish-decision");

    assertThat(decisionReqs).singleElement().extracting("xml").asString()
        .contains("id=\"c7-legacy-Dish\"")
        .contains("id=\"c7-legacy-Season\"")
        .contains("id=\"c7-legacy-GuestCount\"")
        .contains("<requiredDecision href=\"#c7-legacy-Season\"/>")
        .contains("<requiredDecision href=\"#c7-legacy-GuestCount\"/>")
        .contains("dmnElementRef=\"c7-legacy-Dish\"")
        .contains("dmnElementRef=\"c7-legacy-Season\"")
        .contains("dmnElementRef=\"c7-legacy-GuestCount\"")

        .doesNotContain("id=\"Dish\"")
        .doesNotContain("id=\"Season\"")
        .doesNotContain("id=\"GuestCount\"")
        .doesNotContain("<requiredDecision href=\"#Season\"/>")
        .doesNotContain("<requiredDecision href=\"#GuestCount\"/>")
        .doesNotContain("dmnElementRef=\"Dish\"")
        .doesNotContain("dmnElementRef=\"Season\"")
        .doesNotContain("dmnElementRef=\"GuestCount\"");
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
            "\"B\"",
            "inputA", "\"A\"",
            "outputB", "\"B\""));
  }

  @Test
  public void shouldMigrateHistoricDecisionInstanceFromStandaloneDmn() {
    // given
    Date now = ClockUtil.now();
    ClockUtil.setCurrentTime(now);
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    Map<String, Object> variables = Variables.createVariables().putValue("inputA", stringValue("A"));
    decisionService.evaluateDecisionTableByKey("simpleDecisionId", variables);

    // when
    historyMigrator.migrate();

    // then
    List<DecisionDefinitionEntity> migratedDecisions = searchHistoricDecisionDefinitions("simpleDecisionId");
    List<DecisionInstanceEntity> migratedInstances = searchHistoricDecisionInstances("simpleDecisionId");

    assertThat(migratedDecisions).singleElement();
    assertThat(migratedInstances).singleElement().satisfies(instance ->
        assertDecisionInstance(
            instance,
            "simpleDecisionId",
            now,
            null,
            null,
            null,
            migratedDecisions.getFirst().decisionDefinitionKey(),
            DecisionInstanceEntity.DecisionDefinitionType.DECISION_TABLE,
            "\"B\"",
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
            "\"B\"",
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
            "\"C\"",
            "inputB", "\"B\"",
            "outputC", "\"C\""));
  }

  @Test
  public void shouldMigrateChildDecisionInstanceWhenParentInstanceTriggeredFromStandaloneDmn() {
    // given
    Date now = ClockUtil.now();
    ClockUtil.setCurrentTime(now);
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");
    Map<String, Object> variables = Variables.createVariables().putValue("inputA", stringValue("A"));
    decisionService.evaluateDecisionTableByKey("simpleDmnWithReqs2Id", variables);

    // when
    historyMigrator.migrate();

    // then
    List<DecisionInstanceEntity> instances1 = searchHistoricDecisionInstances("simpleDmnWithReqs1Id");
    List<DecisionInstanceEntity> instances2 = searchHistoricDecisionInstances("simpleDmnWithReqs2Id");
    List<DecisionDefinitionEntity> migratedDecisions1 = searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id");
    List<DecisionDefinitionEntity> migratedDecisions2 = searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id");

    assertThat(migratedDecisions1).singleElement();
    assertThat(migratedDecisions2).singleElement();

    assertThat(instances1).singleElement().satisfies(instance ->
        assertDecisionInstance(
            instance,
            "simpleDmnWithReqs1Id",
            now,
            null,
            null,
            null,
            migratedDecisions1.getFirst().decisionDefinitionKey(),
            DecisionInstanceEntity.DecisionDefinitionType.DECISION_TABLE,
            "\"B\"",
            "inputA", "\"A\"",
            "outputB", "\"B\""));

    assertThat(instances2).singleElement().satisfies(instance ->
        assertDecisionInstance(
            instance,
            "simpleDmnWithReqs2Id",
            now,
            null,
            null,
            null,
            migratedDecisions2.getFirst().decisionDefinitionKey(),
            DecisionInstanceEntity.DecisionDefinitionType.DECISION_TABLE,
            "\"C\"",
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
  public void shouldMigrateDecisionResult_Unique() {
    // given / when
    List<DecisionInstanceEntity> migratedInstances = deployStartAndMigrateDmnForResultMigrationTestScenarios(
        "hitPolicyUniqueDecisionId", "hitPolicyUniqueDmn.dmn");

    // then
    assertThat(migratedInstances).singleElement()
        .extracting(DecisionInstanceEntity::result).isEqualTo("\"A\"");
  }

  @Test
  public void shouldMigrateDecisionResult_First() {
    // given / when
    List<DecisionInstanceEntity> migratedInstances = deployStartAndMigrateDmnForResultMigrationTestScenarios(
        "hitPolicyFirstDecisionId", "hitPolicyFirstDmn.dmn");

    // then
    assertThat(migratedInstances).singleElement()
        .extracting(DecisionInstanceEntity::result).isEqualTo("true");
  }

  @Test
  public void shouldMigrateDecisionResult_Any() {
    // given / when
    List<DecisionInstanceEntity> migratedInstances = deployStartAndMigrateDmnForResultMigrationTestScenarios(
        "hitPolicyAnyDecisionId", "hitPolicyAnyDmn.dmn");

    // then
    assertThat(migratedInstances).singleElement()
        .extracting(DecisionInstanceEntity::result).isEqualTo("true");
  }

  @Test
  public void shouldMigrateDecisionResult_Collect() {
    // given / when
    List<DecisionInstanceEntity> migratedInstances = deployStartAndMigrateDmnForResultMigrationTestScenarios(
        "hitPolicyCollectDecisionId", "hitPolicyCollectDmn.dmn");

    // then
    assertThat(migratedInstances).singleElement()
        .extracting(DecisionInstanceEntity::result).isEqualTo("[1,\"not B\",true]");
  }

  @Test
  public void shouldMigrateDecisionResult_CollectSum() {
    // given / when
    List<DecisionInstanceEntity> migratedInstances = deployStartAndMigrateDmnForResultMigrationTestScenarios(
        "hitPolicyCollectSumDecisionId", "hitPolicyCollectSumDmn.dmn");

    // then
    assertThat(migratedInstances).singleElement()
        .extracting(DecisionInstanceEntity::result).isEqualTo("10");
  }

  @Test
  public void shouldMigrateDecisionResult_CollectMin() {
    // given / when
    List<DecisionInstanceEntity> migratedInstances = deployStartAndMigrateDmnForResultMigrationTestScenarios(
        "hitPolicyCollectMinDecisionId", "hitPolicyCollectMinDmn.dmn");

    // then
    assertThat(migratedInstances).singleElement()
        .extracting(DecisionInstanceEntity::result).isEqualTo("1");
  }

  @Test
  public void shouldMigrateDecisionResult_CollectMax() {
    // given / when
    List<DecisionInstanceEntity> migratedInstances = deployStartAndMigrateDmnForResultMigrationTestScenarios(
        "hitPolicyCollectMaxDecisionId", "hitPolicyCollectMaxDmn.dmn");

    // then
    assertThat(migratedInstances).singleElement()
        .extracting(DecisionInstanceEntity::result).isEqualTo("2");
  }

  @Test
  public void shouldMigrateDecisionResult_CollectCount() {
    // given / when
    List<DecisionInstanceEntity> migratedInstances = deployStartAndMigrateDmnForResultMigrationTestScenarios(
        "hitPolicyCollectCountDecisionId", "hitPolicyCollectCountDmn.dmn");

    // then
    assertThat(migratedInstances).singleElement()
        .extracting(DecisionInstanceEntity::result).isEqualTo("2");
  }

  @Test
  public void shouldMigrateDecisionResult_RuleOrder() {
    // given / when
    List<DecisionInstanceEntity> migratedInstances = deployStartAndMigrateDmnForResultMigrationTestScenarios(
        "hitPolicyRuleOrderDecisionId", "hitPolicyRuleOrderDmn.dmn");

    // then
    assertThat(migratedInstances).singleElement()
        .extracting(DecisionInstanceEntity::result).isEqualTo("[\"firstRule\",\"secondRule\"]");
  }

  @Test
  public void shouldCalculateCleanupDateForStandaloneDecision() {
    // given - deploy and evaluate a standalone decision (not triggered by a process)
    deployer.deployCamunda7Decision("simpleDmn.dmn");

    // Evaluate the decision directly without a process instance
    decisionService.evaluateDecisionTableByKey("simpleDecisionId", java.util.Map.of("inputA", "A"));

    // when - migrate history
    historyMigrator.migrate();

    // then - verify standalone decision instance has proper cleanup date
    List<DecisionInstanceEntity> decisionInstances = searchHistoricDecisionInstances("simpleDecisionId");
    assertThat(decisionInstances).hasSize(1);

    DecisionInstanceEntity migratedDecision = decisionInstances.getFirst();
    assertThat(migratedDecision.state()).isEqualTo(DecisionInstanceEntity.DecisionInstanceState.EVALUATED);
    assertThat(migratedDecision.evaluationDate()).isNotNull();

    // Standalone decisions should not have process context
    assertThat(migratedDecision.processInstanceKey()).isNull();
    assertThat(migratedDecision.processDefinitionKey()).isNull();

    // Whitebox test: Query database directly to verify history cleanup date
    OffsetDateTime cleanupDate = cleanup.getDecisionInstanceCleanupDate(migratedDecision.decisionInstanceKey());

    // Verify cleanup date exists and is properly calculated (evaluationDate + 180 days from test property)
    assertThat(cleanupDate)
        .as("Cleanup date should be evaluation date + 180 days")
        .isEqualTo(migratedDecision.evaluationDate().plus(Duration.ofDays(180)));
  }

  protected List<DecisionInstanceEntity> deployStartAndMigrateDmnForResultMigrationTestScenarios(String decisionId,
                                                                                               String decisionFileName) {
    deployer.deployCamunda7Decision(decisionFileName);
    deployBusinessRuleProcessReferencingDecision(decisionId);
    Map<String, Object> variables = Variables.createVariables().putValue("input", stringValue("A"));
    runtimeService.startProcessInstanceByKey(String.format(BUSINESS_RULE_PROCESS_ID_PATTERN, decisionId), variables);

    // when
    historyMigrator.migrate();
    return searchHistoricDecisionInstances(decisionId);
  }

  private void deployBusinessRuleProcessReferencingDecision(String decisionId) {
    BpmnModelInstance c7BusinessRuleProcess = Bpmn.createExecutableProcess(
            String.format(BUSINESS_RULE_PROCESS_ID_PATTERN, decisionId))
        .startEvent("startEvent")
        .businessRuleTask("businessRuleTask")
        .camundaDecisionRef(decisionId)
        .endEvent("endEvent")
        .done();
    deployer.deployC7ModelInstance(String.format(BUSINESS_RULE_PROCESS_ID_PATTERN, decisionId), c7BusinessRuleProcess);
  }


  private void assertDecisionDefinition(
      DecisionDefinitionEntity decision,
      String decisionId,
      String decisionName,
      int version,
      Long decisionRequirementsKey,
      String decisionRequirementsId) {
    assertDecisionDefinition(decision, decisionId, decisionName, version, decisionRequirementsKey, decisionRequirementsId, 
        null, null);
  }

  private void assertDecisionDefinition(
      DecisionDefinitionEntity decision,
      String decisionId,
      String decisionName,
      int version,
      Long decisionRequirementsKey,
      String decisionRequirementsId,
      String decisionRequirementsName,
      Integer decisionRequirementsVersion) {
    assertThat(decision.decisionDefinitionId()).isEqualTo(prefixDefinitionId(decisionId));
    assertThat(decision.decisionDefinitionKey()).isNotNull();
    assertThat(decision.version()).isEqualTo(version);
    assertThat(decision.name()).isEqualTo(decisionName);
    assertThat(decision.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(decision.decisionRequirementsKey()).isEqualTo(decisionRequirementsKey);
    assertThat(decision.decisionRequirementsId()).isEqualTo(prefixDefinitionId(decisionRequirementsId));
    if (decisionRequirementsName != null) {
      assertThat(decision.decisionRequirementsName()).isEqualTo(decisionRequirementsName);
    }
    if (decisionRequirementsVersion != null) {
      assertThat(decision.decisionRequirementsVersion()).isEqualTo(decisionRequirementsVersion);
    }
  }

}
