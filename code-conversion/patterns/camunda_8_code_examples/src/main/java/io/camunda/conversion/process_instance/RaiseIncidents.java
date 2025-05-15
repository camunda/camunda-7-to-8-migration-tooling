package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RaiseIncidents {

    @Autowired
    private CamundaClient camundaClient;

    public void raiseIncident(Map<String, Object> variableMap) {
        camundaClient.newFailCommand(1235891025L)
                .retries(0)
                .errorMessage("some error message")
                .variables(variableMap)
                .send();
    }
}
