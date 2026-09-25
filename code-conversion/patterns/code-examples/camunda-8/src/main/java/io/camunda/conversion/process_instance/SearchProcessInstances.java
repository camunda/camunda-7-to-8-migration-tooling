/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SearchProcessInstances {

    @Autowired
    private CamundaClient camundaClient;

    public ProcessInstance findSingleActiveByVariable(
            String processDefinitionId, String variableName, Object variableValue) {
        var response =
                camundaClient
                        .newProcessInstanceSearchRequest()
                        .page(page -> page.limit(1))
                        .filter(
                                filter ->
                                        filter
                                                .processDefinitionId(processDefinitionId)
                                                .variables(
                                                        Collections.singletonMap(
                                                                variableName, variableValue))
                                                .state(ProcessInstanceState.ACTIVE))
                        .send()
                        .join();

        if (response.page().totalItems() > 1) {
            throw new IllegalStateException(
                    "Process-instance query returned more than one result");
        }
        return response.items().stream().findFirst().orElse(null);
    }
}
