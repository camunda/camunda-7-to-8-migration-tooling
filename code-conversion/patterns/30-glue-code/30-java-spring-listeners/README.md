# Listeners (Execution Listener / Task Listener) &#8594; Camunda 8 Listeners

In Camunda 7, *execution listeners* (`org.camunda.bpm.engine.delegate.ExecutionListener`) and *task listeners* (`org.camunda.bpm.engine.delegate.TaskListener`) run Java code synchronously inside the engine when an element or user task reaches a lifecycle event. They are common for assignment logic, auditing, notifications, and variable preparation.

Camunda 8 has equivalents, implemented as **job workers** instead of in-engine Java callbacks:

| Camunda 7                                          | Camunda 8                                                                  |
| -------------------------------------------------- | -------------------------------------------------------------------------- |
| `ExecutionListener` (`start` / `end` events)        | [Execution listeners](https://docs.camunda.io/docs/components/concepts/execution-listeners/) (8.6+), `zeebe:executionListener` with `eventType` `start` / `end` |
| `TaskListener` (`create`, `assignment`, `complete`, `update`, `delete`) | [User task listeners](https://docs.camunda.io/docs/components/concepts/user-task-listeners/) (8.8+), `zeebe:taskListener` with `eventType` `creating`, `assigning`, `updating`, `completing`, `canceling` |
| Global listeners via `ProcessEnginePlugin` / parse listeners | [Global user task listeners](https://docs.camunda.io/docs/components/concepts/global-user-task-listeners/) (8.9+), configured cluster-wide via configuration files or environment variables — no BPMN change needed |

Key conceptual differences:

-   Camunda 8 listeners are **blocking jobs**: the lifecycle transition waits until the listener job completes. Failures create incidents just like service task jobs.
-   Listener code runs in *your application* (job worker), not inside the engine — no access to engine internals or a `DelegateExecution`/`DelegateTask` object.
-   User task listeners can **correct** task attributes (assignee, due date, candidate groups, priority) and **deny** lifecycle transitions — covering the typical C7 task listener use cases.
-   Execution listener jobs cannot throw BPMN errors.
-   Limitation: execution listeners on a multi-instance body run *after* the collection is evaluated — the C7 pattern of computing the collection variable in a listener on the multi-instance activity does not work. Compute the collection in a preceding service task instead, and flag such cases during assessment.
