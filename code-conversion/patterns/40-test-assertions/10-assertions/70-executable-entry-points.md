# Executable Entry-Point Coverage

Tests that start only the primary process can miss failures in an executable process definition
that is normally reached through a call activity. Cover each valid executable entry point directly.

## Camunda 7

List every executable process definition in the migrated deployment. Start each definition directly
when its model permits a standalone start. Keep a record for definitions that require a parent
process, message, or other trigger.

For every standalone definition, test the normal input set and inputs that the worker may not
receive. A call activity can provide variables that a direct start does not provide.

## Camunda 8

Camunda Process Test (CPT) starts a standalone process with the process ID. Use an empty variable
map to test omitted inputs:

```java
@Autowired
private CamundaClient client;

private ProcessInstanceEvent start(String processId, Map<String, Object> variables) {
  return client.newCreateInstanceCommand()
      .bpmnProcessId(processId)
      .latestVersion()
      .variables(variables)
      .send()
      .join();
}
```

The following test is a red regression scenario for a worker that reads an unavailable variable:

```java
@Test
void shouldExposeMissingVariableRegression() {
  ProcessInstanceEvent processInstance = start("sub-process", Map.of());

  assertThat(processInstance).hasActiveIncidents();
}
```

After the worker preserves the original optional-variable behavior, assert completion and output
variables instead:

```java
@Test
void shouldCompleteStandaloneProcessWithoutOptionalVariable() {
  ProcessInstanceEvent processInstance = start("sub-process", Map.of());

  assertThat(processInstance)
      .isCompleted()
      .hasNoActiveIncidents()
      .hasVariable("y", "hello world");
}

@Test
void shouldCompleteStandaloneProcessWithOptionalVariable() {
  ProcessInstanceEvent processInstance = start("sub-process", Map.of("x", 7));

  assertThat(processInstance)
      .isCompleted()
      .hasNoActiveIncidents()
      .hasVariable("y", "hello world");
}
```

The omitted-input scenario detects the failure hidden by the parent process. The present-input
scenario preserves the normal worker path. Do not retain the red regression expectation after the
worker fix.

Record the process ID, input set, failing element, job type, incident message, and retry state in
the migration validation report. Record the expected completion and output variables after the fix.

Exclude a definition only when it is not a valid standalone entry point. Record the process ID,
exclusion reason, required parent or trigger, and alternative coverage in the report. An exclusion
without a reason is incomplete validation.
