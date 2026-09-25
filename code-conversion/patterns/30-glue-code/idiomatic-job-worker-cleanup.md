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

Bind each required variable with a typed `@Variable(name = "...")` parameter. Use the exact source
variable name, even when it matches the Java parameter name. Do not rely on retained Java parameter
names. Use `@VariablesAsType` when several variables form one input object.
Keep `ActivatedJob` when the worker reads job metadata or its key.

```java
// Before
public void sampleJavaDelegate(ActivatedJob job) {
  Object x = job.getVariable("x");
}

// After
public void sampleJavaDelegate(@Variable(name = "x") Object x) {
}
```

Mark an injected input optional only when the source worker accepts its absence:

```java
public void sampleJavaDelegate(@Variable(name = "comment", optional = true) String comment) {
}
```

Keep a nullable source read as `job.getVariablesAsMap().get("comment")` when the source accepts an
absent variable. Fetch that variable with `fetchVariables` or set `fetchAllVariables = true`.
Do not replace the nullable read with strict `job.getVariable("comment")`.

Use the activated job to pass the complete process-variable map to a delegate:

```java
@JobWorker(type = "persist-project", fetchAllVariables = true)
public Map<String, Object> persistProject(ActivatedJob job) {
  return projectDelegate.persist(job.getVariablesAsMap());
}
```

Never use `@Variable` to request the complete process-variable map. Bind a single map-valued process
variable with its explicit variable name. An `ActivatedJob` parameter disables implicit variable
fetching, so set `fetchAllVariables = true` when the worker needs every process variable.

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
public Map<String, Object> sampleJavaDelegate(@Variable(name = "x") Object x) {
  return Map.of("y", "hello world");
}
```
