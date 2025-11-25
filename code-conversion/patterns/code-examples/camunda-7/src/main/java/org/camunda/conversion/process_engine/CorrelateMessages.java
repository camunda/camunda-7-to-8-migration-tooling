package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.batch.Batch;
import org.camunda.bpm.engine.variable.VariableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class CorrelateMessages {

    @Autowired
    private ProcessEngine engine;

    public void correlateMessage(String messageName, String businessKey, VariableMap variableMap) {
        engine.getRuntimeService().correlateMessage(messageName, businessKey, variableMap);
    }

    public void correlateMessageToOneExecution(String messageName, String executionId, VariableMap variableMap) {
        engine.getRuntimeService().messageEventReceived(messageName, executionId, variableMap);
    }

    public void correlateMessageViaBuilder(String messageName, String businessKey, String tenantId, VariableMap variableMap) {
        engine.getRuntimeService().createMessageCorrelation(messageName)
                .processInstanceBusinessKey(businessKey)
                .tenantId(tenantId)
                .setVariables(variableMap)
                .correlate();
    }

    public Batch correlateMessagesViaBuilderAsync(String messageName, List<String> processInstanceId, VariableMap variableMap) {
        return engine.getRuntimeService().createMessageCorrelationAsync(messageName)
                .processInstanceIds(processInstanceId)
                .setVariables(variableMap)
                .correlateAllAsync();
    }
}
