package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.variable.VariableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CancelProcessInstances {

    @Autowired
    private ProcessEngine engine;

    public void cancelProcessInstance() {
        engine.getRuntimeService().deleteProcessInstance("processInstanceId", "deleteReason");
    }

    public void cancelProcessInstances(List<String> processInstanceIds) {
        engine.getRuntimeService().deleteProcessInstances(processInstanceIds, "deleteReason", true, true);
    }

    public void cancelProcessInstancesAsync(List<String> processInstanceIds) {
        engine.getRuntimeService().deleteProcessInstancesAsync(processInstanceIds, "deleteReason");
    }
}
