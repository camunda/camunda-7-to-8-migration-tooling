/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.constants.MigratorConstants.DEFAULT_LEGACY_ID_PREFIX;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_JOB;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies that a custom {@code camunda.migrator.history.legacy-id-prefix} is applied consistently
 * to <b>every</b> migrated history entity whose definition IDs are prefixed, and that the default
 * prefix is never used when a custom one is configured.
 * <p>
 * The default-prefix behaviour of each entity type is already regression-covered by the sibling
 * {@code History*Test} classes (their {@code searchHistoric*} helpers filter by
 * {@code ConverterUtil.prefixDefinitionId}, i.e. the default {@code c7-legacy-} prefix). This test
 * is the counterpart that proves each transformer/migrator resolves the prefix through
 * {@code LegacyIdPrefixResolver} rather than hard-coding {@code c7-legacy-} — a hard-coded prefix
 * would pass every default-prefix test but fail here.
 * <p>
 * Coverage per prefixed field:
 * <ul>
 *   <li>process definition id — process definition, process instance, flow node, user task,
 *       variable, incident, job, decision instance, audit log;</li>
 *   <li>decision definition id and decision requirements id — decision definition, decision
 *       instance, decision requirements definition;</li>
 *   <li>form id — form definition.</li>
 * </ul>
 * Not covered here (tracked separately): the DMN model's internal element/href rewriting in
 * {@code DecisionRequirementsDefinitionTransformer}, the process-definition start-form id in
 * {@code ProcessDefinitionMigrator}, and identity authorization resource ids in
 * {@code AuthorizationManager} (a different, non-history migration path).
 */
@TestPropertySource(properties = "camunda.migrator.history.legacy-id-prefix=acme-legacy-")
public class CustomLegacyIdPrefixHistoryTest extends HistoryMigrationAbstractTest {

  protected static final String CUSTOM_PREFIX = "acme-legacy-";

  @Autowired
  protected IdentityService identityService;

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

  @Test
  public void shouldApplyConfiguredPrefixToProcessInstanceGraph() {
    // given a completed process instance with a variable, so process instance, flow node, user
    // task and variable history are all produced
    deployer.deployCamunda7Process("userTaskProcess.bpmn", null);
    runtimeService.startProcessInstanceByKey("userTaskProcessId", Map.of("myVar", "myValue"));
    completeAllUserTasksWithDefaultUserTaskId();

    // when
    historyMigrator.migrate();

    // then the process instance carries the configured prefix (and not the default one)
    List<ProcessInstanceEntity> processInstances = processInstanceReader
        .search(ProcessInstanceQuery.of(q -> q.filter(f ->
            f.processDefinitionIds(CUSTOM_PREFIX + "userTaskProcessId")))).items();
    assertThat(processInstances).singleElement().satisfies(pi ->
        assertThat(pi.processDefinitionId()).isEqualTo(CUSTOM_PREFIX + "userTaskProcessId"));
    long processInstanceKey = processInstances.getFirst().processInstanceKey();

    assertThat(processInstanceReader
        .search(ProcessInstanceQuery.of(q -> q.filter(f ->
            f.processDefinitionIds(DEFAULT_LEGACY_ID_PREFIX + "userTaskProcessId")))).items())
        .as("default prefix must not be used").isEmpty();

    // and every flow node instance carries the configured prefix
    List<FlowNodeInstanceEntity> flowNodes = searchHistoricFlowNodes(processInstanceKey);
    assertThat(flowNodes).isNotEmpty().allSatisfy(flowNode ->
        assertThat(flowNode.processDefinitionId()).isEqualTo(CUSTOM_PREFIX + "userTaskProcessId"));

    // and every user task carries the configured prefix
    List<UserTaskEntity> userTasks = searchHistoricUserTasks(processInstanceKey);
    assertThat(userTasks).isNotEmpty().allSatisfy(userTask ->
        assertThat(userTask.processDefinitionId()).isEqualTo(CUSTOM_PREFIX + "userTaskProcessId"));

    // and the migrated variable carries the configured prefix
    List<VariableEntity> variables = searchHistoricVariables("myVar");
    assertThat(variables).singleElement().satisfies(variable ->
        assertThat(variable.processDefinitionId()).isEqualTo(CUSTOM_PREFIX + "userTaskProcessId"));
  }

