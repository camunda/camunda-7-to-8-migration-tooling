# Job Execution in Test Cases

The CPT clock and job utilities in this pattern are available from Camunda 8.8.

## Job and clock API mappings

| Camunda 7 | CPT | Note |
|---|---|---|
| `execute(job())` for an asynchronous continuation | Remove the manual job step | Camunda 8 continues asynchronously. Use a waiting assertion for the next state. |
| `execute(job())` or `managementService.executeJob(id)` for a timer | `processTestContext.increaseTime(duration)` | Assert the timer catch event is active first, or the attached activity for a boundary timer. CPT does not expose a boundary timer as an active element. |
| `ClockUtil.setCurrentTime(date)` or `ClockUtil.reset()` | `processTestContext.setTime(instant)` | CPT resets the clock after each test. |
| Completing an external task with `complete(externalTask(), vars)` or `fetchAndLock(...)` followed by `complete(...)` | `processTestContext.completeJob(jobType, vars)` | Use the converted topic as the job type. Use `throwBpmnErrorFromJob` for `handleBpmnError`. |
| An expected exception from process start or task completion because a delegate failed | `assertThat(pi).hasActiveIncidents()` | A failing Camunda 8 worker creates an incident after its retries instead of throwing into the test. |

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

For an asynchronous continuation, Camunda 7 tests often call `execute(job())` to advance the process. CPT does not require manual execution for asynchronous continuations.

## Camunda 8

Camunda 8 handles timers and async jobs differently, but you also have control in test cases.

Deploy the converted model before starting an instance. See the [test deployment pattern](https://github.com/camunda/camunda-7-to-8-migration-tooling/blob/main/code-conversion/patterns/40-test-assertions/20-test-setup/20-deployment.md) for Camunda 8.8 and 8.9 setup.

You can [manipulate the clock](https://docs.camunda.io/docs/apis-tools/testing/utilities/#manipulate-the-clock) to trigger a BPMN timer event that would be due in the future.

```java
@Autowired
private CamundaProcessTestContext processTestContext;

@Test
void testTimerTriggered() {
  ProcessInstanceEvent instance = client.newCreateInstanceCommand()
    .bpmnProcessId("timer-process")
    .latestVersion()
    .send().join();

  assertThat(instance).hasActiveElements("TimerEvent");

  processTestContext.increaseTime(Duration.ofDays(2)); // for a 2 days timer

  assertThat(instance)
    .hasCompletedElements("TimerEvent")
    .isCompleted();
}
```

Replace `ClockUtil.setCurrentTime(instant)` with `processTestContext.setTime(instant)`. If the process must start at a specific instant, call `setTime` before creating it. If `setTime` is used to trigger a timer, first wait until the timer event is active, for example with `assertThat(instance).hasActiveElements("TimerEvent")`. CPT resets the clock after each test.

For an asynchronous continuation, omit `execute(job())` and assert the next process state with a waiting assertion:

```java
assertThat(instance).hasActiveElements("NextWaitState");
```

You might not want to execute any JobWorkers automatically, then you can disable those for your test case:

```java
@SpringBootTest(
	    properties = {
	    	      "camunda.client.worker.defaults.enabled=false" // disable all job workers
	    })
```

And execute jobs manually in your test, probably using the [complete job](https://docs.camunda.io/docs/apis-tools/testing/utilities/#complete-jobs) utility method to simulate the behavior of a job worker without invoking the actual worker. The command waits for the first job with the given job type and completes it. If no job exists, the command fails.


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

Alternatively you could also [mock workers](https://docs.camunda.io/docs/apis-tools/testing/utilities/#mock-job-workers) which allows you to specify the behavior of the worker for the test case at hand, for example to verify it is executed, to simulate specific result data, or to throw an exception.

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

Complete an external-task job with `processTestContext.completeJob(type, variables)`. Throw a BPMN error from that job with `processTestContext.throwBpmnErrorFromJob(type, errorCode, variables)`. Use the job type from the converted model.