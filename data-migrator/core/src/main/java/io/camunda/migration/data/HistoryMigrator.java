/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.data;

import static io.camunda.migration.data.MigratorMode.LIST_SKIPPED;
import static io.camunda.migration.data.MigratorMode.MIGRATE;
import static io.camunda.migration.data.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_BELONGS_TO_SKIPPED_TASK;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_DECISION_REQUIREMENTS;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_FLOW_NODE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_JOB_REFERENCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_DECISION_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_PROCESS_INSTANCE_KEY;
import static io.camunda.migration.data.impl.logging.HistoryMigratorLogs.SKIP_REASON_MISSING_SCOPE_KEY;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_DECISION_REQUIREMENT;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_FLOW_NODE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_INCIDENT;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_USER_TASK;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.TYPE.HISTORY_VARIABLE;
import static io.camunda.migration.data.impl.persistence.IdKeyMapper.getHistoryTypes;
import static io.camunda.migration.data.impl.util.ConverterUtil.getNextKey;
import static io.camunda.migration.data.impl.util.ConverterUtil.getTenantId;

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
import io.camunda.migration.data.config.C8DataSourceConfigured;
import io.camunda.migration.data.exception.EntityInterceptorException;
import io.camunda.migration.data.exception.MigratorException;
import io.camunda.migration.data.exception.VariableInterceptorException;
import io.camunda.migration.data.impl.EntityConversionService;
import io.camunda.migration.data.impl.clients.C7Client;
import io.camunda.migration.data.impl.clients.C8Client;
import io.camunda.migration.data.impl.clients.DbClient;
import io.camunda.migration.data.impl.logging.HistoryMigratorLogs;
import io.camunda.migration.data.impl.util.ExceptionUtils;
import io.camunda.migration.data.impl.util.PrintUtils;
import io.camunda.migration.data.interceptor.property.EntityConversionContext;
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
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.LiteralExpression;
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

  /**
   * Migrates a process definition from Camunda 7 to Camunda 8.
   *
   * <p>Process definitions describe the structure and behavior of business processes.
   * This method converts the C7 process definition to C8 format and inserts it into the C8 database.
   *
   * @param c7ProcessDefinition the process definition from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  protected void migrateProcessDefinition(ProcessDefinition c7ProcessDefinition) {
    String c7Id = c7ProcessDefinition.getId();
    if (shouldMigrate(c7Id, HISTORY_PROCESS_DEFINITION)) {
      HistoryMigratorLogs.migratingProcessDefinition(c7Id);
      Date deploymentTime = c7Client.getDefinitionDeploymentTime(c7ProcessDefinition.getDeploymentId());
      try {
        ProcessDefinitionDbModel dbModel = convertProcessDefinition(c7ProcessDefinition);
        markMigrated(c7Id, dbModel.processDefinitionKey(), deploymentTime, HISTORY_PROCESS_DEFINITION);
        c8Client.insertProcessDefinition(dbModel);
        HistoryMigratorLogs.migratingProcessDefinitionCompleted(c7Id);
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7Id, HISTORY_PROCESS_DEFINITION, deploymentTime, e);
      }
    }
  }

  protected ProcessDefinitionDbModel convertProcessDefinition(ProcessDefinition c7ProcessDefinition) {
    EntityConversionContext<?, ?> context = createEntityConversionContext(c7ProcessDefinition, ProcessDefinition.class,
        new ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder());
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

  /**
   * Migrates a historic process instance from Camunda 7 to Camunda 8.
   *
   * <p>This method handles the migration of process instances, including their parent-child relationships.
   * The migration process follows these steps:
   * <ol>
   *   <li>Validates that the process definition has been migrated</li>
   *   <li>Resolves parent process instance relationships (for sub-processes)</li>
   *   <li>Converts the C7 process instance to C8 format</li>
   *   <li>Inserts the process instance or marks it as skipped if dependencies are missing</li>
   * </ol>
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *   <li>Parent process instance not yet migrated (for sub-processes) - skipped with {@code SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE}</li>
   * </ul>
   *
   * @param c7ProcessInstance the historic process instance from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion or interception
   */
  protected void migrateProcessInstance(HistoricProcessInstance c7ProcessInstance) {
    String c7ProcessInstanceId = c7ProcessInstance.getId();
    if (shouldMigrate(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
      HistoryMigratorLogs.migratingProcessInstance(c7ProcessInstanceId);
      Long processDefinitionKey = findProcessDefinitionKey(c7ProcessInstance.getProcessDefinitionId());
      String processDefinitionId = c7ProcessInstance.getProcessDefinitionId();

      try {
        ProcessInstanceDbModel.ProcessInstanceDbModelBuilder processInstanceDbModelBuilder = new ProcessInstanceDbModel.ProcessInstanceDbModelBuilder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7ProcessInstance,
            HistoricProcessInstance.class, processInstanceDbModelBuilder);

        processInstanceDbModelBuilder.processInstanceKey(getNextKey());
        if (processDefinitionKey != null) {
          processInstanceDbModelBuilder.processDefinitionKey(processDefinitionKey);
        }

        ProcessInstanceDbModel dbModel = convertProcessInstance(context);
        if (!isMigrated(processDefinitionId, HISTORY_PROCESS_DEFINITION) && dbModel.processDefinitionKey() == null) {
          markSkipped(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(),
              SKIP_REASON_MISSING_PROCESS_DEFINITION);
          HistoryMigratorLogs.skippingProcessInstanceDueToMissingDefinition(c7ProcessInstanceId);
          return;
        }
        String c7SuperProcessInstanceId = c7ProcessInstance.getSuperProcessInstanceId();
        Long parentProcessInstanceKey = null;
        if (c7SuperProcessInstanceId != null) {
          ProcessInstanceEntity parentInstance = findProcessInstanceByC7Id(c7SuperProcessInstanceId);
          if (parentInstance != null) {
            parentProcessInstanceKey = parentInstance.processInstanceKey();
          }
        }
        if (parentProcessInstanceKey != null || c7SuperProcessInstanceId == null) {
          processInstanceDbModelBuilder.parentProcessInstanceKey(parentProcessInstanceKey);
          dbModel = convertProcessInstance(context);
          insertProcessInstance(c7ProcessInstance, dbModel, c7ProcessInstanceId);
        } else {
          if (dbModel.parentProcessInstanceKey() != null) {
            insertProcessInstance(c7ProcessInstance, dbModel, c7ProcessInstanceId);
          } else {
            markSkipped(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE, c7ProcessInstance.getStartTime(),
                SKIP_REASON_MISSING_PARENT_PROCESS_INSTANCE);
            HistoryMigratorLogs.skippingProcessInstanceDueToMissingParent(c7ProcessInstanceId);
          }
        }
      } catch (EntityInterceptorException | VariableInterceptorException e) {
        handleInterceptorException(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE,
            c7ProcessInstance.getStartTime(), e);
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

  /**
   * Migrates a decision requirements definition from Camunda 7 to Camunda 8.
   *
   * <p>Decision requirements definitions (DRD) define the structure of DMN decision models
   * and their dependencies. This method converts the C7 decision requirements definition
   * to C8 format and inserts it into the C8 database.
   *
   * @param c7DecisionRequirements the decision requirements definition from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  protected void migrateDecisionRequirementsDefinition(DecisionRequirementsDefinition c7DecisionRequirements) {
    String c7Id = c7DecisionRequirements.getId();
    if (shouldMigrate(c7Id, HISTORY_DECISION_REQUIREMENT)) {
      HistoryMigratorLogs.migratingDecisionRequirements(c7Id);

      DecisionRequirementsDbModel dbModel;
      Date deploymentTime = c7Client.getDefinitionDeploymentTime(c7DecisionRequirements.getDeploymentId());
      try {
        DecisionRequirementsDbModel.Builder decisionRequirementsDbModelBuilder = new DecisionRequirementsDbModel.Builder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7DecisionRequirements,
            DecisionRequirementsDefinition.class, decisionRequirementsDbModelBuilder);

        dbModel = convertDecisionRequirements(context);
        c8Client.insertDecisionRequirements(dbModel);
        markMigrated(c7Id, dbModel.decisionRequirementsKey(), deploymentTime, HISTORY_DECISION_REQUIREMENT);
        HistoryMigratorLogs.migratingDecisionRequirementsCompleted(c7Id);
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7Id, HISTORY_DECISION_REQUIREMENT, deploymentTime, e);
      }
    }
  }

  protected DecisionRequirementsDbModel convertDecisionRequirements(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    DecisionRequirementsDbModel.Builder builder =
        (DecisionRequirementsDbModel.Builder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  protected DecisionRequirementsDbModel.Builder generateBuilderForDrdForC7DefinitionWithoutDrd(String decisionRequirementsId,
                                                                                               DecisionDefinition c7DecisionDefinition,
                                                                                               String resourceName,
                                                                                               String xml) {
    return new DecisionRequirementsDbModel.Builder().decisionRequirementsKey(getNextKey())
        .decisionRequirementsId(decisionRequirementsId)
        .name(c7DecisionDefinition.getName())
        .resourceName(resourceName)
        .version(c7DecisionDefinition.getVersion())
        .xml(xml)
        .tenantId(getTenantId(c7DecisionDefinition.getTenantId()));
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

  /**
   * Migrates a decision definition from Camunda 7 to Camunda 8.
   *
   * <p>Decision definitions describe individual DMN decisions within a decision requirements definition.
   * This method validates that the parent decision requirements definition has been migrated before
   * attempting to migrate the decision definition.
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Decision requirements definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_DECISION_REQUIREMENTS}</li>
   * </ul>
   *
   * @param c7DecisionDefinition the decision definition from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  protected void migrateDecisionDefinition(DecisionDefinition c7DecisionDefinition) {
    String c7Id = c7DecisionDefinition.getId();
    if (shouldMigrate(c7Id, HISTORY_DECISION_DEFINITION)) {
      HistoryMigratorLogs.migratingDecisionDefinition(c7Id);
      Long decisionRequirementsKey;

      Date deploymentTime = c7Client.getDefinitionDeploymentTime(c7DecisionDefinition.getDeploymentId());

      DecisionDefinitionDbModel dbModel;
      try {
        DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder decisionDefinitionDbModelBuilder =
            new DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7DecisionDefinition,
            DecisionDefinition.class, decisionDefinitionDbModelBuilder);
        decisionDefinitionDbModelBuilder.decisionRequirementsId(c7DecisionDefinition.getDecisionRequirementsDefinitionKey());

        if (c7DecisionDefinition.getDecisionRequirementsDefinitionId() != null) {
          decisionRequirementsKey = dbClient.findC8KeyByC7IdAndType(c7DecisionDefinition.getDecisionRequirementsDefinitionId(),
              HISTORY_DECISION_REQUIREMENT);

          if (decisionRequirementsKey == null) {
            dbModel = convertDecisionDefinition(context);

            if (dbModel.decisionRequirementsKey() != null) {
              insertDecisionDefinition(dbModel, c7Id, deploymentTime);
            } else {
              markSkipped(c7Id, HISTORY_DECISION_DEFINITION, deploymentTime, SKIP_REASON_MISSING_DECISION_REQUIREMENTS);
              HistoryMigratorLogs.skippingDecisionDefinition(c7Id);
            }
            return;
          } else {
            decisionDefinitionDbModelBuilder.decisionRequirementsKey(decisionRequirementsKey);
          }
        } else {
          // For single c7 decisions (no DRD), generate a C8 DecisionRequirementsDefinition to store the DMN XML
          decisionRequirementsKey = createAndMigrateNewDrdForC7DmnWithoutDrd(c7DecisionDefinition, deploymentTime);
          decisionDefinitionDbModelBuilder.decisionRequirementsKey(decisionRequirementsKey);
        }
        dbModel = convertDecisionDefinition(context);
        insertDecisionDefinition(dbModel, c7Id, deploymentTime);
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7Id, HISTORY_DECISION_DEFINITION, deploymentTime, e);
      }
    }
  }

  protected Long createAndMigrateNewDrdForC7DmnWithoutDrd(DecisionDefinition c7DecisionDefinition,
                                                          Date deploymentTime) {
    String newDecisionRequirementsId = "drd-" + c7DecisionDefinition.getId();
    Long decisionRequirementsKey = dbClient.findC8KeyByC7IdAndType(newDecisionRequirementsId,
        HISTORY_DECISION_REQUIREMENT);

    if (decisionRequirementsKey == null) {
      String c7DecisionDefinitionId = c7DecisionDefinition.getId();
      HistoryMigratorLogs.creatingDecisionRequirement(c7DecisionDefinitionId);
      String deploymentId = c7DecisionDefinition.getDeploymentId();
      String resourceName = c7DecisionDefinition.getResourceName();
      String dmnXml = c7Client.getResourceAsString(deploymentId, resourceName);

      DecisionRequirementsDbModel.Builder decisionRequirementsDbModelBuilder = generateBuilderForDrdForC7DefinitionWithoutDrd(newDecisionRequirementsId,
          c7DecisionDefinition, resourceName, dmnXml);
      EntityConversionContext<?, ?> context = createEntityConversionContext(null,
          DecisionRequirementsDefinition.class, decisionRequirementsDbModelBuilder);

      DecisionRequirementsDbModel drdModel = convertDecisionRequirements(context);

      decisionRequirementsKey = drdModel.decisionRequirementsKey();
      c8Client.insertDecisionRequirements(drdModel);
      markMigrated(newDecisionRequirementsId, decisionRequirementsKey, deploymentTime, HISTORY_DECISION_REQUIREMENT);
      HistoryMigratorLogs.creatingDecisionRequirementCompleted(c7DecisionDefinitionId);
    }
    return decisionRequirementsKey;
  }

  protected DecisionDefinitionDbModel convertDecisionDefinition(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder builder =
        (DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  protected void insertDecisionDefinition(DecisionDefinitionDbModel dbModel, String c7Id, Date deploymentTime) {
    c8Client.insertDecisionDefinition(dbModel);
    markMigrated(c7Id, dbModel.decisionDefinitionKey(), deploymentTime, HISTORY_DECISION_DEFINITION);
    HistoryMigratorLogs.migratingDecisionDefinitionCompleted(c7Id);
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

  /**
   * Migrates a historic decision instance from Camunda 7 to Camunda 8.
   *
   * <p>Decision instances represent individual executions of DMN decisions. They can be triggered:
   * <ul>
   *   <li>By business rule tasks in process instances (with process context)</li>
   *   <li>As standalone decisions (without process context)</li>
   * </ul>
   *
   * <p>This method validates that all parent entities have been migrated before attempting
   * to migrate the decision instance. For standalone decisions, process-related validations
   * are skipped.
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Decision definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_DECISION_DEFINITION}</li>
   *   <li>Root decision instance not yet migrated (for nested decisions) - skipped with {@code SKIP_REASON_MISSING_PARENT_DECISION_INSTANCE}</li>
   *   <li>For process-triggered decisions only:</li>
   *   <ul>
   *     <li>Process definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *     <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *     <li>Flow node instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_FLOW_NODE}</li>
   *   </ul>
   * </ul>
   *
   * @param c7DecisionInstance the historic decision instance from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  protected void migrateDecisionInstance(HistoricDecisionInstance c7DecisionInstance) {
    String c7DecisionInstanceId = c7DecisionInstance.getId();
    if (shouldMigrate(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE)) {
      HistoryMigratorLogs.migratingDecisionInstance(c7DecisionInstanceId);
      try {
        DecisionInstanceDbModel.Builder decisionInstanceDbModelBuilder = new DecisionInstanceDbModel.Builder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7DecisionInstance,
            HistoricDecisionInstance.class, decisionInstanceDbModelBuilder);

        // Validate decision definition is migrated and fetch entity
        if (shouldSkipDecisionInstanceDueToMissingDecisionDefinition(c7DecisionInstance, context)) {
          return;
        }
        DecisionDefinitionEntity decisionDefinition = findDecisionDefinition(
            c7DecisionInstance.getDecisionDefinitionId());
        if (decisionDefinition != null) {
          if (decisionDefinition.decisionDefinitionKey() != null) {
            decisionInstanceDbModelBuilder.decisionDefinitionKey(decisionDefinition.decisionDefinitionKey());
          }
          if (decisionDefinition.decisionRequirementsKey() != null) {
            decisionInstanceDbModelBuilder.decisionRequirementsKey(decisionDefinition.decisionRequirementsKey());
          }
        }

        String c7RootDecisionInstanceId = c7DecisionInstance.getRootDecisionInstanceId();
        Long parentDecisionDefinitionKey = null;
        if (c7RootDecisionInstanceId != null) {
          if (shouldSkipDecisionInstanceDueToMissingParent(c7RootDecisionInstanceId, c7DecisionInstance, context)) {
            return;
          }
          DecisionInstanceEntity parentDecision = findDecisionInstance(c7RootDecisionInstanceId);
          if (parentDecision != null && parentDecision.decisionDefinitionKey() != null) {
            parentDecisionDefinitionKey = parentDecision.decisionDefinitionKey();
            decisionInstanceDbModelBuilder.rootDecisionDefinitionKey(parentDecisionDefinitionKey);
          }
        }

        // Check if this is a standalone decision (not triggered by a BPMN)
        boolean isStandaloneDecision = c7DecisionInstance.getProcessDefinitionKey() == null;

        if (!isStandaloneDecision) {
          // For process-triggered decisions, validate all process-related entities
          if (shouldSkipDecisionInstanceDueToMissingProcessDefinition(c7DecisionInstance, context)) {
            return;
          }
          Long processDefinitionKey = findProcessDefinitionKey(c7DecisionInstance.getProcessDefinitionId());
          if (processDefinitionKey != null) {
            decisionInstanceDbModelBuilder.processDefinitionKey(processDefinitionKey);
          }

          if (shouldSkipDecisionInstanceDueToMissingProcessInstance(c7DecisionInstance, context)) {
            return;
          }
          ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7DecisionInstance.getProcessInstanceId());
          if (processInstance != null && processInstance.processInstanceKey() != null) {
            decisionInstanceDbModelBuilder.processInstanceKey(processInstance.processInstanceKey());
          }

          if (shouldSkipDecisionInstanceDueToMissingFlowNodeInstanceInstance(c7DecisionInstance, context)) {
            return;
          }
          FlowNodeInstanceDbModel flowNode = findFlowNodeInstance(c7DecisionInstance.getActivityInstanceId());
          if (flowNode != null) {
            if (flowNode.flowNodeInstanceKey() != null) {
              decisionInstanceDbModelBuilder.flowNodeInstanceKey(flowNode.flowNodeInstanceKey());
            }
            if (flowNode.flowNodeId() != null) {
              decisionInstanceDbModelBuilder.flowNodeId(flowNode.flowNodeId());
            }
          }
        }

        // Generate decision instance key and finalize model
        Long decisionInstanceKey = getNextKey();
        String decisionInstanceId = String.format("%s-%d", decisionInstanceKey, 1);
        DmnModelInstance dmnModelInstance = c7Client.getDmnModelInstance(c7DecisionInstance.getDecisionDefinitionId());

        decisionInstanceDbModelBuilder.decisionInstanceId(decisionInstanceId)
            .decisionInstanceKey(decisionInstanceKey)
            .decisionType(determineDecisionType(dmnModelInstance, c7DecisionInstance.getDecisionDefinitionKey()));

        DecisionInstanceDbModel dbModel = convertDecisionInstance(context);
        c8Client.insertDecisionInstance(dbModel);
        migrateChildDecisionInstances(parentDecisionDefinitionKey, c7DecisionInstance, dbModel);
        markMigrated(c7DecisionInstanceId, dbModel.decisionInstanceKey(), c7DecisionInstance.getEvaluationTime(),
            HISTORY_DECISION_INSTANCE);
        HistoryMigratorLogs.migratingDecisionInstanceCompleted(c7DecisionInstanceId);
      } catch (VariableInterceptorException | EntityInterceptorException e) {
        handleInterceptorException(c7DecisionInstanceId, HISTORY_DECISION_INSTANCE,
            c7DecisionInstance.getEvaluationTime(), e);
      }
    }
  }

  public void migrateChildDecisionInstances(Long parentDecisionDefinitionKey,
                                               HistoricDecisionInstance c7DecisionInstance,
                                               DecisionInstanceDbModel c8ParentDecisionInstanceModel) {
    List<HistoricDecisionInstance> childDecisionInstances =
        c7Client.findChildDecisionInstances(c7DecisionInstance.getId());
    for (int i = 0; i < childDecisionInstances.size(); i++) {
      HistoricDecisionInstance childDecisionInstance = childDecisionInstances.get(i);
      if (shouldMigrate(childDecisionInstance.getId(), TYPE.HISTORY_DECISION_INSTANCE)) {
        try {
          DecisionInstanceDbModel.Builder decisionInstanceDbModelBuilder = new DecisionInstanceDbModel.Builder();
          EntityConversionContext<?, ?> context = createEntityConversionContext(childDecisionInstance,
              HistoricDecisionInstance.class, decisionInstanceDbModelBuilder);
          DecisionDefinitionEntity decisionDefinition = findDecisionDefinition(
              childDecisionInstance.getDecisionDefinitionId());
          String childDecisionInstanceId =
              // +2 because +1 is used for the parent decision instance
              String.format("%s-%d", c8ParentDecisionInstanceModel.decisionInstanceKey(), i + 2);
          DmnModelInstance dmnModelInstance = c7Client.getDmnModelInstance(childDecisionInstance.getDecisionDefinitionId());
          decisionInstanceDbModelBuilder.decisionDefinitionKey(decisionDefinition.decisionDefinitionKey())
              .decisionInstanceId(childDecisionInstanceId)
              .decisionInstanceKey(c8ParentDecisionInstanceModel.decisionInstanceKey())
              .decisionRequirementsKey(decisionDefinition.decisionRequirementsKey())
              .processDefinitionKey(c8ParentDecisionInstanceModel.processDefinitionKey())
              .processInstanceKey(c8ParentDecisionInstanceModel.processInstanceKey())
              .rootDecisionDefinitionKey(parentDecisionDefinitionKey)
              .flowNodeInstanceKey(c8ParentDecisionInstanceModel.flowNodeInstanceKey())
              .flowNodeId(c8ParentDecisionInstanceModel.flowNodeId())
              .decisionType(determineDecisionType(dmnModelInstance, childDecisionInstance.getDecisionDefinitionKey()));
          DecisionInstanceDbModel childDbModel = convertDecisionInstance(context);
          c8Client.insertDecisionInstance(childDbModel);
        } catch (EntityInterceptorException e) {
          handleInterceptorException(childDecisionInstance.getId(), HISTORY_DECISION_INSTANCE,
              childDecisionInstance.getEvaluationTime(), e);
        }
      }
    }
  }

  protected void processDecisionInstanceConversion(HistoricDecisionInstance c7DecisionInstance,
                                                   EntityConversionContext<?, ?> context,
                                                   String c7DecisionInstanceId) {
    DecisionInstanceDbModel dbModel = convertDecisionInstance(context);
    boolean isStandaloneDecision = c7DecisionInstance.getProcessDefinitionKey() == null;

    if (dbModel.decisionDefinitionKey() == null) {
      markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(),
          SKIP_REASON_MISSING_DECISION_DEFINITION);
      HistoryMigratorLogs.skippingDecisionInstanceDueToMissingDecisionDefinition(c7DecisionInstanceId);
    } else if (!isStandaloneDecision && dbModel.processDefinitionKey() == null) {
      markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(),
          SKIP_REASON_MISSING_PROCESS_DEFINITION);
      HistoryMigratorLogs.skippingDecisionInstanceDueToMissingProcessDefinition(c7DecisionInstanceId);
    } else if (!isStandaloneDecision && dbModel.processInstanceKey() == null) {
      markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(),
          SKIP_REASON_MISSING_PROCESS_INSTANCE);
      HistoryMigratorLogs.skippingDecisionInstanceDueToMissingProcessInstance(c7DecisionInstanceId);
    } else if (!isStandaloneDecision && dbModel.flowNodeInstanceKey() == null) {
      markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(),
          SKIP_REASON_MISSING_FLOW_NODE);
      HistoryMigratorLogs.skippingDecisionInstanceDueToMissingFlowNodeInstanceInstance(c7DecisionInstanceId);
    } else {
      String c7RootDecisionInstanceId = c7DecisionInstance.getRootDecisionInstanceId();
      if (c7RootDecisionInstanceId != null && dbModel.rootDecisionDefinitionKey() == null) {
        markSkipped(c7DecisionInstanceId, TYPE.HISTORY_DECISION_INSTANCE, c7DecisionInstance.getEvaluationTime(),
            SKIP_REASON_MISSING_PARENT_DECISION_INSTANCE);
        HistoryMigratorLogs.skippingDecisionInstanceDueToMissingParent(c7DecisionInstanceId);
      } else {
        insertDecisionInstance(c7DecisionInstance, c7DecisionInstanceId, dbModel);
      }
    }
  }


  protected void insertDecisionInstance(HistoricDecisionInstance c7DecisionInstance,
                                        String c7DecisionInstanceId,
                                        DecisionInstanceDbModel dbModel) {
    c8Client.insertDecisionInstance(dbModel);
    markMigrated(c7DecisionInstanceId, dbModel.decisionInstanceKey(), c7DecisionInstance.getEvaluationTime(), HISTORY_DECISION_INSTANCE);
    HistoryMigratorLogs.migratingDecisionInstanceCompleted(c7DecisionInstanceId);
  }

  protected DecisionInstanceDbModel convertDecisionInstance(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    DecisionInstanceDbModel.Builder builder =
        (DecisionInstanceDbModel.Builder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
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

  /**
   * Migrates a historic incident from Camunda 7 to Camunda 8.
   *
   * <p>Incidents represent errors or exceptional conditions that occurred during process execution.
   * This method validates that all parent entities (process instance, process definition, and
   * flow node instance) have been migrated before attempting to migrate the incident.
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *   <li>Process instance key missing - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE_KEY}</li>
   *   <li>Process definition not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_DEFINITION}</li>
   *   <li>Flow node instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_SCOPE_KEY}</li>
   *   <li>Job reference missing - skipped with {@code SKIP_REASON_MISSING_JOB_REFERENCE}</li>
   * </ul>
   *
   * @param c7Incident the historic incident from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  protected void migrateIncident(HistoricIncident c7Incident) {
    String c7IncidentId = c7Incident.getId();
    if (shouldMigrate(c7IncidentId, HISTORY_INCIDENT)) {
      HistoryMigratorLogs.migratingHistoricIncident(c7IncidentId);
      ProcessInstanceEntity c7ProcessInstance = findProcessInstanceByC7Id(c7Incident.getProcessInstanceId());

      try {
        IncidentDbModel.Builder incidentDbModelBuilder = new IncidentDbModel.Builder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7Incident, HistoricIncident.class,
            incidentDbModelBuilder);

        if (c7ProcessInstance != null) {
          Long processInstanceKey = c7ProcessInstance.processInstanceKey();
          incidentDbModelBuilder
              .processInstanceKey(processInstanceKey);
          if (processInstanceKey != null) {
            Long flowNodeInstanceKey = findFlowNodeInstanceKey(c7Incident.getActivityId(), c7Incident.getProcessInstanceId());
            Long processDefinitionKey = findProcessDefinitionKey(c7Incident.getProcessDefinitionId());
            Long jobDefinitionKey = null; // TODO Job table doesn't exist yet.


            incidentDbModelBuilder
                .processDefinitionKey(processDefinitionKey)
                .jobKey(jobDefinitionKey)
                .flowNodeInstanceKey(flowNodeInstanceKey);

            IncidentDbModel dbModel = convertIncident(context);
            insertIncident(c7Incident, dbModel, c7IncidentId);
          } else {
            IncidentDbModel dbModel = convertIncident(context);
            if (dbModel.processInstanceKey() == null) {
              markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(), SKIP_REASON_MISSING_PROCESS_INSTANCE_KEY);
              HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
            } else if (dbModel.processDefinitionKey() == null) {
              markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(), SKIP_REASON_MISSING_PROCESS_DEFINITION);
              HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
            } else if (dbModel.flowNodeInstanceKey() == null) {
              markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(), SKIP_REASON_MISSING_SCOPE_KEY);
              HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
            } else if (dbModel.jobKey() == null) {
              markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(), SKIP_REASON_MISSING_JOB_REFERENCE);
              HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
            }
            else {
              insertIncident(c7Incident, dbModel, c7IncidentId);
            }
          }
        } else {
          IncidentDbModel dbModel = convertIncident(context);
          if (dbModel.processInstanceKey() == null) {
            markSkipped(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(), SKIP_REASON_MISSING_PROCESS_INSTANCE);
            HistoryMigratorLogs.skippingHistoricIncident(c7IncidentId);
          } else {
            insertIncident(c7Incident, dbModel, c7IncidentId);
          }
        }
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7IncidentId, HISTORY_INCIDENT, c7Incident.getCreateTime(), e);
      }
    }
  }

    protected void insertIncident(HistoricIncident c7Incident,
                                  IncidentDbModel dbModel,
                                  String c7IncidentId) {
      c8Client.insertIncident(dbModel);
      markMigrated(c7IncidentId, dbModel.incidentKey(), c7Incident.getCreateTime(), HISTORY_INCIDENT);
      HistoryMigratorLogs.migratingHistoricIncidentCompleted(c7IncidentId);
    }

  protected IncidentDbModel convertIncident(EntityConversionContext<?, ?> context) {
    EntityConversionContext<?, ?> entityConversionContext = entityConversionService.convertWithContext(context);
    IncidentDbModel.Builder builder = (IncidentDbModel.Builder) entityConversionContext.getC8DbModelBuilder();
    return builder.build();
  }

  public void migrateVariables() {
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
   * <p>Variables can be scoped to either:
   * <ul>
   *   <li>A user task (taskId != null)</li>
   *   <li>A process instance with optional activity instance scope</li>
   * </ul>
   *
   * <p>This method validates that all parent entities (process instance, task, activity instance)
   * have been migrated before attempting to migrate the variable.
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *   <li>User task not yet migrated (for task-scoped variables) - skipped with {@code SKIP_REASON_BELONGS_TO_SKIPPED_TASK}</li>
   *   <li>Scope key missing (flow node or process instance) - skipped with {@code SKIP_REASON_MISSING_SCOPE_KEY}</li>
   * </ul>
   *
   * @param c7Variable the historic variable instance from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  protected void migrateVariable(HistoricVariableInstance c7Variable) {
    String c7VariableId = c7Variable.getId();
    if (shouldMigrate(c7VariableId, HISTORY_VARIABLE)) {
      HistoryMigratorLogs.migratingHistoricVariable(c7VariableId);

      try {
        VariableDbModel.VariableDbModelBuilder variableDbModelBuilder = new VariableDbModel.VariableDbModelBuilder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7Variable, HistoricVariableInstance.class,
            variableDbModelBuilder);

        // Handle task-scoped variables
        String taskId = c7Variable.getTaskId();
        if (taskId != null && !isMigrated(taskId, HISTORY_USER_TASK)) {
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
      } catch (EntityInterceptorException | VariableInterceptorException e) {
        handleInterceptorException(c7VariableId, HISTORY_VARIABLE, c7Variable.getCreateTime(), e);
      }
    }
  }

  protected void processVariableConversion(HistoricVariableInstance c7Variable,
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
        HistoryMigratorLogs.skippingHistoricVariableDueToMissingTask(c7VariableId, c7Variable.getTaskId());
      } else {
        HistoryMigratorLogs.skippingHistoricVariableDueToMissingScopeKey(c7VariableId);
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

  /**
   * Migrates a historic user task from Camunda 7 to Camunda 8.
   *
   * <p>User tasks represent work items that need to be completed by human users.
   * This method validates that all parent entities (process instance, flow node instance)
   * have been migrated before attempting to migrate the user task.
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   *   <li>Flow node instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_FLOW_NODE}</li>
   * </ul>
   *
   * @param c7UserTask the historic user task from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  protected void migrateUserTask(HistoricTaskInstance c7UserTask) {
    String c7UserTaskId = c7UserTask.getId();
    if (shouldMigrate(c7UserTaskId, HISTORY_USER_TASK)) {
      HistoryMigratorLogs.migratingHistoricUserTask(c7UserTaskId);

      try {
        UserTaskDbModel.Builder userTaskDbModelBuilder = new UserTaskDbModel.Builder();
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7UserTask, HistoricTaskInstance.class,
            userTaskDbModelBuilder);

        if (isMigrated(c7UserTask.getProcessInstanceId(), HISTORY_PROCESS_INSTANCE)) {

          ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7UserTask.getProcessInstanceId());
          if (processInstance != null) {
            userTaskDbModelBuilder.processInstanceKey(processInstance.processInstanceKey())
                .processDefinitionVersion(processInstance.processDefinitionVersion());
          }
          if (isMigrated(c7UserTask.getActivityInstanceId(), HISTORY_FLOW_NODE)) {
            Long elementInstanceKey = findFlowNodeInstanceKey(c7UserTask.getActivityInstanceId());
            Long processDefinitionKey = findProcessDefinitionKey(c7UserTask.getProcessDefinitionId());

            userTaskDbModelBuilder.processDefinitionKey(processDefinitionKey)
                .elementInstanceKey(elementInstanceKey);

            UserTaskDbModel dbModel = convertUserTask(context);
            insertUserTask(c7UserTask, dbModel, c7UserTaskId);
          } else {
            UserTaskDbModel dbModel = convertUserTask(context);
            if (dbModel.elementInstanceKey() == null) {
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
          } else if (dbModel.elementInstanceKey() == null) {
            markSkipped(c7UserTaskId, TYPE.HISTORY_USER_TASK, c7UserTask.getStartTime(), SKIP_REASON_MISSING_FLOW_NODE);
            HistoryMigratorLogs.skippingHistoricUserTaskDueToMissingFlowNode(c7UserTaskId);
          } else {
            insertUserTask(c7UserTask, dbModel, c7UserTaskId);
          }
        }
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7UserTaskId, HISTORY_USER_TASK, c7UserTask.getStartTime(), e);
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

  /**
   * Migrates a historic flow node instance from Camunda 7 to Camunda 8.
   *
   * <p>Flow nodes represent individual steps in a process execution (activities, gateways, events, etc.).
   * This method validates that the parent process instance has been migrated before attempting to migrate
   * the flow node instance.
   *
   * <p>The migration process:
   * <ol>
   *   <li>Checks if the flow node should be migrated based on the current mode</li>
   *   <li>Validates the parent process instance exists in C8</li>
   *   <li>Retrieves the process definition key</li>
   *   <li>Converts the C7 flow node to C8 format</li>
   *   <li>Either inserts the flow node or marks it as skipped if dependencies are missing</li>
   * </ol>
   *
   * <p>Skip scenarios:
   * <ul>
   *   <li>Process instance not yet migrated - skipped with {@code SKIP_REASON_MISSING_PROCESS_INSTANCE}</li>
   * </ul>
   *
   * @param c7FlowNode the historic activity instance from Camunda 7 to be migrated
   * @throws EntityInterceptorException if an error occurs during entity conversion
   */
  protected void migrateFlowNode(HistoricActivityInstance c7FlowNode) {
    String c7FlowNodeId = c7FlowNode.getId();
    if (shouldMigrate(c7FlowNodeId, HISTORY_FLOW_NODE)) {
      HistoryMigratorLogs.migratingHistoricFlowNode(c7FlowNodeId);
      ProcessInstanceEntity processInstance = findProcessInstanceByC7Id(c7FlowNode.getProcessInstanceId());

      try {
        Long flowNodeInstanceKey = getNextKey();
        FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder flowNodeDbModelBuilder = new FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder();
        flowNodeDbModelBuilder.flowNodeInstanceKey(flowNodeInstanceKey);
        EntityConversionContext<?, ?> context = createEntityConversionContext(c7FlowNode, HistoricActivityInstance.class,
            flowNodeDbModelBuilder);

        if (processInstance != null) {
          Long processInstanceKey = processInstance.processInstanceKey();
          Long processDefinitionKey = findProcessDefinitionKey(c7FlowNode.getProcessDefinitionId());

          flowNodeDbModelBuilder.processInstanceKey(processInstanceKey)
              .treePath(generateTreePath(processInstanceKey, flowNodeInstanceKey))
              .processDefinitionKey(processDefinitionKey);

          FlowNodeInstanceDbModel dbModel = convertFlowNode(context);
          insertFlowNodeInstance(c7FlowNode, dbModel, c7FlowNodeId);
        } else {
          FlowNodeInstanceDbModel dbModel = convertFlowNode(context);
          if (dbModel.processInstanceKey() == null) {
            markSkipped(c7FlowNodeId, HISTORY_FLOW_NODE, c7FlowNode.getStartTime(), SKIP_REASON_MISSING_PROCESS_INSTANCE);
            HistoryMigratorLogs.skippingHistoricFlowNode(c7FlowNodeId);
          } else {
            insertFlowNodeInstance(c7FlowNode, dbModel, c7FlowNodeId);
          }
        }
      } catch (EntityInterceptorException e) {
        handleInterceptorException(c7FlowNodeId, HISTORY_FLOW_NODE, c7FlowNode.getStartTime(), e);
      }
    }
  }

  /**
   * Generates a tree path for flow nodes in the format: processInstanceKey/elementInstanceKey
   *
   * @param processInstanceKey the process instance key
   * @param elementInstanceKey the element instance key (flow node)
   * @return the tree path string
   */
  public static String generateTreePath(Long processInstanceKey, Long elementInstanceKey) {
    return processInstanceKey + "/" + elementInstanceKey;
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

  protected boolean shouldSkipDecisionInstanceDueToMissingDecisionDefinition(HistoricDecisionInstance c7DecisionInstance,
                                                                             EntityConversionContext<?, ?> context) {
    if (!isMigrated(c7DecisionInstance.getDecisionDefinitionId(), HISTORY_DECISION_DEFINITION)) {
      processDecisionInstanceConversion(c7DecisionInstance, context, c7DecisionInstance.getId());
      return true;
    }
    return false;
  }

  protected boolean shouldSkipDecisionInstanceDueToMissingProcessDefinition(HistoricDecisionInstance c7DecisionInstance,
                                                                            EntityConversionContext<?, ?> context) {
    if (!isMigrated(c7DecisionInstance.getProcessDefinitionId(), HISTORY_PROCESS_DEFINITION)) {
      processDecisionInstanceConversion(c7DecisionInstance, context, c7DecisionInstance.getId());
      return true;
    }
    return false;
  }

  protected boolean shouldSkipDecisionInstanceDueToMissingProcessInstance(HistoricDecisionInstance c7DecisionInstance,
                                                                          EntityConversionContext<?, ?> context) {
    if (!isMigrated(c7DecisionInstance.getProcessInstanceId(), HISTORY_PROCESS_INSTANCE)) {
      processDecisionInstanceConversion(c7DecisionInstance, context, c7DecisionInstance.getId());
      return true;
    }
    return false;
  }

  protected boolean shouldSkipDecisionInstanceDueToMissingParent(String c7RootDecisionInstanceId,
                                                                 HistoricDecisionInstance c7DecisionInstance,
                                                                 EntityConversionContext<?, ?> context) {
    if (!isMigrated(c7RootDecisionInstanceId, HISTORY_DECISION_INSTANCE)) {
      processDecisionInstanceConversion(c7DecisionInstance, context, c7DecisionInstance.getId());
      return true;
    }
    return false;
  }

  protected boolean shouldSkipDecisionInstanceDueToMissingFlowNodeInstanceInstance(HistoricDecisionInstance c7DecisionInstance,
                                                                                   EntityConversionContext<?, ?> context) {
    if (!isMigrated(c7DecisionInstance.getActivityInstanceId(), HISTORY_FLOW_NODE)) {
      processDecisionInstanceConversion(c7DecisionInstance, context, c7DecisionInstance.getId());
      return true;
    }
    return false;
  }

  protected <T> EntityConversionContext<?, ?> createEntityConversionContext(T c7Entity,
                                                                            Class<T> c7EntityClass,
                                                                            Object dbModelBuilder) {
    EntityConversionContext<?, ?> context = new EntityConversionContext<>(c7Entity, c7EntityClass, dbModelBuilder,
        processEngine);
    entityConversionService.prepareParentProperties(context);
    return context;
  }

  protected void handleInterceptorException(String c7Id, TYPE type, Date time, MigratorException e) {
    HistoryMigratorLogs.skippingEntityDueToInterceptorError(type, c7Id, e.getMessage());
    HistoryMigratorLogs.stacktrace(e);
    markSkipped(c7Id, type, time, e.getMessage());
  }

  protected DecisionInstanceEntity.DecisionDefinitionType determineDecisionType(DmnModelInstance dmnModelInstance,
                                                                                String decisionDefinitionId) {
    Decision decision = dmnModelInstance.getModelElementById(decisionDefinitionId);
    if (decision == null) {
      return null;
    }

    if (decision.getExpression() instanceof LiteralExpression) {
      return DecisionInstanceEntity.DecisionDefinitionType.LITERAL_EXPRESSION;
    } else {
      return DecisionInstanceEntity.DecisionDefinitionType.DECISION_TABLE;
    }
  }

}
