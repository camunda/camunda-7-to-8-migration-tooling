package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.IntegerValue;
import org.camunda.bpm.engine.variable.value.StringValue;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class HandleProcessVariables {

    @Autowired
    private ProcessEngine engine;

    public void getVariableJavaObjectAPI() {
        int amount = (int) engine.getRuntimeService().getVariable("executionId", "amount");
    }

    public void getVariables(List<String> variableNames) {
        Map<String, Object> variableMap = engine.getRuntimeService().getVariables("executionId", variableNames);
    }

    public void getVariableTyped() {
        TypedValue typedVariable = engine.getRuntimeService().getVariableTyped("executionId", "variableName");
    }

    public void getVariablesTyped(List<String> variableNames) {
        VariableMap variableMap = engine.getRuntimeService().getVariablesTyped("executionId", variableNames, true);
    }

    public void setVariableJavaObjectAPI(int amount) {
        engine.getRuntimeService().setVariable("executionId", "variableName", amount);
    }

    public void setVariableTypedValueAPI(int amount) {
        IntegerValue amountTyped = Variables.integerValue(amount);
        engine.getRuntimeService().setVariable("executionId", "variableName", amountTyped);
    }

    public void setVariablesJavaObjectAPI(Map<String, Object> variableMap) {
        engine.getRuntimeService().setVariables("executionId", variableMap);
    }

    public void setVariablesTypedValueAPI(int amount, String name) {
        IntegerValue amountTyped = Variables.integerValue(amount);
        StringValue nameTyped = Variables.stringValue(name);
        VariableMap variableMap = Variables.putValueTyped("amount", amountTyped);
        variableMap.putValueTyped("name", nameTyped);
        engine.getRuntimeService().setVariables("executionId", variableMap);
    }

    public void setVariablesAsyncJavaObjectAPI(List<String> processInstanceIds, Map<String, Object> variableMap) {
        engine.getRuntimeService().setVariablesAsync(processInstanceIds, variableMap);
    }

    public void setVariablesAsyncTypesValueAPI(List<String> processInstanceIds, int amount, String name) {
        IntegerValue amountTyped = Variables.integerValue(amount);
        StringValue nameTyped = Variables.stringValue(name);
        VariableMap variableMap = Variables.putValueTyped("amount", amountTyped);
        variableMap.putValueTyped("name", nameTyped);
        engine.getRuntimeService().setVariablesAsync(processInstanceIds, variableMap);
    }
}
