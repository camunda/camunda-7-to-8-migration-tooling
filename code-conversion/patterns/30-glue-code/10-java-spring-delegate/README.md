# JavaDelegate &#8594; Job Worker (Spring)

In Camunda 7, JavaDelegates are a common way to implement glue code. JavaDelegates might be

- Spring beans referenced via Expression language (``delegateExpression``)
- Java classes referenced via the class name (``class``)

JavaDelegates run in the same context as the engine. In addition to the DelegateExecution class that provides an interface to interact with the running process instance, a JavaDelegate can also call all engine services, like the Runtime service.

The code conversion patterns for the JavaDelegate cover the most important methods how a JavaDelegate can interact with the running process instance:

- class level changes
- getting and setting process variables
- reporting a failure
- raising an incident
- throwing a BPMN error

There are often multiple methods that achieve the same result. The patterns try to capture as many examples as possible. Delegate code that accesses the engine services is not covered here. Please refer to the patterns for the engine services. In general, delegate code that utilizes engines services is more difficult to migrate to Camunda 8.

## Transaction and security semantics

In Camunda 7, `camunda:asyncBefore` starts a command segment before its activity.
`camunda:asyncAfter` starts a continuation segment after its activity. A failure rolls back the
synchronous work in the command segment that runs the delegate.

Without a boundary between a wait state and the JavaDelegate, the command that completes the wait
state can also run the delegate. If the delegate fails, that command rolls back and the wait state
remains incomplete. A preceding `camunda:asyncAfter` boundary commits its activity before downstream
work continues. Synchronous activities after that boundary can still share the delegate's command.
See [Camunda 7 asynchronous continuations](https://docs.camunda.org/manual/7.14/user-guide/process-engine/transactions-in-processes/#asynchronous-continuations).

A C8 job worker runs outside the engine transaction. Its failure can consume retries and raise an
incident after the preceding user task has completed. The worker does not share the C7 engine
transaction or its thread-bound security context.

Do not describe moving the same Java body to a worker as equivalent synchronous behavior. Ask the user to choose C8 retries and incident handling, a BPMN error or compensation flow, or an explicit manual step. Ask the user to choose a worker-side transaction or security mechanism, or a code refactor, when the source relies on those contexts. Record the chosen behavior and accepted parity gap in `MIGRATION_REPORT.md`. The `SynchronousDelegateTransactionBoundaryTest` in the C8 code examples demonstrates the resulting process state. See the Handling a Failure pattern for additional guidance.
