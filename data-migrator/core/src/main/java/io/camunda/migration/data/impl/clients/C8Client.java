/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.clients;

import static io.camunda.migration.data.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_ACTIVATE_JOBS;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_CREATE_GROUP_MEMBERSHIP;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_CREATE_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_CREATE_TENANT_GROUP_MEMBERSHIP;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_CREATE_TENANT_USER_MEMBERSHIP;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_DEPLOY_C8_RESOURCES;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_FETCH_PROCESS_DEFINITION_XML;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_FETCH_VARIABLE;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_FIND_PROCESS_INSTANCE_BY_KEY;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_AUDIT_LOG;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_DECISION_INSTANCE;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_DECISION_INSTANCE_INPUT;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_DECISION_INSTANCE_OUTPUT;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_DECISION_REQUIREMENTS;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_FLOW_NODE_INSTANCE;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_INCIDENT;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_JOB;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_CANDIDATE_GROUPS;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_CANDIDATE_USERS;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_USER_TASK;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_INSERT_VARIABLE;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_MIGRATE_AUTHORIZATION;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_MIGRATE_GROUP;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_MIGRATE_TENANT;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_MIGRATE_USER;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_MODIFY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_QUERY_TOPOLOGY;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_SEARCH_DECISION_DEFINITIONS;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_SEARCH_DECISION_REQUIREMENTS;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_SEARCH_FLOW_NODE_INSTANCES;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_SEARCH_PROCESS_DEFINITIONS;
import static io.camunda.migration.data.impl.logging.C8ClientLogs.FAILED_TO_SEARCH_USER_TASKS;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.PARTITION_COUNT_PROPERTY;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_USER_TASK;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migration.data.impl.util.ExceptionUtils.callApi;
import static io.camunda.migration.data.impl.util.ExceptionUtils.wrapException;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateAuthorizationCommandStep1;
import io.camunda.client.api.command.CreateGroupCommandStep1;
import io.camunda.client.api.command.CreateTenantCommandStep1;
import io.camunda.client.api.command.CreateUserCommandStep1;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.migration.data.impl.history.C8EntityNotFoundException;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.fetch.GroupGetRequest;
import io.camunda.client.api.fetch.TenantGetRequest;
import io.camunda.client.api.fetch.UserGetRequest;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CreateAuthorizationResponse;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.client.api.search.response.DecisionRequirements;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Group;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.User;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.FormMapper;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.FormDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.exception.IdentityMigratorException;
import io.camunda.migration.data.exception.MigratorException;
import io.camunda.migration.data.impl.identity.C8Authorization;
import io.camunda.migration.data.impl.identity.SecurePasswordGenerator;
import io.camunda.migration.data.impl.model.FlowNodeActivation;
import io.camunda.migration.data.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.identity.Tenant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Wrapper class for Camunda 8 Client API calls with exception handling.
 * Maintains the same exception wrapping behavior as ExceptionUtils.callApi.
 */
@Component
public class C8Client {

  @Autowired
  protected MigratorProperties properties;

  @Autowired
  protected CamundaClient camundaClient;

  // MyBatis mappers for history migration
  // These are optional because they're only available when C8 data source is configured
  @Autowired(required = false)
  protected ProcessInstanceMapper processInstanceMapper;

  @Autowired(required = false)
  protected DecisionInstanceMapper decisionInstanceMapper;

  @Autowired(required = false)
  protected UserTaskMapper userTaskMapper;

  @Autowired(required = false)
  protected VariableMapper variableMapper;

  @Autowired(required = false)
  protected IncidentMapper incidentMapper;

  @Autowired(required = false)
  protected ProcessDefinitionMapper processDefinitionMapper;

  @Autowired(required = false)
  protected DecisionDefinitionMapper decisionDefinitionMapper;

  @Autowired(required = false)
  protected FlowNodeInstanceMapper flowNodeInstanceMapper;

  @Autowired(required = false)
  protected DecisionRequirementsMapper decisionRequirementsMapper;

