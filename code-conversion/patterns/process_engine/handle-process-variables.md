# Handle Variables

The following patterns focus on methods how to handle variables in Camunda 7 and how they convert to Camunda 8.

## Getting Variables

### ProcessEngine (Camunda 7)

#### Java Object API

```java
    public void getVariableJavaObjectAPI() {
        int amount = (int) engine.getRuntimeService().getVariable("executionId", "amount");
    }
```

```java
    public void getVariablesJavaObjectAPI(List<String> variableNames) {
        Map<String, Object> variableMap = engine.getRuntimeService().getVariables("executionId", variableNames);
    }
```

#### Typed Value API

```java
    public void getVariableTypedValueAPI() {
        TypedValue typedVariable = engine.getRuntimeService().getVariableTyped("executionId", "variableName");
    }
```

```java
    public void getVariablesTypedValueAPI(List<String> variableNames) {
        VariableMap variableMap = engine.getRuntimeService().getVariablesTyped("executionId", variableNames, true);
    }
```

## CamundaClient (Camunda 8)

```java
    public void getVariable(Long processInstanceKey) {
        camundaClient.newVariableSearchRequest()
                .filter(variableFilter -> {
                    variableFilter.processInstanceKey(processInstanceKey);
                    variableFilter.name("amount");
                })
                .send()
                .join();
    }
```

```java
    public void getVariables(Long processInstanceKey, List<String> variableNames) {
        camundaClient.newVariableSearchRequest()
                .filter(variableFilter -> {
                    variableFilter.processInstanceKey(processInstanceKey);
                    variableFilter.name(name -> name.in(variableNames));
                })
                .send()
                .join();
    }
```

-   various filter, sorting and pagination options
-   for more information, see [the docs](https://docs.camunda.io/docs/next/apis-tools/camunda-api-rest/specifications/search-variables/)

## Setting Variables

### ProcessEngine (Camunda 7)

#### Java Object API

```java
    public void setVariableJavaObjectAPI(int amount) {
        engine.getRuntimeService().setVariable("executionId", "variableName", amount);
    }
```

```java
    public void setVariablesJavaObjectAPI(Map<String, Object> variableMap) {
        engine.getRuntimeService().setVariables("executionId", variableMap);
    }
```

```java
    public void setVariablesAsyncJavaObjectAPI(List<String> processInstanceIds, Map<String, Object> variableMap) {
        engine.getRuntimeService().setVariablesAsync(processInstanceIds, variableMap);
    }
```

-   async: multiple variables can be set for multiple process instances
-   async: various ProcessInstanceQueries possible

#### Typed Value API

```java
    public void setVariableTypedValueAPI(int amount) {
        IntegerValue amountTyped = Variables.integerValue(amount);
        engine.getRuntimeService().setVariable("executionId", "variableName", amountTyped);
    }
```

```java
    public void setVariablesTypedValueAPI(int amount, String name) {
        IntegerValue amountTyped = Variables.integerValue(amount);
        StringValue nameTyped = Variables.stringValue(name);
        VariableMap variableMap = Variables.putValueTyped("amount", amountTyped);
        variableMap.putValueTyped("name", nameTyped);
        engine.getRuntimeService().setVariables("executionId", variableMap);
    }
```

```java
    public void setVariablesAsyncTypesValueAPI(List<String> processInstanceIds, int amount, String name) {
        IntegerValue amountTyped = Variables.integerValue(amount);
        StringValue nameTyped = Variables.stringValue(name);
        VariableMap variableMap = Variables.putValueTyped("amount", amountTyped);
        variableMap.putValueTyped("name", nameTyped);
        engine.getRuntimeService().setVariablesAsync(processInstanceIds, variableMap);
    }
```

-   async: multiple variables can be set for multiple process instances
-   async: various ProcessInstanceQueries possible

### CamundaClient (Camunda 8)

```java
    public void setVariable(Long elementInstanceKey, int amount) {
        camundaClient.newSetVariablesCommand(elementInstanceKey)
            .variable("amount", amount).send().join();
    }
```

```java
    public void setVariables(Long elementInstanceKey, Map<String, Object> variableMap) {
        camundaClient.newSetVariablesCommand(elementInstanceKey)
    		.variables(variableMap);
    }
```

-   only one element instance can be updates at a time
-   _elementInstanceKey_ can describe any process instance, scope or activity

## Deleting Variables

Deleting variables is not possible in Camunda 8.8. You can set a variable to null or empty string.
