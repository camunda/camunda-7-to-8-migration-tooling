/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.DEFAULT_LEGACY_ID_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies that a custom {@code camunda.migrator.history.legacy-id-prefix} is applied consistently
 * to every migrated history definition type (process, decision and form definitions), and that the
 * default prefix is no longer used when a custom one is configured.
 */
@TestPropertySource(properties = "camunda.migrator.history.legacy-id-prefix=acme-legacy-")
public class CustomLegacyIdPrefixHistoryTest extends HistoryMigrationAbstractTest {

  protected static final String CUSTOM_PREFIX = "acme-legacy-";

  @Test
  public void shouldApplyConfiguredPrefixToAllDefinitionTypes() {
    // given process, decision and form definitions deployed to Camunda 7
    deployer.deployCamunda7Process("userTaskProcess.bpmn", null);
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    repositoryService.createDeployment()
        .addClasspathResource("io/camunda/migration/data/bpmn/c7/processWithForm.bpmn")
        .addClasspathResource("io/camunda/migration/data/form/simple-form.form")
        .deploy();

    // when history is migrated
    historyMigrator.migrate();

    // then the process definition id uses the configured prefix
    List<ProcessDefinitionEntity> processDefinitions = processDefinitionReader
        .search(ProcessDefinitionQuery.of(q -> q.filter(f ->
            f.processDefinitionIds(CUSTOM_PREFIX + "userTaskProcessId")))).items();
    assertThat(processDefinitions).singleElement().satisfies(definition ->
        assertThat(definition.processDefinitionId()).isEqualTo(CUSTOM_PREFIX + "userTaskProcessId"));

    // and the default prefix is not used for the process definition
    assertThat(processDefinitionReader
        .search(ProcessDefinitionQuery.of(q -> q.filter(f ->
            f.processDefinitionIds(DEFAULT_LEGACY_ID_PREFIX + "userTaskProcessId")))).items())
        .isEmpty();

    // and the decision definition and its requirements id use the configured prefix
    List<DecisionDefinitionEntity> decisions = decisionDefinitionReader
        .search(DecisionDefinitionQuery.of(q -> q.filter(f ->
            f.decisionDefinitionIds(CUSTOM_PREFIX + "simpleDecisionId")))).items();
    assertThat(decisions).singleElement().satisfies(decision -> {
      assertThat(decision.decisionDefinitionId()).isEqualTo(CUSTOM_PREFIX + "simpleDecisionId");
      assertThat(decision.decisionRequirementsId()).isEqualTo(CUSTOM_PREFIX + "simpleDmnId");
    });

    List<DecisionRequirementsEntity> decisionRequirements = decisionRequirementsReader
        .search(DecisionRequirementsQuery.of(q -> q.filter(f ->
            f.decisionRequirementsIds(CUSTOM_PREFIX + "simpleDmnId")))).items();
    assertThat(decisionRequirements).singleElement().satisfies(drd ->
        assertThat(drd.decisionRequirementsId()).isEqualTo(CUSTOM_PREFIX + "simpleDmnId"));

    // and the form id uses the configured prefix
    List<FormEntity> forms = formReader
        .search(FormQuery.of(q -> q.filter(f ->
            f.formIds(List.of(CUSTOM_PREFIX + "simple-form"))))).items();
    assertThat(forms).singleElement().satisfies(form ->
        assertThat(form.formId()).isEqualTo(CUSTOM_PREFIX + "simple-form"));
  }
}
