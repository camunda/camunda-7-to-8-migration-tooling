/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.variable.VariableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BroadcastSignals {

    @Autowired
    private ProcessEngine engine;

    public void broadcastSignalGlobally(String signalName, VariableMap variableMap) {
        engine.getRuntimeService().signalEventReceived(signalName, variableMap);
    }

    public void broadcastSignalToOneExecution(String signalName, String executionId, VariableMap variableMap) {
        engine.getRuntimeService().signalEventReceived(signalName, executionId, variableMap);
    }

    public void broadcastSignalGloballyViaBuilder(String signalName, String tenantId, VariableMap variableMap) {
        engine.getRuntimeService().createSignalEvent(signalName)
                .tenantId(tenantId)
                .setVariables(variableMap)
                .send();
    }

    public void broadcastSignalToOneExecutionViaBuilder(String signalName, String executionId, String tenantId, VariableMap variableMap) {
        engine.getRuntimeService().createSignalEvent(signalName)
                .executionId(executionId)
                .tenantId(tenantId)
                .setVariables(variableMap)
                .send();
    }
}
