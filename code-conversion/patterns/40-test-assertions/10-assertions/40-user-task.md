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

The CPT selector-based user-task assertions and completion APIs shown here are available from Camunda 8.8. With [Camunda Process Test (CPT)](https://docs.camunda.io/docs/next/apis-tools/testing/getting-started/), you can use hasActiveElements() to assert the task is active. Furthermore, there are utility methods, for example to [complete jobs](https://docs.camunda.io/docs/next/apis-tools/testing/utilities/#complete-user-tasks).

Note that you typically address elements by ID and not by name, which we do for illustration purposes here:

```java
import java.util.HashMap;
import java.util.Map;

@Autowired
private CamundaClient client;
@Autowired
private CamundaProcessTestContext processTestContext;

@Test
void testUserTaskIsReachedAndCompleted() {
  ProcessInstanceEvent processInstance = client.newCreateInstanceCommand()
    .bpmnProcessId("example-process")
    .latestVersion()
    .send().join();

  assertThat(processInstance)
    .hasActiveElements(byName("Approve Request"));
      
  assertThat(UserTaskSelectors.byTaskName("Approve Request"))
    .isCreated()
    .hasName("Approve Request")
    .hasAssignee("demo");

  // Complete the task by its name selector
  Map<String, Object> variables = new HashMap<>();
  variables.put("approved", true);
  processTestContext.completeUserTask(UserTaskSelectors.byTaskName("Approve Request"), variables);

  assertThat(processInstance)
    .hasCompletedElements("UserTask_Approve")
    .isCompleted()
    .hasVariable("approved", true);
}
```