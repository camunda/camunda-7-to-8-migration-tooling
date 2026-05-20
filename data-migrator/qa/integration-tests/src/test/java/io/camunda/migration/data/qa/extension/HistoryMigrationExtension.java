/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data.qa.extension;

import static io.camunda.migration.data.impl.util.ConverterUtil.prefixDefinitionId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.db.rdbms.write.service.RdbmsPurger;
import io.camunda.migration.data.HistoryMigrator;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.qa.util.SpringProfileResolver;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * JUnit extension that provides history migration testing capabilities.
 * Manages cleanup of C7, C8, and migrator state after each test.
 *
 * This extension is a Spring component that gets injected with required beans.
 * It can be used as a field in test classes with {@literal @}RegisterExtension.
 *
 * Usage:
 * <pre>
 * {@literal @}RegisterExtension
 * protected final HistoryMigrationExtension historyMigration = new HistoryMigrationExtension();
 * </pre>
 */
@Component
public class HistoryMigrationExtension implements BeforeEachCallback, AfterEachCallback, ApplicationContextAware {

  public static final String USER_TASK_ID = "userTaskId";

  private static ApplicationContext applicationContext;

  @Override
  public void setApplicationContext(ApplicationContext context) throws BeansException {
    HistoryMigrationExtension.applicationContext = context;
  }

  private HistoryMigrator getHistoryMigratorBean() {
    return applicationContext != null ? applicationContext.getBean(HistoryMigrator.class) : null;
  }

  private RdbmsPurger getRdbmsPurgerBean() {
    return applicationContext != null ? applicationContext.getBean(RdbmsPurger.class) : null;
  }

  private static <T> T requireBean(Class<T> beanClass) {
    if (applicationContext == null) {
      throw new IllegalStateException(
          beanClass.getSimpleName() + " is not available in the Spring context");
    }
    return applicationContext.getBean(beanClass);
  }

  private HistoryService getHistoryServiceBean() {
    return applicationContext != null ? applicationContext.getBean(HistoryService.class) : null;
  }

  private RepositoryService getRepositoryServiceBean() {
    return applicationContext != null ? applicationContext.getBean(RepositoryService.class) : null;
  }

  private TaskService getTaskServiceBean() {
    return applicationContext != null ? applicationContext.getBean(TaskService.class) : null;
  }

  private DbClient getDbClientBean() {
    return applicationContext != null ? applicationContext.getBean(DbClient.class) : null;
  }

  /**
   * MySQL doesn't support millisecond precision by default, so we need to truncate
   * milliseconds to 0 when running tests against MySQL to avoid timing comparison issues.
   */
  @Override
  public void beforeEach(ExtensionContext context) {
    if (isMySqlActive()) {
      ClockUtil.setCurrentTime(truncateMilliseconds(new Date()));
    }
  }

  @Override
  public void afterEach(ExtensionContext context) {
    ClockUtil.reset();

    // Get ApplicationContext from Spring's ExtensionContext store if not already set
    if (applicationContext == null) {
      applicationContext = SpringExtension.getApplicationContext(context);
    }

    RepositoryService repositoryService = getRepositoryServiceBean();
    if (repositoryService != null) {
      // C7
      repositoryService.createDeploymentQuery().list()
          .forEach(d -> repositoryService.deleteDeployment(d.getId(), true));
    }

    DbClient dbClient = getDbClientBean();
    if (dbClient != null) {
      // Migrator
      dbClient.deleteAllMappings();
    }

    RdbmsPurger rdbmsPurger = getRdbmsPurgerBean();
    if (rdbmsPurger != null) {
      // C8
      rdbmsPurger.purgeRdbms();
    }
  }


  // Helper methods

  public HistoryMigrator getMigrator() {
    return getHistoryMigratorBean();
  }

  public HistoryService getHistoryService() {
    return getHistoryServiceBean();
  }

  public DbClient getDbClient() {
    return getDbClientBean();
  }

  public List<ProcessDefinitionEntity> searchHistoricProcessDefinitions(String processDefinitionId) {
    return requireBean(CamundaClient.class).newProcessDefinitionSearchRequest()
        .filter(f -> f.processDefinitionId(prefixDefinitionId(processDefinitionId)))
        .execute()
        .items()
        .stream()
        .map(this::toProcessDefinitionEntity)
        .toList();
  }

  public List<DecisionDefinitionEntity> searchHistoricDecisionDefinitions(String decisionDefinitionId) {
    return requireBean(CamundaClient.class).newDecisionDefinitionSearchRequest()
        .filter(f -> f.decisionDefinitionId(prefixDefinitionId(decisionDefinitionId)))
        .execute()
        .items()
        .stream()
        .map(this::toDecisionDefinitionEntity)
        .toList();
  }

  public List<DecisionRequirementsEntity> searchHistoricDecisionRequirementsDefinition(String decisionRequirementsId) {
    return requireBean(CamundaClient.class).newDecisionRequirementsSearchRequest()
        .filter(f -> f.decisionRequirementsId(prefixDefinitionId(decisionRequirementsId)))
        .execute()
        .items()
        .stream()
        .map(this::toDecisionRequirementsEntity)
        .toList();
  }

