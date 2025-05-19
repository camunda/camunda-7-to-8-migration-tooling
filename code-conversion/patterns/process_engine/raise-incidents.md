# Raise Incidents

The following patterns focus on methods how to raise incidents in Camunda 7 and how they convert to Camunda 8.

## ProcessEngine (Camunda 7)

```java
    public Incident raiseIncident(String type, String executionId, String configuration, String message) {
        return engine.getRuntimeService().createIncident(type, executionId, configuration, message);
    }
```

-   incidents should be raised in the context of a [JavaDelegate](../java-spring-delegate/) or [External Task Worker](../java-spring-external-task-worker/)

## CamundaClient (Camunda 8)

```java
    public FailJobResponse raiseIncident(Long jobKey, String errorMessage, Map<String, Object> variableMap) {
        return camundaClient.newFailCommand(jobKey)
                .retries(0)
                .errorMessage(errorMessage)
                .variables(variableMap)
                .send()
                .join(); // add reactive response and error handling instead of join()
    }
```

-   incidents should be raised in the context of a job worker, see code conversion examples for a [JavaDelegate](../java-spring-delegate/) or [External Task Worker](../java-spring-external-task-worker/)
