# Question: Zeebe MI-body execution listener timing relative to loop expression evaluation

## Context

We are building diagram conversion tooling for the Camunda 7 to Camunda 8 migration
(the `camunda-7-to-8-data-migrator` repo). We are adding support for converting
`camunda:executionListener` elements that are nested inside
`multiInstanceLoopCharacteristics` in C7 BPMN into `zeebe:executionListener` elements
nested inside C8's `multiInstanceLoopCharacteristics/extensionElements`.

In C7, a `camunda:executionListener event="start"` placed inside
`multiInstanceLoopCharacteristics/extensionElements` fires **once** when the
multi-instance scope is entered, **before** any inner instance is created and
**before** `loopCardinality` / `camunda:collection` expressions are evaluated.
This well-defined ordering enables a common C7 pattern where the start listener
computes or materializes a variable that the MI loop expression then consumes.

## The question

In Zeebe / Camunda 8, when a `zeebe:executionListener eventType="start"` is placed
inside `multiInstanceLoopCharacteristics/extensionElements` (i.e. on the MI body):

1. **Does the MI-body start listener fire BEFORE Zeebe evaluates `inputCollection`
   (and `loopCardinality` if applicable)?**
   In other words: can the listener set/modify a process variable that the
   `inputCollection` FEEL expression then reads?

2. **Or does Zeebe evaluate the loop expressions first and then fire the start
   listener?** In that case, a listener that tries to set a variable consumed by
   `inputCollection` would be too late.

## Why this matters

Several C7 migration scenarios depend on the answer:

| # | C7 pattern | Works in C8 only if listener fires BEFORE loop eval |
|---|---|---|
| 1 | Start listener computes a numeric variable used in `loopCardinality` | Yes |
| 2 | Start listener builds/loads a collection variable used in `camunda:collection` | Yes |
| 3 | Start listener resolves external IDs into a concrete collection | Yes |
| 4 | Start listener transforms JSON/XML (e.g. via Spin) into a collection | Yes |
| 5 | Start listener pre-initializes helper variables for MI expressions (`completionCondition`, etc.) | Yes |
| 6 | Start listener initializes shared context read by inner instances (no dependency on MI expressions) | Works either way |

If the answer to question 1 is **no**, then scenarios 1-5 are not viable migration
paths and customers would need to refactor (e.g. move the computation into a
preceding service task). The migration tooling would then need to emit a warning
rather than silently converting the listener placement.

## Example C8 BPMN for reference

```xml
<bpmn:serviceTask id="miTask" name="Multi-instance Task">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="mi-inner-worker" retries="3" />
  </bpmn:extensionElements>
  <bpmn:multiInstanceLoopCharacteristics>
    <bpmn:extensionElements>
      <zeebe:loopCharacteristics inputCollection="= items" inputElement="item" />
      <zeebe:executionListeners>
        <zeebe:executionListener eventType="start" retries="3" type="mi-body-init" />
      </zeebe:executionListeners>
    </bpmn:extensionElements>
  </bpmn:multiInstanceLoopCharacteristics>
</bpmn:serviceTask>
```

The question is: when `mi-body-init` runs (as a job worker), can it set the `items`
variable that `inputCollection="= items"` then evaluates? Or has `= items` already
been evaluated by the time the listener fires?
