package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.Incident;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RaiseIncidents {

    @Autowired
    private ProcessEngine engine;

    //
    public Incident raiseIncident(String type, String executionId, String configuration, String message) {
        return engine.getRuntimeService().createIncident(type, executionId, configuration, message);
    }
}
