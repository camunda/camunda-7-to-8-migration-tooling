# Listeners (Execution Listener / Task Listener) → Camunda 8 Listeners

In Camunda 7, *execution listeners* (`org.camunda.bpm.engine.delegate.ExecutionListener`) and *task listeners* (`org.camunda.bpm.engine.delegate.TaskListener`) run Java code synchronously inside the engine when an element or user task reaches a lifecycle event. They are common for assignment logic, auditing, notifications, and variable preparation.

Camunda 8 has equivalents, implemented as **job workers** instead of in-engine Java callbacks:

| Camunda 7                                          | Camunda 8                                                                  |
| -------------------------------------------------- | -------------------------------------------------------------------------- |
| `ExecutionListener` (`start` / `end` events)        | [Execution listeners](https://docs.camunda.io/docs/components/concepts/execution-listeners/) (8.6+), `zeebe:executionListener` with `eventType` `start` / `end` |
| `TaskListener` (`create`, `assignment`, `complete`, `update`, `delete`) | [User task listeners](https://docs.camunda.io/docs/components/concepts/user-task-listeners/) (8.8+), `zeebe:taskListener` with `eventType` `creating`, `assigning`, `updating`, `completing`, `canceling` |
| Global listeners via `ProcessEnginePlugin` / parse listeners | [Global user task listeners](https://docs.camunda.io/docs/components/concepts/global-user-task-listeners/) (8.9+), configured cluster-wide via configuration files or environment variables — no BPMN change needed |

Key conceptual differences:

-   Camunda 8 listeners are **blocking jobs**: the lifecycle transition waits until the listener job completes. Failures create incidents just like service task jobs.
-   Listener code runs in *your application* (job worker), not inside the engine — no access to engine internals or a `DelegateExecution`/`DelegateTask` object.
-   User task listeners can **correct** task attributes (assignee, due date, candidate groups, priority) and **deny** lifecycle transitions — covering the typical C7 task listener use cases.
-   Execution listener jobs cannot throw BPMN errors.
-   Limitation: execution listeners on a multi-instance body run *after* the collection is evaluated — the C7 pattern of computing the collection variable in a listener on the multi-instance activity does not work. Compute the collection in a preceding service task instead, and flag such cases during assessment.

## Execution Listener → Execution Listener Job Worker (Spring)

### Camunda 7: Execution Listener

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

### Camunda 8: Execution Listener (Job Worker)

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

## Task Listener → User Task Listener Job Worker (Spring)

### Camunda 7: Task Listener

A Spring bean implementing `TaskListener`, attached to a user task:

```java
@Component
public class AssignmentListener implements TaskListener {

    @Override
    public void notify(DelegateTask delegateTask) {
        String teamManager = (String) delegateTask.getVariable("teamManager");
        delegateTask.setAssignee(teamManager);
        delegateTask.setPriority(80);
    }
}
```

```xml
<bpmn:userTask id="approve-order">
  <bpmn:extensionElements>
    <camunda:taskListener event="create" delegateExpression="${assignmentListener}" />
  </bpmn:extensionElements>
</bpmn:userTask>
```

### Camunda 8: User Task Listener (Job Worker)

Available since **Camunda 8.8**. Define a task listener with a job `type` on the user task:

```xml
<bpmn:userTask id="approve-order">
  <bpmn:extensionElements>
    <zeebe:userTask />
    <zeebe:taskListeners>
      <zeebe:taskListener eventType="creating" type="assign-team-manager" retries="3" />
    </zeebe:taskListeners>
  </bpmn:extensionElements>
</bpmn:userTask>
```

Implement it as a job worker that completes the listener job with a **job result**. Use `autoComplete = false` and complete the command with corrections:

```java
@Component
public class AssignmentListenerWorker {

    @JobWorker(type = "assign-team-manager", autoComplete = false)
    public void handle(JobClient jobClient, ActivatedJob job) {
        String teamManager = (String) job.getVariable("teamManager");

        jobClient
            .newCompleteCommand(job)
            .withResult(r -> r.forUserTask()
                .correctAssignee(teamManager)
                .correctPriority(80))
            .send()
            .join();
    }
}
```

### Denying a Lifecycle Transition

Camunda 7 task listeners often veto transitions by throwing an exception. In Camunda 8, deny explicitly (supported for `assigning`, `updating`, and `completing` events):

```java
    @JobWorker(type = "validate-completion", autoComplete = false)
    public void handle(JobClient jobClient, ActivatedJob job) {
        boolean valid = validate(job.getVariablesAsMap());

        jobClient
            .newCompleteCommand(job)
            .withResult(r -> r.forUserTask()
                .deny(!valid)
                .deniedReason("Policy violation"))
            .send()
            .join();
    }
```

-   event mapping: `create` → `creating`, `assignment` → `assigning`, `update` → `updating`, `complete` → `completing`, `delete` → `canceling`; the C7 `timeout` listener event has no equivalent — model a boundary timer event instead
-   correctable attributes: assignee, due date, follow-up date, candidate users, candidate groups, priority
-   corrections and `deny(true)` cannot be combined in one job result
-   the listener is blocking: the task transition waits until the listener job completes
-   since **Camunda 8.9**, [global user task listeners](https://docs.camunda.io/docs/components/concepts/global-user-task-listeners/) can be configured cluster-wide (configuration file or environment variables) — the right target for C7 patterns that registered listeners globally via `ProcessEnginePlugin` or BPMN parse listeners, e.g. for SLAs, governance, or notifications across all processes
-   in tests, use Camunda Process Test's `processTestContext.completeJobOfUserTaskListener(...)` to simulate listener behavior
