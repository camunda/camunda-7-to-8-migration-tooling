# Raise Incidents

The following patterns focus on methods how to raise incidents in Camunda 7 and how they convert to Camunda 8.

## ProcessEngine (Camunda 7)

```java
	public void raiseIncident() {
        engine.getRuntimeService().createIncident("some type", "some executionId", "some configuration", "some message");
    }
```

-   incidents should be raised in the context of a [JavaDelegate](../java-spring-delegate/) or [External Task Worker](../java-spring-external-task-worker/)

## CamundaClient (Camunda 8)

```java
	public void raiseIncident(Map<String, Object> variableMap) {
        camundaClient.newFailCommand(1235891025L)
            .retries(0)
            .errorMessage("some error message")
            .variables(variableMap)
            .send();
	}
```

-   incidents should be raised in the context of a job worker, see code conversion examples for a [JavaDelegate](../java-spring-delegate/) or [External Task Worker](../java-spring-external-task-worker/)