  public List<ProcessInstanceEntity> searchHistoricProcessInstances(String processDefinitionId) {
    return requireBean(CamundaClient.class).newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(prefixDefinitionId(processDefinitionId)))
        .execute()
        .items()
        .stream()
        .map(this::toProcessInstanceEntity)
        .toList();
  }

  public List<DecisionInstanceEntity> searchHistoricDecisionInstances(String decisionDefinitionId) {
    String prefixedDecisionDefinitionId = prefixDefinitionId(decisionDefinitionId);
    return requireBean(CamundaClient.class).newDecisionInstanceSearchRequest()
        .execute()
        .items()
        .stream()
        .filter(instance -> prefixedDecisionDefinitionId.equals(instance.getDecisionDefinitionId()))
        .map(this::toDecisionInstanceEntity)
        .toList();
  }

  public List<UserTaskEntity> searchHistoricUserTasks(long processInstanceKey) {
    return requireBean(CamundaClient.class).newUserTaskSearchRequest()
        .filter(f -> f.processInstanceKey(processInstanceKey))
        .execute()
        .items()
        .stream()
        .map(this::toUserTaskEntity)
        .toList();
  }

  public List<FlowNodeInstanceEntity> searchHistoricFlowNodesForType(long processInstanceKey, FlowNodeInstanceEntity.FlowNodeType type) {
    var mappedType = mapEnum(io.camunda.client.api.search.enums.ElementInstanceType.class, type.name());
    return new ArrayList<>(requireBean(CamundaClient.class).newElementInstanceSearchRequest()
        .filter(f -> {
          f.processInstanceKey(processInstanceKey);
          if (mappedType != null) {
            f.type(mappedType);
          }
        })
        .execute()
        .items()
        .stream()
        .map(this::toFlowNodeInstanceEntity)
        .toList());
  }

  public List<FlowNodeInstanceEntity> searchHistoricFlowNodesById(String... flowNodeIds) {
    Set<String> flowNodeIdsSet = Arrays.stream(flowNodeIds).collect(Collectors.toSet());
    return requireBean(CamundaClient.class).newElementInstanceSearchRequest()
        .execute()
        .items()
        .stream()
        .filter(elementInstance -> flowNodeIdsSet.contains(elementInstance.getElementId()))
        .map(this::toFlowNodeInstanceEntity)
        .toList();
  }

  public List<IncidentEntity> searchHistoricIncidents(String processDefinitionId) {
    return requireBean(CamundaClient.class).newIncidentSearchRequest()
        .filter(f -> f.processDefinitionId(prefixDefinitionId(processDefinitionId)))
        .execute()
        .items()
        .stream()
        .map(this::toIncidentEntity)
        .toList();
  }

  public List<VariableEntity> searchHistoricVariables(String... varName) {
    Set<String> variableNames = Arrays.stream(varName).collect(Collectors.toSet());
    return requireBean(CamundaClient.class).newVariableSearchRequest()
        .execute()
        .items()
        .stream()
        .filter(variable -> variableNames.isEmpty() || variableNames.contains(variable.getName()))
        .map(this::toVariableEntity)
        .toList();
  }

  private ProcessDefinitionEntity toProcessDefinitionEntity(
      io.camunda.client.api.search.response.ProcessDefinition processDefinition) {
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
      io.camunda.client.api.search.response.DecisionRequirements decisionRequirements) {
    return new DecisionRequirementsEntity(
        decisionRequirements.getDecisionRequirementsKey(),
        decisionRequirements.getDmnDecisionRequirementsId(),
        decisionRequirements.getDmnDecisionRequirementsName(),
        decisionRequirements.getVersion(),
        decisionRequirements.getResourceName(),
        null,
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
            .map(input -> new DecisionInstanceEntity.DecisionInstanceInputEntity(
                input.getInputId(),
                input.getInputName(),
                input.getInputValue()))
            .toList(),
        decisionInstance.getMatchedRules()
            .stream()
            .flatMap(rule -> rule.getEvaluatedOutputs()
                .stream()
                .map(output -> new DecisionInstanceEntity.DecisionInstanceOutputEntity(
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

  public void completeAllUserTasksWithDefaultUserTaskId() {
    TaskService taskService = getTaskServiceBean();
    if (taskService == null) {
      throw new IllegalStateException("TaskService is not available in the Spring context");
    }
    for (Task task : taskService.createTaskQuery().taskDefinitionKey(USER_TASK_ID).list()) {
      taskService.complete(task.getId());
    }
  }

  public void assertVariableExists(String varName, String expectedValue) {
    List<VariableEntity> variables = searchHistoricVariables(varName);
    assertThat(variables).hasSize(1);
    VariableEntity variable = variables.getFirst();
    assertThat(variable.name()).isEqualTo(varName);
    assertThat(variable.value()).isEqualTo(expectedValue);
  }

  /**
   * Checks if MySQL is the active database profile.
   */
  private static boolean isMySqlActive() {
    return SpringProfileResolver.getActiveProfiles().contains("mysql");
  }

  /**
   * Truncates milliseconds from a Date, setting them to 0.
   * This is needed for MySQL which doesn't support millisecond precision by default.
   */
  private static Date truncateMilliseconds(Date date) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTime();
  }

}
