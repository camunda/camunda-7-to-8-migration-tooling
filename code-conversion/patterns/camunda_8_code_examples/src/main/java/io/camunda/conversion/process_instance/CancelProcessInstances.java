package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CancelProcessInstances {

    @Autowired
    private CamundaClient camundaClient;

    public void cancelProcessInstance() {
        camundaClient.newCancelInstanceCommand(2391324L).send();
    }
}
