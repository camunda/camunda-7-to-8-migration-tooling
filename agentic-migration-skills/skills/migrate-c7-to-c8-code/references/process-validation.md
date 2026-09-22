# Process Validation

Every instruction in this reference is mandatory. "Never" means MUST NOT. A preference is marked
(SHOULD) and an option is marked (MAY).

Use this reference to create the executable-entry-point section in `MIGRATION_REPORT.md`. A primary
happy-path test does not cover a process definition that starts independently.

## Inventory executable definitions

Inspect every original and converted BPMN file. Record each process with
`isExecutable="true"` in this table:

| Process ID | Source file | Converted file | Standalone verdict | Scenario IDs |
|---|---|---|---|---|
| `<process-id>` | `<source-path>` | `<converted-path>` | `tested`, `excluded`, or `blocked` | `<scenario-id>` |

Start each executable definition independently when its model permits a direct start. Do not assume
that coverage through a call activity covers a direct start. A call activity can provide variables
that a direct start does not provide.

Use `excluded` only when the process is not a valid standalone entry point. Record the reason, the
required parent or trigger, and the alternative scenario that covers the process in the report.
Treat a missing exclusion reason as a validation failure.

Use `blocked` when a valid standalone entry point cannot run because a required prerequisite is
unavailable. Record the blocker, its owner, and the next action in a blocked scenario row. A blocked
verdict records incomplete coverage.

## Generate scenarios

Create at least these scenarios for every tested executable definition:

| Scenario | Input set | Required assertion |
|---|---|---|
| Normal entry | Inputs used by the existing happy path | Preserve the existing branch and output assertions |
| Omitted input | Omit every input that a worker reads without a model-level default | Complete without an incident when the Camunda 7 source allowed the input to be absent |
| Boundary input | Empty, minimum, maximum, or alternate branch values | Cover each relevant boundary branch |

Keep the normal branch scenarios. Add the direct-start scenarios beside them. Do not replace
primary-process tests with direct-start tests.

For each failing scenario, record the process ID, scenario ID, input set, failing BPMN element,
job type, incident message, and retry state. Record the expected result after the migration fix in
the same row.

## Camunda Process Test shape

Use a direct create-instance command for each standalone process:

```java
private ProcessInstanceEvent start(String processId, Map<String, Object> variables) {
  return client.newCreateInstanceCommand()
      .bpmnProcessId(processId)
      .latestVersion()
      .variables(variables)
      .send()
      .join();
}
```

Run the omitted-input scenario with an empty map. The regression test exposes the old failure:

```java
@Test
void shouldExposeMissingVariableRegression() {
  ProcessInstanceEvent processInstance = start("sub-process", Map.of());

  CamundaAssert.assertThat(processInstance).hasActiveIncidents();
}
```

After the worker preserves the Camunda 7 optional-variable behavior, replace the regression
expectation with the migrated expectation:

```java
@Test
void shouldCompleteStandaloneProcessWithoutOptionalVariable() {
  ProcessInstanceEvent processInstance = start("sub-process", Map.of());

  CamundaAssert.assertThat(processInstance)
      .isCompleted()
      .hasNoActiveIncidents()
      .hasVariable("y", "hello world");
}
```

Run the present-input scenario as well:

```java
@Test
void shouldCompleteStandaloneProcessWithOptionalVariable() {
  ProcessInstanceEvent processInstance = start("sub-process", Map.of("x", 7));

  CamundaAssert.assertThat(processInstance)
      .isCompleted()
      .hasNoActiveIncidents()
      .hasVariable("y", "hello world");
}
```

The regression expectation is evidence of the defect. The migrated expectation is the acceptance
assertion. Do not keep both expectations in the same passing test suite.

## Record the test plan

Add this section to `MIGRATION_REPORT.md`:

```markdown
## Executable entry-point validation

| Process ID | Scenario ID | Input set | Verdict | Failing element | Job type | Incident or blocker | Retry state | Expected after fix |
|---|---|---|---|---|---|---|---|---|
| `<process-id>` | `<scenario-id>` | `<inputs or omitted>` | `tested`, `excluded`, or `blocked` | `<element or n/a>` | `<type or n/a>` | `<message, or blocker with owner and next action>` | `<retries or n/a>` | `<completion and variables>` |

### Exclusions

| Process ID | Reason | Required parent or trigger | Alternative coverage |
|---|---|---|---|
| `<process-id>` | `<why direct start is invalid>` | `<parent or trigger>` | `<scenario-id>` |
```

For a blocked scenario, use `n/a` for unavailable failure fields. Record the blocker, its owner, and
the next action in the `Incident or blocker` field.

Do not mark an excluded process as tested. Do not close the validation section while any executable
definition lacks a scenario or a justified exclusion. A blocked verdict records an open prerequisite
and does not close the validation requirement. Resolve the blocker before closing the section or
record a justified exclusion.
