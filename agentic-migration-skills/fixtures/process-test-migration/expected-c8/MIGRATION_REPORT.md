## Test engine configuration

The Camunda 7 test-only `camunda.cfg.xml` set `history` to `full`, set
`historyTimeToLive` to `180`, and disabled the job executor. The fixture tests do not query history.
The expected project removes this engine-only file and does not add a CPT history setting.

## Process-test migration decisions

| Camunda 7 test | CPT 8.9 test | Mapping and behavior |
|---|---|---|
| `ImplicitDeploymentTest#testStartsImplicitlyDeployedProcess` | `ImplicitDeploymentTest#testStartsImplicitlyDeployedProcess` | Uses JUnit 5 and explicitly deploys the converted copy. The test starts `implicit-process` and asserts completion. |
| `OrderProcessTest#approvesOrder` | `OrderProcessTest#approvesOrder` | `completeUserTask` receives BPMN element ID `Task_Approve`, not the task name. |
| `OrderProcessTest#escalatesAfterOneDay` | `OrderProcessTest#escalatesAfterOneDay` | Replaces manual timer-job execution with `increaseTime(Duration.ofDays(1))`. The test waits for `Task_Escalate` and asserts that `Task_Approve` is inactive. |
| `OrderProcessTest#continuesAfterAsync` | `OrderProcessTest#continuesAfterAsync` | CPT completes the `noopDelegate` job with a test handler. Camunda 7 manually executes the `NoopDelegate` job. |
| `OrderProcessTest#failsWhenDelegateThrows` | `OrderProcessTest#failsWhenDelegateThrows` | CPT fails the `failingDelegate` job with zero retries and asserts an incident. Camunda 7 expects `IllegalStateException` from `FailingDelegate`. |
| `MessageProcessTest#correlatesMessage` | `MessageProcessTest#correlatesMessage` | Camunda 7 `businessKey` `legacy-business-key` becomes C8 `businessId`. The message subscription separately uses `orderId` value `subscription-key`. |

The `noopDelegate` and `failingDelegate` test handlers replace behavior that the Camunda 7 fixture
runs through `NoopDelegate` and `FailingDelegate`. Approval is pending for these mock-boundary
changes. The fixture does not claim parity for either delegate until the project records its
approval.

## Form-free user-task decisions

The Camunda 7 source has no form metadata on these user tasks, and the user chose to keep them
form-free. The `.bpmnlintrc` keeps `camunda-compat/user-task-definition` enabled. Each missing-form
finding stays blocking until the lint and runtime evidence in `model-lint-and-connectivity.md` is
recorded.

| Process ID | User-task ID | User decision |
|---|---|---|
| `order-process` | `Task_Approve` | Keep form-free |
| `order-process` | `Task_Escalate` | Keep form-free |
| `async-process` | `Task_AfterAsync` | Keep form-free |

## Converted models

The Diagram Converter CLI 0.3.6-SNAPSHOT created both converted copies with
`--platform-version 8.9`. It derived the job types `noopDelegate` and `failingDelegate` from the
delegate class names. It kept each class name as a `class` task header. It copied the BPMN diagram
interchange from the Camunda 7 source models without change.

| Converted copy | Manual change after conversion |
|---|---|
| `com/camunda/fixture/tests/converted-c8-ImplicitDeploymentTest.bpmn` | None |
| `com/camunda/fixture/tests/converted-c8-process-test-cases.bpmn` | Replaced the `correlation-key-hint` converter message on `Message_Continue` with `zeebe:subscription correlationKey="= orderId"`. |
