package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class CorrelateMessages {

    @Autowired
    private CamundaClient camundaClient;

    public void correlateMessage(Map<String, Object> variableMap) {
        camundaClient.newCorrelateMessageCommand()
                .messageName("message name")
                .correlationKey("a correlation key")
                .variables(variableMap)
                .send();
    }

    public void publishMessage(Map<String, Object> variableMap) {
        camundaClient.newPublishMessageCommand()
                .messageName("message name")
                .correlationKey("a correlation key")
                .messageId("some messageId")
                .timeToLive(Duration.ofSeconds(30000L))
                .variables(variableMap)
                .send();
    }
}
