package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchProcessDefinitions {

    @Autowired
    private ProcessEngine engine;

    public List<ProcessDefinition> searchProcessDefinitions(String name) {
        return engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionName(name)
                .orderByTenantId()
                .list();
    }
}
