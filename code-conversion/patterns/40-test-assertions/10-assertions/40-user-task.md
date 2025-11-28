# User Task Assertions

## Camunda 7

You can assert that the process is waiting at a user task, and complete it using built-in helpers:

```java
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

  complete(task());

  assertThat(processInstance)
    .hasPassed("UserTask_Approve")
    .isEnded();
}
```


## Camunda 8

With [Camunda Process Test (CPT)](https://docs.camunda.io/docs/next/apis-tools/testing/getting-started/), you can use hasActiveElements() to assert the task is active. Furthermore, there are utility methods, for example to [complete jobs](https://docs.camunda.io/docs/next/apis-tools/testing/utilities/#complete-user-tasks).

Note that you typically address elements by ID and not by name, which we do for illustration purposes here:

```java
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

  // Retrieve and complete task using custom methods
  processTestContext.completeUserTask("Approve Request", variables);

  assertThat(processInstance)
    .hasCompletedElements("UserTask_Approve")
    .isCompleted();
}
```