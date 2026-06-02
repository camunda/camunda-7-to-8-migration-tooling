# NullabilityContract — `DecisionInstanceEntity.decisionDefinitionType`

Related issue: [camunda-7-to-8-migration-tooling#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339)
Disabled test: `NullabilityContractTest.shouldNotProduceNullDecisionDefinitionTypeForUnresolvableDecision`

## What change triggered the investigation

C8 PR [#51301](https://github.com/camunda/camunda/pull/51301) added a `requireNonNull` for `decisionDefinitionType` to the `DecisionInstanceEntity` compact constructor:

```java
Objects.requireNonNull(decisionDefinitionType, "decisionDefinitionType");
```

The migrator infers the type (`DECISION_TABLE` vs `LITERAL_EXPRESSION`) by parsing the DMN XML for each historic decision instance. One branch explicitly returns `null`:

```java
protected DecisionDefinitionType determineDecisionType(HistoricDecisionInstance c7) {
  var dmnModelInstance = c7Client.getDmnModelInstance(c7.getDecisionDefinitionId());
  Decision decision = dmnModelInstance.getModelElementById(c7.getDecisionDefinitionKey());
  if (decision == null) {
    return null;        // ← migrator writes null into DECISION_INSTANCE.TYPE
  }
  …
}
```

`DECISION_INSTANCE.TYPE` is read directly from that column (no JOIN, no read-side coercion), so a null write produces a `requireNonNull` violation at search time.

## How we tried to reproduce

The original test used a DMN with parent/child decision requirements (`simpleDmnWithReqs.dmn`) and a BPMN that exercises both decisions, hoping the child's type lookup would fail. The expected failure path: the parent DMN's `getModelElementById(childKey)` returns `null` because the child decision is not part of the parent's XML.

## Why we think it's not reproducible

The test passes — both parent and child rows have a non-null `decisionDefinitionType`. Two layers conspire to make the null branch unreachable:

1. **The migrator looks up each decision's *own* DMN resource, not the parent's.** The code does:
   ```java
   c7Client.getDmnModelInstance(c7.getDecisionDefinitionId())
   ```
   where `decisionDefinitionId` is the C7 primary key of the historic decision instance's own definition. Camunda 7's `RepositoryService.getDmnModelInstance(id)` always returns the XML resource that the decision was deployed from. By construction, that resource contains a `<decision>` element whose `id` attribute equals the decision's `decisionDefinitionKey`.

2. **Whether parent and child decisions live in the same DMN file or in separate files makes no difference.** In both cases, the child's own `getDmnModelInstance(childId)` returns *the child's* XML, where the child decision is always present. `getModelElementById(childKey)` always finds it.

The `if (decision == null)` branch can only fire if the deployed DMN XML has been corrupted *after* deployment in a way that removes the decision element while leaving the resource registered — which Camunda 7 does not do.

## Question for the C8 team

Given that this null branch is genuinely unreachable through any Camunda 7 deployment, would you prefer:

1. We harden the migrator side anyway — replace `return null;` with `return DecisionDefinitionType.UNSPECIFIED;` (or `UNKNOWN`) so the contract is mechanically guaranteed even for the defensive case. Cheap and worth doing.
2. Or leave it — the static-analysis "null return" can be silenced with a `@SuppressWarnings("NullAway")` since the path is provably dead.

Our intuition is to go with (1) — but we'd like a sanity check that `UNSPECIFIED` / `UNKNOWN` is the intended fallback for "we can't determine the type" rather than something stricter.
