package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.BroadcastSignalResponse;
import io.camunda.client.api.response.DeleteResourceResponse;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.search.response.DecisionDefinition;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HandleResources {

    @Autowired
    private CamundaClient camundaClient;

    public DeploymentEvent deployBPMNModel(String tenantId, String bpmnModelName, BpmnModelInstance bpmnModelInstance) {
        return camundaClient.newDeployResourceCommand()
                .addProcessModel(bpmnModelInstance, bpmnModelName)
                .send()
                .join();
    }

    public DeploymentEvent deployMultipleResourcesByFileName(String fileName1, String fileName2) {
        return camundaClient.newDeployResourceCommand()
                .addResourceFromClasspath(fileName1)
                .addResourceFromClasspath(fileName2)
                .send()
                .join();
    }

    public DeleteResourceResponse deleteProcessDefinition(Long processDefinitionKey) {
        return camundaClient.newDeleteResourceCommand(processDefinitionKey)
                .send()
                .join();
    }

    public DecisionDefinition getDecisionDefinition(Long decisionDefinitionKey) {
        return camundaClient.newDecisionDefinitionGetRequest(decisionDefinitionKey)
                .send()
                .join();
    }
}
