package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.variable.VariableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BroadcastSignals {

    @Autowired
    private ProcessEngine engine;

    @Autowired
    private RuntimeService runtimeService;

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
