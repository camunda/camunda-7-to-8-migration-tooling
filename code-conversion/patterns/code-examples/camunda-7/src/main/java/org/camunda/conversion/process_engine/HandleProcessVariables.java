package org.camunda.conversion.process_engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.batch.Batch;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.IntegerValue;
import org.camunda.bpm.engine.variable.value.ObjectValue;
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

    // getting variables

    public Object getVariableJavaObjectAPI(String executionId, String variableName) {
        return engine.getRuntimeService().getVariable(executionId, variableName);
    }

    public TypedValue getVariableTypedValueAPI(String executionId, String variableName) {
        return engine.getRuntimeService().getVariableTyped(executionId, variableName);
    }

    public Map<String, Object> getVariablesJavaObjectAPI(String executionId, List<String> variableNames) {
        return engine.getRuntimeService().getVariables(executionId, variableNames);
    }

    public VariableMap getVariablesTypedValueAPI(String executionId, List<String> variableNames) {
        return engine.getRuntimeService().getVariablesTyped(executionId, variableNames, true);
    }

    //setting variables

    public void setVariableJavaObjectAPI(String executionId, int amount) {
        engine.getRuntimeService().setVariable(executionId, "amount", amount);
    }

    public void setVariableTypedValueAPI(String executionId, int amount) {
        IntegerValue amountTyped = Variables.integerValue(amount);
        engine.getRuntimeService().setVariable(executionId, "amount", amountTyped);
    }

    public void setVariablesJavaObjectAPI(String executionId, Map<String, Object> variableMap) {
        engine.getRuntimeService().setVariables(executionId, variableMap);
    }

    public void setVariablesTypedValueAPI(String executionId, int amount, String name) {
        IntegerValue amountTyped = Variables.integerValue(amount);
        StringValue nameTyped = Variables.stringValue(name);
        VariableMap variableMap = Variables.createVariables().putValueTyped("amount", amountTyped);
        variableMap.putValueTyped("name", nameTyped);
        engine.getRuntimeService().setVariables(executionId, variableMap);
    }

    public Batch setVariablesAsyncJavaObjectAPI(List<String> processInstanceIds, Map<String, Object> variableMap) {
        return engine.getRuntimeService().setVariablesAsync(processInstanceIds, variableMap);
    }

    public Batch setVariablesAsyncTypesValueAPI(List<String> processInstanceIds, int amount, String name) {
        IntegerValue amountTyped = Variables.integerValue(amount);
        StringValue nameTyped = Variables.stringValue(name);
        VariableMap variableMap = Variables.createVariables().putValueTyped("amount", amountTyped);
        variableMap.putValueTyped("name", nameTyped);
        return engine.getRuntimeService().setVariablesAsync(processInstanceIds, variableMap);
    }

    // custom variable

    public CustomObject getCustomVariableJavaObjectAPI(String executionId, String customVariableName) {
        return (CustomObject) engine.getRuntimeService().getVariable(executionId, customVariableName);
    }

    public CustomObject getCustomVariableTypedValuetAPI(String executionId, String customVariableName) {
        ObjectValue objectValue = engine.getRuntimeService().getVariableTyped(executionId, customVariableName);
        return (CustomObject) objectValue.getValue();
    }

    public void setCustomVariableJavaObjectAPI(String executionId, CustomObject customObject) {
        engine.getRuntimeService().setVariable(executionId, "customObject", customObject);
    }

    public void setCustomVariableTypedValueAPI(String executionId, CustomObject customObject) {
        ObjectValue objectValue = Variables.objectValue(customObject).create();
        engine.getRuntimeService().setVariable(executionId, "customObject", objectValue);
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