  @Autowired(required = false)
  protected AuditLogMapper auditLogMapper;

  @Autowired(required = false)
  protected FormMapper formMapper;

  @Autowired(required = false)
  protected JobMapper jobMapper;

  /**
   * Creates a new process instance with the given BPMN process ID and variables.
   */
  public ProcessInstanceEvent createProcessInstance(String bpmnProcessId, String tenantId,
                                                    Map<String, Object> variables) {
    var createProcessInstance = camundaClient.newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .variables(variables)
        .tenantId(getTenantId(tenantId));


    return callApi(createProcessInstance::execute, FAILED_TO_CREATE_PROCESS_INSTANCE + bpmnProcessId);
  }

  /**
   * Searches for process definitions with the given process definition ID.
   */
  public SearchResponse<ProcessDefinition> searchProcessDefinitions(String processDefinitionId, String tenantId) {
    var searchRequest = camundaClient.newProcessDefinitionSearchRequest().filter(filter -> {
      var filterBuilder = filter.processDefinitionId(processDefinitionId);
      if (!StringUtils.isEmpty(tenantId)) {
        filterBuilder.tenantId(tenantId);
      }
    }).sort(s -> s.version().desc());
    return callApi(searchRequest::execute, FAILED_TO_SEARCH_PROCESS_DEFINITIONS + processDefinitionId);
  }

