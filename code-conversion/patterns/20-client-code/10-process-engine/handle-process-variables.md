# Handle Variables

The following patterns focus on methods how to handle variables in Camunda 7 and how they convert to Camunda 8.

## Getting Variables

### ProcessEngine (Camunda 7)

```java
    public Object getVariableJavaObjectAPI(String executionId, String variableName) {
        return engine.getRuntimeService().getVariable(executionId, variableName);
    }
```

```java
    public TypedValue getVariableTypedValueAPI(String executionId, String variableName) {
        return engine.getRuntimeService().getVariableTyped(executionId, variableName);
    }
```

```java
    public Map<String, Object> getVariablesJavaObjectAPI(String executionId, List<String> variableNames) {
        return engine.getRuntimeService().getVariables(executionId, variableNames);
    }
```

```java
    public VariableMap getVariablesTypedValueAPI(String executionId, List<String> variableNames) {
        return engine.getRuntimeService().getVariablesTyped(executionId, variableNames, true);
    }
```

## CamundaClient (Camunda 8)

```java
    public Variable getVariable(Long processInstanceKey, String variableName) {
        return camundaClient.newVariableSearchRequest()
                .filter(variableFilter -> variableFilter.processInstanceKey(processInstanceKey).name(variableName))
                .send()
                .join() // add reactive response and error handling instead of join()
                .items()
                .get(0);
    }
```

```java
    public List<Variable> getVariables(Long processInstanceKey, List<String> variableNames) {
        return camundaClient.newVariableSearchRequest()
                .filter(variableFilter -> variableFilter.processInstanceKey(processInstanceKey).name(name -> name.in(variableNames)))
                .send()
                .join() // add reactive response and error handling instead of join()
                .items();
    }
```

-   various filter, sorting and pagination options
-   for more information, see [the docs](https://docs.camunda.io/docs/next/apis-tools/camunda-api-rest/specifications/search-variables/)

## Setting Variables

### ProcessEngine (Camunda 7)

#### Java Object API

```java
    public void setVariableJavaObjectAPI(String executionId, int amount) {
        engine.getRuntimeService().setVariable(executionId, "amount", amount);
    }
```

```java
    public void setVariableTypedValueAPI(String executionId, int amount) {
        IntegerValue amountTyped = Variables.integerValue(amount);
        engine.getRuntimeService().setVariable(executionId, "amount", amountTyped);
    }
```

```java
    public void setVariablesJavaObjectAPI(String executionId, Map<String, Object> variableMap) {
        engine.getRuntimeService().setVariables(executionId, variableMap);
    }
```

```java
    public void setVariablesTypedValueAPI(String executionId, int amount, String name) {
        IntegerValue amountTyped = Variables.integerValue(amount);
        StringValue nameTyped = Variables.stringValue(name);
        VariableMap variableMap = Variables.createVariables().putValueTyped("amount", amountTyped);
        variableMap.putValueTyped("name", nameTyped);
        engine.getRuntimeService().setVariables(executionId, variableMap);
    }
```

```java
    public Batch setVariablesAsyncJavaObjectAPI(List<String> processInstanceIds, Map<String, Object> variableMap) {
        return engine.getRuntimeService().setVariablesAsync(processInstanceIds, variableMap);
    }
```

```java
    public Batch setVariablesAsyncTypesValueAPI(List<String> processInstanceIds, int amount, String name) {
        IntegerValue amountTyped = Variables.integerValue(amount);
        StringValue nameTyped = Variables.stringValue(name);
        VariableMap variableMap = Variables.createVariables().putValueTyped("amount", amountTyped);
        variableMap.putValueTyped("name", nameTyped);
        return engine.getRuntimeService().setVariablesAsync(processInstanceIds, variableMap);
    }
```

-   async: multiple variables can be set for multiple process instances
-   async: various ProcessInstanceQueries possible

### CamundaClient (Camunda 8)

```java
    public SetVariablesResponse setVariable(Long elementInstanceKey, int amount) {
        return camundaClient.newSetVariablesCommand(elementInstanceKey)
                .variable("amount", amount)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

```java
    public SetVariablesResponse setVariables(Long elementInstanceKey, Map<String, Object> variableMap) {
        return camundaClient.newSetVariablesCommand(elementInstanceKey)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

-   only one element instance can be updates at a time
-   _elementInstanceKey_ can describe any process instance, scope or activity

## Setting and Getting a Custom Object

### Process Engine (Camunda 7)

#### Getting a Custom Object Variable

```java
    public CustomObject getCustomVariableJavaObjectAPI(String executionId, String customVariableName) {
        return (CustomObject) engine.getRuntimeService().getVariable(executionId, customVariableName);
    }
```

```java
    public CustomObject getCustomVariableTypedValuetAPI(String executionId, String customVariableName) {
        ObjectValue objectValue = engine.getRuntimeService().getVariableTyped(executionId, customVariableName);
        return (CustomObject) objectValue.getValue();
    }
```

#### Setting a Custom Object Variable

```java
    public void setCustomVariableJavaObjectAPI(String executionId, CustomObject customObject) {
        engine.getRuntimeService().setVariable(executionId, "customObject", customObject);
    }
```

```java
    public void setCustomVariableTypedValueAPI(String executionId, CustomObject customObject) {
        ObjectValue objectValue = Variables.objectValue(customObject).create();
        engine.getRuntimeService().setVariable(executionId, "customObject", objectValue);
    }
```

### CamundaClient (Camunda 8)

#### Getting a Custom Object Variable

```java
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
```

-   the value is returned as a JSON string

#### Setting a Custom Object Variable

```java
    public SetVariablesResponse setCustomVariable(Long elementInstanceKey, CustomObject customObject) {
        return camundaClient.newSetVariablesCommand(elementInstanceKey)
                .variable("customObject", customObject)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

-   the object can directly be added here

## Deleting Variables

Deleting variables is not possible in Camunda 8.8. You can set a variable to null or empty string.
