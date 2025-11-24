/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl;

import static io.camunda.migrator.constants.MigratorConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.ACTIVE_JOINING_PARALLEL_GATEWAY_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.NO_C8_TENANT_DEPLOYMENT_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.TENANT_ID_ERROR;
import static io.camunda.migrator.impl.util.C7Utils.MULTI_INSTANCE_BODY_SUFFIX;
import static io.camunda.migrator.impl.util.C7Utils.PARALLEL_GATEWAY_ACTIVITY_TYPE;
import static io.camunda.migrator.impl.util.C7Utils.getActiveActivityIdsById;
import static io.camunda.migrator.impl.util.ExceptionUtils.callApi;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.FAILED_TO_PARSE_BPMN_MODEL;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.FLOW_NODE_NOT_EXISTS_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.NO_C8_DEPLOYMENT_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.NO_EXECUTION_LISTENER_OF_TYPE_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.NO_NONE_START_EVENT_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.CALL_ACTIVITY_LEGACY_ID_ERROR;

import io.camunda.migrator.impl.logging.RuntimeValidatorLogs;
import static io.camunda.zeebe.model.bpmn.Bpmn.readModelFromStream;

import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.clients.C7Client;
import io.camunda.migrator.impl.clients.C8Client;
import io.camunda.migrator.impl.model.FlowNode;
import io.camunda.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeExecutionListenersImpl;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Dedicated class for all validation logic used in the migration process.
 * Consolidates validation methods that were previously scattered across C7Client, C8Client, and RuntimeMigrator.
 */
@Component
public class RuntimeValidator {

  @Autowired
  protected MigratorProperties properties;

  @Autowired
  protected C7Client c7Client;

  @Autowired
  protected C8Client c8Client;

  /**
   * Validates C7 flow nodes for multi-instance loop characteristics.
   */
  public void validateC7FlowNodes(String processDefinitionId, String processInstanceId, String activityId) {
    BpmnModelInstance c7BpmnModelInstance = c7Client.getBpmnModelInstance(processDefinitionId);
    FlowElement element = c7BpmnModelInstance.getModelElementById(activityId);

    if (isMultiInstanceActivity(activityId, element)) {
      String activityIdWithoutSuffix = activityId.replace(MULTI_INSTANCE_BODY_SUFFIX, "");
      throw new IllegalStateException(String.format(MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR, activityIdWithoutSuffix));
    }

    if (PARALLEL_GATEWAY_ACTIVITY_TYPE.equals(element.getElementType().getTypeName())) {
      throw new IllegalStateException(String.format(ACTIVE_JOINING_PARALLEL_GATEWAY_ERROR, activityId, processInstanceId));
    }
  }

  /**
   * Validates C8 process structure and execution listeners.
   */
  public void validateC8Process(String xmlString, ProcessDefinition procDef) {
    var bpmnModelInstance = parseBpmnModel(xmlString);

    var processInstanceStartEvents = bpmnModelInstance.getDefinitions()
        .getModelInstance()
        .getModelElementsByType(StartEvent.class)
        .stream()
        .filter(startEvent -> startEvent.getParentElement() instanceof ProcessImpl)
        .toList();

    boolean hasNoneStartEvent = processInstanceStartEvents.stream()
        .anyMatch(startEvent -> startEvent.getEventDefinitions().isEmpty());
    if (!hasNoneStartEvent) {
      throw new IllegalStateException(String.format(NO_NONE_START_EVENT_ERROR, procDef.getProcessDefinitionId(), procDef.getVersion()));
    }

    // Skip job type validation if disabled
    if (properties.isJobTypeValidationDisabled()) {
      RuntimeValidatorLogs.jobTypeValidationDisabled();
      return;
    }

    String validationJobType = properties.getEffectiveValidationJobType();
    processInstanceStartEvents.forEach(startEvent -> {
      var zBExecutionListeners = startEvent.getSingleExtensionElement(ZeebeExecutionListenersImpl.class);
      boolean hasMigratorListener = zBExecutionListeners != null && zBExecutionListeners.getExecutionListeners()
          .stream()
          .anyMatch(listener -> validationJobType.equals(listener.getType()));

      if (!hasMigratorListener) {
        throw new IllegalStateException(
            String.format(NO_EXECUTION_LISTENER_OF_TYPE_ERROR, validationJobType, startEvent.getId(),
                procDef.getProcessDefinitionId(), procDef.getVersion(), validationJobType));
      }
    });
  }

