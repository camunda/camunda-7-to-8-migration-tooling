package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.api.response.ProcessInstanceEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StartProcessInstance {

    @Autowired
    private CamundaClient camundaClient;

    public ProcessInstanceEvent startProcessByBPMNModelIdentifier(String processDefinitionId, Map<String, Object> variableMap, String tenantId) {
        return camundaClient.newCreateInstanceCommand()
                .bpmnProcessId(processDefinitionId)
                .latestVersion()
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    public ProcessInstanceEvent startProcessByKeyAssignedOnDeployment(Long processDefinitionKey, Map<String, Object> variableMap, String tenantId) {
        return camundaClient.newCreateInstanceCommand()
                .processDefinitionKey(processDefinitionKey)
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    public CorrelateMessageResponse startProcessByMessage(String messageName, String correlationKey, Map<String, Object> variableMap, String tenantId) {
        return camundaClient.newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .variables(variableMap)
                .tenantId(tenantId)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
}
