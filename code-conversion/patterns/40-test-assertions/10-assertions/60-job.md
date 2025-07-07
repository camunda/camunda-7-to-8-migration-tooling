# Job Execution in Test Cases

## Camunda 7

Camunda 7 provides control over job execution through the `managementService`, which is useful for timers, asynchronous continuations, or retries.


```java
@Test
void testTimerFires() {
  ProcessInstance instance = runtimeService()
    .startProcessInstanceByKey("timer-process");

  // Execute the pending job (e.g. a timer)
  Job timerJob = managementService.createJobQuery()
    .processInstanceId(instance.getId())
    .singleResult();

  managementService.executeJob(timerJob.getId());

  assertThat(instance)
    .hasPassed("TimerEvent")
    .isEnded();
}

```

You can also assert job retries and incidents:

```java
Job job = managementService.createJobQuery()
  .processInstanceId(instance.getId())
  .singleResult();

assertEquals(2, job.getRetries());
```

## Camunda 8

Camunda 8 handles timers and async jobs in the job worker model, and does not expose them via the Client API as directly.

To test timer events or ensure the process continues, you generally wait for the engine to progress:

```java
@Test
void testTimerTriggered() {
  ProcessInstanceEvent instance = client.newCreateInstanceCommand()
    .bpmnProcessId("timer-process")
    .latestVersion()
    .send().join();

  assertThat(instance)
    .hasCompletedElements("TimerEvent")
    .isCompleted();
}
```

You might not want to execute any JobWorkers automatically, then you can disable those for your test case:

```java
@SpringBootTest(
	    properties = {
	    	      "camunda.client.worker.defaults.enabled=false" // disable all job workers
	    })
```

And execute jobs manually in your test, probably using this utility method:

```java
  private void completeJobs(final String jobType, final Map<String, Object> variables) {
    client
        .newWorker()
        .jobType(jobType)
        .handler((jobClient, job) -> jobClient.newCompleteCommand(job).variables(variables).send())
        .open();
  }
```

This way you can leave out job workers from test execution. 