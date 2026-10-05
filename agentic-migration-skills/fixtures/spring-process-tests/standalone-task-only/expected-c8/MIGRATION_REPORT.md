# Expected Standalone Task Classification

## Test Inventory

| Test ID | File | Test kind | Signals | Models | Handling | Notes |
|---|---|---|---|---|---|---|
| `standalone-task-only:org.camunda.bpm.example.standalone.StandaloneTaskTest#completesStandaloneTaskWithoutProcessInstance` | `c7-source/src/test/java/org/camunda/bpm/example/standalone/StandaloneTaskTest.java` | out of scope | `ProcessEngineRule`, `TaskService.newTask()`, `TaskService.complete(...)`, null `processInstanceId` | none | Not part of test migration | Completes a standalone task without executing a BPMN process. |

Counts by test kind: out of scope 1.
