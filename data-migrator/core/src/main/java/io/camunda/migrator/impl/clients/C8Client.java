/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.clients;

import static io.camunda.migrator.constants.MigratorConstants.C8_DEFAULT_TENANT;
import static io.camunda.migrator.impl.logging.C8ClientLogs.FAILED_TO_ACTIVATE_JOBS;
import static io.camunda.migrator.impl.logging.C8ClientLogs.FAILED_TO_CREATE_PROCESS_INSTANCE;
import static io.camunda.migrator.impl.logging.C8ClientLogs.FAILED_TO_DEPLOY_C8_RESOURCES;
import static io.camunda.migrator.impl.logging.C8ClientLogs.FAILED_TO_FETCH_PROCESS_DEFINITION_XML;
import static io.camunda.migrator.impl.logging.C8ClientLogs.FAILED_TO_FETCH_VARIABLE;
import static io.camunda.migrator.impl.logging.C8ClientLogs.FAILED_TO_MODIFY_PROCESS_INSTANCE;
import static io.camunda.migrator.impl.logging.C8ClientLogs.FAILED_TO_SEARCH_PROCESS_DEFINITIONS;
import static io.camunda.migrator.impl.util.ConverterUtil.getTenantId;
import static io.camunda.migrator.impl.util.ExceptionUtils.callApi;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.db.rdbms.read.domain.DecisionDefinitionDbQuery;
import io.camunda.db.rdbms.read.domain.DecisionInstanceDbQuery;
import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessInstanceDbQuery;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.model.FlowNodeActivation;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
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
    callApi(() -> processDefinitionMapper.insert(dbModel), "Failed to insert process definition");
  }

  /**
   * Inserts a ProcessInstance into the database.
   */
  public void insertProcessInstance(ProcessInstanceDbModel dbModel) {
    callApi(() -> processInstanceMapper.insert(dbModel), "Failed to insert process instance");
  }

  /**
   * Finds a ProcessInstance by key.
   */
  public ProcessInstanceEntity findProcessInstance(Long key) {
    return callApi(() -> processInstanceMapper.findOne(key), "Failed to find process instance by key: " + key);
  }

  /**
   * Searches for ProcessInstances matching the query.
   */
  public List<ProcessInstanceEntity> searchProcessInstances(ProcessInstanceDbQuery query) {
    return callApi(() -> processInstanceMapper.search(query), "Failed to search process instances");
  }

  /**
   * Inserts a DecisionRequirementsDefinition into the database.
   */
  public void insertDecisionRequirements(DecisionRequirementsDbModel dbModel) {
    callApi(() -> decisionRequirementsMapper.insert(dbModel), "Failed to insert decision requirements");
  }

  /**
   * Inserts a DecisionDefinition into the database.
   */
  public void insertDecisionDefinition(DecisionDefinitionDbModel dbModel) {
    callApi(() -> decisionDefinitionMapper.insert(dbModel), "Failed to insert decision definition");
  }

  /**
   * Searches for DecisionDefinitions matching the query.
   */
  public List<DecisionDefinitionEntity> searchDecisionDefinitions(DecisionDefinitionDbQuery query) {
    return callApi(() -> decisionDefinitionMapper.search(query), "Failed to search decision definitions");
  }

  /**
   * Inserts a DecisionInstance into the database.
   */
  public void insertDecisionInstance(DecisionInstanceDbModel dbModel) {
    callApi(() -> decisionInstanceMapper.insert(dbModel), "Failed to insert decision instance");
  }

  /**
   * Searches for DecisionInstances matching the query.
   */
  public List<DecisionInstanceEntity> searchDecisionInstances(DecisionInstanceDbQuery query) {
    return callApi(() -> decisionInstanceMapper.search(query), "Failed to search decision instances");
  }

  /**
   * Inserts an Incident into the database.
   */
  public void insertIncident(IncidentDbModel dbModel) {
    callApi(() -> incidentMapper.insert(dbModel), "Failed to insert incident");
  }

  /**
   * Inserts a Variable into the database.
   */
  public void insertVariable(VariableDbModel dbModel) {
    callApi(() -> variableMapper.insert(dbModel), "Failed to insert variable");
  }

  /**
   * Inserts a UserTask into the database.
   */
  public void insertUserTask(UserTaskDbModel dbModel) {
    callApi(() -> userTaskMapper.insert(dbModel), "Failed to insert user task");
  }

  /**
   * Inserts a FlowNodeInstance into the database.
   */
  public void insertFlowNodeInstance(FlowNodeInstanceDbModel dbModel) {
    callApi(() -> flowNodeInstanceMapper.insert(dbModel), "Failed to insert flow node instance");
  }

  /**
   * Searches for FlowNodeInstances matching the query.
   */
  public List<FlowNodeInstanceDbModel> searchFlowNodeInstances(FlowNodeInstanceDbQuery query) {
    return callApi(() -> flowNodeInstanceMapper.search(query), "Failed to search flow node instances");
  }

  /**
   * Searches for ProcessDefinitions matching the query.
   */
  public List<ProcessDefinitionEntity> searchProcessDefinitions(ProcessDefinitionDbQuery query) {
    return callApi(() -> processDefinitionMapper.search(query), "Failed to search process definitions");
  }

}
