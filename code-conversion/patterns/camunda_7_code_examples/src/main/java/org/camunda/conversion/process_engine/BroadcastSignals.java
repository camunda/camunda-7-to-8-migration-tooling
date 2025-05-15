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

    public void broadcastSignal(Object signalData, VariableMap variableMap) {
        engine.getRuntimeService().signalEventReceived("signal name", "executionId", variableMap);
    }

    public void broadcastSignalViaBuilder(VariableMap variableMap) {
        engine.getRuntimeService().createSignalEvent("signal name")
                .tenantId("tenantId")
                .setVariables(variableMap)
                .send();
    }
}
