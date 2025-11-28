package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.batch.Batch;
import org.camunda.bpm.engine.variable.VariableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CancelProcessInstances {

    @Autowired
    private ProcessEngine engine;

    public void cancelProcessInstance(String processInstanceId, String deleteReason) {
        engine.getRuntimeService().deleteProcessInstance(processInstanceId, deleteReason);
    }

    public void cancelProcessInstances(List<String> processInstanceIds, String deleteReason, boolean skipCustomListeners, boolean externallyTerminated) {
        engine.getRuntimeService().deleteProcessInstances(processInstanceIds, deleteReason, skipCustomListeners, externallyTerminated);
    }

    public Batch cancelProcessInstancesAsync(List<String> processInstanceIds, String deleteReason) {
        return engine.getRuntimeService().deleteProcessInstancesAsync(processInstanceIds, deleteReason);
    }
}
