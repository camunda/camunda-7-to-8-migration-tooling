# Multi-Instance Start Execution Listener — C7 → C8 Migration

Branch: `1298-support-MI-startEventListener`
Scope: `diagram-converter/core`

## Context

In Camunda 7, a `start` execution listener placed on a multi-instance activity runs **before** the multi-instance body is evaluated, so the listener can dynamically produce the `inputCollection` variable (or loop cardinality).

Camunda 8 (before 8.9) evaluates `inputCollection` / `loopCardinality` **before** any execution listener runs, which breaks that pattern. Camunda 8.9 introduces a new event type `beforeAll` on the multi-instance body that fires before MI evaluation.

This change teaches the diagram converter to translate the C7 pattern into its C8 8.9+ equivalent.

## 1. Supported user stories

From the epic, the following stories are (partially) covered by the diagram converter changes:

- **As a developer migrating from C7, I can replicate my existing multi-instance + execution listener patterns in C8 so that I don't need to redesign my processes.**
  When the converter targets Camunda 8.9+ and encounters a C7 `start` execution listener that is a sibling of a `multiInstanceLoopCharacteristics`, it now emits the listener inside the multi-instance body with `eventType="beforeAll"` — preserving the pre-evaluation semantic.

- **As a developer, I can model a start execution listener on a multi-instance body so that I can produce the `inputCollection` variable before instances are created.**
  The converter now produces exactly that shape in the output BPMN.

- **As a developer, I can use execution listeners on multi-instance elements so that the pattern works consistently across element types.**
  The transformation is applied on any activity that has a `multiInstanceLoopCharacteristics` sibling (service tasks, user tasks, script tasks, etc.), because it is implemented at the listener-visitor level, not per activity type.

### Explicitly NOT in scope

- Pre-8.9 targets: the listener stays at task level with `eventType="start"` (unchanged behavior). Preserving the pre-evaluation semantic on pre-8.9 runtimes is not possible.
- Non-start event types on multi-instance activities (e.g., `end`): not moved into the MI body.
- Runtime/data-migrator changes (the epic spans those; this PR touches only the diagram converter).

## 2. Tests added

All tests live in [BPMN_CONVERSION.yaml](core/src/test/resources/BPMN_CONVERSION.yaml) and are driven by `BpmnConversionTest`.

Five cases cover the feature matrix:

| Name | Version | Event | MI body? | Expected |
|------|---------|-------|----------|----------|
| Multi-instance service task with start execution listener (8.9+) | `8.9` | `start` | yes (`camunda:collection`) | Listener moved inside `multiInstanceLoopCharacteristics/extensionElements` as `eventType="beforeAll"`; REVIEW message about the move. |
| Multi-instance service task with start execution listener (8.8, not moved) | `8.8` | `start` | yes (`camunda:collection`) | Listener stays at task level with `eventType="start"` (version gate); no move message. |
| Multi-instance service task with end execution listener (8.9+, not moved) | `8.9` | `end` | yes (`camunda:collection`) | End listener stays at task level with `eventType="end"`; only `start` triggers the move. |
| Start execution listener without multi-instance sibling (8.9+, not moved) | `8.9` | `start` | no | Listener stays at task level with `eventType="start"`; the move only applies when an MI body is a sibling. |
| Multi-instance service task with start execution listener and loopCardinality (8.9+) | `8.9` | `start` | yes (`loopCardinality`) | Listener moved inside the MI body as `eventType="beforeAll"` (cardinality-based MI bodies are handled the same as collection-based). |

Each case verifies both the converted BPMN XML and the full set of `conversion:message` elements.

## 3. Code changes

### Data model — `AbstractExecutionListenerConvertible`

[`convertible/AbstractExecutionListenerConvertible.java`](core/src/main/java/io/camunda/migration/diagram/converter/convertible/AbstractExecutionListenerConvertible.java)
- Added `beforeAll` to the `EventType` enum.
- Added a second list `zeebeMultiInstanceBodyExecutionListeners` (with getter and `addZeebeMultiInstanceBodyExecutionListener`) so a listener can be routed either to the task's extensionElements or to the MI body's extensionElements at conversion time.

### Visitor — `ExecutionListenerVisitor`

[`visitor/impl/element/ExecutionListenerVisitor.java`](core/src/main/java/io/camunda/migration/diagram/converter/visitor/impl/element/ExecutionListenerVisitor.java)
- When `event == "start"` **and** target version ≥ `8.9` **and** the listener element is a sibling of a `bpmn:multiInstanceLoopCharacteristics`:
- Sets `eventType = beforeAll` on the `ZeebeExecutionListener`.
- Routes it to `addZeebeMultiInstanceBodyExecutionListener(...)` instead of the task-level list.
- Emits a REVIEW message (`multi-instance-start-execution-listener-moved`) describing the move.
- The `executionListenerSupported` message now reports the transformed event name (`beforeAll`).
- `isOnMultiInstanceActivity(DomElement)` helper: navigates `executionListener → extensionElements → activity` and checks for a `multiInstanceLoopCharacteristics` child.

### Conversion — `ExecutionListenerConversion`

[`conversion/ExecutionListenerConversion.java`](core/src/main/java/io/camunda/migration/diagram/converter/conversion/ExecutionListenerConversion.java)
- In addition to the existing task-level listener emission, now also emits MI-body listeners into `multiInstanceLoopCharacteristics/extensionElements/zeebe:executionListeners`, reusing `BpmnElementFactory.getMultiInstanceLoopCharacteristics(element)` and `getExtensionElements(...)`.

### Messages

[`message/MessageFactory.java`](core/src/main/java/io/camunda/migration/diagram/converter/message/MessageFactory.java)
[`resources/message-templates.properties`](core/src/main/resources/message-templates.properties)
- New template `multi-instance-start-execution-listener-moved` (severity `REVIEW`):

> Start execution listener '{{ implementation }}' on multi-instance activity was moved inside the multi-instance body as a 'beforeAll' listener to preserve pre-evaluation semantic (Camunda 8.9+).
> - New factory method `MessageFactory.multiInstanceStartExecutionListenerMoved(String implementation)`.

### Test fixtures

[`test/resources/BPMN_CONVERSION.yaml`](core/src/test/resources/BPMN_CONVERSION.yaml)
- Three cases described in §2.