  /**
   * Validates C8 flow nodes exist in the BPMN model.
   */
  public void validateC8FlowNodes(String xmlString, String activityId) {
    var bpmnModelInstance = parseBpmnModel(xmlString);
    var element = bpmnModelInstance.getModelElementById(activityId);
    if (element == null) {
      throw new IllegalStateException(String.format(FLOW_NODE_NOT_EXISTS_ERROR, activityId));
    }

    // Check if it's a CallActivity and validate propagateAllParentVariables
    if (element instanceof io.camunda.zeebe.model.bpmn.instance.CallActivity callActivity) {
      var extensionElements = callActivity.getExtensionElements();
      if (extensionElements != null) {
        var calledElements = extensionElements.getElementsQuery()
            .filterByType(io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement.class)
            .list();

        for (var calledElement : calledElements) {
          String propagateAllParentVariables = calledElement.getDomElement().getAttribute("propagateAllParentVariables");
          if ("false".equalsIgnoreCase(propagateAllParentVariables)) {
            // Check if there's an explicit mapping for legacyId
            var ioMappings = extensionElements.getElementsQuery()
                .filterByType(io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping.class)
                .list();

            boolean hasLegacyIdMapping = ioMappings.stream()
                .flatMap(mapping -> mapping.getInputs().stream())
                .anyMatch(input -> LEGACY_ID_VAR_NAME.equals(input.getTarget()));

            if (!hasLegacyIdMapping) {
              throw new IllegalStateException(String.format(CALL_ACTIVITY_LEGACY_ID_ERROR, activityId));
            }
          }
        }
      }
    }
  }

  /**
   * Validates that C8 process definition exists for the given process definition ID.
   */
  public void validateC8DefinitionExists(List<ProcessDefinition> c8Definitions,
                                         String c8DefinitionId,
                                         String tenantId,
                                         String c7ProcessInstanceId) {
    if (c8Definitions.isEmpty()) {
      if (hasTenant(tenantId)) {
        throw new IllegalStateException(
            String.format(NO_C8_TENANT_DEPLOYMENT_ERROR, c8DefinitionId, tenantId, c7ProcessInstanceId));
      } else {
        throw new IllegalStateException(String.format(NO_C8_DEPLOYMENT_ERROR, c8DefinitionId, c7ProcessInstanceId));
      }
    }
  }

  /**
   * Parses BPMN model instance from XML string.
   */
  private io.camunda.zeebe.model.bpmn.BpmnModelInstance parseBpmnModel(String xmlString) {
    return callApi(() -> readModelFromStream(new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8))),
        FAILED_TO_PARSE_BPMN_MODEL);
  }

  /**
   * Checks if an activity is a multi-instance activity either by its ID suffix or its characteristics
   */
  private boolean isMultiInstanceActivity(String activityId, FlowElement element) {
    boolean isMultiInstanceBody = activityId.endsWith(MULTI_INSTANCE_BODY_SUFFIX);
    if (isMultiInstanceBody) {
      return true;
    }
    boolean hasMultiInstanceCharacteristics = element instanceof Activity activity
        && activity.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics;
    return hasMultiInstanceCharacteristics;
  }

  /**
   * This method iterates over all the activity instances of the root process instance and its
   * children until it either finds an activityInstance that cannot be migrated or the iteration ends.
   *
   * @param c7ProcessInstanceId the C7 id of the root process instance.
   */
  public void validateProcessInstanceState(String c7ProcessInstanceId) {
    RuntimeValidatorLogs.validateC7ProcessInstance(c7ProcessInstanceId);
    c7Client.fetchAndHandleProcessInstances(processInstance -> {
      String processInstanceId = processInstance.getId();
      String c7DefinitionId = processInstance.getProcessDefinitionId();
      String c8DefinitionId = processInstance.getProcessDefinitionKey();
      String tenantId = processInstance.getTenantId();

      validateMultiTenancy(tenantId);

      var c8Definitions = c8Client.searchProcessDefinitions(c8DefinitionId, tenantId);
      validateC8DefinitionExists(c8Definitions.items(), c8DefinitionId, tenantId, processInstanceId);

      var activityInstanceTree = c7Client.getActivityInstance(processInstanceId);

      ProcessDefinition c8ProcessDefinition = c8Definitions.items().getFirst();
      String c8XmlString = c8Client.getProcessDefinitionXml(c8ProcessDefinition.getProcessDefinitionKey());

      validateC8Process(c8XmlString, c8ProcessDefinition);

      RuntimeValidatorLogs.collectingActiveDescendantActivitiesValidation(processInstanceId);
      Map<String, FlowNode> activityInstanceMap = getActiveActivityIdsById(activityInstanceTree, new HashMap<>());
      RuntimeValidatorLogs.foundActiveActivitiesToValidate(activityInstanceMap.size());

      for (FlowNode flowNode : activityInstanceMap.values()) {
        validateC7FlowNodes(c7DefinitionId, c7ProcessInstanceId, flowNode.activityId());
        validateC8FlowNodes(c8XmlString, flowNode.activityId());
      }
    }, c7ProcessInstanceId);
  }

  protected void validateMultiTenancy(String tenantId) {
    if (hasTenant(tenantId)) {
      if (properties.getTenantIds() == null || !properties.getTenantIds().contains(tenantId)) {
        throw new IllegalStateException(String.format(TENANT_ID_ERROR, tenantId));
      }
    }
  }

  /**
   * Checks if a tenant ID is present and not empty.
   */
  private boolean hasTenant(String tenantId) {
    return !StringUtils.isEmpty(tenantId);
  }
}
