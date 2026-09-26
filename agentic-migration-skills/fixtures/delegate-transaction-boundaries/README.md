# Delegate transaction-boundary path test

This fixture checks how the skill classifies a C7 path with `camunda:asyncAfter`.
The BPMN model routes one incoming path through a user task with an `asyncAfter`
boundary and one path through a user task without that boundary.
Both paths continue to the same JavaDelegate.

## Run the path test

1. Copy this directory into a temporary project and keep the original files unchanged.
2. Run `python3 verify_async_after_path.py` to check the fixture paths.
   On Windows, run `py -3 verify_async_after_path.py`.
3. Run the `migrate-c7-to-c8-code` skill with code-only scope and Approach A.
   Give the skill `c7-source/src/main/resources/async-after-boundary-c7.bpmn` as path evidence.
   Evaluate both values of `useAsyncAfterPath`.
4. Stop after the skill records its pre-transform gate result in
   `MIGRATION_REPORT.md`.

## Expected gate result

| Incoming path | Expected C7 command segment |
|---|---|
| `WaitStateAsyncAfter` to `SynchronousDelegate` | The user-task completion commits before the continuation runs the delegate. The delegate failure cannot roll back that completion. |
| `WaitStateSynchronous` to `SynchronousDelegate` | The user-task completion and delegate run in the same command. The delegate failure rolls back the task completion. |

The gate records both paths and the `camunda:asyncAfter` boundary.
It marks the rollback gap on the synchronous path as **not preserved** in C8.
It asks the user to choose C8 failure handling before it transforms the delegate.
The skill does not run OpenRewrite while that decision remains open.
