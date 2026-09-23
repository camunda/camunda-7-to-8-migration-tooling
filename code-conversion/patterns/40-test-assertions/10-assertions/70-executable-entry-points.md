# Executable Entry-Point Coverage

A test that starts only the main process does not cover an executable subprocess that is normally
reached through a call activity. The call activity can supply variables that a direct start lacks.

## Camunda 7

A delegate can read a variable that may be absent: `execution.getVariable("x")` returns `null`.

## Camunda 8

`ActivatedJob.getVariable("x")` fails the job when `x` is absent, and the process gets an incident.
Bind the variable as `@Variable(name = "x", optional = true)` to keep the Camunda 7 behavior.

Start every executable process directly, with and without the inputs its workers may not receive:

```java
@Test
void shouldCompleteStandaloneProcessWithoutOptionalVariable() {
  ProcessInstanceEvent processInstance = client.newCreateInstanceCommand()
      .bpmnProcessId("sub-process")
      .latestVersion()
      .variables(Map.of()) // repeat with Map.of("x", 7)
      .send()
      .join();

  assertThat(processInstance)
      .isCompleted()
      .hasNoActiveIncidents()
      .hasVariable("y", "hello world");
}
```

If a process is not a valid standalone entry point, record why and which test covers it instead.
