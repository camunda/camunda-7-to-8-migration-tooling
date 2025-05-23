package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HandleResources {

    @Autowired
    private ProcessEngine engine;

    public Deployment deployBPMNModel(String tenantId, String bpmnModelName, BpmnModelInstance bpmnModelInstance) {
        return engine.getRepositoryService().createDeployment()
                .tenantId(tenantId)
                .addModelInstance(bpmnModelName, bpmnModelInstance)
                .deploy();
    }

    public Deployment deployMultipleResourcesByFileName(String fileName1, String fileName2) {
        return engine.getRepositoryService().createDeployment()
                .addClasspathResource(fileName1)
                .addClasspathResource(fileName2)
                .deploy();
    }

    public void deleteProcessDefinition(String processDefinitionId) {
        engine.getRepositoryService().deleteProcessDefinition(processDefinitionId);
    }

    public DecisionDefinition getDecisionDefinition(String decisionDefinitionId) {
        return engine.getRepositoryService().getDecisionDefinition(decisionDefinitionId);
    }
}
