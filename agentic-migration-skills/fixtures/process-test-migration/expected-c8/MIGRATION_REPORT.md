## Test Inventory

| Test ID | File | Test kind | Signals | Models | Handling | Notes |
|---|---|---|---|---|---|---|
| `c7-source:com.camunda.fixture.tests.DeploymentOnlyTest#deploysModelOnly` | `c7-source/src/test/java/com/camunda/fixture/tests/DeploymentOnlyTest.java` | out of scope | `@Deployment`, no engine execution | `com/camunda/fixture/tests/process-test-cases.bpmn` | Not part of test migration | Keep this row. The independent converted-resource check is not a CPT parity mapping. |
| `c7-source:com.camunda.fixture.tests.ContextOnlyTest#initializesEngineWithoutProcess` | `c7-source/src/test/java/com/camunda/fixture/tests/ContextOnlyTest.java` | out of scope | `ProcessEngineRule`, engine initialization only | — | Not part of test migration | No BPMN process or DMN decision executes. |
| `c7-source:com.camunda.fixture.tests.JobWorkerUserTaskTest#searchesProcessTask` | `c7-source/src/test/java/com/camunda/fixture/tests/JobWorkerUserTaskTest.java` | process test | `ProcessEngineRule`, process-backed `TaskService.createTaskQuery` | `com/camunda/fixture/tests/job-worker-user-task.bpmn` | Migrate to CPT | Blocked: the converted user task lacks `zeebe:userTask`. |
| `c7-source:com.camunda.fixture.tests.JobWorkerUserTaskTest#assignsProcessTask` | `c7-source/src/test/java/com/camunda/fixture/tests/JobWorkerUserTaskTest.java` | process test | `ProcessEngineRule`, process-backed `TaskService.claim` | `com/camunda/fixture/tests/job-worker-user-task.bpmn` | Migrate to CPT | Blocked: the converted user task lacks `zeebe:userTask`. |
| `c7-source:com.camunda.fixture.tests.JobWorkerUserTaskTest#completesProcessTask` | `c7-source/src/test/java/com/camunda/fixture/tests/JobWorkerUserTaskTest.java` | process test | `ProcessEngineRule`, process-backed `TaskService.complete` | `com/camunda/fixture/tests/job-worker-user-task.bpmn` | Migrate to CPT | Blocked until an approved model follow-up or an intended job-worker test path exists. |

## Model Inventory

| Camunda 7 model | Converted copy | Used by |
|---|---|---|
| `com/camunda/fixture/tests/ImplicitDeploymentTest.bpmn` | `com/camunda/fixture/tests/converted-c8-ImplicitDeploymentTest.bpmn` | `ImplicitDeploymentTest` |
| `com/camunda/fixture/tests/process-test-cases.bpmn` | `com/camunda/fixture/tests/converted-c8-process-test-cases.bpmn` | `OrderProcessTest`, `MessageProcessTest`, `DeploymentOnlyTest` |
| `com/camunda/fixture/tests/job-worker-user-task.bpmn` | `com/camunda/fixture/tests/converted-c8-job-worker-user-task.bpmn` | `JobWorkerUserTaskTest` |

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

## Blocked process-test pairing

| Camunda 7 test | Converted copy and task | Reason | Next action |
|---|---|---|---|
| `JobWorkerUserTaskTest#searchesProcessTask` | `converted-c8-job-worker-user-task.bpmn`, `job-worker-process#Task_Review` | Blocked: the converted `bpmn:userTask` has no `zeebe:userTask`. CPT User Task API searches cannot find it. | Select an approved model follow-up or the intended job-worker test path. |
| `JobWorkerUserTaskTest#assignsProcessTask` | `converted-c8-job-worker-user-task.bpmn`, `job-worker-process#Task_Review` | Blocked: the converted `bpmn:userTask` has no `zeebe:userTask`. CPT User Task API assignment cannot find it. | Select an approved model follow-up or the intended job-worker test path. |
| `JobWorkerUserTaskTest#completesProcessTask` | `converted-c8-job-worker-user-task.bpmn`, `job-worker-process#Task_Review` | Blocked: the converted `bpmn:userTask` has no `zeebe:userTask`. CPT User Task API calls cannot complete it. | Seek approval for a separate model follow-up or use the intended job-worker test path. Do not add a marker to this M1 converted copy during test migration. |

## Independent deployment validation

`ConvertedDeploymentResourcesIT#deploysConvertedProcessCases` starts `order-process` from the
converted resource and asserts that it reaches `Task_Approve`. It fails when the converted resource
is absent. This added test validates deployment, not parity with
`DeploymentOnlyTest#deploysModelOnly`.

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

The Diagram Converter CLI 0.3.6-SNAPSHOT created the two original converted copies with
`--platform-version 8.9`. It derived the job types `noopDelegate` and `failingDelegate` from the
delegate class names. It kept each class name as a `class` task header. It copied the BPMN diagram
interchange from the Camunda 7 source models without change.
The separate `converted-c8-job-worker-user-task.bpmn` is a synthetic marker-free converted copy.
The test migration does not edit it.

| Converted copy | Manual change after conversion |
|---|---|
| `com/camunda/fixture/tests/converted-c8-ImplicitDeploymentTest.bpmn` | None |
| `com/camunda/fixture/tests/converted-c8-process-test-cases.bpmn` | Replaced the `correlation-key-hint` converter message on `Message_Continue` with `zeebe:subscription correlationKey="= orderId"`. |
