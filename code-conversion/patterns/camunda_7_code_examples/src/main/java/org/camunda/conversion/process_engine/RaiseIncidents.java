package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RaiseIncidents {

    @Autowired
    private ProcessEngine engine;

    public void raiseIncident() {
        engine.getRuntimeService().createIncident("some type", "some executionId", "some configuration", "some message");
    }
}
