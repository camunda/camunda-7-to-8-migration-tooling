# Idiomatic Job Worker Cleanup

OpenRewrite can create a preliminary Camunda 8 worker shape for supported patterns. Compare each
generated worker with its source before cleanup. AI cleanup removes generated names and redundant
code only after the comparison confirms the worker contract.

## Preserve the job type

Rename `*Migrated` and `executeJob*` methods to the job type or the original delegate intent.
Preserve an explicit `@JobWorker(type = "...")` value. If the job type came from the method name,
set it explicitly before renaming the method.

```java
// Before
@JobWorker(type = "sampleJavaDelegate")
public void executeJobMigrated(ActivatedJob job) {
  // ...
}

// After
@JobWorker(type = "sampleJavaDelegate")
public void sampleJavaDelegate(ActivatedJob job) {
  // ...
}
```

## Inject variables

Replace `job.getVariable(...)` and `job.getVariablesAsMap()` with typed `@Variable` parameters.
Use `@VariablesAsType` when several variables form one input object. Keep `ActivatedJob` when the
method uses job metadata or the job key (`job.getKey()`).

```java
// Before
public void sampleJavaDelegate(ActivatedJob job) {
  Object x = job.getVariable("x");
}

// After
public void sampleJavaDelegate(@Variable Object x) {
}
```

Mark an input optional only when the source worker accepts its absence:

```java
public void sampleJavaDelegate(@Variable(optional = true) String comment) {
}
```

For code that reads variables from `ActivatedJob`, use
`job.getVariablesAsMap().get("comment")` when a missing variable must remain `null`.
`job.getVariable("comment")` is strict and fails when the variable is unavailable.

Remove `throws Exception` when the cleaned method no longer throws a checked exception. Keep a
specific checked exception when the worker still requires it.

## Simplify outputs and defaults

Return `Map.of(...)` for a single-entry output map when its values are non-null and callers do not
mutate the map. Keep a mutable map when mutation or nullable values are required.

```java
// Before
Map<String, Object> resultMap = new HashMap<>();
resultMap.put("y", "hello world");
return resultMap;

// After
return Map.of("y", "hello world");
```

Remove `autoComplete = true` because `true` is the default. Keep the attribute when the project
documents the explicit setting as part of its configuration contract.

## Keep migration provenance

Preserve a short Javadoc that identifies the Camunda 7 source. Add one when the source origin is
known and the generated worker has no provenance note.

```java
/**
 * Migrated from the Camunda 7 SampleJavaDelegate.
 */
@JobWorker(type = "sampleJavaDelegate")
public Map<String, Object> sampleJavaDelegate(@Variable Object x) {
  return Map.of("y", "hello world");
}
```
