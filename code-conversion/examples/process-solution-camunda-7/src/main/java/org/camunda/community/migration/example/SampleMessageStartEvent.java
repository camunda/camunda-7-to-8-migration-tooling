/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package de.ilume.jens.delegates;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.runtime.MessageCorrelationResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component("SampleMessageStartEvent")
public class SampleMessageStartEvent implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        final String messageName = (String) execution.getVariable("messageName");
        final String processInstanceId = execution.getProcessInstanceId();
        final String parameter = (String) execution.getVariable("parameter");

        // the following lines needs to be filtered out by visiting blocks (if all references have been eliminated)
        RuntimeService runtimeService = execution.getProcessEngineServices().getRuntimeService();
        final Map<String, Object> processVariables = Map.of("parameter", parameter);

        final List<MessageCorrelationResult> triggeredProcesses =
                runtimeService.createMessageCorrelation(messageName)
                        .processInstanceId(processInstanceId)
                        .setVariables(processVariables)
                        .correlateAllWithResult();
    }
}
