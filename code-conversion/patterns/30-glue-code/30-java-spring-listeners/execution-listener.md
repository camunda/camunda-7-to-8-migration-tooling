# Execution Listener &#8594; Execution Listener Job Worker (Spring)

## Camunda 7: Execution Listener

A Spring bean implementing `ExecutionListener`, attached to an element in the BPMN XML:

```java
@Component
public class LogStartListener implements ExecutionListener {

    @Override
    public void notify(DelegateExecution execution) throws Exception {
        String orderId = (String) execution.getVariable("orderId");
        execution.setVariable("auditedAt", Instant.now().toString());
        // custom logic, e.g. audit log entry
    }
}
```

```xml
<bpmn:serviceTask id="ship-order">
  <bpmn:extensionElements>
    <camunda:executionListener event="start" delegateExpression="${logStartListener}" />
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

## Camunda 8: Execution Listener (Job Worker)

In the BPMN model, define an execution listener with a job `type` (the [Diagram Converter](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/migration-tooling/diagram-converter/) maps `camunda:executionListener` elements for you):

```xml
<bpmn:serviceTask id="ship-order">
  <bpmn:extensionElements>
    <zeebe:executionListeners>
      <zeebe:executionListener eventType="start" type="log-start-listener" retries="3" />
    </zeebe:executionListeners>
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

Implement the listener as a regular `@JobWorker` — listener jobs use the same job infrastructure as service tasks:

```java
@Component
public class LogStartListenerWorker {

    @JobWorker(type = "log-start-listener")
    public Map<String, Object> handle(@Variable String orderId) {
        // custom logic, e.g. audit log entry
        return Map.of("auditedAt", Instant.now().toString());
    }
}
```

-   `event="start"` maps to `eventType="start"`, `event="end"` maps to `eventType="end"`; the C7 `take` event on sequence flows has no equivalent — move the logic into a `start` listener of the target element or a dedicated service task
-   the listener is blocking: the element is not entered/left until the job completes; failures create incidents
-   returned variables are merged into the process instance, like for any job worker
-   throwing a BPMN error from an execution listener job is **not** supported
-   execution listeners can be defined on the process level, subprocesses, tasks, events, and gateways
-   multiple listeners of the same `eventType` execute sequentially in model order
-   listeners attached to a multi-instance *body* fire after the collection is evaluated — collection-preparing listeners must become a preceding service task
