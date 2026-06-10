# Task Listener &#8594; User Task Listener Job Worker (Spring)

## Camunda 7: Task Listener

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

## Camunda 8: User Task Listener (Job Worker)

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

-   event mapping: `create` &#8594; `creating`, `assignment` &#8594; `assigning`, `update` &#8594; `updating`, `complete` &#8594; `completing`, `delete` &#8594; `canceling`; the C7 `timeout` listener event has no equivalent — model a boundary timer event instead
-   correctable attributes: assignee, due date, follow-up date, candidate users, candidate groups, priority
-   corrections and `deny(true)` cannot be combined in one job result
-   the listener is blocking: the task transition waits until the listener job completes
-   since **Camunda 8.9**, [global user task listeners](https://docs.camunda.io/docs/components/concepts/global-user-task-listeners/) can be configured cluster-wide (configuration file or environment variables) — the right target for C7 patterns that registered listeners globally via `ProcessEnginePlugin` or BPMN parse listeners, e.g. for SLAs, governance, or notifications across all processes
-   in tests, use Camunda Process Test's `processTestContext.completeJobOfUserTaskListener(...)` to simulate listener behavior
