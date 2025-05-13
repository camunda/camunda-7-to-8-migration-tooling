package org.camunda.conversion.start_process_instance;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StartProcessInstance {

    @Autowired
    private ProcessEngine engine;

    public void startProcessByBPMNModelIdentifierJavaObjectAPI(VariableMap variableMap) {
        engine.getRuntimeService().startProcessInstanceByKey("order", variableMap);
    }

    public void startProcessByKeyAssignedOnDeploymentJavaObjectAPI(VariableMap variableMap) {
        engine.getRuntimeService().startProcessInstanceById("order:7:444f-fkd2-dyaf", "some business key", variableMap);
    }

    public void startProcessByMessage(VariableMap variableMap) {
        engine.getRuntimeService().startProcessInstanceByMessage("message name", "some business key", variableMap);
    }

    public void startProcessByMessageAndProcessDefinitionId(VariableMap variableMap) {
        engine.getRuntimeService().startProcessInstanceByMessageAndProcessDefinitionId("message name", "processDefinitionId", "some business key", variableMap);
    }
}