  @Test
  public void shouldApplyConfiguredPrefixToIncident() {
    // given a process instance with an incident on its user task
    deployer.deployCamunda7Process("userTaskProcess.bpmn", null);
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    Task task = taskService.createTaskQuery().taskDefinitionKey("userTaskId").singleResult();
    runtimeService.createIncident("foo", task.getExecutionId(), "bar");

    // when
    historyMigrator.migrate();

    // then the migrated incident carries the configured prefix
    List<IncidentEntity> incidents = incidentReader
        .search(IncidentQuery.of(q -> q.filter(f ->
            f.processDefinitionIds(CUSTOM_PREFIX + "userTaskProcessId")))).items();
    assertThat(incidents).singleElement().satisfies(incident ->
        assertThat(incident.processDefinitionId()).isEqualTo(CUSTOM_PREFIX + "userTaskProcessId"));
  }

  @Test
  public void shouldApplyConfiguredPrefixToJob() {
    // given an async-before job that has been executed (produces a historic job log)
    deployer.deployCamunda7Process("asyncBeforeUserTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("asyncBeforeUserTaskProcessId");
    String c7JobId = managementService.createJobQuery().singleResult().getId();
    managementService.executeJob(c7JobId);

    // when (flow nodes must be migrated before jobs since C8 requires a non-null elementInstanceKey)
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_FLOW_NODE);
    historyMigrator.migrateByType(HISTORY_JOB);

    // then the migrated job carries the configured prefix
    List<ProcessInstanceEntity> processInstances = processInstanceReader
        .search(ProcessInstanceQuery.of(q -> q.filter(f ->
            f.processDefinitionIds(CUSTOM_PREFIX + "asyncBeforeUserTaskProcessId")))).items();
    assertThat(processInstances).hasSize(1);

    List<JobEntity> jobs = searchJobs(processInstances.getFirst().processInstanceKey());
    assertThat(jobs).singleElement().satisfies(job ->
        assertThat(job.processDefinitionId()).isEqualTo(CUSTOM_PREFIX + "asyncBeforeUserTaskProcessId"));
  }

  @Test
  public void shouldApplyConfiguredPrefixToDecisionInstance() {
    // given a process that evaluates a decision, producing a historic decision instance
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleProcessId", Map.of("inputA", "A"));

    // when
    historyMigrator.migrate();

    // then the decision instance's decision definition id carries the configured prefix — the
    // reader only matches when the stored id was prefixed by DecisionInstanceTransformer
    // (DecisionInstanceEntity does not expose processDefinitionId/decisionRequirementsId accessors,
    // so those prefixed fields are asserted on the definition entities above / in the sibling test)
    List<DecisionInstanceEntity> customPrefixed = decisionInstanceReader
        .search(DecisionInstanceQuery.of(q -> q.filter(f ->
            f.decisionDefinitionIds(List.of(CUSTOM_PREFIX + "simpleDecisionId"))))).items();
    assertThat(customPrefixed).as("decision instance uses the configured prefix").singleElement();

    // and the default prefix is not used
    assertThat(decisionInstanceReader
        .search(DecisionInstanceQuery.of(q -> q.filter(f ->
            f.decisionDefinitionIds(List.of(DEFAULT_LEGACY_ID_PREFIX + "simpleDecisionId"))))).items())
        .as("default prefix must not be used").isEmpty();
  }

  @Test
  public void shouldApplyConfiguredPrefixToAuditLog() {
    // given a process instance started by an authenticated user (produces a user operation log)
    deployer.deployCamunda7Process("simpleProcess.bpmn");
    identityService.setAuthenticatedUserId("demo");
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    if (task != null) {
      taskService.complete(task.getId());
    }

    // when
    try {
      historyMigrator.migrate();
    } finally {
      identityService.clearAuthentication();
    }

    // then every migrated audit log entry carries the configured prefix
    List<AuditLogEntity> auditLogs = auditLogReader
        .search(AuditLogQuery.of(q -> q.filter(f ->
            f.processDefinitionIds(CUSTOM_PREFIX + "simpleProcess")))).items();
    assertThat(auditLogs).isNotEmpty().allSatisfy(log ->
        assertThat(log.processDefinitionId()).isEqualTo(CUSTOM_PREFIX + "simpleProcess"));
  }
}
