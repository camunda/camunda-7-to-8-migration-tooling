/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.api.response.PublishMessageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class CorrelateMessages {

    @Autowired
    private CamundaClient camundaClient;

    public Long correlateMessage(String messageName, String correlationKey, Map<String, Object> variableMap) {
        CorrelateMessageResponse correlateMessageResponse = camundaClient.newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()

        return correlateMessageResponse.getProcessInstanceKey();
    }

    public PublishMessageResponse publishMessage(String messageName, String correlationKey, String messageId, Map<String, Object> variableMap) {
        return camundaClient.newPublishMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .messageId(messageId)
                .timeToLive(Duration.ofSeconds(30000L))
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
}
