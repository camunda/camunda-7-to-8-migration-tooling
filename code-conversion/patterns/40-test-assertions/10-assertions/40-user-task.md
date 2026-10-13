# User Task Assertions

## Camunda 7

You can assert that the process is waiting at a user task, and complete it using built-in helpers:

```java
import java.util.HashMap;
import java.util.Map;

@Test
void testUserTaskIsReachedAndCompleted() {
  ProcessInstance processInstance = runtimeService()
    .startProcessInstanceByKey("example-process");

  assertThat(processInstance)
    .isWaitingAt("UserTask_Approve");

  // Optionally assert task name or assignee
  assertThat(task())
    .hasName("Approve Request")
    .isAssignedTo("demo");

  Map<String, Object> variables = new HashMap<>();
  variables.put("approved", true);
  complete(task(), variables);

  assertThat(processInstance)
    .hasPassed("UserTask_Approve")
    .isEnded();
  assertThat(processInstance).variables().containsEntry("approved", true);
}
```


## Camunda 8

## User-task API mappings

| Camunda 7 | CPT | Note |
|---|---|---|
| `task()` | No direct counterpart | Poll a search by `processInstanceKey` and `UserTaskState.CREATED` until `.hasSize(1)` passes. Inspect or complete its exact `userTaskKey`. |
| `task("A")` | `UserTaskSelectors.byElementId("A", processInstanceKey)` | `A` is the BPMN user-task element ID. Get `processInstanceKey` from the started `ProcessInstanceEvent`. If the element can repeat, poll a `CREATED` search scoped by `processInstanceKey` and `elementId` until `.hasSize(1)` passes. |
| `findId("Task name")` | `UserTaskSelectors.byTaskName("Task name", processInstanceKey)` | Use the task name selected by the source test. Get `processInstanceKey` from the started `ProcessInstanceEvent`. If the name can match multiple tasks, poll a `CREATED` search scoped by `processInstanceKey` and task name until `.hasSize(1)` passes. |
| `complete(task(), withVariables(vars))` | No direct counterpart | Poll a search by `processInstanceKey` and `UserTaskState.CREATED` until `.hasSize(1)` passes. Complete that task by its exact `userTaskKey`. |
| `complete(task("A"), withVariables(vars))` | `processTestContext.completeUserTask(UserTaskSelectors.byElementId("A", processInstanceKey), vars)` | Include the process-instance key in the selector. If the element can repeat, poll a `CREATED` search scoped by `processInstanceKey` and `elementId` until `.hasSize(1)` passes. |
| `taskService.complete(id, vars)` | `client.newCompleteUserTaskCommand(userTaskKey).variables(vars).send().join()` | When using `CamundaClient`, poll a `CREATED` search with equivalent process-instance and task-identity filters until exactly one result is visible. Complete its exact `userTaskKey`. |
| `claim(task(), "user")` | `client.newAssignUserTaskCommand(userTaskKey).assignee("user").allowOverride(false).send().join()` | Set `allowOverride(false)` so an already-assigned task still fails as it does in Camunda 7. Poll the `CREATED` search by `processInstanceKey` until exactly one task is visible before reading its key. Record a reason before dropping the assignment step. |

The CPT selector-based user-task assertions and completion APIs shown here are available from Camunda 8.8. With [Camunda Process Test (CPT)](https://docs.camunda.io/docs/apis-tools/testing/getting-started/), you can use hasActiveElements() to assert the task is active. Furthermore, there are utility methods, for example to [complete user tasks](https://docs.camunda.io/docs/apis-tools/testing/utilities/#complete-user-tasks).

User-task client searches are eventually consistent. Use a bounded poll before inspecting or completing a search result.

Note that you typically address elements by ID and not by name, which we do for illustration purposes here:

```java
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;

@Autowired
private CamundaClient client;

@Test
void testUserTaskIsReachedAndCompleted() {
  ProcessInstanceEvent processInstance = client.newCreateInstanceCommand()
    .bpmnProcessId("example-process")
    .latestVersion()
    .send().join();

  assertThat(processInstance)
    .hasActiveElements(byName("Approve Request"));

  var userTaskRef = new AtomicReference<UserTask>();
  await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
    var userTasks = client.newUserTaskSearchRequest()
      .filter(filter -> filter
        .processInstanceKey(processInstance.getProcessInstanceKey())
        .state(UserTaskState.CREATED))
      .send().join().items();
    assertThat(userTasks).hasSize(1);
    userTaskRef.set(userTasks.get(0));
  });
  var userTask = userTaskRef.get();
  assertThat(userTask.getName()).isEqualTo("Approve Request");
  assertThat(userTask.getAssignee()).isEqualTo("demo");

  // Complete the selected task by its exact user-task key
  Map<String, Object> variables = new HashMap<>();
  variables.put("approved", true);
  client.newCompleteUserTaskCommand(userTask.getUserTaskKey())
    .variables(variables)
    .send().join();

  assertThat(processInstance)
    .hasCompletedElements("UserTask_Approve")
    .isCompleted()
    .hasVariable("approved", true);
}
```