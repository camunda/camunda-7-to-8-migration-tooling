package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class StartProcessInstance {

    @Autowired
    private CamundaClient camundaClient;

    public void startProcessByBPMNModelIdentifier(Map<String, Object> variableMap) {
        camundaClient.newCreateInstanceCommand()
                .bpmnProcessId("orderProcessIdentifier")
                .latestVersion()
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    public void startProcessByKeyAssignedOnDeployment(Map<String, Object> variableMap) {
        camundaClient.newCreateInstanceCommand()
                .processDefinitionKey(21653461L)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    public void startProcessByMessage(Map<String, Object> variableMap) {
        camundaClient.newCorrelateMessageCommand()
                .messageName("message name")
                .correlationKey("some correlation key")
                .variables(variableMap)
                .send()
                .join();
    }
}
