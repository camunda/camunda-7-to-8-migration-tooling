# Expected Standalone Task Classification

## Test Inventory

| Test ID | File | Test kind | Signals | Models | Handling | Notes |
|---|---|---|---|---|---|---|
| `standalone-task-only:org.camunda.bpm.example.standalone.StandaloneTaskTest#completesStandaloneTaskWithoutProcessInstance` | `c7-source/src/test/java/org/camunda/bpm/example/standalone/StandaloneTaskTest.java` | out of scope | `ProcessEngineRule`, `TaskService.newTask()`, `TaskService.complete(...)`, null `processInstanceId` | none | Not part of test migration | Completes a standalone task without executing a BPMN process. |

### Test kind counts

| Test kind | Count |
|---|---:|
| out of scope (Camunda 8) | 0 |
| manual redesign | 0 |
| manual migration | 0 |
| scenario test | 0 |
| remote-engine test | 0 |
| decision test | 0 |
| process test | 0 |
| out of scope | 1 |
