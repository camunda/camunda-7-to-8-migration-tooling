# Expected Manual Migration Finding

## Test Inventory

| Test ID | File | Test kind | Signals | Models | Handling | Notes |
|---|---|---|---|---|---|---|
| `manual-without-bootstrap:org.camunda.bpm.example.manual.ManualSpringProcessTest#startsAProcessWithTheSpringEngine` | `c7-source/src/test/java/org/camunda/bpm/example/manual/ManualSpringProcessTest.java` | process test | `@ContextConfiguration`, `RuntimeService`, Spring modifier | `c7-source/src/test/resources/manual-process.bpmn` | Report only | Manual migration: no reusable `CamundaClient` worker bootstrap. |

Counts by test kind: process test 1.

## Test migration

| Camunda 7 test | Handling | Reason |
|---|---|---|
| `ManualSpringProcessTest#startsAProcessWithTheSpringEngine` | Report only | Manual migration: the test deploys `manual-process`, which reaches `ManualProcessWorker`. The application has no reusable `CamundaClient` worker bootstrap. |
