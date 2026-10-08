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
| `task()` | No direct counterpart | Search user tasks by `processInstanceKey` and `UserTaskState.CREATED`, then assert `.hasSize(1)` before inspecting the result. Use its exact `userTaskKey` when completing it. |
| `task("A")` | `UserTaskSelectors.byElementId("A", processInstanceKey)` | `A` is the BPMN user-task element ID. Get `processInstanceKey` from the started `ProcessInstanceEvent`. If the element can repeat, search created tasks and assert `.hasSize(1)`. |
| `findId("Task name")` | `UserTaskSelectors.byTaskName("Task name", processInstanceKey)` | Use the task name selected by the source test. Get `processInstanceKey` from the started `ProcessInstanceEvent`. If the name can match multiple created tasks, search and assert `.hasSize(1)`. |
| `complete(task(), withVariables(vars))` | No direct counterpart | Search user tasks by `processInstanceKey` and `UserTaskState.CREATED`. Assert `.hasSize(1)`, then complete the selected task by its exact `userTaskKey`. |
| `complete(task("A"), withVariables(vars))` | `processTestContext.completeUserTask(UserTaskSelectors.byElementId("A", processInstanceKey), vars)` | Include the process-instance key in the selector. If the element can repeat, search created tasks and assert `.hasSize(1)`. |
| `taskService.complete(id, vars)` | `client.newCompleteUserTaskCommand(userTaskKey).variables(vars).send().join()` | Find the corresponding C8 task with equivalent process-instance and task-identity filters, then complete its exact `userTaskKey`. |
| `claim(task(), "user")` | `client.newAssignUserTaskCommand(userTaskKey).assignee("user").allowOverride(false).send().join()` | Set `allowOverride(false)` so an already-assigned task still fails as it does in Camunda 7. Get the task key with a user-task search. Record a reason before dropping the assignment step. |

The CPT selector-based user-task assertions and completion APIs shown here are available from Camunda 8.8. With [Camunda Process Test (CPT)](https://docs.camunda.io/docs/apis-tools/testing/getting-started/), you can use hasActiveElements() to assert the task is active. Furthermore, there are utility methods, for example to [complete user tasks](https://docs.camunda.io/docs/apis-tools/testing/utilities/#complete-user-tasks).

Note that you typically address elements by ID and not by name, which we do for illustration purposes here:

```java
import java.util.HashMap;
import java.util.Map;

import io.camunda.client.api.search.enums.UserTaskState;

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

  var userTasks = client.newUserTaskSearchRequest()
    .filter(filter -> filter
      .processInstanceKey(processInstance.getProcessInstanceKey())
      .state(UserTaskState.CREATED))
    .send().join().items();
  assertThat(userTasks).hasSize(1);
  var userTask = userTasks.get(0);
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