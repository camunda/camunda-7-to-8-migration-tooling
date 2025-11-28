/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.qa.history.entity;

import static io.camunda.migrator.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.NOT_MIGRATING_DECISION_INSTANCE;
import static io.camunda.migrator.impl.logging.RuntimeMigratorLogs.SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR;
import static io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.BUSINESS_RULE_TASK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.migrator.HistoryMigrator;
import io.camunda.migrator.impl.clients.C7Client;
import io.camunda.migrator.qa.history.HistoryMigrationAbstractTest;
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
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryDecisionMigrationTest extends HistoryMigrationAbstractTest {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

  @Autowired
  private C7Client c7Client;

  @Test
  public void migrateSingleHistoricDecision() {
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
  public void migrateHistoricDecisionWithRequirements() {
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
      assertThat(decisionRequirements.resourceName()).isEqualTo("io/camunda/migrator/dmn/c7/simpleDmnWithReqs.dmn");
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
  public void migrateHistoricDecisionInstance() {
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
    HistoricDecisionInstance c7Instance = c7Client.getHistoricDecisionInstanceByDefinitionKey("simpleDecisionId");

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
  public void migrateHistoricDecisionInstanceWithNonDefaultTenant() {
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
  public void migrateChildDecisionInstanceWhenParentInstanceTriggeredFromBusinessTask() {
    // given
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");
    deployer.deployCamunda7Process("businessRuleForDmnWithReqs.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleForDmnWithReqsId",
        Variables.createVariables().putValue("inputA", stringValue("A")));

    // when
    historyMigrator.migrate();

    // then
    List<DecisionInstanceEntity> instances1 = searchHistoricDecisionInstances("simpleDmnWithReqs1Id");
    List<DecisionInstanceEntity> instances2 = searchHistoricDecisionInstances("simpleDmnWithReqs2Id");
    assertThat(instances1).singleElement();
    assertThat(instances2).singleElement();
  }

  @Test
  public void doNotMigrateDecisionInstanceWhenNotOriginatingFromProcessDefinition() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    Map<String, Object> variables = Variables.createVariables().putValue("inputA", stringValue("A"));
    decisionService.evaluateDecisionTableByKey("simpleDecisionId", variables);
    String decisionInstanceId = c7Client.getHistoricDecisionInstanceByDefinitionKey("simpleDecisionId").getId();

    // when
    historyMigrator.migrate();

    // then
    assertThat(searchHistoricDecisionInstances("simpleDecisionId")).isEmpty();
    logs.assertContains(NOT_MIGRATING_DECISION_INSTANCE.replace("{}", decisionInstanceId));
  }
}
