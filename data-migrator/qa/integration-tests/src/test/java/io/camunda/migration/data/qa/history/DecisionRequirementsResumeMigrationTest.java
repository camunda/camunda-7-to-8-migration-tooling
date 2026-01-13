/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.DecisionRequirementsEntity;
import java.util.Date;
import java.util.List;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.jupiter.api.Test;

/**
 * Tests for decision requirements migration with deployment time ordering and resume capability.
 */
public class DecisionRequirementsResumeMigrationTest extends HistoryMigrationAbstractTest {

  @Test
  public void shouldResumeDecisionRequirementsMigrationAfterPartialMigration() throws InterruptedException {
    // given: Deploy first decision
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    
    // Wait a bit to ensure different deployment times
    Thread.sleep(100);
    
    // Migrate first decision requirements
    historyMigrator.migrateDecisionRequirementsDefinitions();
    
    // Verify first decision requirements was migrated
    List<DecisionRequirementsEntity> firstBatch = searchHistoricDecisionRequirementsDefinition("simpleDmnId");
    assertThat(firstBatch).hasSize(1);
    
    // Get the latest create time (deployment time) to verify resume capability
    Date latestCreateTime = dbClient.findLatestCreateTimeByType(IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT);
    assertThat(latestCreateTime).isNotNull();
    
    // when: Deploy second decision after migration
    Thread.sleep(100);
    deployer.deployCamunda7Decision("literalExpressionDmn.dmn");
    
    // Resume migration (should only migrate the new decision requirements)
    historyMigrator.migrateDecisionRequirementsDefinitions();
    
    // then: Both decision requirements should be migrated
    List<DecisionRequirementsEntity> secondBatch = searchHistoricDecisionRequirementsDefinition("literalExpressionDmnId");
    assertThat(secondBatch).hasSize(1);
    
    // Verify first decision requirements still exists and wasn't re-migrated
    List<DecisionRequirementsEntity> allFirstBatch = searchHistoricDecisionRequirementsDefinition("simpleDmnId");
    assertThat(allFirstBatch).hasSize(1);
  }

  @Test
  public void shouldMigrateDecisionRequirementsInDeploymentTimeOrder() throws InterruptedException {
    // given: Deploy multiple decisions at different times
    Date beforeFirstDeploy = ClockUtil.now();
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    
    Thread.sleep(100);
    
    deployer.deployCamunda7Decision("literalExpressionDmn.dmn");
    
    Thread.sleep(100);
    
    deployer.deployCamunda7Decision("dish-decision.dmn");
    
    // when: Migrate all decision requirements
    historyMigrator.migrateDecisionRequirementsDefinitions();
    
    // then: All decision requirements should be migrated
    List<DecisionRequirementsEntity> simpleDmn = searchHistoricDecisionRequirementsDefinition("simpleDmnId");
    List<DecisionRequirementsEntity> literalExpression = searchHistoricDecisionRequirementsDefinition("literalExpressionDmnId");
    List<DecisionRequirementsEntity> dishDecision = searchHistoricDecisionRequirementsDefinition("dish-decision");
    
    assertThat(simpleDmn).hasSize(1);
    assertThat(literalExpression).hasSize(1);
    assertThat(dishDecision).hasSize(1);
    
    // Verify the latest create time is set correctly (should be the deployment time of the last deployment)
    Date latestCreateTime = dbClient.findLatestCreateTimeByType(IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT);
    assertThat(latestCreateTime).isNotNull();
    assertThat(latestCreateTime).isAfter(beforeFirstDeploy);
  }

  @Test
  public void shouldHandleMultipleVersionsOfSameDecisionRequirements() throws InterruptedException {
    // given: Deploy same decision twice
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    
    Thread.sleep(100);
    
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    
    // when: Migrate decision requirements
    historyMigrator.migrateDecisionRequirementsDefinitions();
    
    // then: Both versions should be migrated
    List<DecisionRequirementsEntity> decisionReqs = searchHistoricDecisionRequirementsDefinition("simpleDmnId");
    assertThat(decisionReqs).hasSize(2);
    
    // Verify they have different versions
    assertThat(decisionReqs)
        .extracting(DecisionRequirementsEntity::version)
        .containsExactlyInAnyOrder(1, 2);
  }
}
