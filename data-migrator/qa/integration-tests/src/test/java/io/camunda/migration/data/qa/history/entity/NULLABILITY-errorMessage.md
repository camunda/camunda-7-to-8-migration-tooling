# NullabilityContract — `IncidentEntity.errorMessage`

Related issue: [camunda-7-to-8-migration-tooling#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339)
Disabled test: `NullabilityContractTest.shouldNotProduceNullErrorMessageForIncident`

## What change triggered the investigation

C8 PR [#51301](https://github.com/camunda/camunda/pull/51301) added a
`requireNonNull` for `errorMessage` to the `IncidentEntity` compact
constructor:

```java
Objects.requireNonNull(errorMessage, "errorMessage");
```

The migrator passes Camunda 7's `HistoricIncident.getIncidentMessage()`
straight through into `IncidentDbModel.errorMessage`. Camunda 7 allows a
null message (the column `ACT_HI_INCIDENT.INCIDENT_MSG_` is nullable), so a
Camunda 7 incident whose underlying exception has `getMessage() == null`
will produce a row with `errorMessage IS NULL` in the C8 RDBMS — and that
should violate the C8 contract at search-API read time.

## How we tried to reproduce

The simplest deterministic way to produce a Camunda 7 historic incident
with `getIncidentMessage() == null` is to call

```java
runtimeService.createIncident("foo", executionId, null);
```

against a user-task instance. We deliberately did **not** use an
async-before failing-`JavaDelegate` scenario here: that would also leave
`flowNodeInstanceKey` null (see scenario #10), and the read-side
`requireNonNull(flowNodeInstanceKey)` would fire before the test reached
the `errorMessage` assertion — masking the masking we're trying to study.

The test starts `userTaskProcess.bpmn`, creates the null-message incident
on the user task's execution, runs the full migrator, reads via the search
API (`incidentReader.search(IncidentQuery.of(...))`), and asserts
`errorMessage` is non-null and non-empty.

## Why we think it's not reproducible

The test passes — the migrated row has a non-null `errorMessage`. Tracing
through the C8 read path:

1. The migrator writes `IncidentDbModel.errorMessage = null` (correct
   pass-through of Camunda 7's null).
2. The C8 `IncidentMapper` registers a `NullToEmptyStringTypeHandler` for
   the `ERROR_MESSAGE` column. MyBatis converts the DB `NULL` to the Java
   empty string `""` before constructing the entity.
3. The `IncidentEntity` compact constructor sees `errorMessage = ""` and
   `Objects.requireNonNull(errorMessage, "errorMessage")` passes — empty
   string is not null.

So the null write from the migrator never reaches the entity constructor as
a null; the type handler silently masks it. The contract is satisfied at
read time even though the migrator is technically producing a
contract-incompatible row at write time.

## Question for the C8 team

Same shape as the `decisionDefinitionName` question (see
[`NULLABILITY-decisionDefinitionName.md`](./NULLABILITY-decisionDefinitionName.md)):

Is the `NullToEmptyStringTypeHandler` masking of `ERROR_MESSAGE` part of
the supported contract for `IncidentEntity`? I.e. is it acceptable for the
*write side* to leave the field as SQL `NULL` as long as the read returns
`""`?

If yes, this is a no-op for us. If no (the field should be populated to a
real value at write time, perhaps the exception class name when
`getMessage()` is null), the fix is migrator-side: replace the null with a
sensible default such as `c7Incident.getIncidentType()` or
`"<no message>"`.

The two masking cases (`decisionDefinitionName` and `errorMessage`) could be
bundled into a single design conversation.
