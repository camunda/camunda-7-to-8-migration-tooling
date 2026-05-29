# NullabilityContract — `DecisionDefinitionEntity.decisionRequirementsId`

Related issue: [camunda-7-to-8-migration-tooling#1339](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/1339)
Enabled test: `NullabilityContractTest.shouldNotProduceNullDecisionRequirementsIdForStandaloneDecision`

## What change triggered the investigation

C8 PR [#51301](https://github.com/camunda/camunda/pull/51301) added `Objects.requireNonNull` to the compact constructor of `io.camunda.search.entities.DecisionDefinitionEntity`. Among the newly enforced fields:

```java
Objects.requireNonNull(decisionRequirementsId, "decisionRequirementsId");
```

For DMNs deployed in C7 **without** a wrapping `<definitions>` group (the "standalone DMN" case — a `.dmn` file whose only top-level model element is a single `<decision>`), the migrator writes SQL NULL into `DECISION_DEFINITION.DECISION_REQUIREMENTS_ID`. The C8 search-API read path hydrates a `DecisionDefinitionEntity`, the compact constructor runs `requireNonNull`, and the call fails with NPE.

Confirmed end-to-end against the running stack (see `NULLABILITY-status.md` → "Verified nulls in C8" table, row `decision_def.drd_id null: 1 row`).

## Root cause

`DecisionDefinitionTransformer.java:35`:

```java
.decisionRequirementsId(prefixDefinitionId(entity.getDecisionRequirementsDefinitionKey()))
```

For a standalone DMN the C7 `DecisionDefinition.getDecisionRequirementsDefinitionKey()` returns `null` (no parent DRD on the C7 side). `ConverterUtil.prefixDefinitionId(...)` at `ConverterUtil.java:54-59` short-circuits on null:

```java
public static String prefixDefinitionId(String definitionId) {
  if (definitionId == null) {
    return null;
  }
  return String.format("%s-%s", C7_LEGACY_PREFIX, definitionId);
}
```

Null in, null out — the prefix is never applied, so the column ends up as literal SQL NULL rather than any `"c7-legacy-…"` string.

## What the migrator already does for standalone DMNs

`DecisionDefinitionMigrator.java:87-94` detects the standalone case (`drdId == null`) and synthesizes a DRD via `decisionRequirementsMigrator.migrateSyntheticDrd(c7DecisionDefinition)`. The synthetic DRD is keyed by the DMN file's `<definitions id="...">` attribute (`DecisionRequirementsMigrator.java:82-84`) and is itself written through `DecisionRequirementsDefinitionTransformer.java:65`, where its `decisionRequirementsId` becomes `prefixDefinitionId(<definitionsId>)` — i.e. `c7-legacy-<definitionsId>`.

So the synthetic DRD row exists in C8 with a well-formed id. The bug is purely that the *decision* row's `decisionRequirementsId` doesn't point at it — the migrator leaves the foreign-id reference dangling even though the target row is there.

## Data in scope at the write point

The transformer only sees the C7 `DecisionDefinition`. From that entity it can reach:

- `entity.getId()` — the C7 decision definition id (e.g. `simpleDecisionId:1:abcdef`). Cheap but self-referential — using the decision's own id as its DRD id conflates two distinct identifiers.
- `entity.getKey()` — the deployment-stable decision key (e.g. `simpleDecisionId`). Same self-reference problem.
- Via an injected `C7Client`: `c7Client.getDmnModelInstance(entity.getId()).getDefinitions().getId()` — the DMN file's `<definitions>` id. Same value the synthetic DRD already uses. Reads the deployed DMN XML; always available for a deployed C7 decision, no dependency on `HISTORY_LEVEL`.

## Implementation cost — viable options

1. **Mirror the synthetic-DRD id inside the transformer.** Inject `C7Client` into `DecisionDefinitionTransformer`, branch on `getDecisionRequirementsDefinitionKey() == null`, fall back to `prefixDefinitionId(c7Client.getDmnModelInstance(entity.getId()).getDefinitions().getId())`. ~5 lines + an `@Autowired` field. No new C7 round-trip class — re-uses the same call the synthetic-DRD path already makes.

2. **Move the field out of the transformer into the migrator.** `migrateSyntheticDrd` would need to return the DRD's id alongside the key; the migrator sets `decisionRequirementsId` on the builder and the transformer drops its line. Cleaner ownership boundary — the migrator owns the cross-row link — but ~10 lines spread across two files.

3. **Use the decision's own id as the DRD id (self-reference).** ~2 lines in the transformer. Avoided because the synthetic DRD row would then have a different id than the decision row points at — inconsistent with the grouped-DMN path where the decision's `decisionRequirementsId` matches a real DRD row.

A pure sentinel (e.g. `"standalone"`) doesn't match any actual DRD row, so Operate would render a broken DRD link. Not worth pursuing.

## User-facing impact — needs UX confirmation

The standalone-DMN case already writes both rows (synthetic DRD + decision); only the link between them is missing. With options 1 or 2 the standalone decision would link to its synthetic DRD in Operate's UI the same way grouped DMNs link to their real DRD. That seems coherent but I haven't verified Operate's rendering empirically.

**Open product/UX question:** is treating the synthetic DRD as a normal DRD the intended representation of standalone DMNs in Operate, or should they render distinctly (e.g. with no DRD link at all)? Options 1 and 2 lock in the former; the latter would require negotiating a sanctioned standalone-DMN placeholder with the C8 API team, or skipping the link entirely.

## Phase B verdict

Pre-work was **insufficient** when this investigation started (only the one-line entry in `NULLABILITY-status.md`). It is now sufficient: root cause, data in scope, implementation cost per option, and user-facing impact are all documented above. Phase C (proposing one of the five resolution options) should run in a follow-up.
