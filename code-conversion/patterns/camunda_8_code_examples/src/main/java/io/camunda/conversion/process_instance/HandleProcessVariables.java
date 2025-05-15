package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.VariableFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class HandleProcessVariables {

    @Autowired
    private CamundaClient camundaClient;

    public void getVariable(Long processInstanceKey) {
        camundaClient.newVariableSearchRequest()
                .filter(variableFilter -> variableFilter.processInstanceKey(processInstanceKey).name("amount"))
                .send()
                .join();
    }

    public void getVariables(Long processInstanceKey, List<String> variableNames) {
        camundaClient.newVariableSearchRequest()
                .filter(variableFilter -> variableFilter.processInstanceKey(processInstanceKey).name(name -> name.in(variableNames)))
                .send()
                .join();
    }

    public void setVariable(Long elementInstanceKey, int amount) {
        camundaClient.newSetVariablesCommand(elementInstanceKey)
                .variable("amount", amount).send().join();
    }

    public void setVariables(Long elementInstanceKey, Map<String, Object> variableMap) {
        camundaClient.newSetVariablesCommand(elementInstanceKey)
                .variables(variableMap);
    }
}
