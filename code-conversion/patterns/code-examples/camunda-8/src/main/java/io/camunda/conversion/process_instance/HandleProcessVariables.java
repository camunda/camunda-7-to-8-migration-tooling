package io.camunda.conversion.process_instance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.SetVariablesResponse;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.response.Variable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class HandleProcessVariables {

    @Autowired
    private CamundaClient camundaClient;

    public Variable getVariable(Long processInstanceKey, String variableName) {
        return camundaClient.newVariableSearchRequest()
                .filter(variableFilter -> variableFilter.processInstanceKey(processInstanceKey).name(variableName))
                .send()
                .join() // add reactive response and error handling instead of join()
                .items()
                .get(0);
    }

    public List<Variable> getVariables(Long processInstanceKey, List<String> variableNames) {
        return camundaClient.newVariableSearchRequest()
                .filter(variableFilter -> variableFilter.processInstanceKey(processInstanceKey).name(name -> name.in(variableNames)))
                .send()
                .join() // add reactive response and error handling instead of join()
                .items();
    }

    public SetVariablesResponse setVariable(Long elementInstanceKey, int amount) {
        return camundaClient.newSetVariablesCommand(elementInstanceKey)
                .variable("amount", amount)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    public SetVariablesResponse setVariables(Long elementInstanceKey, Map<String, Object> variableMap) {
        return camundaClient.newSetVariablesCommand(elementInstanceKey)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    // custom variable

    public CustomObject getCustomVariable(Long processInstanceKey, String customVariableName) throws JsonProcessingException {
        Variable variable = camundaClient.newVariableSearchRequest()
                .filter(variableFilter -> variableFilter.processInstanceKey(processInstanceKey).name(customVariableName))
                .send()
                .join() // add reactive response and error handling instead of join()
                .items()
                .get(0);

        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(variable.getValue(), CustomObject.class);
    }

    public SetVariablesResponse setCustomVariable(Long elementInstanceKey, CustomObject customObject) {
        return camundaClient.newSetVariablesCommand(elementInstanceKey)
                .variable("customObject", customObject)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }

    private class CustomObject {
        private String someString;

        private Long someLong;

        public CustomObject(String someString, Long someLong) {
            this.someString = someString;
            this.someLong = someLong;
        }
    }
}
