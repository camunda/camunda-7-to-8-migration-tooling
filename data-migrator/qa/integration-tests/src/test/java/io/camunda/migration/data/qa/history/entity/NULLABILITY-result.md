# NullabilityContract — `DecisionInstanceEntity.result`

Related issue: [camunda-7-to-8-migration-tooling#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339)
Disabled test: `NullabilityContractTest.shouldNotProduceNullResultForDecisionInstanceWithEmptyOutputs`

> **History note.** An earlier version of this doc classified the case as
> *"Not reproducible — C7 history always emits at least one synthetic
> output row → empty-outputs branch is dead code."* That conclusion was a
> misobservation. Direct read of the C7 history producer and the C8 read
> path shows the empty-outputs branch **is** reachable and the null write
> **does** happen — it is just silently masked by `NullToEmptyStringTypeHandler`
> at read time. The corrected analysis below replaces the earlier section.
> Same masking mechanism as `IncidentEntity.errorMessage` (#11) — these
> two cases bundle naturally.

## What change triggered the investigation

C8 PR [#51301](https://github.com/camunda/camunda/pull/51301) added a `requireNonNull` for `result` to the `DecisionInstanceEntity` compact constructor:

```java
// camunda/search/search-domain/src/main/java/io/camunda/search/entities/DecisionInstanceEntity.java:51
Objects.requireNonNull(result, "result");
```

The migrator builds the result string from a Camunda 7 historic decision instance's outputs. One branch explicitly returns Java `null`:

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/DecisionInstanceTransformer.java:173-176
protected String constructResultJsonFromOutputs(List<EvaluatedOutput> outputValues) {
  if (outputValues == null || outputValues.isEmpty()) {
    return null;          // ← migrator writes null into DECISION_INSTANCE.RESULT
  }
  …
}
```

A C7 historic decision instance with zero outputs therefore migrates to a `DECISION_INSTANCE.RESULT IS NULL` row, which — were the read path not masking — the C8 search reader could not construct.

## How we tried to reproduce

To force the empty-outputs branch we created a dedicated fixture:

- **DMN** (`noMatchCollectDmn.dmn`): a single-rule decision table with **hit policy `COLLECT`** (no aggregator) that matches only when `inputA == "OnlyMatchValue"`. With any other input the COLLECT policy permits zero matched rules without raising an evaluation error.
- **BPMN** (`noMatchCollectBusinessRuleProcess.bpmn`): a business rule task with `camunda:mapDecisionResult="collectEntries"`, which tolerates an empty result list (the default `singleEntry` mapping would throw and abort the process before the historic decision instance is finalised).

The test starts the process with `inputA = "DoesNotMatch"`, runs the full migrator, reads via the search API, and asserts `result` is non-null.

## Root cause (corrected)

**Net finding: this is a masked-at-read case, not an unreachable-branch case.** Same shape as #7 (`decisionDefinitionName`) and #11 (`errorMessage`).

### C7 only emits one output instance per matched rule

```java
// camunda-bpm-platform/engine/src/main/java/org/camunda/bpm/engine/impl/history/producer/DefaultDmnHistoryEventProducer.java:277-305
protected List<HistoricDecisionOutputInstance> createHistoricDecisionOutputInstances(DmnDecisionTableEvaluationEvent evaluationEvent, …) {
  List<HistoricDecisionOutputInstance> outputInstances = new ArrayList<HistoricDecisionOutputInstance>();

  List<DmnEvaluatedDecisionRule> matchingRules = evaluationEvent.getMatchingRules();
  for(int index = 0; index < matchingRules.size(); index++) {
    DmnEvaluatedDecisionRule rule = matchingRules.get(index);
    …
    for(DmnEvaluatedOutput outputClause : rule.getOutputEntries().values()) {
      …
      outputInstances.add(outputInstance);
    }
  }
  return outputInstances;
}
```

`[FINDING]` C7's history producer iterates **matched rules**, not output clauses. Zero matched rules ⇒ zero output instances. The list is then set on the historic decision instance via `initDecisionInstanceEventForDecisionTable` (line 238-239). *Wrong if: another producer / listener path adds output instances after this method runs. Grep over `engine/src/main/java/org/camunda/bpm/engine/impl/history/event/` shows only the `HistoricDecisionInstanceEntity.addOutput(...)` accessor — no other code path calls it outside the manager's query-side enrich step.*

### Query-side hydration also stays empty for zero DB rows

```java
// camunda-bpm-platform/engine/src/main/java/org/camunda/bpm/engine/impl/history/event/HistoricDecisionInstanceManager.java:215-233
protected void appendHistoricDecisionOutputInstances(Map<String, HistoricDecisionInstanceEntity> decisionInstancesById, …) {
  List<HistoricDecisionOutputInstanceEntity> decisionOutputInstances = findHistoricDecisionOutputInstancesByDecisionInstanceIds(…);
  initializeOutputInstances(decisionInstancesById.values());

  for (HistoricDecisionOutputInstanceEntity decisionOutputInstance : decisionOutputInstances) {
    …
    historicDecisionInstance.addOutput(decisionOutputInstance);
    …
  }
}
…
protected void initializeOutputInstances(Collection<HistoricDecisionInstanceEntity> decisionInstances) {
  for (… decisionInstance : …) {
    decisionInstance.setOutputs(new ArrayList<HistoricDecisionOutputInstance>());
  }
}
```

`[FINDING]` When the migrator reads via `HistoricDecisionInstanceQuery`, the manager initialises `outputs` to a fresh empty `ArrayList` and then appends only rows that exist in `ACT_HI_DEC_OUT_`. Zero DB rows ⇒ empty list (not null, not throwing). *Wrong if: `initializeOutputInstances` were skipped when no rows are returned — but it is called unconditionally before the for-loop.*

### Migrator hits the empty branch and writes Java null

```java
// data-migrator/core/src/main/java/io/camunda/migration/data/impl/interceptor/history/entity/DecisionInstanceTransformer.java:60-86
public void execute(HistoricDecisionInstance entity, Builder builder) {
  var evaluatedOutputs = mapOutputs(entity, entity.getOutputs());

  String resultJsonString;
  var collectResultValue = entity.getCollectResultValue();
  if (collectResultValue != null) {
    resultJsonString = constructResultFromCollectValue(collectResultValue);
  } else {
    resultJsonString = constructResultJsonFromOutputs(evaluatedOutputs);
  }
  …
  .result(resultJsonString)
```

`[FINDING]` With the `noMatchCollectDmn.dmn` fixture and `inputA = "DoesNotMatch"`, the plain-`COLLECT` hit policy means `evaluationEvent.getMatchingRules()` is empty AND `getCollectResultValue()` returns null (the COLLECT-aggregator handler at `AbstractCollectNumberHitPolicyHandler.java:82-91` only runs for explicit `SUM/MIN/MAX/COUNT` aggregators). The transformer therefore reaches the `constructResultJsonFromOutputs` branch with an empty list and assigns `resultJsonString = null`. *Wrong if: a downstream caller of `Builder` non-null-coerces `result` before it reaches the writer — `DecisionInstanceDbModel.java:181-182` is a plain setter, no coercion.*

### Write path persists Java null as SQL NULL

```xml
<!-- camunda/db/rdbms/src/main/resources/mapper/DecisionInstanceMapper.xml:252-261 -->
VALUES (#{decisionInstanceId}, …,
        #{evaluationDate, jdbcType=TIMESTAMP}, #{result}, #{evaluationFailure},
        …)
```

`[FINDING]` The MyBatis insert binds `#{result}` with no type handler override, so Java `null` becomes SQL NULL in the `DECISION_INSTANCE.RESULT` column. *Wrong if: a Spring/MyBatis-level interceptor rewrites parameters — none is registered for this mapper.*

### Read path silently coerces SQL NULL to empty string

```xml
<!-- camunda/db/rdbms/src/main/resources/mapper/DecisionInstanceMapper.xml:218-219 -->
<arg column="RESULT" javaType="java.lang.String"
  typeHandler="io.camunda.db.rdbms.sql.typehandler.NullToEmptyStringTypeHandler"/>
```

```java
// camunda/db/rdbms/src/main/java/io/camunda/db/rdbms/sql/typehandler/NullToEmptyStringTypeHandler.java:37-39
public String getNullableResult(final ResultSet rs, final String columnName) throws SQLException {
  return nullToEmpty(rs.getString(columnName));
}
```

`[FINDING]` The search result map applies `NullToEmptyStringTypeHandler` to the `RESULT` column. SQL NULL is coerced to `""` **before** the `DecisionInstanceEntity` compact constructor runs, so `Objects.requireNonNull(result, "result")` succeeds (empty string is not null). *Wrong if: the search read path doesn't use this result map — `DecisionInstanceMapper.xml:25-26` declares it as the `searchResultMap` for the `search` select, and `DecisionInstanceDbReader` is the sole consumer.*

### Why the disabled test would pass

`[FINDING]` The test asserts `assertThat(instances.getFirst().result()).isNotNull()` (`NullabilityContractTest.java:317-320`). After the read-side coercion the value is `""`, which satisfies `.isNotNull()`. If the `@Disabled` annotation were removed, the test would pass — but for the masking reason, not for the originally documented "C7 emits a synthetic row" reason. *Wrong if: a different code path runs in the integration-test harness — the test goes through `searchHistoricDecisionInstances(...)` which delegates to the same `DecisionInstanceDbReader` and result map.*

## User-facing impact in Operate

Operate's Decision Instance detail view has a dedicated **"Result" tab** that renders this exact field.

### REST path: gateway passes `result` through verbatim

```java
// camunda/gateways/gateway-mapping-http/src/main/java/io/camunda/gateway/mapping/http/search/SearchQueryResponseMapper.java:1309-1325
public static DecisionInstanceGetQueryResult toDecisionInstanceGetQueryResponse(
    final DecisionInstanceEntity entity) {
  return DecisionInstanceGetQueryResult.Builder.create()
      …
      .result(entity.result())
      …
}
```

```java
// camunda/zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/controller/DecisionInstanceController.java:58-68
@CamundaGetMapping(path = "/{decisionEvaluationInstanceKey}")
public ResponseEntity<DecisionInstanceGetQueryResult> getDecisionInstanceById(
    @PathVariable("decisionEvaluationInstanceKey") final String decisionEvaluationInstanceKey) {
  …
  .orElseGet(() -> getDecisionInstance(decisionEvaluationInstanceKey));
}
```

`[FINDING]` The C8 REST endpoint `GET /decision-instances/{decisionEvaluationInstanceKey}` passes `entity.result()` straight into the response with no nullability check. Whatever the entity holds (post-coercion `""`) is what the client receives. *Wrong if: an interceptor on the response mapper rewrites the field — none is registered.*

`[FINDING]` There is no parallel Operate-Java REST layer that re-shapes `result`. The v1 Operate webapp REST controllers for decisions are gone from this monorepo; under `operate/webapp/src/main/java/io/camunda/operate/webapp/rest/` only `ClientConfig*.java` remain. All Operate UI traffic for decision instances flows through the gateway-rest path above. *Wrong if: an older Operate ES-backed endpoint is still wired — but the v2 client (`fetchDecisionInstance.ts:15-20`) calls `endpoints.getDecisionInstance`, which resolves to the gateway-rest endpoint.*

### UI path: `result` is rendered in a read-only JSON editor

```ts
// operate/client/src/modules/api/v2/decisionInstances/fetchDecisionInstance.ts:15-20
const fetchDecisionInstance = async (decisionEvaluationInstanceKey: string) => {
  return requestWithThrow<GetDecisionInstanceResponseBody>({
    url: endpoints.getDecisionInstance.getUrl({decisionEvaluationInstanceKey}),
    method: endpoints.getDecisionInstance.method,
  });
};
```

```tsx
// operate/client/src/App/DecisionInstance/VariablesPanel/Result/index.tsx:31-46
<Container>
  {status === 'pending' && <Loading data-testid="result-loading-spinner" />}
  {status === 'success' &&
    decisionInstance !== null &&
    decisionInstance.state !== 'FAILED' && (
      <Suspense>
        <JSONViewer
          data-testid="results-json-viewer"
          value={decisionInstance.result ?? '{}'}
        />
      </Suspense>
    )}
  {status === 'success' && decisionInstance?.state === 'FAILED' && (
    <EmptyMessage message="No result available because the evaluation failed" />
  )}
</Container>
```

```tsx
// operate/client/src/App/DecisionInstance/VariablesPanel/Result/JSONViewer/index.tsx:18-24
const JSONViewer: React.FC<Props> = observer(({value, ...props}) => {
  return (
    <Container data-testid={props['data-testid']}>
      <RichTextEditor value={value} readOnly height="100%" />
    </Container>
  );
});
```

`[FINDING]` The "Result" tab passes `decisionInstance.result` straight into a `<JSONViewer>`, which is a read-only `RichTextEditor`. The `?? '{}'` fallback only triggers on `null` / `undefined`, **not** on the empty string. So a migrated no-match-COLLECT decision instance reaches the editor with `value=""`. *Wrong if: a wrapping component normalises `""` to `'{}'` — `JSONViewer` is the only intermediary and it does not.*

`[FINDING]` The "No result available because the evaluation failed" empty-state branch does not fire for migrated rows, because `DecisionInstanceTransformer.java:72` hardcodes `state = EVALUATED`. *Wrong if: the migrator ever writes `state = FAILED` — grep shows `.state(DecisionInstanceEntity.DecisionInstanceState.EVALUATED)` is the only `.state(...)` call in the transformer.*

`[FINDING]` The author of `Result/index.tsx:40` clearly expected `result` to be `null` / `undefined` for the absent case (`?? '{}'` would otherwise be dead code). Read-side coercion to `""` defeats that fallback, producing a worse UX than the migrator would produce if it skipped the coercion. *Wrong if: the coercion is intentionally aligned with a separate UI contract — but the `?? '{}'` makes that interpretation hard to defend.*

`[ASSUMPTION]` The end-state in the UI is a **blank read-only editor pane in the "Result" tab**: no JSON, no `{}`, no empty-state message. I did not load Operate against a migrated row to screenshot this. To upgrade to `[FINDING]` it would be enough to run the e2e walkthrough with a no-match-COLLECT seed and inspect the page. The code path above is unambiguous though — nothing between the type handler and `<RichTextEditor>` modifies the value.

## Question for the C8 team

Two related questions, ideally bundled with the `errorMessage` (#11) and `decisionDefinitionName` (#7) discussions — same underlying design choice.

1. **Is the `NullToEmptyStringTypeHandler` masking of `RESULT` part of the supported contract for `DecisionInstanceEntity`?** I.e. is it acceptable for the write side to leave the field as SQL NULL as long as the read returns `""`, or should write-side callers populate the field explicitly?

2. **If write-side population is preferred**, what value would you like? Plausible candidates from the migrator's vantage point:
   - `"null"` (the 4-char JSON literal `null`) — matches what `objectMapper.writeValueAsString(null)` already produces in the single-output path of `constructResultJsonFromOutputs`, so downstream consumers already see this shape for the "one rule matched, output value was null" case.
   - `"[]"` — semantically "no result rows".
   - `""` — explicitly aligns with the current read-side coercion, but produces a blank Result tab in Operate (see above), which is the worst UX of the three options.

If we go with Option 3 in [`NULLABILITY-resolution.md`](./NULLABILITY-resolution.md) we will write `"null"` unless the C8 team prefers `"[]"`. Either choice gives the Operate "Result" tab a parseable JSON document and removes the silent dependency on `NullToEmptyStringTypeHandler`.
