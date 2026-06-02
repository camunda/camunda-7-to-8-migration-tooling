# NullabilityContract — `AuditLogEntity.entityKey` (general + JOB / EXTERNAL_TASK)

Related issue: [camunda-7-to-8-migration-tooling#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339)

Related rows in [NULLABILITY-status.md](./NULLABILITY-status.md):
- **#12** — general case across all C7 entity types reaching the migrator.
- **#4** — focused JOB instance (failing test: `NullabilityContractTest.shouldNotProduceNullEntityKeyForJobAuditLog`).
- **#5** — focused EXTERNAL_TASK instance (failing test: `NullabilityContractTest.shouldNotProduceNullEntityKeyForExternalTaskAuditLog`).

## Output rules — non-negotiable

1. **Read inventory.** Every claim cites a file:LINE entry from the inventory below.
2. **Verbatim quotes.** Every behavioural claim is backed by a fenced code block whose first line is `// path/to/file.java:LINE`.
3. **Assumption / Finding tags.** `[FINDING]` = directly read; `[ASSUMPTION]` = inferred, with what would need to be read to upgrade. No `[ASSUMPTION]` tags on load-bearing claims.
4. **Falsifier per claim.** After each `[FINDING]`: one sentence stating the concrete code-level condition that would make the finding wrong.
5. **Stop-and-flag.** Facts that have not been directly read are not papered over.

## Read inventory

- `data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/AuditLogMigrator.java:1-263` — migrator write path; the `resolve*Key` branches.
- `data-migrator/core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/AuditLogTransformer.java:1-343` — interceptor; entity-type allowlist + entity-type rewrite.
- `../camunda/search/search-domain/src/main/java/io/camunda/search/entities/AuditLogEntity.java:1-367` — C8 record contract (`requireNonNull(entityKey, ...)` at :55).
- `../camunda/db/rdbms/src/main/resources/mapper/AuditLogMapper.xml:1-609` — SQL mapper; no read-side coercion for ENTITY_KEY.
- `../camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/read/mapper/AuditLogEntityMapper.java:1-52` — DbModel → Entity passthrough.
- `../camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/read/service/AuditLogDbReader.java:1-83` — search() terminal `.toList()` that triggers the NPE.
- `data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NullabilityContractTest.java:170-244` — failing JOB and EXTERNAL_TASK tests.
- `data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/HistoryMigrationAbstractTest.java:213-220` — `searchAuditLogs` helper.
- `data-migrator/qa/integration-tests/src/test/java-c8-current/io/camunda/migration/data/qa/c8compat/C8QueryCompat.java:88-102` — confirms test helper bypasses the entity-side constructor.
- `../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/persistence/entity/UserOperationLogManager.java:131-770` — every `log*Operation` site that produces a row.
- `../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/history/producer/DefaultHistoryEventProducer.java:823-840` — confirms one history event per PropertyChange.
- `../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/ExecuteJobsCmd.java:80-94` — JOB-A (jobId set).
- `../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/SetJobPriorityCmd.java:62-74` — JOB-A.
- `../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/SetJobRetriesCmd.java:95-146` — JOB-A (single) and JOB-B (by jobDefinitionId).
- `../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/AbstractSetJobStateCmd.java:122-154` — JOB-A/B (suspend/activate; jobId may be null).
- `../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/AbstractSetJobsRetriesBatchCmd.java:64-81` — JOB-C (async batch).
- `../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/HistoryCleanupCmd.java:209-219` — JOB-C (engine history-cleanup job).
- `../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/ExternalTaskCmd.java:65-78` — EXT-A.
- `../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/AbstractSetExternalTaskRetriesCmd.java:93-103` — EXT-B (async batch).
- `../camunda-bpm-platform-maintenance/engine/src/main/resources/org/camunda/bpm/engine/db/create/activiti.postgres.create.history.sql:211-240` — `ACT_HI_OP_LOG` schema.

## What change triggered the investigation

C8 PR [#51301](https://github.com/camunda/camunda/pull/51301) added `Objects.requireNonNull(entityKey, "entityKey")` to the `AuditLogEntity` compact constructor:

```java
// ../camunda/search/search-domain/src/main/java/io/camunda/search/entities/AuditLogEntity.java:53
  public AuditLogEntity {
    Objects.requireNonNull(auditLogKey, "auditLogKey");
    Objects.requireNonNull(entityKey, "entityKey");
```

The migrator writes `NULL` into the `entity_key` column on every code path *except* PROCESS_INSTANCE, PROCESS_DEFINITION, and TASK, so reading any other audit-log row through the C8 search API throws NPE.

## Layer 1 — which C7 entity types reach `AuditLogMigrator`

**[FINDING]** `AuditLogTransformer.convertEntityType` is an allowlist switch; anything outside it throws `EntityInterceptorException` and the row is dropped before insert.

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/AuditLogTransformer.java:166
  protected AuditLogEntity.AuditLogEntityType convertEntityType(UserOperationLogEntry userOperationLog) {
    return  switch (userOperationLog.getEntityType()) {
      case PROCESS_INSTANCE -> AuditLogEntity.AuditLogEntityType.PROCESS_INSTANCE;
      case VARIABLE -> AuditLogEntity.AuditLogEntityType.VARIABLE;
      case TASK -> AuditLogEntity.AuditLogEntityType.USER_TASK;
      case USER -> AuditLogEntity.AuditLogEntityType.USER;
      case GROUP, GROUP_MEMBERSHIP -> AuditLogEntity.AuditLogEntityType.GROUP;
      case TENANT, TENANT_MEMBERSHIP -> AuditLogEntity.AuditLogEntityType.TENANT;
      case AUTHORIZATION -> AuditLogEntity.AuditLogEntityType.AUTHORIZATION;
      case INCIDENT -> AuditLogEntity.AuditLogEntityType.INCIDENT;
      case PROCESS_DEFINITION, DEPLOYMENT -> AuditLogEntity.AuditLogEntityType.RESOURCE;
      case JOB, EXTERNAL_TASK -> AuditLogEntity.AuditLogEntityType.JOB;
      ...
      default -> throw new EntityInterceptorException(UNSUPPORTED_AUDIT_LOG_ENTITY_TYPE + userOperationLog.getEntityType());
    };
  }
```

This is wrong if: a default branch is added that returns a non-null `AuditLogEntityType` instead of throwing — then the silently-dropped C7 entity types below would also reach the migrator.

| C7 `entityType`                              | C8 `AuditLogEntityType` |
|----------------------------------------------|--------------------------|
| `PROCESS_INSTANCE`                           | `PROCESS_INSTANCE`       |
| `VARIABLE`                                   | `VARIABLE`               |
| `TASK`                                       | `USER_TASK`              |
| `USER`                                       | `USER`                   |
| `GROUP`, `GROUP_MEMBERSHIP`                  | `GROUP`                  |
| `TENANT`, `TENANT_MEMBERSHIP`                | `TENANT`                 |
| `AUTHORIZATION`                              | `AUTHORIZATION`          |
| `INCIDENT`                                   | `INCIDENT`               |
| `PROCESS_DEFINITION`, `DEPLOYMENT`           | `RESOURCE`               |
| `JOB`, `EXTERNAL_TASK`                       | `JOB`                    |

Silently dropped (no audit-log row, no nullability problem): `BATCH`, `IDENTITY_LINK`, `ATTACHMENT`, `JOB_DEFINITION`, `DECISION_DEFINITION`, `DECISION_INSTANCE`, `DECISION_REQUIREMENTS_DEFINITION`, `CASE_DEFINITION`, `CASE_INSTANCE`, `METRICS`, `TASK_METRICS`, `OPERATION_LOG`, `FILTER`, `COMMENT`, `PROPERTY`.

So the scope of #12 is the **8 allowlisted C7 entity types that the migrator currently doesn't populate `entityKey` for**: `VARIABLE`, `USER`, `GROUP`/`GROUP_MEMBERSHIP`, `TENANT`/`TENANT_MEMBERSHIP`, `AUTHORIZATION`, `INCIDENT`, `DEPLOYMENT`, `JOB`/`EXTERNAL_TASK`.

## Layer 2 — which branches in `AuditLogMigrator` set `entityKey`

**[FINDING]** Three of the four `resolve*` methods set `entityKey` only when their entityType matches; `resolveJobKey` sets `jobKey` but never sets `entityKey`.

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/AuditLogMigrator.java:147
  protected void resolveProcessInstanceKeys(Builder builder, UserOperationLogEntry c7AuditLog) {
    String c7ProcessInstanceId = c7AuditLog.getProcessInstanceId();
    String c7RootProcessInstanceId = c7AuditLog.getRootProcessInstanceId();
    if (c7ProcessInstanceId != null && isMigrated(c7ProcessInstanceId, HISTORY_PROCESS_INSTANCE)) {
      var processInstanceId = findProcessInstanceByC7Id(c7ProcessInstanceId).processInstanceKey();
      builder.processInstanceKey(processInstanceId);
      if (EntityTypes.PROCESS_INSTANCE.equals(c7AuditLog.getEntityType())) {
        builder.entityKey(String.valueOf(processInstanceId));
      }
```

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/AuditLogMigrator.java:176
  protected void resolveProcessDefinitionKey(Builder builder, UserOperationLogEntry c7AuditLog) {
    String c7ProcessDefinitionId = c7AuditLog.getProcessDefinitionId();
    if (c7ProcessDefinitionId != null && isMigrated(c7ProcessDefinitionId, HISTORY_PROCESS_DEFINITION)) {
      Long processDefinitionKey = findProcessDefinitionKey(c7ProcessDefinitionId);
      builder.processDefinitionKey(processDefinitionKey);
      if (EntityTypes.PROCESS_DEFINITION.equals(c7AuditLog.getEntityType())){
        builder.entityKey(String.valueOf(processDefinitionKey));
      }
    }
  }
```

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/AuditLogMigrator.java:200
  protected void resolveUserTaskKey(Builder builder, UserOperationLogEntry c7AuditLog) {
    String c7TaskId = c7AuditLog.getTaskId();
    if (c7TaskId != null && isMigrated(c7TaskId, HISTORY_USER_TASK)) {
      Long taskKey = dbClient.findC8KeyByC7IdAndType(c7TaskId, HISTORY_USER_TASK);
      if (EntityTypes.TASK.equals(c7AuditLog.getEntityType())){
        builder.entityKey(String.valueOf(taskKey));
      }
```

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/AuditLogMigrator.java:224
  protected void resolveJobKey(Builder builder, UserOperationLogEntry c7AuditLog) {
    String c7JobId = c7AuditLog.getJobId();
    if (c7JobId != null && EntityTypes.JOB.equals(c7AuditLog.getEntityType())) {
      if (isMigrated(c7JobId, HISTORY_JOB)) {
        Long jobKey = dbClient.findC8KeyByC7IdAndType(c7JobId, HISTORY_JOB);
        builder.jobKey(jobKey);
      } else if (isMigrated(c7JobId, HISTORY_EXTERNAL_TASK)) {
        Long jobKey = dbClient.findC8KeyByC7IdAndType(c7JobId, HISTORY_EXTERNAL_TASK);
        builder.jobKey(jobKey);
      }
    }
  }
```

This is wrong if: an interceptor running after `AuditLogTransformer` writes `entityKey` for any of the missing entity types. `AuditLogTransformer.execute` explicitly delegates the field:

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/AuditLogTransformer.java:85
    // Note: partitionId is set externally by AuditLogMigrator to match the parent process instance
    // Note: auditLogKey, processInstanceKey, rootProcessInstanceKey, processDefinitionKey, userTaskKey, entityKey, timestamp, historyCleanupDate
    // are set externally in AuditLogMigrator
```

Two distinct bugs in `resolveJobKey`:
1. `entityKey` is silently omitted for the JOB case (row #4).
2. `EXTERNAL_TASK` never enters this branch because the entityType check is `EntityTypes.JOB.equals(getEntityType())` — the `isMigrated(c7JobId, HISTORY_EXTERNAL_TASK)` fallback was meant to cover cases where the migrator stores the row under `HISTORY_EXTERNAL_TASK` even though `entityType==JOB`, not the `entityType==EXTERNAL_TASK` rows themselves (row #5).

## How the null propagates to the C8 read path

**[FINDING]** The row is inserted with whatever the builder holds, then read straight back through the entity constructor.

```xml
<!-- ../camunda/db/rdbms/src/main/resources/mapper/AuditLogMapper.xml:351 -->
    VALUES
    <foreach collection="dbModels" item="auditLog" separator=",">
      (
        #{auditLog.auditLogKey},
        #{auditLog.entityKey},
```

```java
// ../camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/read/service/AuditLogDbReader.java:73
    return executePagedQuery(
        () -> auditLogMapper.count(dbQuery),
        () -> auditLogMapper.search(dbQuery).stream().map(AuditLogEntityMapper::toEntity).toList(),
```

```java
// ../camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/read/mapper/AuditLogEntityMapper.java:15
  public static AuditLogEntity toEntity(final AuditLogDbModel auditLogDbModel) {
    return new AuditLogEntity.Builder()
        .auditLogKey(auditLogDbModel.auditLogKey())
        .entityKey(auditLogDbModel.entityKey())
```

`Stream.toList()` is terminal, so a single null-entityKey row in a page poisons the whole materialisation.

This is wrong if: a typeHandler, resultMap, or interceptor between `auditLogMapper.search` and `AuditLogEntityMapper.toEntity` substitutes a non-null default for ENTITY_KEY. No such handler is registered for the column — the mapper uses `resultType="...AuditLogDbModel"` with no resultMap, no per-column typeHandler:

```xml
<!-- ../camunda/db/rdbms/src/main/resources/mapper/AuditLogMapper.xml:22 -->
  <select id="search"
    parameterType="io.camunda.db.rdbms.read.domain.AuditLogDbQuery"
    resultType="io.camunda.db.rdbms.write.domain.AuditLogDbModel"
    statementType="PREPARED">
```

**[FINDING]** The failing tests assert on the *write-side* DbModel, not on the entity that would NPE, because the test helper bypasses the reader:

```java
// data-migrator/qa/integration-tests/src/test/java-c8-current/io/camunda/migration/data/qa/c8compat/C8QueryCompat.java:88
  public static List<AuditLogDbModel> searchAuditLogs(
      AuditLogMapper auditLogMapper, @SuppressWarnings("unused") AuditLogDbReader auditLogReader,
      String prefixedProcessDefinitionId) {
    return auditLogMapper.search(AuditLogDbQuery.of(b -> b
        .authorizationFilter(AuditLogAuthorizationFilter.allowAll())
        .filter(f -> f.processDefinitionIds(prefixedProcessDefinitionId))));
  }
```

So the integration tests prove "the DB column is null" (the migrator's defect), not "the search API NPEs at runtime" (the contract consequence). The latter follows mechanically from the `requireNonNull` + `.toList()` combination above.

This is wrong if: an upstream serialization layer (REST controller, gateway adapter) catches `NullPointerException` from the entity constructor and filters bad rows before they reach the client. No such layer was inspected in this run; flagged for verification.

## Data in scope per C7 entity type

### `ACT_HI_OP_LOG` schema and per-PropertyChange row shape

**[FINDING]** `ACT_HI_OP_LOG` has no generic `ENTITY_ID_` column — only typed FK columns:

```sql
-- ../camunda-bpm-platform-maintenance/engine/src/main/resources/org/camunda/bpm/engine/db/create/activiti.postgres.create.history.sql:211
create table ACT_HI_OP_LOG (
    ID_ varchar(64) not null,
    DEPLOYMENT_ID_ varchar(64),
    PROC_DEF_ID_ varchar(64),
    PROC_DEF_KEY_ varchar(255),
    ROOT_PROC_INST_ID_ varchar(64),
    PROC_INST_ID_ varchar(64),
    EXECUTION_ID_ varchar(64),
    CASE_DEF_ID_ varchar(64),
    CASE_INST_ID_ varchar(64),
    CASE_EXECUTION_ID_ varchar(64),
    TASK_ID_ varchar(64),
    JOB_ID_ varchar(64),
    JOB_DEF_ID_ varchar(64),
    BATCH_ID_ varchar(64),
    USER_ID_ varchar(255),
    TIMESTAMP_ timestamp not null,
    OPERATION_TYPE_ varchar(64),
    OPERATION_ID_ varchar(64),
    ENTITY_TYPE_ varchar(30),
    PROPERTY_ varchar(64),
    ORG_VALUE_ varchar(4000),
    NEW_VALUE_ varchar(4000),
    TENANT_ID_ varchar(64),
    REMOVAL_TIME_ timestamp,
	CATEGORY_ varchar(64),
	EXTERNAL_TASK_ID_ varchar(64),
	ANNOTATION_ varchar(4000),
    primary key (ID_)
);
```

This is wrong if: another DDL file (a different RDBMS, a Camunda EE-only migration) adds an `ENTITY_ID_` column. Not inspected.

**[FINDING]** Each call to a `log*Operation` produces one row per PropertyChange. The C7 event producer iterates the property changes within a context entry:

```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/history/producer/DefaultHistoryEventProducer.java:823
  public List<HistoryEvent> createUserOperationLogEvents(UserOperationLogContext context) {
    List<HistoryEvent> historyEvents = new ArrayList<HistoryEvent>();

    String operationId = Context.getCommandContext().getOperationId();
    context.setOperationId(operationId);

    for (UserOperationLogContextEntry entry : context.getEntries()) {
      for (PropertyChange propertyChange : entry.getPropertyChanges()) {
        UserOperationLogEntryEventEntity evt = new UserOperationLogEntryEventEntity();

        initUserOperationLogEvent(evt, context, entry, propertyChange);

        historyEvents.add(evt);
      }
    }
```

Consequence: a single C7 operation that mutates 3 properties produces 3 `UserOperationLogEntry` rows sharing `operationId` but each carrying a different `getProperty()`/`getNewValue()`/`getOrgValue()`. The migrator processes one row at a time and cannot see sibling rows without an extra C7 query.

This is wrong if: a non-default `HistoryEventProducer` is registered. Not enumerated.

### JOB — three subscenarios (rows #4 and beyond)

`logJobOperation` accepts a nullable `jobId`:

```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/persistence/entity/UserOperationLogManager.java:386
  public void logJobOperation(String operation, String jobId, String jobDefinitionId, String processInstanceId,
      String processDefinitionId, String processDefinitionKey, List<PropertyChange> propertyChanges) {
    if (isUserOperationLogEnabled()) {

      UserOperationLogContext context = new UserOperationLogContext();
      UserOperationLogContextEntryBuilder entryBuilder =
          UserOperationLogContextEntryBuilder.entry(operation, EntityTypes.JOB)
            .jobId(jobId)
            .jobDefinitionId(jobDefinitionId)
```

Callers split into three groups. The grouping matters for `entityKey` because the available identifier — and whether a C8 `jobKey` can be looked up at all — differs.

#### JOB-A — `jobId` non-null (exercised by the failing test)

`OPERATION_TYPE_EXECUTE`:
```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/ExecuteJobsCmd.java:89
      commandContext.getOperationLogManager().logJobOperation(UserOperationLogEntry.OPERATION_TYPE_EXECUTE,
          jobId, job.getJobDefinitionId(), job.getProcessInstanceId(), job.getProcessDefinitionId(),
          job.getProcessDefinitionKey(), PropertyChange.EMPTY_CHANGE);
```

`OPERATION_TYPE_SET_PRIORITY`:
```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/SetJobPriorityCmd.java:64
    commandContext
      .getOperationLogManager()
      .logJobOperation(
          UserOperationLogEntry.OPERATION_TYPE_SET_PRIORITY,
          job.getId(),
          job.getJobDefinitionId(),
          job.getProcessInstanceId(),
          job.getProcessDefinitionId(),
          job.getProcessDefinitionKey(),
          propertyChange);
```

`OPERATION_TYPE_SET_JOB_RETRIES` single-job branch:
```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/SetJobRetriesCmd.java:114
      commandContext.getOperationLogManager().logJobOperation(getLogEntryOperation(), job.getId(),
          job.getJobDefinitionId(), job.getProcessInstanceId(), job.getProcessDefinitionId(),
          job.getProcessDefinitionKey(), propertyChanges);
```

Plus `SetJobDuedateCmd:63`, `RecalculateJobDuedateCmd:101`, `DeleteJobCmd:62`, and `AbstractSetJobStateCmd:152` *when* called with a non-null `jobId` field.

For these rows, `c7AuditLog.getJobId() != null` and `c7AuditLog.getEntityType() == JOB` — `resolveJobKey` enters the block but never calls `builder.entityKey(...)`.

This is wrong if: one of the listed call sites starts passing a stale or already-completed `jobId` that no longer exists in `ACT_RU_JOB` / `ACT_HI_JOB`. The migrator then falls through to the skip-with-reason at `AuditLogMigrator.java:119-121` and the row is dropped, not written with null.

#### JOB-B — `jobId` null, `jobDefinitionId` set

Sync `setJobRetries(jobDefinitionId, retries)`:
```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/SetJobRetriesCmd.java:144
    commandContext.getOperationLogManager().logJobOperation(getLogEntryOperation(), null, jobDefinitionId, null,
        null, null, propertyChanges);
```

Suspend / activate jobs by non-job key:
```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/AbstractSetJobStateCmd.java:150
  protected void logUserOperation(CommandContext commandContext) {
    PropertyChange propertyChange = new PropertyChange(SUSPENSION_STATE_PROPERTY, null, getNewSuspensionState().getName());
    commandContext.getOperationLogManager().logJobOperation(getLogEntryOperation(), jobId, jobDefinitionId,
      processInstanceId, processDefinitionId, processDefinitionKey, propertyChange);
  }
```

(`AbstractSetJobStateCmd` passes its `jobId` field unchanged; it is null when the caller targeted by jobDefinitionId / processInstanceId / processDefinitionId / processDefinitionKey instead of by jobId.)

For these rows, `getJobId() == null` — `resolveJobKey`'s `if (c7JobId != null && ...)` short-circuits and **no C8 `jobKey` is looked up at all**. The mechanical "mirror `jobKey` to `entityKey`" fix does not apply. The only identifier in scope is `getJobDefinitionId()` / one of the process* IDs — none of which is the audited entity's own key.

This is wrong if: the migrator's `IdKeyMapper` started tracking `HISTORY_JOB_DEFINITION` and `resolveJobKey` was extended to fall back to it. As of `AuditLogMigrator.java:1-263`, the only IdKeyMapper TYPEs referenced are `HISTORY_AUDIT_LOG`, `HISTORY_EXTERNAL_TASK`, `HISTORY_JOB`, `HISTORY_PROCESS_DEFINITION`, `HISTORY_PROCESS_INSTANCE`, `HISTORY_USER_TASK`.

#### JOB-C — all typed FKs null (async batch, engine internals)

Async batch `setJobRetriesAsync`:
```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/AbstractSetJobsRetriesBatchCmd.java:73
    commandContext.getOperationLogManager()
        .logJobOperation(UserOperationLogEntry.OPERATION_TYPE_SET_JOB_RETRIES,
            null,
            null,
            null,
            null,
            null,
            propertyChanges);
```

Engine-internal `CREATE_HISTORY_CLEANUP_JOB`:
```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/HistoryCleanupCmd.java:211
    commandContext.getOperationLogManager()
      .logJobOperation(UserOperationLogEntry.OPERATION_TYPE_CREATE_HISTORY_CLEANUP_JOB,
        null,
        null,
        null,
        null,
        null,
        propertyChange);
```

For these rows, no typed FK is set. The only identifier on the row is `OPERATION_ID_` / `ID_` — i.e. the audit log's own primary key. No natural entity key exists. Resolution requires a sentinel choice (e.g., `auditLogKey`, a literal `null`-substitute string, or skipping the row).

This is wrong if: a later C7 version started populating `BATCH_ID_` on these specific rows. The schema column exists; the call sites above explicitly pass `null` for `batchId` via the seven-arg overload, but other internal call paths could differ. Not exhaustively enumerated outside `cmd/`.

### EXTERNAL_TASK — two subscenarios (row #5)

```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/persistence/entity/UserOperationLogManager.java:638
  public void logExternalTaskOperation(String operation, ExternalTaskEntity externalTask, List<PropertyChange> propertyChanges) {
    if (isUserOperationLogEnabled()) {

      UserOperationLogContext context = new UserOperationLogContext();
      UserOperationLogContextEntryBuilder entryBuilder =
          UserOperationLogContextEntryBuilder.entry(operation, EntityTypes.EXTERNAL_TASK)
            .propertyChanges(propertyChanges)
            .category(UserOperationLogEntry.CATEGORY_OPERATOR);

      if (externalTask != null) {
        ExecutionEntity instance = null;
        ProcessDefinitionEntity definition = null;
        if (externalTask.getProcessInstanceId() != null) {
          instance = getProcessInstanceManager().findExecutionById(externalTask.getProcessInstanceId());
        } else if (externalTask.getProcessDefinitionId() != null) {
          definition = getProcessDefinitionManager().findLatestProcessDefinitionById(externalTask.getProcessDefinitionId());
        }
        entryBuilder.processInstanceId(externalTask.getProcessInstanceId())
          .processDefinitionId(externalTask.getProcessDefinitionId())
          .processDefinitionKey(externalTask.getProcessDefinitionKey())
          .inContextOf(externalTask, instance, definition);
      }
```

#### EXT-A — `externalTask` non-null

All single-task external-task operations route through `ExternalTaskCmd`:
```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/ExternalTaskCmd.java:65
      List<PropertyChange> propertyChanges = getUserOperationLogPropertyChanges(externalTask);
      ...
      commandContext.getOperationLogManager().logExternalTaskOperation(operationType, externalTask,
```

(`ExternalTaskCmd` is the base for `SetExternalTaskRetriesCmd`, `SetExternalTaskPriorityCmd`, `HandleExternalTaskBpmnErrorCmd`, etc. — all single-task.)

For these rows, `getExternalTaskId() != null` and `getEntityType() == EXTERNAL_TASK`. The migrator needs a *new branch* — `resolveJobKey` is currently gated on `EntityTypes.JOB.equals(...)` only. With a branch that mirrors `resolveJobKey` for `EntityTypes.EXTERNAL_TASK`, the lookup via `HISTORY_EXTERNAL_TASK` IdKeyMapper produces a non-null C8 `jobKey` that can be mirrored to `entityKey`.

This is wrong if: the migrator stores the row under `HISTORY_JOB` instead of `HISTORY_EXTERNAL_TASK`. Inspection of `IdKeyMapper` usage was out of scope; the IdKeyMapper TYPE constants exist as separate `HISTORY_EXTERNAL_TASK` and `HISTORY_JOB` (see `AuditLogMigrator.java:17-22`).

#### EXT-B — `externalTask` null (async batch SET_EXTERNAL_TASK_RETRIES)

```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/cmd/AbstractSetExternalTaskRetriesCmd.java:101
    commandContext.getOperationLogManager().logExternalTaskOperation(
        UserOperationLogEntry.OPERATION_TYPE_SET_EXTERNAL_TASK_RETRIES, null, propertyChanges);
```

Identical shape to JOB-C: no `externalTaskId`, no process* IDs. Pure sentinel territory.

This is wrong if: another caller of `logExternalTaskOperation` exists that passes `null` for the `externalTask` argument. Grep found only the two engine call sites above.

### Other allowlisted entity types

| C7 `entityType`              | Identifier in scope                                                                                  | C7→C8 key mapping?                       | Source                                                                                          |
|------------------------------|-------------------------------------------------------------------------------------------------------|-------------------------------------------|--------------------------------------------------------------------------------------------------|
| `USER`                       | `getNewValue()` carries `userId`                                                                       | No                                        | `UserOperationLogManager.java:141-152`                                                            |
| `GROUP`                      | `getNewValue()` carries `groupId`                                                                      | No                                        | `UserOperationLogManager.java:158-169`                                                            |
| `TENANT`                     | `getTenantId()` (typed FK) and `getNewValue()` both carry `tenantId`                                   | No                                        | `UserOperationLogManager.java:175-187`                                                            |
| `GROUP_MEMBERSHIP`           | Up to 2 PropertyChanges per call (`userId`, `groupId`) — one ACT_HI_OP_LOG row each                    | No                                        | `UserOperationLogManager.java:193-218` + `DefaultHistoryEventProducer.java:830`                   |
| `TENANT_MEMBERSHIP`          | Up to 3 PropertyChanges per call (`userId`, `groupId`, `tenantId`) — one row each; plus typed `tenantId` | No                                       | `UserOperationLogManager.java:193-218`                                                            |
| `AUTHORIZATION`              | PropertyChanges only (`permissionBits`, `permissions`, `type`, `resource`, `resourceId`, optionally `userId`/`groupId`); no typed FK | No                                        | `UserOperationLogManager.java:747-770`                                                            |
| `INCIDENT`                   | `getNewValue()` carries `incidentId` (per `logAnnotationOperation`)                                    | Yes — `IdKeyMapper.TYPE.HISTORY_INCIDENT` | `UserOperationLogManager.java:722-744`                                                            |
| `DEPLOYMENT` (→ RESOURCE)    | `getDeploymentId()` (typed FK)                                                                          | No (deployments not migrated)             | `UserOperationLogManager.java:581-597`                                                            |
| `VARIABLE`                   | `getExecutionId()` or `getTaskId()` via `inContextOf`; `getProperty()`=variableName; no variable key   | No                                        | `UserOperationLogManager.java:524-547`                                                            |

Representative verbatim quotes:

```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/persistence/entity/UserOperationLogManager.java:141
  public void logUserOperation(String operation, String userId) {
    if (operation != null && isUserOperationLogEnabled()) {
      UserOperationLogContext context = new UserOperationLogContext();
      UserOperationLogContextEntryBuilder entryBuilder =
          UserOperationLogContextEntryBuilder.entry(operation, EntityTypes.USER)
            .category(UserOperationLogEntry.CATEGORY_ADMIN)
            .propertyChanges(new PropertyChange("userId", null, userId));
```

```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/persistence/entity/UserOperationLogManager.java:193
  public void logMembershipOperation(String operation, String userId, String groupId, String tenantId) {
    if (operation != null && isUserOperationLogEnabled()) {
      String entityType = tenantId == null ? EntityTypes.GROUP_MEMBERSHIP : EntityTypes.TENANT_MEMBERSHIP;
      ...
      List<PropertyChange> propertyChanges = new ArrayList<>();
      if (userId != null) {
        propertyChanges.add(new PropertyChange("userId", null, userId));
      }
      if (groupId != null) {
        propertyChanges.add(new PropertyChange("groupId", null, groupId));
      }
      if (tenantId != null) {
        propertyChanges.add(new PropertyChange("tenantId", null, tenantId));
      }
```

```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/persistence/entity/UserOperationLogManager.java:730
  protected void logAnnotationOperation(String id, String type, String idProperty, String operationType, String tenantId) {
    if (isUserOperationLogEnabled()) {

      UserOperationLogContextEntryBuilder entryBuilder =
          UserOperationLogContextEntryBuilder.entry(operationType, type)
              .propertyChanges(new PropertyChange(idProperty, null, id))
```

```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/persistence/entity/UserOperationLogManager.java:581
  public void logDeploymentOperation(String operation, String deploymentId, String tenantId, List<PropertyChange> propertyChanges) {
    if(isUserOperationLogEnabled()) {

      UserOperationLogContext context = new UserOperationLogContext();

      UserOperationLogContextEntryBuilder entryBuilder =
          UserOperationLogContextEntryBuilder.entry(operation, EntityTypes.DEPLOYMENT)
            .deploymentId(deploymentId)
```

```java
// ../camunda-bpm-platform-maintenance/engine/src/main/java/org/camunda/bpm/engine/impl/persistence/entity/UserOperationLogManager.java:524
  public void logVariableOperation(String operation, String executionId, String taskId, PropertyChange propertyChange) {
    if(isUserOperationLogEnabled()) {

      UserOperationLogContext context = new UserOperationLogContext();

      UserOperationLogContextEntryBuilder entryBuilder =
          UserOperationLogContextEntryBuilder.entry(operation, EntityTypes.VARIABLE)
          .propertyChanges(propertyChange);
```

This is wrong if: another producer (e.g., an EE-only manager, or a custom plugin) writes to `ACT_HI_OP_LOG` directly without going through these methods. Only the open-source `UserOperationLogManager` was inspected.

### Two additional facts

**[FINDING] 1.** `UserOperationLogEntry` is per-PropertyChange. Each row in `ACT_HI_OP_LOG` represents one property change; `getProperty()` / `getNewValue()` expose exactly that one pair, not the full set from the originating operation. The migrator cannot "look across" sibling rows of the same `operationId` without an extra C7 query. Backed by `DefaultHistoryEventProducer.java:830`.

This is wrong if: a later producer collapses multiple property changes into a single event. The current producer iterates property changes inside the entry loop.

**[FINDING] 2.** `updateEntityTypesThatDontMatchBetweenC7andC8` rewrites the entity type *after* `entityKey` has been resolved:

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/AuditLogTransformer.java:333
  protected void updateEntityTypesThatDontMatchBetweenC7andC8(UserOperationLogEntry userOperationLog, Builder builder) {
    if (OPERATION_TYPE_RESOLVE.equals(userOperationLog.getOperationType())
        && PROCESS_INSTANCE.equals(userOperationLog.getEntityType())) {
      builder.entityType(AuditLogEntity.AuditLogEntityType.INCIDENT);
    } else if (OPERATION_TYPE_SET_VARIABLE.equals(userOperationLog.getOperationType())
        || OPERATION_TYPE_SET_VARIABLES.equals(userOperationLog.getOperationType())) {
      builder.entityType(AuditLogEntity.AuditLogEntityType.VARIABLE);
    }
  }
```

Consequence: a `RESOLVE` operation on `PROCESS_INSTANCE` becomes `entityType=INCIDENT` while `entityKey` still holds the C8 process instance key. A `SET_VARIABLE` becomes `entityType=VARIABLE` while `entityKey` holds whatever the upstream branch set (task key for a task-scoped variable). Independent of nullability; flagged for the user's awareness, not addressed in this doc.

This is wrong if: a future `resolveVariableKey` (or similar) is added that re-sets `entityKey` after the entityType rewrite. None exists today.

## How #4 and #5 reproduce

```java
// data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NullabilityContractTest.java:179
  public void shouldNotProduceNullEntityKeyForJobAuditLog() {
    // given: a job audit log entry (Execute operation)
    deployer.deployCamunda7Process("asyncBeforeUserTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("asyncBeforeUserTaskProcessId");

    identityService.setAuthenticatedUserId("demo");
    var jobs = managementService.createJobQuery().list();
    assertThat(jobs).hasSize(1);
    managementService.executeJob(jobs.getFirst().getId());
```

```java
// data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NullabilityContractTest.java:213
  public void shouldNotProduceNullEntityKeyForExternalTaskAuditLog() {
    ...
    externalTaskService.setPriority(externalTasks.getFirst().getId(), 10L);
```

Both currently fail. Both exercise **only the JOB-A / EXT-A subcase** — `getJobId() != null` / `externalTask != null`. The other JOB-B/JOB-C/EXT-B subscenarios above are not covered by any test in `NullabilityContractTest` or `HistoryAuditLogTest`.

This is wrong if: another test in the suite covers `OPERATION_TYPE_SET_JOB_RETRIES` via the by-jobDefinition path or `OPERATION_TYPE_SET_EXTERNAL_TASK_RETRIES` via the async batch. None found in the search above.

## Implementation cost — uneven, per subscenario

Option 3 (migrator-side default), per subscenario:

### Mechanical fixes (failing tests turn green)
- **JOB-A (row #4):** ~1 line — add `builder.entityKey(String.valueOf(jobKey))` after each existing `builder.jobKey(jobKey)` in `resolveJobKey` (`AuditLogMigrator.java:229,232`).
- **EXT-A (row #5):** ~5-8 lines — add an `EXTERNAL_TASK` branch that mirrors JOB-A. Either a new `resolveExternalTaskKey` method or widen the existing `resolveJobKey` predicate to `EntityTypes.JOB.equals(...) || EntityTypes.EXTERNAL_TASK.equals(...)`. The existing `HISTORY_EXTERNAL_TASK` IdKeyMapper lookup at `AuditLogMigrator.java:230-232` already supports the C7→C8 mapping; only the entityType-gate needs to change.

Both rely on the existing skip-with-reason guard to suppress rows whose underlying job/external-task hasn't migrated:
```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/history/migrator/AuditLogMigrator.java:119
      if (c7AuditLog.getJobId() != null && dbModel.jobKey() == null) {
        throw new EntitySkippedException(c7AuditLog, SKIP_REASON_MISSING_JOB_REFERENCE);
      }
```
(For EXT-A the predicate would have to broaden to also consider `getExternalTaskId()`.)

### Decisions required (no purely mechanical fix)
- **JOB-B (sync setJobRetries-by-jobDefinitionId, suspend/activate by non-job key):** no C8 `jobKey` is available because `getJobId() == null`. Options: (i) sentinel from `getJobDefinitionId()` as a raw string; (ii) skip the row; (iii) introduce `HISTORY_JOB_DEFINITION` in `IdKeyMapper` and use that. Cost is in the design call, not the lines.
- **JOB-C (async batch retries, history-cleanup-job audit):** no typed FK at all. Sentinel decision: `auditLogKey`, the operation literal, or skip.
- **EXT-B (async batch SET_EXTERNAL_TASK_RETRIES):** identical shape to JOB-C.
- **USER / GROUP / TENANT:** ~5 lines — set `entityKey = userOperationLog.getNewValue()` in a new branch keyed on entity type. `newValue` is non-null at the C7 call sites (`UserOperationLogManager.java:147,164,182`), but C7's type system does not enforce that; a null check with fallback (skip or sentinel) is cheap to add.
- **GROUP_MEMBERSHIP / TENANT_MEMBERSHIP:** design call — one operation emits up to 2-3 rows, each carrying only its own `userId`/`groupId`/`tenantId` PropertyChange. No natural single key per row.
- **AUTHORIZATION:** design call — no typed FK; closest candidate is the `resourceId` PropertyChange, but semantics depend on the operation type.
- **INCIDENT:** ~5-10 lines — read `getNewValue()` when `getProperty() == "incidentId"`, look up via `HISTORY_INCIDENT` IdKeyMapper, set `entityKey = String.valueOf(c8IncidentKey)`. Must handle "incident not migrated" (skip vs. fallback).
- **DEPLOYMENT:** design call — deployments aren't migrated; the C7 deployment id is the only candidate. Could be a raw-string sentinel.
- **VARIABLE:** design call — no natural variable key in C7. Sentinel (`executionId + ":" + property`, `auditLogKey`, or skip).

## User-facing impact

**[FINDING]** Any C8 search-API call that returns one of the affected audit-log rows throws `NullPointerException` in the `AuditLogEntity` compact constructor at `AuditLogEntity.java:55`. `AuditLogDbReader.search` materialises results via `.stream().map(toEntity).toList()` (`AuditLogDbReader.java:75`), so a single bad row poisons the whole page.

This is wrong if: an upstream serialization layer catches the NPE and filters bad rows before returning. Not inspected in this run.

**No e2e reproduction yet.** Per [NULLABILITY-status.md lines 99-112](./NULLABILITY-status.md), seed attempts to generate `SetJobRetries`/`SetPriority` operations in the e2e environment failed because the EE docker REST API doesn't authenticate callers and unauthenticated callers skip op-log persistence. Decision recorded: don't pursue e2e for these rows.

**What "entityKey" should mean** for entityType in {`VARIABLE`, `GROUP_MEMBERSHIP`, `TENANT_MEMBERSHIP`, `AUTHORIZATION`} is a product call — these rows have no clean conceptual key, and the choice affects what an audit-trail consumer can filter by.

## Open items to confirm before Phase C

1. **Decide the per-entity-type sentinel** for the 5 groups (USER/GROUP/TENANT, MEMBERSHIPs, AUTHORIZATION, DEPLOYMENT, VARIABLE).
2. **Decide the JOB-B / JOB-C / EXT-B handling.** Sentinel from `jobDefinitionId`? Skip? Add a new IdKeyMapper TYPE?
3. **Confirm the JOB-A / EXT-A skip semantics** when the related job (or external task) is not in the C8 IdKeyMapper at audit-log migration time. Existing `SKIP_REASON_MISSING_JOB_REFERENCE` (`AuditLogMigrator.java:119-121`) handles JOB but would need broadening for EXT.
4. **Cross-check the existing entityType/entityKey mismatch** in `updateEntityTypesThatDontMatchBetweenC7andC8` (RESOLVE on PROCESS_INSTANCE → entityType=INCIDENT but entityKey is still the process instance key). Separate from #12's nullability problem; needs its own follow-up.
5. **Add a category-scoped test** that asserts non-null `entityKey` across the supported entity-type allowlist (not just JOB and EXTERNAL_TASK), so a future addition to `convertEntityType` doesn't silently reintroduce the bug. Per [AGENTS.md Defect-Category Discipline](../../../../../../../../../../AGENTS.md).
6. **Confirm or refute the assumption** that no read-side adapter (REST controller, gateway) filters NPE-throwing rows. If such a layer exists, the user-facing impact is softer than "whole page fails".

## Note for the user (per the scope rules)

The failing tests for rows #4 and #5 are the JOB-A / EXT-A subcases. A two-line mechanical fix in `resolveJobKey` makes those green:

```java
// proposed — NOT applied
  if (c7JobId != null
      && (EntityTypes.JOB.equals(c7AuditLog.getEntityType())
          || EntityTypes.EXTERNAL_TASK.equals(c7AuditLog.getEntityType()))) {
    if (isMigrated(c7JobId, HISTORY_JOB)) {
      Long jobKey = dbClient.findC8KeyByC7IdAndType(c7JobId, HISTORY_JOB);
      builder.jobKey(jobKey);
      builder.entityKey(String.valueOf(jobKey));          // new
    } else if (isMigrated(c7JobId, HISTORY_EXTERNAL_TASK)) {
      Long jobKey = dbClient.findC8KeyByC7IdAndType(c7JobId, HISTORY_EXTERNAL_TASK);
      builder.jobKey(jobKey);
      builder.entityKey(String.valueOf(jobKey));          // new
    }
  }
```

This still leaves JOB-B, JOB-C, EXT-B writing null `entityKey`. The narrow JOB-A/EXT-A fix and the broader #12 design call can be decoupled: ship the cheap fix first; schedule the design discussion for the remaining subscenarios and the other entity types.
