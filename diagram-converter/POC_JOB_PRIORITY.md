# POC: C7 `camunda:jobPriority` → C8 `<zeebe:jobPriorityDefinition>`

Proof of concept for mapping the Camunda 7 job priority attribute to the new
Camunda 8 Zeebe extension element proposed for [job pull priority
activation](https://github.com/camunda/camunda/issues) (C8 epic — Aug 2026).

## Scope

- Source: `camunda:jobPriority` attribute, on any BPMN element the C7 engine
  accepts it on (process, activities, events).
- Target: `<zeebe:jobPriorityDefinition priority="..." />` extension element.
- Out of scope for this POC: `camunda:taskPriority` (external task topics) and
  `camunda:priority` (user task) — both remain unsupported as before.

### Value handling

- Numeric literal `"90"` → emitted as-is: `priority="90"`.
- JUEL expression `"${jobPriority}"` → transformed via the existing
  `ExpressionTransformer.transformToFeel(...)` → `priority="=jobPriority"`.

This matches how `camunda:dueDate` is already handled.

## Design decisions

### Decision 1 — Refactor the existing `JobPriorityVisitor` (chosen)

Before this POC, [JobPriorityVisitor](core/src/main/java/io/camunda/migration/diagram/converter/visitor/impl/attribute/JobPriorityVisitor.java)
extended `AbstractRemoveAttributeVisitor` — it silently removed the attribute
and emitted an "attribute removed" message. The POC changes its base class to
`AbstractSupportedAttributeVisitor` so it can now produce output.

**Option A — Refactor the existing visitor (chosen).**

- One attribute → one visitor. Matches the pattern used by `TaskTopicVisitor`,
  `DueDateVisitor`, `FollowUpDateVisitor`, etc. (all converting attributes
  extend `AbstractSupportedAttributeVisitor` from day one).
- No double-processing risk. `AbstractAttributeVisitor#visitFilteredElement`
  (line 18) unconditionally queues the attribute for removal via
  `addAttributeToRemove`, so two visitors on the same attribute would both
  declare it for removal *and* each emit its own message — noisy and
  redundant.
- Single registration in the `META-INF/services` file.
- The existing "attribute removed" message becomes misleading once the value
  is preserved; refactoring fixes that at the same time.

**Option B — Keep the old visitor, add a new `JobPriorityDefinitionVisitor` (not chosen).**

- Pro: smallest surface-area diff to the existing class.
- Cons:
  - The old visitor still emits `MessageFactory.attributeRemoved(...)`, which
    now contradicts the new behavior. You would either have to gut the old
    visitor (equivalent effort to refactoring it) or tolerate a stale
    message.
  - Two visitors on the same `{BPMN, camunda:jobPriority}` pair both run —
    order is SPI-load-order dependent; either one of them would have to
    override `canVisit` to short-circuit, or the old one would need
    deregistering. Either way the "small diff" argument disappears.
  - Diverges from the convention used everywhere else in the codebase.
- Estimated effort to adopt Option B later anyway: ~15 min (same code, just
  split across two classes and re-registered). The refactor in Option A is
  not a one-way door.

### Decision 2 — Emit `<zeebe:jobPriorityDefinition>` on the common base convertible

The convertible hierarchy rooted at `AbstractProcessElementConvertible` covers
every BPMN element that can carry the attribute: process, all activities
(service/user/send/receive/business-rule/script/manual tasks, sub-processes,
call activities, transactions) and events. Placing the field on the common
base means:

- One field, one conversion writer (`ProcessElementConversion`).
- Works automatically on every new element type that extends the base.

Field lives on `AbstractProcessElementConvertible` as an inner
`ZeebeJobPriorityDefinition` holder, paralleling `ZeebeProperty` already on
the same class. Output serialization is added to
[ProcessElementConversion](core/src/main/java/io/camunda/migration/diagram/converter/conversion/ProcessElementConversion.java),
which already handles extension-element creation for this base type.

### Decision 3 — Zeebe extension element name

The C8 proposal lists two candidates: `zeebe:jobPriorityDefinition` and
`zeebe:taskPriorityDefinition`. This POC uses **`zeebe:jobPriorityDefinition`**
— the proposal's stated preference and the name that contrasts most clearly
with the existing `zeebe:priorityDefinition` (user task).

Effort to switch to the alternative name: **trivial** (~2 min). The name
appears in exactly two places — the conversion writer and the YAML test
fixtures. No API, no public extension-point surface depends on it.

## What changed

| File | Change |
|---|---|
| `convertible/AbstractProcessElementConvertible.java` | Added `ZeebeJobPriorityDefinition` holder + getter/setter. |
| `conversion/ProcessElementConversion.java` | Emits `<zeebe:jobPriorityDefinition priority="..."/>` when set. |
| `visitor/impl/attribute/JobPriorityVisitor.java` | Refactored to extend `AbstractSupportedAttributeVisitor`; transforms value and calls `addConversion`. |
| `test/resources/BPMN_CONVERSION.yaml` | Test cases: literal on service task, FEEL on service task, literal on send task. |
| `test/resources/collaboration.bpmn` | (unchanged, but the existing smoke-test now exercises the conversion on a process.) |

## Limitations / follow-ups

- No explicit filter on element type. The visitor runs whenever the attribute
  is present on any BPMN element — same as before. If C8 ends up restricting
  `jobPriorityDefinition` to a specific subset, add a `canVisit` override.
- No priority-range validation (C7 allowed any long; C8 proposal does not
  pin a range yet).
- No message-template localization: the POC currently reuses the generic
  `ExpressionTransformationResultMessageFactory` message for expressions,
  which references the generic migration-docs link. A dedicated message
  entry + docs link can be added once the C8 docs page exists.
