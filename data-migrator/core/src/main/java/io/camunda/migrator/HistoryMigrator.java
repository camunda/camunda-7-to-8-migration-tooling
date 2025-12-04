/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import static io.camunda.migrator.MigratorMode.LIST_SKIPPED;
import static io.camunda.migrator.MigratorMode.MIGRATE;
import static io.camunda.migrator.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_INCIDENT;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_USER_TASK;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.HISTORY_VARIABLE;
import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.SKIP_REASON_BELONGS_TO_SKIPPED_TASK;
import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FLOW_NODE;
import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE;
import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE_KEY;
import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_SCOPE_KEY;
import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_DECISION_REQUIREMENTS;
import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_DECISION_DEFINITION;
import static io.camunda.migrator.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_DECISION_INSTANCE;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.getHistoryTypes;

import io.camunda.db.rdbms.read.domain.DecisionDefinitionDbQuery;
import io.camunda.db.rdbms.read.domain.DecisionInstanceDbQuery;
import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessInstanceDbQuery;
import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.domain.DecisionRequirementsDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.migrator.config.C8DataSourceConfigured;
import io.camunda.migrator.converter.DecisionDefinitionConverter;
import io.camunda.migrator.converter.DecisionInstanceConverter;
import io.camunda.migrator.converter.DecisionRequirementsDefinitionConverter;
import io.camunda.migrator.converter.IncidentConverter;
import io.camunda.migrator.impl.EntityConversionService;
import io.camunda.migrator.impl.clients.C7Client;
import io.camunda.migrator.impl.clients.C8Client;
import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.impl.logging.HistoryMigratorLogs;
import io.camunda.migrator.impl.util.ExceptionUtils;
import io.camunda.migrator.impl.util.PrintUtils;
import io.camunda.migrator.interceptor.property.EntityConversionContext;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinition;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(C8DataSourceConfigured.class)
public class HistoryMigrator {

  // Clients

  @Autowired
  protected DbClient dbClient;

  @Autowired
  protected C7Client c7Client;

  @Autowired
  protected C8Client c8Client;

  @Autowired
  protected EntityConversionService entityConversionService;

  @Autowired
  protected ProcessEngine processEngine;

  // Converters

  @Autowired
  protected DecisionInstanceConverter decisionInstanceConverter;

  @Autowired
  protected IncidentConverter incidentConverter;

  @Autowired
  protected DecisionDefinitionConverter decisionDefinitionConverter;

  @Autowired
  protected DecisionRequirementsDefinitionConverter decisionRequirementsConverter;

  protected MigratorMode mode = MIGRATE;

  protected List<TYPE> requestedEntityTypes;

  public void start() {
    try {
      ExceptionUtils.setContext(ExceptionUtils.ExceptionContext.HISTORY);
      if (LIST_SKIPPED.equals(mode)) {
        printSkippedHistoryEntities();
      } else {
        migrate();
      }
    } finally {
      ExceptionUtils.clearContext();
    }
  }

  protected void printSkippedHistoryEntities() {
    if(requestedEntityTypes == null ||  requestedEntityTypes.isEmpty()) {
      getHistoryTypes().forEach(this::printSkippedEntitiesForType);
    } else {
      requestedEntityTypes.forEach(this::printSkippedEntitiesForType);
    }
  }

  protected void printSkippedEntitiesForType(TYPE type) {
    PrintUtils.printSkippedInstancesHeader(dbClient.countSkippedByType(type), type);
    dbClient.listSkippedEntitiesByType(type);
  }

  public void migrate() {
    migrateProcessDefinitions();
    migrateProcessInstances();
    migrateFlowNodes();
    migrateUserTasks();
    migrateVariables();
    migrateIncidents();
    migrateDecisionRequirementsDefinitions();
    migrateDecisionDefinitions();
    migrateDecisionInstances();
  }

