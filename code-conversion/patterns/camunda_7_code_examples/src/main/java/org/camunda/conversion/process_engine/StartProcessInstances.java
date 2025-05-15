package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.variable.VariableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StartProcessInstances {

    @Autowired
    private ProcessEngine engine;

    public void startProcessByBPMNModelIdentifier(VariableMap variableMap) {
        engine.getRuntimeService().startProcessInstanceByKey("order", variableMap);
    }

    public void startProcessByBPMNModelIdentifierViaBuilder(VariableMap variableMap) {
        engine.getRuntimeService().createProcessInstanceByKey("order")
                .businessKey("some business key")
                .processDefinitionTenantId("some tenantId")
                .setVariables(variableMap)
                .execute();
    }

    public void startProcessByKeyAssignedOnDeployment(VariableMap variableMap) {
        engine.getRuntimeService().startProcessInstanceById("order:7:444f-fkd2-dyaf", "some business key", variableMap);
    }

    public void startProcessByKeyAssignedOnDeploymentViaBuilder(VariableMap variableMap) {
        engine.getRuntimeService().createProcessInstanceById("order:7:444f-fkd2-dyaf")
                .businessKey("some business key")
                .processDefinitionTenantId("some tenantId")
                .setVariables(variableMap)
                .execute();
    }

    public void startProcessByMessage(VariableMap variableMap) {
        engine.getRuntimeService().startProcessInstanceByMessage("message name", "some business key", variableMap);
    }

    public void startProcessByMessageAndProcessDefinitionId(VariableMap variableMap) {
        engine.getRuntimeService().startProcessInstanceByMessageAndProcessDefinitionId("message name", "processDefinitionId", "some business key", variableMap);
    }
}
