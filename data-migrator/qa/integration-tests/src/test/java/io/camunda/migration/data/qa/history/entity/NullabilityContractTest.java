/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history.entity;

import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_AUDIT_LOG;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_EXTERNAL_TASK;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_JOB;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.variable.Variables.createVariables;
import static org.camunda.bpm.engine.variable.Variables.stringValue;

import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.migration.data.qa.history.HistoryMigrationAbstractTest;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.JobQuery;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests documenting migration paths that violate the C8 search entity non-null contract.
 *
 * <p>related to #1339
 */
public class NullabilityContractTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected ExternalTaskService externalTaskService;

  @Autowired
  protected IdentityService identityService;

  @AfterEach
  public void cleanupIdentity() {
    identityService.clearAuthentication();
  }

  // ---------------------------------------------------------------------------
  // Group: JobEntity — worker, lastUpdateTime, elementInstanceKey
  //
  // Current tests read via jobMapper (returns JobDbModel) — bypasses the
  // JobEntity compact constructor that enforces requireNonNull.
  // ---------------------------------------------------------------------------

  @Test
//   @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339"
//       + " — ExternalTaskTransformer.java:65 explicitly sets .worker(null)")
  public void shouldNotProduceNullWorkerForExternalTask() {
    // given: an external task that is NOT locked (no worker assigned)
    var c7Model = Bpmn.createExecutableProcess("nullWorkerProcess")
        .startEvent()
        .serviceTask("externalTask")
          .camundaExternalTask("testTopic")
        .endEvent()
        .done();
    deployer.deployC7ModelInstance("nullWorkerProcess", c7Model);
    runtimeService.startProcessInstanceByKey("nullWorkerProcess");

    // when: migrate without locking the external task — worker remains null
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_EXTERNAL_TASK);

    // then: reading via the search entity API triggers requireNonNull("worker") in JobEntity
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances("nullWorkerProcess");
    assertThat(processInstances).hasSize(1);

    List<JobEntity> jobs = jobReader.search(
        JobQuery.of(b -> b.filter(f -> f.processInstanceKeys(
            processInstances.getFirst().processInstanceKey())))).items();

    assertThat(jobs).hasSize(1);
    // C8 contract: worker must not be null
    assertThat(jobs.getFirst().worker())
        .as("JobEntity.worker — C8 requires non-null (requireNonNull in compact constructor)")
        .isNotNull();
  }

  @Test
//   @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339"
//       + " — lastUpdateTime is never set by the migrator; builder default is null")
  public void shouldNotProduceNullLastUpdateTimeForJob() {
    // given: a completed async-before job
    deployer.deployCamunda7Process("asyncBeforeUserTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("asyncBeforeUserTaskProcessId");

    var jobs = managementService.createJobQuery().list();
    assertThat(jobs).hasSize(1);
    managementService.executeJob(jobs.getFirst().getId());

    // when
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_JOB);

    // then: reading via the search entity API triggers requireNonNull("lastUpdateTime")
    List<ProcessInstanceEntity> processInstances =
        searchHistoricProcessInstances("asyncBeforeUserTaskProcessId");
    assertThat(processInstances).hasSize(1);

    List<JobEntity> c8Jobs = jobReader.search(
        JobQuery.of(b -> b.filter(f -> f.processInstanceKeys(
            processInstances.getFirst().processInstanceKey())))).items();

    assertThat(c8Jobs).hasSize(1);
    // C8 contract: lastUpdateTime must not be null
    assertThat(c8Jobs.getFirst().lastUpdateTime())
        .as("JobEntity.lastUpdateTime — C8 requires non-null (requireNonNull in compact constructor)")
        .isNotNull();
  }

  @Test