  /**
   * Gets the XML content of a process definition by its key.
   */
  public String getProcessDefinitionXml(long processDefinitionKey) {
    var getXmlRequest = camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey);
    return callApi(getXmlRequest::execute, FAILED_TO_FETCH_PROCESS_DEFINITION_XML + processDefinitionKey);
  }

  /**
   * Activates jobs for the specified job type.
   */
  public List<ActivatedJob> activateJobs(String jobType) {
    Set<String> tenantIds = properties.getTenantIds();

    var activateJobs = camundaClient.newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(properties.getPageSize());
    if (tenantIds != null && !tenantIds.isEmpty()) {
      Set<String> tenantIdsWithDefault = new java.util.HashSet<>(tenantIds);
      tenantIdsWithDefault.add(C8_DEFAULT_TENANT);
      activateJobs = activateJobs.tenantIds(List.copyOf(tenantIdsWithDefault));
    }
    return callApi(activateJobs::execute, FAILED_TO_ACTIVATE_JOBS + jobType).getJobs();
  }

  /**
   * Gets a variable value from an activated job.
   */
  public Object getJobVariable(ActivatedJob job, String variableName) {
    return callApi(() -> job.getVariable(variableName), String.format(FAILED_TO_FETCH_VARIABLE, variableName, job.getKey()));
  }

  /**
   * Creates and executes a process instance modification with activity activation.
   */
  public void modifyProcessInstance(long processInstanceKey,
                                    long startEventInstanceKey,
                                    List<FlowNodeActivation> flowNodeActivations) {
    var modifyProcessInstance = camundaClient.newModifyProcessInstanceCommand(processInstanceKey);

    // Cancel start event instance where migrator job sits to avoid executing the activities twice.
    modifyProcessInstance.terminateElement(startEventInstanceKey);

    flowNodeActivations.forEach(flowNodeActivation -> {
      String activityId = flowNodeActivation.activityId();
      Map<String, Object> variables = flowNodeActivation.variables();
      if (variables != null && !variables.isEmpty()) {
        modifyProcessInstance.activateElement(activityId).withVariables(variables, activityId);
      } else {
        modifyProcessInstance.activateElement(activityId);
      }
    });

    callApi(() -> ((ModifyProcessInstanceCommandStep3) modifyProcessInstance).execute(), FAILED_TO_MODIFY_PROCESS_INSTANCE + processInstanceKey);
  }

  /**
   * Deploys C8 models from the given set of model files.
   */
  public void deployResources(Set<Path> models) {
    DeployResourceCommandStep1.DeployResourceCommandStep2 deployResourceCmd = null;
    var deployResource = camundaClient.newDeployResourceCommand();
    for (Path model : models) {
      deployResourceCmd = deployResource.addResourceFile(model.toString());
    }

    if (deployResourceCmd != null) {
      callApi(deployResourceCmd::execute, FAILED_TO_DEPLOY_C8_RESOURCES + models);
    }
  }

  // ========== MyBatis Mapper Wrapper Methods for History Migration ==========

  /**
   * Inserts a ProcessDefinition into the database.
   */
  public void insertProcessDefinition(ProcessDefinitionDbModel dbModel) {
    callApi(() -> processDefinitionMapper.insert(dbModel), FAILED_TO_INSERT_PROCESS_DEFINITION);
  }

  /**
   * Inserts a ProcessInstance into the database.
   */
  public void insertProcessInstance(ProcessInstanceDbModel dbModel) {
    callApi(() -> processInstanceMapper.insert(dbModel), FAILED_TO_INSERT_PROCESS_INSTANCE);
  }

  /**
   * Inserts Process Instance tags into the database.
   */
  public void insertProcessInstanceTags(ProcessInstanceDbModel dbModel) {
    callApi(() -> processInstanceMapper.insertTags(dbModel), FAILED_TO_INSERT_PROCESS_INSTANCE);
  }

  /**
   * Finds a ProcessInstance by key.
   */
  protected ProcessInstanceEntity findProcessInstance(Long key) {
    return callApi(
            () -> camundaClient.newProcessInstanceSearchRequest()
                .filter(filter -> filter.processInstanceKey(key))
                .execute()
                .items(),
            FAILED_TO_FIND_PROCESS_INSTANCE_BY_KEY + key)
        .stream()
        .findFirst()
        .map(this::toProcessInstanceEntity)
        .orElse(null);
  }

  /**
   * Finds a ProcessInstance by key and throws if not found.
   * <p>
   * Use this method when the entity is expected to exist (e.g., based on migration mapping).
   * If the entity is not found, it indicates C8 history cleanup ran before migration completed.
   * </p>
   *
   * @param processInstanceKey the C8 process instance key
   * @return the found process instance
   * @throws C8EntityNotFoundException if not found
   */
  public ProcessInstanceEntity findProcessInstanceOrThrow(Long processInstanceKey) {
    ProcessInstanceEntity result = findProcessInstance(processInstanceKey);
    if (result == null) {
      throw new C8EntityNotFoundException(HISTORY_PROCESS_INSTANCE, processInstanceKey);
    }
    return result;
  }

  /**
   * Inserts a DecisionRequirementsDefinition into the database.
   */
  public void insertDecisionRequirements(DecisionRequirementsDbModel dbModel) {
    callApi(() -> decisionRequirementsMapper.insert(dbModel), FAILED_TO_INSERT_DECISION_REQUIREMENTS);
  }

  /**
   * Searches for DecisionRequirementsDefinition matching the query
   */
  protected List<DecisionRequirementsEntity> searchDecisionRequirementsByKey(Long decisionRequirementsKey) {
    return callApi(
            () -> camundaClient.newDecisionRequirementsSearchRequest()
                .filter(filter -> filter.decisionRequirementsKey(decisionRequirementsKey))
                .execute()
                .items(),
            FAILED_TO_SEARCH_DECISION_REQUIREMENTS)
        .stream()
        .map(this::toDecisionRequirementsEntity)
        .toList();
  }

  /**
   * Finds a DecisionRequirements by key and throws if not found.
   * <p>
   * Use this method when the entity is expected to exist (e.g., based on migration mapping).
   * If the entity is not found, it indicates C8 history cleanup ran before migration completed.
   * </p>
   *
   * @param decisionRequirementsKey the C8 decision requirements key
   * @return the found decision requirements
   * @throws C8EntityNotFoundException if not found
   */
  public DecisionRequirementsEntity findDecisionRequirementsOrThrow(Long decisionRequirementsKey) {
    return C8EntityQuery
        .of(() -> searchDecisionRequirementsByKey(decisionRequirementsKey))
        .findOneOrThrow(HISTORY_DECISION_REQUIREMENT, decisionRequirementsKey);
  }

  /**
   * Inserts a DecisionDefinition into the database.
   */
  public void insertDecisionDefinition(DecisionDefinitionDbModel dbModel) {
    callApi(() -> decisionDefinitionMapper.insert(dbModel), FAILED_TO_INSERT_DECISION_DEFINITION);
  }

  /**
   * Searches for DecisionDefinitions matching the query.
   */
  protected List<DecisionDefinitionEntity> searchDecisionDefinitionsByKey(Long decisionDefinitionKey) {
    return callApi(
            () -> camundaClient.newDecisionDefinitionSearchRequest()
                .filter(filter -> filter.decisionDefinitionKey(decisionDefinitionKey))
                .execute()
                .items(),
            FAILED_TO_SEARCH_DECISION_DEFINITIONS)
        .stream()
        .map(this::toDecisionDefinitionEntity)
        .toList();
  }

  /**
   * Finds a DecisionDefinition by key and throws if not found.
   * <p>
   * Use this method when the entity is expected to exist (e.g., based on migration mapping).
   * If the entity is not found, it indicates C8 history cleanup ran before migration completed.
   * </p>
   *
   * @param decisionDefinitionKey the C8 decision definition key
   * @return the found decision definition
   * @throws C8EntityNotFoundException if not found
   */
  public DecisionDefinitionEntity findDecisionDefinitionOrThrow(Long decisionDefinitionKey) {
    return C8EntityQuery
        .of(() -> searchDecisionDefinitionsByKey(decisionDefinitionKey))
        .findOneOrThrow(HISTORY_DECISION_DEFINITION, decisionDefinitionKey);
  }

  /**
   * Inserts a DecisionInstance into the database.
   */
  public void insertDecisionInstance(DecisionInstanceDbModel dbModel) {
    callApi(() -> decisionInstanceMapper.insert(dbModel), FAILED_TO_INSERT_DECISION_INSTANCE);
    if (!dbModel.evaluatedInputs().isEmpty()) {
      callApi(() -> decisionInstanceMapper.insertInput(dbModel), FAILED_TO_INSERT_DECISION_INSTANCE_INPUT);
    }

    if (!dbModel.evaluatedOutputs().isEmpty()) {
      callApi(() -> decisionInstanceMapper.insertOutput(dbModel), FAILED_TO_INSERT_DECISION_INSTANCE_OUTPUT);
    }
  }

  /**
   * Inserts an Incident into the database.
   */
  public void insertIncident(IncidentDbModel dbModel) {
    callApi(() -> incidentMapper.insert(dbModel), FAILED_TO_INSERT_INCIDENT);
  }

  /**
   * Inserts a Variable into the database.
   */
  public void insertVariable(VariableDbModel dbModel) {
    callApi(() -> variableMapper.insert(new BatchInsertDto(List.of(dbModel))), FAILED_TO_INSERT_VARIABLE);
  }

  /**
   * Inserts a UserTask into the database.
   */
  public void insertUserTask(UserTaskDbModel dbModel) {
    callApi(() -> userTaskMapper.insert(dbModel), FAILED_TO_INSERT_USER_TASK);
  }

  /**
   * Inserts User Task tags into the database.
   */
  public void insertUserTaskTags(UserTaskDbModel dbModel) {
    callApi(() -> userTaskMapper.insertTags(dbModel), FAILED_TO_INSERT_USER_TASK);
  }

  /**
   * Inserts candidate users for a UserTask into the database.
   */
  public void insertCandidateUsers(UserTaskDbModel dbModel) {
    callApi(() -> userTaskMapper.insertCandidateUsers(dbModel), FAILED_TO_INSERT_CANDIDATE_USERS);
  }

  /**
   * Inserts candidate groups for a UserTask into the database.
   */
  public void insertCandidateGroups(UserTaskDbModel dbModel) {
    callApi(() -> userTaskMapper.insertCandidateGroups(dbModel), FAILED_TO_INSERT_CANDIDATE_GROUPS);
  }

  /**
   * Inserts a FlowNodeInstance into the database.
   */
  public void insertFlowNodeInstance(FlowNodeInstanceDbModel dbModel) {
    callApi(() -> flowNodeInstanceMapper.insert(new BatchInsertDto(List.of(dbModel))), FAILED_TO_INSERT_FLOW_NODE_INSTANCE);
  }

  /**
   * Inserts an AuditLog into the database.
   */
  public void insertAuditLog(AuditLogDbModel dbModel) {
    callApi(() -> auditLogMapper.insert(new BatchInsertDto(List.of(dbModel))), FAILED_TO_INSERT_AUDIT_LOG);
  }

  /**
   * Searches for FlowNodeInstances matching the query.
   */
  public List<FlowNodeInstanceEntity> searchFlowNodeInstances(String activityId, Long processInstanceKey) {
    return callApi(
            () -> camundaClient.newElementInstanceSearchRequest()
                .filter(filter -> filter.elementId(activityId).processInstanceKey(processInstanceKey))
                .execute()
                .items(),
            FAILED_TO_SEARCH_FLOW_NODE_INSTANCES)
        .stream()
        .map(this::toFlowNodeInstanceEntity)
        .toList();
  }

  public List<FlowNodeInstanceEntity> findFlowNodes(String activityId, Long processInstanceKey) {
    return searchFlowNodeInstances(activityId, processInstanceKey);
  }

  public FlowNodeInstanceEntity findFlowNodeOrThrow(Long flowNodeInstanceKey) {
    return C8EntityQuery
        .of(() -> callApi(
                () -> camundaClient.newElementInstanceSearchRequest()
                    .filter(filter -> filter.elementInstanceKey(flowNodeInstanceKey))
                    .execute()
                    .items(),
                FAILED_TO_SEARCH_FLOW_NODE_INSTANCES)
            .stream()
            .map(this::toFlowNodeInstanceEntity)
            .toList())
        .findOneOrThrow(HISTORY_FLOW_NODE, flowNodeInstanceKey);
  }

  /**
   * Searches for User tasks matching the query.
   */
  protected List<io.camunda.search.entities.UserTaskEntity> searchUserTasksByKey(Long userTaskKey) {
    return callApi(
            () -> camundaClient.newUserTaskSearchRequest()
                .filter(filter -> filter.userTaskKey(userTaskKey))
                .execute()
                .items(),
            FAILED_TO_SEARCH_USER_TASKS)
        .stream()
        .map(this::toUserTaskEntity)
        .toList();
  }

  /**
   * Finds a User task by key and throws if not found.
   * <p>
   * Use this method when the entity is expected to exist (e.g., based on migration mapping).
   * If the entity is not found, it indicates C8 history cleanup ran before migration completed.
   * </p>
   *
   * @param userTaskKey the C8 user task key
   * @return the found user task
   * @throws C8EntityNotFoundException if not found
   */
  public io.camunda.search.entities.UserTaskEntity findUserTaskOrThrow(Long userTaskKey) {
    return C8EntityQuery
        .of(() -> searchUserTasksByKey(userTaskKey))
        .findOneOrThrow(HISTORY_USER_TASK, userTaskKey);
  }

  /**
   * Searches for ProcessDefinitions matching the query.
   */
  protected List<ProcessDefinitionEntity> searchProcessDefinitionsByKey(Long processDefinitionKey) {
    return callApi(
            () -> camundaClient.newProcessDefinitionSearchRequest()
                .filter(filter -> filter.processDefinitionKey(processDefinitionKey))
                .execute()
                .items(),
            FAILED_TO_SEARCH_PROCESS_DEFINITIONS)
        .stream()
        .map(this::toProcessDefinitionEntity)
        .toList();
  }

  /**
   * Finds a ProcessDefinition by key and throws if not found.
   * <p>
   * Use this method when the entity is expected to exist (e.g., based on migration mapping).
   * If the entity is not found, it indicates C8 history cleanup ran before migration completed.
   * </p>
   *
   * @param processDefinitionKey the C8 process definition key
   * @return the found process definition
   * @throws C8EntityNotFoundException if not found
   */
  public ProcessDefinitionEntity findProcessDefinitionOrThrow(Long processDefinitionKey) {
    return C8EntityQuery
        .of(() -> searchProcessDefinitionsByKey(processDefinitionKey))
        .findOneOrThrow(HISTORY_PROCESS_DEFINITION, processDefinitionKey);
  }

  protected ProcessInstanceEntity toProcessInstanceEntity(ProcessInstance processInstance) {
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
        mapEnum(io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState.class, processInstance.getState()),
        processInstance.getHasIncident(),
        processInstance.getTenantId(),
        null,
        processInstance.getTags(),
        processInstance.getBusinessId());
  }

  protected DecisionRequirementsEntity toDecisionRequirementsEntity(DecisionRequirements decisionRequirements) {
    return new DecisionRequirementsEntity(
        decisionRequirements.getDecisionRequirementsKey(),
        decisionRequirements.getDmnDecisionRequirementsId(),
        decisionRequirements.getDmnDecisionRequirementsName(),
        decisionRequirements.getVersion(),
        decisionRequirements.getResourceName(),
        null,
        decisionRequirements.getTenantId());
  }

  protected DecisionDefinitionEntity toDecisionDefinitionEntity(DecisionDefinition decisionDefinition) {
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

  protected ProcessDefinitionEntity toProcessDefinitionEntity(ProcessDefinition processDefinition) {
    return new ProcessDefinitionEntity(
        processDefinition.getProcessDefinitionKey(),
        processDefinition.getName(),
        processDefinition.getProcessDefinitionId(),
        null,
        processDefinition.getResourceName(),
        processDefinition.getVersion(),
        processDefinition.getVersionTag(),
        processDefinition.getTenantId(),
        null);
  }

  protected FlowNodeInstanceEntity toFlowNodeInstanceEntity(ElementInstance elementInstance) {
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
        mapEnum(io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType.class, elementInstance.getType()),
        mapEnum(io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState.class, elementInstance.getState()),
        elementInstance.getIncident(),
        elementInstance.getIncidentKey(),
        elementInstance.getProcessDefinitionId(),
        elementInstance.getTenantId(),
        null);
  }

  protected io.camunda.search.entities.UserTaskEntity toUserTaskEntity(UserTask userTask) {
    return new io.camunda.search.entities.UserTaskEntity(
        userTask.getUserTaskKey(),
        userTask.getElementId(),
        userTask.getName(),
        userTask.getBpmnProcessId(),
        userTask.getProcessName(),
        userTask.getCreationDate(),
        userTask.getCompletionDate(),
        userTask.getAssignee(),
        mapEnum(io.camunda.search.entities.UserTaskEntity.UserTaskState.class, userTask.getState()),
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

  protected <T extends Enum<T>> T mapEnum(Class<T> target, Enum<?> source) {
    if (source == null) {
      return null;
    }
    try {
      return Enum.valueOf(target, source.name());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Creates a new user in C8
   */
  public void createUser(org.camunda.bpm.engine.identity.User user) {
    String name = user.getFirstName() + " " + user.getLastName();
    CreateUserCommandStep1 command = camundaClient.newCreateUserCommand()
        .username(user.getId())
        .name(name)
        .password(SecurePasswordGenerator.generate());
    if (user.getEmail() != null) {
      command = command.email(user.getEmail());
    }

    callApi(command::execute, FAILED_TO_MIGRATE_USER + user.getId());

  }

  /**
   * Creates a new group in C8
   */
  public void createGroup(org.camunda.bpm.engine.identity.Group group) {
    CreateGroupCommandStep1.CreateGroupCommandStep2 command = camundaClient.newCreateGroupCommand().groupId(group.getId()).name(group.getName());
    callApi(command::execute, FAILED_TO_MIGRATE_GROUP + group.getId());
  }

  /**
   * Creates a new tenant in C8
   */
  public void createTenant(Tenant tenant) {
    CreateTenantCommandStep1 command = camundaClient.newCreateTenantCommand().tenantId(tenant.getId()).name(tenant.getName());
    callApi(command::execute, FAILED_TO_MIGRATE_TENANT + tenant.getId());
  }

  /**
   * Assigns a user to a group in C8, creating a group membership for the user
   */
  public void createGroupAssignment(String groupId, String userId) {
    var command = camundaClient.newAssignUserToGroupCommand().username(userId).groupId(groupId);
    try {
      if (getUser(userId) != null) {
        callApi(command::execute, String.format(FAILED_TO_CREATE_GROUP_MEMBERSHIP, groupId, userId));
      } else {
        throw new IdentityMigratorException("User " + userId + " does not exist in C8");
      }
    } catch (IdentityMigratorException e) {
      throw wrapException(String.format(FAILED_TO_CREATE_GROUP_MEMBERSHIP, groupId, userId), e);
    }
  }

  /**
   * Assigns a user to a tenant in C8, creating a tenant membership for the user
   */
  public void createUserTenantAssignment(String tenantId, String userId) {
    var command = camundaClient.newAssignUserToTenantCommand().username(userId).tenantId(tenantId);
    try {
      if (getUser(userId) != null) {
        callApi(command::execute, String.format(FAILED_TO_CREATE_TENANT_USER_MEMBERSHIP, tenantId, userId));
      } else {
        throw new IdentityMigratorException("User " + userId + " does not exist in C8");
      }
    } catch (IdentityMigratorException e) {
      throw wrapException(String.format(FAILED_TO_CREATE_TENANT_USER_MEMBERSHIP, tenantId, userId), e);
    }
  }

  /**
   * Assigns a group to a tenant in C8, creating a tenant membership for the group
   */
  public void createGroupTenantAssignment(String tenantId, String groupId) {
    var command = camundaClient.newAssignGroupToTenantCommand().groupId(groupId).tenantId(tenantId);
    try {
      if (getGroup(groupId) != null) {
        callApi(command::execute, String.format(FAILED_TO_CREATE_TENANT_GROUP_MEMBERSHIP, tenantId, groupId));
      } else {
        throw new IdentityMigratorException("Group " + groupId + " does not exist in C8");
      }
    } catch (IdentityMigratorException e) {
      throw wrapException(String.format(FAILED_TO_CREATE_TENANT_GROUP_MEMBERSHIP, tenantId, groupId), e);
    }
  }

  /**
   * Creates a new authorization in C8
   */
  public CreateAuthorizationResponse createAuthorization(String c7Id, C8Authorization c8Authorization) {
    CreateAuthorizationCommandStep1.CreateAuthorizationCommandStep6 command = camundaClient
        .newCreateAuthorizationCommand()
        .ownerId(c8Authorization.ownerId())
        .ownerType(c8Authorization.ownerType())
        .resourceId(c8Authorization.resourceId())
        .resourceType(c8Authorization.resourceType())
        .permissionTypes(c8Authorization.permissions().toArray(new PermissionType[0]));

    return callApi(command::execute, FAILED_TO_MIGRATE_AUTHORIZATION + c7Id);
  }

  /**
   * Fetches a tenant by ID from C8
   */
  public io.camunda.client.api.search.response.Tenant getTenant(String tenantId) {
    TenantGetRequest tenantGetRequest = camundaClient.newTenantGetRequest(tenantId);
    return callApi(tenantGetRequest::execute, "Failed to get tenant " + tenantId);
  }

  /**
   * Fetches a user by ID from C8
   */
  public User getUser(String userId) {
    UserGetRequest userGetRequest = camundaClient.newUserGetRequest(userId);
    return callApi(userGetRequest::execute, "Failed to get user " + userId);
  }

  /**
   * Fetches a group by ID from C8
   */
  public Group getGroup(String groupId) {
    GroupGetRequest groupGetRequest = camundaClient.newGroupGetRequest(groupId);
    return callApi(groupGetRequest::execute, "Failed to get group " + groupId);
  }

  /**
   * Inserts a Form into the database.
   */
  public void insertForm(FormDbModel dbModel) {
    callApi(() -> formMapper.insert(dbModel), "Failed to insert form");
  }

  /**
   * Inserts a Job into the database.
   */
  public void insertJob(JobDbModel dbModel) {
    callApi(() -> jobMapper.insert(new BatchInsertDto(List.of(dbModel))), FAILED_TO_INSERT_JOB);
  }

  /**
   * Fetches the available Zeebe partition IDs from the broker topology.
   *
   * <p>Note: This method may return an empty list if the topology response contains no brokers
   * or partitions (e.g., misconfigured cluster). Callers should validate the result before use.
   *
   * @return list of partition IDs (may be empty if no partitions are discovered)
   */
  public List<Integer> fetchPartitionIds() {
    var topology = callApi(() -> camundaClient.newTopologyRequest().execute(), FAILED_TO_QUERY_TOPOLOGY + PARTITION_COUNT_PROPERTY);
    return topology.getBrokers()
        .stream()
        .flatMap(broker -> broker.getPartitions().stream())
        .map(PartitionInfo::getPartitionId)
        .distinct()
        .sorted()
        .toList();
  }

  public static class C8EntityQuery<T> {

    protected final Supplier<List<T>> querySupplier;

    protected C8EntityQuery(Supplier<List<T>> querySupplier) {
      this.querySupplier = querySupplier;
    }

    /**
     * Creates a new C8EntityQuery with the given query supplier.
     *
     * @param querySupplier supplier that executes the query and returns results
     * @param <T> the entity type
     * @return a new C8EntityQuery instance
     */
    public static <T> C8EntityQuery<T> of(Supplier<List<T>> querySupplier) {
      return new C8EntityQuery<>(querySupplier);
    }

    /**
     * Executes the query and returns the first result, or throws
     * {@link C8EntityNotFoundException} if no results are found.
     * <p>
     * Use this method when querying for an entity that is expected to exist
     * based on prior migration (e.g., the mapping table indicates it was migrated).
     * If the entity is not found, it likely means C8 history cleanup ran before
     * migration completed.
     * </p>
     *
     * @param entityType the type of entity being queried (for error context)
     * @param c8Key the C8 key that was used to query (for error context)
     * @return the found entity
     * @throws C8EntityNotFoundException if no entity is found
     */
    public T findOneOrThrow(IdKeyMapper.TYPE entityType, Long c8Key) {
      return querySupplier.get()
          .stream()
          .findFirst()
          .orElseThrow(() -> new C8EntityNotFoundException(entityType, c8Key));
    }

  }
  /**
   * Checks if a user or group with the given ID exists in C8.
   */
  public boolean ownerExists(String userId, String groupId) {
    Object userOrGroup = null;
    try {
      if (isNotBlank(userId)) {
        userOrGroup = getUser(userId);
      } else if (isNotBlank(groupId)) {
        userOrGroup = getGroup(groupId);
      }
      return userOrGroup != null;
    } catch (MigratorException e) {
      if (e.getCause() instanceof ProblemException pe && pe.details().getStatus() == 404) { // Not found
        return false;
      } else {
        throw new IdentityMigratorException("Cannot verify owner existence: " + userOrGroup, e);
      }
    }
  }

  /**
   * Checks if a tenant with the given ID exists in C8.
   */
  public boolean tenantExists(String tenantId) {
    try {
      return getTenant(tenantId) != null;
    } catch (MigratorException e) {
      if (e.getCause() instanceof ProblemException pe && pe.details().getStatus() == 404) { // Not found
        return false;
      } else {
        throw new IdentityMigratorException("Cannot verify tenant existence: " + tenantId, e);
      }
    }
  }
}
