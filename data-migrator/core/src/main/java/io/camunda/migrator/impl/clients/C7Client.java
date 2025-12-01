/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.clients;

import static io.camunda.migrator.impl.logging.C7ClientLogs.FAILED_TO_FETCH_ACTIVITY_INSTANCE;
import static io.camunda.migrator.impl.logging.C7ClientLogs.FAILED_TO_FETCH_BPMN_XML;
import static io.camunda.migrator.impl.logging.C7ClientLogs.FAILED_TO_FETCH_DEPLOYMENT_TIME;
import static io.camunda.migrator.impl.logging.C7ClientLogs.FAILED_TO_FETCH_HISTORIC_ELEMENT;
import static io.camunda.migrator.impl.logging.C7ClientLogs.FAILED_TO_FETCH_PROCESS_INSTANCE;
import static io.camunda.migrator.impl.util.ExceptionUtils.callApi;
import static java.lang.String.format;

import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.Pagination;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.impl.HistoricActivityInstanceQueryImpl;
import org.camunda.bpm.engine.impl.HistoricDecisionInstanceQueryImpl;
import org.camunda.bpm.engine.impl.HistoricIncidentQueryImpl;
import org.camunda.bpm.engine.impl.HistoricProcessInstanceQueryImpl;
import org.camunda.bpm.engine.impl.HistoricTaskInstanceQueryImpl;
import org.camunda.bpm.engine.impl.HistoricVariableInstanceQueryImpl;
import org.camunda.bpm.engine.impl.ProcessDefinitionQueryImpl;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.repository.DecisionDefinitionQuery;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinition;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinitionQuery;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
import org.camunda.bpm.engine.runtime.VariableInstance;
import org.camunda.bpm.engine.runtime.VariableInstanceQuery;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class C7Client {

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected HistoryService historyService;

  @Autowired
  protected RepositoryService repositoryService;

  @Autowired
  protected MigratorProperties properties;

  @Autowired
  protected ApplicationContext context;

  /**
   * Gets a single process instance by ID.
   */
  public ProcessInstance getProcessInstance(String processInstanceId) {
    var query = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId);
    return callApi(query::singleResult, FAILED_TO_FETCH_PROCESS_INSTANCE + processInstanceId);
  }

  /**
   * Gets a single process definition by ID.
   */
  public ProcessDefinition getProcessDefinition(String c7Id) {
    var query = repositoryService.createProcessDefinitionQuery().processDefinitionId(c7Id);
    return callApi(query::singleResult, format(FAILED_TO_FETCH_HISTORIC_ELEMENT, "ProcessDefinition", c7Id));
  }

  /**
   * Gets a single decision requirements definition by ID.
   */
  public DecisionRequirementsDefinition getDecisionRequirementsDefinition(String c7Id) {
    var query = repositoryService.createDecisionRequirementsDefinitionQuery()
        .decisionRequirementsDefinitionId(c7Id);
    return callApi(query::singleResult,
        format(FAILED_TO_FETCH_HISTORIC_ELEMENT, "DecisionRequirementsDefinition", c7Id));
  }

  /**
   * Gets a single decision definition by ID.
   */
  public DecisionDefinition getDecisionDefinition(String c7Id) {
    var query = repositoryService.createDecisionDefinitionQuery().decisionDefinitionId(c7Id);
    return callApi(query::singleResult, format(FAILED_TO_FETCH_HISTORIC_ELEMENT, "DecisionDefinition", c7Id));
  }

  /**
   * Gets a single historic decision instance by ID.
   */
  public HistoricDecisionInstance getHistoricDecisionInstance(String c7Id) {
    var query = historyService.createHistoricDecisionInstanceQuery().decisionInstanceId(c7Id);
    return callApi(query::singleResult, format(FAILED_TO_FETCH_HISTORIC_ELEMENT, "HistoricDecisionInstance", c7Id));
  }

  /**
   * Gets a single historic decision instance by ID.
   */
  public HistoricDecisionInstance getHistoricDecisionInstanceByDefinitionKey(String definitionKey) {
    var query = historyService.createHistoricDecisionInstanceQuery().decisionDefinitionKey(definitionKey);
    return callApi(query::singleResult, format(FAILED_TO_FETCH_HISTORIC_ELEMENT, "HistoricDecisionInstance", definitionKey));
  }

  /**
   * Gets a single historic process instance by ID.
   */
  public HistoricProcessInstance getHistoricProcessInstance(String c7Id) {
    var query = historyService.createHistoricProcessInstanceQuery().processInstanceId(c7Id);
    return callApi(query::singleResult, format(FAILED_TO_FETCH_HISTORIC_ELEMENT, "HistoricProcessInstance", c7Id));
  }

  /**
   * Gets a single historic activity instance by ID.
   */
  public HistoricActivityInstance getHistoricActivityInstance(String c7Id) {
    var query = historyService.createHistoricActivityInstanceQuery().activityInstanceId(c7Id);
    return callApi(query::singleResult, format(FAILED_TO_FETCH_HISTORIC_ELEMENT, "HistoricActivityInstance", c7Id));
  }

  /**
   * Gets a single historic task instance by ID.
   */
  public HistoricTaskInstance getHistoricTaskInstance(String c7Id) {
    var query = historyService.createHistoricTaskInstanceQuery().taskId(c7Id);
    return callApi(query::singleResult, format(FAILED_TO_FETCH_HISTORIC_ELEMENT, "HistoricTaskInstance", c7Id));
  }

  /**
   * Gets a single historic variable instance by ID.
   */
  public HistoricVariableInstance getHistoricVariableInstance(String c7Id) {
    var query = historyService.createHistoricVariableInstanceQuery().variableId(c7Id);
    return callApi(query::singleResult, format(FAILED_TO_FETCH_HISTORIC_ELEMENT, "HistoricVariableInstance", c7Id));
  }

  /**
   * Gets a single historic incident by ID.
   */
  public HistoricIncident getHistoricIncident(String c7Id) {
    var query = historyService.createHistoricIncidentQuery().incidentId(c7Id);
    return callApi(query::singleResult, format(FAILED_TO_FETCH_HISTORIC_ELEMENT, "HistoricIncident", c7Id));
  }

  /**
   * Gets the activity instance tree for a process instance.
   */
  public ActivityInstance getActivityInstance(String processInstanceId) {
    return callApi(() -> runtimeService.getActivityInstance(processInstanceId),
        FAILED_TO_FETCH_ACTIVITY_INSTANCE + processInstanceId);
  }

  /**
   * Gets all variables for a process instance with pagination and variable transformation.
   */
  public List<VariableInstance> getAllVariables(String c7ProcessInstanceId) {
    VariableInstanceQuery variableQuery = runtimeService.createVariableInstanceQuery()
        .disableCustomObjectDeserialization()
        .processInstanceIdIn(c7ProcessInstanceId);

    return new Pagination<VariableInstance>()
        .pageSize(properties.getPageSize())
        .query(variableQuery)
        .toList();
  }

  /**
   * Gets local variables for an activity instance with pagination and variable transformation.
   */
  public List<VariableInstance> getLocalVariables(String activityInstanceId) {
    VariableInstanceQuery variableQuery = runtimeService.createVariableInstanceQuery()
        .disableCustomObjectDeserialization()
        .activityInstanceIdIn(activityInstanceId);

    return new Pagination<VariableInstance>()
        .pageSize(properties.getPageSize())
        .query(variableQuery)
        .toList();
  }

  /**
   * Gets a resource as steam by ID and name.
   */
  public InputStream getResourceAsStream(String resourceId, String resourceName) {
    return callApi(() -> repositoryService.getResourceAsStream(resourceId, resourceName));
  }

  /**
   * Gets the BPMN model instance by process definition ID.
   */
  public BpmnModelInstance getBpmnModelInstance(String processDefinitionId) {
    return callApi(() -> repositoryService.getBpmnModelInstance(processDefinitionId),
        FAILED_TO_FETCH_BPMN_XML + processDefinitionId);
  }

  /**
   * Gets the definition deployment time by definition deployment ID.
   */
  public Date getDefinitionDeploymentTime(String definitionDeploymentId) {
    var query = repositoryService.createDeploymentQuery().deploymentId(definitionDeploymentId);
    return callApi(query::singleResult,
        FAILED_TO_FETCH_DEPLOYMENT_TIME + definitionDeploymentId).getDeploymentTime();
  }

  /**
   * Processes process instances for a given root process instance ID with pagination.
   */
  public void fetchAndHandleProcessInstances(Consumer<ProcessInstance> callback, String rootProcessInstanceId) {
    ProcessInstanceQuery query = runtimeService.createProcessInstanceQuery()
        .rootProcessInstanceId(rootProcessInstanceId);

    new Pagination<ProcessInstance>()
        .pageSize(properties.getPageSize())
        .maxCount(query::count)
        .query(query)
        .callback(callback);
  }

  /**
   * Processes historic root process instances with pagination using the provided callback consumer.
   */
  public void fetchAndHandleHistoricRootProcessInstances(Consumer<IdKeyDbModel> callback, Date startedAfter) {
    var query = historyService.createHistoricProcessInstanceQuery()
        .startedAfter(startedAfter)
        .rootProcessInstances()
        .unfinished()
        .orderByProcessInstanceStartTime()
        .asc()
        // Ensure order is predictable with two order criteria:
        // Without second criteria and PIs have same start time, order is non-deterministic.
        .orderByProcessInstanceId()
        .asc();

    new Pagination<IdKeyDbModel>()
        .pageSize(properties.getPageSize())
        .maxCount(query::count)
        .page(offset -> query.listPage(offset, properties.getPageSize())
            .stream()
            .map(hpi -> new IdKeyDbModel(hpi.getId(), hpi.getStartTime()))
            .collect(Collectors.toList()))
        .callback(callback);
  }

  /**
   * Processes historic process instances with pagination using the provided callback consumer.
   */
  public void fetchAndHandleHistoricProcessInstances(Consumer<HistoricProcessInstance> callback, Date startedAfter) {
    HistoricProcessInstanceQueryImpl query = (HistoricProcessInstanceQueryImpl) historyService.createHistoricProcessInstanceQuery()
        .orderByProcessInstanceStartTime()
        .asc()
        .orderByProcessInstanceId()
        .asc();

    if (startedAfter != null) {
      query.startedAfter(startedAfter);
    }

    new Pagination<HistoricProcessInstance>()
        .pageSize(properties.getPageSize())
        .query(query)
        .maxCount(query::count)
        .callback(callback);
  }

  /**
   * Processes historic decision instances with pagination using the provided callback consumer.
   */
  public void fetchAndHandleHistoricDecisionInstances(Consumer<HistoricDecisionInstance> callback, Date evaluatedAfter) {
    HistoricDecisionInstanceQueryImpl query = (HistoricDecisionInstanceQueryImpl) historyService.createHistoricDecisionInstanceQuery()
        .includeInputs()
        .includeOutputs()
        .orderByEvaluationTime()
        .asc()
        .orderByDecisionInstanceId()
        .asc();

    if (evaluatedAfter != null) {
      query.evaluatedAfter(evaluatedAfter);
    }

    new Pagination<HistoricDecisionInstance>()
        .pageSize(properties.getPageSize())
        .query(query)
        .maxCount(query::count)
        .callback(callback);
  }

  /**
   * Processes process definitions with pagination using the provided callback consumer.
   */
  public void fetchAndHandleProcessDefinitions(Consumer<ProcessDefinition> callback, Date deployedAfter) {
    ProcessDefinitionQueryImpl query = (ProcessDefinitionQueryImpl) repositoryService.createProcessDefinitionQuery()
        .orderByDeploymentTime()
        .asc()
        .orderByProcessDefinitionId()
        .asc();

    if (deployedAfter != null) {
      query.deployedAfter(deployedAfter);
    }

    fetchAndHandleProcessDefinitions(query, callback);
  }

  /**
   * Processes process definitions with pagination using the provided callback consumer.
   */
  public void fetchAndHandleProcessDefinitions(Consumer<ProcessDefinition> callback, String[] ids) {
    ProcessDefinitionQueryImpl query = (ProcessDefinitionQueryImpl) repositoryService.createProcessDefinitionQuery()
        .orderByDeploymentTime()
        .processDefinitionIdIn(ids);

    fetchAndHandleProcessDefinitions(query, callback);
  }

  /**
   * Processes process definitions with pagination using the provided callback consumer.
   */
  public void fetchAndHandleProcessDefinitions(ProcessDefinitionQueryImpl query, Consumer<ProcessDefinition> callback) {
    new Pagination<ProcessDefinition>()
        .pageSize(properties.getPageSize())
        .query(query)
        .maxCount(query::count)
        .callback(callback);
  }

  /**
   * Processes decision definitions with pagination using the provided callback consumer.
   */
  public void fetchAndHandleDecisionDefinitions(Consumer<DecisionDefinition> callback, Date deployedAfter) {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery()
        .orderByDeploymentTime()
        .asc()
        .orderByDecisionDefinitionId()
        .asc();

    if (deployedAfter != null) {
      query.deployedAfter(deployedAfter);
    }

    new Pagination<DecisionDefinition>()
        .pageSize(properties.getPageSize())
        .query(query)
        .maxCount(query::count)
        .callback(callback);
  }

  /**
   * Processes decision requirements with pagination using the provided callback consumer.
   */
  public void fetchAndHandleDecisionRequirementsDefinitions(Consumer<DecisionRequirementsDefinition> callback) {
    DecisionRequirementsDefinitionQuery query = repositoryService.createDecisionRequirementsDefinitionQuery()
        .orderByDecisionRequirementsDefinitionId()
        .asc();

    new Pagination<DecisionRequirementsDefinition>().pageSize(properties.getPageSize())
        .query(query)
        .maxCount(query::count)
        .callback(callback);
  }

  /**
   * Processes historic incidents with pagination using the provided callback consumer.
   */
  public void fetchAndHandleHistoricIncidents(Consumer<HistoricIncident> callback, Date createdAfter) {
    HistoricIncidentQueryImpl query = (HistoricIncidentQueryImpl) historyService.createHistoricIncidentQuery()
        .orderByCreateTime()
        .asc()
        .orderByIncidentId()
        .asc();

    if (createdAfter != null) {
      query.createTimeAfter(createdAfter);
    }

    new Pagination<HistoricIncident>()
        .pageSize(properties.getPageSize())
        .query(query)
        .maxCount(query::count)
        .callback(callback);
  }

  /**
   * Processes variables with pagination using the provided callback consumer.
   */
  public void fetchAndHandleHistoricVariables(Consumer<HistoricVariableInstance> callback, Date createdAfter) {
    HistoricVariableInstanceQueryImpl query = (HistoricVariableInstanceQueryImpl) historyService.createHistoricVariableInstanceQuery()
        .orderByCreationTime()
        .asc()
        .orderByVariableId()
        .asc();

    if (createdAfter != null) {
      query.createdAfter(createdAfter);
    }

    new Pagination<HistoricVariableInstance>()
        .pageSize(properties.getPageSize())
        .query(query)
        .maxCount(query::count)
        .callback(callback);
  }

  /**
   * Processes historic user task instances with pagination using the provided callback consumer.
   */
  public void fetchAndHandleHistoricUserTasks(Consumer<HistoricTaskInstance> callback, Date startedAfter) {
    HistoricTaskInstanceQueryImpl query = (HistoricTaskInstanceQueryImpl) historyService.createHistoricTaskInstanceQuery()
        .orderByHistoricActivityInstanceStartTime()
        .asc()
        .orderByTaskId()
        .asc();

    if (startedAfter != null) {
      query.startedAfter(startedAfter);
    }

    new Pagination<HistoricTaskInstance>()
        .pageSize(properties.getPageSize())
        .query(query)
        .maxCount(query::count)
        .callback(callback);
  }

  /**
   * Processes historic flow node instances with pagination using the provided callback consumer.
   */
  public void fetchAndHandleHistoricFlowNodes(Consumer<HistoricActivityInstance> callback, Date startedAfter) {
    HistoricActivityInstanceQueryImpl query = (HistoricActivityInstanceQueryImpl) historyService.createHistoricActivityInstanceQuery()
        .orderByHistoricActivityInstanceStartTime()
        .asc()
        .orderByHistoricActivityInstanceId()
        .asc();

    if (startedAfter != null) {
      query.startedAfter(startedAfter);
    }

    new Pagination<HistoricActivityInstance>()
        .pageSize(properties.getPageSize())
        .query(query)
        .maxCount(query::count)
        .callback(callback);
  }

}
