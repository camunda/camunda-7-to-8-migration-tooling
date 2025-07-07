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

With [Camunda Process Test (CPT)](https://docs.camunda.io/docs/next/apis-tools/testing/getting-started/), you can use hasActiveElements() to assert the task is active. 
At the moment you need to use the normal client API to retrieve and complete tasks. Of course, you can easily built own utility methods for this.
Such utility methods might be added to CPT over time.

Note that you typically address elements by ID and not by name, which we do for illustration purposes here:

```java
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
  UserTask task = task(processInstance);
  complete(task);

  assertThat(processInstance)
    .hasCompletedElements("UserTask_Approve")
    .isCompleted();
}
```

The following utility methods are used in this test case:

```java
  private void complete(UserTask task) {
        client.newUserTaskCompleteCommand(task.getUserTaskKey()).send().join();
	}

	private UserTask task(ProcessInstanceEvent processInstance) {
		SearchResponse<UserTask> tasks = client.newUserTaskSearchRequest()
		  	.filter((f) -> f.bpmnProcessId(processInstance.getBpmnProcessId()))
		  	.send().join();
		  assertEquals(1, tasks.items().size());
		  return tasks.items().get(0);
	}
```