package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.FailJobResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RaiseIncidents {

    @Autowired
    private CamundaClient camundaClient;

    public FailJobResponse raiseIncident(Long jobKey, String errorMessage, Map<String, Object> variableMap) {
        return camundaClient.newFailCommand(jobKey)
                .retries(0)
                .errorMessage(errorMessage)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
}
