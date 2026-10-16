# Rollback parity — Spring fixture

The C7 test observes three delegates in one engine/database transaction.
Its final delegate fails. The inventory and payment writes roll back.
The CPT test uses three real job workers. The first two writes commit before the last worker creates an incident.

| C7 test ID | CPT test ID | C7 database effects | C8 database effects | Parity verdict | Approval |
|---|---|---|---|---|---|
| `org.camunda.bpm.example.springprocess.RollbackProcessTest#rollsBackDatabaseWritesOnDelegateFailure` | `org.camunda.bpm.example.springprocess.RollbackProcessTest#observesCommittedWritesAfterWorkerFailure` | C7: inventory=0, payment=0 | C8: inventory=1, payment=1 | not verified (semantic delta) | no approval |

**Validation gate: NOT READY.** Both suites can pass while the rollback contract differs.
Keep the C7 zero-write expectation as the parity requirement.
Ask the user to approve a changed contract or an atomic/compensation design.
Record the approver, reason, and C7/C8 database effects before accepting a changed contract.
Do not record a passing `assertion_strength` review or claim C7 parity from the green CPT test.
