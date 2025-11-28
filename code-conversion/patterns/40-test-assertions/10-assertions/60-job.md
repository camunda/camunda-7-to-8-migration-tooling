# Job Execution in Test Cases

## Camunda 7

Camunda 7 provides control over job execution through the `managementService`, which is useful for timers, asynchronous continuations, or retries.


```java
@Test
void testTimerFires() {
  ProcessInstance instance = runtimeService()
    .startProcessInstanceByKey("timer-process");

  // Execute the pending job (e.g. a timer or async)
  Job timerJob = managementService.createJobQuery()
    .processInstanceId(instance.getId())
    .singleResult();
  managementService.executeJob(timerJob.getId());

  assertThat(instance)
    .hasPassed("TimerEvent")
    .isEnded();
}

```

## Camunda 8

Camunda 8 handles timers and async jobs differently, but you also have control in test cases.

You can [manipulate the clock](https://docs.camunda.io/docs/next/apis-tools/testing/utilities/#manipulate-the-clock) to trigger a BPMN timer event that would be due in the future.

```java
@Autowired
private CamundaProcessTestContext processTestContext;

@Test
void testTimerTriggered() {
  ProcessInstanceEvent instance = client.newCreateInstanceCommand()
    .bpmnProcessId("timer-process")
    .latestVersion()
    .send().join();

  processTestContext.increaseTime(Duration.ofDays(2)); // for a 2 days timer

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

And execute jobs manually in your test, probably using the [complete job](https://docs.camunda.io/docs/next/apis-tools/testing/utilities/#complete-jobs) utility method to simulate the behavior of a job worker without invoking the actual worker. The command waits for the first job with the given job type and completes it. If no job exists, the command fails.


```java
@Autowired
private CamundaProcessTestContext processTestContext;

@Test
void testTimerTriggered() {
  // ...
  processTestContext.completeJob("the-job-to-complete");
  //...
}
```

Alternatively you could also [mock workers](https://docs.camunda.io/docs/next/apis-tools/testing/utilities/#mock-job-workers) which allows you to specify the behavior of the worker for the test case at hand, for example to verify it is executed, to simulate specific result data, or to throw an exception.

```java
processTestContext.mockJobWorker("serviceTask1").thenComplete(variables);
processTestContext.mockJobWorker("serviceTask2").thenThrowBpmnError("SOME_ERROR");
processTestContext.mockJobWorker("serviceTask3")
        .withHandler(
            (jobClient, job) -> {
                final Map<String, Object> variables = job.getVariablesAsMap();
                final double orderAmount = (double) variables.get("orderAmount");
                final double discount = orderAmount > 100 ? 0.1 : 0.0;

                jobClient.newCompleteCommand(job).variable("discount", discount).send().join();
            });
```