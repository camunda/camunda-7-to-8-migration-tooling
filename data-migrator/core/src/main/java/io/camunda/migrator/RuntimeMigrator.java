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
import static io.camunda.migrator.impl.logging.RuntimeMigratorLogs.ENABLE_DEBUG_LOGGING;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.exception.VariableInterceptorException;
import io.camunda.migrator.impl.RuntimeValidator;
import io.camunda.migrator.impl.VariableService;
import io.camunda.migrator.impl.clients.C7Client;
import io.camunda.migrator.impl.clients.C8Client;
import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.impl.logging.RuntimeMigratorLogs;
import io.camunda.migrator.impl.model.FlowNode;
import io.camunda.migrator.impl.model.FlowNodeActivation;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.impl.util.C7Utils;
import io.camunda.migrator.impl.util.ExceptionUtils;
import io.camunda.migrator.impl.util.PrintUtils;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RuntimeMigrator {

  @Autowired
  protected C7Client c7Client;

  @Autowired
  protected C8Client c8Client;

  @Autowired
  protected DbClient dbClient;

  @Autowired
  protected VariableService variableService;

  @Autowired
  protected RuntimeValidator runtimeValidator;

  @Autowired
  protected MigratorProperties migratorProperties;

  protected MigratorMode mode = MIGRATE;

  public void start() {
    try {
      ExceptionUtils.setContext(ExceptionUtils.ExceptionContext.RUNTIME);
      if (LIST_SKIPPED.equals(mode)) {
        PrintUtils.printSkippedInstancesHeader(dbClient.countSkippedByType(TYPE.RUNTIME_PROCESS_INSTANCE),
          TYPE.RUNTIME_PROCESS_INSTANCE);
        dbClient.listSkippedEntitiesByType(TYPE.RUNTIME_PROCESS_INSTANCE);
      } else {
        migrate();
      }
    } finally {
      ExceptionUtils.clearContext();
    }
  }

  protected void migrate() {
    fetchProcessInstancesToMigrate(c7ProcessInstance -> {
      String c7ProcessInstanceId = c7ProcessInstance.getC7Id();
      Date createTime = c7ProcessInstance.getCreateTime();

      String skipReason = getSkipReason(c7ProcessInstanceId);
      if (skipReason == null && shouldStartProcessInstance(c7ProcessInstanceId)) {
        startProcessInstance(c7ProcessInstanceId, createTime);
      } else if (isUnknown(c7ProcessInstanceId)) {
        dbClient.insert(c7ProcessInstanceId, null, createTime, TYPE.RUNTIME_PROCESS_INSTANCE, skipReason);
      } else {
        dbClient.updateSkipReason(c7ProcessInstanceId, TYPE.RUNTIME_PROCESS_INSTANCE, skipReason);
      }
    });

    activateMigratorJobs();
  }

  protected String getSkipReason(String c7ProcessInstanceId) {
    try {
      runtimeValidator.validateProcessInstanceState(c7ProcessInstanceId);
      return null;
    } catch (IllegalStateException e) {
      RuntimeMigratorLogs.skippingProcessInstanceValidationError(c7ProcessInstanceId, e.getMessage());
      return e.getMessage();
    }
  }

  protected boolean shouldStartProcessInstance(String c7ProcessInstanceId) {
    return RETRY_SKIPPED.equals(mode) || isUnknown(c7ProcessInstanceId);
  }

  protected boolean isUnknown(String c7ProcessInstanceId) {
    return MIGRATE.equals(mode) && !dbClient.checkExistsByC7IdAndType(c7ProcessInstanceId, RUNTIME_PROCESS_INSTANCE);
  }

  protected void startProcessInstance(String c7ProcessInstanceId, Date createTime) {
    RuntimeMigratorLogs.startingNewC8ProcessInstance(c7ProcessInstanceId);

    try {
      Long processInstanceKey = startNewProcessInstance(c7ProcessInstanceId);
      RuntimeMigratorLogs.startedC8ProcessInstance(processInstanceKey);

      if (processInstanceKey != null) {
        saveRecord(c7ProcessInstanceId, createTime, processInstanceKey);
      }
    } catch (VariableInterceptorException e) {
      handleVariableInterceptorException(e, c7ProcessInstanceId, createTime);
    }
  }

  protected void handleVariableInterceptorException(VariableInterceptorException e, String c7ProcessInstanceId, Date createTime) {
    RuntimeMigratorLogs.skippingProcessInstanceVariableError(c7ProcessInstanceId, e.getMessage());
    RuntimeMigratorLogs.stacktrace(e);

    if (MIGRATE.equals(mode)) {
      dbClient.insert(c7ProcessInstanceId, null, createTime, TYPE.RUNTIME_PROCESS_INSTANCE, e.getMessage());
    } else if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateSkipReason(c7ProcessInstanceId, TYPE.RUNTIME_PROCESS_INSTANCE, e.getMessage());
    }
  }

  protected void saveRecord(String c7ProcessInstanceId, Date createTime, Long processInstanceKey) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateC8KeyByC7IdAndType(c7ProcessInstanceId, processInstanceKey, TYPE.RUNTIME_PROCESS_INSTANCE);
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(c7ProcessInstanceId, processInstanceKey, createTime, TYPE.RUNTIME_PROCESS_INSTANCE);
    }
  }

  protected void fetchProcessInstancesToMigrate(Consumer<IdKeyDbModel> storeMappingConsumer) {
    RuntimeMigratorLogs.fetchingProcessInstances();

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(TYPE.RUNTIME_PROCESS_INSTANCE, storeMappingConsumer);
    } else {
      RuntimeMigratorLogs.fetchingLatestCreateTime();
      Date latestCreateTime = dbClient.findLatestCreateTimeByType(TYPE.RUNTIME_PROCESS_INSTANCE);
      RuntimeMigratorLogs.latestCreateTime(latestCreateTime);

      c7Client.fetchAndHandleHistoricRootProcessInstances(storeMappingConsumer, latestCreateTime);
    }
  }

  protected Long startNewProcessInstance(String c7ProcessInstanceId) throws VariableInterceptorException {
    var processInstance = c7Client.getProcessInstance(c7ProcessInstanceId);
    if (processInstance != null) {
      String bpmnProcessId = processInstance.getProcessDefinitionKey();

      // Ensure all variables are fetched and can be transformed before starting the new instance
      Map<String, Object> globalVariables = variableService.getGlobalVariables(c7ProcessInstanceId);

      return c8Client.createProcessInstance(bpmnProcessId, processInstance.getTenantId(), globalVariables)
          .getProcessInstanceKey();
    } else {
      RuntimeMigratorLogs.processInstanceNotExists(c7ProcessInstanceId);
      return null;
    }
  }

  protected void activateMigratorJobs() {
    RuntimeMigratorLogs.activatingMigratorJobs();
    List<ActivatedJob> migratorJobs;
    do {
      migratorJobs = c8Client.activateJobs(migratorProperties.getJobActivationType());

      RuntimeMigratorLogs.migratorJobsFound(migratorJobs.size());

      migratorJobs.forEach(job -> {
        boolean externallyStarted = variableService.isExternallyStartedJob(job);
        if (!externallyStarted) {
          String c7Id = variableService.getC7IdFromJob(job);
          var activityInstanceTree = c7Client.getActivityInstance(c7Id);

          RuntimeMigratorLogs.collectingActiveDescendantActivities(activityInstanceTree.getActivityId());
          Map<String, FlowNode> activityInstanceMap = C7Utils.getActiveActivityIdsById(activityInstanceTree, new HashMap<>());
          RuntimeMigratorLogs.foundActiveActivitiesToActivate(activityInstanceMap.size());

          List<FlowNodeActivation> flowNodeActivations = activityInstanceMap.entrySet().stream()
              .map(entry -> {
                String activityInstanceId = entry.getKey();
                FlowNode flowNode = entry.getValue();

                Map<String, Object> localVariables = variableService.getLocalVariables(activityInstanceId, flowNode.subProcessInstanceId());
                String activityId = flowNode.activityId();
                return new FlowNodeActivation(activityId, localVariables);
              })
              .collect(Collectors.toList());

          long processInstanceKey = job.getProcessInstanceKey();
          long elementInstanceKey = job.getElementInstanceKey();
          c8Client.modifyProcessInstance(processInstanceKey, elementInstanceKey, flowNodeActivations);
          // no need to complete the job since the modification canceled the migrator job in the start event
        } else {
          RuntimeMigratorLogs.externallyStartedProcessInstance(job.getProcessInstanceKey());
        }
      });

    } while (!migratorJobs.isEmpty());
  }

  public void setMode(MigratorMode mode) {
    this.mode = mode;
  }

}