  public void migrateProcessDefinitions() {
    HistoryMigratorLogs.migratingProcessDefinitions();
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_PROCESS_DEFINITION, idKeyDbModel -> {
        ProcessDefinition historicProcessDefinition = c7Client.getProcessDefinition(idKeyDbModel.getC7Id());
        migrateProcessDefinition(historicProcessDefinition);
      });
    } else {
      c7Client.fetchAndHandleProcessDefinitions(this::migrateProcessDefinition, dbClient.findLatestCreateTimeByType((HISTORY_PROCESS_DEFINITION)));
    }
  }

  protected void migrateProcessDefinition(ProcessDefinition c7ProcessDefinition) {
    String c7Id = c7ProcessDefinition.getId();
    if (shouldMigrate(c7Id, HISTORY_PROCESS_DEFINITION)) {
      HistoryMigratorLogs.migratingProcessDefinition(c7Id);
      ProcessDefinitionDbModel dbModel = convertProcessDefinition(c7ProcessDefinition); // TODO there's no skipping mechanism here yet, add try catch  if convertion fails with an exception
      c8Client.insertProcessDefinition(dbModel);
      Date deploymentTime = c7Client.getDefinitionDeploymentTime(c7ProcessDefinition.getDeploymentId());
      markMigrated(c7Id, dbModel.processDefinitionKey(), deploymentTime, HISTORY_PROCESS_DEFINITION);
      HistoryMigratorLogs.migratingProcessDefinitionCompleted(c7Id);
    }
  }

  protected ProcessDefinitionDbModel convertProcessDefinition(ProcessDefinition c7ProcessDefinition) {
    EntityConversionContext<?, ?> context = new EntityConversionContext<>(c7ProcessDefinition, ProcessDefinition.class,
        new ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder(), processEngine);
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder builder = (ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  public void migrateProcessInstances() {
    HistoryMigratorLogs.migratingProcessInstances();
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_PROCESS_INSTANCE, idKeyDbModel -> {
        HistoricProcessInstance historicProcessInstance = c7Client.getHistoricProcessInstance(idKeyDbModel.getC7Id());
        migrateProcessInstance(historicProcessInstance);
      });
    } else {
      c7Client.fetchAndHandleHistoricProcessInstances(this::migrateProcessInstance, dbClient.findLatestCreateTimeByType((HISTORY_PROCESS_INSTANCE)));
    }
  }

  protected void migrateProcessInstance(HistoricProcessInstance c7ProcessInstance) {
    String c7ProcessInstanceId = c7ProcessInstance.getId();
    if (shouldMigrate(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
      HistoryMigratorLogs.migratingProcessInstance(c7ProcessInstanceId);
      Long processDefinitionKey = findProcessDefinitionKey(c7ProcessInstance.getProcessDefinitionId());
      String processDefinitionId = c7ProcessInstance.getProcessDefinitionId();

      ProcessInstanceDbModel.ProcessInstanceDbModelBuilder processInstanceDbModelBuilder = new ProcessInstanceDbModel.ProcessInstanceDbModelBuilder();
      EntityConversionContext<?, ?> context = new EntityConversionContext<>(c7ProcessInstance,
          HistoricProcessInstance.class, processInstanceDbModelBuilder, processEngine);

      entityConversionService.prepareParentProperties(context);

      if (isMigrated(processDefinitionId, HISTORY_PROCESS_DEFINITION)) {
        String c7SuperProcessInstanceId = c7ProcessInstance.getSuperProcessInstanceId();
        Long parentProcessInstanceKey = null;
        if (c7SuperProcessInstanceId != null) {
          ProcessInstanceEntity parentInstance = findProcessInstanceByC7Id(c7SuperProcessInstanceId);
          if (parentInstance != null) {
            parentProcessInstanceKey = parentInstance.processInstanceKey();
          }
        }
        processInstanceDbModelBuilder
            .processDefinitionKey(processDefinitionKey);
        if (parentProcessInstanceKey != null || c7SuperProcessInstanceId == null) {

          processInstanceDbModelBuilder
              .parentProcessInstanceKey(parentProcessInstanceKey);
          ProcessInstanceDbModel dbModel = convertProcessInstance(context);

          insertProcessInstance(c7ProcessInstance, dbModel, c7ProcessInstanceId);
        } else {
          ProcessInstanceDbModel dbModel = convertProcessInstance(context);
          if (dbModel.parentProcessInstanceKey() == null) {
            markSkipped(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(),
                SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE);
            HistoryMigratorLogs.skippingProcessInstanceDueToMissingParent(c7ProcessInstanceId);
          } else {
            insertProcessInstance(c7ProcessInstance, dbModel, c7ProcessInstanceId);
          }
        }
      } else {
        ProcessInstanceDbModel dbModel = convertProcessInstance(context);
        if (dbModel.parentProcessInstanceKey() == null) {
          markSkipped(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(),
              SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE);
          HistoryMigratorLogs.skippingProcessInstanceDueToMissingParent(c7ProcessInstanceId);
        } else if (dbModel.processDefinitionKey() == null) {
          markSkipped(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(),
              SKIP_REASON_MISSING_PROCESS_DEFINITION);
          HistoryMigratorLogs.skippingProcessInstanceDueToMissingDefinition(c7ProcessInstanceId);
        } else {
          insertProcessInstance(c7ProcessInstance, dbModel, c7ProcessInstanceId);
        }
      }
    }
  }

  protected ProcessInstanceDbModel convertProcessInstance(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    ProcessInstanceDbModel.ProcessInstanceDbModelBuilder builder = (ProcessInstanceDbModel.ProcessInstanceDbModelBuilder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  protected void insertProcessInstance(HistoricProcessInstance c7ProcessInstance,
                                       ProcessInstanceDbModel dbModel,
                                       String c7ProcessInstanceId) {
    c8Client.insertProcessInstance(dbModel);
    markMigrated(c7ProcessInstanceId, dbModel.processInstanceKey(), c7ProcessInstance.getStartTime(), HISTORY_PROCESS_INSTANCE);
    HistoryMigratorLogs.migratingProcessInstanceCompleted(c7ProcessInstanceId);
  }

  public void migrateDecisionRequirementsDefinitions() {
    HistoryMigratorLogs.migratingDecisionRequirements();

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_DECISION_REQUIREMENT, idKeyDbModel -> {
        DecisionRequirementsDefinition c7DecisionRequirement = c7Client.getDecisionRequirementsDefinition(
            idKeyDbModel.getC7Id());
        migrateDecisionRequirementsDefinition(c7DecisionRequirement);
      });
    } else {
      c7Client.fetchAndHandleDecisionRequirementsDefinitions(this::migrateDecisionRequirementsDefinition);
    }
  }

  protected void migrateDecisionRequirementsDefinition(DecisionRequirementsDefinition c7DecisionRequirements) {
    String c7Id = c7DecisionRequirements.getId();
    if (shouldMigrate(c7Id, HISTORY_DECISION_REQUIREMENT)) {
      HistoryMigratorLogs.migratingDecisionRequirements(c7Id);
      DecisionRequirementsDbModel dbModel = decisionRequirementsConverter.apply(c7DecisionRequirements);
      c8Client.insertDecisionRequirements(dbModel);
      Date deploymentTime = c7Client.getDefinitionDeploymentTime(c7DecisionRequirements.getDeploymentId());
      markMigrated(c7Id, dbModel.decisionRequirementsKey(), deploymentTime, HISTORY_DECISION_REQUIREMENT);
      HistoryMigratorLogs.migratingDecisionRequirementsCompleted(c7Id);
    }
  }

  public void migrateDecisionDefinitions() {
    HistoryMigratorLogs.migratingDecisionDefinitions();

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_DECISION_DEFINITION, idKeyDbModel -> {
        DecisionDefinition c7DecisionDefinition = c7Client.getDecisionDefinition(idKeyDbModel.getC7Id());
        migrateDecisionDefinition(c7DecisionDefinition);
      });
    } else {
      c7Client.fetchAndHandleDecisionDefinitions(this::migrateDecisionDefinition,
          dbClient.findLatestCreateTimeByType((HISTORY_DECISION_DEFINITION)));
    }
  }

  protected void migrateDecisionDefinition(DecisionDefinition c7DecisionDefinition) {
    String c7Id = c7DecisionDefinition.getId();
    if (shouldMigrate(c7Id, HISTORY_DECISION_DEFINITION)) {
      HistoryMigratorLogs.migratingDecisionDefinition(c7Id);
      Long decisionRequirementsKey = null;

      Date deploymentTime = c7Client.getDefinitionDeploymentTime(c7DecisionDefinition.getDeploymentId());

      if (c7DecisionDefinition.getDecisionRequirementsDefinitionId() != null) {
        decisionRequirementsKey = dbClient.findC8KeyByC7IdAndType(c7DecisionDefinition.getDecisionRequirementsDefinitionId(),
            HISTORY_DECISION_REQUIREMENT);

        if (decisionRequirementsKey == null) {
          markSkipped(c7Id, HISTORY_DECISION_DEFINITION, deploymentTime, SKIP_REASON_MISSING_DECISION_REQUIREMENTS);
          HistoryMigratorLogs.skippingDecisionDefinition(c7Id);
          return;
        }
      }

      DecisionDefinitionDbModel dbModel = decisionDefinitionConverter.apply(c7DecisionDefinition, decisionRequirementsKey);
      c8Client.insertDecisionDefinition(dbModel);
      markMigrated(c7Id, dbModel.decisionDefinitionKey(), deploymentTime, HISTORY_DECISION_DEFINITION);
      HistoryMigratorLogs.migratingDecisionDefinitionCompleted(c7Id);
    }
  }

  public void migrateDecisionInstances() {
    HistoryMigratorLogs.migratingDecisionInstances();
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_DECISION_INSTANCE, idKeyDbModel -> {
        HistoricDecisionInstance historicDecisionInstance = c7Client.getHistoricDecisionInstance(idKeyDbModel.getC7Id());
        migrateDecisionInstance(historicDecisionInstance);
      });
    } else {
      c7Client.fetchAndHandleHistoricDecisionInstances(this::migrateDecisionInstance,
          dbClient.findLatestCreateTimeByType((HISTORY_DECISION_INSTANCE)));
    }
  }

  protected void migrateDecisionInstance(HistoricDecisionInstance c7DecisionInstance) {
    if (c7DecisionInstance.getProcessDefinitionKey() == null) {
      // only migrate decision instances that were triggered by process definitions
      HistoryMigratorLogs.notMigratingDecisionInstancesNotOriginatingFromBusinessRuleTasks(c7DecisionInstance.getId());
      return;
    }

    String c7DecisionInstanceId = c7DecisionInstance.getId();
    if (shouldMigrate(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE)) {
      HistoryMigratorLogs.migratingDecisionInstance(c7DecisionInstanceId);

      if (!isMigrated(c7DecisionInstance.getDecisionDefinitionId(), HISTORY_DECISION_DEFINITION)) {
        markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(), SKIP_REASON_MISSING_DECISION_DEFINITION);
        HistoryMigratorLogs.skippingDecisionInstanceDueToMissingDecisionDefinition(c7DecisionInstanceId);
        return;
      }

      if (!isMigrated(c7DecisionInstance.getProcessDefinitionId(), HISTORY_PROCESS_DEFINITION)) {
        markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(), SKIP_REASON_MISSING_PROCESS_DEFINITION);
        HistoryMigratorLogs.skippingDecisionInstanceDueToMissingProcessDefinition(c7DecisionInstanceId);
        return;
      }

      if (!isMigrated(c7DecisionInstance.getProcessInstanceId(), HISTORY_PROCESS_INSTANCE)) {
        markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(), SKIP_REASON_MISSING_PROCESS_INSTANCE);
        HistoryMigratorLogs.skippingDecisionInstanceDueToMissingProcessInstance(c7DecisionInstanceId);
        return;
      }

      String c7RootDecisionInstanceId = c7DecisionInstance.getRootDecisionInstanceId();
      Long parentDecisionDefinitionKey = null;
      if (c7RootDecisionInstanceId != null) {
        if (!isMigrated(c7RootDecisionInstanceId, HISTORY_DECISION_INSTANCE)) {
          markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(), SKIP_REASON_MISSING_PARENT_DECISION_INSTANCE);
          HistoryMigratorLogs.skippingDecisionInstanceDueToMissingParent(c7DecisionInstanceId);
          return;
        }
        parentDecisionDefinitionKey = findDecisionInstance(c7RootDecisionInstanceId).decisionDefinitionKey();
      }

      if (!isMigrated(c7DecisionInstance.getActivityInstanceId(), HISTORY_FLOW_NODE)) {
        markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(), SKIP_REASON_MISSING_FLOW_NODE);
        HistoryMigratorLogs.skippingDecisionInstanceDueToMissingFlowNodeInstanceInstance(c7DecisionInstanceId);
        return;
      }

      DecisionDefinitionEntity decisionDefinition = findDecisionDefinition(
          c7DecisionInstance.getDecisionDefinitionId());
      Long processDefinitionKey = findProcessDefinitionKey(c7DecisionInstance.getProcessDefinitionId());
      Long processInstanceKey = findProcessInstanceByC7Id(
          c7DecisionInstance.getProcessInstanceId()).processInstanceKey();
      FlowNodeInstanceDbModel flowNode = findFlowNodeInstance(c7DecisionInstance.getActivityInstanceId());

      DecisionInstanceDbModel dbModel = decisionInstanceConverter.apply(c7DecisionInstance,
          decisionDefinition.decisionDefinitionKey(), processDefinitionKey,
          decisionDefinition.decisionRequirementsKey(), processInstanceKey, parentDecisionDefinitionKey,
          flowNode.flowNodeInstanceKey(), flowNode.flowNodeId());
      c8Client.insertDecisionInstance(dbModel);
      markMigrated(c7DecisionInstanceId, dbModel.decisionInstanceKey(), c7DecisionInstance.getEvaluationTime(), HISTORY_DECISION_INSTANCE);
      HistoryMigratorLogs.migratingDecisionInstanceCompleted(c7DecisionInstanceId);
    }
  }

  public void migrateIncidents() {
    HistoryMigratorLogs.migratingHistoricIncidents();
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_INCIDENT, idKeyDbModel -> {
        HistoricIncident historicIncident = c7Client.getHistoricIncident(idKeyDbModel.getC7Id());
        migrateIncident(historicIncident);
      });
    } else {
      c7Client.fetchAndHandleHistoricIncidents(this::migrateIncident, dbClient.findLatestCreateTimeByType((HISTORY_INCIDENT)));
    }
  }

  protected void migrateIncident(HistoricIncident c7Incident) {
    String c7IncidentId = c7Incident.getId();
    if (shouldMigrate(c7IncidentId, HISTORY_INCIDENT)) {
      HistoryMigratorLogs.migratingHistoricIncident(c7IncidentId);
      ProcessInstanceEntity c7ProcessInstance = findProcessInstanceByC7Id(c7Incident.getProcessInstanceId());
      if (c7ProcessInstance != null) {
        Long processInstanceKey = c7ProcessInstance.processInstanceKey();
        if (processInstanceKey != null) {
          Long flowNodeInstanceKey = findFlowNodeInstanceKey(c7Incident.getActivityId(), c7Incident.getProcessInstanceId());
          Long processDefinitionKey = findProcessDefinitionKey(c7Incident.getProcessDefinitionId());
          Long jobDefinitionKey = null; // TODO Job table doesn't exist yet.
          IncidentDbModel dbModel = incidentConverter.apply(c7Incident, processDefinitionKey, processInstanceKey, jobDefinitionKey, flowNodeInstanceKey);
          c8Client.insertIncident(dbModel);
          markMigrated(c7IncidentId, dbModel.incidentKey(), c7Incident.getCreateTime(), HISTORY_INCIDENT);
          HistoryMigratorLogs.migratingHistoricIncidentCompleted(c7IncidentId);
        } else {
          markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(), SKIP_REASON_MISSING_PROCESS_INSTANCE_KEY);
          HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
        }
      } else {
        markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(), SKIP_REASON_MISSING_PROCESS_INSTANCE);
        HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
      }
    }
  }

  public void migrateVariables() {/**
   * Migrates a historic variable instance from Camunda 7 to Camunda 8.
   *
   * Variables can be scoped to either:
   * - A user task (taskId != null)
   * - A process instance with optional activity instance scope
   *
   * The method validates that all parent entities (process instance, task, activity instance)
   * have been migrated before attempting to migrate the variable.
   *
   * @param c7Variable the historic variable instance from Camunda 7
   */
    HistoryMigratorLogs.migratingHistoricVariables();

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_VARIABLE, idKeyDbModel -> {
        HistoricVariableInstance historicVariableInstance = c7Client.getHistoricVariableInstance(idKeyDbModel.getC7Id());
        migrateVariable(historicVariableInstance);
      });
    } else {
      c7Client.fetchAndHandleHistoricVariables(this::migrateVariable, dbClient.findLatestCreateTimeByType(HISTORY_VARIABLE));
    }
  }

  /**
   * Migrates a historic variable instance from Camunda 7 to Camunda 8.
   *
   * Variables can be scoped to either:
   * - A user task (taskId != null)
   * - A process instance with optional activity instance scope
   *
   * The method validates that all parent entities (process instance, task, activity instance)
   * have been migrated before attempting to migrate the variable.
   *
   * @param c7Variable the historic variable instance from Camunda 7
   */
  protected void migrateVariable(HistoricVariableInstance c7Variable) {
    String c7VariableId = c7Variable.getId();
    if (shouldMigrate(c7VariableId, HISTORY_VARIABLE)) {
      HistoryMigratorLogs.migratingHistoricVariable(c7VariableId);
      VariableDbModel.VariableDbModelBuilder variableDbModelBuilder =
          new VariableDbModel.VariableDbModelBuilder();
      EntityConversionContext<?, ?> context = new EntityConversionContext<>(c7Variable,
          HistoricVariableInstance.class, variableDbModelBuilder, processEngine);

      entityConversionService.prepareParentProperties(context);

      // Handle task-scoped variables
      String taskId = c7Variable.getTaskId();
      if (taskId != null) {
        if (!isMigrated(taskId, HISTORY_USER_TASK)) {
          markSkipped(c7VariableId, TYPE.HISTORY_VARIABLE, c7Variable.getCreateTime(), SKIP_REASON_BELONGS_TO_SKIPPED_TASK);
          HistoryMigratorLogs.skippingHistoricVariableDueToMissingTask(c7VariableId, taskId);
          return;
        }
        processVariableConversion(c7Variable, context, c7VariableId);
        return;
      }

      // Handle process-scoped variables
      String c7ProcessInstanceId = c7Variable.getProcessInstanceId();
      if (!isMigrated(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
        processVariableConversion(c7Variable, context, c7VariableId);
        return;
      }

      // Process instance exists, prepare keys
      ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7ProcessInstanceId);
      Long processInstanceKey = processInstance.processInstanceKey();
      variableDbModelBuilder.processInstanceKey(processInstanceKey);

      // Check if activity instance is migrated
      String activityInstanceId = c7Variable.getActivityInstanceId();
      if (isMigrated(activityInstanceId, HISTORY_FLOW_NODE) ||
          isMigrated(activityInstanceId, HISTORY_PROCESS_INSTANCE)) {
        Long scopeKey = findScopeKey(activityInstanceId);
        if (scopeKey != null) {
          variableDbModelBuilder.scopeKey(scopeKey);
        }
      }

      processVariableConversion(c7Variable, context, c7VariableId);
    }
  }

  private void processVariableConversion(HistoricVariableInstance c7Variable,
                                         EntityConversionContext<?, ?> context,
                                         String c7VariableId) {
    VariableDbModel dbModel = convertVariable(context);

    if (dbModel.processInstanceKey() == null) {
      markSkipped(c7VariableId, TYPE.HISTORY_VARIABLE, c7Variable.getCreateTime(), SKIP_REASON_MISSING_PROCESS_INSTANCE);
      HistoryMigratorLogs.skippingHistoricVariableDueToMissingProcessInstance(c7VariableId);
    } else if (dbModel.scopeKey() == null) {
      String skipReason = c7Variable.getTaskId() != null
          ? SKIP_REASON_BELONGS_TO_SKIPPED_TASK
          : SKIP_REASON_MISSING_SCOPE_KEY;
      markSkipped(c7VariableId, TYPE.HISTORY_VARIABLE, c7Variable.getCreateTime(), skipReason);

      if (c7Variable.getTaskId() != null) {
        HistoryMigratorLogs.skippingHistoricVariableDueToMissingScopeKey(c7VariableId);
      } else {
        HistoryMigratorLogs.skippingHistoricVariableDueToMissingFlowNode(c7VariableId);
      }
    } else {
      insertVariable(c7Variable, dbModel, c7VariableId);
    }
  }
  protected void insertVariable(HistoricVariableInstance c7Variable, VariableDbModel dbModel, String c7VariableId) {
    c8Client.insertVariable(dbModel);
    markMigrated(c7VariableId, dbModel.variableKey(), c7Variable.getCreateTime(), HISTORY_VARIABLE);
    HistoryMigratorLogs.migratingHistoricVariableCompleted(c7VariableId);
  }

  protected VariableDbModel convertVariable(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    VariableDbModel.VariableDbModelBuilder builder =
        (VariableDbModel.VariableDbModelBuilder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  public void migrateUserTasks() {
    HistoryMigratorLogs.migratingHistoricUserTasks();

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_USER_TASK, idKeyDbModel -> {
        HistoricTaskInstance historicTaskInstance = c7Client.getHistoricTaskInstance(idKeyDbModel.getC7Id());
        migrateUserTask(historicTaskInstance);
      });
    } else {
      c7Client.fetchAndHandleHistoricUserTasks(this::migrateUserTask, dbClient.findLatestCreateTimeByType((HISTORY_USER_TASK)));
    }
  }

  protected void migrateUserTask(HistoricTaskInstance c7UserTask) {
    String c7UserTaskId = c7UserTask.getId();
    if (shouldMigrate(c7UserTaskId, HISTORY_USER_TASK)) {
      HistoryMigratorLogs.migratingHistoricUserTask(c7UserTaskId);

      UserTaskDbModel.Builder userTaskDbModelBuilder = new UserTaskDbModel.Builder();
      EntityConversionContext<?, ?> context = new EntityConversionContext<>(c7UserTask,
          HistoricTaskInstance.class, userTaskDbModelBuilder, processEngine);

      entityConversionService.prepareParentProperties(context);

      if (isMigrated(c7UserTask.getProcessInstanceId(), HISTORY_PROCESS_INSTANCE)) {

        ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7UserTask.getProcessInstanceId());
        userTaskDbModelBuilder
            .processInstanceKey(processInstance.processInstanceKey())
            .processDefinitionVersion(processInstance.processDefinitionVersion());
        if (isMigrated(c7UserTask.getActivityInstanceId(), HISTORY_FLOW_NODE)) {
          Long elementInstanceKey = findFlowNodeInstanceKey(c7UserTask.getActivityInstanceId());
          Long processDefinitionKey = findProcessDefinitionKey(c7UserTask.getProcessDefinitionId());

          userTaskDbModelBuilder.processDefinitionKey(processDefinitionKey)
              .processInstanceKey(processInstance.processInstanceKey())
              .elementInstanceKey(elementInstanceKey)
              .processDefinitionVersion(processInstance.processDefinitionVersion());

          UserTaskDbModel dbModel = convertUserTask(context);
          insertUserTask(c7UserTask, dbModel, c7UserTaskId);
        } else {
          UserTaskDbModel dbModel = convertUserTask(context);
          if (dbModel.elementInstanceKey() == null || dbModel.processDefinitionKey() == null) {
            markSkipped(c7UserTaskId, TYPE.HISTORY_USER_TASK, c7UserTask.getStartTime(), SKIP_REASON_MISSING_FLOW_NODE);
            HistoryMigratorLogs.skippingHistoricUserTaskDueToMissingFlowNode(c7UserTaskId);
          } else {
            insertUserTask(c7UserTask, dbModel, c7UserTaskId);
          }
        }
      } else {
        UserTaskDbModel dbModel = convertUserTask(context);
        if (dbModel.processInstanceKey() == null || dbModel.processDefinitionVersion() == null) {
          markSkipped(c7UserTaskId, TYPE.HISTORY_USER_TASK, c7UserTask.getStartTime(),
              SKIP_REASON_MISSING_PROCESS_INSTANCE);
          HistoryMigratorLogs.skippingHistoricUserTaskDueToMissingProcessInstance(c7UserTaskId);
        } else if (dbModel.elementInstanceKey() == null || dbModel.processDefinitionKey() == null) {
          markSkipped(c7UserTaskId, TYPE.HISTORY_USER_TASK, c7UserTask.getStartTime(), SKIP_REASON_MISSING_FLOW_NODE);
          HistoryMigratorLogs.skippingHistoricUserTaskDueToMissingFlowNode(c7UserTaskId);
        } else {
          insertUserTask(c7UserTask, dbModel, c7UserTaskId);
        }
      }
    }
  }

  protected UserTaskDbModel convertUserTask(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    UserTaskDbModel.Builder builder = (UserTaskDbModel.Builder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  protected void insertUserTask(HistoricTaskInstance c7UserTask, UserTaskDbModel dbModel, String c7UserTaskId) {
    c8Client.insertUserTask(dbModel);
    markMigrated(c7UserTaskId, dbModel.userTaskKey(), c7UserTask.getStartTime(), HISTORY_USER_TASK);
    HistoryMigratorLogs.migratingHistoricUserTaskCompleted(c7UserTaskId);
  }

  public void migrateFlowNodes() {
    HistoryMigratorLogs.migratingHistoricFlowNodes();

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(HISTORY_FLOW_NODE, idKeyDbModel -> {
        HistoricActivityInstance historicActivityInstance = c7Client.getHistoricActivityInstance(idKeyDbModel.getC7Id());
        migrateFlowNode(historicActivityInstance);
      });
    } else {
      c7Client.fetchAndHandleHistoricFlowNodes(this::migrateFlowNode, dbClient.findLatestCreateTimeByType((HISTORY_FLOW_NODE)));
    }
  }

  protected void migrateFlowNode(HistoricActivityInstance c7FlowNode) {
    String c7FlowNodeId = c7FlowNode.getId();
    if (shouldMigrate(c7FlowNodeId, HISTORY_FLOW_NODE)) {
      HistoryMigratorLogs.migratingHistoricFlowNode(c7FlowNodeId);
      ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7FlowNode.getProcessInstanceId());
      FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder flowNodeDbModelBuilder =
          new FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder();
      EntityConversionContext<?, ?> context = new EntityConversionContext<>(c7FlowNode,
          HistoricActivityInstance.class, flowNodeDbModelBuilder, processEngine);

      entityConversionService.prepareParentProperties(context);

      if (processInstance != null) {
        Long processInstanceKey = processInstance.processInstanceKey();
        Long processDefinitionKey = findProcessDefinitionKey(c7FlowNode.getProcessDefinitionId());

        flowNodeDbModelBuilder.processInstanceKey(processInstanceKey)
            .processDefinitionKey(processDefinitionKey);

        FlowNodeInstanceDbModel dbModel = convertFlowNode(context);
        insertFlowNodeInstance(c7FlowNode, dbModel, c7FlowNodeId);
      } else {
        FlowNodeInstanceDbModel dbModel = convertFlowNode(context);
        if (dbModel.processInstanceKey() == null) {
          markSkipped(c7FlowNodeId, HISTORY_FLOW_NODE, c7FlowNode.getStartTime(), SKIP_REASON_MISSING_PROCESS_INSTANCE);
          HistoryMigratorLogs.skippingHistoricFlowNode(c7FlowNodeId);
        }
        else {
          insertFlowNodeInstance(c7FlowNode, dbModel, c7FlowNodeId);
        }
      }
    }
  }

  protected void insertFlowNodeInstance(HistoricActivityInstance c7FlowNode, FlowNodeInstanceDbModel dbModel,
                                String c7FlowNodeId) {
    c8Client.insertFlowNodeInstance(dbModel);
    markMigrated(c7FlowNodeId, dbModel.flowNodeInstanceKey(), c7FlowNode.getStartTime(), HISTORY_FLOW_NODE);
    HistoryMigratorLogs.migratingHistoricFlowNodeCompleted(c7FlowNodeId);
  }

  protected FlowNodeInstanceDbModel convertFlowNode(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder builder =
        (FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  protected ProcessInstanceEntity findProcessInstanceByC7Id(String processInstanceId) {
    if (processInstanceId == null)
      return null;

    Long c8Key = dbClient.findC8KeyByC7IdAndType(processInstanceId, HISTORY_PROCESS_INSTANCE);
    if (c8Key == null) {
      return null;
    }

    return c8Client.findProcessInstance(c8Key);
  }

  protected DecisionInstanceEntity findDecisionInstance(String decisionInstanceId) {
    if (decisionInstanceId == null)
      return null;

    Long key = dbClient.findC8KeyByC7IdAndType(decisionInstanceId, HISTORY_DECISION_INSTANCE);
    if (key == null) {
      return null;
    }

    return c8Client.searchDecisionInstances(
            DecisionInstanceDbQuery.of(b -> b.filter(value -> value.decisionInstanceKeys(key))))
        .stream()
        .findFirst()
        .orElse(null);
  }

  protected DecisionDefinitionEntity findDecisionDefinition(String decisionDefinitionId) {
    Long key = dbClient.findC8KeyByC7IdAndType(decisionDefinitionId, HISTORY_DECISION_DEFINITION);
    if (key == null) {
      return null;
    }

    return c8Client.searchDecisionDefinitions(
            DecisionDefinitionDbQuery.of(b -> b.filter(value -> value.decisionDefinitionKeys(key))))
        .stream()
        .findFirst()
        .orElse(null);
  }

  protected Long findProcessDefinitionKey(String processDefinitionId) {
    Long key = dbClient.findC8KeyByC7IdAndType(processDefinitionId, HISTORY_PROCESS_DEFINITION);
    if (key == null) {
      return null;
    }

    List<ProcessDefinitionEntity> processDefinitions = c8Client.searchProcessDefinitions(
        ProcessDefinitionDbQuery.of(b -> b.filter(value -> value.processDefinitionKeys(key))));

    if (!processDefinitions.isEmpty()) {
      return processDefinitions.getFirst().processDefinitionKey();
    } else {
      return null;
    }
  }

  protected Long findFlowNodeInstanceKey(String activityId, String processInstanceId) {
    Long key = dbClient.findC8KeyByC7IdAndType(processInstanceId, HISTORY_PROCESS_INSTANCE);
    if (key == null) {
      return null;
    }

    List<FlowNodeInstanceDbModel> flowNodes = c8Client.searchFlowNodeInstances(FlowNodeInstanceDbQuery.of(
        b -> b.filter(FlowNodeInstanceFilter.of(f -> f.flowNodeIds(activityId).flowNodeInstanceKeys(key)))));

    if (!flowNodes.isEmpty()) {
      return flowNodes.getFirst().flowNodeInstanceKey();
    } else {
      return null;
    }
  }

  protected Long findFlowNodeInstanceKey(String activityInstanceId) {
    return Optional.ofNullable(findFlowNodeInstance(activityInstanceId))
        .map(FlowNodeInstanceDbModel::flowNodeInstanceKey)
        .orElse(null);
  }

  protected FlowNodeInstanceDbModel findFlowNodeInstance(String activityInstanceId) {
    Long key = dbClient.findC8KeyByC7IdAndType(activityInstanceId, HISTORY_FLOW_NODE);
    if (key == null) {
      return null;
    }

    return c8Client.searchFlowNodeInstances(FlowNodeInstanceDbQuery.of(b -> b.filter(f -> f.flowNodeInstanceKeys(key))))
        .stream()
        .findFirst()
        .orElse(null);
  }

  protected Long findScopeKey(String instanceId) {
    Long key = findFlowNodeInstanceKey(instanceId);
    if (key != null) {
      return key;
    }

    Long processInstanceKey = dbClient.findC8KeyByC7IdAndType(instanceId, HISTORY_PROCESS_INSTANCE);
    if (processInstanceKey == null) {
      return null;
    }

    List<ProcessInstanceEntity> processInstances = c8Client.searchProcessInstances(
        ProcessInstanceDbQuery.of(b -> b.filter(value -> value.processInstanceKeys(processInstanceKey))));
    return processInstances.isEmpty() ? null : processInstanceKey;
  }

  protected boolean isMigrated(String id, TYPE type) {
    return dbClient.checkHasC8KeyByC7IdAndType(id, type);
  }

  protected boolean shouldMigrate(String id, TYPE type) {
    if (mode == RETRY_SKIPPED) {
      return !dbClient.checkHasC8KeyByC7IdAndType(id, type);
    }
    return !dbClient.checkExistsByC7IdAndType(id, type);
  }

  protected void markMigrated(String c7Id, Long c8Key, Date createTime, TYPE type) {
    saveRecord(c7Id, c8Key, type, createTime, null);
  }

  protected void markSkipped(String c7Id, TYPE type, Date createTime, String skipReason) {
    saveRecord(c7Id, null, type, createTime, skipReason);
  }

  protected void saveRecord(String c7Id, Long c8Key, TYPE type, Date createTime, String skipReason) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateC8KeyByC7IdAndType(c7Id, c8Key, type);
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(c7Id, c8Key, createTime, type, skipReason);

    }
  }

  public void setMode(MigratorMode mode) {
    this.mode = mode;
  }

  public void setRequestedEntityTypes(List<TYPE> requestedEntityTypes) {
    this.requestedEntityTypes = requestedEntityTypes;
  }

}
