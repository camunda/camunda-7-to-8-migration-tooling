/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.CancelProcessInstanceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CancelProcessInstances {

    @Autowired
    private CamundaClient camundaClient;

    public CancelProcessInstanceResponse cancelProcessInstance(Long processInstanceKey) {
        return camundaClient.newCancelInstanceCommand(processInstanceKey)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
}
