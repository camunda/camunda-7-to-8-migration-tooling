/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.VariableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StartProcessInstances {

    @Autowired
    private ProcessEngine engine;

    public ProcessInstance startProcessByBPMNModelIdentifier(String processDefinitionKey, VariableMap variableMap) {
        return engine.getRuntimeService().startProcessInstanceByKey(processDefinitionKey, variableMap);
    }

    public ProcessInstance startProcessByBPMNModelIdentifierViaBuilder(String processInstanceKey, String businessKey, String tenantId, VariableMap variableMap) {
        return engine.getRuntimeService().createProcessInstanceByKey(processInstanceKey)
                .businessKey(businessKey)
                .processDefinitionTenantId(tenantId)
                .setVariables(variableMap)
                .execute();
    }

    public ProcessInstance startProcessByKeyAssignedOnDeployment(String processDefinitionId, String businessKey, VariableMap variableMap) {
        return engine.getRuntimeService().startProcessInstanceById(processDefinitionId, businessKey, variableMap);
    }

    public ProcessInstance startProcessByKeyAssignedOnDeploymentViaBuilder(String processDefinitionId, String businessKey, String tenantId, VariableMap variableMap) {
        return engine.getRuntimeService().createProcessInstanceById(processDefinitionId)
                .businessKey(businessKey)
                .processDefinitionTenantId(tenantId)
                .setVariables(variableMap)
                .execute();
    }

    public ProcessInstance startProcessByMessage(String messageName, String businessKey, VariableMap variableMap) {
        return engine.getRuntimeService().startProcessInstanceByMessage(messageName, businessKey, variableMap);
    }

    public ProcessInstance startProcessByMessageAndProcessDefinitionId(String messageName, String processDefinitionId, String businessKey, VariableMap variableMap) {
        return engine.getRuntimeService().startProcessInstanceByMessageAndProcessDefinitionId(messageName, processDefinitionId, businessKey, variableMap);
    }
}
