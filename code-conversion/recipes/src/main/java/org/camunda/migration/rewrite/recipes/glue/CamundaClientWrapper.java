package org.camunda.migration.rewrite.recipes.glue;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.BroadcastSignalResponse;
import io.camunda.client.api.response.CancelProcessInstanceResponse;
import io.camunda.client.api.response.CorrelateMessageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CamundaClientWrapper {

    @Autowired
    private CamundaClient camundaClient;

    public BroadcastSignalResponse broadcastSignal(String signalName) {
        return camundaClient.newBroadcastSignalCommand()
                .signalName(signalName)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    public BroadcastSignalResponse broadcastSignalWithVariables(String signalName, Map<String, Object> variableMap) {
        return camundaClient.newBroadcastSignalCommand()
                .signalName(signalName)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    public BroadcastSignalResponse broadcastSignalWithTenantId(String signalName, String tenantId) {
        return camundaClient.newBroadcastSignalCommand()
                .signalName(signalName)
                .tenantId(tenantId)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    public BroadcastSignalResponse broadcastSignalWithTenantIdWithVariables(String signalName, String tenantId, Map<String, Object> variableMap) {
        return camundaClient.newBroadcastSignalCommand()
                .signalName(signalName)
                .tenantId(tenantId)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    public CancelProcessInstanceResponse cancelProcessInstance(Long processInstanceKey) {
        return camundaClient.newCancelInstanceCommand(processInstanceKey)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    public CorrelateMessageResponse correlateMessage(String messageName, String correlationKey) {
        return camundaClient.newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    public CorrelateMessageResponse correlateMessageWithTenantId(String messageName, String correlationKey, String tenantId) {
        return camundaClient.newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .tenantId(tenantId)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    public CorrelateMessageResponse correlateMessageWithVariables(String messageName, String correlationKey, Map<String, Object> variableMap) {
        return camundaClient.newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    public CorrelateMessageResponse correlateMessageWithTenantIdWithVariables(String messageName, String correlationKey, String tenantId, Map<String, Object> variableMap) {
        return camundaClient.newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .tenantId(tenantId)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
}
