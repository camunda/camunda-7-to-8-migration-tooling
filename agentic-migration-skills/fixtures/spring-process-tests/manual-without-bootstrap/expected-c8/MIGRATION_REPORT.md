# Expected Manual Migration Finding

## Test migration

| Camunda 7 test | Handling | Reason |
|---|---|---|
| `ManualSpringProcessTest#startsAProcessWithTheSpringEngine` | Manual migration | The test uses `SpringProcessEngineConfiguration`, and the application has no reusable `CamundaClient` worker bootstrap. The skill cannot start the application's workers through CPT. |