//   @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339"
//       + " — async-before jobs have null elementInstanceKey because the flow node instance"
//       + " does not yet exist at job creation time (JobMigrator.java:123-127)")
  public void shouldNotProduceNullElementInstanceKeyForAsyncBeforeJob() {
    // given: an async-before job (flow nodes NOT migrated)
    deployer.deployCamunda7Process("asyncBeforeUserTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("asyncBeforeUserTaskProcessId");

    var jobs = managementService.createJobQuery().list();
    assertThat(jobs).hasSize(1);
    managementService.executeJob(jobs.getFirst().getId());

    // when: migrate jobs WITHOUT migrating flow nodes
    historyMigrator.migrateByType(HISTORY_PROCESS_DEFINITION);
    historyMigrator.migrateByType(HISTORY_PROCESS_INSTANCE);
    historyMigrator.migrateByType(HISTORY_JOB);

    // then: reading via the search entity API triggers requireNonNull("elementInstanceKey")
    List<ProcessInstanceEntity> processInstances =
        searchHistoricProcessInstances("asyncBeforeUserTaskProcessId");
    assertThat(processInstances).hasSize(1);

    List<JobEntity> c8Jobs = jobReader.search(
        JobQuery.of(b -> b.filter(f -> f.processInstanceKeys(
            processInstances.getFirst().processInstanceKey())))).items();

    assertThat(c8Jobs).hasSize(1);
    // C8 contract: elementInstanceKey must not be null
    assertThat(c8Jobs.getFirst().elementInstanceKey())
        .as("JobEntity.elementInstanceKey — C8 requires non-null (requireNonNull in compact constructor)")
        .isNotNull();
  }

  // ---------------------------------------------------------------------------
  // Group: AuditLogEntity — entityKey
  //
  // Current tests read via auditLogMapper (returns AuditLogDbModel) — bypasses
  // the AuditLogEntity compact constructor that enforces requireNonNull.
  // ---------------------------------------------------------------------------

  @Test
//   @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339"
//       + " — AuditLogMigrator.resolveJobKey sets jobKey but not entityKey for JOB entity type"
//       + " (AuditLogMigrator.java:224-235)")
  public void shouldNotProduceNullEntityKeyForJobAuditLog() {
    // given: a job audit log entry (Execute operation)
    deployer.deployCamunda7Process("asyncBeforeUserTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("asyncBeforeUserTaskProcessId");

    identityService.setAuthenticatedUserId("demo");
    var jobs = managementService.createJobQuery().list();
    assertThat(jobs).hasSize(1);
    managementService.executeJob(jobs.getFirst().getId());

    long auditLogCount = historyService.createUserOperationLogQuery()
        .operationType(UserOperationLogEntry.OPERATION_TYPE_EXECUTE)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when: full migration (jobs migrated before audit logs)
    historyMigrator.migrate();

    // then: verify entityKey is populated for JOB-type audit logs
    List<ProcessInstanceEntity> processInstances =
        searchHistoricProcessInstances("asyncBeforeUserTaskProcessId");
    assertThat(processInstances).hasSize(1);

    var logs = searchAuditLogs("asyncBeforeUserTaskProcessId");
    assertThat(logs).hasSize(1);
    // C8 contract: entityKey must not be null (requireNonNull in AuditLogEntity)
    assertThat(logs.getFirst().entityKey())
        .as("AuditLogEntity.entityKey — C8 requires non-null; JOB audit logs must populate this")
        .isNotNull();
  }

  @Test
//   @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339"
//       + " — AuditLogMigrator does not set entityKey for EXTERNAL_TASK entity type")
  public void shouldNotProduceNullEntityKeyForExternalTaskAuditLog() {
    // given: an external task audit log entry (SetPriority operation)
    var c7Model = Bpmn.createExecutableProcess("extTaskAuditProcess")
        .startEvent()
        .serviceTask("externalTask")
          .camundaExternalTask("testTopic")
        .endEvent()
        .done();
    deployer.deployC7ModelInstance("extTaskAuditProcess", c7Model);
    runtimeService.startProcessInstanceByKey("extTaskAuditProcess");

    identityService.setAuthenticatedUserId("demo");
    var externalTasks = externalTaskService.createExternalTaskQuery().list();
    assertThat(externalTasks).hasSize(1);
    externalTaskService.setPriority(externalTasks.getFirst().getId(), 10L);

    long auditLogCount = historyService.createUserOperationLogQuery()
        .operationType(UserOperationLogEntry.OPERATION_TYPE_SET_PRIORITY)
        .count();
    assertThat(auditLogCount).isEqualTo(1);

    // when: full migration
    historyMigrator.migrate();

    // then: verify entityKey is populated for EXTERNAL_TASK audit logs
    var logs = searchAuditLogs("extTaskAuditProcess");
    assertThat(logs).hasSize(1);
    // C8 contract: entityKey must not be null (requireNonNull in AuditLogEntity)
    assertThat(logs.getFirst().entityKey())
        .as("AuditLogEntity.entityKey — C8 requires non-null; EXTERNAL_TASK audit logs must populate this")
        .isNotNull();
  }

  // ---------------------------------------------------------------------------
  // Group: DecisionInstanceEntity — decisionDefinitionName, decisionDefinitionType, result
  //
  // Existing tests read via decisionInstanceReader (returns DecisionInstanceEntity)
  // but never trigger the null paths. These tests exercise the specific scenarios.
  // ---------------------------------------------------------------------------

  @Test
  @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339"
      + " — Not reproducible via C7 integration: DecisionInstanceTransformer never sets"
      + " decisionDefinitionName on the DbModel, but DecisionInstanceMapper.xml hydrates the"
      + " field at read time via a LEFT JOIN to DECISION_DEFINITION"
      + " (`dd.NAME AS DECISION_DEFINITION_NAME`). Since `historyMigrator.migrate()` always"
      + " migrates the corresponding DecisionDefinition (with a non-null name from C7), the"
      + " JOIN satisfies requireNonNull at search time. The defensive null in the DbModel is"
      + " dead from the search-API perspective."
      + " Fix should be migrator-side: set decisionDefinitionName on the DbModel from"
      + " HistoricDecisionInstance.getDecisionDefinitionName() to guarantee the contract at"
      + " write time too, independent of the LEFT JOIN.")
  public void shouldNotProduceNullDecisionDefinitionNameForDecisionInstance() {
    // given: a decision instance created via a business rule task
    deployer.deployCamunda7Decision("simpleDmn.dmn");
    deployer.deployCamunda7Process("businessRuleProcess.bpmn");
    Map<String, Object> variables = createVariables().putValue("inputA", stringValue("A"));
    runtimeService.startProcessInstanceByKey("businessRuleProcessId", variables);

    // when
    historyMigrator.migrate();

    // then: reading via the search entity API triggers requireNonNull("decisionDefinitionName")
    List<DecisionInstanceEntity> instances = searchHistoricDecisionInstances("simpleDecisionId");
    assertThat(instances).hasSize(1);
    // C8 contract: decisionDefinitionName must not be null
    assertThat(instances.getFirst().decisionDefinitionName())
        .as("DecisionInstanceEntity.decisionDefinitionName — C8 requires non-null"
            + " (requireNonNull in compact constructor); never set in DecisionInstanceTransformer")
        .isNotNull()
        .isNotEmpty();
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339"
      + " — Not reproducible via C7 integration: constructResultJsonFromOutputs"
      + " (DecisionInstanceTransformer.java:173-176) returns null only when the mapped output"
      + " list is empty. Empirically C7's HistoricDecisionInstance.getOutputs() is never an"
      + " empty Java list even for COLLECT with zero matched rules — C7 records at least one"
      + " synthetic output row. mapOutputs then yields size ≥ 1 and the JSON serialization"
      + " produces \"null\" (a non-null string), satisfying requireNonNull(\"result\")."
      + " The defensive null branch is unreachable through normal DMN evaluations."
      + " Fix should be migrator-side: return \"null\" or \"[]\" (a safe JSON literal)"
      + " explicitly in the empty branch rather than Java null, to guarantee the contract.")
  public void shouldNotProduceNullResultForDecisionInstanceWithEmptyOutputs() {
    // given: a decision evaluation that produces zero matching rules with COLLECT hit policy,
    // and a business rule task using mapDecisionResult="collectEntries" so the process tolerates
    // an empty result list (singleEntry would throw and abort the process before the
    // HistoricDecisionInstance is finalized).
    deployer.deployCamunda7Decision("noMatchCollectDmn.dmn");
    deployer.deployCamunda7Process("noMatchCollectBusinessRuleProcess.bpmn");
    Map<String, Object> variables = createVariables().putValue("inputA", stringValue("DoesNotMatch"));
    runtimeService.startProcessInstanceByKey("noMatchCollectBusinessRuleProcessId", variables);

    // when
    historyMigrator.migrate();

    // then: reading via the search entity API triggers requireNonNull("result")
    List<DecisionInstanceEntity> instances = searchHistoricDecisionInstances("noMatchCollectDecisionId");
    assertThat(instances)
        .as("a HistoricDecisionInstance with empty outputs must be migrated")
        .hasSize(1);
    // C8 contract: result must not be null (requireNonNull in compact constructor);
    // null is produced by constructResultJsonFromOutputs for empty output lists.
    assertThat(instances.getFirst().result())
        .as("DecisionInstanceEntity.result — C8 requires non-null"
            + " (requireNonNull in compact constructor); null when outputs are empty")
        .isNotNull();
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339"
      + " — Not reproducible via C7 integration: DecisionInstanceMigrator.determineDecisionType"
      + " (DecisionInstanceMigrator.java:242-248) returns null only when"
      + " dmnModelInstance.getModelElementById(decisionDefinitionKey) returns null."
      + " In any valid C7 deployment this is unreachable: getDmnModelInstance(id) returns the"
      + " XML resource that the decision was deployed from, which by construction contains the"
      + " <decision> element whose XML id equals decisionDefinitionKey. The defensive null"
      + " branch can only be hit by post-deployment XML corruption, which C7 cannot produce."
      + " Fix should be migrator-side: replace `return null;` with"
      + " DecisionDefinitionType.UNSPECIFIED (or UNKNOWN) to guarantee the C8 contract.")
  public void shouldNotProduceNullDecisionDefinitionTypeForUnresolvableDecision() {
    // Scenario as authored — kept as living documentation of the contract.
    // simpleDmnWithReqs.dmn defines both parent (simpleDmnWithReqs2Id) and child
    // (simpleDmnWithReqs1Id) in the SAME XML file. Each c7Client.getDmnModelInstance(...)
    // call returns that file, so getModelElementById finds both keys and the null branch
    // never executes. Splitting the decisions into separate DMN files would not change this:
    // c7Client.getDmnModelInstance(childId) still returns the child's own XML resource,
    // which by definition contains the child's <decision> element.
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");
    deployer.deployCamunda7Process("businessRuleForDmnWithReqs.bpmn");
    runtimeService.startProcessInstanceByKey("businessRuleForDmnWithReqsId",
        createVariables().putValue("inputA", stringValue("A")));

    // when
    historyMigrator.migrate();

    // then: all decision instances should have a non-null decisionDefinitionType
    List<DecisionInstanceEntity> parentInstances =
        searchHistoricDecisionInstances("simpleDmnWithReqs2Id");
    List<DecisionInstanceEntity> childInstances =
        searchHistoricDecisionInstances("simpleDmnWithReqs1Id");

    assertThat(parentInstances).hasSize(1);
    assertThat(childInstances).hasSize(1);

    // C8 contract: decisionDefinitionType must not be null
    assertThat(parentInstances.getFirst().decisionDefinitionType())
        .as("DecisionInstanceEntity.decisionDefinitionType for parent — C8 requires non-null")
        .isNotNull();
    assertThat(childInstances.getFirst().decisionDefinitionType())
        .as("DecisionInstanceEntity.decisionDefinitionType for child — C8 requires non-null;"
            + " null when DMN resolution fails in determineDecisionType")
        .isNotNull();
  }

  // ---------------------------------------------------------------------------
  // Group: DecisionDefinitionEntity — decisionRequirementsId
  //
  // Existing tests read via decisionDefinitionReader (returns
  // DecisionDefinitionEntity) and assert isNullOrEmpty() — this will NPE
  // once C8 enforces the non-null contract.
  // ---------------------------------------------------------------------------

  @Test
  //   @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339"
  //       + " — DecisionDefinitionTransformer.java:35 passes through nullable C7"
  //       + " decisionRequirementsDefinitionKey via prefixDefinitionId(null) → null")
  public void shouldNotProduceNullDecisionRequirementsIdForStandaloneDecision() {
    // given: a simple DMN without explicit decision requirements definition
    deployer.deployCamunda7Decision("simpleDmn.dmn");

    // when
    historyMigrator.migrate();

    // then: reading via the search entity API triggers requireNonNull("decisionRequirementsId")
    List<DecisionDefinitionEntity> migratedDecisions =
        searchHistoricDecisionDefinitions("simpleDecisionId");
    assertThat(migratedDecisions).hasSize(1);

    // C8 contract: decisionRequirementsId must not be null
    assertThat(migratedDecisions.getFirst().decisionRequirementsId())
        .as("DecisionDefinitionEntity.decisionRequirementsId — C8 requires non-null"
            + " (requireNonNull in compact constructor); null for standalone decisions")
        .isNotNull()
        .isNotEmpty();
  }

  // ---------------------------------------------------------------------------
  // Group: IncidentEntity — flowNodeInstanceKey, errorMessage
  //
  // Current tests read via incident DbModel (IncidentDbModel) — bypasses
  // the IncidentEntity compact constructor that enforces requireNonNull.
  // ---------------------------------------------------------------------------

  @Test
  //   @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339"
  //       + " — IncidentMigrator.java:129-140 deliberately keeps flowNodeInstanceKey null"
  //       + " when the incident's activity has a waiting async-before execution")
  public void shouldNotProduceNullFlowNodeInstanceKeyForAsyncBeforeIncident() {
    // given: an async-before failing service task that produces an incident before any
    // flow node instance is created for the activity (incidentProcess.bpmn's service task
    // references the missing class io.camunda.migration.data.qa.util.Foo with retries=0).
    deployer.deployCamunda7Process("incidentProcess.bpmn");
    var c7ProcessInstance = runtimeService.startProcessInstanceByKey("incidentProcessId");
    triggerIncident(c7ProcessInstance.getId());

    // when
    historyMigrator.migrate();

    // then: reading via the search entity API triggers requireNonNull("flowNodeInstanceKey")
    List<IncidentEntity> incidents = incidentReader.search(
        IncidentQuery.of(b -> b.filter(f -> f.processDefinitionIds(
            prefixDefinitionId("incidentProcessId"))))).items();

    assertThat(incidents).hasSize(1);
    // C8 contract: flowNodeInstanceKey must not be null
    assertThat(incidents.getFirst().flowNodeInstanceKey())
        .as("IncidentEntity.flowNodeInstanceKey — C8 requires non-null (requireNonNull"
            + " in compact constructor); IncidentMigrator deliberately writes null when"
            + " the activity has a waiting async-before execution (line 129-140)")
        .isNotNull();
  }

  @Test
  @Disabled("https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339"
      + " — Migrator writes null to IncidentDbModel.errorMessage when C7"
      + " HistoricIncident.getIncidentMessage() is null (here: runtimeService.createIncident"
      + " is called with a null message). However, the C8 read side masks the null to an"
      + " empty string via NullToEmptyStringTypeHandler on the ERROR_MESSAGE column, so"
      + " requireNonNull(\"errorMessage\") is silently satisfied. This test is kept as"
      + " documentation; the masking question (is read-time coercion a supported contract,"
      + " or should the migrator populate the field explicitly?) is the same as"
      + " NULLABILITY-decisionDefinitionName.md.")
  public void shouldNotProduceNullErrorMessageForIncident() {
    // given: a user task instance with a custom incident created via the runtime service
    // with a null message. The user-task path is used (rather than an async-before failing
    // delegate) so flowNodeInstanceKey IS populated — otherwise requireNonNull on
    // flowNodeInstanceKey would fire first and mask the masking-behaviour we want to assert.
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var c7ProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    var task = taskService.createTaskQuery().taskDefinitionKey("userTaskId").singleResult();
    runtimeService.createIncident("foo", task.getExecutionId(), null);

    // when
    historyMigrator.migrate();

    // then: reading via the search entity API would trigger requireNonNull("errorMessage")
    // — except NullToEmptyStringTypeHandler converts the null to "" before the entity
    // constructor sees it, so this assertion passes (errorMessage() == "" is non-null).
    List<IncidentEntity> incidents = incidentReader.search(
        IncidentQuery.of(b -> b.filter(f -> f.processDefinitionIds(
            prefixDefinitionId("userTaskProcessId"))))).items();

    assertThat(incidents).hasSize(1);
    // C8 contract: errorMessage must not be null
    assertThat(incidents.getFirst().errorMessage())
        .as("IncidentEntity.errorMessage — C8 requires non-null (requireNonNull in compact"
            + " constructor); migrator writes null, masked to \"\" by NullToEmptyStringTypeHandler")
        .isNotNull()
        .isNotEmpty();
  }
}
