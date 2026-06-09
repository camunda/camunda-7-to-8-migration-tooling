/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.history;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.impl.util.ConverterUtil.convertDate;
import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static io.camunda.migration.data.qa.extension.HistoryMigrationExtension.USER_TASK_ID;
import static io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState.EVALUATED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.service.RdbmsPurger;
import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.config.C8DataSourceConfigured;
import io.camunda.migration.data.config.MigratorAutoConfiguration;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.util.ConverterUtil;
import io.camunda.migration.data.qa.AbstractMigratorTest;
import io.camunda.migration.data.qa.extension.RdbmsQueryExtension;
import io.camunda.migration.data.qa.util.WithSpringProfile;
import io.camunda.process.test.api.CamundaSpringProcessTest;
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
import io.camunda.search.query.FlowNodeInstanceQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Import({
    HistoryMigrationAbstractTest.HistoryCustomConfiguration.class,
    MigratorAutoConfiguration.class
})
@CamundaSpringProcessTest
@WithSpringProfile("history-level-full")
public abstract class HistoryMigrationAbstractTest extends AbstractMigratorTest {

  // Migrator ---------------------------------------

  @Autowired
  protected HistoryMigrator historyMigrator;

  // C8 ---------------------------------------

  @Autowired
  protected RdbmsPurger rdbmsPurger;

  @Autowired
  protected CamundaClient camundaClient;

  @Autowired
  protected FlowNodeInstanceMapper flowNodeInstanceMapper;

  @RegisterExtension
  @Autowired
  protected RdbmsQueryExtension rdbmsQuery = new RdbmsQueryExtension();

  // C7 ---------------------------------------

  @Autowired
  protected HistoryService historyService;

  @Autowired
  protected DbClient dbClient;

  @AfterEach
  public void cleanup() {
    // C7
    ClockUtil.reset();
    repositoryService.createDeploymentQuery().list().forEach(d -> repositoryService.deleteDeployment(d.getId(), true));

    // Migrator
    dbClient.deleteAllMappings();

    // C8
    rdbmsPurger.purgeRdbms();
  }

  public ProcessDefinitionEntity searchHistoricProcessDefinition(String processDefinitionId) {
    return searchHistoricProcessDefinitions(processDefinitionId).getFirst();
  }

  public List<ProcessDefinitionEntity> searchHistoricProcessDefinitions(String processDefinitionId) {
    return camundaClient.newProcessDefinitionSearchRequest()
        .filter(f -> f.processDefinitionId(prefixDefinitionId(processDefinitionId)))
        .execute()
        .items()
        .stream()
        .map(processDefinition -> toProcessDefinitionEntity(processDefinition, null))
        .toList();
  }

  public List<ProcessDefinitionEntity> searchHistoricProcessDefinitionsWithBpmnXml(String processDefinitionId) {
    return camundaClient.newProcessDefinitionSearchRequest()
        .filter(f -> f.processDefinitionId(prefixDefinitionId(processDefinitionId)))
        .execute()
        .items()
        .stream()
        .map(processDefinition ->
            toProcessDefinitionEntity(
                processDefinition,
                camundaClient.newProcessDefinitionGetXmlRequest(processDefinition.getProcessDefinitionKey())
                    .execute()))
        .toList();
  }

  public List<DecisionDefinitionEntity> searchHistoricDecisionDefinitions(String decisionDefinitionId) {
    return camundaClient.newDecisionDefinitionSearchRequest()
        .filter(f -> f.decisionDefinitionId(prefixDefinitionId(decisionDefinitionId)))
        .execute()
        .items()
        .stream()
        .map(this::toDecisionDefinitionEntity)
        .toList();
  }

  public List<DecisionRequirementsEntity> searchHistoricDecisionRequirementsDefinition(String decisionRequirementsId) {
    return camundaClient.newDecisionRequirementsSearchRequest()
        .filter(f -> f.decisionRequirementsId(prefixDefinitionId(decisionRequirementsId)))
        .execute()
        .items()
        .stream()
        .map(requirements ->
            toDecisionRequirementsEntity(
                requirements,
                camundaClient.newDecisionRequirementsGetXmlRequest(requirements.getDecisionRequirementsKey())
                    .execute()))
        .toList();
  }

