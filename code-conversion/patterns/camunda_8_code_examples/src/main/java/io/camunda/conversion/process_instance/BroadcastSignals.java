package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class BroadcastSignals {

    @Autowired
    private CamundaClient camundaClient;

    public void broadcastSignal(Map<String, Object> variableMap) {
        camundaClient.newBroadcastSignalCommand()
                .signalName("message name")
                .tenantId("tenantId")
                .variables(variableMap)
                .send();
    }
}
