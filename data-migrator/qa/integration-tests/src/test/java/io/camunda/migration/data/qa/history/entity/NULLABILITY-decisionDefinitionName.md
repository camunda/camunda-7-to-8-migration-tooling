# NullabilityContract — `DecisionInstanceEntity.decisionDefinitionName`

Related issue: [camunda-7-to-8-migration-tooling#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339)
Disabled test: `NullabilityContractTest.shouldNotProduceNullDecisionDefinitionNameForDecisionInstance`

## Output rules — non-negotiable

1. **Read inventory.** Begin with a flat list: `path/to/file.java:LINE-LINE — why I read it`, one line per range, in the order opened. If anything is cited later that isn't here, flag it explicitly.

2. **Verbatim quotes.** Every claim about what the code does is backed by a fenced code block containing the actual lines, with `// path/to/file.java:LINE` as the first line of the block. Not paraphrase.

3. **Assumption / Finding tags.**
   - `[FINDING]` — directly read; verbatim quote follows.
   - `[ASSUMPTION]` — inferred, not read. Must include what would need to be read to upgrade it, and why it wasn't read.
   Load-bearing claims must be `[FINDING]`.

4. **Falsifier per claim.** After each `[FINDING]`, one sentence: "Falsifier: wrong if <concrete code-level condition>."

5. **Stop-and-flag.** If reasoning needs a fact that hasn't been directly read, stop, read it, and continue — or, if reading is out of scope, say so explicitly.

## Read inventory

- `camunda/search/search-domain/src/main/java/io/camunda/search/entities/DecisionInstanceEntity.java:17-57` — verify `requireNonNull("decisionDefinitionName")` in the compact constructor (the trigger)
- `data-migrator/core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/DecisionInstanceTransformer.java:60-86` — verify the migrator write path never sets `decisionDefinitionName`
- `camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/write/domain/DecisionInstanceDbModel.java:20-44, 124-300` — verify the DbModel has no `decisionDefinitionName` record component and the Builder has no matching setter
- `camunda/db/rdbms/src/main/resources/mapper/DecisionInstanceMapper.xml:25-57` — verify the search SELECT sources `DECISION_DEFINITION_NAME` from a LEFT JOIN to `DECISION_DEFINITION`
- `camunda/db/rdbms/src/main/resources/mapper/DecisionInstanceMapper.xml:194-227` — verify the resultMap arg for `DECISION_DEFINITION_NAME` applies `NullToEmptyStringTypeHandler`
- `camunda/db/rdbms/src/main/resources/mapper/DecisionInstanceMapper.xml:229-264` — verify the INSERT has no `DECISION_DEFINITION_NAME` column
- `camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/sql/typehandler/NullToEmptyStringTypeHandler.java:1-51` — verify SQL `NULL` → `""`
- `camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/read/NullSafeStrings.java:1-25` — verify `nullToEmpty` semantics
- `data-migrator/core/src/main/java/io/camunda/migration/data/HistoryMigrator.java:90-106, 164-191` — verify migrator ordering and sequential iteration
- `data-migrator/core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/DecisionDefinitionTransformer.java:31-39` — verify the C7 decision-definition name is written to the `DECISION_DEFINITION` row
- `camunda-bpm-platform/engine/src/main/java/org/camunda/bpm/engine/history/HistoricDecisionInstance.java:35-49` — verify C7 surfaces `getDecisionDefinitionName()` (data-in-scope check for any future Option 3)
- `data-migrator/qa/integration-tests/src/test/java/io/camunda/migration/data/qa/history/entity/NullabilityContractTest.java:244-284` — the `@Disabled` repro and its rationale (audited for accuracy)

## What change triggered the investigation

C8 PR [#51301](https://github.com/camunda/camunda/pull/51301) added `Objects.requireNonNull` to the compact constructor of `io.camunda.search.entities.DecisionInstanceEntity`. Among the newly enforced fields is `decisionDefinitionName`.

[FINDING] The compact constructor requires `decisionDefinitionName` non-null when the search reader hydrates a row:

```java
// camunda/search/search-domain/src/main/java/io/camunda/search/entities/DecisionInstanceEntity.java:41
  public DecisionInstanceEntity {
    Objects.requireNonNull(decisionInstanceId, "decisionInstanceId");
    Objects.requireNonNull(decisionInstanceKey, "decisionInstanceKey");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(evaluationDate, "evaluationDate");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(decisionDefinitionId, "decisionDefinitionId");
    Objects.requireNonNull(decisionDefinitionKey, "decisionDefinitionKey");
    Objects.requireNonNull(decisionDefinitionName, "decisionDefinitionName");
```

Falsifier: wrong if `decisionDefinitionName` is later annotated `@Nullable` or the `requireNonNull` line is removed.

Static inspection of the migrator showed the migrated `DECISION_INSTANCE` row carries no `decisionDefinitionName` at all. The reason is structural, not a missed setter:

[FINDING] `DecisionInstanceTransformer.execute()` sets state, dates, IDs, type, inputs, and outputs — `decisionDefinitionName` is never on the chain:

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/DecisionInstanceTransformer.java:72
    builder.state(DecisionInstanceEntity.DecisionInstanceState.EVALUATED)
        .evaluationDate(convertDate(entity.getEvaluationTime()))
        .evaluationFailure(null) // not stored in HistoricDecisionInstance
        .evaluationFailureMessage(null) // not stored in HistoricDecisionInstance
        .processDefinitionId(prefixDefinitionId(entity.getProcessDefinitionKey()))
        .decisionDefinitionId(prefixDefinitionId(entity.getDecisionDefinitionKey()))
        .decisionRequirementsId(prefixDefinitionId(entity.getDecisionRequirementsDefinitionKey()))
        .result(resultJsonString)
        .tenantId(getTenantId(entity.getTenantId()))
        .evaluatedInputs(mapInputs(entity.getId(), entity.getInputs()))
        .evaluatedOutputs(evaluatedOutputs);
```

Falsifier: wrong if a `.decisionDefinitionName(...)` call appears elsewhere on `builder` in this file, or if another interceptor file mutates the same builder with that setter (only possible if the setter exists — see next finding).

[FINDING] `DecisionInstanceDbModel` has no `decisionDefinitionName` record component:

```java
// camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/write/domain/DecisionInstanceDbModel.java:20
public record DecisionInstanceDbModel(
    String decisionInstanceId,
    Long decisionInstanceKey,
    DecisionInstanceState state,
    OffsetDateTime evaluationDate,
    String evaluationFailure,
    String evaluationFailureMessage,
    String result,
    Long flowNodeInstanceKey,
    String flowNodeId,
    Long processInstanceKey,
    Long rootProcessInstanceKey,
    Long processDefinitionKey,
    String processDefinitionId,
    Long decisionDefinitionKey,
    String decisionDefinitionId,
    Long decisionRequirementsKey,
    String decisionRequirementsId,
    Long rootDecisionDefinitionKey,
    DecisionDefinitionType decisionType,
    String tenantId,
    int partitionId,
    List<EvaluatedInput> evaluatedInputs,
    List<EvaluatedOutput> evaluatedOutputs,
    OffsetDateTime historyCleanupDate) {
```

Falsifier: wrong if a `decisionDefinitionName` record component exists in this header, or if the `Builder` (lines 124-269) declares a `decisionDefinitionName` setter.

[FINDING] The `DECISION_INSTANCE` INSERT statement has no `DECISION_DEFINITION_NAME` column. The full INSERT (column list and `VALUES` clause) spans lines 229-264 of the mapper:

```xml
<!-- camunda/db/rdbms/src/main/resources/mapper/DecisionInstanceMapper.xml:229 -->
  <insert id="insert" parameterType="io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel">
    INSERT INTO ${prefix}DECISION_INSTANCE (DECISION_INSTANCE_ID,
                                            DECISION_INSTANCE_KEY,
                                            PROCESS_INSTANCE_KEY,
                                            ROOT_PROCESS_INSTANCE_KEY,
                                            PROCESS_DEFINITION_KEY,
                                            PROCESS_DEFINITION_ID,
                                            DECISION_DEFINITION_KEY,
                                            DECISION_DEFINITION_ID,
                                            DECISION_REQUIREMENTS_KEY,
                                            DECISION_REQUIREMENTS_ID,
                                            FLOW_NODE_INSTANCE_KEY,
                                            FLOW_NODE_ID,
                                            ROOT_DECISION_DEFINITION_KEY,
                                            TYPE,
                                            STATE,
                                            EVALUATION_DATE,
                                            RESULT,
                                            EVALUATION_FAILURE,
                                            EVALUATION_FAILURE_MESSAGE,
                                            TENANT_ID,
                                            PARTITION_ID,
                                            HISTORY_CLEANUP_DATE)
```

Falsifier: wrong if `DECISION_DEFINITION_NAME` appears in this column list, or if any other INSERT into `DECISION_INSTANCE` exists in the mapper. The schema is engineered to derive the name from `DECISION_DEFINITION` at read time only.

This looked like a clear contract violation.

## How we tried to reproduce

We added an integration test (`NullabilityContractTest.shouldNotProduceNullDecisionDefinitionNameForDecisionInstance`) that:

1. Deploys a DMN and a BPMN business rule task that triggers the decision.
2. Starts a process instance and lets the decision evaluate (recording a Camunda 7 historic decision instance).
3. Runs the full migrator (`historyMigrator.migrate()`).
4. Reads back the migrated row via the C8 search API:
   ```java
   decisionInstanceReader.search(DecisionInstanceQuery.of(...))
   ```
5. Asserts `instance.decisionDefinitionName()` is non-null.

The expectation: the C8 entity constructor should throw `NullPointerException("decisionDefinitionName")` when the search reader hydrates the row.

## Why the test passes — read-side coercion masks the write-side absence

Two independent coercion layers in `DecisionInstanceMapper.xml` keep the constructor from ever seeing null. **Either layer alone would satisfy the contract.**

### Layer 1 — LEFT JOIN hydration

[FINDING] The search query sources `DECISION_DEFINITION_NAME` from `dd.NAME` via a LEFT JOIN to `DECISION_DEFINITION`, not from the `DECISION_INSTANCE` row:

```xml
<!-- camunda/db/rdbms/src/main/resources/mapper/DecisionInstanceMapper.xml:27 -->
    SELECT * FROM (
    SELECT di.DECISION_INSTANCE_ID,
    ...
    dd.NAME AS DECISION_DEFINITION_NAME,
    dd.VERSION AS DECISION_DEFINITION_VERSION
    FROM ${prefix}DECISION_INSTANCE di
    LEFT JOIN ${prefix}DECISION_DEFINITION dd ON (di.DECISION_DEFINITION_KEY =
    dd.DECISION_DEFINITION_KEY)
```

Falsifier: wrong if the SELECT sources `DECISION_DEFINITION_NAME` from `di.` instead of `dd.`, or if the JOIN is changed away from a LEFT JOIN such that the row could disappear instead of carrying a NULL.

[FINDING] The migrator pipeline guarantees the corresponding `DECISION_DEFINITION` row is written before the `DECISION_INSTANCE` row. `HistoryMigrator.getMigrators()` returns a fixed-order `List.of(...)` with `decisionDefinitionMigrator` (index 10) ahead of `decisionInstanceMigrator` (index 11), and `migrate()` iterates that list sequentially:

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/HistoryMigrator.java:90
  protected List<HistoryEntityMigrator<?, ?>> getMigrators() {
    return List.of(
        formMigrator,
        processDefinitionMigrator,
        processInstanceMigrator,
        flowNodeMigrator,
        userTaskMigrator,
        variableMigrator,
        jobMigrator,
        externalTaskMigrator,
        incidentMigrator,
        decisionRequirementsMigrator,
        decisionDefinitionMigrator,
        decisionInstanceMigrator,
        auditLogMigrator
    );
  }
```

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/HistoryMigrator.java:172
      getMigrators().forEach(HistoryEntityMigrator::migrate);
```

Falsifier: wrong if `decisionInstanceMigrator` precedes `decisionDefinitionMigrator` in the list, or if `migrate()` switches to a `parallelStream()` / similar non-sequential traversal.

[FINDING] `DecisionDefinitionTransformer` passes `entity.getName()` through to the DbModel, so the joined `dd.NAME` carries whatever name the C7 engine had:

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/DecisionDefinitionTransformer.java:31
  public void execute(DecisionDefinition entity, DecisionDefinitionDbModelBuilder builder) {
    builder.decisionDefinitionKey(getNextKey())
        .name(entity.getName())
        .decisionDefinitionId(prefixDefinitionId(entity.getKey()))
        .decisionRequirementsId(prefixDefinitionId(entity.getDecisionRequirementsDefinitionKey()))
        .tenantId(getTenantId(entity.getTenantId()))
        .version(entity.getVersion());
```

Falsifier: wrong if `.name(entity.getName())` is conditional, removed, or sources its value from somewhere other than the C7 `DecisionDefinition`.

### Layer 2 — `NullToEmptyStringTypeHandler`

[FINDING] The resultMap arg for `DECISION_DEFINITION_NAME` applies `NullToEmptyStringTypeHandler`. The `<arg ...>` element begins on line 212; the `typeHandler` attribute is on line 213:

```xml
<!-- camunda/db/rdbms/src/main/resources/mapper/DecisionInstanceMapper.xml:212 -->
      <arg column="DECISION_DEFINITION_NAME" javaType="java.lang.String"
        typeHandler="io.camunda.db.rdbms.sql.typehandler.NullToEmptyStringTypeHandler"/>
```

Falsifier: wrong if the resultMap arg uses a different type handler, or if the arg is removed and `DECISION_DEFINITION_NAME` is hydrated by some other mechanism.

[FINDING] `NullToEmptyStringTypeHandler` converts SQL `NULL` to `""` at the JDBC layer:

```java
// camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/sql/typehandler/NullToEmptyStringTypeHandler.java:37
  @Override
  public String getNullableResult(final ResultSet rs, final String columnName) throws SQLException {
    return nullToEmpty(rs.getString(columnName));
  }
```

```java
// camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/read/NullSafeStrings.java:22
  public static String nullToEmpty(final String value) {
    return value == null ? "" : value;
  }
```

Falsifier: wrong if `nullToEmpty` is changed to return `null` for `null`, or if `getNullableResult` is rewritten not to call it.

### Combined effect

Even when the LEFT JOIN finds no match (would require a `DECISION_INSTANCE` row whose `DECISION_DEFINITION_KEY` references a missing `DECISION_DEFINITION` row — not produced by the migrator due to the ordering above) **or** when the matched `DECISION_DEFINITION.NAME` is itself null (C7 DMN with no `name` attribute on the `<decision>` element), the field arrives at the `DecisionInstanceEntity` constructor as `""`, not `null`. `requireNonNull` is silently satisfied either way.

`requireNonNull("decisionDefinitionName")` is therefore effectively unreachable through the C8 RDBMS exporter read path under the current schema — the contract is honoured by infrastructure, not by the write side.

## Implementation cost of the resolution options

Migrator-side default (Option 3) is **not a few-line change** here, contrary to how it is framed elsewhere:

[FINDING] There is no `decisionDefinitionName` setter on `DecisionInstanceDbModel.Builder` for the migrator to call. The Builder field declarations begin at line 126; none names `decisionDefinitionName`. Same for the setter methods (lines 151-269). See the full record header above for the symmetric absence on the record components themselves.

Falsifier: wrong if a Builder setter or field named `decisionDefinitionName` exists in `DecisionInstanceDbModel.java`.

[FINDING] There is no `DECISION_DEFINITION_NAME` column on the `DECISION_INSTANCE` table — see the full INSERT column list quoted above. The schema is engineered to derive the name from `DECISION_DEFINITION` at read time only.

Falsifier: wrong if `DECISION_DEFINITION_NAME` is added to the INSERT or appears in a later `ALTER TABLE` referenced from `DecisionInstanceMapper.xml`.

[FINDING] Both absences live in the C8 monorepo (`db/rdbms`), outside this repo's control. (Verified by the file paths of both quoted snippets — they live under `camunda/db/rdbms/`, the C8 repository, not under `data-migrator/`.)

Falsifier: wrong if the relevant `DecisionInstanceDbModel.java` / `DecisionInstanceMapper.xml` are copied into this repo and overridden locally.

[ASSUMPTION] A real Option 3 would require, in the C8 monorepo:

1. Schema migration adding `DECISION_DEFINITION_NAME` to `DECISION_INSTANCE`.
2. Adding the field to `DecisionInstanceDbModel` (record component + Builder method).
3. Updating the `insert` statement in `DecisionInstanceMapper.xml`.
4. Updating the `search` statement to read `di.DECISION_DEFINITION_NAME` (or to retain the LEFT JOIN as a fallback).

What would upgrade this to `[FINDING]`: actually performing the change and counting the files touched, or reading the C8 schema-changelog files under `db/rdbms/src/main/resources/db/changelog/` to confirm a schema migration is the right mechanism. Not done because Option 3 is not the recommended option for this row, so the cost enumeration is illustrative rather than load-bearing.

Only after that upstream work lands could the migrator-side change (`DecisionInstanceTransformer` calling `.decisionDefinitionName(entity.getDecisionDefinitionName())`) be made. The C7 value is available:

[FINDING] `HistoricDecisionInstance` surfaces `getDecisionDefinitionName()`:

```java
// camunda-bpm-platform/engine/src/main/java/org/camunda/bpm/engine/history/HistoricDecisionInstance.java:42
  /** The name of the decision definition */
  String getDecisionDefinitionName();
```

Falsifier: wrong if this getter is removed in the version of `camunda-bpm` the migrator depends on.

This case is therefore structurally different from #11 (`IncidentEntity.errorMessage`), where — per `NULLABILITY-errorMessage.md` — the column and DbModel field both already exist and Option 3 is genuinely a few migrator lines. The #11 claim is recorded as out-of-scope here; it was not directly verified during this investigation.

Within this repo, the realistic options are:

- **Option 1 — do nothing**: rely on the two existing coercion layers.
- **Option 2 — canary test**: re-enable `shouldNotProduceNullDecisionDefinitionNameForDecisionInstance` (currently `@Disabled`) so any future C8 change to either layer — removing the type handler, replacing the LEFT JOIN, or denormalising the column — lands as a clear failure here. Cost: tens of lines including a corrected rationale comment.
- **Option 4 — negotiate with C8**: clarify whether the JOIN+type-handler hydration is the supported contract, or whether C8 plans to denormalise the column.

## Discrepancies to be aware of

These are observations about adjacent docs / test code that future readers should not propagate as facts:

- **`NULLABILITY-status.md` row #7 summary** compresses the masking into the JOIN alone: *"LEFT JOIN to `DECISION_DEFINITION` hydrates the field — write-side null invisible to contract"*. Layer 2 (`NullToEmptyStringTypeHandler`) is doing equal work — it also covers the case where the JOIN matches but `dd.NAME` is itself NULL. The status row's compression understates the read-side defences.

- **The `@Disabled` rationale** on `NullabilityContractTest.shouldNotProduceNullDecisionDefinitionNameForDecisionInstance:253-264` says *"Fix should be migrator-side: set `decisionDefinitionName` on the DbModel from `HistoricDecisionInstance.getDecisionDefinitionName()`"*. As written this is misleading — there is no Builder setter to call (verified above). A future reader should treat the test's suggested fix as dependent on prior C8-side work; see Option 3 above for the prerequisite chain.

## Question for the C8 team

Is the JOIN-plus-`NullToEmptyStringTypeHandler` hydration of `decisionDefinitionName` the intended contract for `DecisionInstanceEntity`, or is it incidental and the column should be denormalised into `DECISION_INSTANCE`?

If JOIN-based hydration is intended, this case is a no-op on the migrator side — the C7-to-C8 migrator has nowhere to write the value. If denormalisation is planned, the schema change happens in the C8 repo and the migrator-side hook (`HistoricDecisionInstance.getDecisionDefinitionName()` → `DecisionInstanceDbModel.Builder.decisionDefinitionName(...)`) can land alongside it.
