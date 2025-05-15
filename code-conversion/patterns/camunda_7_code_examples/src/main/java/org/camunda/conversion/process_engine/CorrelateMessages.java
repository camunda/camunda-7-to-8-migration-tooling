package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.variable.VariableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class CorrelateMessages {

    @Autowired
    private ProcessEngine engine;

    public void correlateMessage(VariableMap variableMap) {
        engine.getRuntimeService().correlateMessage("message name", "a business key", variableMap);
    }

    public void messageEventReceived(VariableMap variableMap) {
        engine.getRuntimeService().messageEventReceived("message name", "execution id", variableMap);
    }

    public void correlateMessageViaBuilder(VariableMap variableMap) {
        engine.getRuntimeService().createMessageCorrelation("message name")
                .processInstanceBusinessKey("a business key")
                .tenantId("tenantId")
                .setVariables(variableMap)
                .correlate();
    }

    public void correlateMessagesViaBuilderAsync(List<String> processInstanceId, VariableMap variableMap) {
        engine.getRuntimeService().createMessageCorrelationAsync("message name")
                .processInstanceIds(processInstanceId)
                .setVariables(variableMap)
                .correlateAllAsync();
    }
}
