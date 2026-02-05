/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.impl.clients;

import static io.camunda.migration.data.impl.logging.C7ClientLogs.FAILED_TO_FETCH_ACTIVITY_INSTANCE;
import static io.camunda.migration.data.impl.logging.C7ClientLogs.FAILED_TO_FETCH_AUTHORIZATIONS;
import static io.camunda.migration.data.impl.logging.C7ClientLogs.FAILED_TO_FETCH_BPMN_XML;
import static io.camunda.migration.data.impl.logging.C7ClientLogs.FAILED_TO_FETCH_DEPLOYMENT_TIME;
import static io.camunda.migration.data.impl.logging.C7ClientLogs.FAILED_TO_FETCH_DMN_XML;
import static io.camunda.migration.data.impl.logging.C7ClientLogs.FAILED_TO_FETCH_HISTORIC_ELEMENT;
import static io.camunda.migration.data.impl.logging.C7ClientLogs.FAILED_TO_FETCH_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.C7ClientLogs.FAILED_TO_FETCH_TENANTS;
import static io.camunda.migration.data.impl.util.ExceptionUtils.callApi;
import static java.lang.String.format;

import io.camunda.migration.data.config.property.MigratorProperties;
import io.camunda.migration.data.impl.Pagination;
import io.camunda.migration.data.impl.persistence.IdKeyDbModel;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.AuthorizationQuery;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.TenantQuery;
import org.camunda.bpm.engine.impl.AuthorizationQueryImpl;
import org.camunda.bpm.engine.impl.HistoricActivityInstanceQueryImpl;
import org.camunda.bpm.engine.impl.HistoricDecisionInstanceQueryImpl;
import org.camunda.bpm.engine.impl.HistoricIncidentQueryImpl;
import org.camunda.bpm.engine.impl.HistoricProcessInstanceQueryImpl;
import org.camunda.bpm.engine.impl.HistoricTaskInstanceQueryImpl;
import org.camunda.bpm.engine.impl.HistoricVariableInstanceQueryImpl;
import org.camunda.bpm.engine.impl.UserOperationLogQueryImpl;
import org.camunda.bpm.engine.impl.ProcessDefinitionQueryImpl;
import org.camunda.bpm.engine.impl.TenantQueryImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
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
import org.camunda.bpm.model.dmn.DmnModelInstance;
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

  @Autowired
  protected IdentityService identityService;

  @Autowired
  protected AuthorizationService authorizationService;

  @Autowired
  protected ProcessEngineConfigurationImpl processEngineConfiguration;

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
    var query = historyService.createHistoricDecisionInstanceQuery()
        .includeInputs()
        .includeOutputs()
        .disableCustomObjectDeserialization()
        .decisionInstanceId(c7Id);

    return callApi(query::singleResult, format(FAILED_TO_FETCH_HISTORIC_ELEMENT, "HistoricDecisionInstance", c7Id));
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
    var query = historyService.createHistoricVariableInstanceQuery()
        .disableCustomObjectDeserialization()
        .variableId(c7Id);
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
   * Gets a resource as string by ID and name.
   */
  public String getResourceAsString(String resourceId, String resourceName) {
    return callApi(() -> new String(processEngineConfiguration.getCommandExecutorTxRequiresNew()
        .execute(commandContext -> commandContext.getResourceManager()
        .findResourceByDeploymentIdAndResourceName(resourceId, resourceName)
        .getBytes()), Charset.defaultCharset()));
  }

  /**
   * Gets a resource as string by ID and name.
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
   * Gets the DMN model instance by decision definition ID.
   */
  public DmnModelInstance getDmnModelInstance(String decisionDefinitionId) {
    return callApi(() -> repositoryService.getDmnModelInstance(decisionDefinitionId),
        FAILED_TO_FETCH_DMN_XML + decisionDefinitionId);
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
        .disableCustomObjectDeserialization()
        .rootDecisionInstancesOnly()
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
        .disableCustomObjectDeserialization()
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

  /**
   * Processes historic user operation log entries with pagination using the provided callback consumer.
   */
  public void fetchAndHandleUserOperationLogEntries(Consumer<UserOperationLogEntry> callback, Date timestampAfter) {
    UserOperationLogQueryImpl query = (UserOperationLogQueryImpl) historyService.createUserOperationLogQuery()
        .orderByTimestamp()
        .asc()
//        .orderbByOperationId()
//        .asc()
        ;

    if (timestampAfter != null) {
      query.afterTimestamp(timestampAfter);
    }

    new Pagination<UserOperationLogEntry>()
        .pageSize(properties.getPageSize())
        .query(query)
        .maxCount(query::count)
        .callback(callback);
  }

  /**
   * Gets a single user operation log entry by ID.
   */
  public UserOperationLogEntry getUserOperationLogEntry(String c7Id) {
    var query = historyService.createUserOperationLogQuery().operationId(c7Id);
    return callApi(query::singleResult, format(FAILED_TO_FETCH_HISTORIC_ELEMENT, "UserOperationLogEntry", c7Id));
  }

  /**
   * Processes tenant entities with pagination using the provided callback consumer.
   */
  public void fetchAndHandleTenants(Consumer<Tenant> callback, String idAfter) {
    TenantQueryImpl query = (TenantQueryImpl) ((TenantQueryImpl) identityService
        .createTenantQuery())
        .idAfter(idAfter)
        .orderByTenantId()
        .asc();

    new Pagination<Tenant>()
        .pageSize(properties.getPageSize())
        .query(query)
        .maxCount(query::count)
        .callback(callback);
  }

  /**
   * Gets a single tenant by ID.
   */
  public Tenant getTenant(String tenantId) {
    TenantQuery query = identityService.createTenantQuery().tenantId(tenantId);
    return callApi(query::singleResult, FAILED_TO_FETCH_TENANTS);
  }

  /**
   * Processes authorization entities with pagination using the provided callback consumer.
   */
  public void fetchAndHandleAuthorizations(Consumer<Authorization> callback, String idAfter) {
    AuthorizationQueryImpl query = (AuthorizationQueryImpl) ((AuthorizationQueryImpl) (((AuthorizationQueryImpl) authorizationService
        .createAuthorizationQuery()))
        .idAfter(idAfter))
        .orderByAuthorizationId()
        .asc();

    new Pagination<Authorization>()
        .pageSize(properties.getPageSize())
        .query(query)
        .maxCount(query::count)
        .callback(callback);
  }

  /**
   * Gets a single authorization by ID.
   */
  public Authorization getAuthorization(String authorizationId) {
    AuthorizationQuery query = authorizationService.createAuthorizationQuery().authorizationId(authorizationId);
    return callApi(query::singleResult, FAILED_TO_FETCH_AUTHORIZATIONS);
  }

  public List<HistoricDecisionInstance> findChildDecisionInstances(String rootDecisionInstanceId) {
    HistoricDecisionInstanceQueryImpl query = (HistoricDecisionInstanceQueryImpl) historyService.createHistoricDecisionInstanceQuery()
        .rootDecisionInstanceId(rootDecisionInstanceId)
        .includeInputs()
        .includeOutputs()
        .disableCustomObjectDeserialization()
        .orderByEvaluationTime()
        .asc()
        .orderByDecisionInstanceId()
        .asc();

    return query.list().stream()
        .filter(decisionInstance -> decisionInstance.getRootDecisionInstanceId() != null)
        .collect(Collectors.toList());
  }

}