  public List<ProcessInstanceEntity> searchHistoricProcessInstances(String processDefinitionId) {
    return searchHistoricProcessInstances(processDefinitionId, false);
  }

  public List<AuditLogEntity> searchAuditLogs(String processDefinitionId) {
    return camundaClient.newAuditLogSearchRequest()
        .filter(f -> f.processDefinitionId(prefixDefinitionId(processDefinitionId)))
        .execute()
        .items()
        .stream()
        .map(this::toAuditLogEntity)
        .toList();
  }

  public List<AuditLogEntity> searchAuditLogsByCategory(String name) {
    return camundaClient.newAuditLogSearchRequest()
        .filter(f -> f.category(AuditLogCategoryEnum.valueOf(name)))
        .execute()
        .items()
        .stream()
        .map(this::toAuditLogEntity)
        .toList();
  }

  /**
   * When the built-in ProcessInstanceTransformer is disabled, the processDefinitionId
   * is NOT prefixed during migration. This method allows searching with or without prefixing.
   */
  public List<ProcessInstanceEntity> searchHistoricProcessInstances(String processDefinitionId,
                                                                    boolean builtInTransformerDisabled) {
    String finalProcessDefinitionId = builtInTransformerDisabled
        ?
        processDefinitionId :
        prefixDefinitionId(processDefinitionId);
    return camundaClient.newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(finalProcessDefinitionId))
        .execute()
        .items()
        .stream()
        .map(this::toProcessInstanceEntity)
        .toList();
  }

  public List<DecisionInstanceEntity> searchHistoricDecisionInstances(String... decisionDefinitionIds) {
    return Arrays.stream(decisionDefinitionIds)
        .map(ConverterUtil::prefixDefinitionId)
        .flatMap(this::searchHistoricDecisionInstancesByDefinitionId)
        .toList();
  }

  public List<UserTaskEntity> searchHistoricUserTasks(long processInstanceKey) {
    return camundaClient.newUserTaskSearchRequest()
        .filter(f -> f.processInstanceKey(processInstanceKey))
        .execute()
        .items()
        .stream()
        .map(this::toUserTaskEntity)
        .toList();
  }

  public List<UserTaskEntity> searchHistoricUserTasks() {
    return camundaClient.newUserTaskSearchRequest()
        .execute()
        .items()
        .stream()
        .map(this::toUserTaskEntity)
        .toList();
  }

  public List<FlowNodeInstanceEntity> searchHistoricFlowNodesForType(long processInstanceKey, FlowNodeInstanceEntity.FlowNodeType type) {
    var mappedType = mapEnum(io.camunda.client.api.search.enums.ElementInstanceType.class, type.name());
    if (mappedType == null) {
      throw new IllegalArgumentException("Unsupported flow node type mapping for " + type);
    }
    return camundaClient.newElementInstanceSearchRequest()
        .filter(f -> {
          f.processInstanceKey(processInstanceKey);
          f.type(mappedType);
        })
        .execute()
        .items()
        .stream()
        .map(this::toFlowNodeInstanceEntity)
        .toList();
  }

  public List<FlowNodeInstanceEntity> searchHistoricFlowNodes(long processInstanceKey) {
    return camundaClient.newElementInstanceSearchRequest()
        .filter(f -> f.processInstanceKey(processInstanceKey))
        .execute()
        .items()
        .stream()
        .map(this::toFlowNodeInstanceEntity)
        .toList();
  }

  public List<FlowNodeInstanceEntity> searchHistoricFlowNodesByTenant(String tenantId) {
    return camundaClient.newElementInstanceSearchRequest()
        .filter(f -> f.tenantIds(List.of(tenantId)))
        .execute()
        .items()
        .stream()
        .map(this::toFlowNodeInstanceEntity)
        .toList();
  }

  public List<FlowNodeInstanceDbModel> searchFlowNodeInstancesByName(String flowNodeName) {
    return flowNodeInstanceMapper.search(
        FlowNodeInstanceDbQuery.of(b -> b.filter(f -> f.flowNodeNames(flowNodeName))));
  }

  public List<FlowNodeInstanceDbModel> searchFlowNodeInstancesByProcessInstanceKeyAndReturnAsDbModel(Long processInstanceKey) {
    return flowNodeInstanceMapper.search(
        FlowNodeInstanceDbQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey))));
  }

  public List<IncidentEntity> searchHistoricIncidents(String processDefinitionId) {
    return camundaClient.newIncidentSearchRequest()
        .filter(f -> f.processDefinitionId(prefixDefinitionId(processDefinitionId)))
        .execute()
        .items()
        .stream()
        .map(this::toIncidentEntity)
        .toList();
  }

  public List<IncidentDbModel> searchIncidentsByProcessInstanceKeyAndReturnAsDbModel(Long processInstanceKey) {
    String sql = "SELECT INCIDENT_KEY, TREE_PATH, PROCESS_INSTANCE_KEY, FLOW_NODE_INSTANCE_KEY " +
        "FROM INCIDENT WHERE PROCESS_INSTANCE_KEY = ?";

    return rdbmsQuery.query(sql, (rs, rowNum) -> {
      IncidentDbModel.Builder builder = new IncidentDbModel.Builder();
      builder.incidentKey(rs.getLong("INCIDENT_KEY"));
      builder.treePath(rs.getString("TREE_PATH"));
      builder.processInstanceKey(rs.getLong("PROCESS_INSTANCE_KEY"));
      builder.flowNodeInstanceKey(rs.getLong("FLOW_NODE_INSTANCE_KEY"));
      return builder.build();
    }, processInstanceKey);
  }

  public int searchProcessInstancePartitionId(Long processInstanceKey) {
    String sql = "SELECT PARTITION_ID FROM PROCESS_INSTANCE WHERE PROCESS_INSTANCE_KEY = ?";
    return rdbmsQuery.query(sql, (rs, rowNum) -> rs.getInt("PARTITION_ID"), processInstanceKey).getFirst();
  }

  public List<Integer> searchDecisionInstancePartitionIds(Long decisionInstanceKey) {
    String sql = "SELECT PARTITION_ID FROM DECISION_INSTANCE WHERE DECISION_INSTANCE_KEY = ?";
    return rdbmsQuery.query(sql, (rs, rowNum) -> rs.getInt("PARTITION_ID"), decisionInstanceKey);
  }

  public int searchVariablePartitionId(Long variableKey) {
    String sql = "SELECT PARTITION_ID FROM VARIABLE WHERE VAR_KEY = ?";
    return rdbmsQuery.query(sql, (rs, rowNum) -> rs.getInt("PARTITION_ID"), variableKey).getFirst();
  }

  public int searchUserTaskPartitionId(Long userTaskKey) {
    String sql = "SELECT PARTITION_ID FROM USER_TASK WHERE USER_TASK_KEY = ?";
    return rdbmsQuery.query(sql, (rs, rowNum) -> rs.getInt("PARTITION_ID"), userTaskKey).getFirst();
  }

  public List<VariableEntity> searchHistoricVariables(String varName) {
    return camundaClient.newVariableSearchRequest()
        .filter(f -> f.name(varName))
        .execute()
        .items()
        .stream()
        .map(this::toVariableEntity)
        .toList();
  }

  public List<VariableEntity> searchHistoricVariables(Long processInstanceKey) {
    return camundaClient.newVariableSearchRequest()
        .filter(f -> f.processInstanceKey(processInstanceKey))
        .execute()
        .items()
        .stream()
        .map(this::toVariableEntity)
        .toList();
  }

  public List<VariableEntity> searchHistoricVariables() {
    return camundaClient.newVariableSearchRequest()
        .execute()
        .items()
        .stream()
        .map(this::toVariableEntity)
        .toList();
  }

  public List<JobEntity> searchJobs(long processInstanceKey) {
    return camundaClient.newJobSearchRequest()
        .filter(f -> f.processInstanceKey(processInstanceKey))
        .execute()
        .items()
        .stream()
        .map(this::toJobEntity)
        .toList();
  }

  public List<JobEntity> searchJobs() {
    return camundaClient.newJobSearchRequest()
        .execute()
        .items()
        .stream()
        .map(this::toJobEntity)
        .toList();
  }

  public List<FormEntity> searchForms(String... formIds) {
    Set<String> prefixedFormIds = Arrays.stream(formIds)
        .map(ConverterUtil::prefixDefinitionId)
        .collect(Collectors.toSet());
    Map<Long, FormEntity> formsByKey = new LinkedHashMap<>();

    // There is no dedicated bulk form search request in the Camunda Client API.
    for (var processDefinition : camundaClient.newProcessDefinitionSearchRequest().execute().items()) {
      try {
        var form = camundaClient.newProcessDefinitionGetFormRequest(processDefinition.getProcessDefinitionKey()).execute();
        if (prefixedFormIds.isEmpty() || prefixedFormIds.contains(form.getFormId())) {
          formsByKey.putIfAbsent(form.getFormKey(), toFormEntity(form));
        }
      } catch (RuntimeException e) {
        if (!isNotFound(e)) {
          throw e;
        }
      }
    }

    for (var userTask : camundaClient.newUserTaskSearchRequest().execute().items()) {
      try {
        var form = camundaClient.newUserTaskGetFormRequest(userTask.getUserTaskKey()).execute();
        if (prefixedFormIds.isEmpty() || prefixedFormIds.contains(form.getFormId())) {
          formsByKey.putIfAbsent(form.getFormKey(), toFormEntity(form));
        }
      } catch (RuntimeException e) {
        if (!isNotFound(e)) {
          throw e;
        }
      }
    }

    return new ArrayList<>(formsByKey.values());
  }

  private Stream<DecisionInstanceEntity> searchHistoricDecisionInstancesByDefinitionId(
      String prefixedDecisionDefinitionId) {
    return camundaClient.newDecisionInstanceSearchRequest()
        .filter(f -> f.decisionDefinitionId(prefixedDecisionDefinitionId))
        .execute()
        .items()
        .stream()
        .map(this::toDecisionInstanceEntity);
  }

  private ProcessDefinitionEntity toProcessDefinitionEntity(
      io.camunda.client.api.search.response.ProcessDefinition processDefinition,
      String bpmnXml) {
    return new ProcessDefinitionEntity(
        processDefinition.getProcessDefinitionKey(),
        processDefinition.getName(),
        processDefinition.getProcessDefinitionId(),
        bpmnXml,
        processDefinition.getResourceName(),
        processDefinition.getVersion(),
        processDefinition.getVersionTag(),
        processDefinition.getTenantId(),
        null);
  }

  private DecisionDefinitionEntity toDecisionDefinitionEntity(
      io.camunda.client.api.search.response.DecisionDefinition decisionDefinition) {
    return new DecisionDefinitionEntity(
        decisionDefinition.getDecisionKey(),
        decisionDefinition.getDmnDecisionId(),
        decisionDefinition.getDmnDecisionName(),
        decisionDefinition.getVersion(),
        decisionDefinition.getDmnDecisionRequirementsId(),
        decisionDefinition.getDecisionRequirementsKey(),
        decisionDefinition.getDecisionRequirementsName(),
        decisionDefinition.getDecisionRequirementsVersion(),
        decisionDefinition.getTenantId());
  }

  private DecisionRequirementsEntity toDecisionRequirementsEntity(
      io.camunda.client.api.search.response.DecisionRequirements decisionRequirements,
      String xml) {
    return new DecisionRequirementsEntity(
        decisionRequirements.getDecisionRequirementsKey(),
        decisionRequirements.getDmnDecisionRequirementsId(),
        decisionRequirements.getDmnDecisionRequirementsName(),
        decisionRequirements.getVersion(),
        decisionRequirements.getResourceName(),
        xml,
        decisionRequirements.getTenantId());
  }

  private ProcessInstanceEntity toProcessInstanceEntity(
      io.camunda.client.api.search.response.ProcessInstance processInstance) {
    return new ProcessInstanceEntity(
        processInstance.getProcessInstanceKey(),
        processInstance.getRootProcessInstanceKey(),
        processInstance.getProcessDefinitionId(),
        processInstance.getProcessDefinitionName(),
        processInstance.getProcessDefinitionVersion(),
        processInstance.getProcessDefinitionVersionTag(),
        processInstance.getProcessDefinitionKey(),
        processInstance.getParentProcessInstanceKey(),
        processInstance.getParentElementInstanceKey(),
        processInstance.getStartDate(),
        processInstance.getEndDate(),
        mapEnum(ProcessInstanceEntity.ProcessInstanceState.class, processInstance.getState()),
        processInstance.getHasIncident(),
        processInstance.getTenantId(),
        null,
        processInstance.getTags(),
        processInstance.getBusinessId());
  }

  private DecisionInstanceEntity toDecisionInstanceEntity(
      io.camunda.client.api.search.response.DecisionInstance decisionInstance) {
    return new DecisionInstanceEntity(
        decisionInstance.getDecisionInstanceId(),
        decisionInstance.getDecisionInstanceKey(),
        mapEnum(DecisionInstanceEntity.DecisionInstanceState.class, decisionInstance.getState()),
        decisionInstance.getEvaluationDate(),
        decisionInstance.getEvaluationFailure(),
        null,
        decisionInstance.getProcessDefinitionKey(),
        decisionInstance.getProcessInstanceKey(),
        decisionInstance.getRootProcessInstanceKey(),
        decisionInstance.getElementInstanceKey(),
        decisionInstance.getTenantId(),
        decisionInstance.getDecisionDefinitionId(),
        decisionInstance.getDecisionDefinitionKey(),
        decisionInstance.getDecisionDefinitionName(),
        decisionInstance.getDecisionDefinitionVersion(),
        mapEnum(DecisionInstanceEntity.DecisionDefinitionType.class, decisionInstance.getDecisionDefinitionType()),
        decisionInstance.getRootDecisionDefinitionKey(),
        decisionInstance.getResult(),
        decisionInstance.getEvaluatedInputs()
            .stream()
            .map(input ->
                new DecisionInstanceEntity.DecisionInstanceInputEntity(
                    input.getInputId(),
                    input.getInputName(),
                    input.getInputValue()))
            .toList(),
        decisionInstance.getMatchedRules()
            .stream()
            .flatMap(rule -> rule.getEvaluatedOutputs()
                .stream()
                .map(output ->
                    new DecisionInstanceEntity.DecisionInstanceOutputEntity(
                        output.getOutputId(),
                        output.getOutputName(),
                        output.getOutputValue(),
                        rule.getRuleId(),
                        rule.getRuleIndex())))
            .toList());
  }

  private UserTaskEntity toUserTaskEntity(io.camunda.client.api.search.response.UserTask userTask) {
    return new UserTaskEntity(
        userTask.getUserTaskKey(),
        userTask.getElementId(),
        userTask.getName(),
        userTask.getBpmnProcessId(),
        userTask.getProcessName(),
        userTask.getCreationDate(),
        userTask.getCompletionDate(),
        userTask.getAssignee(),
        mapEnum(UserTaskEntity.UserTaskState.class, userTask.getState()),
        userTask.getFormKey(),
        userTask.getProcessDefinitionKey(),
        userTask.getProcessInstanceKey(),
        userTask.getRootProcessInstanceKey(),
        userTask.getElementInstanceKey(),
        userTask.getTenantId(),
        userTask.getDueDate(),
        userTask.getFollowUpDate(),
        userTask.getCandidateGroups(),
        userTask.getCandidateUsers(),
        userTask.getExternalFormReference(),
        userTask.getProcessDefinitionVersion(),
        userTask.getCustomHeaders(),
        userTask.getPriority(),
        userTask.getTags());
  }

  private FlowNodeInstanceEntity toFlowNodeInstanceEntity(
      io.camunda.client.api.search.response.ElementInstance elementInstance) {
    return new FlowNodeInstanceEntity(
        elementInstance.getElementInstanceKey(),
        elementInstance.getProcessInstanceKey(),
        elementInstance.getRootProcessInstanceKey(),
        elementInstance.getProcessDefinitionKey(),
        elementInstance.getStartDate(),
        elementInstance.getEndDate(),
        elementInstance.getElementId(),
        elementInstance.getElementName(),
        null,
        mapEnum(FlowNodeInstanceEntity.FlowNodeType.class, elementInstance.getType()),
        mapEnum(FlowNodeInstanceEntity.FlowNodeState.class, elementInstance.getState()),
        elementInstance.getIncident(),
        elementInstance.getIncidentKey(),
        elementInstance.getProcessDefinitionId(),
        elementInstance.getTenantId(),
        null);
  }

  private IncidentEntity toIncidentEntity(io.camunda.client.api.search.response.Incident incident) {
    return new IncidentEntity(
        incident.getIncidentKey(),
        incident.getProcessDefinitionKey(),
        incident.getProcessDefinitionId(),
        incident.getProcessInstanceKey(),
        incident.getRootProcessInstanceKey(),
        mapEnum(IncidentEntity.ErrorType.class, incident.getErrorType()),
        incident.getErrorMessage(),
        incident.getElementId(),
        incident.getElementInstanceKey(),
        incident.getCreationTime(),
        mapEnum(IncidentEntity.IncidentState.class, incident.getState()),
        incident.getJobKey(),
        incident.getTenantId());
  }

  private VariableEntity toVariableEntity(io.camunda.client.api.search.response.Variable variable) {
    return new VariableEntity(
        variable.getVariableKey(),
        variable.getName(),
        variable.getValue(),
        variable.getValue(),
        variable.isTruncated(),
        variable.getScopeKey(),
        variable.getProcessInstanceKey(),
        variable.getRootProcessInstanceKey(),
        null,
        variable.getTenantId());
  }

  private JobEntity toJobEntity(io.camunda.client.api.search.response.Job job) {
    return new JobEntity(
        job.getJobKey(),
        job.getType(),
        job.getWorker(),
        mapEnum(JobEntity.JobState.class, job.getState()),
        mapEnum(JobEntity.JobKind.class, job.getKind()),
        mapEnum(JobEntity.ListenerEventType.class, job.getListenerEventType()),
        job.getRetries(),
        job.isDenied(),
        job.getDeniedReason(),
        job.hasFailedWithRetriesLeft(),
        job.getErrorCode(),
        job.getErrorMessage(),
        job.getCustomerHeaders(),
        job.getDeadline(),
        job.getEndTime(),
        job.getProcessDefinitionId(),
        job.getProcessDefinitionKey(),
        job.getProcessInstanceKey(),
        job.getRootProcessInstanceKey(),
        job.getElementId(),
        job.getElementInstanceKey(),
        job.getTenantId(),
        job.getCreationTime(),
        job.getLastUpdateTime());
  }

  private AuditLogEntity toAuditLogEntity(io.camunda.client.api.search.response.AuditLogResult auditLog) {
    return new AuditLogEntity(
        auditLog.getAuditLogKey(),
        auditLog.getEntityKey(),
        mapEnum(AuditLogEntity.AuditLogEntityType.class, auditLog.getEntityType()),
        mapEnum(AuditLogEntity.AuditLogOperationType.class, auditLog.getOperationType()),
        parseLong(auditLog.getBatchOperationKey()),
        mapEnum(io.camunda.search.entities.BatchOperationType.class, auditLog.getBatchOperationType()),
        auditLog.getTimestamp(),
        auditLog.getActorId(),
        mapEnum(AuditLogEntity.AuditLogActorType.class, auditLog.getActorType()),
        auditLog.getAgentElementId(),
        auditLog.getTenantId(),
        null,
        mapEnum(AuditLogEntity.AuditLogOperationResult.class, auditLog.getResult()),
        mapEnum(AuditLogEntity.AuditLogOperationCategory.class, auditLog.getCategory()),
        auditLog.getProcessDefinitionId(),
        parseLong(auditLog.getProcessDefinitionKey()),
        parseLong(auditLog.getProcessInstanceKey()),
        parseLong(auditLog.getRootProcessInstanceKey()),
        parseLong(auditLog.getElementInstanceKey()),
        parseLong(auditLog.getJobKey()),
        parseLong(auditLog.getUserTaskKey()),
        auditLog.getDecisionRequirementsId(),
        parseLong(auditLog.getDecisionRequirementsKey()),
        auditLog.getDecisionDefinitionId(),
        parseLong(auditLog.getDecisionDefinitionKey()),
        parseLong(auditLog.getDecisionEvaluationKey()),
        parseLong(auditLog.getDeploymentKey()),
        parseLong(auditLog.getFormKey()),
        parseLong(auditLog.getResourceKey()),
        mapEnum(AuditLogEntity.AuditLogEntityType.class, auditLog.getRelatedEntityType()),
        auditLog.getRelatedEntityKey(),
        auditLog.getEntityDescription(),
        null);
  }

  private FormEntity toFormEntity(io.camunda.client.api.search.response.Form form) {
    return new FormEntity(
        form.getFormKey(),
        form.getTenantId(),
        form.getFormId(),
        form.getSchema(),
        form.getVersion());
  }

  private Long parseLong(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Long.parseLong(value);
  }

  private boolean isNotFound(RuntimeException exception) {
    return exception instanceof ClientHttpException clientHttpException && clientHttpException.code() == 404;
  }

  private <S extends Enum<S>, T extends Enum<T>> T mapEnum(Class<T> target, S source) {
    return source == null ? null : mapEnum(target, source.name());
  }

  private <T extends Enum<T>> T mapEnum(Class<T> target, String source) {
    if (source == null) {
      return null;
    }
    try {
      return Enum.valueOf(target, source);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  @Configuration
  public static class HistoryCustomConfiguration {

    @Bean
    @Conditional(C8DataSourceConfigured.class)
    public RdbmsPurger rdbmsPurger(
        PurgeMapper purgeMapper,
        VendorDatabaseProperties vendorDatabaseProperties) {
      return new RdbmsPurger(purgeMapper, vendorDatabaseProperties);
    }

  }

  protected void completeAllUserTasksWithDefaultUserTaskId() {
    for (Task task : taskService.createTaskQuery().taskDefinitionKey(USER_TASK_ID).list()) {
      taskService.complete(task.getId());
    }
  }

  protected void assertDecisionInstance(
      DecisionInstanceEntity instance,
      String decisionDefinitionId,
      Date evaluationDate,
      Long flowNodeInstanceKey,
      Long processInstanceKey,
      Long processDefinitionKey,
      Long decisionDefinitionKey,
      Long rootDecisionDefinitionKey,
      DecisionInstanceEntity.DecisionDefinitionType decisionDefinitionType,
      String result,
      String inputName,
      String inputValue,
      String outputName,
      String outputValue,
      String outputRuleId,
      int outputRuleIndex) {
    assertThat(instance.decisionInstanceId()).isNotNull();
    assertThat(instance.decisionInstanceKey()).isNotNull();
    assertThat(instance.state()).isEqualTo(EVALUATED);
    assertThat(instance.evaluationDate()).isEqualTo(convertDate(evaluationDate));
    assertThat(instance.evaluationFailure()).isNull();
    assertThat(instance.evaluationFailureMessage()).isNull();
    assertThat(instance.flowNodeInstanceKey()).isEqualTo(flowNodeInstanceKey);
    assertThat(instance.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(instance.processDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(instance.decisionDefinitionKey()).isEqualTo(decisionDefinitionKey);
    assertThat(instance.rootDecisionDefinitionKey()).isEqualTo(rootDecisionDefinitionKey);
    assertThat(instance.decisionDefinitionId()).isEqualTo(prefixDefinitionId(decisionDefinitionId));
    assertThat(instance.tenantId()).isEqualTo(C8_DEFAULT_TENANT);
    assertThat(instance.decisionDefinitionType()).isEqualTo(decisionDefinitionType);
    assertThat(instance.result()).isEqualTo(result);
    assertThat(instance.evaluatedInputs()).singleElement().satisfies(input -> {
      assertThat(input.inputId()).isNotNull();
      assertThat(input.inputName()).isEqualTo(inputName);
      assertThat(input.inputValue()).isEqualTo(inputValue);
    });
    assertThat(instance.evaluatedOutputs()).singleElement().satisfies(output -> {
      assertThat(output.outputId()).isNotNull();
      assertThat(output.outputName()).isEqualTo(outputName);
      assertThat(output.outputValue()).isEqualTo(outputValue);
      assertThat(output.ruleId()).isEqualTo(outputRuleId);
      assertThat(output.ruleIndex()).isEqualTo(outputRuleIndex);
    });
  }
}
