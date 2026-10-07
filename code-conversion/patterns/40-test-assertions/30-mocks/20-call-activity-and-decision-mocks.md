# Call Activity and Decision Mocks

CPT can replace a called process or a DMN decision when the Camunda 7 test already replaced that same boundary. The fixed-output overloads are available from Camunda 8.8.

## Camunda 7

```java
ProcessExpressions.registerCallActivityMock("archive-invoice")
    .onExecutionSetVariables(Variables.putValue("archived", true))
    .deploy(rule);
```

## Camunda 8

```java
processTestContext.mockChildProcess("archive-invoice", Map.of("archived", true));
processTestContext.mockDmnDecision("credit-check", Map.of("approved", true));
```

| Camunda 7 | CPT | Note |
|---|---|---|
| `registerCallActivityMock("child").onExecutionSetVariables(vars).deploy(rule)` | `mockChildProcess("child", vars)` | |
| `registerCallActivityMock(...)` with output derived from parent variables | `mockChildProcess("child", variables -> output)` | The function overload requires 8.9. On 8.8, use a fixed output map when it preserves behavior. Otherwise deploy the converted child or report the case for manual migration. |
| `registerCallActivityMock(...)` with `onExecutionWaitForMessage`, `onExecutionWaitForTimerWithDuration`, `onExecutionSendMessage`, `onExecutionRunIntoError`, or `onExecutionDo` | No counterpart | Deploy the real converted child or ask the user to approve a test-only child model. |
| An existing mock of the decision evaluated by a business rule task | `mockDmnDecision(decisionId, output)` | Keep the same boundary. Do not add a decision mock when the Camunda 7 test evaluated the real decision. |

The CPT child-process mock deploys a dummy process with the given process ID. The DMN mock deploys a dummy decision with the given decision ID. Both return the configured output.

[CPT child-process mocks](https://docs.camunda.io/docs/apis-tools/testing/utilities/#mock-child-processes) · [CPT DMN mocks](https://docs.camunda.io/docs/apis-tools/testing/utilities/#mock-dmn-decisions)
