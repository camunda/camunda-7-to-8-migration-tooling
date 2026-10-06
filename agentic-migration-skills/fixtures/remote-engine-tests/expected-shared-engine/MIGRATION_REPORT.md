# Shared-engine test migration report

## Test parity ledger

| Camunda 7 test | Verdict | Reason |
|---|---|---|
| `SharedEnginePaymentTest` | `manual` | CPT deletes all runtime data between tests, so the test needs a dedicated Camunda 8 runtime. |
